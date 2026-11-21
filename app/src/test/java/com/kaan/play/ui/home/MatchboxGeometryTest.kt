package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchboxGeometryTest {
    private val eps = 1e-4f

    @Test fun rowStepsAndNeighboursPeekAtTheEdges() {
        assertEquals(0f, MatchboxGeometry.boxX(0f), eps)
        assertEquals(-MatchboxGeometry.STEP, MatchboxGeometry.boxX(-1f), eps)
        // Komşu kutular aralıklı ve kısmen ekranda (sahne yarı genişliği 180).
        assertTrue(MatchboxGeometry.STEP > MatchboxGeometry.SLEEVE_W)
        assertTrue(MatchboxGeometry.STEP - MatchboxGeometry.SLEEVE_W / 2f < MatchboxGeometry.STAGE / 2f)
        // Açık çekmece afişin çoğunu gösterir.
        assertTrue(MatchboxGeometry.OPEN > MatchboxGeometry.TRAY_LEN * 0.8f)
    }

    @Test fun boxesJumpingAcrossTheRingAreHidden() {
        assertEquals(1f, MatchboxGeometry.alpha(0f, 10), eps)
        assertEquals(1f, MatchboxGeometry.alpha(2f, 10), eps)
        assertEquals(0f, MatchboxGeometry.alpha(-5f, 10), eps)
        assertEquals(0f, MatchboxGeometry.alpha(1.5f, 3), eps)
    }

    @Test fun transitionTimingsFollowTheSpec() {
        assertEquals(380, MatchboxGeometry.CLOSE_MS)
        assertEquals(560, MatchboxGeometry.SHIFT_MS)
        assertEquals(620, MatchboxGeometry.OPEN_MS)
        assertEquals(4_500L, MatchboxGeometry.AUTOPLAY_MS)
        assertEquals(380, MatchboxGeometry.closeMs(1f))
        assertEquals(190, MatchboxGeometry.closeMs(0.5f))
        assertEquals(0, MatchboxGeometry.closeMs(0f))
        assertEquals(620, MatchboxGeometry.openMs(0f))
        assertEquals(0, MatchboxGeometry.openMs(1f))
    }

    @Test fun swipeAdvancesOnAThirdOrAFling() {
        assertEquals(3f, MatchboxGeometry.settleTarget(3f, 3.2f, 0f), eps)
        assertEquals(4f, MatchboxGeometry.settleTarget(3f, 3.34f, 0f), eps)
        assertEquals(2f, MatchboxGeometry.settleTarget(3f, 2.6f, 0f), eps)
        assertEquals(4f, MatchboxGeometry.settleTarget(3f, 3.05f, 2f), eps)
        assertEquals(2f, MatchboxGeometry.settleTarget(3f, 2.95f, -2f), eps)
        assertEquals(5f, MatchboxGeometry.settleTarget(3f, 5.2f, 0f), eps)
    }

    @Test fun tapOnSidesSteps() {
        assertEquals(0, MatchboxGeometry.tapStep(0f))
        assertEquals(0, MatchboxGeometry.tapStep(-80f))
        assertEquals(1, MatchboxGeometry.tapStep(150f))
        assertEquals(-1, MatchboxGeometry.tapStep(-150f))
    }

    @Test fun sleeveColoursCycleWithReadableInk() {
        assertEquals(MatchboxGeometry.sleeveColor(0), MatchboxGeometry.sleeveColor(7))
        assertEquals(0xFF2F4B7C.toInt(), MatchboxGeometry.sleeveColor(1))
        assertEquals(MatchboxGeometry.INK_DARK, MatchboxGeometry.inkOn(0xFFE2B13C.toInt()))
        assertEquals(MatchboxGeometry.INK_DARK, MatchboxGeometry.inkOn(0xFFD98CA2.toInt()))
        assertEquals(MatchboxGeometry.INK_LIGHT, MatchboxGeometry.inkOn(0xFF1F1D1B.toInt()))
        assertEquals(MatchboxGeometry.INK_LIGHT, MatchboxGeometry.inkOn(0xFF2F4B7C.toInt()))
        for (c in MatchboxGeometry.SLEEVES) {
            val ink = MatchboxGeometry.inkOn(c)
            val hi = maxOf(MatchboxGeometry.luminance(c), MatchboxGeometry.luminance(ink)) + 0.05f
            val lo = minOf(MatchboxGeometry.luminance(c), MatchboxGeometry.luminance(ink)) + 0.05f
            assertTrue("contrast ${Integer.toHexString(c)}", hi / lo >= 3.5f)
        }
    }

    @Test fun labels() {
        assertEquals("No. 01", MatchboxGeometry.number(0))
        assertEquals("İ", MatchboxGeometry.initial("ilk aşk"))
        assertEquals("K", MatchboxGeometry.initial("\"Kahraman\" Koca"))
        assertEquals("2024", MatchboxGeometry.detail(" 2024 ", "Dizi"))
        assertEquals("Dizi", MatchboxGeometry.detail(null, "Dizi"))
        assertEquals("Dizi", MatchboxGeometry.detail("çok uzun bir açıklama metni burada duruyor", "Dizi"))
    }

    @Test fun longWordsShrinkTheTitleInsteadOfBreaking() {
        assertEquals(23f, MatchboxGeometry.titleSize("İkiz Kardeşim Mafya Kraliçesi"), eps)
        val long = MatchboxGeometry.titleSize("Milyarderin Kız Kardeşi")
        assertTrue(long < 23f && long >= 15f)
        assertTrue(long * 0.62f * 11 <= MatchboxGeometry.SLEEVE_W - 32f + eps)
        assertEquals(15f, MatchboxGeometry.titleSize("Süperkahramanlaştırılamayanlar"), eps)
        assertEquals(23f, MatchboxGeometry.titleSize(""), eps)
    }

    @Test fun onlyDramaVaktiProvider() {
        assertTrue(isDramaVaktiProvider("DramaVakti"))
        assertTrue(isDramaVaktiProvider("Drama Vakti"))
        assertFalse(isDramaVaktiProvider("Vakti"))
        assertFalse(isDramaVaktiProvider("DramaTR"))
        assertFalse(isDramaVaktiProvider("Shortİzle"))
        assertFalse(isDramaVaktiProvider("DramaVaktiMp4"))
    }
}
