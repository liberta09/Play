package com.kaan.play.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarFocusRulesTest {

    @Test
    fun targetAttachedAndNotScrolled_requestsTarget() {
        val action = TopBarFocusRules.decide(
            targetAttached = true,
            isScrolled = false,
            canScrollToTop = true,
        )
        assertEquals(TopBarFocusRules.Action.REQUEST_TARGET, action)
    }

    @Test
    fun isScrolledAndCanScroll_scrollsToTopAndFocuses() {
        val action = TopBarFocusRules.decide(
            targetAttached = false,
            isScrolled = true,
            canScrollToTop = true,
        )
        assertEquals(TopBarFocusRules.Action.SCROLL_TO_TOP_AND_FOCUS, action)
    }

    @Test
    fun isScrolledEvenIfTargetAttached_scrollsToTopAndFocuses() {
        val action = TopBarFocusRules.decide(
            targetAttached = true,
            isScrolled = true,
            canScrollToTop = true,
        )
        assertEquals(TopBarFocusRules.Action.SCROLL_TO_TOP_AND_FOCUS, action)
    }

    @Test
    fun targetNotAttachedAndCannotScroll_movesFocusDown() {
        val action = TopBarFocusRules.decide(
            targetAttached = false,
            isScrolled = false,
            canScrollToTop = false,
        )
        assertEquals(TopBarFocusRules.Action.MOVE_FOCUS_DOWN, action)
    }

    @Test
    fun targetAttachedAndCannotScroll_requestsTarget() {
        val action = TopBarFocusRules.decide(
            targetAttached = true,
            isScrolled = false,
            canScrollToTop = false,
        )
        assertEquals(TopBarFocusRules.Action.REQUEST_TARGET, action)
    }
}
