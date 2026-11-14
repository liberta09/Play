package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmStripGeometryTest {
    private val eps = 1e-3f

    @Test fun centreFrameGrowsSidesShrink() {
        assertEquals(1f, FilmStripGeometry.focus(0f), eps)
        assertEquals(0f, FilmStripGeometry.focus(1.2f), eps)
        assertEquals(1f + FilmStripGeometry.GROW, FilmStripGeometry.scale(0f), eps)
        assertEquals(1f - FilmStripGeometry.SHRINK, FilmStripGeometry.scale(-1f), eps)
        assertEquals(FilmStripGeometry.scale(2f), FilmStripGeometry.scale(1f), eps)
    }

    @Test fun bandTiltsWithSpeedAndIsCapped() {
        assertEquals(0f, FilmStripGeometry.tilt(0f), eps)
        assertTrue(FilmStripGeometry.tilt(1f) < 0f)
        assertEquals(-FilmStripGeometry.tilt(1f), FilmStripGeometry.tilt(-1f), eps)
        assertEquals(FilmStripGeometry.MAX_TILT, FilmStripGeometry.tilt(-50f), eps)
    }

    @Test fun holesMoveWithTheFramesAndTileEvenly() {
        val gap = FilmStripGeometry.HOLE_GAP
        val a = FilmStripGeometry.firstHole(0f, -180f)
        assertTrue(a <= -180f && a > -180f - gap)
        // Bir kare kayınca delik deseni aynı (kare başına tam sayıda delik).
        assertEquals(a, FilmStripGeometry.firstHole(1f, -180f), eps)
        // Yarım delik kayınca desen yarım aralık kayar.
        val b = FilmStripGeometry.firstHole(0.5f / FilmStripGeometry.HOLES, -180f)
        assertEquals(gap / 2f, ((a - b) % gap + gap) % gap, 0.01f)
    }

    @Test fun frameEdgesSitBetweenFrames() {
        // Ortadaki kare (konum 0) −STEP/2 … +STEP/2 arasında: sınır ±STEP/2'de.
        assertEquals(-FilmStripGeometry.STEP / 2f, FilmStripGeometry.firstEdge(0f, -100f), eps)
        assertEquals(FilmStripGeometry.STEP / 2f, FilmStripGeometry.firstEdge(0f, 0f), eps)
        assertEquals(0f, FilmStripGeometry.firstEdge(0.5f, -10f), eps)
    }

    @Test fun fewFramesFadeBeforeWrapping() {
        assertEquals(1f, FilmStripGeometry.alpha(0f, 3), eps)
        assertEquals(0f, FilmStripGeometry.alpha(1.5f, 3), eps)
        assertEquals(0f, FilmStripGeometry.alpha(2.6f, 10), eps)
    }
}
