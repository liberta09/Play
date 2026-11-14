package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FanGeometryTest {
    private val eps = 1e-4f

    @Test fun centreIsUprightFullSizeAndOnTop() {
        val c = FanGeometry.slot(0f, 10)
        assertEquals(0f, c.rotation, eps); assertEquals(1f, c.scale, eps)
        assertEquals(0f, c.x, eps); assertEquals(0f, c.y, eps); assertEquals(1f, c.alpha, eps)
        assertTrue(c.z > FanGeometry.slot(1f, 10).z)
    }

    @Test fun sideSlotsFollowFanTableSymmetrically() {
        val expected = listOf(Triple(7f, 0.9346f, 11f), Triple(14f, 0.8498f, 22f), Triple(21f, 0.7756f, 30f))
        expected.forEachIndexed { i, (rot, scale, x) ->
            val right = FanGeometry.slot((i + 1).toFloat(), 10)
            val left = FanGeometry.slot(-(i + 1).toFloat(), 10)
            assertEquals(rot, right.rotation, eps); assertEquals(-rot, left.rotation, eps)
            assertEquals(scale, right.scale, eps); assertEquals(scale, left.scale, eps)
            assertEquals(x, right.x, eps); assertEquals(-x, left.x, eps)
            assertEquals(right.y, left.y, eps); assertTrue(right.y > 0f)
            assertEquals(1f, right.alpha, eps)
        }
        assertTrue(FanGeometry.slot(1f, 10).z > FanGeometry.slot(2f, 10).z)
        assertTrue(FanGeometry.slot(2f, 10).z > FanGeometry.slot(3f, 10).z)
    }

    @Test fun betweenSlotsInterpolatesAndBeyondThreeFadesOut() {
        val half = FanGeometry.slot(0.5f, 10)
        assertEquals(3.5f, half.rotation, eps); assertEquals(5.5f, half.x, eps)
        assertEquals(0f, FanGeometry.slot(3.5f, 10).alpha, eps)
        assertEquals(21f, FanGeometry.slot(3.4f, 10).rotation, eps)
        // Az kartta karşı kenara atlayan kart görünmez (5 kart: ±2.5).
        assertEquals(0f, FanGeometry.slot(2.5f, 5).alpha, eps)
        assertEquals(1f, FanGeometry.slot(2f, 5).alpha, eps)
    }

    @Test fun offsetWrapsAroundTheRing() {
        assertEquals(0f, FanGeometry.offset(4, 4f, 10), eps)
        assertEquals(-1f, FanGeometry.offset(9, 0f, 10), eps)
        assertEquals(1f, FanGeometry.offset(0, 9f, 10), eps)
        assertEquals(1f, FanGeometry.offset(0, -1f, 10), eps)
        assertEquals(-0.25f, FanGeometry.offset(2, 12.25f, 10), eps)
        assertEquals(2, FanGeometry.index(-8f, 10))
    }

    @Test fun swipeMovesExactlyOneCardButJitterStays() {
        assertEquals(3f, FanGeometry.settleTarget(3f, 3.1f), eps)
        assertEquals(4f, FanGeometry.settleTarget(3f, 3.3f), eps)
        assertEquals(2f, FanGeometry.settleTarget(3f, 2.8f), eps)
        assertEquals(4f, FanGeometry.settleTarget(3f, 4.6f), eps)
        assertEquals(2f, FanGeometry.settleTarget(3f, 0.9f), eps)
    }

    @Test fun onlyShortizleMatches() {
        assertTrue(isShortizleProvider("Shortizle"))
        assertTrue(isShortizleProvider("Shortİzle"))
        assertFalse(isShortizleProvider("LiderShort"))
        assertFalse(isShortizleProvider("DramaVakti"))
    }
}
