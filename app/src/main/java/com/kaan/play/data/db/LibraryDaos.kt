package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("UPDATE watch_history SET userId = :userId, profileId = :profileId WHERE userId = 'legacy-user'")
    suspend fun adoptLegacy(userId: String, profileId: String)

    /**
     * Her içeriğin (film ya da dizi) en son izlenen kaydı, yeni izleme üstte, en çok 30 kayıt.
     * İzlendi/izleniyor ayrımı burada değil, [com.kaan.play.data.library.WatchRules]'ta yapılır.
     */
    @Query(
        """
        SELECT h.* FROM watch_history AS h
        WHERE h.userId = :userId AND h.profileId = :profileId AND h.id = (
            SELECT candidate.id FROM watch_history AS candidate
            WHERE candidate.userId = :userId AND candidate.profileId = :profileId
              AND candidate.tmdbId = h.tmdbId AND candidate.type = h.type
            ORDER BY candidate.lastWatchedAt DESC, candidate.id DESC LIMIT 1
        )
        AND h.durationMs > 0
        ORDER BY h.lastWatchedAt DESC, h.id DESC
        """,
    )
    fun observeLatestPerContent(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    ): Flow<List<WatchHistoryEntity>>

    /** Kütüphane > Geçmiş: tüm kayıtlar (film ve her bölüm ayrı), son izlenen üstte. */
    @Query("SELECT * FROM watch_history WHERE userId = :userId AND profileId = :profileId ORDER BY lastWatchedAt DESC, id DESC LIMIT 200")
    fun observeHistory(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    ): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE userId = :userId AND profileId = :profileId ORDER BY lastWatchedAt DESC, id DESC")
    suspend fun all(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    ): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE userId = :userId AND profileId = :profileId AND id = :id")
    suspend fun delete(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        id: Long,
    )

    suspend fun delete(id: Long) = delete(id = id, userId = "legacy-user", profileId = "legacy-profile")

    @Query("DELETE FROM watch_history WHERE userId = :userId AND profileId = :profileId AND tmdbId = :tmdbId AND type = :type")
    suspend fun deleteForMedia(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        tmdbId: Int,
        type: String,
    )

    @Query("DELETE FROM watch_history WHERE userId = :userId AND profileId = :profileId")
    suspend fun clear(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    )

    suspend fun clear() = clear(userId = "legacy-user", profileId = "legacy-profile")

    /** (tmdbId, type, season, episode) benzersizdir: aynı bölüm yeniden kaydedilince satır yenilenir. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE userId = :userId AND profileId = :profileId AND tmdbId = :tmdbId AND type = :type AND season = :season AND episode = :episode")
    suspend fun find(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        tmdbId: Int,
        type: MediaType,
        season: Int,
        episode: Int,
    ): WatchHistoryEntity?

    suspend fun find(tmdbId: Int, type: MediaType, season: Int, episode: Int): WatchHistoryEntity? =
        find("legacy-user", "legacy-profile", tmdbId, type, season, episode)

    @Query("UPDATE watch_history SET positionMs = durationMs, lastWatchedAt = :lastWatchedAt WHERE userId = :userId AND profileId = :profileId AND id = :id AND durationMs > 0")
    suspend fun markFinished(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        id: Long,
        lastWatchedAt: Long,
    )
}

@Dao
interface SubtitleDao {

    @Query("SELECT language FROM subtitle_language WHERE contentKey = :contentKey LIMIT 1")
    suspend fun language(contentKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLanguage(entity: SubtitleLanguageEntity)

    @Query("SELECT offsetMs FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey LIMIT 1")
    suspend fun offset(contentKey: String, subtitleKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOffset(entity: SubtitleOffsetEntity)

    @Query("DELETE FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey")
    suspend fun clearOffset(contentKey: String, subtitleKey: String)

    /** Tablo sınırsız büyümesin: en yeni [keep] kaydırma kalır. */
    @Query(
        "DELETE FROM subtitle_offset WHERE rowid NOT IN " +
            "(SELECT rowid FROM subtitle_offset ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun pruneOffsets(keep: Int)

    /** Tablo sınırsız büyümesin: en yeni [keep] dil tercihi kalır. */
    @Query(
        "DELETE FROM subtitle_language WHERE rowid NOT IN " +
            "(SELECT rowid FROM subtitle_language ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun pruneLanguages(keep: Int)

    @Query("DELETE FROM subtitle_offset")
    suspend fun clearOffsets()

    @Query("DELETE FROM subtitle_language")
    suspend fun clearLanguages()

    @androidx.room.Transaction
    suspend fun clearAll() {
        clearOffsets()
        clearLanguages()
    }

    suspend fun deleteAll() {
        clearAll()
    }
}

@Dao
interface DownloadDao {

    /** Kütüphane > İndirilenler: yeni eklenen üstte. */
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

    /** Satır silinmişse (iptal) hiçbir şey yapmaz. */
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

    @Query("UPDATE favorites SET userId = :userId, profileId = :profileId WHERE userId = 'legacy-user'")
    suspend fun adoptLegacy(userId: String, profileId: String)

    @Query("SELECT * FROM favorites WHERE userId = :userId AND profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    ): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE userId = :userId AND profileId = :profileId ORDER BY addedAt DESC")
    suspend fun all(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    ): List<FavoriteEntity>

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE userId = :userId AND profileId = :profileId AND tmdbId = :tmdbId AND type = :type")
    fun observeIsFavorite(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        tmdbId: Int,
        type: MediaType,
    ): Flow<Boolean>

    fun observeIsFavorite(tmdbId: Int, type: MediaType): Flow<Boolean> =
        observeIsFavorite("legacy-user", "legacy-profile", tmdbId, type)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND profileId = :profileId AND tmdbId = :tmdbId AND type = :type")
    suspend fun remove(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
        tmdbId: Int,
        type: MediaType,
    )

    suspend fun remove(tmdbId: Int, type: MediaType) =
        remove("legacy-user", "legacy-profile", tmdbId, type)

    @Query("DELETE FROM favorites WHERE userId = :userId AND profileId = :profileId")
    suspend fun clear(
        userId: String = "legacy-user",
        profileId: String = "legacy-profile",
    )

    suspend fun clear() = clear("legacy-user", "legacy-profile")
}
