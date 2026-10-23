package com.kaan.play.data.library

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import kotlinx.coroutines.flow.Flow

/** Yerel kütüphane: izleme geçmişi ve favoriler. Kütüphane ekranı 7. adımda bunun üstüne kurulur. */
class LibraryRepository(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Her içeriğin (film/dizi) en son kaydı, yeniden eskiye. "İzlendi" olanlar da dahildir; süzme [ContinueWatching]'de. */
    val latestPerContent: Flow<List<WatchHistoryEntity>> = history.observeLatestPerContent()

    /** Geçmiş ekranı: her kayıt ayrı (film, dizide her bölüm), son izlenen üstte. */
    val watchHistory: Flow<List<WatchHistoryEntity>> = history.observeHistory()

    val allFavorites: Flow<List<FavoriteEntity>> = favorites.observeAll()

    suspend fun removeFavorite(type: MediaType, tmdbId: Int) = favorites.remove(tmdbId, type)

    suspend fun removeHistory(id: Long) = history.delete(id)

    /** Yalnızca geçmişi siler; favoriler etkilenmez. */
    suspend fun clearHistory() = history.clear()

    fun isFavorite(type: MediaType, tmdbId: Int): Flow<Boolean> = favorites.observeIsFavorite(tmdbId, type)

    /** Favori durumunu ayarlar. Başlık/poster, Kütüphane'nin ağsız çizilebilmesi için anlık kopyadır. */
    suspend fun setFavorite(media: MediaSummary, favorite: Boolean) {
        if (favorite) {
            favorites.add(
                FavoriteEntity(
                    tmdbId = media.id,
                    type = media.type,
                    title = media.title,
                    posterPath = media.posterPath,
                    addedAt = clock(),
                ),
            )
        } else {
            favorites.remove(media.id, media.type)
        }
    }

    /** Bu içeriğin (dizide bu bölümün) kaydı; hiç izlenmediyse null. */
    suspend fun progress(target: PlayTarget): WatchHistoryEntity? {
        val r = target.request
        return history.find(r.tmdbId, r.type, r.season ?: 0, r.episode ?: 0)
    }

    /**
     * Konumu kaydeder; [WatchRules.shouldRecord] sağlanmıyorsa (30 sn'den az, süre bilinmiyor) hiçbir şey yazmaz.
     * Aynı içerik/bölüm satırı yenilenir. Konum süreyi aşmaz. @return kaydedildi mi.
     */
    suspend fun saveProgress(target: PlayTarget, positionMs: Long, durationMs: Long): Boolean {
        if (target.request.addonType in listOf("tv", "channel")) return false
        if (!WatchRules.shouldRecord(positionMs, durationMs)) return false
        val r = target.request
        val season = r.season ?: 0
        val episode = r.episode ?: 0
        val existing = history.find(r.tmdbId, r.type, season, episode)
        history.upsert(
            WatchHistoryEntity(
                id = existing?.id ?: 0,
                tmdbId = r.tmdbId,
                type = r.type,
                season = season,
                episode = episode,
                positionMs = positionMs.coerceAtMost(durationMs),
                durationMs = durationMs,
                lastWatchedAt = clock(),
                title = target.title,
                posterPath = target.posterPath,
                pluginId = r.pluginId,
                dataUrl = r.dataUrl,
                contentUrl = r.contentUrl,
            ),
        )

        return true
    }
}
