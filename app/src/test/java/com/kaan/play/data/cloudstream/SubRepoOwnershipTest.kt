package com.kaan.play.data.cloudstream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.plugin.ManifestKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubRepoOwnershipTest {

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FakeAddonDao : AddonDao {
        val rows = MutableStateFlow<List<AddonEntity>>(emptyList())
        private var seq = 1L
        override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = rows.map { it.filter { r -> r.type == type } }
        override suspend fun enabledOfType(type: AddonType) = rows.value.filter { it.enabled && it.type == type }
        override suspend fun countBySourceUrl(url: String) = rows.value.count { it.sourceUrl == url }
        override suspend fun countOfType(type: AddonType) = rows.value.count { it.type == type }
        override suspend fun insert(entity: AddonEntity): Long {
            if (rows.value.any { it.sourceUrl == entity.sourceUrl }) return -1L
            val id = seq++
            rows.value += entity.copy(id = id)
            return id
        }
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
        override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
        override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {}
        override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
    }

    private class FakePluginDao : PluginDao {
        private val rows = MutableStateFlow<List<PluginEntity>>(emptyList())
        private var seq = 1L
        override fun observeAll(): Flow<List<PluginEntity>> = rows
        override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> = rows
        override suspend fun enabled(): List<PluginEntity> = emptyList()
        override suspend fun enabledOfType(type: AddonType): List<PluginEntity> = emptyList()
        override suspend fun ofRepo(repoId: Long) = rows.value.filter { it.repoId == repoId }
        override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }
        override suspend fun insert(entity: PluginEntity): Long { val id = seq++; rows.value += entity.copy(id = id); return id }
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
        override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) {}
        override suspend fun setAllEnabled(enabled: Boolean) {}
        override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
        override suspend fun updateInfo(
            id: Long,
            name: String,
            version: String?,
            description: String?,
            filename: String,
            scriptUrl: String,
            supportedTypes: String,
            contentLanguage: String,
            hasSettings: Boolean,
            sha256: String,
            sizeBytes: Long,
        ) {}
        override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    }

    @Test
    fun removableChildrenExcludesManualAndSharedAndHandlesCycles() {
        var state = SubRepoOwnershipState()
        val parentA = "https://a.example/repo.json"
        val parentB = "https://b.example/repo.json"
        val onlyA = "https://only-a.example/repo.json"
        val sharedAB = "https://shared.example/repo.json"
        val manualAndA = "https://manual.example/repo.json"

        state = SubRepoOwnershipRules.recordManual(state, parentA)
        state = SubRepoOwnershipRules.recordManual(state, parentB)
        state = SubRepoOwnershipRules.recordManual(state, manualAndA)
        state = SubRepoOwnershipRules.recordSubRepos(state, parentA, listOf(onlyA, sharedAB, manualAndA, parentB))
        // Döngü + ortak sahiplik: B hem A'ya hem shared'a referans verir
        state = SubRepoOwnershipRules.recordSubRepos(state, parentB, listOf(sharedAB, parentA))

        val installed = listOf(parentA, parentB, onlyA, sharedAB, manualAndA)
        val removableFromA = SubRepoOwnershipRules.removableChildren(state, parentA, installed)

        assertEquals(setOf(SubRepoOwnershipRules.normalize(onlyA)), removableFromA)
        assertFalse(SubRepoOwnershipRules.normalize(sharedAB) in removableFromA)
        assertFalse(SubRepoOwnershipRules.normalize(manualAndA) in removableFromA)
        assertFalse(SubRepoOwnershipRules.normalize(parentB) in removableFromA)

        // Şimdi B kaldırılırsa (sadece kendisi), sharedAB yalnızca A'da kalır; A kaldırılınca sharedAB silinebilir olmalı!
        val afterBRemoved = SubRepoOwnershipRules.onRepoRemoved(state, parentB)
        val removableFromANow = SubRepoOwnershipRules.removableChildren(
            afterBRemoved,
            parentA,
            listOf(parentA, onlyA, sharedAB, manualAndA),
        )
        assertEquals(
            setOf(SubRepoOwnershipRules.normalize(onlyA), SubRepoOwnershipRules.normalize(sharedAB)),
            removableFromANow,
        )
    }

    @Test
    fun manifestWithRepositoriesFieldClassifiesAsExternalAndDiscoversSubReposWithoutSilentInstall() = runTest {
        val rawParent = """
            {
              "name": "Üst Depo",
              "repositories": [
                "https://sub1.example/repo.json",
                {"name": "Alt 2", "url": "https://sub2.example/repo.json"},
                "http://insecure.example/repo.json"
              ]
            }
        """.trimIndent()

        assertEquals(ManifestKind.External, ManifestKind.classify(rawParent))
        val parsed = CsRepoManifestParser.parseRoot(rawParent, baseUrl = "https://parent.example/repo.json")
        assertEquals("Üst Depo", parsed.name)
        assertEquals(listOf("https://sub1.example/repo.json", "https://sub2.example/repo.json", "http://insecure.example/repo.json"), parsed.subRepos.map { it.url })

        val addonDao = FakeAddonDao()
        val pluginDao = FakePluginDao()
        val ownershipStore = SubRepoOwnershipStore(MemoryStore())
        val discoveredCalls = mutableListOf<Pair<String, List<PluginRepoItem>>>()
        val tmpDir = File.createTempFile("cs_sub_test", "").also { it.delete(); it.mkdirs() }

        val repo = CsRepository(
            addonDao = addonDao,
            pluginDao = pluginDao,
            fetcher = ManifestFetcher { throw AddonException(AddonException.Kind.Http, "404") },
            httpClient = OkHttpClient(),
            store = CsDexStore(tmpDir),
            loader = object : PluginDexLoader {
                override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String) =
                    CsApiResult.NoApiRegistered(pluginId)
            },
            ownershipStore = ownershipStore,
            onSubReposDiscovered = { p, subs -> discoveredCalls += p to subs },
        )

        repo.addFetched("https://parent.example/repo.json", rawParent, recordManual = true)

        // Alt depolar sessizce kurulmadı; onay penceresine devredildi!
        assertEquals(1, addonDao.countOfType(AddonType.CLOUDSTREAM_REPO))
        assertEquals(1, discoveredCalls.size)
        assertEquals("https://parent.example/repo.json", discoveredCalls.single().first)

        // Kullanıcı onayıyla kurulum simülasyonu + basamaklı kaldırma:
        val sub1Raw = """{"name":"Alt 1","pluginLists":["https://sub1.example/plugins.json"]}"""
        val subRepoWithDummyEntry = CsRepository(
            addonDao = addonDao,
            pluginDao = pluginDao,
            fetcher = ManifestFetcher { """[]""" },
            httpClient = OkHttpClient(),
            store = CsDexStore(tmpDir),
            loader = object : PluginDexLoader {
                override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String) =
                    CsApiResult.NoApiRegistered(pluginId)
            },
            ownershipStore = ownershipStore,
        )
        // sub1 normalde eklenti içerir; DAO'ya alt depo eklenmiş varsayalım ve sahiplik kaydedelim:
        val sub1Id = addonDao.insert(
            AddonEntity(
                type = AddonType.CLOUDSTREAM_REPO,
                sourceUrl = "https://sub1.example/repo.json",
                addonId = "https://sub1.example/repo.json",
                name = "Alt 1",
                version = null,
                description = null,
                logoUrl = null,
                enabled = true,
                manifestJson = sub1Raw,
                addedAt = 1L,
            ),
        )
        assertTrue(sub1Id > 0)
        ownershipStore.recordSubRepos("https://parent.example/repo.json", listOf("https://sub1.example/repo.json"))

        val parentModel = repo.repos.first().first { it.sourceUrl == "https://parent.example/repo.json" }
        assertEquals(1, repo.removableSubRepos(parentModel).size)

        subRepoWithDummyEntry.remove(parentModel.id, cascadeSubRepos = true)
        assertEquals(0, addonDao.countOfType(AddonType.CLOUDSTREAM_REPO))
    }
}
