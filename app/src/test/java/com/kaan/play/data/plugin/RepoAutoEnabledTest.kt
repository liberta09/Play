package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.Cs3Contents
import com.kaan.play.data.cloudstream.Cs3Downloader
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.cloudstream.CsDexStore
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.PluginDexLoader
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kullanıcı kararı (2026-10-23):
 * Yeni eklenen depolarda (Nuvio ve CloudStream) kurulan eklentiler varsayılan olarak AÇIK gelir.
 * Güncelleme mevcut açık/kapalı durumunu korur; yeni eklenen eklentiler açık gelir.
 * Deep link onaysız ekleme yapmaz.
 */
class RepoAutoEnabledTest {

    private class FakeDb {
        private val lock = Any()
        val addonRows = MutableStateFlow<List<AddonEntity>>(emptyList())
        val pluginRows = MutableStateFlow<List<PluginEntity>>(emptyList())
        private var addonSeq = 1L
        private var pluginSeq = 1L

        val addons = object : AddonDao {
            override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = addonRows.map { l -> l.filter { it.type == type } }
            override suspend fun enabledOfType(type: AddonType) = synchronized(lock) { addonRows.value.filter { it.enabled && it.type == type } }
            override suspend fun countBySourceUrl(url: String) = synchronized(lock) { addonRows.value.count { it.sourceUrl == url } }
            override suspend fun countOfType(type: AddonType) = synchronized(lock) { addonRows.value.count { it.type == type } }
            override suspend fun insert(entity: AddonEntity): Long = synchronized(lock) {
                if (addonRows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
                val id = addonSeq++
                addonRows.value = addonRows.value + entity.copy(id = id)
                id
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) = synchronized(lock) {
                addonRows.value = addonRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) = synchronized(lock) {
                addonRows.value = addonRows.value.map { if (it.type == type) it.copy(enabled = enabled) else it }
            }
            override suspend fun delete(id: Long) = synchronized(lock) {
                addonRows.value = addonRows.value.filterNot { it.id == id }
                pluginRows.value = pluginRows.value.filterNot { it.repoId == id }
            }
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) = synchronized(lock) {
                addonRows.value = addonRows.value.map {
                    if (it.id == id) it.copy(name = name, version = version, description = description, manifestJson = manifestJson) else it
                }
            }
            override suspend fun byId(id: Long) = synchronized(lock) { addonRows.value.firstOrNull { it.id == id } }
        }

        val plugins = object : PluginDao {
            override fun observeAll(): Flow<List<PluginEntity>> = pluginRows
            override suspend fun ofRepo(repoId: Long) = synchronized(lock) { pluginRows.value.filter { it.repoId == repoId } }
            override suspend fun byId(id: Long) = synchronized(lock) { pluginRows.value.firstOrNull { it.id == id } }
            override suspend fun enabled() = synchronized(lock) { pluginRows.value.filter { it.enabled } }
            override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> =
                combine(pluginRows, addonRows) { pList, aList ->
                    val repoIds = aList.filter { it.type == type }.map { it.id }.toSet()
                    pList.filter { it.repoId in repoIds }
                }
            override suspend fun enabledOfType(type: AddonType): List<PluginEntity> = synchronized(lock) {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                pluginRows.value.filter { it.enabled && it.repoId in repoIds }
            }
            override suspend fun insert(entity: PluginEntity): Long = synchronized(lock) {
                if (pluginRows.value.any { it.repoId == entity.repoId && it.pluginId == entity.pluginId }) return -1
                val id = pluginSeq++
                pluginRows.value = pluginRows.value + entity.copy(id = id)
                id
            }
            override suspend fun updateInfo(
                id: Long, name: String, version: String?, description: String?, filename: String, scriptUrl: String,
                supportedTypes: String, contentLanguage: String, hasSettings: Boolean, sha256: String, sizeBytes: Long,
            ) = synchronized(lock) {
                pluginRows.value = pluginRows.value.map {
                    if (it.id == id) it.copy(
                        name = name, version = version, description = description, filename = filename, scriptUrl = scriptUrl,
                        supportedTypes = supportedTypes, contentLanguage = contentLanguage, hasSettings = hasSettings,
                        sha256 = sha256, sizeBytes = sizeBytes,
                    ) else it
                }
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) = synchronized(lock) {
                pluginRows.value = pluginRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) = synchronized(lock) {
                pluginRows.value = pluginRows.value.map { if (it.repoId == repoId) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) = synchronized(lock) {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                pluginRows.value = pluginRows.value.map { if (it.repoId in repoIds) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabled(enabled: Boolean) = synchronized(lock) {
                pluginRows.value = pluginRows.value.map { it.copy(enabled = enabled) }
            }
            override suspend fun delete(id: Long) = synchronized(lock) {
                pluginRows.value = pluginRows.value.filterNot { it.id == id }
            }
        }
    }

    private class FakeStore : PluginCodeStore {
        val files = HashMap<Long, String>()
        override fun write(pluginId: Long, code: String) { files[pluginId] = code }
        override fun read(pluginId: Long) = files[pluginId]
        override fun delete(pluginId: Long) { files.remove(pluginId) }
    }

    private val nuvioGoodCode = "async function getStreams() { return []; } module.exports = { getStreams };"

    @Test
    fun newNuvioRepoInstallsAllPluginsEnabled() = runTest {
        val db = FakeDb()
        val store = FakeStore()
        val codeMap = mapOf(
            "https://example.com/repo/p1.js" to nuvioGoodCode,
            "https://example.com/repo/p2.js" to nuvioGoodCode,
        )
        val manifest = """{"name":"Nuvio Test Repo","version":"1.0.0","scrapers":[
            {"id":"p1","name":"Plugin One","version":"1.0.0","filename":"p1.js"},
            {"id":"p2","name":"Plugin Two","version":"1.0.0","filename":"p2.js"}
        ]}"""
        val repo = PluginRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { manifest },
            codeFetcher = PluginCodeFetcher { url, _ -> codeMap[url] ?: throw Exception("404") },
            store = store,
        )

        val result = repo.addFetched("https://example.com/repo/manifest.json", manifest)
        assertEquals(2, result.installed)

        val installedPlugins = db.pluginRows.value
        assertEquals(2, installedPlugins.size)
        // Kullanıcı kararı 2026-10-23: yeni Nuvio deposundaki eklentiler açık kurulmalı
        assertTrue("Tüm eklentiler AÇIK kurulmalı", installedPlugins.all { it.enabled })
        assertEquals(setOf("p1", "p2"), installedPlugins.map { it.pluginId }.toSet())
    }

    @Test
    fun newCloudStreamRepoInstallsAllPluginsEnabled() = runTest {
        val db = FakeDb()
        val tempDir = File.createTempFile("cs_repo_test", "").also { it.delete(); it.mkdirs() }
        val store = CsDexStore(tempDir)

        val repoManifestJson = """{"name":"CS Test Repo","manifestVersion":1,"pluginLists":["https://example.com/cs/plugins.json"]}"""
        val pluginListJson = """[
            {"name":"CS Plugin 1","internalName":"Cs1","version":1,"url":"https://example.com/cs/cs1.cs3","tvTypes":["Movie"]},
            {"name":"CS Plugin 2","internalName":"Cs2","version":2,"url":"https://example.com/cs/cs2.cs3","tvTypes":["TvSeries"]}
        ]"""

        val fetcher = ManifestFetcher { url ->
            if ("plugins.json" in url) pluginListJson else repoManifestJson
        }

        val cs3Downloader = Cs3Downloader { _, _, targetId ->
            store.dexFile(targetId).writeBytes("dummy dex".toByteArray())
            store.manifestFile(targetId).writeText("""{"pluginClassName":"com.example.CsPlugin"}""")
            Cs3Contents(sha256 = "dummy-sha", sizeBytes = 100L, pluginManifest = "{}")
        }

        val loader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.NoApiRegistered(pluginId)
        }

        val csRepo = CsRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = fetcher,
            httpClient = OkHttpClient(),
            store = store,
            loader = loader,
            cs3Downloader = cs3Downloader,
        )

        val result = csRepo.addFetched("https://example.com/cs/repo.json", repoManifestJson)
        assertEquals(2, result.installed)

        val plugins = db.pluginRows.value
        assertEquals(2, plugins.size)
        // Kullanıcı kararı 2026-10-23: yeni CloudStream deposundaki eklentiler açık kurulmalı
        assertTrue("Tüm CS eklentileri AÇIK kurulmalı", plugins.all { it.enabled })
        assertEquals(setOf("Cs1", "Cs2"), plugins.map { it.pluginId }.toSet())
    }

    @Test
    fun repoUpdatePreservesExistingEnabledStateAndNewPluginsComeEnabled() = runTest {
        val db = FakeDb()
        val store = FakeStore()
        val codeMap = HashMap<String, String>().apply {
            put("https://example.com/repo/p1.js", nuvioGoodCode)
            put("https://example.com/repo/p2.js", nuvioGoodCode)
            put("https://example.com/repo/p3.js", nuvioGoodCode)
        }

        val initialManifest = """{"name":"Nuvio Repo","version":"1.0.0","scrapers":[
            {"id":"p1","name":"Plugin 1","version":"1.0.0","filename":"p1.js"},
            {"id":"p2","name":"Plugin 2","version":"1.0.0","filename":"p2.js"}
        ]}"""

        val repo = PluginRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { initialManifest },
            codeFetcher = PluginCodeFetcher { url, _ -> codeMap[url] ?: throw Exception("404") },
            store = store,
        )

        repo.addFetched("https://example.com/repo/manifest.json", initialManifest)
        val p1 = db.pluginRows.value.first { it.pluginId == "p1" }
        val p2 = db.pluginRows.value.first { it.pluginId == "p2" }

        // p1 açık kalsın (true), p2 kullanıcı tarafından kapatılsın (false)
        repo.setPluginEnabled(p2.id, false)
        assertTrue(db.pluginRows.value.first { it.id == p1.id }.enabled)
        assertFalse(db.pluginRows.value.first { it.id == p2.id }.enabled)

        // Güncelleme: p1 sürüm arttı, p2 sürüm arttı, p3 depoya YENİ eklendi
        val updatedManifest = """{"name":"Nuvio Repo","version":"1.1.0","scrapers":[
            {"id":"p1","name":"Plugin 1","version":"1.1.0","filename":"p1.js"},
            {"id":"p2","name":"Plugin 2","version":"1.1.0","filename":"p2.js"},
            {"id":"p3","name":"Plugin 3","version":"1.0.0","filename":"p3.js"}
        ]}"""

        val repoWithUpdate = PluginRepository(
            addonDao = db.addons,
            pluginDao = db.plugins,
            fetcher = ManifestFetcher { updatedManifest },
            codeFetcher = PluginCodeFetcher { url, _ -> codeMap[url] ?: throw Exception("404") },
            store = store,
        )

        val plan = repoWithUpdate.checkForUpdates(p1.repoId)
        assertEquals(listOf("Plugin 1", "Plugin 2"), plan.changed)
        assertEquals(listOf("Plugin 3"), plan.added)

        val updateResult = repoWithUpdate.applyUpdate(plan)
        assertEquals(3, updateResult.updated)

        val rows = db.pluginRows.value.associateBy { it.pluginId }
        // p1 önceden açıktı -> güncellemede AÇIK kalmalı
        assertTrue("Mevcut açık eklenti açık kalmalı", rows.getValue("p1").enabled)
        // p2 önceden kapalıydı -> güncellemede KAPALI kalmalı
        assertFalse("Mevcut kapalı eklenti kapalı kalmalı", rows.getValue("p2").enabled)
        // p3 güncelleme ile depoya yeni geldi -> yeni kurulum sayılır ve AÇIK gelmeli
        assertTrue("Depoya sonradan eklenen yeni eklenti AÇIK gelmeli", rows.getValue("p3").enabled)
    }

    @Test
    fun deepLinkWithoutConfirmationDoesNotAddAnyRepoOrPlugin() = runTest {
        val db = FakeDb()
        val store = FakeStore()
        val manifestFetcher = ManifestFetcher {
            """{"name":"DeepLink Repo","version":"1.0.0","scrapers":[]}"""
        }
        val addonRepo = AddonRepository(db.addons, manifestFetcher)
        val plugins = PluginRepository(db.addons, db.plugins, manifestFetcher, { _, _ -> "" }, store)
        val csRepos = CsRepository(db.addons, db.plugins, manifestFetcher, OkHttpClient(), CsDexStore(File.createTempFile("cs_temp", "").also { it.delete(); it.mkdirs() }), object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String) = CsApiResult.NoApiRegistered(pluginId)
        })
        val installer = SourceInstaller(addonRepo, plugins, csRepos, manifestFetcher)
        val handler = DeepLinkHandler(installer, this)

        // Bağlantı handle edildiğinde yalnızca onay bekleyen yük (pending) oluşur
        handler.handle("play", "add", null, null, "https://example.com/repo/manifest.json")
        assertNotNull("Onay bekleyen yük oluşmalı", handler.pending.value)
        assertEquals(0, db.addonRows.value.size)
        assertEquals(0, db.pluginRows.value.size)

        // Kullanıcı reddederse (dismiss) hiçbir şey eklenmez
        handler.dismiss()
        assertNull("Pending temizlenmeli", handler.pending.value)
        assertEquals(0, db.addonRows.value.size)
        assertEquals(0, db.pluginRows.value.size)
    }
}
