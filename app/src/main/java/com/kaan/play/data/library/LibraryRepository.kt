package com.kaan.play.data.library

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.sync.LocalProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Yerel kütüphane: izleme geçmişi ve favoriler. */
class LibraryRepository(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val profile: LocalProfileRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val userFlow: Flow<String> = profile.userId.map { it.ifEmpty { "legacy-user" } }
    private val profileFlow: Flow<String> = profile.id.map { it.ifEmpty { "legacy-profile" } }

    private suspend fun scope(): Pair<String, String> {
        val u = profile.userId.first().ifEmpty { "legacy-user" }
        val p = profile.id.first().ifEmpty { "legacy-profile" }
        return Pair(u, p)
    }

    /** Her içeriğin (film/dizi) en son kaydı, yeniden eskiye. */
    val latestPerContent: Flow<List<WatchHistoryEntity>> = profile.activeProfile.flatMapLatest { p ->
        val u = p?.id?.let { profile.userId.first().ifEmpty { "legacy-user" } } ?: "legacy-user"
        val profId = p?.id ?: "legacy-profile"
        history.observeLatestPerContent(u, profId)
    }

    /** Geçmiş ekranı: her kayıt ayrı (film, dizide her bölüm), son izlenen üstte. */
    val watchHistory: Flow<List<WatchHistoryEntity>> = profile.activeProfile.flatMapLatest { p ->
        val u = p?.id?.let { profile.userId.first().ifEmpty { "legacy-user" } } ?: "legacy-user"
        val profId = p?.id ?: "legacy-profile"
        history.observeHistory(u, profId)
    }

    val allFavorites: Flow<List<FavoriteEntity>> = profile.activeProfile.flatMapLatest { p ->
        val u = p?.id?.let { profile.userId.first().ifEmpty { "legacy-user" } } ?: "legacy-user"
        val profId = p?.id ?: "legacy-profile"
        favorites.observeAll(u, profId)
    }

    suspend fun removeFavorite(type: MediaType, tmdbId: Int) {
        val (u, p) = scope()
        favorites.remove(u, p, tmdbId, type)
    }

    suspend fun removeHistory(id: Long) {
        val (u, p) = scope()
        history.delete(u, p, id)
    }

    suspend fun clearHistory() {
        val (u, p) = scope()
        history.clear(u, p)
    }

    fun isFavorite(type: MediaType, tmdbId: Int): Flow<Boolean> = profile.activeProfile.flatMapLatest { p ->
        val u = p?.id?.let { profile.userId.first().ifEmpty { "legacy-user" } } ?: "legacy-user"
        val profId = p?.id ?: "legacy-profile"
        favorites.observeIsFavorite(u, profId, tmdbId, type)
    }

    suspend fun setFavorite(media: MediaSummary, favorite: Boolean) {
        val (u, p) = scope()
        if (favorite) {
            favorites.add(
                FavoriteEntity(
                    tmdbId = media.id,
                    type = media.type,
                    userId = u,
                    profileId = p,
                    title = media.title,
                    posterPath = media.posterPath,
                    addedAt = clock(),
                ),
            )
        } else {
            favorites.remove(u, p, media.id, media.type)
        }
    }

    suspend fun addHistoryToFavorites(entry: WatchHistoryEntity) {
        val (u, p) = scope()
        favorites.add(
            FavoriteEntity(
                tmdbId = entry.tmdbId,
                type = entry.type,
                userId = u,
                profileId = p,
                title = entry.title,
                posterPath = entry.posterPath,
                addedAt = clock(),
            )
        )
    }

    suspend fun progress(target: PlayTarget): WatchHistoryEntity? {
        val (u, p) = scope()
        val r = target.request
        return history.find(u, p, r.tmdbId, r.type, r.season ?: 0, r.episode ?: 0)
    }

    suspend fun saveProgress(target: PlayTarget, positionMs: Long, durationMs: Long): Boolean {
        if (target.request.addonType in listOf("tv", "channel")) return false
        if (!WatchRules.shouldRecord(positionMs, durationMs)) return false
        val (u, p) = scope()
        val r = target.request
        val season = r.season ?: 0
        val episode = r.episode ?: 0
        val existing = history.find(u, p, r.tmdbId, r.type, season, episode)
        history.upsert(
            WatchHistoryEntity(
                id = existing?.id ?: 0,
                tmdbId = r.tmdbId,
                type = r.type,
                userId = u,
                profileId = p,
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
