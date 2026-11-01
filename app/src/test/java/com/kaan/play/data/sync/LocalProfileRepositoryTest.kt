package com.kaan.play.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class LocalProfileRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun h(tmdb: Int, at: Long) =
        HistoryBackup(tmdb, "movie", 0, 0, 1000, 10000, at, "H$tmdb", null)

    private fun f(tmdb: Int, at: Long) =
        FavoriteBackup(tmdb, "movie", "F$tmdb", null, at)

    @Test
    fun ensureIdCreatesDefaultWithFixedIdWhenEmpty() = runTest {
        val store = MemoryPreferencesStore()
        val repo = LocalProfileRepository(store)

        val id = repo.ensureId()
        assertEquals(LocalProfileRepository.DEFAULT_ID, id)
        assertEquals("default", id)

        val all = repo.allProfiles()
        assertEquals(1, all.size)
        assertEquals("default", all.first().id)
        assertEquals(LocalProfileRepository.DEFAULT_NAME, all.first().name)
        assertFalse(all.first().hasPin)

        assertEquals("default", repo.id.first())
        assertEquals("Varsayılan", repo.name.first())
    }

    @Test
    fun consolidateDefaultProfilesCombinesMultipleDefaultsAndRemapsActive() = runTest {
        val store = MemoryPreferencesStore()
        val repo = LocalProfileRepository(store)

        val p1 = LocalProfileSummary("uuid-1", "Varsayılan")
        val p2 = LocalProfileSummary("uuid-2", "Varsayılan")
        val p3 = LocalProfileSummary("uuid-3", "Varsayılan")

        val b1 = LocalProfileBackup(
            profileId = "uuid-1",
            favorites = listOf(f(1, 100)),
            history = listOf(h(10, 100)),
        )
        val b2 = LocalProfileBackup(
            profileId = "uuid-2",
            favorites = listOf(f(2, 200)),
            history = listOf(h(20, 200)),
        )
        val b3 = LocalProfileBackup(
            profileId = "uuid-3",
            favorites = listOf(f(3, 300)),
            history = listOf(h(30, 300)),
        )

        val profilesKey = stringPreferencesKey("sync_local_profiles")
        val backupsKey = stringPreferencesKey("sync_local_profile_backups")
        val idKey = stringPreferencesKey("sync_local_profile_id")
        val nameKey = stringPreferencesKey("sync_local_profile_name")

        store.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(ListSerializer(LocalProfileSummary.serializer()), listOf(p1, p2, p3))
            val backupsMap = mapOf(
                "uuid-1" to json.encodeToString(LocalProfileBackup.serializer(), b1),
                "uuid-2" to json.encodeToString(LocalProfileBackup.serializer(), b2),
                "uuid-3" to json.encodeToString(LocalProfileBackup.serializer(), b3),
            )
            prefs[backupsKey] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), backupsMap)
            prefs[idKey] = "uuid-3" // Aktif profil atılacak olan uuid-3
            prefs[nameKey] = "Varsayılan"
        }

        val result = repo.consolidateDefaultProfiles()
        assertNotNull(result)
        assertEquals(1, result!!.profiles.size)
        assertEquals("uuid-1", result.profiles.single().id)

        // Aktif profil korunan uuid-1'e geçmeli
        assertEquals("uuid-1", result.activeProfileId)
        assertEquals("uuid-1", repo.id.first())

        // DataStore'daki profil listesi tek profile inmeli
        val all = repo.allProfiles()
        assertEquals(1, all.size)
        assertEquals("uuid-1", all.single().id)

        // DataStore'daki yedek birleşmiş olmalı: 3 favori ve 3 geçmiş de korunmalı (veri kaybı yok)
        val backupJson = repo.backup("uuid-1")
        assertNotNull(backupJson)
        val mergedBackup = json.decodeFromString(LocalProfileBackup.serializer(), backupJson!!)
        assertEquals(setOf(1, 2, 3), mergedBackup.favorites.map { it.tmdbId }.toSet())
        assertEquals(setOf(10, 20, 30), mergedBackup.history.map { it.tmdbId }.toSet())
    }

    @Test
    fun pinnedDefaultProfileNotConsolidated() = runTest {
        val store = MemoryPreferencesStore()
        val repo = LocalProfileRepository(store)

        val pinned = LocalProfileSummary("p-pin", "Varsayılan", pinHash = "hash123")
        val unpinned = LocalProfileSummary("p-no-pin", "Varsayılan", pinHash = null)

        val profilesKey = stringPreferencesKey("sync_local_profiles")
        store.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(ListSerializer(LocalProfileSummary.serializer()), listOf(pinned, unpinned))
        }

        val result = repo.consolidateDefaultProfiles()
        assertNull(result) // Birleştirme yapılmaz

        val all = repo.allProfiles()
        assertEquals(2, all.size)
    }

    @Test
    fun replaceProfilesKeepingActiveSwitchesDiscardedActiveId() = runTest {
        val store = MemoryPreferencesStore()
        val repo = LocalProfileRepository(store)

        val idKey = stringPreferencesKey("sync_local_profile_id")
        val nameKey = stringPreferencesKey("sync_local_profile_name")
        store.edit { prefs ->
            prefs[idKey] = "discarded-id"
            prefs[nameKey] = "Varsayılan"
        }

        val newProfiles = listOf(
            LocalProfileSummary("kept-id", "Varsayılan"),
            LocalProfileSummary("other-id", "Diğer"),
        )
        val remapped = mapOf("discarded-id" to "kept-id")

        repo.replaceProfilesKeepingActive(newProfiles, remapped)

        assertEquals("kept-id", repo.id.first())
        assertEquals("Varsayılan", repo.name.first())
    }
}
