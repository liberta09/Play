package com.kaan.play.data.collection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CollectionJsonTest {

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun parsesFlexibleFieldsAndCountsUnsupportedSources() {
        val raw = """
            {
              "name": "Özel Paket",
              "folders": [
                {
                  "name": "Favori Listem",
                  "catalogSources": [
                    {"provider": "tmdb", "tmdbSourceType": "list", "tmdbId": 12345},
                    {"provider": "trakt", "tmdbSourceType": "list", "tmdbId": 999}
                  ]
                },
                {
                  "title": "Nolan",
                  "sources": [
                    {"provider": "tmdb", "tmdbSourceType": "collection", "tmdbId": 263},
                    {"provider": "tmdb", "mediaType": "movie", "tmdbId": 157336},
                    {"provider": "imdb", "id": "tt0816692"}
                  ]
                },
                {
                  "title": "90lar Bilim Kurgu",
                  "sources": [
                    {
                      "provider": "tmdb",
                      "type": "series",
                      "tmdbSourceType": "discover",
                      "sortBy": "vote_average.desc",
                      "filters": {
                        "with_genres": "878,18",
                        "with_original_language": "en",
                        "primary_release_year": 1999,
                        "first_air_date_year": 1998,
                        "vote_average.gte": 7.5,
                        "with_companies": "420",
                        "with_networks": "49",
                        "api_key": "FORBIDDEN",
                        "certification": "R"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = CollectionJsonParser.parse(raw)
        assertEquals("Özel Paket", parsed.title)
        assertEquals(3, parsed.folders.size)
        assertEquals(2, parsed.unsupportedSourcesCount)

        val f0 = parsed.folders[0]
        assertEquals("Favori Listem", f0.title)
        assertEquals(CollectionSourceKind.List, f0.sources.single().kind)
        assertEquals(12345, f0.sources.single().tmdbId)

        val f1 = parsed.folders[1]
        assertEquals(2, f1.sources.size)
        assertEquals(CollectionSourceKind.Collection, f1.sources[0].kind)
        assertEquals(CollectionSourceKind.Single, f1.sources[1].kind)
        assertEquals(MediaType.Movie, f1.sources[1].mediaType)
        assertEquals(157336, f1.sources[1].tmdbId)

        val discover = parsed.folders[2].sources.single()
        assertEquals(CollectionSourceKind.Discover, discover.kind)
        assertEquals(MediaType.Tv, discover.mediaType)
        assertEquals("vote_average.desc", discover.sortBy)
        assertEquals(
            setOf(
                "with_genres",
                "with_original_language",
                "primary_release_year",
                "first_air_date_year",
                "vote_average.gte",
                "with_companies",
                "with_networks",
            ),
            discover.filters.keys,
        )
        assertNull(discover.filters["api_key"])
        assertNull(discover.filters["certification"])
    }

    @Test
    fun rejectsMalformedJsonAndMissingFolders() {
        listOf("{bad json", "[]", """{"title":"NoFolders"}""", "").forEach { bad ->
            try {
                CollectionJsonParser.parse(bad)
                fail("InvalidJson bekleniyordu: $bad")
            } catch (_: CollectionImportException.InvalidJson) {
                // beklenen
            }
        }
    }

    @Test
    fun enforcesFileSizeLimitAndStorageLimitAndRemovesCollection() = runTest {
        val repo = CollectionRepository(MemoryStore(), clock = { 100L }, idGenerator = { "c-1" })

        val oversized = "x".repeat(CollectionJsonParser.MAX_IMPORT_BYTES + 1)
        try {
            repo.importJson(oversized)
            fail("FileTooLarge bekleniyordu")
        } catch (_: CollectionImportException.FileTooLarge) {
            // beklenen
        }

        val valid = """
            {
              "title": "Test Koleksiyon",
              "folders": [
                {"title": "Klasör 1", "sources": [{"provider": "tmdb", "mediaType": "movie", "tmdbId": 603}]}
              ]
            }
        """.trimIndent()
        val result = repo.importJson(valid)
        assertEquals(1, result.foldersAdded)
        assertEquals(0, result.unsupportedSourcesCount)
        assertEquals(1, repo.collections.first().size)
        assertEquals(1, repo.folderShelves.first().size)

        // 256 KB depolama sınırı aşılınca reddedilmeli (her biri < 512 KB geçerli koleksiyonlar eklenerek)
        val mediumFilters = "12,".repeat(250).trimEnd(',')
        val folderJson = """{"title":"Uzun ${"a".repeat(500)}","sources":[{"provider":"tmdb","mediaType":"movie","tmdbSourceType":"discover","filters":{"with_genres":"$mediumFilters"}}]}"""
        val collectionJson = """{"title":"Parça","folders":[${List(50) { folderJson }.joinToString(",")}]}"""
        assertTrue(collectionJson.length < CollectionJsonParser.MAX_IMPORT_BYTES)
        var storageRejected = false
        for (i in 1..20) {
            try {
                repo.importJson(collectionJson)
            } catch (_: CollectionImportException.StorageFull) {
                storageRejected = true
                break
            }
        }
        assertTrue("Depolama sınırı (256 KB) StorageFull fırlatmalı", storageRejected)

        repo.removeCollection("c-1")
        assertTrue(repo.collections.first().isEmpty())
        assertTrue(repo.folderShelves.first().isEmpty())
    }
}
