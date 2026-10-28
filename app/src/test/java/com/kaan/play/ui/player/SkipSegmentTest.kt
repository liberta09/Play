package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentTest {
    private val episodeDuration = 45 * 60 * 1_000L

    @Test
    fun noDurationOnlyGuessIsShownForMoviesOrEpisodes() {
        assertTrue(SkipSegmentDetector.detect(MediaType.Movie, episodeDuration).isEmpty())
        assertTrue(SkipSegmentDetector.detect(MediaType.Tv, episodeDuration).isEmpty())
        assertTrue(SkipSegmentDetector.detect(MediaType.Tv, episodeDuration, addonType = "tv").isEmpty())
        assertTrue(SkipSegmentDetector.detect(MediaType.Tv, episodeDuration, addonType = "channel").isEmpty())
    }

    @Test
    fun vagueChapterNamesDoNotCreateSkipButtons() {
        val segments = SkipSegmentDetector.detect(
            MediaType.Tv,
            episodeDuration,
            listOf(
                SkipMarker("Opening scene", 20_000L, 80_000L),
                SkipMarker("Ending", episodeDuration - 90_000L, episodeDuration),
                SkipMarker("Jenerik", episodeDuration - 90_000L, episodeDuration),
            ),
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun explicitRecapIntroAndOutroMarkersAreAcceptedWhenTheirRangesArePlausible() {
        val segments = SkipSegmentDetector.detect(
            MediaType.Tv,
            episodeDuration,
            listOf(
                SkipMarker("Previously on", 0L, 55_000L),
                SkipMarker("Opening credits", 55_000L, 130_000L),
                SkipMarker("End credits", episodeDuration - 95_000L, episodeDuration),
            ),
        )
        assertEquals(
            listOf(SkipSegmentKind.Recap, SkipSegmentKind.Intro, SkipSegmentKind.Outro),
            segments.map { it.kind },
        )
    }

    @Test
    fun explicitChapterMarkersCanAlsoEnableMovieSkipButtons() {
        val segments = SkipSegmentDetector.detect(
            MediaType.Movie,
            episodeDuration,
            listOf(SkipMarker("Opening theme", 0L, 110_000L)),
        )
        assertEquals(listOf(SkipSegment(SkipSegmentKind.Intro, 0L, 110_000L)), segments)
    }

    @Test
    fun previewAndOpenEndedCreditsMarkersAreRecognizedAndMayBeActiveTogether() {
        val credits = SkipSegment(SkipSegmentKind.Outro, episodeDuration - 120_000L, episodeDuration, openEndedEnd = true)
        val preview = SkipSegment(SkipSegmentKind.Preview, episodeDuration - 60_000L, episodeDuration)
        val active = SkipSegmentDetector.activeSegments(
            listOf(credits, preview),
            positionMs = episodeDuration - 30_000L,
        )
        assertEquals(listOf(SkipSegmentKind.Outro, SkipSegmentKind.Preview), active.map { it.kind })
        assertTrue(active.first().openEndedEnd)
    }

    @Test
    fun verifiedThreeSecondSegmentsAndPreviewChaptersAreAccepted() {
        val segments = SkipSegmentDetector.detect(
            MediaType.Movie,
            durationMs = 9 * 60_000L,
            markers = listOf(
                SkipMarker("Intro", 0L, 3_000L),
                SkipMarker("Next episode preview", 8 * 60_000L, 8 * 60_000L + 4_000L),
            ),
        )
        assertEquals(listOf(SkipSegmentKind.Intro, SkipSegmentKind.Preview), segments.map { it.kind })
    }

    @Test
    fun automaticSkipIsOptInAndEachSegmentIsEligibleOnlyOncePerSession() {
        val intro = SkipSegment(SkipSegmentKind.Intro, 0L, 30_000L)
        assertNull(SkipSegmentDetector.autoSkipCandidate(listOf(intro), enabled = false, playing = true, attemptedThisSession = emptySet()))
        assertNull(SkipSegmentDetector.autoSkipCandidate(listOf(intro), enabled = true, playing = false, attemptedThisSession = emptySet()))
        assertEquals(intro, SkipSegmentDetector.autoSkipCandidate(listOf(intro), enabled = true, playing = true, attemptedThisSession = emptySet()))
        assertNull(SkipSegmentDetector.autoSkipCandidate(listOf(intro), enabled = true, playing = true, attemptedThisSession = setOf(intro.key)))
    }

    @Test
    fun outOfRangeOversizedAndMisplacedMarkersAreRejectedRatherThanClamped() {
        val segments = SkipSegmentDetector.detect(
            MediaType.Tv,
            episodeDuration,
            listOf(
                SkipMarker("Intro", 0L, episodeDuration + 1L),
                SkipMarker("Opening credits", 0L, 10 * 60 * 1_000L),
                SkipMarker("Previously on", episodeDuration / 2L, episodeDuration / 2L + 30_000L),
                SkipMarker("End credits", episodeDuration / 3L, episodeDuration),
            ),
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun aCompletedIntroIsNotOfferedDuringResumeSeekStartup() {
        val intro = SkipSegment(SkipSegmentKind.Intro, 0L, 120_000L)
        assertNull(
            SkipSegmentDetector.active(
                segments = listOf(intro),
                positionMs = 0L,
                resumePositionMs = 20 * 60 * 1_000L,
            ),
        )
    }

    @Test
    fun introStillAppearsWhenResumePositionIsInsideIt() {
        val intro = SkipSegment(SkipSegmentKind.Intro, 0L, 120_000L)
        assertNotNull(
            SkipSegmentDetector.active(
                segments = listOf(intro),
                positionMs = 60_000L,
                resumePositionMs = 60_000L,
            ),
        )
    }
}
