package com.kaan.play.data.skip

import com.kaan.play.data.cloudstream.PlayNetworkGuard
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

/** TheIntroDB isteğine yalnızca kimlik, sezon/bölüm ve süre gider; PlayNetworkGuard yerel adresi sessizce engeller. */
class IntroDbPrivacyTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        PlayNetworkGuard.disallowLoopbackPort(server.port)
        server.close()
    }

    @Test
    fun onlyIdentityEpisodeAndDurationAreSentWithoutStreamDataOrCookies() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse.Builder().body("""{"intro":[{"start_ms":0,"end_ms":60000}]}""").build())
        val repository = IntroDbRepository(OkHttpClient(), endpoint = server.url("/v4/media").toString())

        repository.timestamps(
            StreamRequest(
                tmdbId = 1399, imdbId = "tt0944947", type = MediaType.Tv, season = 2, episode = 5,
                title = "Gizli başlık", dataUrl = "https://example.com/stream.m3u8", contentUrl = "https://example.com/dizi",
            ),
            durationMs = 3_000_000L,
        )

        val request = server.takeRequest()
        assertEquals(setOf("tmdb_id", "duration_ms", "season", "episode"), request.url.queryParameterNames)
        assertNull(request.headers["Cookie"])
        assertNull(request.headers["Referer"])
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun guardedClientBlocksPrivateEndpointSilently() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse.Builder().body("""{"intro":[{"start_ms":0,"end_ms":60000}]}""").build())
        val guarded = OkHttpClient.Builder()
            .addInterceptor(PlayNetworkGuard.interceptor)
            .addNetworkInterceptor(PlayNetworkGuard.interceptor)
            .build()
        val repository = IntroDbRepository(guarded, endpoint = server.url("/v4/media").toString())

        val result = repository.timestamps(StreamRequest(tmdbId = 27205, imdbId = null, type = MediaType.Movie), 7_200_000L)

        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
