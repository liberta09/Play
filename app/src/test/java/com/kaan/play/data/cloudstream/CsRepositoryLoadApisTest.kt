package com.kaan.play.data.cloudstream

import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CsRepositoryLoadApisTest {

    private class TestDb {
        val pluginRows = MutableStateFlow<List<PluginEntity>>(emptyList())

        val addons = object : AddonDao {
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

        val plugins = object : PluginDao {
            override fun observeAll(): Flow<List<PluginEntity>> = pluginRows
            override suspend fun ofRepo(repoId: Long) = pluginRows.value.filter { it.repoId == repoId }
            override suspend fun byId(id: Long) = pluginRows.value.firstOrNull { it.id == id }
            override suspend fun enabled() = pluginRows.value.filter { it.enabled }
            override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> = pluginRows
            override suspend fun enabledOfType(type: AddonType) = pluginRows.value.filter { it.enabled }
            override suspend fun insert(entity: PluginEntity): Long {
                pluginRows.value += entity
                return entity.id
            }
            override suspend fun updateInfo(
                id: Long, name: String, version: String?, description: String?,
                filename: String, scriptUrl: String, supportedTypes: String,
                contentLanguage: String, hasSettings: Boolean, sha256: String, sizeBytes: Long
            ) {}
            override suspend fun setEnabled(id: Long, enabled: Boolean) {}
            override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) {}
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
            override suspend fun setAllEnabled(enabled: Boolean) {}
            override suspend fun delete(id: Long) {}
        }
    }

    private fun sampleEntity(id: Long = 1L, pluginId: String = "TestPlugin") = PluginEntity(
        id = id,
        repoId = 10L,
        pluginId = pluginId,
        name = "Test Plugin",
        version = "1.0",
        scriptUrl = "https://example.com/test.cs3",
        sha256 = "dummy-sha256",
        installedAt = 1000L,
    )

    @Test
    fun `plugin row yoksa PluginRowMissing doner`() = runTest {
        val db = TestDb()
        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.NoApiRegistered(pluginId)
        }
        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
        )

        val result = repo.loadApis(999L)
        assertTrue(result is CsApiResult.PluginRowMissing)
        assertEquals(999L, (result as CsApiResult.PluginRowMissing).rowId)
    }

    @Test
    fun `dex yoksa depodan BIR kez yeniden indirir ve basariliysa yukler`() = runTest {
        val db = TestDb()
        val entity = sampleEntity(id = 1L, pluginId = "AutoFixPlugin")
        db.plugins.insert(entity)

        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        var downloadCount = 0

        val fakeDownloader = Cs3Downloader { _, _, pluginId ->
            downloadCount++
            store.dexFile(pluginId).writeBytes("fake dex bytes".toByteArray())
            store.manifestFile(pluginId).writeText("""{"pluginClassName":"com.example.TestPlugin"}""")
            Cs3Contents("dummy-sha", 123L, "{}")
        }

        val fakeApi = object : MainAPI() {
            override var name = "AutoFixAPI"
        }
        val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult {
                return if (dexFile.exists()) CsApiResult.Success(listOf(fakeApi)) else CsApiResult.DexFileMissing(pluginId)
            }
        }

        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
            cs3Downloader = fakeDownloader,
        )

        val result = repo.loadApis(1L)
        assertEquals("Yeniden indirme tam 1 kez çağrılmalı", 1, downloadCount)
        assertTrue("Sonuç Success olmalı", result is CsApiResult.Success)
        assertEquals(1, (result as CsApiResult.Success).apis.size)
        assertEquals("AutoFixAPI", result.apis.first().name)
    }

    @Test
    fun `dex yoksa ve yeniden indirme basarisizsa tek sefer dener ikinci kez denemez`() = runTest {
        val db = TestDb()
        val entity = sampleEntity(id = 2L, pluginId = "BrokenPlugin")
        db.plugins.insert(entity)

        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        var downloadCount = 0

        val fakeDownloader = Cs3Downloader { _, _, _ ->
            downloadCount++
            throw RuntimeException("Ağ hatası: indirme başarısız")
        }

        val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.DexFileMissing(pluginId)
        }

        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
            cs3Downloader = fakeDownloader,
        )

        val result = repo.loadApis(2L)
        assertEquals("Başarısızlıkta da tek sefer denenmeli", 1, downloadCount)
        assertTrue(result is CsApiResult.DexFileMissing)
        assertEquals("BrokenPlugin", (result as CsApiResult.DexFileMissing).pluginId)
    }

    @Test
    fun `allowRedownload false ise indirme hic denenmez`() = runTest {
        val db = TestDb()
        val entity = sampleEntity(id = 3L, pluginId = "NoRetryPlugin")
        db.plugins.insert(entity)

        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        var downloadCount = 0

        val fakeDownloader = Cs3Downloader { _, _, _ ->
            downloadCount++
            Cs3Contents("dummy-sha", 123L, "{}")
        }

        val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.DexFileMissing(pluginId)
        }

        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
            cs3Downloader = fakeDownloader,
        )

        val result = repo.loadApis(3L, allowRedownload = false)
        assertEquals("allowRedownload=false iken indirme çağrılmamalı", 0, downloadCount)
        assertTrue(result is CsApiResult.DexFileMissing)
    }

    @Test
    fun `dex mevcutken loader hatalari dogru iletilir`() = runTest {
        val db = TestDb()
        val entity = sampleEntity(id = 4L, pluginId = "IncompatiblePlugin")
        db.plugins.insert(entity)

        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        store.dexFile(4L).writeBytes("dex".toByteArray())

        val loaderIncompatible = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.IncompatibleLibrary("NoSuchMethodError", pluginId)
        }

        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loaderIncompatible,
        )

        val result = repo.loadApis(4L)
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        val failure = result as CsApiResult.IncompatibleLibrary
        assertEquals("NoSuchMethodError", failure.causeClass)
        assertEquals("IncompatiblePlugin", failure.pluginId)
    }

    @Test
    fun `dex mevcutken api kaydi yoksa NoApiRegistered iletilir`() = runTest {
        val db = TestDb()
        val entity = sampleEntity(id = 5L, pluginId = "EmptyPlugin")
        db.plugins.insert(entity)

        val tempDir = File.createTempFile("cs_store_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)
        store.dexFile(5L).writeBytes("dex".toByteArray())

        val loaderEmpty = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.NoApiRegistered(pluginId)
        }

        val repo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { "" },
            httpClient = OkHttpClient(),
            store = store,
            loader = loaderEmpty,
        )

        val result = repo.loadApis(5L)
        assertTrue(result is CsApiResult.NoApiRegistered)
        assertEquals("EmptyPlugin", (result as CsApiResult.NoApiRegistered).pluginId)
    }
}
