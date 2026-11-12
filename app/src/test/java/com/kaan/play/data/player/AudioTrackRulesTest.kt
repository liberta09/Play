package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackRulesTest {

    @Test
    fun nextTrackCyclesAndWrapsAroundSupportedTracks() {
        val single = listOf(AudioTrackInfo(0, "en", "English", isSelected = true))
        assertNull(AudioTrackRules.nextTrack(single))

        val tracks = listOf(
            AudioTrackInfo(0, "en", "English", isSelected = true),
            AudioTrackInfo(-1, "tr", "DTS", isSelected = false, isSupported = false),
            AudioTrackInfo(1, "tr", "Türkçe", isSelected = false),
            AudioTrackInfo(2, "de", "Deutsch", isSelected = false),
        )
        assertEquals(1, AudioTrackRules.nextTrack(tracks)?.index)

        val secondSelected = tracks.map { it.copy(isSelected = it.index == 1) }
        assertEquals(2, AudioTrackRules.nextTrack(secondSelected)?.index)

        val lastSelected = tracks.map { it.copy(isSelected = it.index == 2) }
        assertEquals(0, AudioTrackRules.nextTrack(lastSelected)?.index)
    }

    @Test
    fun detectsMissingLanguageMetadata() {
        assertFalse(
            AudioTrackRules.hasAnyLanguageInfo(
                listOf(
                    AudioTrackInfo(0, null, "Track 1", isSelected = true),
                    AudioTrackInfo(1, "und", null, isSelected = false),
                ),
            ),
        )
        assertTrue(
            AudioTrackRules.hasAnyLanguageInfo(
                listOf(
                    AudioTrackInfo(0, "und", null, isSelected = true),
                    AudioTrackInfo(1, "tur", null, isSelected = false),
                ),
            ),
        )
    }

    @Test
    fun displayNameHidesTechnicalDetails() {
        val named = AudioTrackInfo(0, "tr", "EAC3 5.1", isSelected = true, mime = "audio/eac3", channelCount = 6)
        assertEquals(
            "Türkçe",
            AudioTrackRules.displayNameForUi(named, 1, { "Parça $it" }, { "Türkçe" }),
        )

        val techOnly = AudioTrackInfo(1, "und", "Stereo", isSelected = false, mime = "audio/aac", channelCount = 2)
        assertEquals(
            "Parça 2",
            AudioTrackRules.displayNameForUi(techOnly, 2, { "Parça $it" }, { it }),
        )

        val humanLabel = AudioTrackInfo(2, null, "Yönetmen Yorumu", isSelected = false)
        assertEquals(
            "Yönetmen Yorumu",
            AudioTrackRules.displayNameForUi(humanLabel, 3, { "Parça $it" }, { it }),
        )
    }
}
