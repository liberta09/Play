package com.kaan.play.data.stream

import androidx.media3.common.C
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.match.ContentDurationCheck
import com.kaan.play.data.match.ContentKind
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.NuvioAddonCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class NuvioLiveStreamTest {

    private lateinit var server: MockWebServer
    private val requestedPaths = mutableListOf<String>()
    private val routes = mutableMapOf<String, () -> MockResponse>()

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requestedPaths) {
                        requestedPaths += request.url.encodedPath
                    }
                    return routes[request.url.encodedPath]?.invoke()
                        ?: MockResponse.Builder().code(404).build()
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

    @Test
    fun catalogParsesLiveChannelAndSeriesCorrectly() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("tr"))
        val manifest = AddonManifest(
            id = "test.live.addon",
            name = "Test Live Addon",
            version = "1.0.0",
            types = listOf("tv", "series"),
            resources = listOf(ManifestResource("catalog"), ManifestResource("meta"), ManifestResource("stream")),
            catalogs = listOf(ManifestCatalog(type = "tv", id = "live-channels", name = "Canlı Yayınlar")),
        )
        val addon = Addon(
            id = 1L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = server.url("/manifest.json").toString(),
            manifest = manifest,
            enabled = true,
            addedAt = 1L,
        )

        val catalogJson = """
            {
              "metas": [
                {
                  "id": "live_ch_1",
                  "type": "tv",
                  "name": "Spor TV 1",
                  "poster": "https://img.example.com/ch1.png"
                },
                {
                  "id": "series_1",
                  "type": "series",
                  "name": "Örnek Dizi",
                  "poster": "https://img.example.com/s1.png"
                }
              ]
            }
        """.trimIndent()

        routes["/catalog/tv/live-channels.json"] = { json(catalogJson) }

        val catalog = NuvioAddonCatalog(addon, OkHttpClient())
        val rows = catalog.rows().first()

        assertEquals(1, rows.size)
        val items = rows.first().items
        assertEquals(2, items.size)

        // Canlı kanal
        val liveItem = items[0]
        assertEquals("live_ch_1", liveItem.id)
        assertEquals("Spor TV 1", liveItem.title)
        assertEquals("Canlı", liveItem.subtitle)
        assertEquals(MediaType.Tv, liveItem.mediaType)
        assertEquals("tv", liveItem.addonType)

        // Dizi
        val seriesItem = items[1]
        assertEquals("series_1", seriesItem.id)
        assertEquals("Örnek Dizi", seriesItem.title)
        assertEquals("Dizi", seriesItem.subtitle)
        assertEquals(MediaType.Tv, seriesItem.mediaType)
        assertEquals("series", seriesItem.addonType)
    }

    @Test
    fun addonStreamProviderRequestsLiveStreamWithCustomTypeAndContentId() = runBlocking {
        val manifest = AddonManifest(
            id = "test.live.addon",
            name = "Test Live Addon",
            version = "1.0.0",
            types = listOf("tv"),
            resources = listOf(ManifestResource("stream")),
        )
        val addon = Addon(
            id = 5L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = server.url("/manifest.json").toString(),
            manifest = manifest,
            enabled = true,
            addedAt = 5L,
        )

        val streamJson = """
            {
              "streams": [
                {
                  "title": "Spor 1080p",
                  "url": "https://cdn.example.com/live/ch1.m3u8",
                  "behaviorHints": {
                    "proxyHeaders": {
                      "request": {
                        "Referer": "https://sports.example.com/",
                        "User-Agent": "Mozilla/5.0"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        routes["/stream/tv/wiospor_live1.json"] = { json(streamJson) }

        val provider = AddonStreamProvider(
            addons = { listOf(addon) },
            client = OkHttpClient(),
        )

        val request = StreamRequest(
            tmdbId = -100,
            imdbId = null,
            type = MediaType.Tv,
            pluginId = "nuvio_addon:5",
            addonType = "tv",
            addonContentId = "wiospor_live1",
        )

        val results = provider.streams(request).first()
        assertTrue(results is StreamResult.Found)
        val found = results as StreamResult.Found
        assertEquals(1, found.streams.size)
        val stream = found.streams.first()
        assertEquals("https://cdn.example.com/live/ch1.m3u8", stream.url)
        assertEquals("https://sports.example.com/", stream.headers["Referer"])
        assertEquals("Mozilla/5.0", stream.headers["User-Agent"])

        synchronized(requestedPaths) {
            assertTrue(requestedPaths.contains("/stream/tv/wiospor_live1.json"))
        }
    }

    @Test
    fun compositeStreamProviderDispatchesOnlyToNuvioWhenPluginIdIsNuvioAddon() = runBlocking {
        val manifest = AddonManifest(
            id = "test.live.addon",
            name = "Test Live Addon",
            version = "1.0.0",
            types = listOf("tv"),
            resources = listOf(ManifestResource("stream")),
        )
        val addon = Addon(
            id = 5L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = server.url("/manifest.json").toString(),
            manifest = manifest,
            enabled = true,
            addedAt = 5L,
        )

        val streamJson = """{"streams":[{"url":"https://cdn.example.com/live.m3u8"}]}"""
        routes["/stream/tv/live_feed.json"] = { json(streamJson) }

        val addonProvider = AddonStreamProvider(
            addons = { listOf(addon) },
            client = OkHttpClient(),
        )

        var csCalled = false
        val dummyCsProvider = object : StreamProvider {
            override fun streams(request: StreamRequest) = kotlinx.coroutines.flow.flow<StreamResult> {
                csCalled = true
            }
            override suspend fun sources(request: StreamRequest): List<StreamSource> {
                csCalled = true
                return emptyList()
            }
        }

        val composite = CompositeStreamProvider(listOf(addonProvider, dummyCsProvider))

        val request = StreamRequest(
            tmdbId = -200,
            imdbId = null,
            type = MediaType.Tv,
            pluginId = "nuvio_addon:5",
            addonType = "tv",
            addonContentId = "live_feed",
        )

        val result = composite.streams(request).first()
        assertTrue(result is StreamResult.Found)
        assertFalse("CloudStream sağlayıcısına istek gitmemeli", csCalled)
    }

    @Test
    fun contentDurationCheckDoesNotEliminateLiveStreams() {
        val r1 = ContentDurationCheck.check(C.TIME_UNSET, null, ContentKind.Tv)
        assertEquals(ContentDurationCheck.Result.Unknown, r1)

        val r2 = ContentDurationCheck.check(0L, null, ContentKind.Tv)
        assertEquals(ContentDurationCheck.Result.Unknown, r2)

        val r3 = ContentDurationCheck.check(-1L, 0, ContentKind.Tv)
        assertEquals(ContentDurationCheck.Result.Unknown, r3)
    }

    @Test
    fun streamRequestContentIdRegression() {
        val liveRequest = StreamRequest(
            tmdbId = 1,
            imdbId = null,
            type = MediaType.Tv,
            addonType = "tv",
            addonContentId = "custom_channel_id",
        )
        assertEquals("custom_channel_id", liveRequest.contentId)

        val movieRequest = StreamRequest(
            tmdbId = 2,
            imdbId = "tt0111161",
            type = MediaType.Movie,
        )
        assertEquals("tt0111161", movieRequest.contentId)

        val seriesRequest = StreamRequest(
            tmdbId = 3,
            imdbId = "tt0903747",
            type = MediaType.Tv,
            season = 1,
            episode = 2,
        )
        assertEquals("tt0903747:1:2", seriesRequest.contentId)
    }
}
