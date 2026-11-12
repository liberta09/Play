package com.kaan.play.ui.player

import androidx.compose.ui.input.key.Key
import com.kaan.play.ui.player.PlayerKeyAction.OpenMenu
import com.kaan.play.ui.player.PlayerKeyAction.PassThrough
import com.kaan.play.ui.player.PlayerKeyAction.Reveal
import com.kaan.play.ui.player.PlayerKeyAction.SeekBack
import com.kaan.play.ui.player.PlayerKeyAction.SeekForward
import com.kaan.play.ui.player.PlayerKeyAction.TogglePlay
import com.kaan.play.ui.player.PlayerKeyAction.ZapNext
import com.kaan.play.ui.player.PlayerKeyAction.ZapPrev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {

    private fun hidden(key: Key) = PlayerKeys.actionFor(key, controlsVisible = false)
    private fun visible(key: Key) = PlayerKeys.actionFor(key, controlsVisible = true)

    @Test
    fun hiddenControlsMapEveryRemoteKeyToItsJob() {
        assertEquals(TogglePlay, hidden(Key.DirectionCenter))
        assertEquals(TogglePlay, hidden(Key.Enter))
        assertEquals(TogglePlay, hidden(Key.NumPadEnter))
        assertEquals(SeekBack, hidden(Key.DirectionLeft))
        assertEquals(SeekForward, hidden(Key.DirectionRight))
        assertEquals(Reveal, hidden(Key.DirectionUp))
        assertEquals(OpenMenu, hidden(Key.DirectionDown))
        assertEquals(OpenMenu, hidden(Key.Menu))
    }

    @Test
    fun anyOtherKeyBringsTheControlsBack() {
        assertEquals(Reveal, hidden(Key.Tab))
        assertEquals(Reveal, hidden(Key.A))
        assertEquals(Reveal, hidden(Key.Spacebar))
    }

    @Test
    fun mediaKeysAlwaysWork() {
        listOf(false, true).forEach { shown ->
            fun a(k: Key) = PlayerKeys.actionFor(k, shown)
            assertEquals(TogglePlay, a(Key.MediaPlayPause))
            assertEquals(TogglePlay, a(Key.MediaPlay))
            assertEquals(TogglePlay, a(Key.MediaPause))
            assertEquals(SeekForward, a(Key.MediaFastForward))
            assertEquals(SeekForward, a(Key.MediaStepForward))
            assertEquals(SeekBack, a(Key.MediaRewind))
            assertEquals(SeekBack, a(Key.MediaStepBackward))
        }
    }

    @Test
    fun visibleControlsLeaveDirectionsAndOkToTheFocusedButton() {
        listOf(Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
            .forEach { assertEquals(PassThrough, visible(it)) }
    }

    @Test
    fun progressBarFocusSeeksWithLeftRightAndMovesFocusWithUpDown() {
        fun bar(k: Key) = PlayerKeys.actionFor(k, controlsVisible = true, progressFocused = true)
        assertEquals(SeekBack, bar(Key.DirectionLeft))
        assertEquals(SeekForward, bar(Key.DirectionRight))
        assertEquals(TogglePlay, bar(Key.DirectionCenter))
        assertEquals(TogglePlay, bar(Key.Enter))
        // Slider Yukarı/Aşağı ile değeri oynatırdı: Aşağı düğmelere geçer, Yukarı çubuktan çıkar (olay burada alınır).
        assertEquals(PlayerKeyAction.GoToButtons, bar(Key.DirectionDown))
        assertEquals(PlayerKeyAction.LeaveBarUp, bar(Key.DirectionUp))
        // Çubuk odakta değilken (düğmeler) Sol/Sağ yine düğmeleri gezdirir.
        assertEquals(PassThrough, PlayerKeys.actionFor(Key.DirectionLeft, controlsVisible = true, progressFocused = false))
        // Kontroller gizliyken çubuk bilgisi önemsizdir.
        assertEquals(SeekBack, PlayerKeys.actionFor(Key.DirectionLeft, controlsVisible = false, progressFocused = true))
    }

    @Test
    fun volumeKeysAreNeverSwallowed() {
        listOf(Key.VolumeUp, Key.VolumeDown, Key.VolumeMute).forEach {
            assertEquals(PassThrough, hidden(it))
            assertEquals(PassThrough, visible(it))
        }
    }

    @Test
    fun holdingSeekAcceleratesInSteps() {
        assertEquals(10_000L, SeekAcceleration.stepMs(0)) // ilk basış
        assertEquals(0L, SeekAcceleration.stepMs(1)) // uzun basışın ilk gecikmesi
        assertEquals(0L, SeekAcceleration.stepMs(2))
        assertEquals(10_000L, SeekAcceleration.stepMs(3)) // ilk tekrar adımı
        assertEquals(0L, SeekAcceleration.stepMs(4))
        assertEquals(10_000L, SeekAcceleration.stepMs(7))
        assertEquals(10_000L, SeekAcceleration.stepMs(19))
        assertEquals(30_000L, SeekAcceleration.stepMs(23))
        assertEquals(30_000L, SeekAcceleration.stepMs(47))
        assertEquals(60_000L, SeekAcceleration.stepMs(51))
        assertEquals(60_000L, SeekAcceleration.stepMs(999 * 4 + 3))
    }

    @Test
    fun accelerationNeverGetsSlowerAndStepsAreWholeTenSeconds() {
        var previous = 0L
        for (repeat in 0..400) {
            val step = SeekAcceleration.stepMs(repeat)
            assertEquals(0L, step % 10_000L)
            if (step > 0) {
                assert(step >= previous) { "tekrar $repeat: $step < $previous" }
                previous = step
            }
        }
    }

    // Kullanıcının bulduğu hata (2026-10-21): kumandayla çubukta sarınca KeyUp Slider'a gidip videoyu başa döndürüyordu.
    @Test
    fun barKeyUpIsConsumedSoSliderCannotSeekToStart() {
        for (key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
            assertTrue(key.toString(), PlayerKeys.consumesBarKeyUp(key, controlsVisible = true, progressFocused = true))
        }
        // Çubuk odakta değilse ya da kontroller gizliyse düğmeler/gezinti normal çalışır.
        assertFalse(PlayerKeys.consumesBarKeyUp(Key.DirectionRight, controlsVisible = true, progressFocused = false))
        assertFalse(PlayerKeys.consumesBarKeyUp(Key.DirectionRight, controlsVisible = false, progressFocused = true))
        // Yukarı/Aşağı odak gezintisi için KeyDown'da ele alınır; KeyUp'ları yutulmaz.
        assertFalse(PlayerKeys.consumesBarKeyUp(Key.DirectionDown, controlsVisible = true, progressFocused = true))
    }

    @Test
    fun progressSeekOnlyAfterRealDrag() {
        // Sürüklenmeden gelen "bitti" (kumanda KeyUp'ı): kesir 0 olsa bile başa SARILMAZ.
        assertNull(ProgressSeek.finishTargetMs(dragging = false, dragFraction = 0f, durationMs = 3_600_000L))
        // Gerçek sürükleme: kesre göre sarılır.
        assertEquals(900_000L, ProgressSeek.finishTargetMs(dragging = true, dragFraction = 0.25f, durationMs = 3_600_000L))
        // Süre bilinmiyorsa sarılmaz.
        assertNull(ProgressSeek.finishTargetMs(dragging = true, dragFraction = 0.5f, durationMs = 0L))
    }

    @Test
    fun liveZapKeysSwitchChannelsOnlyOnLiveBroadcasts() {
        for (visible in listOf(false, true)) {
            assertEquals(ZapNext, PlayerKeys.actionFor(Key.ChannelUp, controlsVisible = visible, isLive = true))
            assertEquals(ZapNext, PlayerKeys.actionFor(Key.PageUp, controlsVisible = visible, isLive = true))
            assertEquals(ZapPrev, PlayerKeys.actionFor(Key.ChannelDown, controlsVisible = visible, isLive = true))
            assertEquals(ZapPrev, PlayerKeys.actionFor(Key.PageDown, controlsVisible = visible, isLive = true))

            assertFalse(PlayerKeys.actionFor(Key.ChannelUp, controlsVisible = visible, isLive = false) in listOf(ZapNext, ZapPrev))
            assertFalse(PlayerKeys.actionFor(Key.ChannelDown, controlsVisible = visible, isLive = false) in listOf(ZapNext, ZapPrev))
            assertFalse(PlayerKeys.actionFor(Key.PageUp, controlsVisible = visible, isLive = false) in listOf(ZapNext, ZapPrev))
            assertFalse(PlayerKeys.actionFor(Key.PageDown, controlsVisible = visible, isLive = false) in listOf(ZapNext, ZapPrev))
        }

        assertEquals(+1, PlayerKeys.zapDirectionForKeyCode(166, isLive = true))
        assertEquals(+1, PlayerKeys.zapDirectionForKeyCode(92, isLive = true))
        assertEquals(-1, PlayerKeys.zapDirectionForKeyCode(167, isLive = true))
        assertEquals(-1, PlayerKeys.zapDirectionForKeyCode(93, isLive = true))
        assertEquals(0, PlayerKeys.zapDirectionForKeyCode(166, isLive = false))
        assertEquals(0, PlayerKeys.zapDirectionForKeyCode(167, isLive = false))
    }
}
