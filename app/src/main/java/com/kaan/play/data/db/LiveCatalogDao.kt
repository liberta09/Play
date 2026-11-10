package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveCatalogDao {

    @Query("SELECT * FROM live_catalog ORDER BY createdAt ASC, id ASC")
    fun observeCatalogs(): Flow<List<LiveCatalogEntity>>

    @Query("SELECT * FROM live_catalog_channel ORDER BY catalogId ASC, sortIndex ASC, channelKey ASC")
    fun observeAllChannels(): Flow<List<LiveCatalogChannelEntity>>

    @Query("SELECT * FROM live_catalog_channel WHERE catalogId = :catalogId ORDER BY sortIndex ASC, channelKey ASC")
    suspend fun channelsForCatalog(catalogId: Long): List<LiveCatalogChannelEntity>

    @Query("SELECT MAX(sortIndex) FROM live_catalog_channel WHERE catalogId = :catalogId")
    suspend fun maxSortIndex(catalogId: Long): Int?

    @Query("SELECT * FROM live_catalog_channel WHERE catalogId = :catalogId AND channelKey = :channelKey LIMIT 1")
    suspend fun channelInCatalog(catalogId: Long, channelKey: String): LiveCatalogChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(entity: LiveCatalogEntity): Long

    @Query("UPDATE live_catalog SET name = :name WHERE id = :catalogId")
    suspend fun renameCatalog(catalogId: Long, name: String)

    @Query("DELETE FROM live_catalog WHERE id = :catalogId")
    suspend fun deleteCatalog(catalogId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(entity: LiveCatalogChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(entities: List<LiveCatalogChannelEntity>)

    @Query("DELETE FROM live_catalog_channel WHERE catalogId = :catalogId AND channelKey = :channelKey")
    suspend fun removeChannel(catalogId: Long, channelKey: String)

    @Transaction
    suspend fun addChannelAtEnd(
        catalogId: Long,
        channelKey: String,
        title: String,
        logoUrl: String?,
        routesJson: String,
    ) {
        val existing = channelInCatalog(catalogId, channelKey)
        val sortIdx = existing?.sortIndex ?: ((maxSortIndex(catalogId) ?: -1) + 1)
        upsertChannel(
            LiveCatalogChannelEntity(
                catalogId = catalogId,
                channelKey = channelKey,
                sortIndex = sortIdx,
                title = title,
                logoUrl = logoUrl,
                routesJson = routesJson,
            ),
        )
    }

    @Transaction
    suspend fun createCatalogWithChannel(
        name: String,
        createdAt: Long,
        channelKey: String,
        title: String,
        logoUrl: String?,
        routesJson: String,
    ): Long {
        val catalogId = insertCatalog(LiveCatalogEntity(name = name, createdAt = createdAt))
        if (catalogId > 0) {
            upsertChannel(
                LiveCatalogChannelEntity(
                    catalogId = catalogId,
                    channelKey = channelKey,
                    sortIndex = 0,
                    title = title,
                    logoUrl = logoUrl,
                    routesJson = routesJson,
                ),
            )
        }
        return catalogId
    }
}
