package com.kaan.play.data.skip

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntroDbRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: IntroDbRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = IntroDbRepository(
            client = OkHttpClient.Builder().build(),
            endpoint = server.url("/v4/media").toString(),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun tvLookupSendsTmdbEpisodeCoordinatesAndDurationAndParsesSegments() = kotlinx.coroutines.test.runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "tmdb_id": 1399,
                  "type": "tv",
                  "intro": [{"start_ms": null, "end_ms": 24000}],
                  "recap": [{"start_ms": 25000, "end_ms": 98000}],
                  "credits": [{"start_ms": 2500000, "end_ms": null}]
                }
                """.trimIndent(),
            ).build(),
        )

        val result = repository.timestamps(
            StreamRequest(tmdbId = 1399, imdbId = null, type = MediaType.Tv, season = 1, episode = 3),
            durationMs = 2_700_000L,
        )

        val request = server.takeRequest()
        val url = request.url
        assertEquals("1399", url.queryParameter("tmdb_id"))
        assertEquals("1", url.queryParameter("season"))
        assertEquals("3", url.queryParameter("episode"))
        assertEquals("2700000", url.queryParameter("duration_ms"))
        assertEquals(
            listOf(
                SkipTimestamp(SkipTimeType.Intro, 0L, 24_000L),
                SkipTimestamp(SkipTimeType.Recap, 25_000L, 98_000L),
                SkipTimestamp(SkipTimeType.Credits, 2_500_000L, 2_700_000L, openEndedEnd = true),
            ),
            result,
        )
    }

    @Test
    fun movieLookupDoesNotSendEpisodeCoordinates() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse.Builder().body("""{"intro":[],"recap":[],"credits":[]}""").build())

        val result = repository.timestamps(
            StreamRequest(tmdbId = 27205, imdbId = null, type = MediaType.Movie),
            durationMs = 7_200_000L,
        )

        val url = server.takeRequest().url
        assertEquals("27205", url.queryParameter("tmdb_id"))
        assertEquals("7200000", url.queryParameter("duration_ms"))
        assertNull(url.queryParameter("season"))
        assertNull(url.queryParameter("episode"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun shortVerifiedSegmentsAreValidatedAndLiveOrUnidentifiedContentIsNotSent() = kotlinx.coroutines.test.runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"intro":[{"start_ms":null,"end_ms":2500},{"start_ms":null,"end_ms":3000}]}""",
            ).build(),
        )
        val shortVerified = repository.timestamps(
            StreamRequest(tmdbId = 1, imdbId = null, type = MediaType.Movie),
            durationMs = 9 * 60_000L,
        )
        val short = repository.timestamps(
            StreamRequest(tmdbId = -1, imdbId = null, type = MediaType.Movie),
            durationMs = 9 * 60_000L,
        )
        val live = repository.timestamps(
            StreamRequest(tmdbId = 1, imdbId = null, type = MediaType.Tv, season = 1, episode = 1, addonType = "channel"),
            durationMs = 30 * 60_000L,
        )

        assertEquals(listOf(SkipTimestamp(SkipTimeType.Intro, 0L, 3_000L, openEndedEnd = false)), shortVerified)
        assertTrue(live.isEmpty())
        assertTrue(short.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun imdbIdentityFallbackAndPreviewAreSupportedAndNullEndUsesDuration() = kotlinx.coroutines.test.runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"type":"movie","preview":[{"start_ms":250000,"end_ms":null}]}""",
            ).build(),
        )
        val result = repository.timestamps(
            StreamRequest(tmdbId = -1, imdbId = "tt0133093", type = MediaType.Movie),
            durationMs = 600_000L,
        )
        val url = server.takeRequest().url
        assertEquals("tt0133093", url.queryParameter("imdb_id"))
        assertNull(url.queryParameter("tmdb_id"))
        assertEquals("600000", url.queryParameter("duration_ms"))
        assertEquals(listOf(SkipTimestamp(SkipTimeType.Preview, 250_000L, 600_000L, openEndedEnd = true)), result)
    }

    @Test
    fun absentEntriesAreCachedForTheSameEpisodeAndDuration() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        val request = StreamRequest(tmdbId = 1399, imdbId = null, type = MediaType.Tv, season = 1, episode = 3)

        assertTrue(repository.timestamps(request, 2_700_000L).isEmpty())
        assertTrue(repository.timestamps(request, 2_700_000L).isEmpty())

        assertEquals(1, server.requestCount)
    }
}
