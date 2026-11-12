package com.kaan.play.data.tmdb

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.PersonCreditSort
import com.kaan.play.data.net.PlayJson
import com.kaan.play.i18n.UiLanguage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonCreditsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo(lang: () -> UiLanguage) = TmdbRepository(
        client = OkHttpClient(),
        json = PlayJson,
        apiKey = "test-key",
        language = lang,
        baseUrl = server.url("/"),
    )

    @Test
    fun `kisi tek combined_credits istegi atar ve bos biyografide Ingilizce yedegi alir`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {
                      "id": 73421,
                      "name": "Joaquin Phoenix",
                      "biography": "",
                      "profile_path": "/joaquin.jpg",
                      "combined_credits": {
                        "cast": [
                          {"id": 475557, "media_type": "movie", "title": "Joker", "poster_path": "/joker.jpg", "release_date": "2019-10-02", "popularity": 95.0, "adult": false},
                          {"id": 999, "media_type": "movie", "title": "Adult Film", "adult": true, "popularity": 200.0},
                          {"id": 1418, "media_type": "tv", "name": "Test Series", "first_air_date": "2022-05-10", "popularity": 40.0, "adult": false}
                        ],
                        "crew": [
                          {"id": 475557, "media_type": "movie", "title": "Joker", "release_date": "2019-10-02", "popularity": 99.0, "adult": false, "job": "Producer"}
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .body("""{"id":73421,"name":"Joaquin Phoenix","biography":"English bio fallback."}""")
                .build(),
        )

        val person = repo { UiLanguage.Turkish }.person(73421)

        val req1 = server.takeRequest()
        assertEquals("/person/73421", req1.url.encodedPath)
        assertEquals("combined_credits", req1.url.queryParameter("append_to_response"))
        assertEquals("tr-TR", req1.url.queryParameter("language"))

        val req2 = server.takeRequest()
        assertEquals("/person/73421", req2.url.encodedPath)
        assertEquals("en-US", req2.url.queryParameter("language"))
        assertEquals(null, req2.url.queryParameter("append_to_response"))

        assertEquals("English bio fallback.", person.biography)
        assertEquals("/joaquin.jpg", person.profilePath)
        // Joker cast + crew tek kartta birleşir, adult=true dışlanır.
        assertEquals(1, person.movieCredits.size)
        assertEquals(475557, person.movieCredits.single().media.id)
        assertEquals(99.0, person.movieCredits.single().popularity, 1e-6)
        assertEquals(1, person.tvCredits.size)
        assertFalse(person.movieCredits.any { it.media.id == 999 })
    }

    @Test
    fun `populer ve tarih siralamasi dogru calisir ve adult tamamen elenir`() {
        val dto = PersonDetailDto(
            id = 10,
            name = "Actor",
            biography = "Bio",
            combinedCredits = CombinedCreditsDto(
                cast = listOf(
                    PersonCreditDto(id = 1, mediaType = "movie", title = "Older Hit", releaseDate = "2010-01-01", popularity = 120.0, adult = false),
                    PersonCreditDto(id = 2, mediaType = "movie", title = "Newer Indie", releaseDate = "2024-08-15", popularity = 25.0, adult = false),
                    PersonCreditDto(id = 3, mediaType = "movie", title = "Undated", releaseDate = null, popularity = 50.0, adult = false),
                    PersonCreditDto(id = 4, mediaType = "movie", title = "Adult Blocked", releaseDate = "2025-01-01", popularity = 999.0, adult = true),
                ),
            ),
        )
        val parsed = PersonCreditsRules.toPersonDetail(dto)
        assertEquals(3, parsed.movieCredits.size)
        assertTrue(parsed.tvCredits.isEmpty())

        val byPop = PersonCreditsRules.sort(parsed.movieCredits, PersonCreditSort.Popular)
        assertEquals(listOf(1, 3, 2), byPop.map { it.media.id })

        val byDate = PersonCreditsRules.sort(parsed.movieCredits, PersonCreditSort.Date)
        assertEquals(listOf(2, 1, 3), byDate.map { it.media.id })
    }
}
