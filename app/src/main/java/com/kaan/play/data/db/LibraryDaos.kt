package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT h.* FROM watch_history AS h WHERE h.userId=:userId AND h.profileId=:profileId AND h.id = (SELECT candidate.id FROM watch_history AS candidate WHERE candidate.userId=:userId AND candidate.profileId=:profileId AND candidate.tmdbId=h.tmdbId AND candidate.type=h.type ORDER BY candidate.lastWatchedAt DESC, candidate.id DESC LIMIT 1) AND h.durationMs > 0 AND h.manuallyMarkedWatched = 0 ORDER BY h.lastWatchedAt DESC, h.id DESC")
    fun observeLatestPerContent(userId: String = "legacy-user", profileId: String = "legacy-profile"): Flow<List<WatchHistoryEntity>>
    @Query("SELECT h.* FROM watch_history AS h WHERE h.userId=:userId AND h.profileId=:profileId AND h.id = (SELECT candidate.id FROM watch_history AS candidate WHERE candidate.userId=:userId AND candidate.profileId=:profileId AND candidate.tmdbId=h.tmdbId AND candidate.type=h.type ORDER BY candidate.lastWatchedAt DESC, candidate.id DESC LIMIT 1) AND h.durationMs > 0 AND h.manuallyMarkedWatched = 1 ORDER BY h.lastWatchedAt DESC, h.id DESC")
    fun observeManuallyWatched(userId: String = "legacy-user", profileId: String = "legacy-profile"): Flow<List<WatchHistoryEntity>>
    @Query("SELECT * FROM watch_history WHERE userId=:userId AND profileId=:profileId ORDER BY lastWatchedAt DESC, id DESC LIMIT 200")
    fun observeHistory(userId: String = "legacy-user", profileId: String = "legacy-profile"): Flow<List<WatchHistoryEntity>>
    @Query("SELECT * FROM watch_history WHERE userId=:userId AND profileId=:profileId ORDER BY lastWatchedAt DESC, id DESC")
    suspend fun all(userId: String = "legacy-user", profileId: String = "legacy-profile"): List<WatchHistoryEntity>
    @Query("DELETE FROM watch_history WHERE userId=:userId AND profileId=:profileId AND id=:id")
    suspend fun delete(userId: String = "legacy-user", profileId: String = "legacy-profile", id: Long)
    @Query("DELETE FROM watch_history WHERE userId=:userId AND profileId=:profileId AND tmdbId=:tmdbId AND type=:type")
    suspend fun deleteContent(userId: String, profileId: String, tmdbId: Int, type: MediaType)
    @Query("UPDATE watch_history SET positionMs=durationMs, lastWatchedAt=MAX(:watchedAt, (SELECT COALESCE(MAX(previous.lastWatchedAt), 0) + 1 FROM watch_history AS previous WHERE previous.userId=:userId AND previous.profileId=:profileId AND previous.tmdbId=(SELECT selected.tmdbId FROM watch_history AS selected WHERE selected.userId=:userId AND selected.profileId=:profileId AND selected.id=:id) AND previous.type=(SELECT selected.type FROM watch_history AS selected WHERE selected.userId=:userId AND selected.profileId=:profileId AND selected.id=:id))), manuallyMarkedWatched=1 WHERE userId=:userId AND profileId=:profileId AND tmdbId=(SELECT selected.tmdbId FROM watch_history AS selected WHERE selected.userId=:userId AND selected.profileId=:profileId AND selected.id=:id) AND type=(SELECT selected.type FROM watch_history AS selected WHERE selected.userId=:userId AND selected.profileId=:profileId AND selected.id=:id) AND durationMs > 0")
    suspend fun markWatched(userId: String, profileId: String, id: Long, watchedAt: Long): Int
    @Query("DELETE FROM watch_history WHERE userId=:userId AND profileId=:profileId")
    suspend fun clear(userId: String = "legacy-user", profileId: String = "legacy-profile")
    @Query("UPDATE watch_history SET userId=:userId, profileId=:profileId WHERE userId='legacy-user'")
    suspend fun adoptLegacy(userId: String, profileId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)
    @Query("SELECT * FROM watch_history WHERE userId=:userId AND profileId=:profileId AND tmdbId=:tmdbId AND type=:type AND season=:season AND episode=:episode")
    suspend fun find(userId: String = "legacy-user", profileId: String = "legacy-profile", tmdbId: Int, type: MediaType, season: Int, episode: Int): WatchHistoryEntity?
    @Query("SELECT MAX(lastWatchedAt) FROM watch_history WHERE userId=:userId AND profileId=:profileId AND tmdbId=:tmdbId AND type=:type")
    suspend fun latestTimestampForContent(userId: String, profileId: String, tmdbId: Int, type: MediaType): Long?
}

@Dao
interface SubtitleDao {
    @Query("SELECT language FROM subtitle_language WHERE contentKey = :contentKey")
    suspend fun language(contentKey: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLanguage(entity: SubtitleLanguageEntity)
    @Query("SELECT offsetMs FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey")
    suspend fun offset(contentKey: String, subtitleKey: String): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOffset(entity: SubtitleOffsetEntity)
    @Query("DELETE FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey")
    suspend fun clearOffset(contentKey: String, subtitleKey: String)
    @Query("DELETE FROM subtitle_offset WHERE rowid NOT IN (SELECT rowid FROM subtitle_offset ORDER BY updatedAt DESC LIMIT :keep)")
    suspend fun pruneOffsets(keep: Int)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun find(id: Long): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND type = :type AND season = :season AND episode = :episode")
    suspend fun findContent(tmdbId: Int, type: MediaType, season: Int, episode: Int): DownloadEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DownloadEntity): Long
    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, downloadedBytes: Long, totalBytes: Long)
    @Query("UPDATE downloads SET subtitlesJson = :json WHERE id = :id")
    suspend fun setSubtitles(id: Long, json: String)
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("DELETE FROM downloads")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE userId=:userId AND profileId=:profileId ORDER BY addedAt DESC")
    fun observeAll(userId: String = "legacy-user", profileId: String = "legacy-profile"): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE userId=:userId AND profileId=:profileId ORDER BY addedAt DESC")
    suspend fun all(userId: String = "legacy-user", profileId: String = "legacy-profile"): List<FavoriteEntity>
    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE userId=:userId AND profileId=:profileId AND tmdbId=:tmdbId AND type=:type")
    fun observeIsFavorite(userId: String = "legacy-user", profileId: String = "legacy-profile", tmdbId: Int, type: MediaType): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE userId=:userId AND profileId=:profileId AND tmdbId=:tmdbId AND type=:type")
    suspend fun remove(userId: String = "legacy-user", profileId: String = "legacy-profile", tmdbId: Int, type: MediaType)
    @Query("DELETE FROM favorites WHERE userId=:userId AND profileId=:profileId")
    suspend fun clear(userId: String = "legacy-user", profileId: String = "legacy-profile")
    @Query("UPDATE favorites SET userId=:userId, profileId=:profileId WHERE userId='legacy-user'")
    suspend fun adoptLegacy(userId: String, profileId: String)
}
