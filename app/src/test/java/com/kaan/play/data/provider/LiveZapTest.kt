package com.kaan.play.data.provider

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveZapTest {

    @After
    fun tearDown() {
        LiveZapListHolder.clear()
    }

    private fun route(id: String = "ch1") = LiveSourceRoute(
        pluginId = "nuvio_addon:1",
        addonType = "tv",
        contentId = id,
    )

    private fun ch(key: String, available: Boolean = true, routes: List<LiveSourceRoute> = listOf(route(key))) =
        LiveZapChannel(
            channelKey = key,
            title = key.uppercase(),
            routes = routes,
            isAvailable = available,
        )

    @Test
    fun nextAvailableIndexWrapsForwardAndBackwardAndSkipsUnavailable() {
        val channels = listOf(
            ch("c0", available = true),
            ch("c1", available = false),
            ch("c2", available = true, routes = emptyList()),
            ch("c3", available = true),
            ch("c4", available = false),
        )

        // c0 ileri -> c1 (uygunsuz), c2 (rotasız) atlanır -> c3
        assertEquals(3, LiveZapRules.nextAvailableIndex(channels, currentIndex = 0, direction = +1))
        // c3 ileri -> c4 (uygunsuz) atlanır, başa sarar -> c0
        assertEquals(0, LiveZapRules.nextAvailableIndex(channels, currentIndex = 3, direction = +1))
        // c0 geri -> sondan sarar -> c4 atlanır -> c3
        assertEquals(3, LiveZapRules.nextAvailableIndex(channels, currentIndex = 0, direction = -1))
        // c3 geri -> c2, c1 atlanır -> c0
        assertEquals(0, LiveZapRules.nextAvailableIndex(channels, currentIndex = 3, direction = -1))
    }

    @Test
    fun nextAvailableIndexReturnsNullForSingleEmptyZeroDirectionOrAllUnavailable() {
        val single = listOf(ch("only"))
        assertNull(LiveZapRules.nextAvailableIndex(single, currentIndex = 0, direction = +1))
        assertNull(LiveZapRules.nextAvailableIndex(emptyList(), currentIndex = 0, direction = +1))
        assertNull(LiveZapRules.nextAvailableIndex(listOf(ch("a"), ch("b")), currentIndex = 0, direction = 0))

        val allUnavailable = listOf(
            ch("a", available = true),
            ch("b", available = false),
            ch("c", available = true, routes = emptyList()),
        )
        assertNull(LiveZapRules.nextAvailableIndex(allUnavailable, currentIndex = 0, direction = +1))
        assertNull(LiveZapRules.nextAvailableIndex(allUnavailable, currentIndex = 0, direction = -1))
    }

    @Test
    fun sourceForResolvesCatalogVisibleListAndUnavailable() {
        assertEquals(LiveZapListSource.Catalog, LiveZapRules.sourceFor(catalogId = "live-tr", holderHasList = true))
        assertEquals(LiveZapListSource.Catalog, LiveZapRules.sourceFor(catalogId = "live-tr", holderHasList = false))
        assertEquals(LiveZapListSource.VisibleList, LiveZapRules.sourceFor(catalogId = null, holderHasList = true))
        assertEquals(LiveZapListSource.VisibleList, LiveZapRules.sourceFor(catalogId = " ", holderHasList = true))
        assertEquals(LiveZapListSource.Unavailable, LiveZapRules.sourceFor(catalogId = null, holderHasList = false))
    }

    @Test
    fun sequencerAcceptsOnlyLatestTicket() {
        val seq = LiveZapRequestSequencer()
        val t1 = seq.issue(1)
        assertTrue(seq.isLatest(t1))

        val t2 = seq.issue(2)
        val t3 = seq.issue(3)

        assertFalse(seq.isLatest(t1))
        assertFalse(seq.isLatest(t2))
        assertTrue(seq.isLatest(t3))
        assertEquals(3, t3.requestedIndex)
    }

    @Test
    fun holderEvictsOldestEntryWhenExceedingCapacity12() {
        val keys = (1..13).map { idx ->
            LiveZapListHolder.put(listOf(ch("ch$idx")))
        }

        // İlk eklenen (1.) 13. eklemede düşmeli, 2..13 yaşamalı
        assertNull(LiveZapListHolder.get(keys.first()))
        for (i in 1 until keys.size) {
            val loaded = LiveZapListHolder.get(keys[i])
            assertNotNull("Anahtar #$i hala bellekte olmalı", loaded)
            assertEquals("ch${i + 1}", loaded!!.single().channelKey)
        }

        LiveZapListHolder.remove(keys[1])
        assertNull(LiveZapListHolder.get(keys[1]))
    }
}
