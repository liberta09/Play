package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayabilityRulesTest {

    private fun track(mime: String, supported: Boolean, selected: Boolean = false, channels: Int = 6) =
        AudioTrackInfo(
            index = if (supported) 0 else -1,
            language = "en",
            label = null,
            isSelected = selected,
            isSupported = supported,
            mime = mime,
            channelCount = channels,
        )

    @Test
    fun `ses parcasi yoksa elenmez`() {
        assertFalse(AudioPlayabilityRules.isUnplayable(emptyList()))
    }

    @Test
    fun `ses parcalarinin hepsi desteksizse elenir`() {
        val tracks = listOf(
            track("audio/true-hd", supported = false, channels = 8),
            track("audio/vnd.dts.hd", supported = false),
        )
        assertTrue(AudioPlayabilityRules.isUnplayable(tracks))
    }

    @Test
    fun `tek desteksiz ses parcasi da elenir`() {
        assertTrue(AudioPlayabilityRules.isUnplayable(listOf(track("audio/eac3-joc", supported = false))))
    }

    @Test
    fun `en az bir parca destekliyse elenmez`() {
        val tracks = listOf(
            track("audio/true-hd", supported = false, channels = 8),
            track("audio/ac3", supported = true, selected = true),
        )
        assertFalse(AudioPlayabilityRules.isUnplayable(tracks))
    }

    @Test
    fun `tani satiri adres icermez ve bicimi gosterir`() {
        val line = AudioPlayabilityRules.describe(listOf(track("audio/true-hd", supported = false, channels = 8)))
        assertEquals("audio/true-hd 8k dil=en destek=hayır seçili=hayır", line)
        assertEquals("yok", AudioPlayabilityRules.describe(emptyList()))
    }
}
