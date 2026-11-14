package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDeckGeometryTest {
    private val g = CardDeckGeometry
    private val eps = 1e-4f

    @Test fun cardsBehindAreSmallerLowerAndTilted() {
        val top = g.rest(0, 0f)
        assertEquals(0f, top.rotation, eps)
        assertEquals(1f, top.scale, eps)
        var lastY = 0f
        for (d in 1..3) {
            val p = g.rest(d, d.toFloat())
            assertTrue(p.scale < 1f && p.y > lastY && p.rotation != 0f && p.alpha == 1f)
            lastY = p.y
        }
        assertEquals(0f, g.rest(0, g.SHOWN + 1f).alpha, eps)
    }

    @Test fun draggedCardFollowsAndLeans() {
        val right = g.thrown(0.2f)
        assertEquals(0.2f, right.x, eps)
        assertTrue(right.rotation > 0f)
        assertTrue(g.thrown(-0.2f).rotation < 0f)
        assertEquals(0f, g.rise(0f), eps)
        assertEquals(1f, g.rise(g.RISE), eps)
        // Fırlayan kart sahneden çıkar.
        assertTrue(g.FLY - g.CARD_W / 2f > 0.5f)
    }

    @Test fun releaseThrowsInTheSwipeDirection() {
        assertEquals(0, g.release(0.1f, 0f))
        assertEquals(1, g.release(0.3f, 0f))
        assertEquals(-1, g.release(-0.3f, 0f))
        assertEquals(-1, g.release(-0.05f, -1.5f)) // fiske
        assertEquals(0, g.release(0.1f, -1.2f)) // ters yöne zayıf fiske: geri döner
        assertEquals(2, g.depth(0, 1, 3))
        assertEquals(0, g.depth(4, 4, 6))
    }
}
