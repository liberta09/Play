package com.kaan.play.data.provider

import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.model.MediaType
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * CloudStream .cs3 eklentileri için katalog sağlayıcı.
 * [pluginId]: DB'deki `PluginEntity.id`.
 */
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
        val sections = runtime.mainPage(apis, page = 1)
        val rows = sections.mapIndexed { index, section ->
            val items = section.items
                .distinctBy { it.url.ifBlank { it.name } }
                .map { s ->
                    CatalogItem(
                        id = "${s.name}_${s.url}",
                        title = s.name,
                        subtitle = when (s.type) {
                            TvType.TvSeries -> "Dizi"
                            TvType.Anime -> "Anime"
                            else -> null
                        },
                        posterUrl = s.posterUrl,
                        posterHeaders = s.posterHeaders,
                        mediaType = when (s.type) {
                            TvType.TvSeries, TvType.Anime -> MediaType.Tv
                            else -> MediaType.Movie
                        },
                        providerId = providerId,
                        rawUrl = s.url,
                    )
                }
            CatalogRow(
                id = "${section.title}_$index",
                title = section.title,
                items = items,
                hasNext = section.hasNext,
                page = 1,
                isHorizontalImages = section.isHorizontalImages,
                providerId = providerId,
                providerName = name,
            )
        }.filter { it.items.isNotEmpty() }
        emit(rows)
    }.flowOn(Dispatchers.IO)

    override suspend fun loadMore(rowId: String, page: Int): List<CatalogItem> = withContext(Dispatchers.IO) {
        val apis = repository.loadApis(pluginId).apisOrEmpty()
        if (apis.isEmpty()) return@withContext emptyList()

        for (api in apis) {
            val matching = api.mainPage.firstOrNull { rowId.contains(it.name) || rowId.startsWith(it.name) }
            if (matching != null) {
                val req = MainPageRequest(matching.name, matching.data, matching.horizontalImages)
                val resp = try { api.getMainPage(page, req) } catch (_: Throwable) { null }
                val items = resp?.items?.flatMap { it.list }
                    ?.distinctBy { it.url.ifBlank { it.name } }
                    ?.map { s ->
                        CatalogItem(
                            id = "${s.name}_${s.url}",
                            title = s.name,
                            subtitle = when (s.type) {
                                TvType.TvSeries -> "Dizi"
                                TvType.Anime -> "Anime"
                                else -> null
                            },
                            posterUrl = s.posterUrl,
                            posterHeaders = s.posterHeaders,
                            mediaType = when (s.type) {
                                TvType.TvSeries, TvType.Anime -> MediaType.Tv
                                else -> MediaType.Movie
                            },
                            providerId = providerId,
                            rawUrl = s.url,
                        )
                    } ?: emptyList()
                return@withContext items
            }
        }
        emptyList()
    }
}
