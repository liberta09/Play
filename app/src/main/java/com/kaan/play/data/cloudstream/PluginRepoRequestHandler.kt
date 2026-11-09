package com.kaan.play.data.cloudstream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.plugin.SourceInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface PluginRepoEvent {
    data class Completed(val successCount: Int, val total: Int) : PluginRepoEvent
}

class PluginRepoRequestHandler(
    private val installRepo: suspend (String) -> Unit,
    private val addonDao: AddonDao,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val debounceDelayMs: Long = 1500L,
) {
    constructor(
        installer: SourceInstaller,
        addonDao: AddonDao,
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
        debounceDelayMs: Long = 1500L,
    ) : this(
        installRepo = { installer.add(it) },
        addonDao = addonDao,
        dataStore = dataStore,
        scope = scope,
        debounceDelayMs = debounceDelayMs,
    )
    private val lock = Any()
    private val rawCandidates = mutableListOf<PluginRepoItem>()
    private var debounceJob: Job? = null

    private val _pendingRequest = MutableStateFlow<List<PluginRepoItem>?>(null)
    val pendingRequest: StateFlow<List<PluginRepoItem>?> = _pendingRequest.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val eventChannel = Channel<PluginRepoEvent>(Channel.BUFFERED)
    val events: Flow<PluginRepoEvent> = eventChannel.receiveAsFlow()

    fun enqueue(name: String, url: String) {
        android.util.Log.i("PlayCs", "PluginRepoRequestHandler.enqueue: name='$name' url='$url'")
        synchronized(lock) {
            rawCandidates.add(PluginRepoItem(name = name, url = url))
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceDelayMs)
                processQueue()
            }
        }
    }

    suspend fun processQueue() {
        val candidates = synchronized(lock) {
            val copy = rawCandidates.toList()
            rawCandidates.clear()
            copy
        }
        if (candidates.isEmpty()) return

        val installed = runCatching {
            addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first().map { it.sourceUrl }.toSet()
        }.getOrDefault(emptySet())

        val declined = runCatching {
            dataStore.data.first()[DECLINED_PLUGIN_REPOS] ?: emptySet()
        }.getOrDefault(emptySet())

        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = installed,
            declinedUrls = declined,
        )

        android.util.Log.i(
            "PlayCs",
            "PluginRepoRequestHandler.processQueue: raw=${candidates.size}, installed=${installed.size}, declined=${declined.size}, filtered=${filtered.size}"
        )

        if (filtered.isEmpty()) {
            _pendingRequest.value = null
            return
        }

        _pendingRequest.value = filtered
    }

    fun confirm() {
        val items = _pendingRequest.value ?: return
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            val gate = Semaphore(2)
            var successCount = 0
            coroutineScope {
                items.map { item ->
                    launch {
                        gate.withPermit {
                            val res = runCatching { installRepo(item.url) }
                            if (res.isSuccess) {
                                successCount++
                            }
                        }
                    }
                }
            }
            _pendingRequest.value = null
            _busy.value = false
            eventChannel.trySend(PluginRepoEvent.Completed(successCount = successCount, total = items.size))
        }
    }

    fun dismiss() {
        val items = _pendingRequest.value ?: return
        if (_busy.value) return
        _pendingRequest.value = null
        scope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    val current = prefs[DECLINED_PLUGIN_REPOS] ?: emptySet()
                    val toAdd = items.flatMap { listOf(it.url, PluginRepoRequestRules.normalizeUrl(it.url)) }.toSet()
                    prefs[DECLINED_PLUGIN_REPOS] = current + toAdd
                }
            }
        }
    }

    companion object {
        val DECLINED_PLUGIN_REPOS = stringSetPreferencesKey("declined_plugin_repos")
    }
}
