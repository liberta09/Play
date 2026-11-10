package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstimatedEndTimeTest {

    @Test
    fun calculatesEndTimeAccountingForPlaybackSpeedAndClamping() {
        val now = 1_700_000_000_000L
        val ninetyMinutesMs = 90L * 60_000L

        // Normal hız 1.0x -> 90 dk kalan
        assertEquals(
            now + 90L * 60_000L,
            EstimatedEndTime.calculateEndEpochMs(now, positionMs = 0L, durationMs = ninetyMinutesMs, speed = 1.0f, isLive = false),
        )

        // 1.5x hız -> 90 dk video kalanı gerçek zamanda 60 dk sürer
        assertEquals(
            now + 60L * 60_000L,
            EstimatedEndTime.calculateEndEpochMs(now, positionMs = 0L, durationMs = ninetyMinutesMs, speed = 1.5f, isLive = false),
        )

        // Yarıda 1.5x hız -> 30 dk video kalanı 20 dk gerçek zaman sürer
        assertEquals(
            now + 20L * 60_000L,
            EstimatedEndTime.calculateEndEpochMs(now, positionMs = 60L * 60_000L, durationMs = ninetyMinutesMs, speed = 1.5f, isLive = false),
        )
    }

    @Test
    fun returnsNullForLiveStreamsOrUnknownDuration() {
        val now = 1_700_000_000_000L
        assertNull(EstimatedEndTime.calculateEndEpochMs(now, positionMs = 1_000L, durationMs = 0L, speed = 1.0f, isLive = false))
        assertNull(EstimatedEndTime.calculateEndEpochMs(now, positionMs = 1_000L, durationMs = -1L, speed = 1.0f, isLive = false))
        assertNull(EstimatedEndTime.calculateEndEpochMs(now, positionMs = 1_000L, durationMs = 60_000L, speed = 1.0f, isLive = true))
        assertNull(EstimatedEndTime.calculateEndEpochMs(now, positionMs = 1_000L, durationMs = 60_000L, speed = 0f, isLive = false))
    }
}
