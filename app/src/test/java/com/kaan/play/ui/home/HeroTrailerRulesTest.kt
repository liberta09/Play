package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroTrailerRulesTest {

    @Test
    fun canPlayTrailer_whenAllConditionsMet_returnsTrue() {
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = true,
            heroAutoAdvanceSetting = true,
            perfPolicyHeroEnabled = true,
            isLowRamDevice = false,
            isWifiOrEthernet = true,
        )
        assertTrue(canPlay)
    }

    @Test
    fun canPlayTrailer_whenSettingDisabled_returnsFalse() {
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = false,
            heroAutoAdvanceSetting = true,
            perfPolicyHeroEnabled = true,
            isLowRamDevice = false,
            isWifiOrEthernet = true,
        )
        assertFalse(canPlay)
    }

    @Test
    fun canPlayTrailer_whenAutoAdvanceDisabled_returnsFalse() {
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = true,
            heroAutoAdvanceSetting = false,
            perfPolicyHeroEnabled = true,
            isLowRamDevice = false,
            isWifiOrEthernet = true,
        )
        assertFalse(canPlay)
    }

    @Test
    fun canPlayTrailer_whenPerformanceModeEnabled_returnsFalse() {
        // perfPolicyHeroEnabled == false
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = true,
            heroAutoAdvanceSetting = true,
            perfPolicyHeroEnabled = false,
            isLowRamDevice = false,
            isWifiOrEthernet = true,
        )
        assertFalse(canPlay)
    }

    @Test
    fun canPlayTrailer_whenLowRamDevice_returnsFalse() {
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = true,
            heroAutoAdvanceSetting = true,
            perfPolicyHeroEnabled = true,
            isLowRamDevice = true,
            isWifiOrEthernet = true,
        )
        assertFalse(canPlay)
    }

    @Test
    fun canPlayTrailer_whenMobileData_returnsFalse() {
        // isWifiOrEthernet == false
        val canPlay = HeroTrailerRules.canPlayTrailer(
            trailerSettingEnabled = true,
            heroAutoAdvanceSetting = true,
            perfPolicyHeroEnabled = true,
            isLowRamDevice = false,
            isWifiOrEthernet = false,
        )
        assertFalse(canPlay)
    }

    @Test
    fun startPositionMs_seeksTo8sOnlyWhenLongerThan40s() {
        assertEquals(0L, HeroTrailerRules.startPositionMs(0L))
        assertEquals(0L, HeroTrailerRules.startPositionMs(30L))
        assertEquals(0L, HeroTrailerRules.startPositionMs(40L)) // 40s -> 0
        assertEquals(8_000L, HeroTrailerRules.startPositionMs(41L)) // >40s -> 8000ms
        assertEquals(8_000L, HeroTrailerRules.startPositionMs(120L))
    }

    @Test
    fun shouldSkipCover_skipsAfter5SecondsWithoutFirstFrame() {
        assertFalse(HeroTrailerRules.shouldSkipCover(hasRenderedFirstFrame = true, elapsedSinceLoadMs = 1000L))
        assertFalse(HeroTrailerRules.shouldSkipCover(hasRenderedFirstFrame = true, elapsedSinceLoadMs = 6000L))
        assertFalse(HeroTrailerRules.shouldSkipCover(hasRenderedFirstFrame = false, elapsedSinceLoadMs = 4999L))
        assertTrue(HeroTrailerRules.shouldSkipCover(hasRenderedFirstFrame = false, elapsedSinceLoadMs = 5000L))
        assertTrue(HeroTrailerRules.shouldSkipCover(hasRenderedFirstFrame = false, elapsedSinceLoadMs = 8000L))
    }

    @Test
    fun nextCandidateIndex_cyclesProperly() {
        assertEquals(0, HeroTrailerRules.nextCandidateIndex(0, 1))
        assertEquals(1, HeroTrailerRules.nextCandidateIndex(0, 3))
        assertEquals(2, HeroTrailerRules.nextCandidateIndex(1, 3))
        assertEquals(0, HeroTrailerRules.nextCandidateIndex(2, 3))
    }
}
