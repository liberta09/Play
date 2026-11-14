package com.kaan.play.ui.home.slider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SnapshotsGeometryTest {
    private val g = SnapshotsGeometry
    private val eps = 1e-4f

    @Test fun frontPhotoIsBigAndStraight() {
        val front = g.pose(3, 3f, 8)
        assertEquals(0f, front.rotation, eps)
        assertEquals(1f, front.scale, eps)
        assertEquals(0f, front.x, eps)
        // Arkadakiler eğik, küçük ve kaymış.
        for (i in 4..7) {
            val p = g.pose(i, 3f, 8)
            assertTrue(abs(p.rotation) > 2f)
            assertTrue(p.scale < 0.9f)
        }
    }

    @Test fun leavingPhotoSwingsAsideAndGoesUnder() {
        val count = 6
        // Konum 0 → 1: 0 numaralı fotoğraf savruluyor.
        val mid = g.pose(0, 0.5f, count)
        assertTrue("yana savrulur", mid.x < -0.4f)
        assertTrue("dönerek", abs(mid.rotation) > 15f)
        assertEquals(count + 1, g.layer(0, 0.3f, count)) // ilk yarıda en üstte
        assertEquals(0, g.layer(0, 0.7f, count)) // sonra en altta
        // Sonunda kendi açısıyla yığının dibinde; varışta duruş süreklidir.
        val end = g.pose(0, 0.9999f, count)
        val rest = g.rest(0, count - 1f)
        assertEquals(rest.x, end.x, 1e-2f)
        assertEquals(rest.rotation, end.rotation, 1e-1f)
    }

    @Test fun nextPhotoComesForwardAndLayersAreUnique() {
        val count = 7
        assertEquals(count, g.layer(1, 0.4f, count))
        for (pos in listOf(0f, 0.2f, 0.7f, 2.5f)) {
            val layers = List(count) { g.layer(it, pos, count) }
            assertEquals(count, layers.toSet().size)
        }
        // Çok derindekiler görünmez.
        assertEquals(0f, g.rest(0, g.VISIBLE + 1f).alpha, eps)
        assertTrue(g.FRAME_H < g.SCENE_H)
    }
}
