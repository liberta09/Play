package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRoutePlannerTest {

    private fun route(pluginId: String, contentId: String = "ch1", dataUrl: String? = null) =
        LiveSourceRoute(pluginId = pluginId, addonType = "tv", contentId = contentId, dataUrl = dataUrl)

    @Test
    fun nextUntriedRouteIndexCyclesAndSkipsFailedRoutesInSession() {
        val routes = listOf(
            route("nuvio_addon:1", "c1"),
            route("nuvio_addon:2", "c2"),
            route("cs:3", "https://example.com/live", "https://example.com/live"),
        )

        val failed = mutableSetOf<String>()
        // Hiç başarısız yokken elle veya otomatik 0 -> 1 -> 2 -> 0 döner
        assertEquals(1, LiveRoutePlanner.nextUntriedRouteIndex(routes, currentIndex = 0, failedKeys = failed))
        assertEquals(2, LiveRoutePlanner.nextUntriedRouteIndex(routes, currentIndex = 1, failedKeys = failed))
        assertEquals(0, LiveRoutePlanner.nextUntriedRouteIndex(routes, currentIndex = 2, failedKeys = failed))

        // 0 ve 1 oturumda başarısız işaretlenirse 2'ye atlanır; yalnızca 2 kalmışsa başka rota yoktur (null)
        failed += LiveRoutePlanner.routeKey(routes[0])
        assertEquals(2, LiveRoutePlanner.nextUntriedRouteIndex(routes, currentIndex = 1, failedKeys = failed))

        failed += LiveRoutePlanner.routeKey(routes[1])
        assertNull(LiveRoutePlanner.nextUntriedRouteIndex(routes, currentIndex = 2, failedKeys = failed))
    }

    @Test
    fun nextUntriedRouteIndexReturnsNullForSingleOrEmptyRoutes() {
        assertNull(LiveRoutePlanner.nextUntriedRouteIndex(emptyList(), currentIndex = 0, failedKeys = emptySet()))
        assertNull(LiveRoutePlanner.nextUntriedRouteIndex(listOf(route("nuvio_addon:1")), currentIndex = 0, failedKeys = emptySet()))
    }

    @Test
    fun routeFromRequestHandlesNuvioCloudStreamAndRejectsVod() {
        val nuvio = StreamRequest(
            tmdbId = 0,
            imdbId = null,
            type = MediaType.Tv,
            pluginId = "nuvio_addon:7",
            addonType = "TV",
            addonContentId = "live:trt1",
        )
        assertEquals(
            LiveSourceRoute("nuvio_addon:7", "tv", "live:trt1", dataUrl = null),
            LiveRoutePlanner.routeFromRequest(nuvio),
        )

        val cs = StreamRequest(
            tmdbId = 0,
            imdbId = null,
            type = MediaType.Tv,
            pluginId = "cs:5",
            addonType = "channel",
            addonContentId = "https://cs.example/1",
            dataUrl = "https://cs.example/1",
        )
        assertEquals(
            LiveSourceRoute("cs:5", "channel", "https://cs.example/1", dataUrl = "https://cs.example/1"),
            LiveRoutePlanner.routeFromRequest(cs),
        )

        val movie = nuvio.copy(tmdbId = 550, type = MediaType.Movie)
        assertNull(LiveRoutePlanner.routeFromRequest(movie))
    }
}
