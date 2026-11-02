package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StallWatchdogRulesTest {

    @Test
    fun elapsedLessThanTimeoutReturnsBekle() {
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 1000L,
            currentBufferedPositionMs = 1000L,
            initialPositionMs = 500L,
            currentPositionMs = 500L,
            elapsedMs = 7999L,
            reprepareCount = 0,
        )
        assertEquals(StallWatchdogRules.Decision.BEKLE, decision)
    }

    @Test
    fun bufferProgressingReturnsBekleEvenAfterTimeout() {
        // Ağ yavaş olsa da tampon ilerliyor: DOKUNMA, bekle
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 1000L,
            currentBufferedPositionMs = 2500L,
            initialPositionMs = 500L,
            currentPositionMs = 500L,
            elapsedMs = 8500L,
            reprepareCount = 0,
        )
        assertEquals(StallWatchdogRules.Decision.BEKLE, decision)
    }

    @Test
    fun positionProgressingReturnsBekleEvenAfterTimeout() {
        // Oynatma ilerliyor: bekle
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 5000L,
            currentBufferedPositionMs = 5000L,
            initialPositionMs = 1000L,
            currentPositionMs = 1500L,
            elapsedMs = 8500L,
            reprepareCount = 0,
        )
        assertEquals(StallWatchdogRules.Decision.BEKLE, decision)
    }

    @Test
    fun trueStallWithoutDataProgressTriggersYenidenHazirla() {
        // Tampon ve konum 8 sn boyunca hiç ilerlemedi: yeniden hazırla
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 1000L,
            currentBufferedPositionMs = 1000L,
            initialPositionMs = 500L,
            currentPositionMs = 500L,
            elapsedMs = 8000L,
            reprepareCount = 0,
        )
        assertEquals(StallWatchdogRules.Decision.YENIDEN_HAZIRLA, decision)
    }

    @Test
    fun reprepareLimitReachedGivesUp() {
        // Yükleme/sarma başına en çok 1 yeniden hazırlama: sonra vazgeç
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 1000L,
            currentBufferedPositionMs = 1000L,
            initialPositionMs = 500L,
            currentPositionMs = 500L,
            elapsedMs = 8500L,
            reprepareCount = 1,
        )
        assertEquals(StallWatchdogRules.Decision.VAZGEC, decision)
    }

    @Test
    fun higherReprepareCountAlwaysGivesUp() {
        val decision = StallWatchdogRules.decide(
            initialBufferedPositionMs = 1000L,
            currentBufferedPositionMs = 2000L,
            initialPositionMs = 500L,
            currentPositionMs = 500L,
            elapsedMs = 10000L,
            reprepareCount = 2,
        )
        assertEquals(StallWatchdogRules.Decision.VAZGEC, decision)
    }
}
