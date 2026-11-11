package com.kaan.play.ui.home

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
}
