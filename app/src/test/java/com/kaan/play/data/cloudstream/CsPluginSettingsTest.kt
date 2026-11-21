package com.kaan.play.data.cloudstream

import android.app.Activity
import android.content.Context
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CsPluginSettingsTest {
    private lateinit var directory: File

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        directory = createTempDirectory("cs-settings-").toFile()
    }

    @After fun cleanup() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    private class Db {
        val rows = MutableStateFlow<List<PluginEntity>>(emptyList())
        var addonEnabled = true
        private val addon = AddonEntity(id = 10, type = AddonType.CLOUDSTREAM_REPO,
            sourceUrl = "https://example.org/repo.json", addonId = "test", name = "Test", version = null,
            description = null, logoUrl = null, manifestJson = "{}", addedAt = 1)
        val addons = object : AddonDao {
            override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = MutableStateFlow(listOf(addon))
            override suspend fun enabledOfType(type: AddonType) = listOf(addon)
            override suspend fun countBySourceUrl(url: String) = 0
            override suspend fun countOfType(type: AddonType) = 1
            override suspend fun insert(entity: AddonEntity) = entity.id
            override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) = Unit
            override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.repoId == id } }
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) = Unit
            override suspend fun byId(id: Long) = addon.copy(enabled = addonEnabled).takeIf { it.id == id }
        }
        val plugins = object : PluginDao {
            override fun observeAll(): Flow<List<PluginEntity>> = rows
            override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> = rows
            override suspend fun ofRepo(repoId: Long) = rows.value.filter { it.repoId == repoId }
            override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
            override suspend fun enabled() = rows.value.filter { it.enabled }
            override suspend fun enabledOfType(type: AddonType) = enabled()
            override suspend fun insert(entity: PluginEntity): Long { rows.value += entity; return entity.id }
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, filename: String,
                scriptUrl: String, supportedTypes: String, contentLanguage: String, hasSettings: Boolean, sha256: String, sizeBytes: Long) = Unit
            override suspend fun setEnabled(id: Long, enabled: Boolean) { rows.value = rows.value.map { if (it.id == id) it.copy(enabled = enabled) else it } }
            override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) { rows.value = rows.value.map { if (it.repoId == repoId) it.copy(enabled = enabled) else it } }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) { rows.value = rows.value.map { it.copy(enabled = enabled) } }
            override suspend fun setAllEnabled(enabled: Boolean) = setAllEnabledOfType(AddonType.CLOUDSTREAM_REPO, enabled)
            override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
        }
    }

    private fun row(enabled: Boolean = true) = PluginEntity(id = 1, repoId = 10,
        pluginId = "OwnPlugin", name = "Own plugin", version = "4", sha256 = "artifact-4",
        scriptUrl = "https://example.org/own.cs3", installedAt = 1, enabled = enabled)

    private fun repository(db: Db, load: suspend () -> CsApiResult): CsRepository {
        val store = CsDexStore(directory)
        store.dexFile(1).writeText("exact-test-dex")
        store.manifestFile(1).writeText("""{"pluginClassName":"test.OwnPlugin"}""")
        return CsRepository(db.addons, db.plugins, ManifestFetcher { error("No network in settings test") },
            OkHttpClient(), store, object : PluginDexLoader {
                override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String) = load()
            })
    }

    private fun success(settings: ((Context) -> Unit)? = null, unload: () -> Unit = {}) =
        CsApiResult.Success(listOf(object : MainAPI() {}), CsLoadedPlugin(settings, unload))

    @Test fun `settings are lazy disabled plugins do not execute and loaded callback is reused`() = runTest {
        val db = Db().apply { rows.value = listOf(row(enabled = false)) }
        var loads = 0
        var opens = 0
        val context = Activity()
        var received: Context? = null
        val repo = repository(db) { loads++; success({ opens++; received = it }) }
        repo.plugins.first()
        assertEquals(0, loads)
        assertEquals(CsPluginSettingsResult.Disabled, repo.openSettings(1, context))
        assertTrue(repo.loadApis(1) is CsApiResult.LoadFailed)
        assertEquals(0, loads)
        repo.setEnabled(1, true)
        db.addonEnabled = false
        assertEquals(CsPluginSettingsResult.Unavailable, repo.openSettings(1, context))
        assertEquals(0, loads)
        db.addonEnabled = true
        assertEquals(CsPluginSettingsResult.Opened, repo.openSettings(1, context))
        val apis = repo.loadApis(1)
        assertEquals(CsPluginSettingsResult.Opened, repo.openSettings(1, context))
        assertSame(apis, repo.loadApis(1))
        assertSame(context, received)
        assertEquals(1, loads)
        assertEquals(2, opens)
    }

    @Test fun `unsupported callback and callback failure have honest sanitized results`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var throws = false
        val repo = repository(db) {
            if (throws) success({ throw IllegalArgumentException("private runtime details must not escape") })
            else success()
        }
        assertEquals(CsPluginSettingsResult.NotSupported, repo.openSettings(1, Activity()))
        repo.setEnabled(1, false)
        repo.setEnabled(1, true)
        throws = true
        val result = repo.openSettings(1, Activity())
        assertEquals(CsPluginSettingsResult.Failed("IllegalArgumentException"), result)
        assertFalse(result.toString().contains("private runtime"))
    }

    @Test fun `artifact and dex replacement revoke old callbacks without reusing previous version`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var loads = 0
        var unloads = 0
        val repo = repository(db) { loads++; success({}, { unloads++ }) }
        val old = repo.loadApis(1) as CsApiResult.Success
        db.rows.value = listOf(row().copy(version = "5", sha256 = "artifact-5"))
        val updated = repo.loadApis(1)
        assertNotSame(old, updated)
        assertFalse(old.plugin!!.openSettings(Activity()))
        assertEquals(1, unloads)
        File(directory, "1.dex").writeText("replacement-dex-with-different-identity")
        assertNotSame(updated, repo.loadApis(1))
        assertEquals(3, loads)
        assertEquals(2, unloads)
    }

    @Test fun `individual repository all-disable and removal unload each owned handle exactly once`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var unloads = 0
        val repo = repository(db) { success({}, { unloads++ }) }
        val first = repo.loadApis(1) as CsApiResult.Success
        repo.setEnabled(1, false)
        repo.setEnabled(1, false)
        assertFalse(first.plugin!!.openSettings(Activity()))
        assertEquals(1, unloads)
        repo.setEnabled(1, true); repo.loadApis(1); repo.setRepoEnabled(10, false)
        assertEquals(2, unloads)
        repo.setEnabled(1, true); repo.loadApis(1); repo.setAllEnabled(false)
        assertEquals(3, unloads)
        repo.setEnabled(1, true); repo.loadApis(1); repo.remove(10)
        assertEquals(4, unloads)
        assertTrue(repo.loadApis(1) is CsApiResult.PluginRowMissing)
    }

    @Test fun `disabled row during suspended load cannot publish a late callback`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var unloads = 0
        var loads = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repo = repository(db) {
            loads++
            if (loads == 1) {
                started.complete(Unit)
                finish.await()
            }
            success({}, { unloads++ })
        }
        val pending = async { repo.loadApis(1) }
        started.await()
        db.rows.value = listOf(row(enabled = false))
        finish.complete(Unit)
        assertEquals(CsApiResult.LoadFailed("PluginChangedDuringLoad", "OwnPlugin"), pending.await())
        assertEquals(1, unloads)
        repo.setEnabled(1, true)
        assertEquals(CsPluginSettingsResult.Opened, repo.openSettings(1, Activity()))
        assertEquals(2, loads)
    }

    @Test fun `queued old UI emission cannot unload a freshly loaded artifact`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var loads = 0
        var unloads = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repo = repository(db) {
            loads++
            if (loads == 1) {
                started.complete(Unit)
                finish.await()
            }
            success({}, { unloads++ })
        }
        val oldLoad = async { repo.loadApis(1) }
        started.await() // Hold the repository lock with the original artifact.
        val freshLoad = async { repo.loadApis(1) }
        runCurrent() // Queue the next load first, before the old Flow snapshot.
        val oldUiEmission = async { repo.plugins.first() }
        runCurrent()
        db.rows.value = listOf(row().copy(version = "5", sha256 = "artifact-5"))
        finish.complete(Unit)
        assertTrue(oldLoad.await() is CsApiResult.LoadFailed)
        val fresh = freshLoad.await() as CsApiResult.Success
        assertEquals("4", oldUiEmission.await().single().version)
        assertEquals(1, unloads)
        assertTrue(fresh.plugin!!.openSettings(Activity()))
        assertSame(fresh, repo.loadApis(1))
        assertEquals(2, loads)
    }

    @Test fun `cancellation is preserved and late loaded handle is released`() = runTest {
        val db = Db().apply { rows.value = listOf(row()) }
        var unloads = 0
        var cancel = true
        val repo = repository(db) {
            if (cancel) currentCoroutineContext()[Job]!!.cancel(CancellationException("cancel test load"))
            success({}, { unloads++ })
        }
        val pending = launch { repo.loadApis(1) }
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals(1, unloads)
        cancel = false
        assertTrue(repo.loadApis(1) is CsApiResult.Success)
    }
}
