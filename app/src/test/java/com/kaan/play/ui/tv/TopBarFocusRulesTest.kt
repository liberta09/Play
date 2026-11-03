package com.kaan.play.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarFocusRulesTest {

    @Test
    fun targetAttachedAndFocusTaken_returnsInitialTarget() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = true,
            focusTaken = true,
            isHome = true,
            isScrolled = false,
        )
        assertEquals(TopBarFocusRules.Step.INITIAL_TARGET, step)
    }

    @Test
    fun initialFocusFailed_scrolledHome_returnsScrollAndRetry() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = true,
            focusTaken = false,
            isHome = true,
            isScrolled = true,
        )
        assertEquals(TopBarFocusRules.Step.SCROLL_AND_RETRY, step)
    }

    @Test
    fun targetNotAttached_scrolledHome_returnsScrollAndRetry() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = false,
            focusTaken = false,
            isHome = true,
            isScrolled = true,
        )
        assertEquals(TopBarFocusRules.Step.SCROLL_AND_RETRY, step)
    }

    @Test
    fun initialFocusFailed_notHome_returnsMoveFocusDown() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = true,
            focusTaken = false,
            isHome = false,
            isScrolled = true,
        )
        assertEquals(TopBarFocusRules.Step.MOVE_FOCUS_DOWN, step)
    }

    @Test
    fun initialFocusFailed_homeNotScrolled_returnsMoveFocusDown() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = true,
            focusTaken = false,
            isHome = true,
            isScrolled = false,
        )
        assertEquals(TopBarFocusRules.Step.MOVE_FOCUS_DOWN, step)
    }

    @Test
    fun targetNotAttached_notHome_returnsMoveFocusDown() {
        val step = TopBarFocusRules.decideStep(
            targetAttached = false,
            focusTaken = false,
            isHome = false,
            isScrolled = false,
        )
        assertEquals(TopBarFocusRules.Step.MOVE_FOCUS_DOWN, step)
    }
}
