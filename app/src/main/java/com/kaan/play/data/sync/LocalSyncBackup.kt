package com.kaan.play.data.sync

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
data class LocalProfileBackup(
    val format: Int = 1,
    val profileName: String,
    val profileId: String,
    val exportedAt: Long,
    val history: List<HistoryBackup> = emptyList(),
    val favorites: List<FavoriteBackup> = emptyList(),
)

@Serializable
data class HistoryBackup(
    val tmdbId: Int,
    val type: String,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val title: String,
    val posterPath: String? = null,
    val pluginId: String? = null,
    val dataUrl: String? = null,
    val contentUrl: String? = null,
    val manuallyMarkedWatched: Boolean = false,
)

@Serializable
data class FavoriteBackup(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterPath: String? = null,
    val addedAt: Long,
)

class LocalSyncBackup(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val profile: LocalProfileRepository,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { action -> action() },
) {
    suspend fun clearCurrent() {
        val userId = profile.ensureUserId()
        val profileId = profile.ensureId()
        clearProfile(userId, profileId)
    }

    suspend fun clearProfile(userId: String, profileId: String) {
        inTransaction {
            history.clear(userId, profileId)
            favorites.clear(userId, profileId)
        }
    }

    suspend fun exportJson(): String {
        val profileId = profile.ensureId()
        val name = profile.name.first()
        val userId = profile.ensureUserId()
        return PlayJson.encodeToString(
            LocalProfileBackup.serializer(),
            LocalProfileBackup(
                profileName = name,
                profileId = profileId,
                exportedAt = System.currentTimeMillis(),
                history = history.all(userId, profileId).map {
                    HistoryBackup(it.tmdbId, it.type.name, it.season, it.episode, it.positionMs, it.durationMs, it.lastWatchedAt, it.title, it.posterPath, it.pluginId, it.dataUrl, it.contentUrl, it.manuallyMarkedWatched)
                },
                favorites = favorites.all(userId, profileId).map { FavoriteBackup(it.tmdbId, it.type.name, it.title, it.posterPath, it.addedAt) },
            ),
        )
    }

    suspend fun importJson(raw: String): Result<Int> = runCatching {
        val backup = PlayJson.decodeFromString(LocalProfileBackup.serializer(), raw)
        require(backup.format == 1) { "Desteklenmeyen yerel profil yedeği" }
        val userId = profile.ensureUserId()
        val profileId = profile.ensureId()
        val historyRows = backup.history.mapNotNull { row ->
            val type = MediaType.entries.firstOrNull { it.name == row.type } ?: return@mapNotNull null
            WatchHistoryEntity(id=0, tmdbId=row.tmdbId, type=type, userId=userId, profileId=profileId, season=row.season, episode=row.episode, positionMs=row.positionMs, durationMs=row.durationMs, lastWatchedAt=row.lastWatchedAt, title=row.title, posterPath=row.posterPath, pluginId=row.pluginId, dataUrl=row.dataUrl, contentUrl=row.contentUrl, manuallyMarkedWatched=row.manuallyMarkedWatched)
        }
        val favoriteRows = backup.favorites.mapNotNull { row ->
            val type = MediaType.entries.firstOrNull { it.name == row.type } ?: return@mapNotNull null
            FavoriteEntity(tmdbId=row.tmdbId, type=type, userId=userId, profileId=profileId, title=row.title, posterPath=row.posterPath, addedAt=row.addedAt)
        }
        inTransaction {
            history.clear(userId, profileId)
            favorites.clear(userId, profileId)
            historyRows.forEach { history.upsert(it) }
            favoriteRows.forEach { favorites.add(it) }
        }
        profile.setName(backup.profileName)
        profile.ensureId()
        backup.history.size + backup.favorites.size
    }

    /** İki cihazın yerel yedeğini son izleme/favori ekleme zamanına göre birleştirir. */
    suspend fun mergeJson(raw: String): Result<Int> {
        val userId = profile.ensureUserId()
        val profileId = profile.ensureId()
        return mergeJsonFor(userId, profileId, raw)
    }

    /** Buluttan gelen yedeği aktif profili değiştirmeden hedef profile birleştirir. */
    suspend fun mergeJsonFor(
        userId: String,
        profileId: String,
        raw: String,
        historyOverride: List<HistoryBackup>? = null,
        favoritesOverride: List<FavoriteBackup>? = null,
    ): Result<Int> = runCatching {
        val incoming = PlayJson.decodeFromString(LocalProfileBackup.serializer(), raw)
        require(incoming.format == 1) { "Desteklenmeyen yerel profil yedeği" }
        require(incoming.profileId == profileId) { "Yedek profil kimliği eşleşmiyor" }

        val currentHistory = history.all(userId, profileId).map {
            HistoryBackup(it.tmdbId, it.type.name, it.season, it.episode, it.positionMs, it.durationMs, it.lastWatchedAt, it.title, it.posterPath, it.pluginId, it.dataUrl, it.contentUrl, it.manuallyMarkedWatched)
        }
        val currentFavorites = favorites.all(userId, profileId).map {
            FavoriteBackup(it.tmdbId, it.type.name, it.title, it.posterPath, it.addedAt)
        }
        val mergedHistory = (historyOverride ?: (currentHistory + incoming.history))
            .groupBy(::historyKey)
            .map { (_, rows) -> rows.maxBy { it.lastWatchedAt } }
        val mergedFavorites = (favoritesOverride ?: (currentFavorites + incoming.favorites))
            .groupBy(::favoriteKey)
            .map { (_, rows) -> rows.maxBy { it.addedAt } }
        val historyChanged = currentHistory.associateBy(::historyKey) != mergedHistory.associateBy(::historyKey)
        val favoritesChanged = currentFavorites.associateBy(::favoriteKey) != mergedFavorites.associateBy(::favoriteKey)

        if (historyChanged || favoritesChanged) {
            val historyRows = mergedHistory.mapNotNull { row ->
                val type = MediaType.entries.firstOrNull { it.name == row.type } ?: return@mapNotNull null
                WatchHistoryEntity(id=0, tmdbId=row.tmdbId, type=type, userId=userId, profileId=profileId, season=row.season, episode=row.episode, positionMs=row.positionMs, durationMs=row.durationMs, lastWatchedAt=row.lastWatchedAt, title=row.title, posterPath=row.posterPath, pluginId=row.pluginId, dataUrl=row.dataUrl, contentUrl=row.contentUrl, manuallyMarkedWatched=row.manuallyMarkedWatched)
            }
            val favoriteRows = mergedFavorites.mapNotNull { row ->
                val type = MediaType.entries.firstOrNull { it.name == row.type } ?: return@mapNotNull null
                FavoriteEntity(tmdbId=row.tmdbId, type=type, userId=userId, profileId=profileId, title=row.title, posterPath=row.posterPath, addedAt=row.addedAt)
            }
            inTransaction {
                if (historyChanged) {
                    history.clear(userId, profileId)
                    historyRows.forEach { history.upsert(it) }
                }
                if (favoritesChanged) {
                    favorites.clear(userId, profileId)
                    favoriteRows.forEach { favorites.add(it) }
                }
            }
        }
        mergedHistory.size + mergedFavorites.size
    }

    private fun historyKey(row: HistoryBackup) = "${row.tmdbId}:${row.type}:${row.season}:${row.episode}"
    private fun favoriteKey(row: FavoriteBackup) = "${row.tmdbId}:${row.type}"
}
