package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccordionGeometryTest {
    private val eps = 1e-3f

    private fun layout(position: Float, count: Int, total: Float = 360f, gap: Float = 3f): Pair<FloatArray, FloatArray> {
        val l = FloatArray(count)
        val w = FloatArray(count)
        AccordionGeometry.layout(position, count, total, gap, l, w)
        return l to w
    }

    @Test fun oneStripOpenAndWidthsFillTheRow() {
        for (count in listOf(3, 6, 10)) for (p in listOf(0f, 2.3f, 9.5f, -0.4f)) {
            val (l, w) = layout(p, count)
            assertEquals(360f, l[count - 1] + w[count - 1], 0.01f)
        }
        val (_, w) = layout(2f, 10)
        assertEquals(360f * AccordionGeometry.OPEN_FRACTION, w[2], eps)
        assertTrue(w.filterIndexed { i, _ -> i != 2 }.all { it < 20f && it > 10f })
    }

    @Test fun closingStripHandsOverToTheNext() {
        val (_, w) = layout(4.25f, 8)
        assertEquals(0.75f, AccordionGeometry.openness(4, 4.25f, 8), eps)
        assertEquals(0.25f, AccordionGeometry.openness(5, 4.25f, 8), eps)
        assertTrue(w[4] > w[5] && w[5] > w[6])
        // Sondan başa: yalnız son ve ilk şerit değişir.
        assertEquals(0.5f, AccordionGeometry.openness(0, 7.5f, 8), eps)
        assertEquals(0.5f, AccordionGeometry.openness(7, 7.5f, 8), eps)
        assertEquals(0f, AccordionGeometry.openness(3, 7.5f, 8), eps)
    }

    @Test fun tapFindsStripAndShortestTarget() {
        val (l, w) = layout(0f, 6)
        assertEquals(0, AccordionGeometry.stripAt(10f, 6, l, w))
        assertEquals(5, AccordionGeometry.stripAt(359f, 6, l, w))
        assertEquals(1, AccordionGeometry.stripAt(l[1] + 1f, 6, l, w))
        assertEquals(-1f, AccordionGeometry.targetFor(5, 0f, 6), eps)
        assertEquals(8f, AccordionGeometry.targetFor(2, 6f, 6), eps)
        assertEquals("03", AccordionGeometry.number(2))
    }
}
