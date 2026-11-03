package com.kaan.play.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterPrefetchRulesTest {

    @Test
    fun horizontalWindow_emptyRow_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 0,
            itemCount = 0,
            windowSize = 4,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun horizontalWindow_singleCardRow_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 0,
            itemCount = 1,
            windowSize = 4,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun horizontalWindow_negativeIndex_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = -1,
            itemCount = 10,
            windowSize = 4,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun horizontalWindow_outOfBoundsIndex_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 10,
            itemCount = 10,
            windowSize = 4,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun horizontalWindow_zeroWindowSize_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 2,
            itemCount = 10,
            windowSize = 0,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun horizontalWindow_startOfRow_returnsNextNeighborsUpToWindow() {
        // 10 kartlık satırın 0. indeksinde: sağa doğru 4 kart (1, 2, 3, 4)
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 0,
            itemCount = 10,
            windowSize = 4,
        )
        assertEquals(listOf(1, 2, 3, 4), indices)
    }

    @Test
    fun horizontalWindow_startOfRow_shortRow_returnsAvailableNeighbors() {
        // 3 kartlık satırın 0. indeksinde: sağa doğru sadece 1 ve 2
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 0,
            itemCount = 3,
            windowSize = 4,
        )
        assertEquals(listOf(1, 2), indices)
    }

    @Test
    fun horizontalWindow_endOfRow_returnsPreviousNeighborsUpToWindow() {
        // 10 kartlık satırın 9. indeksinde: sola doğru 4 kart (8, 7, 6, 5)
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 9,
            itemCount = 10,
            windowSize = 4,
        )
        assertEquals(listOf(8, 7, 6, 5), indices)
    }

    @Test
    fun horizontalWindow_middleOfRow_interleavesClosestNeighbors() {
        // 10 kartlık satırın 5. indeksinde:
        // step 1: 6, 4
        // step 2: 7, 3
        // step 3: 8, 2
        // step 4: 9, 1
        val indices = PosterPrefetchRules.calculateHorizontalIndices(
            currentIndex = 5,
            itemCount = 10,
            windowSize = 4,
        )
        assertEquals(listOf(6, 4, 7, 3, 8, 2, 9, 1), indices)
    }

    @Test
    fun verticalWindow_emptyNextRow_returnsEmpty() {
        val indices = PosterPrefetchRules.calculateVerticalIndices(
            nextRowCount = 0,
            windowSize = 6,
        )
        assertTrue(indices.isEmpty())
    }

    @Test
    fun verticalWindow_fewerThanWindowCards_returnsAllAvailable() {
        // Bir sonraki satırda 3 kart varsa: 0, 1, 2
        val indices = PosterPrefetchRules.calculateVerticalIndices(
            nextRowCount = 3,
            windowSize = 6,
        )
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun verticalWindow_manyCards_returnsFirstSix() {
        // Bir sonraki satırda 20 kart varsa: ilk 6'sı (0..5)
        val indices = PosterPrefetchRules.calculateVerticalIndices(
            nextRowCount = 20,
            windowSize = 6,
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), indices)
    }

    @Test
    fun nextRowIndex_correctlyCalculatesOrReturnsNullAtEnd() {
        // 3 satırlı yapıda:
        // 0 -> 1
        // 1 -> 2
        // 2 -> null (son satır)
        assertEquals(1, PosterPrefetchRules.nextRowIndex(0, 3))
        assertEquals(2, PosterPrefetchRules.nextRowIndex(1, 3))
        assertNull(PosterPrefetchRules.nextRowIndex(2, 3))
        assertNull(PosterPrefetchRules.nextRowIndex(-1, 3))
    }
}
