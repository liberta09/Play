package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveChannelDao {

    @Query("SELECT * FROM live_channel_activity")
    fun observeActivity(): Flow<List<LiveChannelActivityEntity>>

    @Query("SELECT * FROM live_channel_order ORDER BY sortIndex ASC")
    fun observeOrder(): Flow<List<LiveChannelOrderEntity>>

    @Query("SELECT * FROM live_channel_activity WHERE channelKey = :channelKey LIMIT 1")
    suspend fun activityFor(channelKey: String): LiveChannelActivityEntity?

    @Query("SELECT * FROM live_channel_order ORDER BY sortIndex ASC")
    suspend fun allOrders(): List<LiveChannelOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(entity: LiveChannelActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(entities: List<LiveChannelOrderEntity>)

    @Query("UPDATE live_channel_activity SET lastWatchedAt = 0")
    suspend fun clearWatchTimestamps()

    @Query("UPDATE live_channel_activity SET isFavorite = 0")
    suspend fun clearFavorites()

    @Transaction
    suspend fun markWatched(channelKey: String, watchedAt: Long) {
        val current = activityFor(channelKey)
        upsertActivity(
            LiveChannelActivityEntity(
                channelKey = channelKey,
                lastWatchedAt = watchedAt,
                isFavorite = current?.isFavorite ?: false,
            ),
        )
    }

    @Transaction
    suspend fun setFavorite(channelKey: String, favorite: Boolean) {
        val current = activityFor(channelKey)
        upsertActivity(
            LiveChannelActivityEntity(
                channelKey = channelKey,
                lastWatchedAt = current?.lastWatchedAt ?: 0L,
                isFavorite = favorite,
            ),
        )
    }

    @Transaction
    suspend fun replaceOrders(entities: List<LiveChannelOrderEntity>) {
        if (entities.isEmpty()) return
        upsertOrders(entities)
    }
}
