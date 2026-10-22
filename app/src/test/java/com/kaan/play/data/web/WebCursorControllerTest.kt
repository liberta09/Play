package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebCursorControllerTest {

    @Test
    fun initialPosition_isCenteredInViewport() {
        val controller = WebCursorController()
        controller.setViewport(1920f, 1080f)

        assertEquals(960f, controller.x, 0.001f)
        assertEquals(540f, controller.y, 0.001f)
        assertTrue(controller.isVisible)
        assertFalse(controller.isFaded)
    }

    @Test
    fun dpadMovement_updatesCoordinatesCorrectly_K1() {
        var currentTime = 1000L
        val controller = WebCursorController(
            baseStep = 10f,
            maxStep = 30f,
            clock = { currentTime }
        )
        controller.setViewport(1000f, 1000f)
        controller.setPosition(500f, 500f)

        // Sağ hareket -> X artar
        val r1 = controller.move(MoveDirection.RIGHT, timeMs = currentTime)
        assertEquals(510f, r1.x, 0.001f)
        assertEquals(500f, r1.y, 0.001f)
        assertEquals(0, r1.scrollDx)
        assertEquals(0, r1.scrollDy)

        // Aşağı hareket -> Y artar
        currentTime += 500L // Yön değiştiğinde veya süre geçince yeni adım
        val r2 = controller.move(MoveDirection.DOWN, timeMs = currentTime)
        assertEquals(510f, r2.x, 0.001f)
        assertEquals(510f, r2.y, 0.001f)

        // Sol hareket -> X azalır
        currentTime += 500L
        val r3 = controller.move(MoveDirection.LEFT, timeMs = currentTime)
        assertEquals(500f, r3.x, 0.001f)
        assertEquals(510f, r3.y, 0.001f)

        // Yukarı hareket -> Y azalır
        currentTime += 500L
        val r4 = controller.move(MoveDirection.UP, timeMs = currentTime)
        assertEquals(500f, r4.x, 0.001f)
        assertEquals(500f, r4.y, 0.001f)
    }

    @Test
    fun acceleration_increasesSpeedWhenKeyIsHeld_K1() {
        var currentTime = 1000L
        val controller = WebCursorController(
            baseStep = 10f,
            maxStep = 40f,
            accelerationDurationMs = 800L,
            clock = { currentTime }
        )
        controller.setViewport(2000f, 2000f)
        controller.setPosition(100f, 100f)

        // İlk basış (t = 1000 ms) -> taban hız
        val step0 = controller.move(MoveDirection.RIGHT, timeMs = currentTime)
        assertEquals(10f, step0.step, 0.001f)

        // 400 ms sonra (t = 1400 ms) -> yarı ivmelenmiş hız (10 + (40-10) * 0.5 = 25)
        currentTime = 1400L
        val step1 = controller.move(MoveDirection.RIGHT, timeMs = currentTime)
        assertEquals(25f, step1.step, 0.001f)
        assertTrue("İkinci adım ilkinden hızlı olmalı", step1.step > step0.step)

        // 800 ms sonra (t = 1800 ms) -> tam azami hız (40)
        currentTime = 1800L
        val step2 = controller.move(MoveDirection.RIGHT, timeMs = currentTime)
        assertEquals(40f, step2.step, 0.001f)

        // 1200 ms sonra (t = 2200 ms) -> azami hızda kalır (40)
        currentTime = 2200L
        val step3 = controller.move(MoveDirection.RIGHT, timeMs = currentTime)
        assertEquals(40f, step3.step, 0.001f)
    }

    @Test
    fun acceleration_resetsOnDirectionChangeOrPause_K1() {
        var currentTime = 1000L
        val controller = WebCursorController(
            baseStep = 10f,
            maxStep = 40f,
            accelerationDurationMs = 800L,
            clock = { currentTime }
        )
        controller.setViewport(2000f, 2000f)
        controller.setPosition(100f, 100f)

        // Hızlandır
        controller.move(MoveDirection.RIGHT, timeMs = 1000L)
        controller.move(MoveDirection.RIGHT, timeMs = 1800L)

        // Yön değiştirince taban hıza dönmeli
        val downStep = controller.move(MoveDirection.DOWN, timeMs = 1900L)
        assertEquals(10f, downStep.step, 0.001f)

        // Hızlandır
        controller.move(MoveDirection.DOWN, timeMs = 2700L)

        // Uzun süre tuşa basılmayınca (> HOLD_TIMEOUT_MS) sıfırlanmalı
        val afterPause = controller.move(MoveDirection.DOWN, timeMs = 3500L)
        assertEquals(10f, afterPause.step, 0.001f)
    }

    @Test
    fun screenBoundsClipping_doesNotExitViewport_K1() {
        val controller = WebCursorController(baseStep = 50f)
        controller.setViewport(500f, 400f)

        // Sol kenarı aşmaya çalış
        controller.setPosition(20f, 200f)
        val rLeft = controller.move(MoveDirection.LEFT)
        assertEquals(0f, rLeft.x, 0.001f)
        assertEquals(0f, controller.x, 0.001f)

        // Üst kenarı aşmaya çalış
        controller.setPosition(200f, 20f)
        val rUp = controller.move(MoveDirection.UP)
        assertEquals(0f, rUp.y, 0.001f)
        assertEquals(0f, controller.y, 0.001f)

        // Sağ kenarı aşmaya çalış
        controller.setPosition(480f, 200f)
        val rRight = controller.move(MoveDirection.RIGHT)
        assertEquals(500f, rRight.x, 0.001f)
        assertEquals(500f, controller.x, 0.001f)

        // Alt kenarı aşmaya çalış
        controller.setPosition(200f, 380f)
        val rDown = controller.move(MoveDirection.DOWN)
        assertEquals(400f, rDown.y, 0.001f)
        assertEquals(400f, controller.y, 0.001f)
    }

    @Test
    fun edgeScroll_producesScrollDeltaWhenAtBoundary_K3() {
        val controller = WebCursorController(baseStep = 20f)
        controller.setViewport(500f, 400f)

        // Alt kenara dayanmış durumda aşağı hareket -> scrollDy pozitif
        controller.setPosition(250f, 400f)
        val downResult = controller.move(MoveDirection.DOWN)
        assertEquals(400f, downResult.y, 0.001f)
        assertEquals(20, downResult.scrollDy)
        assertEquals(0, downResult.scrollDx)

        // Üst kenara dayanmış durumda yukarı hareket -> scrollDy negatif
        controller.setPosition(250f, 0f)
        val upResult = controller.move(MoveDirection.UP)
        assertEquals(0f, upResult.y, 0.001f)
        assertEquals(-20, upResult.scrollDy)
        assertEquals(0, upResult.scrollDx)

        // Sağ kenara dayanmış durumda sağa hareket -> scrollDx pozitif
        controller.setPosition(500f, 200f)
        val rightResult = controller.move(MoveDirection.RIGHT)
        assertEquals(500f, rightResult.x, 0.001f)
        assertEquals(20, rightResult.scrollDx)
        assertEquals(0, rightResult.scrollDy)

        // Sol kenara dayanmış durumda sola hareket -> scrollDx negatif
        controller.setPosition(0f, 200f)
        val leftResult = controller.move(MoveDirection.LEFT)
        assertEquals(0f, leftResult.x, 0.001f)
        assertEquals(-20, leftResult.scrollDx)
        assertEquals(0, leftResult.scrollDy)

        // Kenardan içeri doğru hareket -> kaydırma yok (0, 0)
        val backResult = controller.move(MoveDirection.RIGHT)
        assertEquals(20f, backResult.x, 0.001f)
        assertEquals(0, backResult.scrollDx)
        assertEquals(0, backResult.scrollDy)
    }

    @Test
    fun inactivityFade_after3Seconds_K8() {
        var currentTime = 1000L
        val controller = WebCursorController(
            inactivityTimeoutMs = 3000L,
            clock = { currentTime }
        )
        controller.setViewport(500f, 500f)

        // Başlangıçta soluk değil
        assertFalse(controller.isFaded)

        // 2.9 sn sonra henüz soluk değil
        currentTime = 3900L
        assertFalse(controller.isFaded)
        assertFalse(controller.updateInactivity(currentTime))

        // 3.0 sn sonra soluklaşır
        currentTime = 4000L
        assertTrue(controller.isFaded)
        assertTrue(controller.updateInactivity(currentTime))

        // Kullanıcı tuşa basınca (hareket veya etkileşim) hemen uyanır
        currentTime = 4500L
        controller.onUserInteraction(currentTime)
        assertFalse(controller.isFaded)

        // Tekrar 3 sn geçince soluklaşır
        currentTime = 7500L
        assertTrue(controller.isFaded)
    }

    @Test
    fun hideAndShow_togglesVisibilityAndResetsFade() {
        var currentTime = 1000L
        val controller = WebCursorController(
            inactivityTimeoutMs = 3000L,
            clock = { currentTime }
        )
        controller.setViewport(500f, 500f)
        assertTrue(controller.isVisible)

        // Üst çubuğa geçince gizle
        controller.hide()
        assertFalse(controller.isVisible)

        // Zaman geçse de çubuktan dönünce görünür ve uyanık olmalı
        currentTime = 5000L
        controller.show(currentTime)
        assertTrue(controller.isVisible)
        assertFalse(controller.isFaded)
    }

    @Test
    fun onUserInteraction_notifiesOnChangeWhenWakingFromFadedState() {
        var currentTime = 1000L
        val controller = WebCursorController(
            inactivityTimeoutMs = 3000L,
            clock = { currentTime }
        )
        controller.setViewport(500f, 500f)

        // 3 sn geçtikten sonra soluklaşır
        currentTime = 4001L
        assertTrue(controller.isFaded)

        var notified = false
        controller.onChange = { notified = true }

        // Etkileşim olunca uyanır ve dinleyiciye haber verir
        controller.onUserInteraction(currentTime)
        assertFalse(controller.isFaded)
        assertTrue(notified)
    }

    @Test
    fun viewportResize_clampsExistingPositionWithoutResettingToCenter() {
        val controller = WebCursorController()
        controller.setViewport(1000f, 1000f)
        controller.setPosition(800f, 700f)

        // Viewport küçülürse (ör. ekran klavyesi açıldı veya yön değişti)
        controller.setViewport(600f, 500f)
        assertEquals(600f, controller.x, 0.001f)
        assertEquals(500f, controller.y, 0.001f)

        // resetToCenter true verilirse merkeze gider
        controller.setViewport(600f, 500f, resetToCenter = true)
        assertEquals(300f, controller.x, 0.001f)
        assertEquals(250f, controller.y, 0.001f)
    }

    @Test
    fun zeroOrNegativeViewport_safelyHandled() {
        val controller = WebCursorController()
        controller.setViewport(-10f, -50f)
        assertEquals(0f, controller.viewportWidth, 0.001f)
        assertEquals(0f, controller.viewportHeight, 0.001f)
        assertEquals(0f, controller.x, 0.001f)
        assertEquals(0f, controller.y, 0.001f)
    }
}
