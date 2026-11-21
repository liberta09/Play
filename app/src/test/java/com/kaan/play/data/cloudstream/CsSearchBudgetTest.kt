package com.kaan.play.data.cloudstream

import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.match.ContentIdentity
import com.kaan.play.data.match.ContentKind
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kaynak aramasının bellek bütçesi: az bellekli cihazda eş zamanlılık sınırı, yalnız arama için yüklenen
 * eklentilerin bırakılması ve arama sürerken doğrulama tarayıcısının açılmaması; telefonda önceki davranış.
 */
class CsSearchBudgetTest {

    private val gib = 1024L * 1024 * 1024

    @Test
    fun `cihaz sinifi - 2 GB TV kutusu az bellekli, telefon onceki davranis`() {
        assertEquals(CsSearchBudget.LowMemory, CsSearchBudget.of(isLowRamDevice = false, memoryClassMb = 256, totalMemBytes = 2 * gib))
        assertEquals(CsSearchBudget.Default, CsSearchBudget.of(isLowRamDevice = false, memoryClassMb = 512, totalMemBytes = 12 * gib))
        assertEquals(CsSearchBudget.LowMemory, CsSearchBudget.of(isLowRamDevice = true, memoryClassMb = 512, totalMemBytes = 12 * gib))
        assertEquals(CsSearchBudget.LowMemory, CsSearchBudget.of(isLowRamDevice = false, memoryClassMb = 128, totalMemBytes = 8 * gib))
        // Toplam RAM okunamadıysa (0) yalnız diğer işaretlere bakılır.
        assertEquals(CsSearchBudget.Default, CsSearchBudget.of(isLowRamDevice = false, memoryClassMb = 256, totalMemBytes = 0))
        assertEquals(6, CsSearchBudget.Default.maxParallel)
        assertFalse(CsSearchBudget.Default.releaseAfterSearch || CsSearchBudget.Default.skipChallengeSolve)
        assertEquals(null, CsSearchBudget.Default.maxResponseBytes)
    }

    @Test
    fun `az bellekte ayni anda en cok 3 eklenti aranir, telefonda 6`() = runBlocking {
        assertEquals(3, Harness(plugins = 12).search(CsSearchBudget.LowMemory).peak)
        assertEquals(6, Harness(plugins = 12).search(CsSearchBudget.Default).peak)
    }

    @Test
    fun `az bellekte yalniz arama icin yuklenen eklentiler birakilir, onceden yuklu olan kalir`() = runBlocking {
        val h = Harness(plugins = 5)
        h.repo.loadApis(1L) // ana sayfa ya da detay bu eklentiyi önceden yüklemiş
        h.search(CsSearchBudget.LowMemory)
        assertEquals(setOf(2L, 3L, 4L, 5L), h.unloaded.toSet())
        // Bırakılan eklenti gerektiğinde yeniden yüklenir.
        assertTrue(h.repo.loadApis(3L) is CsApiResult.Success)
        assertEquals(6, h.loads.get())
    }

    @Test
    fun `bellegi bitiren eklenti az bellekte kaynak aramasinda hic yuklenmez`() = runBlocking {
        assertEquals(setOf("MoreShort"), CsSearchBudget.LowMemory.skipPlugins)
        val h = Harness(plugins = 3)
        h.search(CsSearchBudget.LowMemory.copy(skipPlugins = setOf("p2")))
        assertEquals(2, h.loads.get())
        assertEquals(setOf(1L, 3L), h.unloaded.toSet())
    }

    @Test
    fun `az bellekte Java bellegi sikisikken siradaki eklenti bekler, telefonda beklemez`() {
        val max = 256L shl 20
        assertTrue(CsSearchBudget.LowMemory.heapTight(150L shl 20, max))
        assertFalse(CsSearchBudget.LowMemory.heapTight(100L shl 20, max))
        assertFalse(CsSearchBudget.Default.heapTight(250L shl 20, max))
    }

    @Test
    fun `telefonda eklentiler birakilmaz`() = runBlocking {
        val h = Harness(plugins = 5)
        h.search(CsSearchBudget.Default)
        assertTrue(h.unloaded.isEmpty())
    }

    @Test
    fun `keep dogruysa eklenti yerinde kalir`() = runBlocking {
        val h = Harness(plugins = 2)
        h.repo.useApis<Int>(1L, release = true, keep = { it > 0 }) { 1 }
        h.repo.useApis<Int>(2L, release = true, keep = { it > 0 }) { 0 }
        assertEquals(listOf(2L), h.unloaded.toList())
    }

    @Test
    fun `az bellekte arama surerken dogrulama tarayicisi acilmaz, bitince yeniden acilabilir`() = runBlocking {
        val low = Harness(plugins = 3)
        low.search(CsSearchBudget.LowMemory)
        assertTrue(low.skippedDuringSearch.all { it } && low.skippedDuringSearch.isNotEmpty())
        assertFalse(CloudflareSolver.solveSkipped())

        val phone = Harness(plugins = 3)
        phone.search(CsSearchBudget.Default)
        assertTrue(phone.skippedDuringSearch.none { it } && phone.skippedDuringSearch.isNotEmpty())
    }

    @Test
    fun `yalniz canli yayin turu olan eklenti film-dizi icin sorulmaz`() {
        assertTrue(isLiveOnly(listOf("Live")))
        assertTrue(isLiveOnly(listOf("Live Stream")))
        assertTrue(isLiveOnly(listOf("LiveStream", "NSFW")))
        assertFalse(isLiveOnly(listOf("Live", "TvSeries", "Movie"))) // ör. WioSpor, RecTV: film/dizi de verir
        assertFalse(isLiveOnly(listOf("Documentary", "Live")))
        assertFalse(isLiveOnly(listOf("Live", " Tv Series")))
        assertFalse(isLiveOnly(listOf("AsianDrama")))
        assertFalse(isLiveOnly(emptyList())) // tür bilinmiyorsa atlanmaz
    }

    @Test
    fun `kaynak aramasinda yalniz canli eklentiler yuklenmez, telefonda da`() = runBlocking {
        for (budget in listOf(CsSearchBudget.Default, CsSearchBudget.LowMemory)) {
            val h = Harness(plugins = 4, types = mapOf(2L to "Live", 3L to "Live,Movie,TvSeries"))
            h.search(budget)
            assertEquals(3, h.loads.get())
            assertEquals(setOf("P1", "P3", "P4"), h.loadedNames.toSet())
        }
    }

    /** Sahte veritabanı + yükleyici: her eklenti, aramada biraz bekleyen tek bir [MainAPI] verir. */
    private class Harness(plugins: Int, types: Map<Long, String> = emptyMap(), names: Map<Long, String> = emptyMap()) {
        val unloaded: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val skippedDuringSearch: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())
        val loads = AtomicInteger()
        val loadedNames: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val active = AtomicInteger()
        private val peakActive = AtomicInteger()

        private val rows = MutableStateFlow((1L..plugins).map { id ->
            PluginEntity(id = id, repoId = 10L, pluginId = "P$id", name = names[id] ?: "P$id", version = "1", scriptUrl = "https://example.com/$id.cs3",
                sha256 = "sha$id", enabled = true, installedAt = 1L, supportedTypes = types[id] ?: "Movie,TvSeries")
        })

        private val store = CsDexStore(File.createTempFile("cs_budget", "").also { it.delete(); it.mkdirs() }).also { s ->
            (1L..plugins).forEach { s.dexFile(it).writeBytes(byteArrayOf(1)) }
        }

        private val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult {
                loads.incrementAndGet()
                loadedNames += pluginId
                val rowId = pluginId.removePrefix("P").toLong()
                val api = object : MainAPI() {
                    override var name = pluginId
                    override suspend fun search(query: String): List<SearchResponse>? {
                        val now = active.incrementAndGet()
                        peakActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                        skippedDuringSearch += CloudflareSolver.solveSkipped()
                        delay(40)
                        active.decrementAndGet()
                        return emptyList()
                    }
                }
                return CsApiResult.Success(listOf(api), CsLoadedPlugin(unload = { unloaded += rowId }))
            }
        }

        val repo = CsRepository(
            addonDao = NoAddons,
            pluginDao = object : PluginDao {
                override fun observeAll(): Flow<List<PluginEntity>> = rows
                override suspend fun ofRepo(repoId: Long) = rows.value.filter { it.repoId == repoId }
                override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
                override suspend fun enabled() = rows.value.filter { it.enabled }
                override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> = rows
                override suspend fun enabledOfType(type: AddonType) = rows.value.filter { it.enabled }
                override suspend fun insert(entity: PluginEntity): Long = entity.id
                override suspend fun updateInfo(
                    id: Long, name: String, version: String?, description: String?,
                    filename: String, scriptUrl: String, supportedTypes: String,
                    contentLanguage: String, hasSettings: Boolean, sha256: String, sizeBytes: Long,
                ) {}
                override suspend fun setEnabled(id: Long, enabled: Boolean) {}
                override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) {}
                override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
                override suspend fun setAllEnabled(enabled: Boolean) {}
                override suspend fun delete(id: Long) {}
            },
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
        )

        class Result(val peak: Int)

        suspend fun search(budget: CsSearchBudget): Result {
            val provider = CsStreamProvider(
                repository = repo,
                runtime = CsRuntime(),
                identityResolver = { ContentIdentity.of(ContentKind.Movie, "Resident Evil", null, emptyList(), 2002, tmdbId = 1576) },
                timeoutMs = 5_000L,
                budget = budget,
            )
            provider.streams(StreamRequest(tmdbId = 1576, imdbId = null, type = MediaType.Movie)).toList()
            return Result(peakActive.get())
        }
    }

    private object NoAddons : AddonDao {
        override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = MutableStateFlow(emptyList())
        override suspend fun enabledOfType(type: AddonType) = emptyList<AddonEntity>()
        override suspend fun countBySourceUrl(url: String) = 0
        override suspend fun countOfType(type: AddonType) = 0
        override suspend fun insert(entity: AddonEntity): Long = 1L
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
        override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
        override suspend fun delete(id: Long) {}
        override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {}
        override suspend fun byId(id: Long): AddonEntity? = null
    }
}
