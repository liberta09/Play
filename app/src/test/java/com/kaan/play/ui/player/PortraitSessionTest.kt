package com.kaan.play.ui.player

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class PortraitSessionTest {
    private fun sample() = PlaySession.testVideo("https://example.invalid/video.mp4")

    @Test fun portraitChoiceSurvivesSessionRoundTrip() {
        val expected = sample().copy(portraitMode = true)
        val encoded = PlayJson.encodeToString(PlaySession.serializer(), expected)
        val decoded = PlayJson.decodeFromString(PlaySession.serializer(), encoded)
        assertTrue(decoded.portraitMode)
        assertEquals(expected.target, decoded.target)
        assertEquals(expected.stream, decoded.stream)
    }

    @Test fun sessionsWithoutOrientationFieldRemainLandscape() {
        val encoded = PlayJson.encodeToString(PlaySession.serializer(), sample())
        val oldFields = PlayJson.parseToJsonElement(encoded).jsonObject.filterKeys { it != "portraitMode" }
        assertFalse(PlayJson.decodeFromString(PlaySession.serializer(), JsonObject(oldFields).toString()).portraitMode)
    }

    @Test fun forwardingOrientationDoesNotChangeNextEpisodeOrResumePosition() {
        val next = sample().copy(startPositionMs = 42000, fromAutoNext = true)
        val portrait = next.copy(portraitMode = true)
        assertEquals(next.target, portrait.target); assertEquals(next.startPositionMs, portrait.startPositionMs)
        assertEquals(next.stream, portrait.stream); assertTrue(portrait.fromAutoNext)
    }
}
