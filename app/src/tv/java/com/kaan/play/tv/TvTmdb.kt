package com.kaan.play.tv

import com.kaan.play.R
import com.kaan.play.PlayApp
import com.kaan.play.AppContainer
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.home.HomeRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Filmler ve diziler (§4.1, yatay 16:9). Nuvio eklentileri (izlelan, Dizibox, HDFilmCehennemi, jasoleys, Torrentio…)
 * kendi katalog vermez; Play'deki gibi içerik TMDB'den seçilir, yayını bu eklentiler bulur. Kimlik `tmdb` sağlayıcısı
 * ve `movie:123` / `tv:123` adresiyle taşınır.
 */
internal const val TMDB_PROVIDER = "tmdb"
internal const val IMDB_PROVIDER = "imdb"

/** Film/dizi oynatabilecek bir kaynak var mı: akış veren Nuvio addon'u ya da Nuvio eklenti deposu. */
internal suspend fun AppContainer.hasFilmSources(): Boolean = runCatching {
    plugins.repos.first().isNotEmpty() || addons.enabledAddons().any { it.manifest.providesStreams }
}.getOrDefault(false)

/**
 * TMDB satırları, Play ana sayfasındaki sırayla (Play `HomeRow`: Trend filmler, Trend diziler, Popüler filmler, Popüler diziler;
 * ana sayfa Play'deki gibi). Filmler / Diziler sayfaları yalnız kendi türünün satırlarını ister.
 * Satır kimliği `tmdb:<HomeRow>`: "Tümünü gör" ızgarası sonraki sayfaları bununla ister ([tmdbPage]).
 */
internal suspend fun AppContainer.tmdbRows(rows: List<HomeRow>): List<CatalogRow> = coroutineScope {
    // Satır adları Play'in seçili dilinde (Compose dışı: uygulama bağlamı API 33 altında cihaz dilinde kalırdı).
    val res = appContext.localized()
    rows.map { row ->
        async {
            val items = runCatching { row.page(tmdb, 1).items }.getOrDefault(emptyList()).map { it.toCatalogItem() }
            if (items.isEmpty()) null else CatalogRow(
                id = TMDB_ROW_PREFIX + row.name, title = res.getString(row.tvTitle()), items = items, isHorizontalImages = true,
                providerId = TMDB_PROVIDER, providerName = res.getString(R.string.tv_section_films),
            )
        }
    }.awaitAll().filterNotNull()
}

/** TMDB satırının [page]. sayfası; satır TMDB satırı değilse (ör. eski önbellek kimliği) null. */
internal suspend fun AppContainer.tmdbPage(rowId: String, page: Int): List<CatalogItem>? {
    val row = HomeRow.entries.firstOrNull { TMDB_ROW_PREFIX + it.name == rowId } ?: return null
    return row.page(tmdb, page).items.map { it.toCatalogItem() }
}

private const val TMDB_ROW_PREFIX = "tmdb:"

private fun HomeRow.tvTitle(): Int = when (this) {
    HomeRow.TrendingMovies -> R.string.tv_home_trending_movies
    HomeRow.TrendingSeries -> R.string.tv_home_trending_series
    HomeRow.PopularMovies -> R.string.tv_home_popular_movies
    HomeRow.PopularSeries -> R.string.tv_home_popular_series
}

// Tür etiketi seçili dilde; çağıranlar (ana sayfa, arama) bağlam taşımadığı için Play'in uygulama bağlamıyla okunur.
internal fun MediaSummary.toCatalogItem() = CatalogItem(
    id = "tmdb:${type.tmdbPath}:$id",
    title = title,
    subtitle = listOfNotNull(
        PlayApp.appContext.localized().getString(if (type == MediaType.Tv) R.string.tv_home_type_series else R.string.tv_home_type_movie),
        year?.toString(),
        rating?.takeIf { it > 0 }?.let { "★ " + String.format(java.util.Locale.ROOT, "%.1f", it) },
    ).joinToString(" · "),
    posterUrl = TmdbImages.url(backdropPath ?: posterPath, TmdbImages.Size.Backdrop),
    mediaType = type,
    providerId = TMDB_PROVIDER,
    rawUrl = "${type.tmdbPath}:$id",
)

/**
 * Bir katalog kartının detay adresi:
 * - CloudStream: eklenti + içerik adresi.
 * - TMDB satırı: `tmdb` + `movie:123`.
 * - Nuvio katalog: kimlik IMDb (`tt…`) ise `imdb` + kimlik, `tmdb:…` ise TMDB.
 * Açılamayan kart (ör. kimliği tanınmayan Nuvio içeriği) için null.
 */
internal fun CatalogItem.toRef(landscape: Boolean): DetailRef? {
    rawUrl?.let { return DetailRef(providerId, it, landscape, title, posterUrl) }
    if (providerId.startsWith("nuvio_addon:")) {
        if (Regex("tt\\d+").matches(id)) return DetailRef(IMDB_PROVIDER, id, landscape, title, posterUrl)
        Regex("tmdb:(\\d+)").matchEntire(id)?.let { m ->
            return DetailRef(TMDB_PROVIDER, "${mediaType.tmdbPath}:${m.groupValues[1]}", landscape, title, posterUrl)
        }
    }
    return null
}
