package com.kaan.play.data.sync

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class LocalSyncBackup(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val profile: LocalProfileRepository,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun favoriteKey(fav: FavoriteBackup): String = "${fav.tmdbId}:${fav.type.lowercase()}"
    private fun historyKey(h: HistoryBackup): String = "${h.tmdbId}:${h.type.lowercase()}:${h.season}:${h.episode}"

    suspend fun exportJson(): String {
        val uId = profile.userId.first().ifEmpty { "legacy-user" }
        val pId = profile.id.first().ifEmpty { "legacy-profile" }
        val pName = profile.name.first().ifEmpty { "Varsayılan" }
        val favs = favorites.all(uId, pId).map {
            FavoriteBackup(
                tmdbId = it.tmdbId,
                type = it.type.name.lowercase(),
                title = it.title,
                posterPath = it.posterPath,
                addedAt = it.addedAt,
            )
        }
        val hist = history.all(uId, pId).map {
            HistoryBackup(
                tmdbId = it.tmdbId,
                type = it.type.name.lowercase(),
                season = it.season,
                episode = it.episode,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                lastWatchedAt = it.lastWatchedAt,
                title = it.title,
                posterPath = it.posterPath,
                pluginId = it.pluginId,
                dataUrl = it.dataUrl,
                contentUrl = it.contentUrl,
            )
        }
        val backup = LocalProfileBackup(
            profileId = pId,
            profileName = pName,
            format = 1,
            favorites = favs,
            history = hist,
            exportedAt = System.currentTimeMillis(),
        )
        return json.encodeToString(LocalProfileBackup.serializer(), backup)
    }

    suspend fun importJson(jsonStr: String): Result<Unit> = runCatching {
        val uId = profile.userId.first().ifEmpty { "legacy-user" }
        val pId = profile.id.first().ifEmpty { "legacy-profile" }
        val backup = json.decodeFromString(LocalProfileBackup.serializer(), jsonStr)
        clearProfile(uId, pId)
        for (f in backup.favorites) {
            val type = if (f.type.equals("tv", ignoreCase = true)) MediaType.Tv else MediaType.Movie
            favorites.add(
                FavoriteEntity(
                    tmdbId = f.tmdbId,
                    type = type,
                    userId = uId,
                    profileId = pId,
                    title = f.title,
                    posterPath = f.posterPath,
                    addedAt = f.addedAt,
                )
            )
        }
        for (h in backup.history) {
            val type = if (h.type.equals("tv", ignoreCase = true)) MediaType.Tv else MediaType.Movie
            history.upsert(
                WatchHistoryEntity(
                    tmdbId = h.tmdbId,
                    type = type,
                    userId = uId,
                    profileId = pId,
                    season = h.season,
                    episode = h.episode,
                    positionMs = h.positionMs,
                    durationMs = h.durationMs,
                    lastWatchedAt = h.lastWatchedAt,
                    title = h.title,
                    posterPath = h.posterPath,
                    pluginId = h.pluginId,
                    dataUrl = h.dataUrl,
                    contentUrl = h.contentUrl,
                )
            )
        }
    }

    suspend fun mergeJson(jsonStr: String): Result<Unit> {
        val uId = profile.userId.first().ifEmpty { "legacy-user" }
        val pId = profile.id.first().ifEmpty { "legacy-profile" }
        return mergeJsonFor(uId, pId, jsonStr)
    }

    suspend fun mergeJsonFor(
        targetUserId: String,
        targetProfileId: String,
        jsonStr: String,
        extraFavorites: List<FavoriteBackup> = emptyList(),
        extraHistory: List<HistoryBackup> = emptyList(),
    ): Result<Unit> = runCatching {
        val backup = if (jsonStr.isNotBlank()) {
            json.decodeFromString(LocalProfileBackup.serializer(), jsonStr)
        } else {
            LocalProfileBackup()
        }
        val existingFavs = favorites.all(targetUserId, targetProfileId)
        val favMap = existingFavs.associateBy { "${it.tmdbId}:${it.type.name.lowercase()}" }.toMutableMap()
        for (f in backup.favorites + extraFavorites) {
            val key = favoriteKey(f)
            val current = favMap[key]
            if (current == null || f.addedAt > current.addedAt) {
                val type = if (f.type.equals("tv", ignoreCase = true)) MediaType.Tv else MediaType.Movie
                val entity = FavoriteEntity(
                    tmdbId = f.tmdbId,
                    type = type,
                    userId = targetUserId,
                    profileId = targetProfileId,
                    title = f.title,
                    posterPath = f.posterPath,
                    addedAt = f.addedAt,
                )
                favorites.add(entity)
                favMap[key] = entity
            }
        }

        val existingHist = history.all(targetUserId, targetProfileId)
        val histMap = existingHist.associateBy { "${it.tmdbId}:${it.type.name.lowercase()}:${it.season}:${it.episode}" }.toMutableMap()
        for (h in backup.history + extraHistory) {
            val key = historyKey(h)
            val current = histMap[key]
            if (current == null || h.lastWatchedAt > current.lastWatchedAt) {
                val type = if (h.type.equals("tv", ignoreCase = true)) MediaType.Tv else MediaType.Movie
                val entity = WatchHistoryEntity(
                    id = current?.id ?: 0,
                    tmdbId = h.tmdbId,
                    type = type,
                    userId = targetUserId,
                    profileId = targetProfileId,
                    season = h.season,
                    episode = h.episode,
                    positionMs = h.positionMs,
                    durationMs = h.durationMs,
                    lastWatchedAt = h.lastWatchedAt,
                    title = h.title,
                    posterPath = h.posterPath,
                    pluginId = h.pluginId,
                    dataUrl = h.dataUrl,
                    contentUrl = h.contentUrl,
                )
                history.upsert(entity)
                histMap[key] = entity
            }
        }
    }

    suspend fun clearCurrent() {
        val uId = profile.userId.first().ifEmpty { "legacy-user" }
        val pId = profile.id.first().ifEmpty { "legacy-profile" }
        clearProfile(uId, pId)
    }

    suspend fun clearProfile(userId: String, profileId: String) {
        favorites.clear(userId, profileId)
        history.clear(userId, profileId)
    }
}
