package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.cloudstream.CsDexStore
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.PluginDexLoader
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogInstallCoordinatorTest {

    private class TestDb {
        val addonRows = MutableStateFlow<List<AddonEntity>>(emptyList())
        val pluginRows = MutableStateFlow<List<PluginEntity>>(emptyList())
        private var addonSeq = 1L
        private var pluginSeq = 1L

        val addons = object : AddonDao {
            override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> =
                addonRows.map { l -> l.filter { it.type == type } }
            override suspend fun enabledOfType(type: AddonType) = addonRows.value.filter { it.enabled && it.type == type }
            override suspend fun countBySourceUrl(url: String) = addonRows.value.count { it.sourceUrl == url }
            override suspend fun countOfType(type: AddonType) = addonRows.value.count { it.type == type }
            override suspend fun insert(entity: AddonEntity): Long {
                if (addonRows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
                val id = addonSeq++
                addonRows.value += entity.copy(id = id)
                return id
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) {
                addonRows.value = addonRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
                addonRows.value = addonRows.value.map { if (it.type == type) it.copy(enabled = enabled) else it }
            }
            override suspend fun delete(id: Long) {
                addonRows.value = addonRows.value.filterNot { it.id == id }
                pluginRows.value = pluginRows.value.filterNot { it.repoId == id }
            }
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {
                addonRows.value = addonRows.value.map {
                    if (it.id == id) it.copy(name = name, version = version, description = description, manifestJson = manifestJson) else it
                }
            }
            override suspend fun byId(id: Long) = addonRows.value.firstOrNull { it.id == id }
        }

        val plugins = object : PluginDao {
            override fun observeAll(): Flow<List<PluginEntity>> = pluginRows
            override suspend fun ofRepo(repoId: Long) = pluginRows.value.filter { it.repoId == repoId }
            override suspend fun byId(id: Long) = pluginRows.value.firstOrNull { it.id == id }
            override suspend fun enabled() = pluginRows.value.filter { it.enabled }
            override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> =
                combine(pluginRows, addonRows) { plugins, addons ->
                    val repoIds = addons.filter { it.type == type }.map { it.id }.toSet()
                    plugins.filter { it.repoId in repoIds }
                }
            override suspend fun enabledOfType(type: AddonType): List<PluginEntity> {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                return pluginRows.value.filter { it.enabled && it.repoId in repoIds }
            }
            override suspend fun insert(entity: PluginEntity): Long {
                val id = pluginSeq++
                pluginRows.value += entity.copy(id = id)
                return id
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { if (it.repoId == repoId) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                pluginRows.value = pluginRows.value.map { if (it.repoId in repoIds) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabled(enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { it.copy(enabled = enabled) }
            }
            override suspend fun delete(id: Long) {
                pluginRows.value = pluginRows.value.filterNot { it.id == id }
            }
            override suspend fun updateInfo(
                id: Long, name: String, version: String?, description: String?, filename: String,
                scriptUrl: String, supportedTypes: String, contentLanguage: String, hasSettings: Boolean,
                sha256: String, sizeBytes: Long,
            ) {
                pluginRows.value = pluginRows.value.map {
                    if (it.id == id) it.copy(
                        name = name, version = version, description = description, filename = filename,
                        scriptUrl = scriptUrl, supportedTypes = supportedTypes, contentLanguage = contentLanguage,
                        hasSettings = hasSettings, sha256 = sha256, sizeBytes = sizeBytes,
                    ) else it
                }
            }
        }
    }

    private class TestEnv {
        val db = TestDb()
        val networkData = mutableMapOf<String, String>()
        val fetcher = ManifestFetcher { url ->
            networkData[url] ?: throw AddonException(Kind.Http, "404")
        }
        val codeFetcher = PluginCodeFetcher { url, _ ->
            networkData[url] ?: "// stub\nasync function getStreams(){ return []; }\nmodule.exports = { getStreams };"
        }
        val codeStore = object : PluginCodeStore {
            private val files = mutableMapOf<Long, String>()
            override fun write(pluginId: Long, code: String) { files[pluginId] = code }
            override fun read(pluginId: Long): String? = files[pluginId]
            override fun delete(pluginId: Long) { files.remove(pluginId) }
        }
        val csDexDir = File.createTempFile("csdex_test", "").also { it.delete(); it.mkdirs() }
        val csLoader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult =
                CsApiResult.NoApiRegistered(pluginId)
        }

        val addonRepo = AddonRepository(db.addons, fetcher, clock = { 1000L })
        val pluginRepo = PluginRepository(db.addons, db.plugins, fetcher, codeFetcher, codeStore)
        val csRepo = CsRepository(db.addons, db.plugins, fetcher, OkHttpClient(), CsDexStore(csDexDir), csLoader)
        val installer = SourceInstaller(addonRepo, pluginRepo, csRepo, fetcher)
    }

    @Test
    fun installPluginRepoWithSourceTypeNuvioRepoSucceedsWithoutKindMismatch() = runTest {
        val env = TestEnv()
        val coordinator = CatalogInstallCoordinator(this, env.installer)

        // BronzeCloud / Manitux benzeri scrapers içeren Nuvio eklenti deposu
        val repoUrl = "https://raw.githubusercontent.com/dr-octagon/Nuvio/main/repo.json"
        val manifest = """
            {
                "name": "Bronze Cloud",
                "version": "1.0.0",
                "description": "Bronze Cloud scraper repo",
                "scrapers": [
                    {
                        "id": "bronze_scraper",
                        "name": "Bronze Scraper",
                        "version": "1.0.0",
                        "filename": "bronze.js"
                    }
                ]
            }
        """.trimIndent()
        env.networkData[repoUrl] = manifest
        env.networkData["https://raw.githubusercontent.com/dr-octagon/Nuvio/main/bronze.js"] =
            "async function getStreams(){ return []; } module.exports = { getStreams };"

        // central_repos.json'dan nuvioKind=addon geldiği için sourceType=NUVIO_REPO verilmiş olsun
        val started = coordinator.install(repoUrl, SourceType.NUVIO_REPO)
        assertTrue("Kurulum başlatılmalı", started)

        var outcome: CatalogInstallOutcome? = null
        for (i in 0 until 100) {
            testScheduler.advanceUntilIdle()
            outcome = coordinator.outcome.value
            if (outcome != null) break
            Thread.sleep(50)
        }
        assertTrue("Kurulum Success olmalı, gerçekte: $outcome", outcome is CatalogInstallOutcome.Success)
        val success = outcome as CatalogInstallOutcome.Success
        assertEquals(repoUrl, success.url)
        assertTrue("Sonuç PluginRepo olmalı", success.result is AddResult.PluginRepo)
        val pluginResult = success.result as AddResult.PluginRepo
        assertEquals("Bronze Cloud", pluginResult.name)
        assertEquals(1, pluginResult.installed)
        assertNull("installingUrl sıfırlanmış olmalı", coordinator.installingUrl.value)
    }

    @Test
    fun installStandardAddonSucceeds() = runTest {
        val env = TestEnv()
        val coordinator = CatalogInstallCoordinator(this, env.installer)

        val addonUrl = "https://torrentio.strem.fun/manifest.json"
        val manifest = """
            {
                "id": "com.stremio.torrentio.addon",
                "name": "Torrentio",
                "version": "1.0.14",
                "description": "Torrent video streams provider",
                "types": ["movie", "series"],
                "resources": ["stream"]
            }
        """.trimIndent()
        env.networkData[addonUrl] = manifest

        val started = coordinator.install(addonUrl, SourceType.NUVIO_REPO)
        assertTrue(started)

        var outcome: CatalogInstallOutcome? = null
        for (i in 0 until 100) {
            testScheduler.advanceUntilIdle()
            outcome = coordinator.outcome.value
            if (outcome != null) break
            Thread.sleep(50)
        }
        assertTrue("Sonuç Success olmalı", outcome is CatalogInstallOutcome.Success)
        val success = outcome as CatalogInstallOutcome.Success
        assertTrue("Sonuç Addon olmalı", success.result is AddResult.Addon)
        assertEquals("Torrentio", (success.result as AddResult.Addon).name)
    }

    @Test
    fun clearOutcomeClearsState() = runTest {
        val env = TestEnv()
        val coordinator = CatalogInstallCoordinator(this, env.installer)
        val url = "https://example.com/manifest.json"
        env.networkData[url] = """
            {
                "id": "com.example.addon",
                "name": "Example",
                "version": "1.0.0",
                "types": ["movie"],
                "resources": ["stream"]
            }
        """.trimIndent()

        coordinator.install(url, SourceType.NUVIO_REPO)
        testScheduler.advanceUntilIdle()
        assertTrue(coordinator.outcome.value is CatalogInstallOutcome.Success)

        coordinator.clearOutcome()
        assertNull(coordinator.outcome.value)
    }
}
