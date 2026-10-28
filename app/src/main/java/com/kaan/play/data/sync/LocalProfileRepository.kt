package com.kaan.play.data.sync

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LocalProfileRepository(val store: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val id: Flow<String> = store.data.map { it[ID] ?: "" }.distinctUntilChanged()
    val name: Flow<String> = store.data.map { it[NAME] ?: DEFAULT_NAME }.distinctUntilChanged()
    val userId: Flow<String> = store.data.map { it[USER_ID] ?: "" }.distinctUntilChanged()
    val syncCode: Flow<String> = store.data.map { it[SYNC_CODE] ?: "" }.distinctUntilChanged()
    val profileSetupComplete: Flow<Boolean> = store.data.map { it[PROFILE_SETUP_COMPLETE] ?: false }.distinctUntilChanged()

    val peerHost: Flow<String?> = store.data.map { it[PEER_HOST] }.distinctUntilChanged()
    val peerPort: Flow<Int?> = store.data.map { it[PEER_PORT] }.distinctUntilChanged()
    val peerCode: Flow<String?> = store.data.map { it[PEER_CODE] }.distinctUntilChanged()

    val profiles: Flow<List<LocalProfileSummary>> = store.data.map { prefs ->
        val raw = prefs[PROFILES] ?: ""
        decodeProfiles(raw)
    }.distinctUntilChanged()

    val activeProfile: Flow<LocalProfileSummary?> = store.data.map { prefs ->
        val curId = prefs[ID]
        val list = decodeProfiles(prefs[PROFILES] ?: "")
        if (curId.isNullOrEmpty()) list.firstOrNull() else list.firstOrNull { it.id == curId } ?: list.firstOrNull()
    }.distinctUntilChanged()

    suspend fun allProfiles(): List<LocalProfileSummary> = profiles.first()

    suspend fun activeProfile(): LocalProfileSummary? = activeProfile.firstOrNull()

    suspend fun ensureUserId(): String {
        val current = userId.first()
        if (current.isNotEmpty()) return current
        val newId = UUID.randomUUID().toString()
        store.edit { it[USER_ID] = newId }
        return newId
    }

    suspend fun ensureId(): String {
        val current = id.first()
        if (current.isNotEmpty()) return current
        val list = allProfiles()
        if (list.isNotEmpty()) {
            val first = list.first()
            setActiveProfile(first)
            return first.id
        }
        val defaultProfile = createProfile(DEFAULT_NAME)
        setActiveProfile(defaultProfile)
        return defaultProfile.id
    }

    suspend fun ensureSyncCode(): String {
        val current = syncCode.first()
        if (current.isNotEmpty()) return current
        val allowed = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { allowed.random() }.joinToString("")
        store.edit { it[SYNC_CODE] = code }
        return code
    }

    suspend fun completeProfileSetup() {
        store.edit { it[PROFILE_SETUP_COMPLETE] = true }
    }

    suspend fun createProfile(
        name: String,
        avatar: String = "aurora",
        isChild: Boolean = false,
        pin: String? = null,
    ): LocalProfileSummary {
        val newId = UUID.randomUUID().toString()
        val pinHash = pin?.takeIf { it.isNotEmpty() }?.let { hashPin(it) }
        val newProfile = LocalProfileSummary(
            id = newId,
            name = name,
            avatar = avatar,
            isChild = isChild,
            pinHash = pinHash,
        )
        store.edit { prefs ->
            val list = decodeProfiles(prefs[PROFILES] ?: "").toMutableList()
            list.add(newProfile)
            prefs[PROFILES] = encodeProfiles(list)
            if (prefs[ID].isNullOrEmpty()) {
                prefs[ID] = newProfile.id
                prefs[NAME] = newProfile.name
            }
        }
        return newProfile
    }

    suspend fun setActiveProfile(profile: LocalProfileSummary) {
        store.edit { prefs ->
            prefs[ID] = profile.id
            prefs[NAME] = profile.name
        }
    }

    suspend fun selectProfile(profileId: String) {
        allProfiles().firstOrNull { it.id == profileId }?.let { setActiveProfile(it) }
    }

    suspend fun setHidden(profileId: String, isHidden: Boolean) {
        setProfileHidden(profileId, isHidden)
    }

    suspend fun updateProfile(
        profileId: String,
        name: String,
        avatar: String,
        isChild: Boolean,
        pin: String?,
    ) {
        updateProfile(profileId) { current ->
            current.copy(
                name = name,
                avatar = avatar,
                isChild = isChild,
                pinHash = if (pin != null) (if (pin.isBlank()) null else hashPin(pin)) else current.pinHash,
            )
        }
    }

    suspend fun verifyPin(profileId: String, pin: String): Boolean {
        val prof = allProfiles().firstOrNull { it.id == profileId } ?: return false
        return verifyPin(prof, pin)
    }

    suspend fun deleteProfile(profileId: String) {
        store.edit { prefs ->
            val list = decodeProfiles(prefs[PROFILES] ?: "").filterNot { it.id == profileId }
            prefs[PROFILES] = encodeProfiles(list)
            if (prefs[ID] == profileId) {
                val next = list.firstOrNull()
                if (next != null) {
                    prefs[ID] = next.id
                    prefs[NAME] = next.name
                } else {
                    prefs.remove(ID)
                    prefs.remove(NAME)
                }
            }
        }
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        updateProfile(profileId) { it.copy(name = newName) }
        if (id.first() == profileId) {
            store.edit { it[NAME] = newName }
        }
    }

    suspend fun setProfileAvatar(profileId: String, avatar: String) {
        updateProfile(profileId) { it.copy(avatar = avatar) }
    }

    suspend fun setProfileChild(profileId: String, isChild: Boolean) {
        updateProfile(profileId) { it.copy(isChild = isChild) }
    }

    suspend fun setProfileHidden(profileId: String, isHidden: Boolean) {
        updateProfile(profileId) { it.copy(isHidden = isHidden) }
    }

    suspend fun setProfilePin(profileId: String, pin: String?) {
        val hash = pin?.takeIf { it.isNotEmpty() }?.let { hashPin(it) }
        updateProfile(profileId) { it.copy(pinHash = hash) }
    }

    suspend fun setName(name: String) {
        val curId = id.first()
        if (curId.isNotEmpty()) renameProfile(curId, name)
    }

    suspend fun setAvatar(avatar: String) {
        val curId = id.first()
        if (curId.isNotEmpty()) setProfileAvatar(curId, avatar)
    }

    suspend fun setChildProfile(isChild: Boolean) {
        val curId = id.first()
        if (curId.isNotEmpty()) setProfileChild(curId, isChild)
    }

    suspend fun setPin(pin: String?) {
        val curId = id.first()
        if (curId.isNotEmpty()) setProfilePin(curId, pin)
    }

    suspend fun updateActiveProfile(transform: (LocalProfileSummary) -> LocalProfileSummary) {
        val curId = id.first()
        if (curId.isNotEmpty()) updateProfile(curId, transform)
    }

    private suspend fun updateProfile(profileId: String, transform: (LocalProfileSummary) -> LocalProfileSummary) {
        store.edit { prefs ->
            val list = decodeProfiles(prefs[PROFILES] ?: "").map {
                if (it.id == profileId) transform(it) else it
            }
            prefs[PROFILES] = encodeProfiles(list)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val cur = activeProfile() ?: return true
        return verifyPin(cur, pin)
    }

    fun verifyPin(profile: LocalProfileSummary, pin: String): Boolean {
        val hash = profile.pinHash ?: return true
        return verifyPinHash(hash, pin)
    }

    suspend fun isActiveAdmin(): Boolean {
        val cur = activeProfile() ?: return true
        return !cur.isChild && cur.pinHash == null
    }

    suspend fun setPeer(host: String, code: String, port: Int = 8080) {
        store.edit {
            it[PEER_HOST] = host
            it[PEER_CODE] = code
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

    suspend fun backup(profileId: String): String? {
        val raw = store.data.first()[BACKUPS] ?: return null
        val map = decodeBackups(raw)
        return map[profileId]
    }

    suspend fun saveBackup(profileId: String, backupJson: String) {
        store.edit { prefs ->
            val map = decodeBackups(prefs[BACKUPS] ?: "").toMutableMap()
            map[profileId] = backupJson
            prefs[BACKUPS] = encodeBackups(map)
        }
    }

    fun mergeProfileBackups(existing: String, incoming: String): String {
        val existingMap = decodeBackups(existing).toMutableMap()
        val incomingMap = decodeBackups(incoming)
        for ((k, v) in incomingMap) {
            existingMap[k] = v
        }
        return encodeBackups(existingMap)
    }

    suspend fun exportTransfer(
        extraSources: List<SourceTransferItem> = emptyList(),
        extraPlugins: List<PluginStateTransfer> = emptyList(),
    ): LocalProfileTransfer {
        val profs = allProfiles()
        val rawBackups = store.data.first()[BACKUPS] ?: ""
        val backupsMap = decodeBackups(rawBackups)
        return LocalProfileTransfer(
            profiles = profs,
            backups = backupsMap,
            sources = extraSources,
            warnings = emptyList(),
        )
    }

    suspend fun importTransfer(jsonStr: String): Boolean = runCatching {
        val transfer = decodeTransfer(jsonStr)
        store.edit { prefs ->
            prefs[PROFILES] = encodeProfiles(transfer.profiles)
            prefs[BACKUPS] = encodeBackups(transfer.backups)
            val first = transfer.profiles.firstOrNull()
            if (first != null) {
                prefs[ID] = first.id
                prefs[NAME] = first.name
            }
            prefs[PROFILE_SETUP_COMPLETE] = true
        }
        true
    }.getOrDefault(false)

    fun decodeTransfer(jsonStr: String): LocalProfileTransfer =
        json.decodeFromString(LocalProfileTransfer.serializer(), jsonStr)

    suspend fun resetForCloudAccountSwitch() {
        store.edit {
            it.remove(ID)
            it.remove(NAME)
            it.remove(PROFILES)
            it.remove(BACKUPS)
            it.remove(USER_ID)
            it.remove(PROFILE_SETUP_COMPLETE)
        }
    }

    suspend fun resetProfilesForCloudImport(newProfiles: List<LocalProfileSummary>) {
        store.edit { prefs ->
            prefs[PROFILES] = encodeProfiles(newProfiles)
            val first = newProfiles.firstOrNull()
            if (first != null) {
                prefs[ID] = first.id
                prefs[NAME] = first.name
            }
            prefs[PROFILE_SETUP_COMPLETE] = true
        }
    }

    /** Wi-Fi senkronizasyonu: profil listesini değiştirir, etkin profil (varsa) değişmez. */
    suspend fun replaceProfilesKeepingActive(newProfiles: List<LocalProfileSummary>) {
        store.edit { prefs ->
            prefs[PROFILES] = encodeProfiles(newProfiles)
            val active = newProfiles.firstOrNull { it.id == prefs[ID] }
            if (active != null) {
                prefs[NAME] = active.name
            } else {
                newProfiles.firstOrNull()?.let {
                    prefs[ID] = it.id
                    prefs[NAME] = it.name
                }
            }
        }
    }

    private fun decodeProfiles(raw: String): List<LocalProfileSummary> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(SummaryListSerializer, raw)
        }.getOrDefault(emptyList())
    }

    private fun encodeProfiles(list: List<LocalProfileSummary>): String =
        json.encodeToString(SummaryListSerializer, list)

    private fun decodeBackups(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(ProfileMapSerializer, raw)
        }.getOrDefault(emptyMap())
    }

    private fun encodeBackups(map: Map<String, String>): String =
        json.encodeToString(ProfileMapSerializer, map)

    private fun verifyPinHash(encoded: String, pin: String): Boolean {
        if (encoded.isBlank() || pin.isBlank()) return false
        if (encoded.startsWith("pbkdf2-sha256$")) {
            return runCatching {
                val parts = encoded.split("$")
                val iterations = parts[1].toInt()
                val salt = Base64.decode(parts[2], Base64.NO_WRAP)
                val expected = Base64.decode(parts[3], Base64.NO_WRAP)
                val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val actual = skf.generateSecret(spec).encoded
                spec.clearPassword()
                actual.contentEquals(expected)
            }.getOrDefault(false)
        } else {
            val md = MessageDigest.getInstance("SHA-256")
            val actual = md.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            return actual.equals(encoded, ignoreCase = true)
        }
    }

    companion object {
        const val DEFAULT_NAME = "Varsayılan"
        private val ID = stringPreferencesKey("sync_local_profile_id")
        private val NAME = stringPreferencesKey("sync_local_profile_name")
        private val PROFILES = stringPreferencesKey("sync_local_profiles")
        private val BACKUPS = stringPreferencesKey("sync_local_profile_backups")
        private val USER_ID = stringPreferencesKey("sync_local_user_id")
        private val SYNC_CODE = stringPreferencesKey("sync_local_code")
        private val PROFILE_SETUP_COMPLETE = booleanPreferencesKey("sync_profile_setup_complete")
        private val PEER_HOST = stringPreferencesKey("sync_local_peer_host")
        private val PEER_PORT = intPreferencesKey("sync_local_peer_port")
        private val PEER_CODE = stringPreferencesKey("sync_local_peer_code")

        private val SummaryListSerializer = ListSerializer(LocalProfileSummary.serializer())
        private val ProfileMapSerializer = MapSerializer(String.serializer(), String.serializer())

        fun hashPin(pin: String): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val spec = PBEKeySpec(pin.toCharArray(), salt, 210000, 256)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derived = skf.generateSecret(spec).encoded
            spec.clearPassword()
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val hashB64 = Base64.encodeToString(derived, Base64.NO_WRAP)
            return "pbkdf2-sha256$210000$$saltB64$$hashB64"
        }
    }
}
