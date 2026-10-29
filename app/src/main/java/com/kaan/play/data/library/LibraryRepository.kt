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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Her history/favorite erişimi aynı anda geçerli userId ve profileId ile sınırlandırılır. */
class LibraryRepository(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val profile: LocalProfileRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private suspend fun scope(): Pair<String, String> = if (profile == null) "test-user" to "test-profile" else profile.ensureUserId() to profile.ensureId()
    private val userFlow get() = profile?.userId ?: flowOf("test-user")
    private val profileFlow get() = profile?.id ?: flowOf("test-profile")
    val latestPerContent: Flow<List<WatchHistoryEntity>> = profileFlow.flatMapLatest { id -> userFlow.flatMapLatest { user -> if (id.isBlank() || user.isBlank()) flowOf(emptyList()) else history.observeLatestPerContent(user,id) } }
    val watchHistory: Flow<List<WatchHistoryEntity>> = profileFlow.flatMapLatest { id -> userFlow.flatMapLatest { user -> if (id.isBlank() || user.isBlank()) flowOf(emptyList()) else history.observeHistory(user,id) } }
    val manuallyWatched: Flow<List<WatchHistoryEntity>> = profileFlow.flatMapLatest { id -> userFlow.flatMapLatest { user -> if (id.isBlank() || user.isBlank()) flowOf(emptyList()) else history.observeManuallyWatched(user,id) } }
    val allFavorites: Flow<List<FavoriteEntity>> = profileFlow.flatMapLatest { id -> userFlow.flatMapLatest { user -> if (id.isBlank() || user.isBlank()) flowOf(emptyList()) else favorites.observeAll(user,id) } }
    suspend fun removeFavorite(type: MediaType, tmdbId: Int) { val (u,p)=scope(); favorites.remove(u,p,tmdbId,type) }
    suspend fun removeHistory(id: Long) { val (u,p)=scope(); history.delete(u,p,id) }
    suspend fun removeContentHistory(entry: WatchHistoryEntity) {
        val (activeUser, activeProfile) = scope()
        val userId = entry.userId.takeIf { it.isNotBlank() && it != "legacy-user" } ?: activeUser
        val profileId = entry.profileId.takeIf { it.isNotBlank() && it != "legacy-profile" } ?: activeProfile
        history.deleteContent(userId, profileId, entry.tmdbId, entry.type)
    }
    suspend fun clearHistory() { val (u,p)=scope(); history.clear(u,p) }
    suspend fun addHistoryToFavorites(entry: WatchHistoryEntity) {
        val (u, p) = scope()
        favorites.add(FavoriteEntity(tmdbId = entry.tmdbId, type = entry.type, title = entry.title, posterPath = entry.posterPath, addedAt = clock(), userId = u, profileId = p))
    }
    suspend fun markHistoryWatched(id: Long): Boolean {
        val (u, p) = scope()
        return history.markWatched(u, p, id, clock()) > 0
    }
    suspend fun markHistoryWatched(entry: WatchHistoryEntity): Boolean {
        val (activeUser, activeProfile) = scope()
        val userId = entry.userId.takeIf { it.isNotBlank() && it != "legacy-user" } ?: activeUser
        val profileId = entry.profileId.takeIf { it.isNotBlank() && it != "legacy-profile" } ?: activeProfile
        return history.markWatched(userId, profileId, entry.id, clock()) > 0
    }
    fun isFavorite(type: MediaType, tmdbId: Int): Flow<Boolean> = profileFlow.flatMapLatest { id -> userFlow.flatMapLatest { user -> if (id.isBlank() || user.isBlank()) flowOf(false) else favorites.observeIsFavorite(user,id,tmdbId,type) } }
    suspend fun setFavorite(media: MediaSummary, favorite: Boolean) { val (u,p)=scope(); if (favorite) favorites.add(FavoriteEntity(tmdbId=media.id,type=media.type,title=media.title,posterPath=media.posterPath,addedAt=clock(),userId=u,profileId=p)) else favorites.remove(u,p,media.id,media.type) }
    suspend fun progress(target: PlayTarget): WatchHistoryEntity? { val (u,p)=scope(); val r=target.request; return history.find(u,p,r.tmdbId,r.type,r.season?:0,r.episode?:0) }
    suspend fun saveProgress(target: PlayTarget, positionMs: Long, durationMs: Long): Boolean {
        if (target.request.addonType in listOf("tv", "channel") || !WatchRules.shouldRecord(positionMs,durationMs)) return false
        val (u,p)=scope(); val r=target.request; val season=r.season?:0; val episode=r.episode?:0; val existing=history.find(u,p,r.tmdbId,r.type,season,episode)
        val latestTitleTimestamp = history.latestTimestampForContent(u, p, r.tmdbId, r.type) ?: 0L
        val recordedAt = maxOf(clock(), latestTitleTimestamp + 1L, (existing?.lastWatchedAt ?: 0L) + 1L)
        history.upsert(WatchHistoryEntity(id=existing?.id?:0,tmdbId=r.tmdbId,type=r.type,season=season,episode=episode,positionMs=positionMs.coerceAtMost(durationMs),durationMs=durationMs,lastWatchedAt=recordedAt,title=target.title,posterPath=target.posterPath,pluginId=r.pluginId,dataUrl=r.dataUrl,contentUrl=r.contentUrl,userId=u,profileId=p))
        return true
    }
}
