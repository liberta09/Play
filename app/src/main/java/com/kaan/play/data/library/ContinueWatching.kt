package com.kaan.play.data.library

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.tmdb.TmdbException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/** "İzlemeye devam et" satırının bir kartı. */
sealed interface ContinueItem {
    val entry: WatchHistoryEntity

    /** Yarım kalmış film/bölüm: kayıtlı konumdan devam. */
    data class Resume(override val entry: WatchHistoryEntity) : ContinueItem

    /** Dizide son bölüm bitti ([entry]); sıradaki bölüm önerilir. */
    data class Next(override val entry: WatchHistoryEntity, val season: Int, val episode: Int) : ContinueItem
}

enum class ContinueSortMode { RecentFirst, OldestFirst, MoviesFirst, SeriesFirst, TitleAscending }

/** Sıralama değişse de hiçbir devam kaydını düşürmeden aynı öğeleri döndürür. */
fun sortContinueItems(items: List<ContinueItem>, mode: ContinueSortMode): List<ContinueItem> {
    val recent = compareByDescending<ContinueItem> { it.entry.lastWatchedAt }.thenByDescending { it.entry.id }
    return when (mode) {
        ContinueSortMode.RecentFirst -> items.sortedWith(recent)
        ContinueSortMode.OldestFirst -> items.sortedWith(compareBy<ContinueItem> { it.entry.lastWatchedAt }.thenBy { it.entry.id })
        ContinueSortMode.MoviesFirst -> items.sortedWith(compareBy<ContinueItem> { if (it.entry.type == MediaType.Movie) 0 else 1 }.then(recent))
        ContinueSortMode.SeriesFirst -> items.sortedWith(compareBy<ContinueItem> { if (it.entry.type == MediaType.Tv) 0 else 1 }.then(recent))
        ContinueSortMode.TitleAscending -> items.sortedWith(
            compareBy<ContinueItem, String>(String.CASE_INSENSITIVE_ORDER) { it.entry.title }.then(recent),
        )
    }
}

/**
 * Geçmişten "devam et" listesi. Yarım kalanlar hemen gelir; bitmiş dizilerin sıradaki bölümü
 * ([nextEpisode]) ağdan çözülünce liste bir kez daha yayılır. Biten filmler listeden kalkar.
 */
class ContinueWatching(
    library: LibraryRepository,
    private val nextEpisode: suspend (WatchHistoryEntity) -> Pair<Int, Int>?,
) {
    val items: Flow<List<ContinueItem>> = library.latestPerContent.transformLatest { rows ->
        val finishedSeries = rows.filter { it.type == MediaType.Tv && WatchRules.isWatched(it.positionMs, it.durationMs) }
        val firstPass = build(rows, emptyMap())
        emit(firstPass)
        if (finishedSeries.isEmpty()) return@transformLatest

        val resolved = coroutineScope {
            finishedSeries.map { row ->
                async { row.id to (try { nextEpisode(row) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; null }) }
            }.awaitAll()
        }.mapNotNull { (id, next) -> next?.let { id to it } }.toMap()
        val secondPass = build(rows, resolved)
        if (resolved.isNotEmpty()) emit(secondPass)
    }

    private fun build(rows: List<WatchHistoryEntity>, next: Map<Long, Pair<Int, Int>>): List<ContinueItem> =
        rows.mapNotNull { row ->
            when {
                !WatchRules.isWatched(row.positionMs, row.durationMs) -> ContinueItem.Resume(row)
                row.type == MediaType.Tv -> next[row.id]?.let { ContinueItem.Next(row, it.first, it.second) }
                else -> null // izlenmiş film listeden kalkar
            }
        }
}

/**
 * Bir bölümden sonraki izlenebilir bölümü bulur: aynı sezonun sonraki bölümü, yoksa sonraki sezonun ilk bölümü.
 * Yayın tarihi gelmemiş (ya da bilinmeyen) bölüm "yok" sayılır; öneri yalnızca oynatılabilir bölümü gösterir.
 * [today]: `yyyy-MM-dd`. Başarılı sonuçlar (yok dahil) bellekte tutulur; ağ hataları [TmdbException] olarak çıkar.
 */
class NextEpisodeResolver(
    private val loadSeason: suspend (tvId: Int, season: Int) -> SeasonDetail,
    private val today: () -> String,
) {
    private val cache = HashMap<Triple<Int, Int, Int>, Pair<Int, Int>?>()

    suspend fun next(tvId: Int, season: Int, episode: Int): Pair<Int, Int>? {
        val key = Triple(tvId, season, episode)
        synchronized(cache) { if (key in cache) return cache[key] }
        val result = find(tvId, season, episode)
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun find(tvId: Int, season: Int, episode: Int): Pair<Int, Int>? {
        val current = loadSeason(tvId, season).episodes
        val later = current.filter { it.number > episode }.minByOrNull { it.number }
        if (later != null) return if (aired(later.airDate)) season to later.number else null

        val following = try {
            loadSeason(tvId, season + 1).episodes
        } catch (e: TmdbException) {
            if (e.kind == TmdbException.Kind.NotFound) return null else throw e
        }
        val first = following.minByOrNull { it.number } ?: return null
        return if (aired(first.airDate)) (season + 1) to first.number else null
    }

    private fun aired(airDate: String?): Boolean = airDate != null && airDate <= today()
}
