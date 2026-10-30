package com.kaan.play.data.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentIdleRulesTest {
    private val idle = TorrentIdleRules.IDLE_MS

    @Test
    fun `oynatici varken ya da torrent acikken kapanmaz`() {
        assertFalse(TorrentIdleRules.shouldStop(users = 1, hasActive = false, idleSinceMs = 0L, now = idle * 10))
        assertFalse(TorrentIdleRules.shouldStop(users = 0, hasActive = true, idleSinceMs = 0L, now = idle * 10))
    }

    @Test
    fun `bosa dusmemis oturum kapanmaz`() {
        assertFalse(TorrentIdleRules.shouldStop(users = 0, hasActive = false, idleSinceMs = null, now = idle * 10))
    }

    @Test
    fun `bosta suresi dolmadan kapanmaz, dolunca kapanir`() {
        assertFalse(TorrentIdleRules.shouldStop(0, false, idleSinceMs = 1_000L, now = 1_000L + idle - 1))
        assertTrue(TorrentIdleRules.shouldStop(0, false, idleSinceMs = 1_000L, now = 1_000L + idle))
    }

    @Test
    fun `bos sure 10 dakikadir`() {
        assertTrue(TorrentIdleRules.IDLE_MS == 600_000L)
    }
}
