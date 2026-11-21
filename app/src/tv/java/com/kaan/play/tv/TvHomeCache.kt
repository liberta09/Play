package com.kaan.play.tv

import android.content.Context
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.provider.ProviderItem
import com.kaan.play.data.provider.ProviderKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ana sayfa satırlarının disk önbelleği (liste önbellekte, açılışta ağı beklemez).
 * Açılışta son görülen satırlar anında gösterilir; sağlayıcılar arkada yenilenir. Yalnız katalog bilgisi
 * (ad, görsel adresi, içerik adresi) saklanır; oturum, başlık ya da anahtar saklanmaz.
 */
class TvHomeCache(context: Context, kind: HomeKind) {
    private val file = File(context.filesDir, when (kind) {
        HomeKind.Home -> "tv_home_drama.json"
        HomeKind.Movies -> "tv_home_movies.json"
        HomeKind.Series -> "tv_home_series.json"
        HomeKind.Shorts -> "tv_home_shorts.json" // Shorts Drama
    })

    init {
        // "Filmler ve diziler" bölümü Filmler + Diziler olarak ayrıldı: eski bölümün önbelleği artık okunmaz.
        if (kind != HomeKind.Home) File(context.filesDir, "tv_home_films.json").delete()
    }

    data class Snapshot(val sections: List<ProviderRows>, val tmdb: List<CatalogRow>)

    fun read(): Snapshot? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        val sections = o.getJSONArray("sections").objects().map { s ->
            val p = s.getJSONObject("provider")
            ProviderRows(
                ProviderItem(id = p.getString("id"), name = p.getString("name"), subtitle = "", kind = ProviderKind.valueOf(p.getString("kind"))),
                s.getJSONArray("rows").objects().map { it.toRow() },
            )
        }
        Snapshot(sections, o.optJSONArray("tmdb")?.objects()?.map { it.toRow() }.orEmpty())
    }.getOrNull()

    fun write(sections: List<ProviderRows>, tmdb: List<CatalogRow>) {
        runCatching {
            val o = JSONObject()
                .put("sections", JSONArray(sections.filter { !it.rows.isNullOrEmpty() }.map { s ->
                    JSONObject()
                        .put("provider", JSONObject().put("id", s.provider.id).put("name", s.provider.name).put("kind", s.provider.kind.name))
                        .put("rows", JSONArray(s.rows.orEmpty().map { it.toJson() }))
                }))
                .put("tmdb", JSONArray(tmdb.map { it.toJson() }))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun CatalogRow.toJson() = JSONObject()
        .put("id", id).put("title", title).put("h", isHorizontalImages)
        .put("pid", providerId).put("pname", providerName)
        .put("items", JSONArray(items.map { i ->
            JSONObject().put("id", i.id).put("t", i.title).put("s", i.subtitle).put("p", i.posterUrl)
                .put("m", i.mediaType.name).put("pid", i.providerId).put("u", i.rawUrl).put("a", i.addonType)
        }))

    private fun JSONObject.toRow() = CatalogRow(
        id = getString("id"), title = getString("title"), isHorizontalImages = optBoolean("h"),
        providerId = getString("pid"), providerName = optString("pname"),
        items = getJSONArray("items").objects().map { i ->
            CatalogItem(
                id = i.getString("id"), title = i.getString("t"), subtitle = i.optNull("s"), posterUrl = i.optNull("p"),
                mediaType = runCatching { MediaType.valueOf(i.getString("m")) }.getOrDefault(MediaType.Movie),
                providerId = i.getString("pid"), rawUrl = i.optNull("u"), addonType = i.optNull("a"),
            )
        },
    )

    private fun JSONObject.optNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).ifEmpty { null }
}
