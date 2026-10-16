package com.kaan.play.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CloudStream ana sayfa katalogları.
 *
 * Bu katman sadece eklentinin ilan ettiği katalogları okur; Nuvio ve mevcut akış
 * sağlayıcılarının davranışına dokunmaz. Her eklenti/katalog ayrı hata sınırında çalışır.
 */
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

data class CsCatalogRow(
    val catalog: CsCatalog,
    val items: List<CsCatalogItem>,
    val error: String? = null,
)

/**
 * Her çağrıda etkin eklentilerden katalogları üretir. DEX yükleme mevcut
 * CsRepository/CsDexLoader üzerinden yapıldığı için kurulum biçimi değişmez.
 */
class CsCatalogRepository(
    private val repository: CsRepository,
    private val timeoutMs: Long = 12_000L,
) {
    suspend fun catalogs(): List<CsCatalog> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CsCatalog>()
        repository.enabledPlugins().forEach { plugin ->
            runCatching {
                repository.loadApis(plugin.id).forEach { api ->
                    if (!api.hasMainPage) return@forEach
                    api.mainPage.forEach { page ->
                        result += CsCatalog(
                            key = "${plugin.id}:${api.name}:${page.name}:${page.data.orEmpty()}",
                            pluginId = plugin.id,
                            pluginName = plugin.name,
                            apiName = api.name,
                            title = if (api.mainPage.size > 1) "${plugin.name} · ${page.name}" else plugin.name,
                            request = MainPageRequest(page.name, page.data.orEmpty()),
                        )
                    }
                }
            }.onFailure {
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
                .onFailure { Log.w(TAG, "Katalog yüklenemedi: ${catalog.title}, sayfa=$page", it) }
                .getOrNull()
        } ?: return@withContext null
        CsCatalogPage(
            catalog = catalog,
            items = response.items.flatMap { row -> row.list.map { it.toCatalogItem(catalog) } },
            page = page,
            hasMore = response.hasNext,
        )
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
