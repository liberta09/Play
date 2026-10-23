package com.kaan.play.data.subtitle

import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Yerel sahte sunucuyla: gerçek bir altyazı addon'una dokunmadan `subtitles` protokolünü uçtan uca sınar. */
class AddonSubtitleProviderTest {

    private lateinit var server: MockWebServer
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val requestedPaths = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requestedPaths) { requestedPaths += request.url.encodedPath }
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

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun addon(
        id: Long,
        name: String,
        path: String,
        resources: List<String> = listOf("subtitles"),
        types: List<String> = listOf("movie", "series"),
        idPrefixes: List<String>? = listOf("tt"),
    ) = Addon(
        id = id,
        type = AddonType.NUVIO_ADDON,
        sourceUrl = server.url("$path/manifest.json").toString(),
        manifest = AddonManifest(
            id = "test.$id", name = name, types = types,
            resources = resources.map { ManifestResource(it) }, idPrefixes = idPrefixes,
        ),
        enabled = true,
        addedAt = id,
    )

    private fun provider(addons: List<Addon>, timeout: Duration = 5.seconds) =
        AddonSubtitleProvider(addons = { addons }, client = OkHttpClient(), timeout = { timeout })

    private val movie = StreamRequest(tmdbId = 603, imdbId = "tt0133093", type = MediaType.Movie)
    private val episode = StreamRequest(tmdbId = 1396, imdbId = "tt0903747", type = MediaType.Tv, season = 1, episode = 3)

    @Test
    fun returnsTracksWithNormalizedLanguageAndOriginName() = runTest {
        routes["/os/subtitles/movie/tt0133093.json"] = {
            json(
                """{"subtitles":[
                  {"id":"1","url":"https://subs.example/tr.srt","lang":"tur"},
                  {"id":"2","url":"https://subs.example/en.srt","lang":"eng"},
                  {"id":"3","url":"https://subs.example/tr.srt","lang":"tur"},
                  {"id":"4","url":"ftp://subs.example/x.srt","lang":"fre"},
                  {"id":"5","url":"https://subs.example/xx.srt","lang":"zzz"},
                  {"id":"6","lang":"deu"},
                  "bozuk kayıt"
                ]}""",
            )
        }
        val results = provider(listOf(addon(1, "OpenSubs", "/os"))).subtitles(movie).toList()
        val found = results.single() as SubtitleResult.Found
        assertEquals(StreamSource(1, "OpenSubs"), found.source)
        assertEquals(
            listOf(
                SubtitleTrack("https://subs.example/tr.srt", "tr", "OpenSubs"),
                SubtitleTrack("https://subs.example/en.srt", "en", "OpenSubs"),
                SubtitleTrack("https://subs.example/xx.srt", null, "OpenSubs"), // dili tanınmayan yine de listelenir
            ),
            found.tracks,
        )
    }

    @Test
    fun seriesRequestUsesSeasonAndEpisodeInTheId() = runTest {
        routes["/os/subtitles/series/tt0903747:1:3.json"] = { json("""{"subtitles":[{"id":"1","url":"https://s/1.srt","lang":"eng"}]}""") }
        val found = provider(listOf(addon(1, "OpenSubs", "/os"))).subtitles(episode).toList().single() as SubtitleResult.Found
        assertEquals(1, found.tracks.size)
        assertEquals(listOf("/os/subtitles/series/tt0903747:1:3.json"), requestedPaths)
    }

    @Test
    fun onlyAddonsWithASubtitlesResourceAndMatchingTypeAndPrefixAreAsked() = runTest {
        routes["/subs/subtitles/movie/tt0133093.json"] = { json("""{"subtitles":[]}""") }
        val addons = listOf(
            addon(1, "Altyazı", "/subs"),
            addon(2, "Yalnız akış", "/streamonly", resources = listOf("stream")),
            addon(3, "Yalnız dizi", "/seriesonly", types = listOf("series")),
            addon(4, "Başka önek", "/kitsu", idPrefixes = listOf("kitsu")),
        )
        val p = provider(addons)
        assertEquals(listOf(StreamSource(1, "Altyazı")), p.sources(movie))
        assertEquals(1, p.subtitles(movie).toList().size)
        assertEquals(listOf("/subs/subtitles/movie/tt0133093.json"), requestedPaths) // diğerlerine hiç istek gitmedi
    }

    @Test
    fun noImdbIdMeansNothingToAsk() = runTest {
        val p = provider(listOf(addon(1, "OpenSubs", "/os")))
        assertTrue(p.subtitles(movie.copy(imdbId = null)).toList().isEmpty())
        assertTrue(p.sources(movie.copy(imdbId = null)).isEmpty())
        assertTrue(requestedPaths.isEmpty())
    }

    @Test
    fun slowAddonTimesOutWithoutBlockingTheOthers() = runTest {
        routes["/slow/subtitles/movie/tt0133093.json"] = {
            MockResponse.Builder().body("""{"subtitles":[]}""").headersDelay(2, TimeUnit.SECONDS).build()
        }
        routes["/fast/subtitles/movie/tt0133093.json"] = { json("""{"subtitles":[{"id":"1","url":"https://s/1.srt","lang":"tur"}]}""") }
        val results = provider(listOf(addon(1, "Yavaş", "/slow"), addon(2, "Hızlı", "/fast")), timeout = 300.milliseconds)
            .subtitles(movie).toList()
        assertEquals(2, results.size)
        assertEquals("Hızlı", results[0].source.addonName) // hızlı olan beklemeden ilk geldi
        assertEquals(SubtitleResult.Failed(StreamSource(1, "Yavaş"), FailureReason.Timeout), results[1])
    }

    @Test
    fun brokenJsonWrongShapeAndHttpErrorsAreReportedNotSwallowed() = runTest {
        routes["/bad/subtitles/movie/tt0133093.json"] = { json("{bu json değil") }
        routes["/shape/subtitles/movie/tt0133093.json"] = { json("""{"streams":[]}""") }
        routes["/err/subtitles/movie/tt0133093.json"] = { MockResponse.Builder().code(500).build() }
        val results = provider(listOf(addon(1, "Bozuk", "/bad"), addon(2, "Şekil", "/shape"), addon(3, "Hata", "/err")))
            .subtitles(movie).toList().associateBy { it.source.addonId }
        assertEquals(FailureReason.InvalidResponse, (results.getValue(1) as SubtitleResult.Failed).reason)
        assertEquals(FailureReason.InvalidResponse, (results.getValue(2) as SubtitleResult.Failed).reason)
        assertEquals(FailureReason.Http, (results.getValue(3) as SubtitleResult.Failed).reason)
    }

    @Test
    fun unreachableAddonIsANetworkFailure() = runTest {
        val dead = addon(1, "Ölü", "/dead")
        server.close()
        val result = provider(listOf(dead), timeout = 2.seconds).subtitles(movie).toList().single()
        assertEquals(FailureReason.Network, (result as SubtitleResult.Failed).reason)
    }

    @Test
    fun oversizedResponseIsRejected() = runTest {
        routes["/big/subtitles/movie/tt0133093.json"] = { json("""{"subtitles":[""" + "{\"url\":\"https://s/x.srt\"},".repeat(200) + "{}]}") }
        val p = AddonSubtitleProvider(addons = { listOf(addon(1, "Büyük", "/big")) }, client = OkHttpClient(), maxBytes = 1_000)
        assertEquals(FailureReason.InvalidResponse, (p.subtitles(movie).toList().single() as SubtitleResult.Failed).reason)
    }

    @Test
    fun parseCapsTracksPerAddonPerLanguage() {
        val body = """{"subtitles":[""" +
            (1..30).joinToString(",") { """{"url":"https://s/tr$it.srt","lang":"tur","title":"Turkish Release $it"}""" } + "," +
            (1..30).joinToString(",") { """{"url":"https://s/en$it.srt","lang":"eng","file":"english_sub_$it.vtt"}""" } + "]}"
        val parsed = AddonSubtitleProvider.parse("X", body)!!
        assertEquals(10, parsed.size)
        assertEquals(AddonSubtitleProvider.MAX_PER_LANGUAGE, parsed.count { it.language == "tr" })
        assertEquals(AddonSubtitleProvider.MAX_PER_LANGUAGE, parsed.count { it.language == "en" })
        assertEquals("Turkish Release 1", parsed.first { it.language == "tr" }.label)
        assertEquals("english_sub_1", parsed.first { it.language == "en" }.label)
        assertNull(AddonSubtitleProvider.parse("X", "[]"))
    }

    @Test
    fun subtitleOnlyAddonsNeverAppearInTheStreamSourceList() = runTest {
        val subtitleOnly = addon(1, "Yalnız altyazı", "/subs")
        val streams = AddonStreamProvider(addons = { listOf(subtitleOnly) }, client = OkHttpClient())
        assertTrue(streams.sources(movie).isEmpty())
        assertTrue(streams.streams(movie).toList().isEmpty())
        assertTrue(requestedPaths.isEmpty())
    }
}
