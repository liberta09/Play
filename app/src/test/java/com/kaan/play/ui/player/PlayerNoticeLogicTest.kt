package com.kaan.play.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNoticeLogicTest {

    @Test
    fun shouldShowDetail_whenDebugTrueAndDetailPresent_returnsTrue() {
        assertTrue(PlayerNoticeLogic.shouldShowDetail(isDebug = true, detail = "ERROR_CODE_IO_BAD_HTTP_STATUS (2004)"))
        assertTrue(PlayerNoticeLogic.shouldShowDetail(isDebug = true, detail = "content"))
    }

    @Test
    fun shouldShowDetail_whenRelease_alwaysReturnsFalse() {
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = false, detail = "ERROR_CODE_IO_BAD_HTTP_STATUS (2004)"))
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = false, detail = "content"))
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = false, detail = ""))
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = false, detail = null))
    }

    @Test
    fun shouldShowDetail_whenDebugTrueButDetailBlankOrNull_returnsFalse() {
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = true, detail = null))
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = true, detail = ""))
        assertFalse(PlayerNoticeLogic.shouldShowDetail(isDebug = true, detail = "   "))
    }
}
