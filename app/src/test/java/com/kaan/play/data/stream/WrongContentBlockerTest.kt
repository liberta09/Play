package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WrongContentBlockerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun contentIdentityPrefersTmdbThenImdbThenFoldedTitleAndIgnoresEpisode() {
        val ep1 = StreamRequest(1396, "tt0903747", MediaType.Tv, season = 1, episode = 1)
        val ep5 = StreamRequest(1396, "tt0903747", MediaType.Tv, season = 5, episode = 8)
        assertEquals("tv:tmdb:1396", WrongContentBlocker.contentIdentity(ep1))
        assertEquals(WrongContentBlocker.contentIdentity(ep1), WrongContentBlocker.contentIdentity(ep5))

        val movieTmdb = StreamRequest(550, "tt0137523", MediaType.Movie)
        assertEquals("movie:tmdb:550", WrongContentBlocker.contentIdentity(movieTmdb))
        assertNotEquals(
            WrongContentBlocker.contentIdentity(movieTmdb),
            WrongContentBlocker.contentIdentity(movieTmdb.copy(type = MediaType.Tv)),
        )

        val imdbOnly = StreamRequest(0, "TT0137523", MediaType.Movie, title = "Dövüş Kulübü", year = 1999)
        assertEquals("imdb:tt0137523", WrongContentBlocker.contentIdentity(imdbOnly))

        val titleOnly = StreamRequest(0, null, MediaType.Movie, title = "  İstanbul'un Şafağı! (2) ", year = 2024)
        assertEquals("title:istanbul un safagı 2:2024", WrongContentBlocker.contentIdentity(titleOnly))
    }

    @Test
    fun pluginIdentityUsesAddonIdOrFoldedNameAndBuildsDeterministicSha256Key() {
        val byId = StreamSource(42L, "Test Source")
        assertEquals("id:42", WrongContentBlocker.pluginIdentity(byId))

        val byName = StreamSource(0L, "  Şahin-Film!! ")
        assertEquals("name:sahin film", WrongContentBlocker.pluginIdentity(byName))

        val s1e1 = StreamRequest(100, null, MediaType.Tv, season = 1, episode = 1)
        val s3e4 = StreamRequest(100, null, MediaType.Tv, season = 3, episode = 4)
        val key1 = WrongContentBlocker.blockKey(s1e1, byId)
        val key2 = WrongContentBlocker.blockKey(s3e4, byId)
        assertEquals(64, key1.length)
        assertEquals(key1, key2)
    }

    @Test
    fun trimsAt500KeepingNewestAndStorePersistsBlocks() = runBlocking {
        var now = 100_000L
        var list = emptyList<WrongContentBlockEntry>()
        for (i in 1..505) {
            now += 10
            list = WrongContentBlocker.upsertAndTrim(list, "k$i", now)
        }
        assertEquals(WrongContentBlocker.MAX_ENTRIES, list.size)
        assertFalse(list.any { it.key == "k1" })
        assertTrue(list.any { it.key == "k505" })

        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        val store = WrongContentBlockStore(ds) { now }
        val sourceBlocked = StreamSource(9L, "Yanlış Eklenti")
        val sourceOther = StreamSource(10L, "Doğru Eklenti")
        val ep1 = StreamRequest(999, "tt999", MediaType.Tv, season = 1, episode = 1)
        val ep2 = StreamRequest(999, "tt999", MediaType.Tv, season = 2, episode = 3)

        store.block(ep1, sourceBlocked)
        assertTrue(store.isBlocked(ep1, sourceBlocked))
        assertTrue(store.isBlocked(ep2, sourceBlocked))
        assertFalse(store.isBlocked(ep1, sourceOther))
    }

    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun storesDisplayMetadataSupportsLegacyEntriesAndRemoveUnblocksPlugin() = runBlocking {
        val prefs = MemoryPreferencesStore()
        // Eski biçim (görünen ad olmadan yalnızca özet anahtar + tarih) yazılır
        val legacyJson = """[{"key":"legacy_hash_key_1","addedAtMs":5000}]"""
        prefs.edit { it[WrongContentBlockStore.KEY] = legacyJson }

        val store = WrongContentBlockStore(prefs) { 90_000L }
        val req = StreamRequest(550, "tt0137523", MediaType.Movie, title = "Dövüş Kulübü", year = 1999)
        val source = StreamSource(7L, "TestAddon")
        val addedKey = store.block(req, source)

        val entries = store.entriesFlow.first()
        assertEquals(2, entries.size)
        val newEntry = entries.first { it.key == addedKey }
        assertEquals("TestAddon", newEntry.addonName)
        assertEquals("Dövüş Kulübü", newEntry.contentTitle)
        assertEquals(1999, newEntry.year)
        assertEquals(7L, newEntry.addonId)

        val legacyEntry = entries.first { it.key == "legacy_hash_key_1" }
        assertNull(legacyEntry.addonName)
        assertNull(legacyEntry.contentTitle)

        assertTrue(store.isBlocked(req, source))
        store.remove(addedKey)
        assertFalse(store.isBlocked(req, source))
        assertFalse(store.isBlockedCached(req, source))
        assertEquals(listOf("legacy_hash_key_1"), store.entriesFlow.first().map { it.key })

        store.remove("legacy_hash_key_1")
        assertTrue(store.entriesFlow.first().isEmpty())
    }
}
