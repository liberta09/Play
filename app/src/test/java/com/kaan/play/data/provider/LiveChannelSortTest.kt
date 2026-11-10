package com.kaan.play.data.provider

import com.kaan.play.data.db.LiveChannelActivityEntity
import com.kaan.play.data.db.LiveChannelDao
import com.kaan.play.data.db.LiveChannelOrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChannelSortTest {

    private data class Channel(val key: String, val title: String)

    private val channels = listOf(
        Channel("trt1", "TRT 1"),
        Channel("kanald", "Kanal D"),
        Channel("atv", "ATV"),
        Channel("showtv", "Show TV"),
        Channel("canakkale", "Çanakkale TV"),
    )

    @Test
    fun sortModesOrderChannelsDeterministically() {
        val activity = mapOf(
            "trt1" to LiveChannelActivityEntity("trt1", lastWatchedAt = 100L, isFavorite = false),
            "showtv" to LiveChannelActivityEntity("showtv", lastWatchedAt = 500L, isFavorite = true),
            "atv" to LiveChannelActivityEntity("atv", lastWatchedAt = 0L, isFavorite = true),
        )
        val order = mapOf(
            "showtv" to 0,
            "atv" to 1,
            "kanald" to 2,
        )

        val asc = LiveChannelSort.sort(channels, LiveTvSortMode.TitleAscending, { it.key }, { it.title }, activity, order)
        assertEquals(listOf("atv", "canakkale", "kanald", "showtv", "trt1"), asc.map { it.key })

        val desc = LiveChannelSort.sort(channels, LiveTvSortMode.TitleDescending, { it.key }, { it.title }, activity, order)
        assertEquals(listOf("trt1", "showtv", "kanald", "canakkale", "atv"), desc.map { it.key })

        val recent = LiveChannelSort.sort(channels, LiveTvSortMode.Recent, { it.key }, { it.title }, activity, order)
        assertEquals(listOf("showtv", "trt1", "atv", "canakkale", "kanald"), recent.map { it.key })

        val favs = LiveChannelSort.sort(channels, LiveTvSortMode.FavoritesFirst, { it.key }, { it.title }, activity, order)
        assertEquals(listOf("atv", "showtv", "canakkale", "kanald", "trt1"), favs.map { it.key })

        val manual = LiveChannelSort.sort(channels, LiveTvSortMode.Manual, { it.key }, { it.title }, activity, order)
        assertEquals(listOf("showtv", "atv", "kanald", "canakkale", "trt1"), manual.map { it.key })
    }

    @Test
    fun computeMovedOrderMovesUpDownAndGuardsBounds() {
        val all = listOf("a", "b", "c", "d")
        val upFromStart = LiveChannelSort.computeMovedOrder(all, all, emptyMap(), "a", -1)
        assertNull(upFromStart)

        val downFromEnd = LiveChannelSort.computeMovedOrder(all, all, emptyMap(), "d", +1)
        assertNull(downFromEnd)

        val moveBUp = LiveChannelSort.computeMovedOrder(all, all, emptyMap(), "b", -1)!!
        assertEquals(listOf("b" to 0, "a" to 1, "c" to 2, "d" to 3), moveBUp.map { it.channelKey to it.sortIndex })

        val filteredVisible = listOf("b", "d")
        val existing = moveBUp.associate { it.channelKey to it.sortIndex }
        val moveBDownInFiltered = LiveChannelSort.computeMovedOrder(filteredVisible, listOf("b", "a", "c", "d"), existing, "b", +1)!!
        assertEquals(listOf("d" to 0, "a" to 1, "c" to 2, "b" to 3), moveBDownInFiltered.map { it.channelKey to it.sortIndex })
    }

    @Test
    fun repositoryTracksWatchTimestampFavoritesAndManualOrderWithoutLoss() = runBlocking {
        val fakeDao = FakeLiveChannelDao()
        var now = 10_000L
        val repo = LiveChannelRepository(fakeDao, clock = { now })

        repo.toggleFavorite("trt1")
        now = 20_000L
        repo.markWatched("trt1")

        val row = repo.activityByKey.first()["trt1"]!!
        assertTrue(row.isFavorite)
        assertEquals(20_000L, row.lastWatchedAt)

        repo.toggleFavorite("trt1")
        val afterUnfav = repo.activityByKey.first()["trt1"]!!
        assertFalse(afterUnfav.isFavorite)
        assertEquals(20_000L, afterUnfav.lastWatchedAt)

        repo.moveOrder(listOf("a", "b", "c"), listOf("a", "b", "c"), "c", -1)
        val orders = repo.orderByKey.first()
        assertEquals(mapOf("a" to 0, "c" to 1, "b" to 2), orders)

        repo.clearWatchHistory()
        assertEquals(0L, repo.activityByKey.first()["trt1"]!!.lastWatchedAt)
        repo.clearFavorites()
        assertFalse(repo.activityByKey.first()["trt1"]!!.isFavorite)
    }

    private class FakeLiveChannelDao : LiveChannelDao {
        private val activity = MutableStateFlow<Map<String, LiveChannelActivityEntity>>(emptyMap())
        private val orders = MutableStateFlow<Map<String, LiveChannelOrderEntity>>(emptyMap())

        override fun observeActivity(): Flow<List<LiveChannelActivityEntity>> = activity.map { it.values.toList() }

        override fun observeOrder(): Flow<List<LiveChannelOrderEntity>> =
            orders.map { it.values.sortedBy { row -> row.sortIndex } }

        override suspend fun activityFor(channelKey: String): LiveChannelActivityEntity? = activity.value[channelKey]

        override suspend fun allOrders(): List<LiveChannelOrderEntity> =
            orders.value.values.sortedBy { it.sortIndex }

        override suspend fun upsertActivity(entity: LiveChannelActivityEntity) {
            activity.value = activity.value + (entity.channelKey to entity)
        }

        override suspend fun upsertOrders(entities: List<LiveChannelOrderEntity>) {
            orders.value = orders.value + entities.associateBy { it.channelKey }
        }

        override suspend fun clearWatchTimestamps() {
            activity.value = activity.value.mapValues { (_, v) -> v.copy(lastWatchedAt = 0L) }
        }

        override suspend fun clearFavorites() {
            activity.value = activity.value.mapValues { (_, v) -> v.copy(isFavorite = false) }
        }
    }
}
