package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdHidePlayerGuardTest {

    @Test
    fun identifiesDangerousSelectors() {
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("""[class*="money"]"""))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous(".ps-head-container"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("""div[class^="r_header_"]"""))
    }

    @Test
    fun identifiesProtectedPlayerSelectors() {
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("video"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("video.jw-video"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("iframe"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("iframe.embed-responsive-item"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous(".jwplayer"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous(".plyr"))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("""#player"""))
        assertTrue(AdHidePlayerGuard.isProtectedOrDangerous("""[id*="player"]"""))
    }

    @Test
    fun allowsSafeBannerSelectors() {
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".card-money"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money--video"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money-child"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money-item"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money-text"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".money-wrapper"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".rek-wrap"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".reklam-link"))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(""".video_content > a[target="_blank"]"""))
        assertFalse(AdHidePlayerGuard.isProtectedOrDangerous(".col > .alert-danger-50.alert"))
    }

    @Test
    fun filtersOutDangerousAndProtectedSelectors() {
        val input = listOf(
            ".card-money",
            """[class*="money"]""", // dangerous
            ".money-wrapper",
            ".ps-head-container", // dangerous
            "video", // protected
            ".reklam-link",
            "iframe", // protected
            """div[class^="r_header_"]""" // dangerous
        )

        val filtered = AdHidePlayerGuard.filterSafeSelectors(input)
        assertEquals(listOf(".card-money", ".money-wrapper", ".reklam-link"), filtered)
    }

    @Test
    fun buildGuardScriptContainsRequiredChecks() {
        val script = AdHidePlayerGuard.buildGuardScript()
        assertTrue(script.contains("__play_ad_hide__"))
        assertTrue(script.contains("display === 'none'"))
        assertTrue(script.contains("parentElement"))
        assertTrue(script.contains("getComputedStyle"))
        assertTrue(script.contains("styleEl.remove()"))
        assertTrue(script.contains("'REMOVED'"))
    }

    @Test
    fun cssIncludesRevertProtectionBlock() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "hdfilmcehennemi.*##.money-wrapper"
        ))

        val css = rules.buildCssForHost("hdfilmcehennemi.nl")
        assertTrue("Oynatıcı öğeleri revert kuralıyla korunmalıdır", css!!.contains("display: revert !important;"))
        assertTrue("video etiketi korunmalıdır", css.contains("video"))
        assertTrue("iframe etiketi korunmalıdır", css.contains("iframe"))
        assertTrue(".jwplayer korunmalıdır", css.contains(".jwplayer"))
    }
}
