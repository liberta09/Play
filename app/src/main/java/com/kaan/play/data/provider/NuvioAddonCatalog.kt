package com.kaan.play.data.provider

import android.util.Log
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonUrl
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Nuvio (Stremio protokolü) addon'ları için katalog sağlayıcı.
 * Manifest'teki `catalogs` dizisinden `{baseUrl}/catalog/{type}/{id}.json` istekleriyle veri çeker.
 */
class NuvioAddonCatalog(
    private val addon: Addon,
    private val client: OkHttpClient,
    private val timeout: Duration = 10.seconds,
) : ProviderCatalog {

    override val providerId: String = "nuvio_addon:${addon.id}"
    override val name: String = addon.manifest.name

    override fun rows(): Flow<List<CatalogRow>> = rowsFor(addon.manifest.catalogs, liveOnly = false)

    /** TV sekmesinde tv/channel türlerini ve adı açıkça canlı olan katalogları dener; yanıttan yalnız canlı öğeleri tutar. */
    fun liveRows(): Flow<List<CatalogRow>> = rowsFor(
        catalogs = addon.manifest.catalogs.filter {
            LiveCatalogRules.isLiveCatalog(it.type, it.name, it.id)
        },
        liveOnly = true,
        parallelism = LIVE_CATALOG_PARALLELISM,
        totalTimeout = LIVE_ROWS_TOTAL_TIMEOUT,
    )

    private fun rowsFor(
        catalogs: List<ManifestCatalog>,
        liveOnly: Boolean,
        parallelism: Int = 1,
        totalTimeout: Duration? = null,
    ): Flow<List<CatalogRow>> = flow {
        if (catalogs.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val rows = if (parallelism <= 1) {
            val loaded = mutableListOf<CatalogRow>()
            for (cat in catalogs) fetchRowSafely(cat, liveOnly)?.let(loaded::add)
            loaded
        } else if (totalTimeout != null) {
            coroutineScope {
                val completed = Channel<Unit>(Channel.UNLIMITED)
                val semaphore = Semaphore(parallelism)
                val rowsByIndex = mutableMapOf<Int, CatalogRow>()
                val rowsLock = Any()
                val jobs = catalogs.mapIndexed { index, cat ->
                    launch {
                        val row = semaphore.withPermit { fetchRowSafely(cat, liveOnly) }
                        if (row != null) synchronized(rowsLock) { rowsByIndex[index] = row }
                        completed.send(Unit)
                    }
                }
                val deadline = System.currentTimeMillis() + totalTimeout.inWholeMilliseconds
                var finished = 0
                while (finished < jobs.size) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L || withTimeoutOrNull(remaining) { completed.receive() } == null) break
                    finished++
                }
                jobs.filterNot { it.isCompleted }.forEach { it.cancel() }
                jobs.joinAll()
                synchronized(rowsLock) { rowsByIndex.toSortedMap().values.toList() }
            }
        } else {
            coroutineScope {
                val semaphore = Semaphore(parallelism)
                catalogs.map { cat ->
                    async { semaphore.withPermit { fetchRowSafely(cat, liveOnly) } }
                }.awaitAll().filterNotNull()
            }
        }
        emit(rows)
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchRowSafely(cat: ManifestCatalog, liveOnly: Boolean): CatalogRow? = try {
        fetchRow(cat, liveOnly)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        Log.w(TAG, "$name/${cat.id} katalog çekilemedi: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    private suspend fun fetchRow(cat: ManifestCatalog, liveOnly: Boolean): CatalogRow? {
        val httpUrl = AddonUrl.endpoint(addon.sourceUrl, "catalog", cat.type, "${cat.id}.json")
        if (httpUrl == null) {
            Log.w(TAG, "$name: Geçersiz katalog URL (kaynak adresi maskeli)")
            return null
        }

        val jsonText = fetchAddonText(client, httpUrl, timeout, MAX_BYTES)
        val response = PlayJson.decodeFromString(StremioCatalogResponse.serializer(), jsonText)
        val metas = response.metas.distinctBy { it.id }
        val items = metas.asSequence()
            .filter { !liveOnly || LiveCatalogRules.isLiveType(it.type) || LiveCatalogRules.isKnownLiveChannel(it.name) }
            .map { meta ->
                val type = meta.type.lowercase(java.util.Locale.ROOT)
                val live = LiveCatalogRules.isLiveType(type) || LiveCatalogRules.isKnownLiveChannel(meta.name)
                CatalogItem(
                    id = meta.id,
                    title = meta.name,
                    subtitle = when (type) {
                        "series" -> "Dizi"
                        "tv", "channel" -> if (java.util.Locale.getDefault().language == "tr") "Canlı" else "Live"
                        else -> if (live) if (java.util.Locale.getDefault().language == "tr") "Canlı" else "Live" else null
                    },
                    posterUrl = meta.poster,
                    posterHeaders = null,
                    mediaType = if (type == "series" || live) MediaType.Tv else MediaType.Movie,
                    providerId = providerId,
                    rawUrl = null,
                    addonType = if (live && !LiveCatalogRules.isLiveType(type)) "channel" else type,
                )
            }
            .toList()

        if (items.isEmpty()) return null
        val rowTitle = cat.name ?: when (cat.id) {
            "top" -> if (cat.type == "series") "Popüler Diziler" else "Popüler Filmler"
            else -> cat.id.replaceFirstChar { it.uppercase() }
        }
        return CatalogRow(
            id = "${cat.type}:${cat.id}",
            title = rowTitle,
            items = items,
            hasNext = items.size >= 10,
            page = 1,
            providerId = providerId,
            providerName = name,
        )
    }

    override suspend fun loadMore(rowId: String, page: Int): List<CatalogItem> = withContext(Dispatchers.IO) {
        val parts = rowId.split(':')
        if (parts.size < 2) return@withContext emptyList()
        val type = parts[0]
        val id = parts[1]
        val skip = (page - 1) * 20
        val httpUrl = AddonUrl.endpoint(addon.sourceUrl, "catalog", type, id, "skip=$skip.json") ?: return@withContext emptyList()

        try {
            val jsonText = fetchAddonText(client, httpUrl, timeout, MAX_BYTES)
            val response = PlayJson.decodeFromString(StremioCatalogResponse.serializer(), jsonText)
            response.metas.map { meta ->
                CatalogItem(
                    id = meta.id,
                    title = meta.name,
                    subtitle = when (meta.type.lowercase()) {
                        "series" -> "Dizi"
                        "tv", "channel" -> if (java.util.Locale.getDefault().language == "tr") "Canlı" else "Live"
                        else -> null
                    },
                    posterUrl = meta.poster,
                    posterHeaders = null,
                    mediaType = when (meta.type.lowercase()) {
                        "series", "tv", "channel" -> MediaType.Tv
                        else -> MediaType.Movie
                    },
                    providerId = providerId,
                    rawUrl = null,
                    addonType = meta.type.lowercase(),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private companion object {
        const val TAG = "PlayNuvioCatalog"
        const val MAX_BYTES = 5L * 1024 * 1024
        const val LIVE_CATALOG_PARALLELISM = 2
        val LIVE_ROWS_TOTAL_TIMEOUT = 6.seconds
    }
}

@Serializable
private data class StremioCatalogResponse(
    val metas: List<StremioMeta> = emptyList(),
)

@Serializable
private data class StremioMeta(
    val id: String,
    val type: String = "movie",
    val name: String,
    val poster: String? = null,
    val description: String? = null,
)
