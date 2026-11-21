package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFlowGeometryTest {
    private val g = CoverFlowGeometry
    private val eps = 1e-4f

    @Test fun centreIsFlatAndSidesFaceTheCentre() {
        val c = g.pose(0f, 10)
        assertEquals(0f, c.rotation, eps)
        assertEquals(1f, c.scale, eps)
        val r = g.pose(1f, 10)
        val l = g.pose(-1f, 10)
        assertEquals(g.ROT, r.rotation, eps)
        assertEquals(-g.ROT, l.rotation, eps)
        assertEquals(g.X1, r.x, eps)
        assertEquals(-g.X1, l.x, eps)
        assertEquals(g.SIDE_SCALE, r.scale, eps)
        // Sonrakiler sık dizili, sırayla.
        assertEquals(g.X1 + g.GAP, g.pose(2f, 10).x, eps)
    }

    @Test fun poseIsContinuousAndFarCoversFade() {
        var last = g.pose(0f, 10)
        for (s in 1..60) {
            val p = g.pose(s / 20f, 10)
            assertTrue(p.x >= last.x - eps)
            assertTrue(p.x - last.x < 0.05f)
            last = p
        }
        assertEquals(0f, g.pose(3.6f, 10).alpha, eps)
        // 3 kartta karşı kenara atlayan kart görünmez.
        assertEquals(0f, g.pose(1.5f, 3).alpha, eps)
    }

    @Test fun flickAndTap() {
        assertEquals(1f, g.settle(0f, 0.4f, 0f), eps)
        assertEquals(0f, g.settle(0f, 0.1f, 0f), eps)
        assertEquals(4f, g.settle(0f, 1f, 20f), eps)
        assertEquals(-3f, g.settle(0f, -0.6f, -8f), eps)
        assertEquals(0, g.tapStep(0.1f))
        assertEquals(1, g.tapStep(0.3f))
        assertEquals(-1, g.tapStep(-0.3f))
        assertEquals(2, g.tapStep(0.43f))
    }
}
