package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaGeometryTest {
    private val eps = 1e-4f

    @Test fun openCurtainIsGatheredAtTheTie() {
        val top = CinemaGeometry.innerEdge(0f, 0f)
        val tie = CinemaGeometry.innerEdge(CinemaGeometry.TIE_Y, 0f)
        val bottom = CinemaGeometry.innerEdge(1f, 0f)
        assertTrue(tie < top && tie < bottom)
        assertTrue(top < 0.25f) // açıkken afiş (ortadaki ~%42) görünür
    }

    @Test fun closedCurtainMeetsInTheMiddle() {
        for (y in listOf(0f, 0.3f, CinemaGeometry.TIE_Y, 1f)) {
            assertTrue(CinemaGeometry.innerEdge(y, 1f) >= 0.5f)
        }
        val half = CinemaGeometry.innerEdge(0.5f, 0.5f)
        assertTrue(half > CinemaGeometry.innerEdge(0.5f, 0f) && half < CinemaGeometry.innerEdge(0.5f, 1f))
    }

    @Test fun partialMovesTakeTheRemainingTime() {
        assertEquals(CinemaGeometry.CLOSE_MS, CinemaGeometry.closeMs(0f))
        assertEquals(0, CinemaGeometry.closeMs(1f))
        assertEquals(CinemaGeometry.OPEN_MS, CinemaGeometry.openMs(1f))
        assertEquals(0, CinemaGeometry.openMs(0f))
    }

    @Test fun dragClosesCurtainButNeverFully() {
        assertEquals(0f, CinemaGeometry.dragClose(0f), eps)
        assertTrue(CinemaGeometry.dragClose(-60f) > 0f)
        assertEquals(CinemaGeometry.dragClose(60f), CinemaGeometry.dragClose(-60f), eps)
        assertEquals(CinemaGeometry.DRAG_CLOSE, CinemaGeometry.dragClose(1000f), eps)
        assertEquals(1f, CinemaGeometry.posterScale(0f), eps)
        assertTrue(CinemaGeometry.posterScale(1f) < 1f)
    }
}
