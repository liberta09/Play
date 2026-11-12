package com.kaan.play.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarRulesTest {

    @Test
    fun defaultIsTopAndStoredParsingHandlesAllPositionsAndCorruptInput() {
        assertEquals(NavBarPosition.Top, NavBarPosition.DEFAULT)
        assertEquals(NavBarPosition.Top, NavBarPosition.fromStored(null))
        assertEquals(NavBarPosition.Top, NavBarPosition.fromStored("invalid"))
        assertEquals(NavBarPosition.Bottom, NavBarPosition.fromStored("Bottom"))
        assertEquals(NavBarPosition.Bottom, NavBarPosition.fromStored("alt"))
        assertEquals(NavBarPosition.Left, NavBarPosition.fromStored("left"))
        assertEquals(NavBarPosition.Right, NavBarPosition.fromStored("Right"))
        assertEquals(NavBarPosition.Right, NavBarPosition.fromStored("sağ"))
    }

    @Test
    fun verticalStripAndDirectionBridgesMatchAllFourPositions() {
        assertFalse(NavBarRules.isVerticalStrip(NavBarPosition.Top))
        assertFalse(NavBarRules.isVerticalStrip(NavBarPosition.Bottom))
        assertTrue(NavBarRules.isVerticalStrip(NavBarPosition.Left))
        assertTrue(NavBarRules.isVerticalStrip(NavBarPosition.Right))

        assertEquals(EdgeDirection.Down, NavBarRules.menuExitDirection(NavBarPosition.Top))
        assertEquals(EdgeDirection.Up, NavBarRules.menuExitDirection(NavBarPosition.Bottom))
        assertEquals(EdgeDirection.Right, NavBarRules.menuExitDirection(NavBarPosition.Left))
        assertEquals(EdgeDirection.Left, NavBarRules.menuExitDirection(NavBarPosition.Right))

        assertEquals(EdgeDirection.Up, NavBarRules.contentReturnDirection(NavBarPosition.Top))
        assertEquals(EdgeDirection.Down, NavBarRules.contentReturnDirection(NavBarPosition.Bottom))
        assertEquals(EdgeDirection.Left, NavBarRules.contentReturnDirection(NavBarPosition.Left))
        assertEquals(EdgeDirection.Right, NavBarRules.contentReturnDirection(NavBarPosition.Right))
    }

    @Test
    fun contentPaddingAndHomePillShiftClearBarInEveryPosition() {
        assertEquals(NavContentPaddingDp(0, 0, 0, 0), NavBarRules.contentPaddingDp(NavBarPosition.Bottom, showNav = false, isHome = false))
        assertEquals(NavContentPaddingDp(0, 0, 0, 0), NavBarRules.contentPaddingDp(NavBarPosition.Top, showNav = true, isHome = true))
        assertEquals(NavContentPaddingDp(58, 0, 0, 0), NavBarRules.contentPaddingDp(NavBarPosition.Top, showNav = true, isHome = false))
        assertEquals(NavContentPaddingDp(0, 68, 0, 0), NavBarRules.contentPaddingDp(NavBarPosition.Bottom, showNav = true, isHome = false))
        assertEquals(NavContentPaddingDp(0, 0, 88, 0), NavBarRules.contentPaddingDp(NavBarPosition.Left, showNav = true, isHome = true))
        assertEquals(NavContentPaddingDp(0, 0, 0, 88), NavBarRules.contentPaddingDp(NavBarPosition.Right, showNav = true, isHome = true))

        assertEquals(20, NavBarRules.homeProviderPillBottomPaddingDp(NavBarPosition.Top))
        assertEquals(88, NavBarRules.homeProviderPillBottomPaddingDp(NavBarPosition.Bottom))
        assertEquals(68, NavBarRules.homeListExtraBottomDp(NavBarPosition.Bottom))
        assertEquals(0, NavBarRules.homeListExtraBottomDp(NavBarPosition.Left))
    }

    @Test
    fun stripLabelVisibleOnlyWhenFocusedOrSelected() {
        assertFalse(NavBarRules.showStripItemLabel(focused = false, selected = false))
        assertTrue(NavBarRules.showStripItemLabel(focused = true, selected = false))
        assertTrue(NavBarRules.showStripItemLabel(focused = false, selected = true))
        assertTrue(NavBarRules.showStripItemLabel(focused = true, selected = true))
    }
}
