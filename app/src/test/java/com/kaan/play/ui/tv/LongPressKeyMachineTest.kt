package com.kaan.play.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LongPressKeyMachineTest {

    private lateinit var machine: LongPressKeyMachine

    @Before
    fun setUp() {
        machine = LongPressKeyMachine(longPressThresholdMs = 500L)
    }

    @Test
    fun shortPressAndRelease_emitsClick() {
        // 1. İlk basış: Press yayılmalı, durum PRESSED olmalı
        val downAction = machine.onKeyDown(repeatCount = 0, isLongPress = false)
        assertEquals(LongPressKeyMachine.Action.Press, downAction)
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        // 2. 500 ms dolmadan tuş bırakılır: Click dönmeli, durum IDLE olmalı
        val upAction = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.Click, upAction)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }

    @Test
    fun longPress500msAndRelease_emitsShowDialogOnReleaseAndNoClick() {
        // 1. İlk basış: Press
        val downAction = machine.onKeyDown(repeatCount = 0, isLongPress = false)
        assertEquals(LongPressKeyMachine.Action.Press, downAction)
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        // 2. 500 ms doldu: uzun basma algılanır ve silahlanır, ancak henüz pencere açılmaz (Nothing)
        val timeoutAction = machine.onTimeout()
        assertEquals(LongPressKeyMachine.Action.Nothing, timeoutAction)
        assertEquals(LongPressKeyMachine.State.LONG_PRESS_ARMED, machine.state)

        // 3. Tuş basılı tutulurken gelen otomatik tekrarlar pencere açmamalı veya ek tetikleme yapmamalı
        assertEquals(LongPressKeyMachine.Action.Nothing, machine.onKeyDown(repeatCount = 1, isLongPress = false))
        assertEquals(LongPressKeyMachine.Action.Nothing, machine.onKeyDown(repeatCount = 2, isLongPress = false))
        assertEquals(LongPressKeyMachine.State.LONG_PRESS_ARMED, machine.state)

        // 4. Tuş BIRAKILDIĞINDA (KeyUp) pencere açılmalı (ShowDialogOnRelease) ve Click ASLA verilmemeli
        val upAction = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.ShowDialogOnRelease, upAction)
        assertTrue(upAction !is LongPressKeyMachine.Action.Click)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }

    @Test
    fun repeatedKeyDowns_doNotTriggerMultipleActions() {
        // İlk basış
        assertEquals(LongPressKeyMachine.Action.Press, machine.onKeyDown(repeatCount = 0, isLongPress = false))

        // Donanımsal tekrar olayları (repeatCount > 0) hiçbir ek eylem tetiklemez
        for (i in 1..5) {
            val repeatAction = machine.onKeyDown(repeatCount = i, isLongPress = false)
            assertEquals(LongPressKeyMachine.Action.Nothing, repeatAction)
        }
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        // Zaman aşımı sonrası da tekrarlar yutulur
        machine.onTimeout()
        for (i in 6..10) {
            val repeatAction = machine.onKeyDown(repeatCount = i, isLongPress = false)
            assertEquals(LongPressKeyMachine.Action.Nothing, repeatAction)
        }
        assertEquals(LongPressKeyMachine.State.LONG_PRESS_ARMED, machine.state)
    }

    @Test
    fun focusLost_resetsStateAndEmitsCancel() {
        // Basılı durumdayken odak kaybedildi
        machine.onKeyDown(repeatCount = 0, isLongPress = false)
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        val cancelAction = machine.onFocusLost()
        assertEquals(LongPressKeyMachine.Action.Cancel, cancelAction)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)

        // IDLE durumdayken odak kaybı Nothing döner
        assertEquals(LongPressKeyMachine.Action.Nothing, machine.onFocusLost())

        // Uzun basma silahlanmışken odak kaybı da Cancel döner ve sıfırlar
        machine.onKeyDown(repeatCount = 0, isLongPress = false)
        machine.onTimeout()
        assertEquals(LongPressKeyMachine.State.LONG_PRESS_ARMED, machine.state)
        assertEquals(LongPressKeyMachine.Action.Cancel, machine.onFocusLost())
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }

    @Test
    fun afterLongPressDismiss_nextShortPress_emitsClickImmediately() {
        // 1. Önceki uzun basma döngüsü: bas, bekle, bırak (pencere açıldı)
        machine.onKeyDown(0, false)
        machine.onTimeout()
        val releaseAction = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.ShowDialogOnRelease, releaseAction)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)

        // 2. Kullanıcı pencereyi kapattı ve karta geri döndü.
        // İlk kısa basışın yutulmadığı, doğrudan Press ve ardından Click verdiği doğrulanır:
        val firstNextDown = machine.onKeyDown(0, false)
        assertEquals(LongPressKeyMachine.Action.Press, firstNextDown)
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        val firstNextUp = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.Click, firstNextUp)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }

    @Test
    fun nativeLongPressFlag_armsLongPress_andEmitsShowDialogOnRelease() {
        // Android KeyEvent.FLAG_LONG_PRESS veya isLongPress = true geldiğinde
        machine.onKeyDown(0, false)
        assertEquals(LongPressKeyMachine.State.PRESSED, machine.state)

        // Native bayrak KeyDown sırasında tetiklendi:
        val nativeLongDown = machine.onKeyDown(1, isLongPress = true)
        assertEquals(LongPressKeyMachine.Action.Nothing, nativeLongDown)
        assertEquals(LongPressKeyMachine.State.LONG_PRESS_ARMED, machine.state)

        // Bırakılınca ShowDialogOnRelease
        val upAction = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.ShowDialogOnRelease, upAction)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }

    @Test
    fun onKeyUp_whenIdle_emitsNothing() {
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
        val upAction = machine.onKeyUp()
        assertEquals(LongPressKeyMachine.Action.Nothing, upAction)
        assertEquals(LongPressKeyMachine.State.IDLE, machine.state)
    }
}
