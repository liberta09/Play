package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreplayArtworkRulesTest {

    @Test
    fun returnsNullWhenDisabled() {
        assertNull(
            PreplayArtworkRules.selectPath(
                enabled = false,
                isLive = false,
                backdropPath = "/back.jpg",
                posterPath = "/poster.jpg",
            ),
        )
    }

    @Test
    fun movieOrSeriesPrefersBackdropAndFallsBackToPoster() {
        assertEquals(
            "/back.jpg",
            PreplayArtworkRules.selectPath(
                enabled = true,
                isLive = false,
                backdropPath = "/back.jpg",
                posterPath = "/poster.jpg",
            ),
        )
        assertEquals(
            "/poster.jpg",
            PreplayArtworkRules.selectPath(
                enabled = true,
                isLive = false,
                backdropPath = "   ",
                posterPath = "/poster.jpg",
            ),
        )
    }

    @Test
    fun liveTvPrefersPosterLogoUrlAndFallsBackToBackdrop() {
        assertEquals(
            "https://cdn.example/trt1.png",
            PreplayArtworkRules.selectPath(
                enabled = true,
                isLive = true,
                backdropPath = "/back.jpg",
                posterPath = "https://cdn.example/trt1.png",
            ),
        )
    }

    @Test
    fun shouldShowOnlyUntilFirstFrameWithoutErrorOrTorrentOverlay() {
        assertTrue(
            PreplayArtworkRules.shouldShow(
                enabled = true,
                artworkUrl = "https://image.tmdb.org/t/p/w1280/back.jpg",
                firstFrameRendered = false,
                showTorrentOverlay = false,
                hasError = false,
            ),
        )
        assertFalse(
            PreplayArtworkRules.shouldShow(
                enabled = false,
                artworkUrl = "https://image.tmdb.org/t/p/w1280/back.jpg",
                firstFrameRendered = false,
                showTorrentOverlay = false,
                hasError = false,
            ),
        )
        assertFalse(
            PreplayArtworkRules.shouldShow(
                enabled = true,
                artworkUrl = "https://image.tmdb.org/t/p/w1280/back.jpg",
                firstFrameRendered = true,
                showTorrentOverlay = false,
                hasError = false,
            ),
        )
        assertFalse(
            PreplayArtworkRules.shouldShow(
                enabled = true,
                artworkUrl = "https://image.tmdb.org/t/p/w1280/back.jpg",
                firstFrameRendered = false,
                showTorrentOverlay = true,
                hasError = false,
            ),
        )
        assertFalse(
            PreplayArtworkRules.shouldShow(
                enabled = true,
                artworkUrl = "https://image.tmdb.org/t/p/w1280/back.jpg",
                firstFrameRendered = false,
                showTorrentOverlay = false,
                hasError = true,
            ),
        )
    }
}
