package com.kaan.play.data.party

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchPartySyncRulesTest {

    private lateinit var syncRules: WatchPartySyncRules
    private var currentTime: Long = 100_000L

    @Before
    fun setUp() {
        currentTime = 100_000L
        syncRules = WatchPartySyncRules(timeProvider = { currentTime })
    }

    @Test
    fun `shouldApplyPacket accepts newer packets and drops older or equal packets`() {
        val packet1 = PartySyncPacket(seq = 1, sentAt = 100_000L, isPlaying = true, positionMs = 5000L)
        val packet2 = PartySyncPacket(seq = 2, sentAt = 100_000L, isPlaying = true, positionMs = 6000L)
        val latePacket1 = PartySyncPacket(seq = 1, sentAt = 100_000L, isPlaying = true, positionMs = 5500L)

        // Initial state accepts seq=1
        assertTrue(syncRules.shouldApplyPacket(packet1))
        syncRules.evaluateSync(localCurrentPositionMs = 0L, packet = packet1, nowMs = 100_000L)
        assertEquals(1L, syncRules.lastAppliedSeq)

        // Newer packet seq=2 is accepted
        assertTrue(syncRules.shouldApplyPacket(packet2))
        syncRules.evaluateSync(localCurrentPositionMs = 5000L, packet = packet2, nowMs = 100_000L)
        assertEquals(2L, syncRules.lastAppliedSeq)

        // Late packet seq=1 or seq=2 is rejected
        assertFalse(syncRules.shouldApplyPacket(latePacket1))
        assertFalse(syncRules.shouldApplyPacket(packet2))
        val result = syncRules.evaluateSync(localCurrentPositionMs = 6000L, packet = latePacket1, nowMs = 100_000L)
        assertNull(result)
    }

    @Test
    fun `latency compensation clamps between 0 and 3000ms`() {
        val now = 100_000L

        // Normal 500ms latency
        val normalLatency = syncRules.computeLatency(sentAt = 99_500L, nowMs = now)
        assertEquals(500L, normalLatency)

        // High 5000ms latency -> clamped to 3000ms
        val highLatency = syncRules.computeLatency(sentAt = 95_000L, nowMs = now)
        assertEquals(3000L, highLatency)

        // Clock skew in future (sentAt > now) -> clamped to 0ms
        val futureLatency = syncRules.computeLatency(sentAt = 105_000L, nowMs = now)
        assertEquals(0L, futureLatency)
    }

    @Test
    fun `computeTargetPosition applies latency compensation when playing`() {
        val now = 100_000L
        val packetPlaying = PartySyncPacket(
            seq = 1,
            sentAt = 99_000L, // 1000ms latency
            isPlaying = true,
            positionMs = 10_000L,
            speed = 1.0f,
        )

        val target = syncRules.computeTargetPosition(packetPlaying, nowMs = now)
        // 10_000 + (1000 * 1.0) = 11_000
        assertEquals(11_000L, target)
    }

    @Test
    fun `computeTargetPosition does not compensate latency when paused`() {
        val now = 100_000L
        val packetPaused = PartySyncPacket(
            seq = 1,
            sentAt = 98_000L, // 2000ms latency
            isPlaying = false,
            positionMs = 10_000L,
            speed = 1.0f,
        )

        val target = syncRules.computeTargetPosition(packetPaused, nowMs = now)
        // Latency not added when paused
        assertEquals(10_000L, target)
    }

    @Test
    fun `computeTargetPosition accounts for playback speed`() {
        val now = 100_000L
        val packet15x = PartySyncPacket(
            seq = 1,
            sentAt = 99_000L, // 1000ms latency
            isPlaying = true,
            positionMs = 10_000L,
            speed = 1.5f,
        )

        val target = syncRules.computeTargetPosition(packet15x, nowMs = now)
        // 10_000 + (1000 * 1.5) = 11_500
        assertEquals(11_500L, target)
    }

    @Test
    fun `evaluateSync triggers seek when drift exceeds 2000ms`() {
        val now = 100_000L
        val packet = PartySyncPacket(
            seq = 1,
            sentAt = now, // 0 latency
            isPlaying = true,
            positionMs = 10_000L,
        )

        // Local position = 7_000ms, drift = 3_000ms (> 2000ms) -> should seek
        val actionSeek = syncRules.evaluateSync(localCurrentPositionMs = 7_000L, packet = packet, nowMs = now)
        assertNotNull(actionSeek)
        assertTrue(actionSeek!!.shouldSeek)
        assertEquals(10_000L, actionSeek.targetPositionMs)

        // Reset and test drift <= 2000ms
        syncRules.reset()
        // Local position = 9_000ms, drift = 1_000ms (<= 2000ms) -> should NOT seek
        val actionNoSeek = syncRules.evaluateSync(localCurrentPositionMs = 9_000L, packet = packet, nowMs = now)
        assertNotNull(actionNoSeek)
        assertFalse(actionNoSeek!!.shouldSeek)
        assertEquals(10_000L, actionNoSeek.targetPositionMs)
    }

    @Test
    fun `checkContentTransition triggers when content differs`() {
        val currentTmdbId = 100
        val currentSeason = 1
        val currentEpisode = 1

        val sameContent = PartySyncPacket(
            seq = 1,
            sentAt = 100_000L,
            isPlaying = true,
            positionMs = 5000L,
            tmdbId = 100,
            season = 1,
            episode = 1,
        )
        assertNull(syncRules.checkContentTransition(currentTmdbId, currentSeason, currentEpisode, sameContent))

        val nextEpisode = PartySyncPacket(
            seq = 2,
            sentAt = 100_000L,
            isPlaying = true,
            positionMs = 0L,
            tmdbId = 100,
            season = 1,
            episode = 2,
        )
        val transition = syncRules.checkContentTransition(currentTmdbId, currentSeason, currentEpisode, nextEpisode)
        assertNotNull(transition)
        assertEquals(100, transition!!.tmdbId)
        assertEquals(1, transition.season)
        assertEquals(2, transition.episode)
    }

    @Test
    fun `createHostPacket increments sequence and creates valid packet`() {
        val now = 100_000L
        val p1 = syncRules.createHostPacket(positionMs = 5000L, isPlaying = true, speed = 1.0f, nowMs = now)
        val p2 = syncRules.createHostPacket(positionMs = 6000L, isPlaying = false, speed = 1.25f, nowMs = now + 1000L)

        assertEquals(1L, p1.seq)
        assertEquals(5000L, p1.positionMs)
        assertTrue(p1.isPlaying)
        assertEquals(now, p1.sentAt)

        assertEquals(2L, p2.seq)
        assertEquals(6000L, p2.positionMs)
        assertFalse(p2.isPlaying)
        assertEquals(1.25f, p2.speed, 0.001f)
    }
}
