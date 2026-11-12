package com.kaan.play.data.library

import com.kaan.play.data.model.MediaType
import java.text.Collator
import java.util.Locale

/** Ana sayfadaki "İzlemeye devam et" satırı için sıralama seçenekleri. */
enum class ContinueSortMode {
    Recent,
    Oldest,
    MoviesFirst,
    SeriesFirst,
    TitleAsc,
}

/** "İzlemeye devam et" öğeleri için saf sıralama kuralları (Android'den bağımsız). */
object ContinueSorting {
    private val turkishLocale = Locale.forLanguageTag("tr")

    fun sort(items: List<ContinueItem>, mode: ContinueSortMode): List<ContinueItem> {
        if (items.size <= 1) return items
        val collator = Collator.getInstance(turkishLocale).apply {
            strength = Collator.SECONDARY
        }
        val byRecentDesc = Comparator<ContinueItem> { a, b ->
            val cmp = b.entry.lastWatchedAt.compareTo(a.entry.lastWatchedAt)
            if (cmp != 0) cmp else a.entry.tmdbId.compareTo(b.entry.tmdbId)
        }
        val comparator: Comparator<ContinueItem> = when (mode) {
            ContinueSortMode.Recent -> byRecentDesc
            ContinueSortMode.Oldest -> Comparator { a, b ->
                val cmp = a.entry.lastWatchedAt.compareTo(b.entry.lastWatchedAt)
                if (cmp != 0) cmp else a.entry.tmdbId.compareTo(b.entry.tmdbId)
            }
            ContinueSortMode.MoviesFirst -> Comparator { a, b ->
                val aMovie = a.entry.type == MediaType.Movie
                val bMovie = b.entry.type == MediaType.Movie
                if (aMovie != bMovie) {
                    if (aMovie) -1 else 1
                } else {
                    byRecentDesc.compare(a, b)
                }
            }
            ContinueSortMode.SeriesFirst -> Comparator { a, b ->
                val aSeries = a.entry.type == MediaType.Tv
                val bSeries = b.entry.type == MediaType.Tv
                if (aSeries != bSeries) {
                    if (aSeries) -1 else 1
                } else {
                    byRecentDesc.compare(a, b)
                }
            }
            ContinueSortMode.TitleAsc -> Comparator { a, b ->
                val cmp = collator.compare(a.entry.title.trim(), b.entry.title.trim())
                if (cmp != 0) cmp else byRecentDesc.compare(a, b)
            }
        }
        return items.sortedWith(comparator)
    }
}
