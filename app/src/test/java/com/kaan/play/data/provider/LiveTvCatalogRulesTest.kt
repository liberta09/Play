package com.kaan.play.data.provider

import com.kaan.play.data.db.LiveCatalogChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvCatalogRulesTest {

    private fun entity(key: String, sortIndex: Int) = LiveCatalogChannelEntity(
        catalogId = 1L,
        channelKey = key,
        sortIndex = sortIndex,
        title = key.uppercase(),
        logoUrl = null,
        routesJson = "[]",
    )

    @Test
    fun routeSerializationRoundTripsWithoutLoss() {
        val routes = listOf(
            LiveSourceRoute("nuvio_addon:5", "tv", "trt1", null),
            LiveSourceRoute("cs:12", "tv", "trt-1", "https://example.invalid/live/trt1"),
            LiveSourceRoute("nuvio_addon:5", "tv", "trt1", null),
        )
        val encoded = LiveTvCatalogRules.encodeRoutes(routes)
        val decoded = LiveTvCatalogRules.decodeRoutes(encoded)
        assertEquals(2, decoded.size)
        assertEquals(routes[0], decoded[0])
        assertEquals(routes[1], decoded[1])
        assertTrue(LiveTvCatalogRules.decodeRoutes("bad-json").isEmpty())
    }

    @Test
    fun reordersChannelsWithinBoundsAndNormalizesSortIndices() {
        val initial = listOf(entity("a", 0), entity("b", 5), entity("c", 9))

        assertNull(LiveTvCatalogRules.computeReordered(initial, "a", -1))
        assertNull(LiveTvCatalogRules.computeReordered(initial, "c", +1))
        assertNull(LiveTvCatalogRules.computeReordered(initial, "missing", +1))

        val movedDown = LiveTvCatalogRules.computeReordered(initial, "a", +1)!!
        assertEquals(listOf("b", "a", "c"), movedDown.map { it.channelKey })
        assertEquals(listOf(0, 1, 2), movedDown.map { it.sortIndex })

        val movedUp = LiveTvCatalogRules.computeReordered(movedDown, "c", -1)!!
        assertEquals(listOf("b", "c", "a"), movedUp.map { it.channelKey })
        assertEquals(listOf(0, 1, 2), movedUp.map { it.sortIndex })
    }

    @Test
    fun buildsZapChannelsAndResolvesCatalogZapSource() {
        val saved = listOf(
            LiveTvCatalogChannel(
                catalogId = 7L,
                channelKey = "trt1",
                sortIndex = 0,
                title = "TRT 1",
                logoUrl = null,
                routes = listOf(LiveSourceRoute("nuvio_addon:1", "tv", "trt1")),
            ),
            LiveTvCatalogChannel(
                catalogId = 7L,
                channelKey = "atv",
                sortIndex = 1,
                title = "ATV",
                logoUrl = null,
                routes = listOf(LiveSourceRoute("cs:2", "tv", "atv", "https://example.invalid/atv")),
            ),
        )
        val zap = LiveTvCatalogRules.toZapChannels(saved)
        assertEquals(listOf("trt1", "atv"), zap.map { it.channelKey })
        assertEquals(LiveZapListSource.Catalog, LiveZapRules.sourceFor("user_catalog:7", zap.isNotEmpty()))
    }
}
