package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightGeometryTest {
    private val eps = 1e-4f

    @Test fun rowPlacementIsSymmetric() {
        assertEquals(0f, SpotlightGeometry.x(0f), eps)
        assertEquals(SpotlightGeometry.STEP, SpotlightGeometry.x(1f), eps)
        assertEquals(-SpotlightGeometry.x(1.7f), SpotlightGeometry.x(-1.7f), eps)
        assertTrue(SpotlightGeometry.x(2f) - SpotlightGeometry.x(1f) < SpotlightGeometry.STEP)
        assertEquals(1f, SpotlightGeometry.scale(0f), eps)
        assertEquals(SpotlightGeometry.SIDE_SCALE, SpotlightGeometry.scale(-1f), eps)
    }

    @Test fun onlyThePosterUnderTheBeamIsLit() {
        assertEquals(1f, SpotlightGeometry.lit(0f, 0f), eps)
        assertEquals(0f, SpotlightGeometry.lit(1f, 0f), eps)
        // Işık bir sonrakine kayınca o aydınlanır, ortadaki söner.
        assertEquals(1f, SpotlightGeometry.lit(1f, 1f), eps)
        assertEquals(0f, SpotlightGeometry.lit(0f, 1f), eps)
        val mid = SpotlightGeometry.lit(0f, 0.3f)
        assertTrue(mid > 0f && mid < 1f)
    }

    @Test fun lampTurnsTowardTheBeam() {
        assertEquals(0f, SpotlightGeometry.lampAngle(0f), eps)
        assertTrue(SpotlightGeometry.lampAngle(SpotlightGeometry.STEP) > 0f)
        assertEquals(-SpotlightGeometry.lampAngle(80f), SpotlightGeometry.lampAngle(-80f), eps)
    }
}
