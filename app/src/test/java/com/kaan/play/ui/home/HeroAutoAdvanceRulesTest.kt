package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroAutoAdvanceRulesTest {

    @Test
    fun constantsMatchSpecification() {
        assertEquals(6_000L, HeroAutoAdvanceRules.NO_TRAILER_DURATION_MS)
        assertEquals(14_000L, HeroAutoAdvanceRules.WITH_TRAILER_MAX_DURATION_MS)
        assertEquals(2_000L, HeroAutoAdvanceRules.TRAILER_START_DELAY_MS)
        assertEquals(8_000L, HeroAutoAdvanceRules.TRAILER_PLAY_TIMEOUT_MS)
        assertEquals(6_000L, HeroAutoAdvanceRules.RESUME_DELAY_MS)
    }

    @Test
    fun shouldAutoAdvance_whenAllConditionsMet_returnsTrue() {
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = false,
            isScrollInProgress = false,
            isResumed = true,
            autoAdvanceSetting = true,
            perfPolicyEnabled = true,
        )
        assertTrue(result)
    }

    @Test
    fun shouldAutoAdvance_whenUserFocusPresent_returnsFalse() {
        // Kumandada odak düğmedeyken ASLA kaymaz
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = true,
            isScrollInProgress = false,
            isResumed = true,
            autoAdvanceSetting = true,
            perfPolicyEnabled = true,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAutoAdvance_whenScrollInProgress_returnsFalse() {
        // Kullanıcı parmakla sürüklerken durur
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = false,
            isScrollInProgress = true,
            isResumed = true,
            autoAdvanceSetting = true,
            perfPolicyEnabled = true,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAutoAdvance_whenNotResumed_returnsFalse() {
        // Ekran arka planda veya başka sayfadayken durur
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = false,
            isScrollInProgress = false,
            isResumed = false,
            autoAdvanceSetting = true,
            perfPolicyEnabled = true,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAutoAdvance_whenSettingDisabled_returnsFalse() {
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = false,
            isScrollInProgress = false,
            isResumed = true,
            autoAdvanceSetting = false,
            perfPolicyEnabled = true,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAutoAdvance_whenPerfPolicyDisabled_returnsFalse() {
        // Performans modu
        val result = HeroAutoAdvanceRules.shouldAutoAdvance(
            hasUserFocus = false,
            isScrollInProgress = false,
            isResumed = true,
            autoAdvanceSetting = true,
            perfPolicyEnabled = false,
        )
        assertFalse(result)
    }

    @Test
    fun nextPageIndex_loopsAroundProperly() {
        assertEquals(0, HeroAutoAdvanceRules.nextPageIndex(0, 1))
        assertEquals(1, HeroAutoAdvanceRules.nextPageIndex(0, 5))
        assertEquals(2, HeroAutoAdvanceRules.nextPageIndex(1, 5))
        assertEquals(4, HeroAutoAdvanceRules.nextPageIndex(3, 5))
        // Sondan sonra başa döner
        assertEquals(0, HeroAutoAdvanceRules.nextPageIndex(4, 5))
    }

    @Test
    fun pageDurationMs_reflectsTrailerPresence() {
        assertEquals(6_000L, HeroAutoAdvanceRules.pageDurationMs(hasTrailer = false))
        assertEquals(14_000L, HeroAutoAdvanceRules.pageDurationMs(hasTrailer = true))
    }
}
