package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGeometryTest {
    private val g = BookshelfGeometry
    private val eps = 1e-4f

    @Test fun spinesFitTheShelfAndAreCentred() {
        for (count in 3..10) {
            val left = g.spineLeft(0, count)
            val right = g.spineLeft(count - 1, count) + g.spineWidth(count)
            assertTrue("sol pay", left >= g.SIDE - eps)
            assertTrue("sağ pay", right <= 1f - g.SIDE + eps)
            assertEquals("ortalı", 0.5f, (left + right) / 2f, eps)
        }
        assertEquals(g.SPINE_MAX_W, g.spineWidth(3), eps)
    }

    @Test fun tapOnShelfFindsTheSpine() {
        val count = 8
        for (i in 0 until count) {
            val mid = g.spineLeft(i, count) + g.spineWidth(count) / 2f
            assertEquals(i, g.slotAt(mid, count))
        }
        assertNull(g.slotAt(0.01f, count))
        assertNull(g.slotAt(0.99f, count))
    }

    @Test fun pulledBookStartsAsTheSpineAndEndsAsTheCover() {
        val count = 6
        val index = 2
        val start = g.pull(0f, index, count)
        // Raftayken: sırt bize dönük, menteşe cildin sağ kenarında, boy cilt boyu.
        assertEquals(-90f, start.turn, eps)
        assertEquals(g.spineLeft(index, count) + g.spineWidth(count), start.hingeX, eps)
        assertEquals(g.SHELF_Y - g.spineHeight(index), start.top, eps)
        assertEquals(g.spineHeight(index), g.COVER_H * start.scale, eps)
        // Tam boy sırt genişliği ölçeklenince raftaki sırta eşit.
        assertEquals(g.spineWidth(count), g.thickness(index, count) * start.scale, eps)
        val end = g.pull(1f, index, count)
        assertEquals(0f, end.turn, eps)
        assertEquals(1f, end.scale, eps)
        assertEquals(0.5f - g.COVER_W / 2f, end.hingeX, eps)
        assertEquals(g.COVER_TOP, end.top, eps)
    }

    @Test fun pullIsMonotonicAndCoverStaysInScene() {
        var last = -91f
        for (s in 0..20) {
            val p = g.pull(s / 20f, 4, 10)
            assertTrue(p.turn >= last - eps)
            last = p.turn
            assertTrue(p.top >= 0f)
        }
        assertTrue(g.COVER_TOP + g.COVER_H < g.SHELF_Y)
        assertEquals(0, g.closeMs(0f))
        assertEquals(0, g.openMs(1f))
    }

    @Test fun edgeOnFacesAreNotDrawn() {
        assertEquals(0f, g.faceAlpha(90f), eps)
        assertEquals(0f, g.faceAlpha(-89f), eps)
        assertEquals(1f, g.faceAlpha(45f), eps)
        assertEquals(1f, g.faceAlpha(0f), eps)
    }
}
