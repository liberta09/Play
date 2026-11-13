package com.kaan.play.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvScreenRulesTest {

    @Test
    fun testCalculateGridColumns() {
        assertEquals(3, TvScreenRules.calculateGridColumns(360))
        assertEquals(3, TvScreenRules.calculateGridColumns(411))
        assertEquals(4, TvScreenRules.calculateGridColumns(600))
        assertEquals(6, TvScreenRules.calculateGridColumns(840))
    }

    @Test
    fun calculateGridColumns_largeFontWidensTiles() {
        assertEquals(8, TvScreenRules.calculateGridColumns(1067, 1f))
        assertEquals(6, TvScreenRules.calculateGridColumns(1067, 1.3f))
        assertEquals(4, TvScreenRules.calculateGridColumns(1067, 2f))
        assertEquals(3, TvScreenRules.calculateGridColumns(360, 2f))
        assertEquals(8, TvScreenRules.calculateGridColumns(1067, 0.85f))
    }

    @Test
    fun testShowSourceRow() {
        // User catalogs > 0
        assertTrue(TvScreenRules.showSourceRow(1, 0, 0))

        // 0 user catalogs, but multiple sources
        assertTrue(TvScreenRules.showSourceRow(0, 1, 1))

        // 0 user catalogs, only 1 source
        assertFalse(TvScreenRules.showSourceRow(0, 1, 0))
        assertFalse(TvScreenRules.showSourceRow(0, 0, 1))

        // 0 user catalogs, 0 sources
        assertFalse(TvScreenRules.showSourceRow(0, 0, 0))
    }

    @Test
    fun testShouldShowSourceChip() {
        // Sayısı 0 olan kaynak çipi gizlenir
        assertFalse(TvScreenRules.shouldShowSourceChip(0))
        assertTrue(TvScreenRules.shouldShowSourceChip(1))
        assertTrue(TvScreenRules.shouldShowSourceChip(129))
    }

    @Test
    fun channelActions_normalList_hasFavoriteAndAddOnly() {
        val actions = TvScreenRules.channelActions(isCustomListView = false, canReorder = false, index = 2, lastIndex = 9)
        assertEquals(
            listOf(TvScreenRules.ChannelAction.ToggleFavorite, TvScreenRules.ChannelAction.AddToList),
            actions,
        )
    }

    @Test
    fun channelActions_manualOrder_middleChannelCanMoveBothWays() {
        val actions = TvScreenRules.channelActions(isCustomListView = false, canReorder = true, index = 3, lastIndex = 9)
        assertTrue(TvScreenRules.ChannelAction.MoveUp in actions)
        assertTrue(TvScreenRules.ChannelAction.MoveDown in actions)
        assertFalse(TvScreenRules.ChannelAction.RemoveFromList in actions)
    }

    @Test
    fun channelActions_firstAndLastChannelsCannotMoveOutOfRange() {
        val first = TvScreenRules.channelActions(isCustomListView = false, canReorder = true, index = 0, lastIndex = 9)
        assertFalse(TvScreenRules.ChannelAction.MoveUp in first)
        assertTrue(TvScreenRules.ChannelAction.MoveDown in first)
        val last = TvScreenRules.channelActions(isCustomListView = false, canReorder = true, index = 9, lastIndex = 9)
        assertTrue(TvScreenRules.ChannelAction.MoveUp in last)
        assertFalse(TvScreenRules.ChannelAction.MoveDown in last)
    }

    @Test
    fun channelActions_customListAddsRemove_andMovingNeedsNoSearch() {
        val actions = TvScreenRules.channelActions(isCustomListView = true, canReorder = true, index = 1, lastIndex = 4)
        assertTrue(TvScreenRules.ChannelAction.RemoveFromList in actions)
        assertTrue(TvScreenRules.ChannelAction.MoveUp in actions)
        val searching = TvScreenRules.channelActions(isCustomListView = true, canReorder = false, index = 1, lastIndex = 4)
        assertTrue(TvScreenRules.ChannelAction.RemoveFromList in searching)
        assertFalse(TvScreenRules.ChannelAction.MoveUp in searching)
    }

    @Test
    fun channelActions_unknownIndex_neverOffersMove() {
        val actions = TvScreenRules.channelActions(isCustomListView = false, canReorder = true, index = -1, lastIndex = 9)
        assertFalse(TvScreenRules.ChannelAction.MoveUp in actions)
        assertFalse(TvScreenRules.ChannelAction.MoveDown in actions)
    }
}
