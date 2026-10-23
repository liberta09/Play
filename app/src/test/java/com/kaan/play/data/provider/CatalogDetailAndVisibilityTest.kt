package com.kaan.play.data.provider

import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.CatalogVisibility
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CatalogDetailAndVisibilityTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun catalogFilterAppliesToHomeAndProviderRows() = runTest {
        val manifest = AddonManifest(
            id = "org.wiospor",
            name = "WioSpor",
            types = listOf("tv"),
            resources = listOf(ManifestResource("catalog"), ManifestResource("stream")),
            catalogs = listOf(
                ManifestCatalog(type = "tv", id = "canli", name = "Canlı"),
                ManifestCatalog(type = "tv", id = "spor", name = "Spor"),
            ),
        )

        val entity = AddonEntity(
            id = 10L,
            type = AddonType.NUVIO_ADDON,
            sourceUrl = server.url("/manifest.json").toString(),
            addonId = manifest.id,
            name = manifest.name,
            version = "1.0",
            description = "Test",
            logoUrl = null,
            enabled = true,
            manifestJson = PlayJson.encodeToString(AddonManifest.serializer(), manifest),
            addedAt = 1000L,
        )

        val fakeAddonDao = object : AddonDao {
            override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = emptyFlow()
            override suspend fun enabledOfType(type: AddonType): List<AddonEntity> = emptyList()
            override suspend fun countBySourceUrl(url: String): Int = 0
            override suspend fun countOfType(type: AddonType): Int = 0
            override suspend fun insert(entity: AddonEntity): Long = entity.id
            override suspend fun setEnabled(id: Long, enabled: Boolean) {}
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
            override suspend fun delete(id: Long) {}
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {}
            override suspend fun byId(id: Long): AddonEntity? = if (id == 10L) entity else null
        }

        server.enqueue(json("""{"metas":[{"id":"ch1","name":"Kanal 1","type":"tv"}]}"""))
        server.enqueue(json("""{"metas":[{"id":"ch2","name":"Kanal 2","type":"tv"}]}"""))

        var disabled = emptySet<String>()
        val repo = CatalogRepository(
            addonDao = fakeAddonDao,
            pluginDao = null,
            csRepository = null,
            csRuntime = null,
            httpClient = OkHttpClient(),
            getDisabledCatalogs = { disabled },
        )

        // 1. Varsayılan (hepsi açık)
        val res1 = repo.loadCatalog(setOf("nuvio_addon:10"))
        assertTrue(res1 is CatalogResult.Success)
        val rows1 = (res1 as CatalogResult.Success).rows
        assertEquals(2, rows1.size)
        assertEquals(listOf("tv:canli", "tv:spor"), rows1.map { it.id })

        // 2. "tv:spor" kapatıldığında (cache'ten okunurken filtrelenir)
        disabled = setOf(CatalogVisibility.key(10L, "tv", "spor"))
        val res2 = repo.loadCatalog(setOf("nuvio_addon:10"))
        assertTrue(res2 is CatalogResult.Success)
        val rows2 = (res2 as CatalogResult.Success).rows
        assertEquals(1, rows2.size)
        assertEquals("tv:canli", rows2.first().id)

        // 3. "tv:canli" da kapatıldığında -> Empty döner
        disabled = setOf(CatalogVisibility.key(10L, "tv", "canli"), CatalogVisibility.key(10L, "tv", "spor"))
        val res3 = repo.loadCatalog(setOf("nuvio_addon:10"))
        assertEquals(CatalogResult.Empty, res3)

        // 4. loadMore da kapalı katalogda boş döner
        val moreHidden = repo.loadMore("nuvio_addon:10", "tv:spor", 2)
        assertTrue(moreHidden.isEmpty())
    }

    @Test
    fun catalogItemCountFetchHandlesErrorsAndTimeoutGracefully() = runTest {
        // Hata durumunda (500)
        server.enqueue(MockResponse.Builder().code(500).build())
        val client = OkHttpClient()

        var failed = false
        try {
            val url = server.url("/catalog/tv/bad.json")
            fetchAddonText(client, url, 1.seconds, 1024L)
        } catch (_: Throwable) {
            failed = true
        }
        assertTrue("500 hatası yakalanmalı ve uygulama çökmemeli", failed)

        // Zaman aşımı durumunda
        server.enqueue(
            MockResponse.Builder()
                .headersDelay(2, TimeUnit.SECONDS)
                .body("""{"metas":[]}""")
                .build()
        )
        var timedOut = false
        try {
            val url = server.url("/catalog/tv/slow.json")
            fetchAddonText(client, url, 200.milliseconds, 1024L)
        } catch (_: Throwable) {
            timedOut = true
        }
        assertTrue("Zaman aşımı yakalanmalı ve uygulama çökmemeli", timedOut)
    }
    @Test
    fun whenCacheIsPopulatedAndCatalogIsDisabledRowsAreFilteredAndEmptyHidesSection() = runTest {
        val cache = CatalogCache()
        val row1 = CatalogRow(id = "tv:canli", title = "Canlı", providerId = "nuvio_addon:10", providerName = "WioSpor", items = emptyList())
        val row2 = CatalogRow(id = "tv:spor", title = "Spor", providerId = "nuvio_addon:10", providerName = "WioSpor", items = emptyList())
        cache.put("nuvio_addon:10", listOf(row1, row2))

        // Önbellek doluyken:
        val cached = cache.get("nuvio_addon:10")
        org.junit.Assert.assertNotNull(cached)
        assertEquals(2, cached!!.size)

        // 1. tv:spor kapatıldığında filtrelenmiş satırlar yalnızca tv:canli içerir
        var disabled = setOf(CatalogVisibility.key(10L, "tv", "spor"))
        val visible1 = CatalogVisibility.filterRows(cached, disabled)
        assertEquals(1, visible1.size)
        assertEquals("tv:canli", visible1.first().id)

        // 2. Her iki katalog kapatıldığında boş döner (raf gizlenir)
        disabled = setOf(CatalogVisibility.key(10L, "tv", "spor"), CatalogVisibility.key(10L, "tv", "canli"))
        val visible2 = CatalogVisibility.filterRows(cached, disabled)
        assertTrue(visible2.isEmpty())

        // 3. removeForAddon çağrılınca önbellek temizlenir
        cache.removeForAddon(10L)
        org.junit.Assert.assertNull(cache.get("nuvio_addon:10"))
    }
}
