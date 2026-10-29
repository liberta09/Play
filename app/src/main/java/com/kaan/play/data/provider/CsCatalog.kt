package com.kaan.play.data.provider

import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.model.MediaType
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** CloudStream .cs3 eklentileri için katalog sağlayıcı. [pluginId]: DB'deki `PluginEntity.id`. */
class CsCatalog(
    private val pluginId: Long,
    override val name: String,
    private val repository: CsRepository,
    private val runtime: CsRuntime,
) : ProviderCatalog {

    override val providerId: String = "cs:$pluginId"

    override fun rows(): Flow<List<CatalogRow>> = flow {
        val apis = repository.loadApis(pluginId).apisOrEmpty()
        if (apis.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        emit(toRows(runtime.mainPage(apis, page = 1)))
    }.flowOn(Dispatchers.IO)

    /**
     * Yalnızca CloudStream Live API’lerini tarar. Her eklentiye kısa toplam süre bütçesi ve API başına
     * sınırlı sayıda ana sayfa bölümü uygulanır; genel katalogdaki film/dizi bölümleri hiç çağrılmaz.
     */
    suspend fun liveRows(
        timeoutMs: Long = LIVE_SECTION_TIMEOUT_MS,
        totalBudgetMs: Long = LIVE_TOTAL_BUDGET_MS,
        maxSectionsPerApi: Int = LIVE_MAX_SECTIONS_PER_API,
    ): List<CatalogRow> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val apiResult = withTimeoutOrNull(minOf(totalBudgetMs, LIVE_API_LOAD_TIMEOUT_MS).coerceAtLeast(1L)) {
            repository.loadApis(pluginId)
        } ?: return@withContext emptyList()
        val liveApis = apiResult.apisOrEmpty()
            .filter { api -> mayContainLiveChannels(api) || isKnownLiveSource(api) }
            .sortedByDescending { runCatching { TvType.Live in it.supportedTypes }.getOrDefault(false) }
        if (liveApis.isEmpty()) return@withContext emptyList()

        val deadline = startedAt + totalBudgetMs
        val mainDeadline = minOf(
            deadline - LIVE_SEARCH_BUDGET_MS,
            System.currentTimeMillis() + LIVE_MAINPAGE_BUDGET_MS,
        )
        val mainSections = mutableListOf<com.kaan.play.data.cloudstream.CsCatalogSection>()
        for (api in liveApis) {
            val remaining = mainDeadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            val liveSectionNames = runCatching { api.mainPage.map { it.name }.filter(::isLiveSectionTitle).toSet() }
                .getOrDefault(emptySet())
            val sectionFilter: ((String) -> Boolean)? = liveSectionNames.takeIf { it.isNotEmpty() }?.let { names ->
                { sectionName -> sectionName in names }
            }
            val apiBudget = minOf(LIVE_API_TIMEOUT_MS, remaining).coerceAtLeast(1L)
            val sections = withTimeoutOrNull(apiBudget) {
                runtime.mainPage(
                    api = api,
                    page = 1,
                    timeoutMs = minOf(timeoutMs, LIVE_SECTION_TIMEOUT_MS, apiBudget),
                    maxSectionsPerApi = if (sectionFilter == null && !isKnownLiveSource(api))
                        minOf(maxSectionsPerApi, LIVE_GENERIC_MAX_SECTIONS) else maxSectionsPerApi,
                    sectionNameFilter = sectionFilter,
                )
            }.orEmpty()
            mainSections += sections
        }
        val mainPageRows = toRows(mainSections, liveOnly = true)
        if (mainPageRows.isNotEmpty()) return@withContext mainPageRows

        val searchRemaining = minOf(deadline - System.currentTimeMillis(), LIVE_SEARCH_BUDGET_MS)
        if (searchRemaining <= 0L) return@withContext emptyList()
        val searchSections = withTimeoutOrNull(searchRemaining) {
            runtime.liveSearch(
                apis = liveApis,
                timeoutMs = minOf(LIVE_SEARCH_QUERY_TIMEOUT_MS, searchRemaining.coerceAtLeast(1L)),
                isLiveCandidate = { item -> isLive(item) },
            )
        }.orEmpty()
        toRows(searchSections, liveOnly = true)
    }

    private fun mayContainLiveChannels(api: MainAPI): Boolean = runCatching {
        TvType.Live in api.supportedTypes || api.mainPage.any { section -> isLiveSectionTitle(section.name) }
    }.getOrDefault(false)

    private fun isKnownLiveSource(api: MainAPI): Boolean =
        LiveCatalogRules.isKnownLiveChannel(api.name) || LiveCatalogRules.isKnownLiveChannel(name) ||
            LiveCatalogRules.isKnownLiveProvider(api.name) || LiveCatalogRules.isKnownLiveProvider(name)

    private fun isLiveSectionTitle(value: String): Boolean {
        val title = value.lowercase(java.util.Locale.ROOT)
        return listOf("live", "channel", "canlı", "kanal", "iptv", "television").any(title::contains) ||
            TV_TITLE_PATTERN.containsMatchIn(title) || LiveCatalogRules.isKnownLiveChannel(value)
    }

    private fun toRows(sections: List<com.kaan.play.data.cloudstream.CsCatalogSection>, liveOnly: Boolean = false): List<CatalogRow> =
        sections.mapIndexedNotNull { index, section ->
            val items = section.items
                .distinctBy { it.url.ifBlank { it.name } }
                .filter { !liveOnly || isLive(it) }
                .map { item ->
                    val live = isLive(item)
                    CatalogItem(
                        id = "${item.name}_${item.url}",
                        title = item.name,
                        year = com.kaan.play.data.stream.TitleCleaner.cleanTitleAndYear(item.name).second,
                        subtitle = when {
                            live -> "Canlı"
                            item.type == TvType.TvSeries -> "Dizi"
                            item.type == TvType.Anime -> "Anime"
                            else -> null
                        },
                        posterUrl = item.posterUrl,
                        posterHeaders = item.posterHeaders,
                        mediaType = when {
                            live || item.type == TvType.TvSeries || item.type == TvType.Anime -> MediaType.Tv
                            else -> MediaType.Movie
                        },
                        providerId = providerId,
                        rawUrl = item.url,
                        addonType = if (live) "channel" else null,
                    )
                }
            if (items.isEmpty()) null else CatalogRow(
                id = "${section.title}_$index",
                title = section.title,
                items = items,
                hasNext = section.hasNext,
                page = 1,
                isHorizontalImages = section.isHorizontalImages,
                providerId = providerId,
                providerName = name,
            )
        }

    private fun isLive(item: SearchResponse): Boolean =
        item is LiveSearchResponse || item.type == TvType.Live || LiveCatalogRules.isKnownLiveChannel(item.name)

    override suspend fun loadMore(rowId: String, page: Int): List<CatalogItem> = withContext(Dispatchers.IO) {
        val apis = repository.loadApis(pluginId).apisOrEmpty()
        if (apis.isEmpty()) return@withContext emptyList()

        for (api in apis) {
            val matching = api.mainPage.firstOrNull { rowId.contains(it.name) || rowId.startsWith(it.name) }
            if (matching != null) {
                val req = MainPageRequest(matching.name, matching.data, matching.horizontalImages)
                val resp = try { api.getMainPage(page, req) } catch (_: Throwable) { null }
                return@withContext resp?.items?.flatMap { it.list }
                    ?.distinctBy { it.url.ifBlank { it.name } }
                    ?.map { item ->
                        val live = isLive(item)
                        CatalogItem(
                            id = "${item.name}_${item.url}",
                            title = item.name,
                            year = com.kaan.play.data.stream.TitleCleaner.cleanTitleAndYear(item.name).second,
                            subtitle = when {
                                live -> "Canlı"
                                item.type == TvType.TvSeries -> "Dizi"
                                item.type == TvType.Anime -> "Anime"
                                else -> null
                            },
                            posterUrl = item.posterUrl,
                            posterHeaders = item.posterHeaders,
                            mediaType = when {
                                live || item.type == TvType.TvSeries || item.type == TvType.Anime -> MediaType.Tv
                                else -> MediaType.Movie
                            },
                            providerId = providerId,
                            rawUrl = item.url,
                            addonType = if (live) "channel" else null,
                        )
                    } ?: emptyList()
            }
        }
        emptyList()
    }

    private companion object {
        const val LIVE_SECTION_TIMEOUT_MS = 1_500L
        const val LIVE_API_LOAD_TIMEOUT_MS = 2_000L
        const val LIVE_API_TIMEOUT_MS = 1_500L
        const val LIVE_MAINPAGE_BUDGET_MS = 3_000L
        const val LIVE_SEARCH_BUDGET_MS = 1_500L
        const val LIVE_SEARCH_QUERY_TIMEOUT_MS = 450L
        const val LIVE_TOTAL_BUDGET_MS = 6_500L
        const val LIVE_MAX_SECTIONS_PER_API = 6
        const val LIVE_GENERIC_MAX_SECTIONS = 2
        val TV_TITLE_PATTERN = Regex("\\btv\\b")
    }
}
