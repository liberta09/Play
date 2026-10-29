package com.kaan.play.data.provider

import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.cloudstream.CsCatalogSection
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.model.MediaType
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProviderCatalogTest {

    @Test
    fun catalogCacheStoresAndExpiresEntries() {
        var currentTime = 1000L
        val cache = CatalogCache(ttlMs = 5000L, clock = { currentTime })

        val sampleRows = listOf(
            CatalogRow(
                id = "row1",
                title = "Popüler Filmler",
                items = listOf(
                    CatalogItem(
                        id = "item1",
                        title = "Film 1",
                        posterUrl = "https://example.com/p1.jpg",
                        providerId = "cs:1",
                    ),
                ),
                providerId = "cs:1",
                providerName = "TestProvider",
            ),
        )

        assertNull(cache.get("cs:1"))

        cache.put("cs:1", sampleRows)
        val cached = cache.get("cs:1")
        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals("Popüler Filmler", cached.first().title)

        // TTL içinde geçerli
        currentTime += 4000L
        assertNotNull(cache.get("cs:1"))

        // TTL aşıldığında sonlanır
        currentTime += 2000L // Toplam 6000L > 5000L
        assertNull(cache.get("cs:1"))

        // Temizleme testi
        cache.put("cs:2", sampleRows)
        cache.clear("cs:2")
        assertNull(cache.get("cs:2"))

        cache.put("partial-live", sampleRows, ttlMs = 1000L)
        currentTime += 999L
        assertNotNull(cache.get("partial-live"))
        currentTime += 2L
        assertNull(cache.get("partial-live"))
    }

    @Test
    fun csRuntimeMainPageParsesSectionsCorrectly(): Unit = runBlocking {
        val runtime = CsRuntime()
        val fakeApi = object : MainAPI() {
            override var name = "FakeProvider"
            override var mainUrl = "https://fake.provider"
            override val mainPage: List<MainPageData> = listOf(
                MainPageData("Vizyondakiler", "https://fake.provider/vizyon"),
                MainPageData("Popüler Diziler", "https://fake.provider/diziler"),
            )

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
                return when (request.name) {
                    "Vizyondakiler" -> newHomePageResponse(
                        request,
                        listOf(
                            newMovieSearchResponse("Film A", "https://fake.provider/film-a", TvType.Movie) {
                                posterUrl = "https://fake.provider/film-a.jpg"
                            },
                        ),
                        true,
                    )
                    "Popüler Diziler" -> newHomePageResponse(
                        request,
                        listOf(
                            newMovieSearchResponse("Dizi B", "https://fake.provider/dizi-b", TvType.TvSeries) {
                                posterUrl = "https://fake.provider/dizi-b.jpg"
                            },
                        ),
                        false,
                    )
                    else -> null
                }
            }
        }

        val sections = runtime.mainPage(fakeApi, page = 1)
        assertEquals(2, sections.size)

        assertEquals("Vizyondakiler", sections[0].title)
        assertEquals(1, sections[0].items.size)
        assertEquals("Film A", sections[0].items[0].name)
        assertTrue(sections[0].hasNext)

        assertEquals("Popüler Diziler", sections[1].title)
        assertEquals(1, sections[1].items.size)
        assertEquals("Dizi B", sections[1].items[0].name)
    }

    @Test
    fun csRuntimeCanSkipUnrelatedSectionsAndReachLiveSectionBeyondLimit(): Unit = runBlocking {
        val requested = mutableListOf<String>()
        val liveSectionName = "Canlı TV Kanalları"
        val fakeApi = object : MainAPI() {
            override var name = "LiveProvider"
            override var mainUrl = "https://live.provider"
            override val mainPage: List<MainPageData> = listOf(
                MainPageData("Filmler", "movies"),
                MainPageData("Diziler", "series"),
                MainPageData(liveSectionName, "live"),
            )

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
                requested += request.name
                return newHomePageResponse(
                    request,
                    listOf(newLiveSearchResponse("Haber TV", "https://live.provider/news", TvType.Live)),
                    false,
                )
            }
        }

        val rows = CsRuntime().mainPage(
            api = fakeApi,
            maxSectionsPerApi = 1,
            sectionNameFilter = { it.contains("Canlı") },
        )

        assertEquals(listOf(liveSectionName), requested)
        assertEquals(liveSectionName, rows.single().title)
        assertEquals("Haber TV", rows.single().items.single().name)
    }

    @Test
    fun csRuntimeHandlesSectionFailureWithoutCrashing(): Unit = runBlocking {
        val runtime = CsRuntime()
        val fakeApi = object : MainAPI() {
            override var name = "FailingProvider"
            override var mainUrl = "https://failing.provider"
            override val mainPage: List<MainPageData> = listOf(
                MainPageData("Bozuk Satır", "https://failing.provider/error"),
                MainPageData("Çalışan Satır", "https://failing.provider/ok"),
            )

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
                if (request.name == "Bozuk Satır") {
                    throw IOException("Network error in section")
                }
                return newHomePageResponse(
                    request,
                    listOf(
                        newMovieSearchResponse("Başarılı Film", "https://failing.provider/ok-1", TvType.Movie),
                    ),
                    false,
                )
            }
        }

        // Hata alan satır atlanmalı, uygulama çökmemeli ve çalışan satır dönmeli
        val sections = runtime.mainPage(fakeApi, page = 1)
        assertEquals(1, sections.size)
        assertEquals("Çalışan Satır", sections[0].title)
        assertEquals("Başarılı Film", sections[0].items[0].name)
    }

    @Test
    fun csRuntimeLiveSearchFallbackKeepsOnlyLiveResponses(): Unit = runBlocking {
        val runtime = CsRuntime()
        val fakeApi = object : MainAPI() {
            override var name = "FakeLiveProvider"
            override var mainUrl = "https://live.provider"
            override val supportedTypes = setOf(TvType.Live)

            override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> = listOf(
                newLiveSearchResponse("Spor TV", "https://live.provider/channel-1", TvType.Live),
                newMovieSearchResponse("A film", "https://live.provider/movie-1", TvType.Movie),
            )
        }

        val rows = runtime.liveSearch(listOf(fakeApi), queries = listOf("live"))

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().items.size)
        assertEquals("Spor TV", rows.single().items.single().name)
    }

    @Test
    fun nuvioAddonCatalogFetchesAndParsesMetas(): Unit = runBlocking {
        val manifest = AddonManifest(
            id = "org.stremio.cinemeta",
            name = "Cinemeta",
            types = listOf("movie", "series"),
            resources = listOf(ManifestResource("stream")),
            catalogs = listOf(
                ManifestCatalog(type = "movie", id = "top", name = "Popüler Filmler"),
            ),
        )
        val addon = Addon(
            id = 1L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = "https://v3-cinemeta.strem.io/manifest.json",
            manifest = manifest,
            enabled = true,
            addedAt = 1000L,
        )

        val fakeClient = createFakeOkHttpClient { req ->
            if (req.url.toString() == "https://v3-cinemeta.strem.io/catalog/movie/top.json") {
                val json = """
                    {
                        "metas": [
                            {
                                "id": "tt0111161",
                                "type": "movie",
                                "name": "The Shawshank Redemption",
                                "poster": "https://images.metahub.space/poster/medium/tt0111161/img.jpg"
                            }
                        ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val catalog = NuvioAddonCatalog(addon, fakeClient)
        val rows = catalog.rows().first()

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("movie:top", row.id)
        assertEquals("Popüler Filmler", row.title)
        assertEquals(1, row.items.size)

        val item = row.items.first()
        assertEquals("tt0111161", item.id)
        assertEquals("The Shawshank Redemption", item.title)
        assertEquals(MediaType.Movie, item.mediaType)
        assertEquals("https://images.metahub.space/poster/medium/tt0111161/img.jpg", item.posterUrl)
    }

    @Test
    fun nuvioAddonCatalogHandlesTmdbIdAndLoadMorePagination(): Unit = runBlocking {
        val manifest = AddonManifest(
            id = "org.jasoelys.catalog.v1000",
            name = "Jasoelys",
            types = listOf("movie", "series"),
            resources = listOf(ManifestResource("catalog"), ManifestResource("meta")),
            idPrefixes = listOf("tt", "tmdb:"),
            catalogs = listOf(
                ManifestCatalog(type = "movie", id = "top", name = "Popüler Filmler"),
            ),
        )
        val addon = Addon(
            id = 2L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = "https://katalog.jasoelys.workers.dev/manifest.json",
            manifest = manifest,
            enabled = true,
            addedAt = 1000L,
        )

        val fakeClient = createFakeOkHttpClient { req ->
            when (req.url.toString()) {
                "https://katalog.jasoelys.workers.dev/catalog/movie/top.json" -> {
                    val json = """{"metas":[{"id":"tmdb:1514026","type":"movie","name":"Buddy","poster":"https://img/1.jpg"}]}"""
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(json.toResponseBody("application/json".toMediaType())).build()
                }
                "https://katalog.jasoelys.workers.dev/catalog/movie/top/skip=20.json" -> {
                    val json = """{"metas":[{"id":"tmdb:999","type":"movie","name":"Page 2 Film","poster":"https://img/2.jpg"}]}"""
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(json.toResponseBody("application/json".toMediaType())).build()
                }
                else -> {
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(404).message("Not Found")
                        .body("".toResponseBody("application/json".toMediaType())).build()
                }
            }
        }

        val catalog = NuvioAddonCatalog(addon, fakeClient)
        val rows = catalog.rows().first()
        assertEquals(1, rows.size)
        assertEquals("tmdb:1514026", rows[0].items[0].id)
        assertEquals("Buddy", rows[0].items[0].title)

        // loadMore (sayfa 2 -> skip=20)
        val page2Items = catalog.loadMore("movie:top", page = 2)
        assertEquals(1, page2Items.size)
        assertEquals("tmdb:999", page2Items[0].id)
        assertEquals("Page 2 Film", page2Items[0].title)
    }

    @Test
    fun playCatalogsServerDownHidesSectionSilently(): Unit = runBlocking {
        // Play Özel Kataloglar tohumlandığı gibi; sunucu kapalı (her istek bağlantı hatası).
        val seeded = com.kaan.play.data.addon.DefaultCatalogAddons.DEFAULTS.single()
        val addon = Addon(
            id = 5L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = seeded.url,
            manifest = seeded.manifest,
            enabled = true,
            addedAt = 1000L,
        )
        var calls = 0
        val downClient = createFakeOkHttpClient { calls++; throw IOException("Connection refused") }

        val rows = NuvioAddonCatalog(addon, downClient).rows().first()

        assertTrue(rows.isEmpty())
        assertEquals(10, calls)
        // Ana sayfa bölümü: hata/boş sonuçta raf gösterilmez (sessizce gizlenir).
        assertTrue(ProviderRules.homeSectionRows(CatalogResult.Empty).isEmpty())
        assertTrue(ProviderRules.homeSectionRows(CatalogResult.Error()).isEmpty())
    }

    private fun createFakeOkHttpClient(interceptor: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> interceptor(chain.request()) }
            .build()
    }

    @Test
    fun buildHomeProvidersPutsPlayCatalogsFirstRegardlessOfAddedOrder() {
        // Eklenme sırası: Jasoelys, İzlemeli, Play Özel Kataloglar (en son eklendi).
        val nuvioAddons = listOf(
            ProviderItem(id = "nuvio_addon:8", name = "Jasoelys", subtitle = "Nuvio · Jasoelys", kind = ProviderKind.NuvioAddon, addonId = "jasoelys"),
            ProviderItem(id = "nuvio_addon:11", name = "İzlemeli.com Katalog", subtitle = "Nuvio · İzlemeli", kind = ProviderKind.NuvioAddon, addonId = "izlemeli"),
            ProviderItem(
                id = "nuvio_addon:23", name = "Play Özel Kataloglar", subtitle = "Nuvio · Play Özel Kataloglar",
                kind = ProviderKind.NuvioAddon, addonId = "com.play.custom.catalogs",
            ),
        )
        val csPlugins = listOf(ProviderItem(id = "cs:10", name = "DiziBox", subtitle = "CloudStream", kind = ProviderKind.CloudStream))

        val result = ProviderRules.buildHomeProviders(nuvioAddons, csPlugins, setOf("cs:10"))

        // Play Özel Kataloglar önde, diğer addon'lar eklenme sırasıyla, sonra iğnelenmiş CloudStream.
        assertEquals(listOf("nuvio_addon:23", "nuvio_addon:8", "nuvio_addon:11", "cs:10"), result.map { it.id })
    }

    @Test
    fun buildHomeProvidersOrdersNuvioFirstThenPinnedCsAlphabetically() {
        val nuvioAddons = listOf(
            ProviderItem(id = "nuvio_addon:1", name = "Jasoelys", subtitle = "Nuvio · Jasoelys", kind = ProviderKind.NuvioAddon),
            ProviderItem(id = "nuvio_addon:2", name = "Cinemeta", subtitle = "Nuvio · Cinemeta", kind = ProviderKind.NuvioAddon),
        )
        val csPlugins = listOf(
            ProviderItem(id = "cs:10", name = "VidMoly", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
            ProviderItem(id = "cs:20", name = "Sinefy", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
            ProviderItem(id = "cs:30", name = "DiziBox", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
        )
        // Yalnızca cs:20 (Sinefy) ve cs:30 (DiziBox) iğnelenmiş
        val pinned = setOf("cs:20", "cs:30")

        val result = ProviderRules.buildHomeProviders(nuvioAddons, csPlugins, pinned)

        assertEquals(4, result.size)
        // 1. ve 2.: Nuvio katalog addon'ları (eklenme sırasıyla)
        assertEquals("nuvio_addon:1", result[0].id)
        assertEquals("Jasoelys", result[0].name)
        assertEquals("nuvio_addon:2", result[1].id)
        assertEquals("Cinemeta", result[1].name)
        // 3. ve 4.: İğnelenmiş CloudStream eklentileri (alfabetik: DiziBox sonra Sinefy)
        assertEquals("cs:30", result[2].id)
        assertEquals("DiziBox", result[2].name)
        assertEquals("cs:20", result[3].id)
        assertEquals("Sinefy", result[3].name)
    }

    @Test
    fun buildHomeProvidersReturnsEmptyWhenNoCatalogsOrPins() {
        val csPlugins = listOf(
            ProviderItem(id = "cs:10", name = "VidMoly", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
        )
        val result = ProviderRules.buildHomeProviders(emptyList(), csPlugins, emptySet())
        assertTrue(result.isEmpty())
    }
}
