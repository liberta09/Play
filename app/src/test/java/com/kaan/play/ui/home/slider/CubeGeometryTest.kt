package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeGeometryTest {
    private val eps = 1e-3f

    @Test fun onlyLeavingAndArrivingFacesShow() {
        assertTrue(CubeGeometry.visible(0f))
        assertTrue(CubeGeometry.visible(-0.6f) && CubeGeometry.visible(0.4f))
        assertFalse(CubeGeometry.visible(1f) || CubeGeometry.visible(-1.2f))
    }

    @Test fun facesShareTheEdgeAndTurnNinetyDegrees() {
        // Giden yüzün sağ kenarı ile gelen yüzün sol kenarı aynı yerde (yüz genişliği birimi).
        val f = 0.3f
        val leavingRightEdge = CubeGeometry.shift(-f) + CubeGeometry.pivotX(-f)
        val arrivingLeftEdge = CubeGeometry.shift(1f - f) + CubeGeometry.pivotX(1f - f)
        assertEquals(leavingRightEdge, arrivingLeftEdge, eps)
        assertEquals(90f, kotlin.math.abs(CubeGeometry.rotation(-f) - CubeGeometry.rotation(1f - f)), eps)
        assertEquals(0f, CubeGeometry.rotation(0f), eps)
    }

    @Test fun cubeRecedesMidTurnAndShades() {
        assertEquals(1f, CubeGeometry.recede(3f), eps)
        assertEquals(1f - CubeGeometry.DIP, CubeGeometry.recede(3.5f), eps)
        assertEquals(0f, CubeGeometry.shade(0f), eps)
        assertEquals(CubeGeometry.SHADE, CubeGeometry.shade(-1f), eps)
    }
}
