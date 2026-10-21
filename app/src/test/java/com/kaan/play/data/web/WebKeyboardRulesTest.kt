package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebKeyboardRulesTest {

    @Test
    fun shouldRequestKeyboard_whenEditTextType_returnsTrue_K4() {
        assertTrue(WebKeyboardRules.shouldRequestKeyboard(9))
        assertTrue(WebKeyboardRules.shouldRequestKeyboard(WebKeyboardRules.HIT_TYPE_EDIT_TEXT))
    }

    @Test
    fun shouldRequestKeyboard_whenOtherTypes_returnsFalse_K4() {
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(0)) // UNKNOWN_TYPE
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(1))
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(7)) // SRC_ANCHOR_TYPE
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(8)) // SRC_IMAGE_ANCHOR_TYPE
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(-1))
    }

    @Test
    fun shouldRequestKeyboard_whenNull_returnsFalse_K4() {
        assertFalse(WebKeyboardRules.shouldRequestKeyboard(null))
    }

    @Test
    fun resolveBackAction_whenKeyboardVisible_returnsHideKeyboard_K4_K6() {
        // Klavye açıkken, tam ekran ve geçmiş olsa bile öncelik klavyeyi kapatmaktır
        val action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = true,
            isFullscreen = true,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.HIDE_KEYBOARD, action)
    }

    @Test
    fun resolveBackAction_whenFullscreenAndNotKeyboard_returnsExitFullscreen_K6() {
        val action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = true,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.EXIT_FULLSCREEN, action)
    }

    @Test
    fun resolveBackAction_whenCanGoBackAndNotKeyboardNorFullscreen_returnsNavigateBack_K4_K6() {
        val action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = false,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.NAVIGATE_BACK, action)
    }

    @Test
    fun resolveBackAction_whenNothingActive_returnsFinish_K6() {
        val action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = false,
            canGoBack = false
        )
        assertEquals(WebKeyboardRules.BackAction.FINISH, action)
    }

    @Test
    fun resolveBackAction_verifiesCompletePriorityOrder_K6() {
        // Sıra: klavye -> tam ekran -> geçmiş -> kapat

        // 1. Adım: Klavye açık
        var action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = true,
            isFullscreen = true,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.HIDE_KEYBOARD, action)

        // 2. Adım: Klavye kapandıktan sonra tam ekrandan çıkış
        action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = true,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.EXIT_FULLSCREEN, action)

        // 3. Adım: Tam ekrandan çıktıktan sonra geçmişte geri git
        action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = false,
            canGoBack = true
        )
        assertEquals(WebKeyboardRules.BackAction.NAVIGATE_BACK, action)

        // 4. Adım: Geçmiş bitince ekranı kapat
        action = WebKeyboardRules.resolveBackAction(
            isKeyboardVisible = false,
            isFullscreen = false,
            canGoBack = false
        )
        assertEquals(WebKeyboardRules.BackAction.FINISH, action)
    }
}
