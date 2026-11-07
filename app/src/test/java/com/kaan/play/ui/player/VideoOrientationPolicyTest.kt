package com.kaan.play.ui.player

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class VideoOrientationPolicyTest {
    @Test fun renderedVerticalAndHorizontalGeometryAreDistinguished() {
        assertEquals(true, VideoOrientationPolicy.isPortrait(1080, 1920))
        assertEquals(false, VideoOrientationPolicy.isPortrait(1920, 1080))
        assertEquals(false, VideoOrientationPolicy.isPortrait(1080, 1080))
    }
    @Test fun pixelAspectAndPendingRotationAreAppliedExactlyOnce() {
        assertEquals(false, VideoOrientationPolicy.isPortrait(720, 1080, 2f))
        assertEquals(true, VideoOrientationPolicy.isPortrait(1920, 1080, .5f))
        assertEquals(true, VideoOrientationPolicy.isPortrait(1920, 1080, 1f, 90))
        assertEquals(false, VideoOrientationPolicy.isPortrait(1080, 1920, 1f, 270))
    }
    @Test fun unknownGeometryDoesNotFlipAnExistingAutomaticOrientation() {
        assertNull(VideoOrientationPolicy.isPortrait(0, 0))
        assertNull(VideoOrientationPolicy.isPortrait(100, 200, Float.NaN))
        assertTrue(VideoOrientationPolicy.resolve(true, true, false, true, 0, 0))
    }
    @Test fun manualLandscapeChoiceWinsOverVerticalMetadata() {
        assertFalse(VideoOrientationPolicy.resolve(true, true, true, false, 1080, 1920))
        assertTrue(VideoOrientationPolicy.resolve(true, false, true, true, 1920, 1080))
    }
    @Test fun disabledSettingAndTvStayLandscapeWithoutManualPhoneChoice() {
        assertFalse(VideoOrientationPolicy.resolve(true, false, false, true, 1080, 1920))
        assertFalse(VideoOrientationPolicy.resolve(false, true, true, true, 1080, 1920))
    }
    @Test fun automaticPortraitIsNotMistakenForAManualChoiceInNextSession() {
        val automatic = PlaySession.testVideo("https://example.invalid/video.mp4")
            .copy(portraitMode = true, orientationManuallyChosen = false, fromAutoNext = true)
        val decoded = PlayJson.decodeFromString(PlaySession.serializer(), PlayJson.encodeToString(PlaySession.serializer(), automatic))
        assertFalse(VideoOrientationPolicy.wasManuallyChosen(decoded.orientationManuallyChosen, decoded.portraitMode))
        assertFalse(VideoOrientationPolicy.resolve(true, true, false, decoded.portraitMode, 1920, 1080))
        assertTrue(decoded.fromAutoNext)
    }
    @Test fun legacyExplicitPortraitAndNewManualLandscapeRemainExplicit() {
        val encoded = PlayJson.encodeToString(PlaySession.serializer(), PlaySession.testVideo("https://example.invalid/video.mp4").copy(portraitMode = true))
        val old = JsonObject(PlayJson.parseToJsonElement(encoded).jsonObject.filterKeys { it != "orientationManuallyChosen" })
        val legacy = PlayJson.decodeFromString(PlaySession.serializer(), old.toString())
        assertTrue(VideoOrientationPolicy.wasManuallyChosen(legacy.orientationManuallyChosen, legacy.portraitMode))
        assertTrue(VideoOrientationPolicy.wasManuallyChosen(true, false))
    }
    @Test fun oldPositionalPortraitArgumentKeepsItsMeaning() {
        val sample = PlaySession.testVideo("https://example.invalid/video.mp4")
        val legacyCall = PlaySession(sample.target, sample.stream, 0L, false, null, emptyList(), false, false, false, true)
        assertTrue(legacyCall.portraitMode)
        assertNull(legacyCall.orientationManuallyChosen)
        assertTrue(VideoOrientationPolicy.wasManuallyChosen(legacyCall.orientationManuallyChosen, legacyCall.portraitMode))
    }

}
