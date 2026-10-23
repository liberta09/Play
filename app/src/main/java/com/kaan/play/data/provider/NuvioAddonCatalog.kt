package com.kaan.play.data.provider

import android.util.Log
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    override fun rows(): Flow<List<CatalogRow>> = flow {
        val catalogs = addon.manifest.catalogs
        if (catalogs.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val rows = mutableListOf<CatalogRow>()
        for (cat in catalogs) {
            val urlString = "${addon.baseUrl}/catalog/${cat.type}/${cat.id}.json"
            val httpUrl = urlString.toHttpUrlOrNull()
            if (httpUrl == null) {
                Log.w(TAG, "$name: Geçersiz katalog URL: $urlString")
                continue
            }

            try {
                val jsonText = fetchAddonText(client, httpUrl, timeout, MAX_BYTES)
                val response = PlayJson.decodeFromString(StremioCatalogResponse.serializer(), jsonText)
                val items = response.metas
                    .distinctBy { it.id }
                    .map { meta ->
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

                if (items.isNotEmpty()) {
                    val rowTitle = cat.name ?: when (cat.id) {
                        "top" -> if (cat.type == "series") "Popüler Diziler" else "Popüler Filmler"
                        else -> cat.id.replaceFirstChar { it.uppercase() }
                    }
                    rows += CatalogRow(
                        id = "${cat.type}:${cat.id}",
                        title = rowTitle,
                        items = items,
                        hasNext = items.size >= 10,
                        page = 1,
                        providerId = providerId,
                        providerName = name,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "$name/${cat.id} katalog çekilemedi: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        emit(rows)
    }.flowOn(Dispatchers.IO)

    override suspend fun loadMore(rowId: String, page: Int): List<CatalogItem> = withContext(Dispatchers.IO) {
        val parts = rowId.split(':')
        if (parts.size < 2) return@withContext emptyList()
        val type = parts[0]
        val id = parts[1]
        val skip = (page - 1) * 20
        val urlString = "${addon.baseUrl}/catalog/$type/$id/skip=$skip.json"
        val httpUrl = urlString.toHttpUrlOrNull() ?: return@withContext emptyList()

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
