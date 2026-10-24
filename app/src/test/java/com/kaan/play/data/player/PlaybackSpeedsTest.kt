package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSpeedsTest {

    @Test
    fun defaultSpeedIsOne() {
        assertEquals(1.0f, PlaybackSpeeds.DEFAULT, 0.001f)
    }

    @Test
    fun allSpeedsContainExpectedValues() {
        val expected = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        assertEquals(expected.size, PlaybackSpeeds.ALL.size)
        expected.zip(PlaybackSpeeds.ALL).forEach { (exp, act) ->
            assertEquals(exp, act, 0.001f)
        }
    }

    @Test
    fun formatOptionLabels() {
        assertEquals("0.75×", PlaybackSpeeds.format(0.75f))
        assertEquals("Normal (1×)", PlaybackSpeeds.format(1.0f, "Normal"))
        assertEquals("1.25×", PlaybackSpeeds.format(1.25f))
        assertEquals("1.5×", PlaybackSpeeds.format(1.5f))
        assertEquals("1.75×", PlaybackSpeeds.format(1.75f))
        assertEquals("2×", PlaybackSpeeds.format(2.0f))
    }

    @Test
    fun badgeLabels() {
        assertNull(PlaybackSpeeds.badgeLabel(1.0f))
        assertEquals("0.75×", PlaybackSpeeds.badgeLabel(0.75f))
        assertEquals("1.25×", PlaybackSpeeds.badgeLabel(1.25f))
        assertEquals("1.5×", PlaybackSpeeds.badgeLabel(1.5f))
        assertEquals("1.75×", PlaybackSpeeds.badgeLabel(1.75f))
        assertEquals("2×", PlaybackSpeeds.badgeLabel(2.0f))
    }

    @Test
    fun formatMultiplierWithoutTrailingZeros() {
        assertEquals("1×", PlaybackSpeeds.formatMultiplier(1.0f))
        assertEquals("2×", PlaybackSpeeds.formatMultiplier(2.0f))
        assertEquals("1.5×", PlaybackSpeeds.formatMultiplier(1.5f))
        assertEquals("0.75×", PlaybackSpeeds.formatMultiplier(0.75f))
        assertEquals("1.25×", PlaybackSpeeds.formatMultiplier(1.25f))
        assertEquals("1.75×", PlaybackSpeeds.formatMultiplier(1.75f))
    }
}
