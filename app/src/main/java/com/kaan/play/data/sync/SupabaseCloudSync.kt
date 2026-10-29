package com.kaan.play.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kaan.play.BuildConfig
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
private data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtMillis: Long,
)

@Serializable
private data class AuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
private data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthUser? = null,
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
private data class SyncBaseline(
    val history: Map<String, Long> = emptyMap(),
    val favorites: Map<String, Long> = emptyMap(),
)

private data class RemoteCloudSnapshot(val transferJson: String, val updatedAt: String)
private class CloudWriteConflict : Exception()

data class CloudAccountState(val userId: String, val email: String)

enum class CloudRegistrationResult { SIGNED_IN, EMAIL_CONFIRMATION_REQUIRED }

/** Supabase Auth + kullanıcı başına tek, RLS ile korunan snapshot. Service-role anahtarı hiçbir zaman istemciye girmez. */
class SupabaseCloudSync(
    context: Context,
    private val profiles: LocalProfileRepository,
    private val backups: LocalSyncBackup,
    private val historyDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val projectUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
) {
    private val appContext = context.applicationContext
    private val baseUrl = projectUrl.trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
    private val sessionStore = EncryptedCloudSessionStore(appContext)
    private val syncMetadata = appContext.getSharedPreferences("cloud_sync_metadata", Context.MODE_PRIVATE)
    private val syncMutex = Mutex()
    private val _account = MutableStateFlow(sessionStore.readOrNull()?.toAccount())
    val account: StateFlow<CloudAccountState?> = _account.asStateFlow()

    suspend fun createAccount(email: String, password: String): CloudRegistrationResult = withContext(Dispatchers.IO) {
        check(sessionStore.readOrNull() == null) { "Önce mevcut hesaptan çıkış yapın." }
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@') && normalizedEmail.substringAfter('@').contains('.')) { "Geçerli bir e-posta adresi girin." }
        require(password.length >= 8) { "Şifre en az 8 karakter olmalı." }
        val response = authPost(
            endpoint = "auth/v1/signup",
            body = buildJsonObject { put("email", normalizedEmail); put("password", password) }.toString(),
        )
        val parsed = decodeAuth(response)
        val session = parsed.toSession(normalizedEmail)
        if (session == null) return@withContext CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED
        sessionStore.write(session)
        _account.value = session.toAccount()
        CloudRegistrationResult.SIGNED_IN
    }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        check(sessionStore.readOrNull() == null) { "Önce mevcut hesaptan çıkış yapın." }
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@')) { "Geçerli bir e-posta adresi girin." }
        val response = authPost(
            endpoint = "auth/v1/token?grant_type=password",
            body = buildJsonObject { put("email", normalizedEmail); put("password", password) }.toString(),
        )
        val parsed = decodeAuth(response)
        val session = parsed.toSession(normalizedEmail) ?: error("Giriş oturumu alınamadı. E-posta doğrulaması gerekiyorsa gelen kutunuzu kontrol edin.")
        sessionStore.write(session)
        _account.value = session.toAccount()
    }

    /** Çıkışı her durumda tamamlar; son eşitleme başarısızsa false döner ve cihaz kopyası hesap izolasyonu için temizlenir. */
    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val current = sessionStore.readOrNull() ?: run { _account.value = null; return@withLock true }
            val synced = runCatching { syncLocked() }.isSuccess
            val valid = sessionStore.readOrNull() ?: current
            runCatching { authPost("auth/v1/logout", "{}", valid.accessToken) }
            val localUserId = profiles.ensureUserId()
            runCatching { profiles.allProfiles().forEach { backups.clearProfile(localUserId, it.id) } }
            val editor = syncMetadata.edit()
            syncMetadata.all.keys.filter { it.startsWith("baseline:${current.userId}:") }.forEach { editor.remove(it) }
            editor.apply()
            profiles.resetForCloudAccountSwitch()
            sessionStore.clear()
            _account.value = null
            synced
        }
    }

    suspend fun synchronize(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock { syncLocked() }
    }

    private suspend fun syncLocked(): Int {
        var session = sessionStore.readOrNull() ?: return 0
        if (session.expiresAtMillis < System.currentTimeMillis() + 60_000L) {
            session = refresh(session)
            sessionStore.write(session)
            _account.value = session.toAccount()
        }
        profiles.ensureUserId()
        profiles.ensureId()
        val localUserId = profiles.ensureUserId()
        repeat(MAX_SYNC_RETRIES) { attempt ->
            val remote = fetchRemoteSnapshot(session)
            if (remote != null) mergeRemoteSnapshot(localUserId, remote)
            val transfer = exportCloudTransfer(localUserId)
            try {
                saveRemoteTransfer(session, transfer, remote)
                saveSyncBaselines(session.userId, transfer)
                return profiles.allProfiles().size
            } catch (conflict: CloudWriteConflict) {
                if (attempt == MAX_SYNC_RETRIES - 1) {
                    throw IllegalStateException("Bulut hesabı başka cihazda aynı anda değişti. Veriler korundu; biraz sonra tekrar eşitlenecek.", conflict)
                }
            }
        }
        error("Bulut eşitlemesi tamamlanamadı.")
    }

    private suspend fun mergeRemoteSnapshot(localUserId: String, snapshot: RemoteCloudSnapshot) {
        val remote = runCatching { profiles.decodeTransfer(snapshot.transferJson) }
            .getOrElse { throw IllegalStateException("Bulut yedeği okunamadı; yerel veriler korunuyor.", it) }
        val localProfiles = profiles.allProfiles()
        val currentProfileId = profiles.id.first()
        val localHasAnyLibraryData = localProfiles.any { local ->
            historyDao.all(localUserId, local.id).isNotEmpty() || favoriteDao.all(localUserId, local.id).isNotEmpty()
        }
        val isEmptySingleLocalProfile = localProfiles.size == 1 && !localHasAnyLibraryData
        if (isEmptySingleLocalProfile && remote.profiles.none { it.id == currentProfileId }) {
            profiles.resetProfilesForCloudImport()
        }
        val deviceProfiles = remote.profiles.sortedByDescending { it.isAdmin }.map { incoming ->
            val local = localProfiles.firstOrNull { it.id == incoming.id }
            incoming.copy(
                syncCode = local?.syncCode?.takeIf(String::isNotBlank) ?: (100000..999999).random().toString(),
                // Raw PIN hiçbir zaman buluta gitmez; yalnızca profildeki tuzlu PBKDF2 özeti paylaşılır.
                pinHash = incoming.pinHash ?: local?.pinHash,
                isHidden = local?.isHidden ?: false,
            )
        }
        val profilesOnly = LocalProfileTransfer(
            profiles = deviceProfiles,
            backups = emptyMap(),
            sources = emptyList(),
            warnings = emptyList(),
        )
        profiles.importTransfer(PlayJson.encodeToString(LocalProfileTransfer.serializer(), profilesOnly))
        // profileSetupComplete is local DataStore state, not part of the cloud snapshot. After
        // clearing app data it is false, so PlayRoot otherwise hides the successfully restored
        // profiles behind the first-run empty state even though importTransfer saved them.
        if (deviceProfiles.isNotEmpty()) profiles.completeProfileSetup()
        val profilesById = deviceProfiles.associateBy { it.id }
        remote.backups.forEach { (profileId, rawBackup) ->
            if (profileId !in profilesById) return@forEach
            val incoming = runCatching { PlayJson.decodeFromString(LocalProfileBackup.serializer(), rawBackup) }.getOrNull() ?: return@forEach
            if (incoming.profileId != profileId) return@forEach
            val localHistory = historyDao.all(localUserId, profileId).map { row ->
                HistoryBackup(row.tmdbId, row.type.name, row.season, row.episode, row.positionMs, row.durationMs, row.lastWatchedAt, row.title, row.posterPath, row.pluginId, row.dataUrl, row.contentUrl, row.manuallyMarkedWatched)
            }
            val localFavorites = favoriteDao.all(localUserId, profileId).map { row ->
                FavoriteBackup(row.tmdbId, row.type.name, row.title, row.posterPath, row.addedAt)
            }
            val baseline = readSyncBaseline(localUserId, profileId)
            val mergedHistory = mergeVersionedRows(
                baseline?.history,
                localHistory,
                incoming.history,
                key = ::historyKey,
                version = { it.lastWatchedAt },
            ).map { row ->
                val local = localHistory.firstOrNull { historyKey(it) == historyKey(row) }
                row.copy(
                    pluginId = row.pluginId ?: local?.pluginId,
                    dataUrl = row.dataUrl ?: local?.dataUrl,
                    contentUrl = row.contentUrl ?: local?.contentUrl,
                )
            }
            val mergedFavorites = mergeVersionedRows(
                baseline?.favorites,
                localFavorites,
                incoming.favorites,
                key = ::favoriteKey,
                version = { it.addedAt },
            )
            val mergedBackup = PlayJson.encodeToString(
                LocalProfileBackup.serializer(),
                incoming.copy(history = mergedHistory, favorites = mergedFavorites),
            )
            backups.mergeJsonFor(localUserId, profileId, mergedBackup, mergedHistory, mergedFavorites).getOrThrow()
        }
    }

    private fun <T> mergeVersionedRows(
        baseline: Map<String, Long>?,
        localRows: List<T>,
        remoteRows: List<T>,
        key: (T) -> String,
        version: (T) -> Long,
    ): List<T> {
        val local = localRows.associateBy(key)
        val remote = remoteRows.associateBy(key)
        if (baseline == null) {
            return (local.values + remote.values).groupBy(key).map { (_, rows) -> rows.maxBy(version) }
        }
        return (baseline.keys + local.keys + remote.keys).mapNotNull { itemKey ->
            val localRow = local[itemKey]
            val remoteRow = remote[itemKey]
            val baselineVersion = baseline[itemKey]
            val localChanged = localRow?.let(version) != baselineVersion
            val remoteChanged = remoteRow?.let(version) != baselineVersion
            when {
                localChanged -> localRow
                remoteChanged -> remoteRow
                else -> localRow ?: remoteRow
            }
        }
    }

    private fun historyKey(row: HistoryBackup) = "${row.tmdbId}:${row.type}:${row.season}:${row.episode}"
    private fun favoriteKey(row: FavoriteBackup) = "${row.tmdbId}:${row.type}"

    private fun baselineKey(userId: String, profileId: String) = "baseline:$userId:$profileId"

    private fun readSyncBaseline(userId: String, profileId: String): SyncBaseline? =
        syncMetadata.getString(baselineKey(userId, profileId), null)?.let { raw ->
            runCatching { PlayJson.decodeFromString(SyncBaseline.serializer(), raw) }.getOrNull()
        }

    private fun saveSyncBaselines(userId: String, transferJson: String) {
        val transfer = PlayJson.decodeFromString(LocalProfileTransfer.serializer(), transferJson)
        val editor = syncMetadata.edit()
        transfer.backups.forEach { (profileId, raw) ->
            val backup = PlayJson.decodeFromString(LocalProfileBackup.serializer(), raw)
            val baseline = SyncBaseline(
                history = backup.history.associate { historyKey(it) to it.lastWatchedAt },
                favorites = backup.favorites.associate { favoriteKey(it) to it.addedAt },
            )
            editor.putString(baselineKey(userId, profileId), PlayJson.encodeToString(SyncBaseline.serializer(), baseline))
        }
        editor.apply()
    }

    private suspend fun exportCloudTransfer(localUserId: String): String {
        val summaries = profiles.allProfiles()
        val cloudProfiles = summaries.map { it.copy(syncCode = "", isHidden = false) }
        val cloudBackups = linkedMapOf<String, String>()
        for (summary in summaries) {
            val history = historyDao.all(localUserId, summary.id)
                .sortedByDescending { it.lastWatchedAt }
                .map { row ->
                    HistoryBackup(
                        tmdbId = row.tmdbId,
                        type = row.type.name,
                        season = row.season,
                        episode = row.episode,
                        positionMs = row.positionMs,
                        durationMs = row.durationMs,
                        lastWatchedAt = row.lastWatchedAt,
                        title = row.title,
                        posterPath = row.posterPath,
                        // Akış/eklenti URL'leri buluta gönderilmez; cihaz yerelinde kalır.
                        pluginId = null,
                        dataUrl = null,
                        contentUrl = null,
                        manuallyMarkedWatched = row.manuallyMarkedWatched,
                    )
                }
            val favorites = favoriteDao.all(localUserId, summary.id)
                .sortedByDescending { it.addedAt }
                .map { FavoriteBackup(it.tmdbId, it.type.name, it.title, it.posterPath, it.addedAt) }
            cloudBackups[summary.id] = PlayJson.encodeToString(
                LocalProfileBackup.serializer(),
                LocalProfileBackup(
                    profileName = summary.name,
                    profileId = summary.id,
                    exportedAt = System.currentTimeMillis(),
                    history = history,
                    favorites = favorites,
                ),
            )
        }
        return PlayJson.encodeToString(
            LocalProfileTransfer.serializer(),
            LocalProfileTransfer(profiles = cloudProfiles, backups = cloudBackups),
        )
    }

    private suspend fun fetchRemoteSnapshot(session: CloudSession): RemoteCloudSnapshot? {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/play_cloud_state?select=payload,updated_at&user_id=eq.${session.userId}")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()
        return execute(request) { raw ->
            val rows = PlayJson.decodeFromString(JsonArray.serializer(), raw)
            val row = rows.firstOrNull()?.jsonObject ?: return@execute null
            val payload = row["payload"]?.jsonObject ?: return@execute null
            val transfer = payload["transfer"]?.toString() ?: return@execute null
            val updatedAt = row["updated_at"]?.jsonPrimitive?.contentOrNull ?: return@execute null
            RemoteCloudSnapshot(transfer, updatedAt)
        }
    }

    private suspend fun saveRemoteTransfer(session: CloudSession, transferJson: String, expected: RemoteCloudSnapshot?) {
        val transfer = PlayJson.parseToJsonElement(transferJson)
        val payload = buildJsonObject { put("version", 1); put("transfer", transfer) }
        val timestamp = java.time.Instant.now().toString()
        val body = buildJsonObject {
            if (expected == null) put("user_id", session.userId)
            put("payload", payload)
            put("updated_at", timestamp)
        }.toString()
        val base = "$baseUrl/rest/v1/play_cloud_state".toHttpUrl()
        val url = if (expected == null) base else base.newBuilder()
            .addQueryParameter("user_id", "eq.${session.userId}")
            .addQueryParameter("updated_at", "eq.${expected.updatedAt}")
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Prefer", if (expected == null) "return=minimal" else "return=representation")
        val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
        val request = if (expected == null) requestBuilder.post(requestBody).build() else requestBuilder.patch(requestBody).build()
        withContext(Dispatchers.IO) {
            http.newCall(request).await().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code == 409 || response.code == 412) throw CloudWriteConflict()
                if (!response.isSuccessful) throw IllegalStateException(friendlyError(raw, response.code))
                if (expected != null) {
                    val rows = runCatching { PlayJson.decodeFromString(JsonArray.serializer(), raw) }.getOrNull()
                    if (rows.isNullOrEmpty()) throw CloudWriteConflict()
                }
            }
        }
    }

    private suspend fun refresh(old: CloudSession): CloudSession {
        val response = authPost(
            endpoint = "auth/v1/token?grant_type=refresh_token",
            body = buildJsonObject { put("refresh_token", old.refreshToken) }.toString(),
        )
        return decodeAuth(response).toSession(old.email)
            ?: error("Oturum yenilenemedi. Lütfen yeniden giriş yapın.")
    }

    private fun decodeAuth(raw: String): AuthResponse = PlayJson.decodeFromString(AuthResponse.serializer(), raw)

    private fun AuthResponse.toSession(fallbackEmail: String): CloudSession? {
        val access = accessToken ?: return null
        val refresh = refreshToken ?: return null
        val id = user?.id ?: return null
        return CloudSession(
            accessToken = access,
            refreshToken = refresh,
            userId = id,
            email = user.email ?: fallbackEmail,
            expiresAtMillis = System.currentTimeMillis() + ((expiresIn ?: 3600L) * 1000L),
        )
    }

    private suspend fun authPost(endpoint: String, body: String, bearer: String? = null): String {
        val builder = Request.Builder()
            .url("$baseUrl/$endpoint")
            .header("apikey", publishableKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return execute(builder.build()) { it }
    }

    private suspend fun <T> execute(request: Request, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        http.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(friendlyError(raw, response.code))
            if (raw.isBlank()) {
                @Suppress("UNCHECKED_CAST")
                return@withContext (Unit as T)
            }
            parse(raw)
        }
    }

    private fun friendlyError(raw: String, status: Int): String {
        val fields = runCatching { PlayJson.parseToJsonElement(raw).jsonObject }.getOrNull()
        val message = sequenceOf("msg", "message", "error_description", "error")
            .mapNotNull { fields?.get(it)?.jsonPrimitive?.contentOrNull }
            .firstOrNull()
            .orEmpty()
        return when {
            message.contains("invalid login credentials", true) -> "E-posta veya şifre hatalı."
            message.contains("email not confirmed", true) -> "E-posta doğrulanmamış. Gelen kutunuzu kontrol edin."
            message.contains("user already registered", true) -> "Bu e-posta ile hesap zaten var; giriş yapın."
            message.contains("password should be at least", true) -> "Şifre Supabase güvenlik koşullarını karşılamıyor."
            message.isNotBlank() -> message
            else -> "Bulut bağlantı hatası (HTTP $status). İnternet bağlantınızı kontrol edip tekrar deneyin."
        }
    }

    private fun CloudSession.toAccount() = CloudAccountState(userId, email)

    private class EncryptedCloudSessionStore(context: Context) {
        private val prefs = context.getSharedPreferences("cloud_auth_secure", Context.MODE_PRIVATE)
        private val alias = "com.kaan.play.supabase.session.v1"

        @Synchronized
        fun readOrNull(): CloudSession? = runCatching {
            val encoded = prefs.getString(SESSION_KEY, null) ?: return null
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH)
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val ciphertext = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            PlayJson.decodeFromString(CloudSession.serializer(), cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
        }.getOrNull()

        @Synchronized
        fun write(session: CloudSession) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(PlayJson.encodeToString(CloudSession.serializer(), session).toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + encrypted
            prefs.edit().putString(SESSION_KEY, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
        }

        @Synchronized
        fun clear() {
            prefs.edit().remove(SESSION_KEY).apply()
        }

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(alias)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generator.generateKey()
            }
            return keyStore.getKey(alias, null) as SecretKey
        }

        companion object {
            private const val SESSION_KEY = "session_ciphertext"
            private const val IV_LENGTH = 12
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_SYNC_RETRIES = 4
    }
}
