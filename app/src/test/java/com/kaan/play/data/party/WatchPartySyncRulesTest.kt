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
        val packet1 = PartyControlPacket(seq = 1, sentAtMs = 100_000L, playing = true, positionMs = 5000L)
        val packet2 = PartyControlPacket(seq = 2, sentAtMs = 100_000L, playing = true, positionMs = 6000L)
        val latePacket1 = PartyControlPacket(seq = 1, sentAtMs = 100_000L, playing = true, positionMs = 5500L)

        // Initial state accepts seq=1
        assertTrue(syncRules.shouldApplyPacket(packet1))
        syncRules.evaluateSync(localCurrentPositionMs = 0L, packet = packet1, nowMs = 100_000L)
        assertEquals(1L, syncRules.lastAppliedSeq)

        // Newer packet seq=2 is accepted
        assertTrue(syncRules.shouldApplyPacket(packet2))
        syncRules.evaluateSync(localCurrentPositionMs = 5000L, packet = packet2, nowMs = 102_000L)
        assertEquals(2L, syncRules.lastAppliedSeq)

        // Late packet seq=1 or equal seq=2 is rejected
        assertFalse(syncRules.shouldApplyPacket(latePacket1))
        assertFalse(syncRules.shouldApplyPacket(packet2))
        val result = syncRules.evaluateSync(localCurrentPositionMs = 6000L, packet = latePacket1, nowMs = 103_000L)
        assertNull(result)
    }

    @Test
    fun `latency compensation clamps between 0 and 3000ms`() {
        val now = 100_000L

        // Normal 500ms latency
        val normalLatency = syncRules.computeLatency(sentAtMs = 99_500L, nowMs = now)
        assertEquals(500L, normalLatency)

        // High 5000ms latency -> clamped to 3000ms
        val highLatency = syncRules.computeLatency(sentAtMs = 95_000L, nowMs = now)
        assertEquals(3000L, highLatency)

        // Clock skew in future (sentAtMs > now) -> clamped to 0ms
        val futureLatency = syncRules.computeLatency(sentAtMs = 105_000L, nowMs = now)
        assertEquals(0L, futureLatency)
    }

    @Test
    fun `computeTargetPosition applies latency compensation when playing`() {
        val now = 100_000L
        val packetPlaying = PartyControlPacket(
            seq = 1,
            sentAtMs = 99_000L, // 1000ms latency
            playing = true,
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
        val packetPaused = PartyControlPacket(
            seq = 1,
            sentAtMs = 98_000L, // 2000ms latency
            playing = false,
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
        val packet15x = PartyControlPacket(
            seq = 1,
            sentAtMs = 99_000L, // 1000ms latency
            playing = true,
            positionMs = 10_000L,
            speed = 1.5f,
        )

        val target = syncRules.computeTargetPosition(packet15x, nowMs = now)
        // 10_000 + (1000 * 1.5) = 11_500
        assertEquals(11_500L, target)
    }

    @Test
    fun `evaluateSync triggers seek when drift exceeds 2000ms and respects 1000ms throttle`() {
        val now = 100_000L
        val packet1 = PartyControlPacket(
            seq = 1,
            sentAtMs = now,
            playing = true,
            positionMs = 10_000L,
        )

        // Drift = 3000ms (> 2000ms) -> should seek
        val action1 = syncRules.evaluateSync(localCurrentPositionMs = 7_000L, packet = packet1, nowMs = now)
        assertNotNull(action1)
        assertTrue(action1!!.shouldSeek)
        assertEquals(10_000L, action1.targetPositionMs)

        // Packet 2 sent 500ms later (< MIN_SEEK_INTERVAL_MS = 1000ms) with large drift
        val packet2 = PartyControlPacket(
            seq = 2,
            sentAtMs = now + 500L,
            playing = true,
            positionMs = 15_000L,
        )
        val action2 = syncRules.evaluateSync(localCurrentPositionMs = 10_000L, packet = packet2, nowMs = now + 500L)
        assertNotNull(action2)
        // Throttled because only 500ms passed since last seek!
        assertFalse(action2!!.shouldSeek)

        // Packet 3 sent 1200ms after last seek (> 1000ms) with drift > 2000ms
        val packet3 = PartyControlPacket(
            seq = 3,
            sentAtMs = now + 1200L,
            playing = true,
            positionMs = 15_000L,
        )
        val action3 = syncRules.evaluateSync(localCurrentPositionMs = 10_000L, packet = packet3, nowMs = now + 1200L)
        assertNotNull(action3)
        assertTrue(action3!!.shouldSeek)
    }

    @Test
    fun `evaluateSync does not seek when drift is within 2000ms`() {
        val now = 100_000L
        val packet = PartyControlPacket(
            seq = 1,
            sentAtMs = now,
            playing = true,
            positionMs = 10_000L,
        )

        // Local = 8_500ms, drift = 1_500ms (<= 2000ms) -> should NOT seek
        val action = syncRules.evaluateSync(localCurrentPositionMs = 8_500L, packet = packet, nowMs = now)
        assertNotNull(action)
        assertFalse(action!!.shouldSeek)
        assertEquals(10_000L, action.targetPositionMs)
    }

    @Test
    fun `checkContentTransition triggers when content differs`() {
        val currentTmdbId = 100
        val currentSeason = 1
        val currentEpisode = 1

        val sameContent = PartyContentPacket(
            tmdbId = 100,
            mediaType = "tv",
            season = 1,
            episode = 1,
            title = "Dizi",
        )
        assertNull(syncRules.checkContentTransition(currentTmdbId, currentSeason, currentEpisode, sameContent))

        val nextEpisode = PartyContentPacket(
            tmdbId = 100,
            mediaType = "tv",
            season = 1,
            episode = 2,
            title = "Dizi",
        )
        val transition = syncRules.checkContentTransition(currentTmdbId, currentSeason, currentEpisode, nextEpisode)
        assertNotNull(transition)
        assertEquals(100, transition!!.tmdbId)
        assertEquals(1, transition.season)
        assertEquals(2, transition.episode)
    }

    @Test
    fun `createControlPacket increments sequence and creates valid packet`() {
        val now = 100_000L
        val p1 = syncRules.createControlPacket(positionMs = 5000L, isPlaying = true, speed = 1.0f, nowMs = now)
        val p2 = syncRules.createControlPacket(positionMs = 6000L, isPlaying = false, speed = 1.25f, nowMs = now + 1000L)

        assertEquals(1L, p1.seq)
        assertEquals(5000L, p1.positionMs)
        assertTrue(p1.playing)
        assertEquals(now, p1.sentAtMs)

        assertEquals(2L, p2.seq)
        assertEquals(6000L, p2.positionMs)
        assertFalse(p2.playing)
        assertEquals(1.25f, p2.speed, 0.001f)
    }
}
