package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlobeGeometryTest {
    private val eps = 1e-3f

    @Test fun frontCardIsLargestSharpestAndNearest() {
        val front = FloatArray(7).also { GlobeGeometry.place(0f, 8, it) }
        assertEquals(1f, front[2], eps)
        assertEquals(1f, front[3], eps)
        assertEquals(0f, front[4], eps)
        assertEquals(0f, front[6], eps)
        for (q in listOf(1f, 2f, 3f, 4f)) {
            val o = FloatArray(7).also { GlobeGeometry.place(q, 8, it) }
            assertTrue(o[2] < front[2] && o[3] < front[3] && o[6] > 0f)
        }
    }

    @Test fun sidesAreMirroredAndBackCardRisesAndFades() {
        val r = FloatArray(7).also { GlobeGeometry.place(1f, 6, it) }
        val l = FloatArray(7).also { GlobeGeometry.place(-1f, 6, it) }
        assertTrue(r[0] > 0f && l[0] < 0f)
        assertEquals(r[2], l[2], eps)
        assertEquals(-r[5], l[5], eps)
        val back = FloatArray(7).also { GlobeGeometry.place(3f, 6, it) }
        val front = FloatArray(7).also { GlobeGeometry.place(0f, 6, it) }
        assertTrue(back[1] < front[1]) // arka taraf ekranda yukarıda
        assertTrue(back[3] < 0.4f && back[4] > 0.5f)
    }

    @Test fun ringWrapsSmoothly() {
        // Tam arka ±yarım tur aynı nokta.
        val a = FloatArray(7).also { GlobeGeometry.place(-3f, 6, it) }
        val b = FloatArray(7).also { GlobeGeometry.place(2.9999f, 6, it) }
        assertTrue(abs(a[0] - b[0]) < 0.1f && abs(a[1] - b[1]) < 0.1f)
        assertEquals(-60f, GlobeGeometry.spin(1f, 6), eps)
    }

    @Test fun sphereOutlineAndFrontBack() {
        val p = FloatArray(3)
        GlobeGeometry.spherePoint(0f, 90f, p) // ekvatorun sağ ucu: kenar çizgisi
        assertEquals(GlobeGeometry.SPHERE_R, p[0], 1f)
        GlobeGeometry.spherePoint(0f, 0f, p)
        assertTrue(p[2] > 0f && p[1] > 0f) // ön nokta önde ve merkezin altında
        GlobeGeometry.spherePoint(0f, 180f, p)
        assertTrue(p[2] < 0f)
    }
}
