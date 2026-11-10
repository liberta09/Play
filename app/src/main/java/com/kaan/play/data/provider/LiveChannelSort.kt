package com.kaan.play.data.provider

import com.kaan.play.data.db.LiveChannelActivityEntity
import com.kaan.play.data.db.LiveChannelDao
import com.kaan.play.data.db.LiveChannelOrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

enum class LiveTvSortMode {
    TitleAscending,
    TitleDescending,
    Recent,
    FavoritesFirst,
    Manual,
}

/** Canlı TV kanalları için saf sıralama ve manuel sıra güncelleme kuralları (Android'den bağımsız). */
object LiveChannelSort {
    private val turkishLocale = Locale.forLanguageTag("tr")

    private fun titleCollator(): Collator = Collator.getInstance(turkishLocale).apply {
        strength = Collator.SECONDARY
    }

    fun <T> sort(
        channels: List<T>,
        mode: LiveTvSortMode,
        keyOf: (T) -> String,
        titleOf: (T) -> String,
        activity: Map<String, LiveChannelActivityEntity>,
        order: Map<String, Int>,
    ): List<T> {
        if (channels.size <= 1) return channels
        val collator = titleCollator()
        val byTitleAsc = Comparator<T> { a, b ->
            val cmp = collator.compare(titleOf(a).trim(), titleOf(b).trim())
            if (cmp != 0) cmp else keyOf(a).compareTo(keyOf(b))
        }
        val comparator: Comparator<T> = when (mode) {
            LiveTvSortMode.TitleAscending -> byTitleAsc
            LiveTvSortMode.TitleDescending -> Comparator { a, b ->
                val cmp = collator.compare(titleOf(b).trim(), titleOf(a).trim())
                if (cmp != 0) cmp else keyOf(a).compareTo(keyOf(b))
            }
            LiveTvSortMode.Recent -> Comparator { a, b ->
                val ta = activity[keyOf(a)]?.lastWatchedAt ?: 0L
                val tb = activity[keyOf(b)]?.lastWatchedAt ?: 0L
                val aWatched = ta > 0L
                val bWatched = tb > 0L
                when {
                    aWatched != bWatched -> if (aWatched) -1 else 1
                    aWatched && ta != tb -> tb.compareTo(ta)
                    else -> byTitleAsc.compare(a, b)
                }
            }
            LiveTvSortMode.FavoritesFirst -> Comparator { a, b ->
                val fa = activity[keyOf(a)]?.isFavorite == true
                val fb = activity[keyOf(b)]?.isFavorite == true
                if (fa != fb) {
                    if (fa) -1 else 1
                } else {
                    byTitleAsc.compare(a, b)
                }
            }
            LiveTvSortMode.Manual -> Comparator { a, b ->
                val ia = order[keyOf(a)] ?: Int.MAX_VALUE
                val ib = order[keyOf(b)] ?: Int.MAX_VALUE
                if (ia != ib) ia.compareTo(ib) else byTitleAsc.compare(a, b)
            }
        }
        return channels.sortedWith(comparator)
    }

    /**
     * Görünür listede [channelKey] öğesini [delta] (-1 yukarı, +1 aşağı) kaydırarak kalıcı [LiveChannelOrderEntity] listesini üretir.
     * [allManualKeys]: mevcut manuel sıralamadaki tüm kanal anahtarları; [visibleKeys]: şu an ekranda görünen sıralı anahtarlar.
     */
    fun computeMovedOrder(
        visibleKeys: List<String>,
        allManualKeys: List<String>,
        existingOrder: Map<String, Int>,
        channelKey: String,
        delta: Int,
    ): List<LiveChannelOrderEntity>? {
        val visibleIdx = visibleKeys.indexOf(channelKey)
        val targetVisibleIdx = visibleIdx + delta
        if (visibleIdx < 0 || targetVisibleIdx !in visibleKeys.indices || delta == 0) return null
        val neighborKey = visibleKeys[targetVisibleIdx]

        // Önce mevcut kayıtlı anahtarlar (sortIndex sırasıyla), sonra henüz kaydı olmayan yeni kanallar.
        val persistedOrdered = existingOrder.entries.sortedBy { it.value }.map { it.key }
        val combined = (persistedOrdered + allManualKeys).distinct().toMutableList()
        val idxA = combined.indexOf(channelKey)
        val idxB = combined.indexOf(neighborKey)
        if (idxA < 0 || idxB < 0) return null
        combined[idxA] = neighborKey
        combined[idxB] = channelKey
        return combined.mapIndexed { index, key -> LiveChannelOrderEntity(channelKey = key, sortIndex = index) }
    }
}

/** Canlı TV kanalları için izleme zamanı, favori ve manuel sıra deposu. */
class LiveChannelRepository(
    private val dao: LiveChannelDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val activityByKey: Flow<Map<String, LiveChannelActivityEntity>> =
        dao.observeActivity().map { rows -> rows.associateBy { it.channelKey } }

    val orderByKey: Flow<Map<String, Int>> =
        dao.observeOrder().map { rows -> rows.associate { it.channelKey to it.sortIndex } }

    suspend fun markWatched(channelKey: String) {
        if (channelKey.isBlank()) return
        dao.markWatched(channelKey, clock())
    }

    suspend fun toggleFavorite(channelKey: String) {
        if (channelKey.isBlank()) return
        val current = dao.activityFor(channelKey)?.isFavorite ?: false
        dao.setFavorite(channelKey, !current)
    }

    suspend fun moveOrder(
        visibleKeys: List<String>,
        allManualKeys: List<String>,
        channelKey: String,
        delta: Int,
    ) {
        val existing = dao.allOrders().associate { it.channelKey to it.sortIndex }
        val updated = LiveChannelSort.computeMovedOrder(
            visibleKeys = visibleKeys,
            allManualKeys = allManualKeys,
            existingOrder = existing,
            channelKey = channelKey,
            delta = delta,
        ) ?: return
        dao.replaceOrders(updated)
    }

    suspend fun clearWatchHistory() {
        dao.clearWatchTimestamps()
    }

    suspend fun clearFavorites() {
        dao.clearFavorites()
    }
}
