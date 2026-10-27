package com.kaan.play.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.WatchHistoryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SupabaseCloudSync(
    private val appContext: Context,
    private val profiles: LocalProfileRepository,
    private val backups: LocalSyncBackup,
    private val historyDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val projectUrl: String = "",
    private val publishableKey: String = "",
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessionStore = EncryptedCloudSessionStore(appContext)
    private val syncMetadata: SharedPreferences =
        appContext.getSharedPreferences("supabase_sync_metadata", Context.MODE_PRIVATE)
    private val syncMutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _account = MutableStateFlow<CloudAccountState?>(sessionStore.load()?.let { toAccount(it) })
    val account: StateFlow<CloudAccountState?> = _account.asStateFlow()

    private val baseUrl: String
        get() = projectUrl.trimEnd('/')

    private fun isConfigured(): Boolean = projectUrl.isNotBlank() && publishableKey.isNotBlank()

    private fun toAccount(session: CloudSession): CloudAccountState =
        CloudAccountState(email = session.email, userId = session.userId)

    suspend fun createAccount(email: String, pass: String): Result<CloudRegistrationResult> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase altyapısı yapılandırılmamış."))
            runCatching {
            val body = buildJsonObject {
                put("email", email.trim())
                put("password", pass)
            }.toString()

            val res = authPost("auth/v1/signup", body)
            val auth = decodeAuth(res)
            if (auth.accessToken != null && auth.user != null) {
                val session = toSession(auth, email.trim())
                sessionStore.save(session)
                _account.value = toAccount(session)
                CloudRegistrationResult.SIGNED_IN
            } else {
                CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED
            }
        }
    }

    suspend fun signIn(email: String, pass: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase altyapısı yapılandırılmamış."))
            runCatching {
                val body = buildJsonObject {
                    put("email", email.trim())
                    put("password", pass)
                }.toString()

                val res = authPost("auth/v1/token?grant_type=password", body)
                val auth = decodeAuth(res)
                if (auth.accessToken.isNullOrEmpty() || auth.user == null) {
                    val msg = auth.message ?: auth.msg ?: "Giriş yapılamadı"
                    throw IllegalStateException(friendlyError(msg, 400))
                }
                val session = toSession(auth, email.trim())
                sessionStore.save(session)
                _account.value = toAccount(session)
                // Giriş sonrası ilk senkron
                synchronize()
                Unit
            }
        }

    suspend fun signOut(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load()
                if (session != null && isConfigured()) {
                    runCatching {
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/logout")
                            .header("apikey", publishableKey)
                            .header("Authorization", "Bearer ${session.accessToken}")
                            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                        http.newCall(req).execute().close()
                    }
                }
                sessionStore.clear()
                syncMetadata.edit().clear().apply()
                _account.value = null
            }
        }

    suspend fun synchronize(): Result<Unit> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.success(Unit)
            runCatching {
            var session = sessionStore.load() ?: return@runCatching
            if (System.currentTimeMillis() >= session.expiresAtMillis - 60_000L) {
                session = refresh(session) ?: throw IllegalStateException("Oturum süresi doldu, lütfen tekrar giriş yapın.")
            }

            // 1. Uzak durumu oku
            val remote = fetchRemoteSnapshot(session)

            // 2. Birleştir
            val transfer = exportCloudTransfer(session.userId)
            val transferJson = json.encodeToString(LocalProfileTransfer.serializer(), transfer)

            if (remote != null) {
                mergeRemoteSnapshot(session.userId, remote)
            }

            // 3. Uzak veritabanına yaz
            saveRemoteTransfer(session, transferJson, remote)
        }
        }
    }

    private suspend fun fetchRemoteSnapshot(session: CloudSession): RemoteCloudSnapshot? {
        val url = "$baseUrl/rest/v1/play_cloud_state?select=payload,updated_at&user_id=eq.${session.userId}"
        val req = Request.Builder()
            .url(url)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()

        return execute(req) { bodyStr ->
            val arr = json.parseToJsonElement(bodyStr).jsonArray
            if (arr.isEmpty()) return@execute null
            val obj = arr[0].jsonObject
            val payload = obj["payload"]?.toString() ?: ""
            val updatedAt = obj["updated_at"]?.jsonPrimitive?.content ?: ""
            RemoteCloudSnapshot(transferJson = payload, updatedAt = updatedAt)
        }
    }

    private suspend fun saveRemoteTransfer(
        session: CloudSession,
        transferJson: String,
        currentRemote: RemoteCloudSnapshot?,
    ) {
        val nowUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val payloadElement = json.parseToJsonElement(transferJson)
        val bodyObj = buildJsonObject {
            put("user_id", session.userId)
            put("payload", payloadElement)
            put("updated_at", nowUtc)
        }

        val req = Request.Builder()
            .url("$baseUrl/rest/v1/play_cloud_state")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Prefer", "resolution=merge-duplicates")
            .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        execute(req) { "" }
    }

    private suspend fun mergeRemoteSnapshot(userId: String, remote: RemoteCloudSnapshot) {
        if (remote.transferJson.isBlank()) return
        val remoteTransfer = runCatching {
            json.decodeFromString(LocalProfileTransfer.serializer(), remote.transferJson)
        }.getOrNull() ?: return

        // Profilleri birleştir
        val localProfiles = profiles.allProfiles()
        val profileMap = localProfiles.associateBy { it.id }.toMutableMap()
        for (rp in remoteTransfer.profiles) {
            val local = profileMap[rp.id]
            if (local == null) {
                profileMap[rp.id] = rp
            }
        }
        profiles.resetProfilesForCloudImport(profileMap.values.toList())

        // Geçmiş ve favorileri profil bazında birleştir
        for ((pId, bkp) in remoteTransfer.backups) {
            val bkpJson = bkp
            backups.mergeJsonFor(userId, pId, bkpJson)
        }
    }

    private suspend fun exportCloudTransfer(userId: String): LocalProfileTransfer =
        profiles.exportTransfer()

    private suspend fun refresh(session: CloudSession): CloudSession? {
        val body = buildJsonObject {
            put("refresh_token", session.refreshToken)
        }.toString()

        return runCatching {
            val res = authPost("auth/v1/token?grant_type=refresh_token", body)
            val auth = decodeAuth(res)
            val newSession = toSession(auth, session.email)
            sessionStore.save(newSession)
            _account.value = toAccount(newSession)
            newSession
        }.getOrNull()
    }

    private suspend fun authPost(path: String, bodyJson: String): String {
        val req = Request.Builder()
            .url("$baseUrl/$path")
            .header("apikey", publishableKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return execute(req) { it }
    }

    private suspend fun <T> execute(req: Request, parser: (String) -> T): T =
        withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (!res.isSuccessful) {
                    val msg = friendlyError(body, res.code)
                    throw IllegalStateException(msg)
                }
                parser(body)
            }
        }

    private fun decodeAuth(body: String): AuthResponse =
        json.decodeFromString(AuthResponse.serializer(), body)

    private fun toSession(auth: AuthResponse, email: String): CloudSession {
        val token = auth.accessToken ?: ""
        val refresh = auth.refreshToken ?: ""
        val expiresAt = System.currentTimeMillis() + ((auth.expiresIn ?: 3600L) * 1000L)
        val uid = auth.user?.id ?: ""
        return CloudSession(
            userId = uid,
            email = email,
            accessToken = token,
            refreshToken = refresh,
            expiresAtMillis = expiresAt,
        )
    }

    private fun friendlyError(raw: String, code: Int): String {
        val parsed = runCatching { json.decodeFromString(JsonObject.serializer(), raw) }.getOrNull()
        val errorDesc = parsed?.get("error_description")?.jsonPrimitive?.content
        val msg = parsed?.get("msg")?.jsonPrimitive?.content
            ?: parsed?.get("message")?.jsonPrimitive?.content
            ?: errorDesc
        return when {
            msg?.contains("Invalid login credentials", ignoreCase = true) == true -> "Geçersiz e-posta veya şifre."
            msg?.contains("User already registered", ignoreCase = true) == true -> "Bu e-posta adresi zaten kayıtlı."
            msg?.contains("Email not confirmed", ignoreCase = true) == true -> "E-posta henüz onaylanmamış. Lütfen gelen kutunuzu kontrol edin veya Supabase panelinden 'Confirm email' kapatın."
            msg?.contains("Password should be at least", ignoreCase = true) == true -> "Şifre en az 6 karakter olmalıdır."
            msg?.contains("rate limit", ignoreCase = true) == true || code == 429 -> "Çok fazla e-posta isteği yapıldı, lütfen biraz bekleyin."
            !msg.isNullOrBlank() -> msg
            else -> "Bağlantı hatası ($code)"
        }
    }

    private class EncryptedCloudSessionStore(context: Context) {
        private val prefs = context.getSharedPreferences("cloud_auth_secure", Context.MODE_PRIVATE)
        private val alias = "com.kaan.play.supabase.session.v1"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): CloudSession? {
            val encoded = prefs.getString(SESSION_KEY, null) ?: return null
            return runCatching {
                val full = Base64.decode(encoded, Base64.NO_WRAP)
                if (full.size <= IV_LENGTH) return null
                val iv = full.copyOfRange(0, IV_LENGTH)
                val cipherBytes = full.copyOfRange(IV_LENGTH, full.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val key = getOrCreateSecretKey()
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val plain = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
                json.decodeFromString(CloudSession.serializer(), plain)
            }.getOrNull()
        }

        fun save(session: CloudSession) {
            runCatching {
                val plain = json.encodeToString(CloudSession.serializer(), session)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val key = getOrCreateSecretKey()
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
                val full = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, full, 0, iv.size)
                System.arraycopy(cipherBytes, 0, full, iv.size, cipherBytes.size)
                val encoded = Base64.encodeToString(full, Base64.NO_WRAP)
                prefs.edit().putString(SESSION_KEY, encoded).apply()
            }
        }

        fun clear() {
            prefs.edit().remove(SESSION_KEY).apply()
        }

        private fun getOrCreateSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }

        companion object {
            private const val SESSION_KEY = "session_ciphertext"
            private const val IV_LENGTH = 12
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
