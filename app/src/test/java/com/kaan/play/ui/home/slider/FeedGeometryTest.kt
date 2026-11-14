package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedGeometryTest {
    private val eps = 1e-4f

    @Test fun centreCardIsFullSizeAndClear() {
        assertEquals(0f, FeedGeometry.shift(0f), eps)
        assertEquals(1f, FeedGeometry.scale(0f), eps)
        assertEquals(0f, FeedGeometry.dim(0f), eps)
    }

    @Test fun nextCardPeeksBelowPreviousLeavesAbove() {
        // Sıradaki afişin üst kenarı sahnenin altında PEEK kadar görünür.
        val nextTop = FeedGeometry.TOP + FeedGeometry.shift(1f)
        assertEquals(FeedGeometry.STAGE_H - FeedGeometry.PEEK, nextTop, eps)
        // Yukarı çıkan afiş tamamen sahne dışına çıkar.
        assertTrue(FeedGeometry.TOP + FeedGeometry.shift(-1f) + FeedGeometry.CARD_H <= 0f)
        assertEquals(1f - FeedGeometry.SHRINK, FeedGeometry.scale(1f), eps)
        assertEquals(FeedGeometry.scale(1f), FeedGeometry.scale(-3f), eps)
        assertEquals(FeedGeometry.DIM, FeedGeometry.dim(-1f), eps)
    }

    @Test fun onlyThreeCardsAreDrawn() {
        assertTrue(FeedGeometry.visible(-0.5f))
        assertTrue(FeedGeometry.visible(0f))
        assertTrue(FeedGeometry.visible(1.5f))
        assertFalse(FeedGeometry.visible(-1f))
        assertFalse(FeedGeometry.visible(2f))
    }
}
