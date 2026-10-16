package com.kaan.play.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** CloudStream ana sayfa katalogları; Nuvio ve mevcut oynatma akışından bağımsızdır. */
data class CsCatalog(
    val key: String,
    val pluginId: Long,
    val pluginName: String,
    val apiName: String,
    val title: String,
    val request: MainPageRequest,
)

data class CsCatalogItem(
    val pluginId: Long,
    val pluginName: String,
    val apiName: String,
    val title: String,
    val url: String,
    val posterUrl: String?,
    val typeName: String,
)

data class CsCatalogPage(
    val catalog: CsCatalog,
    val items: List<CsCatalogItem>,
    val page: Int,
    val hasMore: Boolean,
)

/** Eklentinin kendi URL'sinden yüklenmiş detay; TMDB kimliğine dönüştürülmez. */
data class CsCatalogDetail(
    val item: CsCatalogItem,
    val response: LoadResponse,
)

data class CsCatalogStreams(
    val detail: CsCatalogDetail,
    val links: List<ExtractorLink>,
    val subtitles: List<SubtitleFile>,
    val episodeOptions: List<CsCatalogEpisode>,
)

data class CsCatalogEpisode(
    val name: String,
    val season: Int?,
    val episode: Int?,
    val data: String,
)

/** Etkin eklentilerin kataloglarını listeler ve seçilen kataloğun sayfalarını yükler. */
class CsCatalogRepository(
    private val repository: CsRepository,
    private val timeoutMs: Long = 12_000L,
) {
    suspend fun catalogs(): List<CsCatalog> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CsCatalog>()
        repository.enabledPlugins().forEach { plugin ->
            runCatching {
                repository.loadApis(plugin.id).forEach apiLoop@{ api ->
                    if (!api.hasMainPage) return@apiLoop
                    api.mainPage.forEach { page ->
                        result += CsCatalog(
                            key = "${plugin.id}:${api.name}:${page.name}:${page.data}",
                            pluginId = plugin.id,
                            pluginName = plugin.name,
                            apiName = api.name,
                            title = if (api.mainPage.size > 1) "${plugin.name} · ${page.name}" else plugin.name,
                            request = MainPageRequest(page.name, page.data, page.horizontalImages),
                        )
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Katalog listesi alınamadı: ${plugin.name}", it)
            }
        }
        result.distinctBy { it.key }
    }

    suspend fun loadPage(catalog: CsCatalog, page: Int = 1): CsCatalogPage? = withContext(Dispatchers.IO) {
        val plugin = repository.enabledPlugins().firstOrNull { it.id == catalog.pluginId } ?: return@withContext null
        val apis = runCatching { repository.loadApis(plugin.id) }.getOrElse {
            Log.w(TAG, "Eklenti yüklenemedi: ${plugin.name}", it)
            return@withContext null
        }
        val api = apis.firstOrNull { it.name == catalog.apiName } ?: return@withContext null
        val response = withTimeoutOrNull(timeoutMs) {
            runCatching { api.getMainPage(page, catalog.request) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Katalog yüklenemedi: ${catalog.title}, sayfa=$page", it)
                }
                .getOrNull()
        } ?: return@withContext null
        CsCatalogPage(
            catalog = catalog,
            items = response.items.flatMap { row -> row.list.map { it.toCatalogItem(catalog) } },
            page = page,
            hasMore = response.hasNext,
        )
    }

    /**
     * Katalog kartını doğrudan ilgili CloudStream sağlayıcısında açar.
     * Başlığı TMDB ile eşleştirmeye çalışmaz; böylece siteye özgü içerik URL'si korunur.
     */
    suspend fun loadDetail(item: CsCatalogItem): CsCatalogDetail? = withContext(Dispatchers.IO) {
        val plugin = repository.enabledPlugins().firstOrNull { it.id == item.pluginId }
            ?: return@withContext null
        val apis = runCatching { repository.loadApis(plugin.id) }.getOrElse {
            Log.w(TAG, "Detay için eklenti yüklenemedi: " + plugin.name, it)
            return@withContext null
        }
        val api: MainAPI = apis.firstOrNull { it.name == item.apiName }
            ?: return@withContext null
        val response = withTimeoutOrNull(timeoutMs) {
            runCatching { api.load(item.url) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Detay yüklenemedi: " + item.title, it)
                }
                .getOrNull()
        } ?: return@withContext null
        CsCatalogDetail(item, response)
    }

    /** İçerik ayrıntılarını ve eklentinin kendi oynatma bağlantılarını getirir. */
    suspend fun loadStreams(item: CsCatalogItem, episodeData: String? = null): CsCatalogStreams? = withContext(Dispatchers.IO) {
        val detail = loadDetail(item) ?: return@withContext null
        val response = detail.response
        val episodes = when (response) {
            is TvSeriesLoadResponse -> response.episodes.map { CsCatalogEpisode(it.name, it.season, it.episode, it.data) }
            is AnimeLoadResponse -> response.episodes.values.flatten().map { CsCatalogEpisode(it.name, null, it.episode, it.data) }
            else -> emptyList()
        }
        val data = episodeData ?: when (response) {
            is MovieLoadResponse -> response.dataUrl
            is LiveStreamLoadResponse -> response.dataUrl
            else -> null
        }
        if (data == null) return@withContext CsCatalogStreams(detail, emptyList(), emptyList(), episodes)
        val plugin = repository.enabledPlugins().firstOrNull { it.id == item.pluginId } ?: return@withContext null
        val api = runCatching { repository.loadApis(plugin.id).firstOrNull { it.name == item.apiName } }.getOrNull() ?: return@withContext null
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()
        val completed = withTimeoutOrNull(timeoutMs) {
            runCatching {
                api.loadLinks(data = data, isCasting = false, subtitleCallback = { subtitles += it }, callback = { links += it })
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Oynatma bağlantıları alınamadı: ${item.title}", it)
            }
            true
        } ?: false
        if (!completed) return@withContext null
        CsCatalogStreams(detail, links, subtitles, episodes)
    }
    private fun SearchResponse.toCatalogItem(catalog: CsCatalog) = CsCatalogItem(
        pluginId = catalog.pluginId,
        pluginName = catalog.pluginName,
        apiName = catalog.apiName,
        title = name,
        url = url,
        posterUrl = posterUrl,
        typeName = type.toString(),
    )

    private companion object {
        const val TAG = "PlayCsCatalog"
    }
}
