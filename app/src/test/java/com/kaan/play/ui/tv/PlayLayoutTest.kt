package com.kaan.play.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLayoutTest {

    @Test
    fun phonePortraitKeepsTheBottomBarAndSingleColumn() {
        val phone = PlayLayout(360, 800)
        assertFalse(phone.useRail)
        assertFalse(phone.twoPane)
        // büyük yazı/geniş telefon (411x891) de aynı
        assertFalse(PlayLayout(411, 891).useRail)
        assertFalse(PlayLayout(411, 891).twoPane)
    }

    @Test
    fun lowPhoneLandscapeKeepsTheBottomBarButGetsTwoPaneDetail() {
        val landscape = PlayLayout(800, 360)
        assertFalse(landscape.useRail) // yan menü sığmaz: alt menüde kalır
        assertTrue(landscape.twoPane) // kapak ekranı kaplamasın
    }

    @Test
    fun tabletsAndTvsUseTheRailAndTwoPaneDetail() {
        listOf(PlayLayout(1280, 800), PlayLayout(800, 1280), PlayLayout(960, 540), PlayLayout(1067, 600)).forEach {
            assertTrue("$it rail", it.useRail)
            assertTrue("$it twoPane", it.twoPane)
        }
    }

    @Test
    fun railDimensions() {
        assertEquals(72, PlayLayout.RAIL_COLLAPSED_WIDTH_DP)
        assertEquals(184, PlayLayout.RAIL_EXPANDED_WIDTH_DP)
    }

    @Test
    fun boundaries() {
        assertTrue(PlayLayout(600, 480).useRail)
        assertFalse(PlayLayout(599, 900).useRail)
        assertFalse(PlayLayout(900, 479).useRail)
        assertTrue(PlayLayout(600, 1000).twoPane)
        assertFalse(PlayLayout(599, 1000).twoPane)
    }
}
