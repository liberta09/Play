package com.kaan.play.data.stream

import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Yerel sahte sunucuyla: gerçek bir addon'a dokunmadan protokolü uçtan uca sınar. */
class AddonStreamProviderTest {

    private lateinit var server: MockWebServer

    /** Yol -> cevap. Kayıtsız yollar 404 döner. */
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requestedPaths = mutableListOf<String>()
    private val requestedQueries = mutableListOf<String?>()
    private val requestedHeaders = mutableListOf<String?>()

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requestedPaths) {
                        requestedPaths += request.url.encodedPath
                        requestedQueries += request.url.encodedQuery
                        requestedHeaders += request.headers["Accept"]
                    }
                    return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun addon(
        id: Long,
        name: String,
        path: String,
        types: List<String> = listOf("movie", "series"),
        idPrefixes: List<String>? = listOf("tt"),
    ) = Addon(
        id = id,
        type = AddonType.NUVIO_ADDON,
        sourceUrl = server.url("$path/manifest.json").toString(),
        manifest = AddonManifest(
            id = "test.$id", name = name, types = types,
            resources = listOf(ManifestResource("stream")), idPrefixes = idPrefixes,
        ),
        enabled = true,
        addedAt = id,
    )

    private fun provider(addons: List<Addon>, timeout: Duration = 5.seconds) =
        AddonStreamProvider(addons = { addons }, client = OkHttpClient(), timeout = { timeout })

    private val movie = StreamRequest(tmdbId = 603, imdbId = "tt0133093", type = MediaType.Movie)

    private fun StreamResult.found() = this as StreamResult.Found
    private fun StreamResult.failed() = this as StreamResult.Failed

    @Test
    fun successParsesUrlStreamsQualitySizeSubtitlesAndProxyHeaders() = runTest {
        routes["/a/stream/movie/tt0133093.json"] = {
            json(
                """
                {"streams":[
                  {"name":"Hızlı 1080p","title":"The Matrix\n💾 2.5 GB","url":"https://cdn.example.com/m.mp4",
                   "subtitles":[{"id":"1","url":"https://subs.example.com/tr.vtt","lang":"tur"},{"id":"2","url":"ftp://x/y.srt","lang":"eng"}],
                   "behaviorHints":{"proxyHeaders":{"request":{"Referer":"https://ref.example.com","User-Agent":"X"}}}},
                  {"name":"Torrent","infoHash":"abcdef0123456789abcdef0123456789abcdef01"},
                  {"name":"YouTube","ytId":"dQw4w9WgXcQ"},
                  {"name":"4K","url":"https://cdn.example.com/m4k.mkv","behaviorHints":{"videoSize":8000000000}}
                ]}
                """,
            )
        }

        val results = provider(listOf(addon(1, "Addon A", "/a"))).streams(movie).toList()

        assertEquals(1, results.size)
        val found = results.single().found()
        assertEquals(StreamSource(1, "Addon A"), found.source)
        assertEquals(3, found.streams.size) // ytId akışı yok sayıldı; infoHash magnet olarak alındı

        val first = found.streams[0]
        assertEquals("https://cdn.example.com/m.mp4", first.url)
        assertEquals(1080, first.resolution)
        assertEquals(2_500_000_000L, first.sizeBytes)
        assertEquals(mapOf("Referer" to "https://ref.example.com", "User-Agent" to "X"), first.headers)
        assertEquals(listOf(StreamSubtitle("https://subs.example.com/tr.vtt", "tur", mapOf("User-Agent" to "X", "Referer" to "https://ref.example.com"))), first.subtitles) // ftp atıldı

        assertEquals("magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01", found.streams[1].url)

        val second = found.streams[2]
        assertEquals(2160, second.resolution) // "4K" adından
        assertEquals(8_000_000_000L, second.sizeBytes) // videoSize ipucundan
        assertTrue(second.headers.isEmpty())

        assertEquals(listOf("/a/stream/movie/tt0133093.json"), requestedPaths)
        assertEquals(listOf<String?>("application/json"), requestedHeaders)
    }

    @Test
    fun authQueryFromManifestUrlIsSentOnStreamRequest() = runTest {
        routes["/secure/stream/movie/tt0133093.json"] = { json("""{"streams":[]}""") }
        val secure = addon(1, "Token addon", "/secure").copy(
            sourceUrl = server.url("/secure/manifest.json?token=secret-123&client=play").toString(),
        )

        val result = provider(listOf(secure)).streams(movie).toList().single()

        assertTrue(result.found().streams.isEmpty())
        assertEquals(listOf("token=secret-123&client=play"), requestedQueries)
    }

    @Test
    fun emptyAnswerIsFoundWithoutStreamsNotAFailure() = runTest {
        routes["/a/stream/movie/tt0133093.json"] = { json("""{"streams":[]}""") }

        val result = provider(listOf(addon(1, "A", "/a"))).streams(movie).toList().single()

        assertTrue(result.found().streams.isEmpty())
    }

    @Test
    fun slowAddonTimesOutWithoutBlockingTheOthers() = runTest {
        routes["/slow/stream/movie/tt0133093.json"] = {
            MockResponse.Builder().body("""{"streams":[]}""").headersDelay(2, TimeUnit.SECONDS).build()
        }
        routes["/fast/stream/movie/tt0133093.json"] = {
            json("""{"streams":[{"name":"720p","url":"https://cdn.example.com/f.mp4"}]}""")
        }

        val results = provider(
            listOf(addon(1, "Yavaş", "/slow"), addon(2, "Hızlı", "/fast")),
            timeout = 300.milliseconds,
        ).streams(movie).toList()

        // Hızlı olan önce gelir (yavaşı beklemez); yavaş olan "yanıt vermedi" olarak bildirilir.
        assertEquals(2, results.size)
        assertEquals("Hızlı", results[0].found().source.addonName)
        assertEquals(StreamResult.Failed(StreamSource(1, "Yavaş"), FailureReason.Timeout), results[1])
    }

    @Test
    fun malformedJsonIsReportedAsInvalidResponse() = runTest {
        routes["/bad/stream/movie/tt0133093.json"] = { json("<html>not json</html>") }
        routes["/shape/stream/movie/tt0133093.json"] = { json("""{"streams":"yok"}""") }
        routes["/missing/stream/movie/tt0133093.json"] = { json("""{"hello":1}""") }

        val results = provider(
            listOf(addon(1, "Bad", "/bad"), addon(2, "Shape", "/shape"), addon(3, "Missing", "/missing")),
        ).streams(movie).toList()

        assertEquals(3, results.size)
        results.forEach { assertEquals(FailureReason.InvalidResponse, it.failed().reason) }
    }

    @Test
    fun oneBrokenEntryDoesNotDropTheRest() = runTest {
        routes["/a/stream/movie/tt0133093.json"] = {
            json("""{"streams":[{"url":{"nested":true},"name":"bozuk"},{"name":"ok 480p","url":"http://cdn.example.com/ok.mp4"}]}""")
        }

        val streams = provider(listOf(addon(1, "A", "/a"))).streams(movie).toList().single().found().streams

        assertEquals(listOf("http://cdn.example.com/ok.mp4"), streams.map { it.url })
        assertEquals(480, streams.single().resolution)
    }

    @Test
    fun httpErrorAndUnreachableHostAreFailures() = runTest {
        routes["/err/stream/movie/tt0133093.json"] = { MockResponse.Builder().code(500).build() }
        val dead = addon(2, "Ölü", "/dead").copy(sourceUrl = "http://127.0.0.1:1/manifest.json")

        val results = provider(listOf(addon(1, "Hatalı", "/err"), dead)).streams(movie).toList()
            .associate { it.source.addonName to it.failed().reason }

        assertEquals(FailureReason.Http, results["Hatalı"])
        assertEquals(FailureReason.Network, results["Ölü"])
    }

    @Test
    fun addonsThatDoNotMatchTypeOrIdPrefixAreNotQueried() = runTest {
        val addons = listOf(
            addon(1, "Kitsu", "/kitsu", idPrefixes = listOf("kitsu")), // önek uymuyor
            addon(2, "Sadece dizi", "/series", types = listOf("series")), // tür uymuyor
        )

        val results = provider(addons).streams(movie).toList()

        assertTrue(results.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun resourceLevelPrefixOverridesManifestLevel() = runTest {
        val special = addon(1, "Kaynak düzeyi", "/a", idPrefixes = listOf("kitsu")).let {
            it.copy(manifest = it.manifest.copy(resources = listOf(ManifestResource("stream", idPrefixes = listOf("tt")))))
        }
        routes["/a/stream/movie/tt0133093.json"] = { json("""{"streams":[]}""") }

        assertEquals(1, provider(listOf(special)).streams(movie).toList().size)
    }

    @Test
    fun seriesUsesImdbSeasonEpisodeIdAndKeepsBasePath() = runTest {
        routes["/cfg/x/stream/series/tt0944947:2:5.json"] = { json("""{"streams":[]}""") }
        val request = StreamRequest(tmdbId = 1399, imdbId = "tt0944947", type = MediaType.Tv, season = 2, episode = 5)

        val results = provider(listOf(addon(1, "A", "/cfg/x"))).streams(request).toList()

        assertEquals(1, results.size)
        assertEquals(listOf("/cfg/x/stream/series/tt0944947:2:5.json"), requestedPaths)
    }

    @Test
    fun withoutImdbIdOrEpisodeNothingIsQueried() = runTest {
        val addons = listOf(addon(1, "A", "/a"))
        val noImdb = movie.copy(imdbId = null)
        val noEpisode = StreamRequest(1399, "tt0944947", MediaType.Tv, season = 1, episode = null)

        assertTrue(provider(addons).streams(noImdb).toList().isEmpty())
        assertTrue(provider(addons).streams(noEpisode).toList().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun oversizedAnswerIsInvalidResponse() = runTest {
        routes["/a/stream/movie/tt0133093.json"] = { json("""{"streams":[],"pad":"${"x".repeat(5_000)}"}""") }
        val small = AddonStreamProvider(
            addons = { listOf(addon(1, "A", "/a")) }, client = OkHttpClient(),
            timeout = { 5.seconds }, maxBytes = 1_000,
        )

        assertEquals(FailureReason.InvalidResponse, small.streams(movie).toList().single().failed().reason)
    }
}
