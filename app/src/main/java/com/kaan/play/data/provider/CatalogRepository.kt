package com.kaan.play.data.provider

import android.util.Log
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

sealed interface CatalogResult {
    data class Success(val rows: List<CatalogRow>) : CatalogResult
    data object NoCatalogProvided : CatalogResult
    data class Error(
        val message: String? = null,
        val isBotBlocked: Boolean = false,
        val verifyUrl: String? = null,
        val verifyHost: String? = null,
    ) : CatalogResult
    data object Empty : CatalogResult
}

/**
 * Sağlayıcı merkezli ana sayfa için katalog çekme ve önbellek yöneticisi.
 */
class CatalogRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao? = null,
    private val csRepository: CsRepository? = null,
    private val csRuntime: CsRuntime? = null,
    private val httpClient: OkHttpClient,
    val cache: CatalogCache = CatalogCache(),
    private val getDisabledCatalogs: (suspend () -> Set<String>)? = null,
) {
    /** Etkin Nuvio addon'larının tv/channel kataloglarını TV sekmesi için birleştirir. */
    suspend fun loadLiveCatalog(forceRefresh: Boolean = false): CatalogResult =
        loadLiveCatalogInternal(forceRefresh) { }

    /** Sağlayıcılar tamamlandıkça kısmi sonuçları yayınlar; son değer her zaman son toplu sonucu temsil eder. */
    fun loadLiveCatalogUpdates(forceRefresh: Boolean = false): Flow<CatalogResult> = channelFlow {
        val result = loadLiveCatalogInternal(forceRefresh) { partial -> trySend(partial) }
        send(result)
    }.flowOn(Dispatchers.IO)

    private suspend fun loadLiveCatalogInternal(
        forceRefresh: Boolean,
        onPartial: (CatalogResult) -> Unit,
    ): CatalogResult = withContext(Dispatchers.IO) {
        val cacheKey = "__live_tv_v4__"
        val disabled = getDisabledCatalogs?.invoke() ?: emptySet()
        if (!forceRefresh) {
            cache.get(cacheKey)?.let { cached ->
                val visible = com.kaan.play.data.addon.CatalogVisibility.filterRows(cached, disabled)
                return@withContext if (visible.isEmpty()) CatalogResult.Empty else CatalogResult.Success(visible)
            }
            cache.get(LIVE_PARTIAL_CACHE_KEY)?.let { cached ->
                val visible = com.kaan.play.data.addon.CatalogVisibility.filterRows(cached, disabled)
                if (visible.isNotEmpty()) onPartial(CatalogResult.Success(visible))
            }
        }
        val nuvioAddons = addonDao.enabledOfType(AddonType.NUVIO_ADDON)
        val cloudStreamPlugins = if (pluginDao != null && csRepository != null && csRuntime != null) {
            pluginDao.enabledOfType(AddonType.CLOUDSTREAM_REPO)
        } else {
            emptyList()
        }
        var allProvidersCompleted = true
        val rows = coroutineScope {
            val gate = Semaphore(LIVE_PROVIDER_CONCURRENCY)
            val completed = Channel<Unit>(Channel.UNLIMITED)
            val rowsByProvider = ConcurrentHashMap<Int, List<CatalogRow>>()
            val jobs = mutableListOf<Job>()
            var providerIndex = 0
            nuvioAddons.forEach { entity ->
                val index = providerIndex++
                jobs += launch {
                    val result = gate.withPermit {
                        safeLiveRows("Nuvio ${entity.addonId}") {
                            val manifest = PlayJson.decodeFromString(AddonManifest.serializer(), entity.manifestJson)
                            val addon = Addon(entity.id, entity.type, entity.sourceUrl, manifest, entity.enabled, entity.addedAt)
                            LiveCatalogRules.onlyLiveRows(
                                NuvioAddonCatalog(addon, httpClient, timeout = LIVE_NUVIO_TIMEOUT).liveRows().first(),
                            )
                        }
                    }
                    rowsByProvider[index] = result
                    completed.send(Unit)
                }
            }
            if (pluginDao != null && csRepository != null && csRuntime != null) {
                cloudStreamPlugins.forEach { plugin ->
                    val index = providerIndex++
                    jobs += launch {
                        val result = gate.withPermit {
                            safeLiveRows("CloudStream ${plugin.name}") {
                                LiveCatalogRules.onlyLiveRows(
                                    CsCatalog(plugin.id, plugin.name, csRepository, csRuntime).liveRows(),
                                )
                            }
                    }
                        rowsByProvider[index] = result
                        completed.send(Unit)
                    }
                }
            }
            var completedCount = 0
            var lastPartialRows: List<CatalogRow>? = null
            val finishedInTime = withTimeoutOrNull(LIVE_CATALOG_TOTAL_TIMEOUT_MS) {
                repeat(jobs.size) {
                    completed.receive()
                    completedCount++
                    val partialRows = limitLiveRows(rowsByProvider.toSortedMap().values.flatten())
                    if (partialRows.isNotEmpty() && partialRows != lastPartialRows) {
                        lastPartialRows = partialRows
                        val partial = liveCatalogResult(partialRows, disabled)
                        if (partial is CatalogResult.Success) {
                            onPartial(partial)
                            cache.put(LIVE_PARTIAL_CACHE_KEY, partialRows, LIVE_PARTIAL_CACHE_TTL_MS)
                        }
                    }
                }
                true
            } ?: false
            if (!finishedInTime) {
                jobs.filterNot { it.isCompleted }.forEach { it.cancel() }
                Log.w(TAG, "Canlı TV toplam süre sınırına ulaştı; tamamlanan ${completedCount}/${jobs.size} sağlayıcının sonuçları kullanılıyor.")
            }
            jobs.joinAll()
            completed.close()
            allProvidersCompleted = finishedInTime
            rowsByProvider.toSortedMap().values.flatten()
        }
        val limited = limitLiveRows(rows)
        if (limited.isEmpty()) {
            if (allProvidersCompleted) cache.clear(LIVE_PARTIAL_CACHE_KEY)
            return@withContext CatalogResult.Empty
        }
        if (allProvidersCompleted) {
            cache.put(cacheKey, limited)
            cache.clear(LIVE_PARTIAL_CACHE_KEY)
        } else {
            cache.put(LIVE_PARTIAL_CACHE_KEY, limited, LIVE_PARTIAL_CACHE_TTL_MS)
        }
        liveCatalogResult(limited, disabled)
    }

    private fun limitLiveRows(rows: List<CatalogRow>): List<CatalogRow> = rows.map { row ->
        row.copy(items = row.items.distinctBy { "${it.providerId}:${it.id}" }.take(MAX_LIVE_ITEMS))
    }.filter { it.items.isNotEmpty() }

    private fun liveCatalogResult(rows: List<CatalogRow>, disabled: Set<String>): CatalogResult {
        val visible = com.kaan.play.data.addon.CatalogVisibility.filterRows(rows, disabled)
        return if (visible.isEmpty()) CatalogResult.Empty else CatalogResult.Success(visible)
    }

    private suspend fun safeLiveRows(label: String, block: suspend () -> List<CatalogRow>): List<CatalogRow> = try {
        block()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        Log.w(TAG, "Canlı TV katalog hatası ($label): ${t.javaClass.simpleName}: ${t.message}")
        emptyList()
    }

    /**
     * Seçili sağlayıcıların katalog satırlarını sırayla çeker.
     * [forceRefresh] true ise bellek içi önbellek atlanır ("Yeniden dene").
     */
    suspend fun loadCatalog(
        selectedIds: Set<String>,
        forceRefresh: Boolean = false,
    ): CatalogResult = withContext(Dispatchers.IO) {
        val validIds = selectedIds.filter { it != ProviderConstants.ALL_PROVIDERS_ID }
        if (validIds.isEmpty()) return@withContext CatalogResult.Empty

        // Yalnızca Nuvio JS eklentileri mi seçili?
        val allAreNuvioPlugins = validIds.all { it.startsWith("nuvio_plugin:") }
        if (allAreNuvioPlugins) {
            return@withContext CatalogResult.NoCatalogProvided
        }

        val disabled = getDisabledCatalogs?.invoke() ?: emptySet()
        val cacheKey = validIds.sorted().joinToString(",")
        if (!forceRefresh) {
            cache.get(cacheKey)?.let { cachedRows ->
                val visible = com.kaan.play.data.addon.CatalogVisibility.filterRows(cachedRows, disabled)
                return@withContext if (visible.isEmpty()) CatalogResult.Empty else CatalogResult.Success(visible)
            }
        }

        val allRows = mutableListOf<CatalogRow>()
        var hasAtLeastOneProvider = false
        var hasError = false
        var hasBotBlocked = false
        var blockedVerifyUrl: String? = null
        var blockedVerifyHost: String? = null

        for (id in validIds) {
            when {
                id.startsWith("cs:") -> {
                    if (pluginDao == null || csRepository == null || csRuntime == null) continue
                    val pluginRowId = id.removePrefix("cs:").toLongOrNull() ?: continue
                    val plugin = pluginDao.byId(pluginRowId) ?: continue
                    hasAtLeastOneProvider = true
                    val providerStarted = System.currentTimeMillis()
                    try {
                        val catalog = CsCatalog(pluginRowId, plugin.name, csRepository, csRuntime)
                        val rows = catalog.rows().first()
                        allRows.addAll(rows)
                    } catch (t: Throwable) {
                        Log.e(TAG, "CS katalog hatası ($id): ${t.message}", t)
                        hasError = true
                    }
                    // Eklenti mainUrl dışındaki bir alan adında engel sayfasına takıldıysa da "Doğrula" göster.
                    val stuck = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.unresolvedSince(providerStarted)
                    if (stuck != null) {
                        hasBotBlocked = true
                        blockedVerifyHost = stuck.first
                        blockedVerifyUrl = stuck.second
                    }
                    val apis = csRepository.loadApis(pluginRowId).apisOrEmpty()
                    for (api in apis) {
                        if (com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.wasBlocked(api.mainUrl)) {
                            hasBotBlocked = true
                            val host = com.kaan.play.data.cloudstream.cloudflare.CloudflareCache.normalizeHost(api.mainUrl)
                            blockedVerifyHost = host
                            blockedVerifyUrl = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.getBlockedUrl(host) ?: api.mainUrl
                        }
                    }
                }
                id.startsWith("nuvio_addon:") -> {
                    val addonRowId = id.removePrefix("nuvio_addon:").toLongOrNull() ?: continue
                    val addonEntity = addonDao.byId(addonRowId) ?: continue
                    hasAtLeastOneProvider = true
                    try {
                        val manifest = PlayJson.decodeFromString(AddonManifest.serializer(), addonEntity.manifestJson)
                        val addon = Addon(
                            id = addonEntity.id,
                            type = addonEntity.type,
                            sourceUrl = addonEntity.sourceUrl,
                            manifest = manifest,
                            enabled = addonEntity.enabled,
                            addedAt = addonEntity.addedAt,
                        )
                        val catalog = NuvioAddonCatalog(addon, httpClient)
                        val rows = catalog.rows().first()
                        allRows.addAll(rows)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Nuvio addon katalog hatası ($id): ${t.message}", t)
                        hasError = true
                    }
                }
                id.startsWith("nuvio_plugin:") -> {
                    // Nuvio JS eklentileri katalog sunmaz
                }
            }
        }

        if (allRows.isNotEmpty()) {
            cache.put(cacheKey, allRows)
            val visible = com.kaan.play.data.addon.CatalogVisibility.filterRows(allRows, disabled)
            if (visible.isNotEmpty()) CatalogResult.Success(visible) else CatalogResult.Empty
        } else if (hasBotBlocked && allRows.isEmpty()) {
            CatalogResult.Error(
                isBotBlocked = true,
                verifyUrl = blockedVerifyUrl,
                verifyHost = blockedVerifyHost,
            )
        } else if (hasError && allRows.isEmpty()) {
            CatalogResult.Error()
        } else {
            CatalogResult.Empty
        }
    }

    /**
     * Bir katalog satırının sonraki sayfa öğelerini çeker ("Tümünü göster" ızgarası için).
     */
    suspend fun loadMore(
        providerId: String,
        rowId: String,
        page: Int,
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val disabled = getDisabledCatalogs?.invoke() ?: emptySet()
        if (providerId.startsWith("nuvio_addon:")) {
            val addonRowId = providerId.removePrefix("nuvio_addon:")
            val colonIdx = rowId.indexOf(':')
            if (colonIdx > 0) {
                val type = rowId.substring(0, colonIdx)
                val catId = rowId.substring(colonIdx + 1)
                if (!com.kaan.play.data.addon.CatalogVisibility.isVisible(addonRowId, type, catId, disabled)) {
                    return@withContext emptyList()
                }
            }
        }
        try {
            when {
                providerId.startsWith("cs:") -> {
                    if (pluginDao == null || csRepository == null || csRuntime == null) return@withContext emptyList()
                    val pluginRowId = providerId.removePrefix("cs:").toLongOrNull() ?: return@withContext emptyList()
                    val plugin = pluginDao.byId(pluginRowId) ?: return@withContext emptyList()
                    val catalog = CsCatalog(pluginRowId, plugin.name, csRepository, csRuntime)
                    catalog.loadMore(rowId, page)
                }
                providerId.startsWith("nuvio_addon:") -> {
                    val addonRowId = providerId.removePrefix("nuvio_addon:").toLongOrNull() ?: return@withContext emptyList()
                    val addonEntity = addonDao.byId(addonRowId) ?: return@withContext emptyList()
                    val manifest = PlayJson.decodeFromString(AddonManifest.serializer(), addonEntity.manifestJson)
                    val addon = Addon(
                        id = addonEntity.id,
                        type = addonEntity.type,
                        sourceUrl = addonEntity.sourceUrl,
                        manifest = manifest,
                        enabled = addonEntity.enabled,
                        addedAt = addonEntity.addedAt,
                    )
                    val catalog = NuvioAddonCatalog(addon, httpClient)
                    catalog.loadMore(rowId, page)
                }
                else -> emptyList()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loadMore hatası ($providerId / $rowId / p=$page): ${t.message}", t)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "PlayCatalog"
        const val MAX_LIVE_ITEMS = 512
        const val LIVE_PROVIDER_CONCURRENCY = 8
        const val LIVE_CATALOG_TOTAL_TIMEOUT_MS = 8_000L
        const val LIVE_PARTIAL_CACHE_KEY = "__live_tv_partial_v4__"
        const val LIVE_PARTIAL_CACHE_TTL_MS = 30_000L
        val LIVE_NUVIO_TIMEOUT = 3.seconds
    }
}
