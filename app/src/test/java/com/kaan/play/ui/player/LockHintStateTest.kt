package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockHintStateTest {

    @Test
    fun initial_stateIsHidden() {
        val state = LockHintState()
        assertFalse(state.isVisible(0L))
        assertFalse(state.isVisible(1000L))
        assertEquals(0L, state.remainingMs(0L))
        assertEquals(0L, state.remainingMs(1000L))
    }

    @Test
    fun show_staysVisibleForExactTimeout() {
        val state = LockHintState(3000L)
        val now = 1000L
        state.show(now)

        assertTrue("Başlangıç anında görünür", state.isVisible(1000L))
        assertTrue("1.5 sn sonra görünür", state.isVisible(2500L))
        assertEquals(1500L, state.remainingMs(2500L))

        assertTrue("2.999 sn sonra görünür", state.isVisible(3999L))
        assertEquals(1L, state.remainingMs(3999L))

        assertFalse("3.000 sn dolunca gizlenir", state.isVisible(4000L))
        assertEquals(0L, state.remainingMs(4000L))

        assertFalse("Süre geçtikten sonra gizli", state.isVisible(5000L))
        assertEquals(0L, state.remainingMs(5000L))
    }

    @Test
    fun multipleShow_resetsTimeout() {
        val state = LockHintState(3000L)
        state.show(1000L)
        assertTrue(state.isVisible(2000L))

        // Kullanıcı 2000 ms anında yeniden dokunur/tuşa basar: süre 2000 + 3000 = 5000L olur
        state.show(2000L)
        assertTrue(state.isVisible(4000L))
        assertEquals(1000L, state.remainingMs(4000L))
        assertTrue(state.isVisible(4999L))
        assertFalse(state.isVisible(5000L))
    }

    @Test
    fun hide_clearsVisibilityImmediately() {
        val state = LockHintState(3000L)
        state.show(1000L)
        assertTrue(state.isVisible(1500L))

        state.hide()
        assertFalse(state.isVisible(1500L))
        assertEquals(0L, state.remainingMs(1500L))
        assertFalse(state.isVisible(2000L))
    }

    @Test
    fun customTimeout_respected() {
        val state = LockHintState(timeoutMs = 5000L)
        state.show(100L)
        assertTrue(state.isVisible(5099L))
        assertFalse(state.isVisible(5100L))
    }
}
