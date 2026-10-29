package com.kaan.play.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import com.kaan.play.data.net.PlayJson
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import android.util.Base64

@Serializable
data class LocalProfileSummary(
    val id: String,
    val name: String,
    val syncCode: String,
    val avatar: String = "space_astro",
    val isChild: Boolean = false,
    val pinHash: String? = null,
    val isHidden: Boolean = false,
    val isAdmin: Boolean = false,
)

/** Harici hesap gerektirmeyen cihaz profili. Profil yalnızca bu cihazda tutulur. */
class LocalProfileRepository(private val store: DataStore<Preferences>) {
    val name: Flow<String> = store.data.map { it[NAME] ?: DEFAULT_NAME }
    val id: Flow<String> = store.data.map { it[ID] ?: "" }
    val syncCode: Flow<String> = store.data.map { it[SYNC_CODE] ?: "" }
    val peerHost: Flow<String> = store.data.map { it[PEER_HOST] ?: "" }
    val peerCode: Flow<String> = store.data.map { it[PEER_CODE] ?: "" }
    val peerPort: Flow<Int> = store.data.map { it[PEER_PORT] ?: 43821 }
    val profiles: Flow<List<LocalProfileSummary>> = store.data.map { decodeProfiles(it[PROFILES]) }
    val profileSetupComplete: Flow<Boolean> = store.data.map { it[PROFILE_SETUP_COMPLETE] ?: false }
    val lastActiveProfileId: Flow<String?> = store.data.map {
        it[LAST_ACTIVE_PROFILE_ID] ?: if (it[PROFILE_SETUP_COMPLETE] == true) it[ID]?.takeIf { id -> id.isNotBlank() } else null
    }
    val userId: Flow<String> = store.data.map { it[USER_ID] ?: "" }

    suspend fun ensureUserId(): String {
        var value = ""
        store.edit { prefs -> value = prefs[USER_ID] ?: UUID.randomUUID().toString(); prefs[USER_ID] = value }
        return value
    }

    suspend fun ensureId(): String {
        var value = ""
        store.edit { prefs ->
            val setupComplete = prefs[PROFILE_SETUP_COMPLETE] ?: false
            value = prefs[ID] ?: UUID.randomUUID().toString()
            prefs[ID] = value
            var current = decodeProfiles(prefs[PROFILES])
            if (setupComplete || current.isNotEmpty()) {
                if (current.none { it.isAdmin } && current.isNotEmpty()) current = current.toMutableList().also { it[0] = it[0].copy(isAdmin = true) }
                if (current.none { it.id == value }) {
                    val firstIsAdmin = current.none { it.isAdmin }
                    current = current + LocalProfileSummary(value, prefs[NAME] ?: DEFAULT_NAME, prefs[SYNC_CODE] ?: (100000..999999).random().toString(), isAdmin = firstIsAdmin)
                }
                prefs[PROFILES] = encodeProfiles(current)
            }
        }
        return value
    }

    suspend fun setName(value: String) {
        val next = value.trim().ifBlank { DEFAULT_NAME }
        store.edit { prefs ->
            prefs[NAME] = next
            prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == (prefs[ID] ?: "")) it.copy(name = next) else it })
        }
    }

    suspend fun updateActiveProfile(transform: (LocalProfileSummary) -> LocalProfileSummary) {
        store.edit { prefs ->
            val activeId = prefs[ID] ?: return@edit
            val next = decodeProfiles(prefs[PROFILES]).map { if (it.id == activeId) transform(it) else it }
            prefs[PROFILES] = encodeProfiles(next)
            next.firstOrNull { it.id == activeId }?.let {
                prefs[NAME] = it.name
                prefs[SYNC_CODE] = it.syncCode
            }
        }
    }

    suspend fun setAvatar(avatar: String) = updateActiveProfile { it.copy(avatar = avatar) }
    suspend fun setChildProfile(child: Boolean) = updateActiveProfile { it.copy(isChild = child) }
    suspend fun setPin(pin: String?) {
        require(pin == null || pin.matches(Regex("\\d{4,12}"))) { "PIN 4-12 rakam olmalı" }
        updateActiveProfile { it.copy(pinHash = pin?.let(::hashPin)) }
    }
    suspend fun activeProfile(): LocalProfileSummary? {
        val active = id.first()
        return profiles.first().firstOrNull { it.id == active }
    }
    suspend fun verifyPin(pin: String): Boolean = activeProfile()?.let { verifyPin(it, pin) } ?: false
    fun verifyPin(profile: LocalProfileSummary, pin: String): Boolean = verifyPinHash(profile.pinHash, pin)
    private fun verifyPinHash(encoded: String?, pin: String): Boolean {
        if (encoded == null) return false
        if (!encoded.startsWith("pbkdf2-sha256$")) return MessageDigest.isEqual(encoded.toByteArray(), MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }.toByteArray())
        return runCatching {
            val fields = encoded.split('$'); val iterations = fields[1].toInt(); val salt = Base64.decode(fields[2], Base64.NO_WRAP); val expected = Base64.decode(fields[3], Base64.NO_WRAP)
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded; spec.clearPassword()
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    suspend fun allProfiles(): List<LocalProfileSummary> = store.data.map { decodeProfiles(it[PROFILES]) }.first()

    suspend fun createProfile(
        name: String,
        avatar: String = "space_astro",
        isChild: Boolean = false,
        pin: String? = null,
    ): LocalProfileSummary {
        val all = allProfiles()
        require(all.isEmpty() || isActiveAdmin()) { "Yalnızca admin profil oluşturabilir" }
        val id = UUID.randomUUID().toString()
        val syncCode = (100000..999999).random().toString()
        val firstIsAdmin = all.none { it.isAdmin }
        val profile = LocalProfileSummary(
            id = id,
            name = name.trim().take(40).ifBlank { DEFAULT_NAME },
            syncCode = syncCode,
            avatar = avatar,
            isChild = isChild,
            pinHash = pin?.takeIf { it.isNotBlank() }?.let(::hashPin),
            isAdmin = firstIsAdmin,
        )
        store.edit { prefs ->
            prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]) + profile)
            prefs[ID] = id
            prefs[LAST_ACTIVE_PROFILE_ID] = id
            prefs[NAME] = profile.name
            prefs[SYNC_CODE] = syncCode
        }
        return profile
    }

    suspend fun isActiveAdmin(): Boolean {
        val all = profiles.first()
        if (all.isEmpty()) return true
        val activeId = id.first()
        return all.any { it.id == activeId && it.isAdmin }
    }

    suspend fun renameProfile(profileId: String, name: String) {
        require(isActiveAdmin()) { "Yalnızca admin profil düzenleyebilir" }
        val normalized = name.trim().take(40).ifBlank { DEFAULT_NAME }
        store.edit { prefs ->
            prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == profileId) it.copy(name = normalized) else it })
            if (prefs[ID] == profileId) prefs[NAME] = normalized
        }
    }

    suspend fun deleteProfile(profileId: String): Boolean {
        if (!isActiveAdmin()) return false
        val before = allProfiles()
        if (before.size <= 1 || before.none { it.id == profileId } || before.first { it.id == profileId }.isAdmin) return false
        store.edit { prefs ->
            val remaining = decodeProfiles(prefs[PROFILES]).filterNot { it.id == profileId }
            prefs[PROFILES] = encodeProfiles(remaining)
            val backups = decodeBackups(prefs[BACKUPS]).toMutableMap().apply { remove(profileId) }
            prefs[BACKUPS] = PlayJson.encodeToString(ProfileMapSerializer, backups)
            if (prefs[LAST_ACTIVE_PROFILE_ID] == profileId) {
                val next = remaining.firstOrNull()
                if (next != null) prefs[LAST_ACTIVE_PROFILE_ID] = next.id else prefs.remove(LAST_ACTIVE_PROFILE_ID)
            }
            if (prefs[ID] == profileId) {
                val next = remaining.first()
                prefs[ID] = next.id
                prefs[NAME] = next.name
                prefs[SYNC_CODE] = next.syncCode
            }
        }
        return true
    }

    suspend fun setProfileHidden(profileId: String, hidden: Boolean): Boolean {
        val current = allProfiles().firstOrNull { it.id == profileId } ?: return false
        if (!isActiveAdmin() || current.isAdmin || (hidden && current.pinHash.isNullOrBlank())) return false
        store.edit { prefs -> prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == profileId) it.copy(isHidden = hidden) else it }) }
        return true
    }

    suspend fun setProfilePin(profileId: String, pin: String?) {
        require(isActiveAdmin() || id.first() == profileId) { "Yalnızca admin başka profilin PIN’ini değiştirebilir" }
        require(pin == null || pin.matches(Regex("\\d{4,12}"))) { "PIN 4-12 rakam olmalı" }
        store.edit { prefs -> prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == profileId) it.copy(pinHash = pin?.let(::hashPin)) else it }) }
    }

    suspend fun setProfileAvatar(profileId: String, avatar: String) {
        require(isActiveAdmin() || id.first() == profileId) { "Yalnızca admin başka profilin avatarını değiştirebilir" }
        store.edit { prefs -> prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == profileId) it.copy(avatar = avatar) else it }) }
    }

    suspend fun setProfileChild(profileId: String, child: Boolean) {
        require(isActiveAdmin() || id.first() == profileId) { "Yalnızca admin başka profilin ayarını değiştirebilir" }
        store.edit { prefs -> prefs[PROFILES] = encodeProfiles(decodeProfiles(prefs[PROFILES]).map { if (it.id == profileId) it.copy(isChild = child) else it }) }
    }

    suspend fun setActiveProfile(profile: LocalProfileSummary) {
        store.edit {
            it[ID] = profile.id
            it[LAST_ACTIVE_PROFILE_ID] = profile.id
            it[NAME] = profile.name
            it[SYNC_CODE] = profile.syncCode
        }
    }

    suspend fun saveBackup(profileId: String, json: String) {
        store.edit { prefs ->
            val map = decodeBackups(prefs[BACKUPS]).toMutableMap()
            map[profileId] = json
            prefs[BACKUPS] = PlayJson.encodeToString(ProfileMapSerializer, map)
        }
    }

    suspend fun backup(profileId: String): String? = store.data.map { decodeBackups(it[BACKUPS])[profileId] }.first()

    suspend fun exportTransfer(sources: List<SourceTransferItem> = emptyList(), warnings: List<String> = emptyList()): String = store.data.map { prefs ->
        PlayJson.encodeToString(
            LocalProfileTransfer.serializer(),
            LocalProfileTransfer(decodeProfiles(prefs[PROFILES]), decodeBackups(prefs[BACKUPS]), sources, warnings),
        )
    }.first()

    /** TV’ye gelen tüm profilleri birleştirir; aynı kimlikteki profil ve yedek karşıya yazılır. */
    suspend fun importTransfer(raw: String): String? {
        val incoming = PlayJson.decodeFromString(LocalProfileTransfer.serializer(), raw)
        var activeId = ""
        store.edit { prefs ->
            val existing = decodeProfiles(prefs[PROFILES]).associateBy { it.id }
            val merged = (existing.values + incoming.profiles).associateBy { it.id }.values.toList()
            val backups = decodeBackups(prefs[BACKUPS]).toMutableMap()
            incoming.backups.forEach { (profileId, incomingBackup) ->
                val localBackup = backups[profileId]
                backups[profileId] = if (localBackup == null) incomingBackup else mergeProfileBackups(localBackup, incomingBackup)
            }
            prefs[PROFILES] = encodeProfiles(merged)
            prefs[BACKUPS] = PlayJson.encodeToString(ProfileMapSerializer, backups)
            activeId = prefs[ID] ?: merged.firstOrNull()?.id.orEmpty()
            merged.firstOrNull { it.id == activeId }?.let {
                prefs[ID] = it.id
                prefs[NAME] = it.name
                prefs[SYNC_CODE] = it.syncCode
            }
        }
        return backup(activeId)
    }

    /** Aynı profile ait iki cihaz yedeğindeki farklı izleme/favori kayıtlarını kaybetmeden birleştirir. */
    private fun mergeProfileBackups(localRaw: String, incomingRaw: String): String = runCatching {
        val serializer = LocalProfileBackup.serializer()
        val local = PlayJson.decodeFromString(serializer, localRaw)
        val incoming = PlayJson.decodeFromString(serializer, incomingRaw)
        if (local.profileId != incoming.profileId) return@runCatching incomingRaw
        val histories = (local.history + incoming.history)
            .groupBy { "${it.tmdbId}:${it.type}:${it.season}:${it.episode}" }
            .map { (_, rows) -> rows.maxBy { it.lastWatchedAt } }
        val favorites = (local.favorites + incoming.favorites)
            .groupBy { "${it.tmdbId}:${it.type}" }
            .map { (_, rows) -> rows.maxBy { it.addedAt } }
        val newest = if (incoming.exportedAt >= local.exportedAt) incoming else local
        PlayJson.encodeToString(serializer, newest.copy(
            exportedAt = maxOf(local.exportedAt, incoming.exportedAt),
            history = histories,
            favorites = favorites,
        ))
    }.getOrDefault(incomingRaw)

    fun decodeTransfer(raw: String): LocalProfileTransfer = PlayJson.decodeFromString(LocalProfileTransfer.serializer(), raw)

    suspend fun ensureSyncCode(): String {
        var value = ""
        store.edit { prefs ->
            value = prefs[SYNC_CODE] ?: (100000..999999).random().toString()
            prefs[SYNC_CODE] = value
        }
        return value
    }

    suspend fun setPeer(host: String, code: String, port: Int = 43821) {
        store.edit {
            it[PEER_HOST] = host.trim()
            it[PEER_CODE] = code.trim()
            it[PEER_PORT] = port
        }
    }

    suspend fun clearPeer() {
        store.edit {
            it.remove(PEER_HOST)
            it.remove(PEER_CODE)
            it.remove(PEER_PORT)
        }
    }

    /** Sign-out sonrası başka bir bulut hesabıyla karışmayı önlemek için cihazdaki profil alanını sıfırlar. */
    suspend fun resetForCloudAccountSwitch() {
        store.edit { prefs ->
            prefs.remove(ID)
            prefs.remove(LAST_ACTIVE_PROFILE_ID)
            prefs.remove(NAME)
            prefs.remove(SYNC_CODE)
            prefs.remove(PEER_HOST)
            prefs.remove(PEER_CODE)
            prefs.remove(PEER_PORT)
            prefs.remove(PROFILES)
            prefs.remove(BACKUPS)
            prefs.remove(USER_ID)
            prefs.remove(PROFILE_SETUP_COMPLETE)
        }
    }

    /** Boş ilk kurulum profilini siler; yeni cihaz mevcut bulut profilini aktif seçebilir. */
    suspend fun resetProfilesForCloudImport() {
        store.edit { prefs ->
            prefs.remove(ID)
            prefs.remove(LAST_ACTIVE_PROFILE_ID)
            prefs.remove(NAME)
            prefs.remove(SYNC_CODE)
            prefs.remove(PROFILES)
            prefs.remove(BACKUPS)
        }
    }

    suspend fun completeProfileSetup(profileId: String? = null) {
        store.edit { prefs ->
            prefs[PROFILE_SETUP_COMPLETE] = true
            val targetId = profileId ?: prefs[ID]
            if (!targetId.isNullOrBlank()) {
                prefs[LAST_ACTIVE_PROFILE_ID] = targetId
            }
        }
    }

    companion object {
        val LAST_ACTIVE_PROFILE_ID = stringPreferencesKey("last_active_profile_id")
        private val ID = stringPreferencesKey("local_profile_id")
        private val NAME = stringPreferencesKey("local_profile_name")
        private val SYNC_CODE = stringPreferencesKey("local_sync_code")
        private val PEER_HOST = stringPreferencesKey("local_sync_peer_host")
        private val PEER_CODE = stringPreferencesKey("local_sync_peer_code")
        private val PEER_PORT = androidx.datastore.preferences.core.intPreferencesKey("local_sync_peer_port")
        private val PROFILES = stringPreferencesKey("local_profiles")
        private val BACKUPS = stringPreferencesKey("local_profile_backups")
        private val PROFILE_SETUP_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey("profile_setup_complete")
        private val SummaryListSerializer = ListSerializer(LocalProfileSummary.serializer())
        private val ProfileMapSerializer = MapSerializer(String.serializer(), String.serializer())

        private fun decodeProfiles(raw: String?): List<LocalProfileSummary> = raw?.let { runCatching { PlayJson.decodeFromString(SummaryListSerializer, it) }.getOrNull() }.orEmpty()
        private fun encodeProfiles(value: List<LocalProfileSummary>): String = PlayJson.encodeToString(SummaryListSerializer, value)
        private fun decodeBackups(raw: String?): Map<String, String> = raw?.let { runCatching { PlayJson.decodeFromString(ProfileMapSerializer, it) }.getOrNull() }.orEmpty()
        private fun hashPin(pin: String): String {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            return "pbkdf2-sha256\$210000\$${Base64.encodeToString(salt, Base64.NO_WRAP)}\$${Base64.encodeToString(derived, Base64.NO_WRAP)}"
        }
        private val USER_ID = stringPreferencesKey("local_user_id")
        const val DEFAULT_NAME = "Play Kullanıcısı"
    }
}
