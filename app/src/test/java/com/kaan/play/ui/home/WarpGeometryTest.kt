package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WarpGeometryTest {
    private val eps = 1e-3f
    private fun pose(q: Float) = FloatArray(3).also { WarpGeometry.pose(q, it) }

    @Test fun frontCardFacesViewerAtTheFrontPlane() {
        val p = pose(0f)
        assertEquals(0f, p[0], eps); assertEquals(0f, p[1], eps); assertEquals(0f, p[2], eps)
        assertEquals(1f, WarpGeometry.scaleAt(p[1]), eps)
    }

    @Test fun neighboursHingeAwayThenTurnBehind() {
        assertEquals(WarpGeometry.YAW_A, pose(WarpGeometry.Q_A)[2], eps)
        val rest = pose(1f)
        assertTrue(rest[2] > 179f)
        // Komşu kart halkanın arkasında, ama kenarda kısmen görünür (sahne yarı genişliği 320).
        val screen = rest[0] * WarpGeometry.scaleAt(rest[1])
        assertTrue("screen=$screen", screen in 200f..300f)
        var lastYaw = -1f
        var lastZ = -1f
        var q = 0f
        while (q <= 1.04f) {
            val p = pose(q)
            assertTrue("yaw q=$q", p[2] >= lastYaw - eps)
            assertTrue("z q=$q", p[1] >= lastZ - 0.5f)
            lastYaw = p[2]; lastZ = p[1]
            q += 0.01f
        }
    }

    @Test fun ringIsSymmetricAndContinuous() {
        for (q in listOf(0.2f, 0.46f, 0.6f, 0.9f, 1.5f)) {
            val r = pose(q)
            val l = pose(-q)
            assertEquals(-r[0], l[0], eps); assertEquals(r[1], l[1], eps); assertEquals(-r[2], l[2], eps)
        }
        for (edge in listOf(WarpGeometry.Q_A, 0.75f, 1.04f)) {
            val a = pose(edge - 1e-4f)
            val b = pose(edge + 1e-4f)
            for (k in 0..2) assertTrue("edge=$edge k=$k", abs(a[k] - b[k]) < 0.5f)
        }
    }

    @Test fun atMostTwoCardsEachSideAndFewCardsDoNotJump() {
        assertTrue(WarpGeometry.alpha(2f, 10) > 0f)
        assertEquals(0f, WarpGeometry.alpha(2.06f, 10), eps)
        assertEquals(1f, WarpGeometry.alpha(1f, 10), eps)
        assertEquals(0f, WarpGeometry.alpha(1.5f, 3), eps)
        assertEquals(1f, WarpGeometry.alpha(1f, 3), eps)
    }

    @Test fun spinWhipsFastThenCreeps() {
        assertEquals(0f, WarpGeometry.whip(0f), eps)
        assertEquals(1f, WarpGeometry.whip(1f), eps)
        assertTrue(WarpGeometry.whip(0.02f) < 0.06f)
        assertTrue(WarpGeometry.whip(0.12f) > 0.4f)
        assertTrue(WarpGeometry.whip(0.5f) > 0.9f)
        var last = 0f
        for (i in 1..100) {
            val v = WarpGeometry.whip(i / 100f)
            assertTrue(v >= last); last = v
        }
    }

    @Test fun dragSettlesWithMomentumAndTapsStep() {
        assertEquals(3f, WarpGeometry.settleTarget(3.2f, 0f), eps)
        assertEquals(4f, WarpGeometry.settleTarget(3.4f, 2f), eps)
        assertEquals(2f, WarpGeometry.settleTarget(3f, -5f), eps)
        assertEquals(1, WarpGeometry.tapStep(200f))
        assertEquals(-1, WarpGeometry.tapStep(-200f))
        assertEquals(0, WarpGeometry.tapStep(100f))
        assertTrue(WarpGeometry.edgeStretch(300f) > 1.1f)
        assertEquals(1f, WarpGeometry.edgeStretch(0f), eps)
    }

    @Test fun glowFavoursVividColoursAndBrightens() {
        val red = 0xFF801010.toInt()
        val grey = 0xFF404040.toInt()
        val c = WarpGeometry.glowColor(IntArray(64) { if (it < 16) red else grey })
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        assertTrue("r=$r g=$g", r > 200 && g < r / 2)
        assertEquals(WarpGeometry.DEFAULT_GLOW, WarpGeometry.glowColor(IntArray(64) { 0xFF000000.toInt() }))
    }

    @Test fun onlyDramaTrMatches() {
        assertTrue(isDramaTrProvider("DramaTR"))
        assertTrue(isDramaTrProvider("Drama TR"))
        assertFalse(isDramaTrProvider("TR"))
        assertFalse(isDramaTrProvider("DramaTRDizi"))
        assertFalse(isDramaTrProvider("DramaVakti"))
        assertFalse(isDramaTrProvider("Shortİzle"))
    }
}
