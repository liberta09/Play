package com.kaan.play.data.cloudstream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginRepoRequestHandlerTest {

    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FakeAddonDao : AddonDao {
        val repos = mutableListOf<AddonEntity>()
        override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = flowOf(repos.filter { it.type == type })
        override suspend fun enabledOfType(type: AddonType): List<AddonEntity> = repos.filter { it.type == type && it.enabled }
        override suspend fun countBySourceUrl(url: String): Int = repos.count { it.sourceUrl == url }
        override suspend fun countOfType(type: AddonType): Int = repos.count { it.type == type }
        override suspend fun insert(entity: AddonEntity): Long { repos.add(entity); return repos.size.toLong() }
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
        override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {}
        override suspend fun delete(id: Long) {}
        override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {}
        override suspend fun byId(id: Long): AddonEntity? = null
    }

    @Test
    fun enqueueDebouncesAndFilters() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 100L,
            isBlockedHost = { _, _ -> false },
        )

        handler.enqueue("Repo 1", "https://example.com/repo1.json")
        handler.enqueue("Repo 2", "https://example.com/repo2.json")
        assertNull(handler.pendingRequest.value)

        advanceTimeBy(150L)
        runCurrent()
        val pending = handler.pendingRequest.value
        assertNotNull(pending)
        assertEquals(2, pending!!.size)
    }

    @Test
    fun dismissSavesDeclinedRepos() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        handler.enqueue("Repo 1", "https://example.com/declined.json")
        advanceTimeBy(100L)
        runCurrent()
        assertNotNull(handler.pendingRequest.value)

        handler.dismiss()
        runCurrent()
        assertNull(handler.pendingRequest.value)

        val declined = store.data.first()[PluginRepoRequestHandler.DECLINED_PLUGIN_REPOS]
        assertNotNull(declined)
        assertTrue(declined!!.any { it.contains("declined.json") })
    }

    @Test
    fun confirmInstallsReposAndEmitsCompletedEvent() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        val events = mutableListOf<PluginRepoEvent>()
        val collectJob = launch { handler.events.collect { events.add(it) } }

        handler.enqueue("Repo 1", "https://example.com/add1.json")
        handler.enqueue("Repo 2", "https://example.com/add2.json")
        advanceTimeBy(100L)
        runCurrent()

        handler.confirm()
        advanceUntilIdle()

        assertEquals(2, addedUrls.size)
        assertNull(handler.pendingRequest.value)
        assertEquals(1, events.size)
        val completed = events.first() as PluginRepoEvent.Completed
        assertEquals(2, completed.successCount)
        assertEquals(2, completed.total)

        collectJob.cancel()
    }

    @Test
    fun enqueueAndDebounceDoesNotInstallBeforeConfirm() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        handler.enqueue("Repo 1", "https://example.com/pending.json")
        advanceTimeBy(100L)
        runCurrent()

        assertNotNull(handler.pendingRequest.value)
        assertTrue(addedUrls.isEmpty())
    }

    @Test
    fun confirmWithFailingRepoDecrementsSuccessCount() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val failingUrl = "https://example.com/fail.json"
        val handler = PluginRepoRequestHandler(
            installRepo = { url ->
                if (url == failingUrl) {
                    error("Indirme basarisiz")
                } else {
                    addedUrls.add(url)
                }
            },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        val events = mutableListOf<PluginRepoEvent>()
        val collectJob = launch { handler.events.collect { events.add(it) } }

        handler.enqueue("Good Repo", "https://example.com/good.json")
        handler.enqueue("Bad Repo", failingUrl)
        advanceTimeBy(100L)
        runCurrent()

        handler.confirm()
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/good.json"), addedUrls)
        assertEquals(1, events.size)
        val completed = events.first() as PluginRepoEvent.Completed
        assertEquals(1, completed.successCount)
        assertEquals(2, completed.total)

        collectJob.cancel()
    }

    @Test
    fun dismissedRepoDoesNotShowOnSubsequentEnqueue() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        handler.enqueue("Repo 1", "https://example.com/once.json")
        advanceTimeBy(100L)
        runCurrent()
        assertNotNull(handler.pendingRequest.value)

        handler.dismiss()
        advanceUntilIdle()
        assertNull(handler.pendingRequest.value)

        // Ayni adres tekrar geldigi zaman pendingRequest null kalmali
        handler.enqueue("Repo 1", "https://example.com/once.json")
        advanceTimeBy(100L)
        runCurrent()
        assertNull(handler.pendingRequest.value)
        assertTrue(addedUrls.isEmpty())
    }

    @Test
    fun userInfoUrlDoesNotEnterPendingList() = runTest {
        val store = MemoryPreferencesStore()
        val dao = FakeAddonDao()
        val addedUrls = mutableListOf<String>()
        val handler = PluginRepoRequestHandler(
            installRepo = { addedUrls.add(it) },
            addonDao = dao,
            dataStore = store,
            scope = this,
            debounceDelayMs = 50L,
            isBlockedHost = { _, _ -> false },
        )

        handler.enqueue("Spoofed", "https://raw.githubusercontent.com@baska-site.com/x.json")
        advanceTimeBy(100L)
        runCurrent()

        assertNull(handler.pendingRequest.value)
        assertTrue(addedUrls.isEmpty())
    }
}
