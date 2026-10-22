package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMediaRulesTest {

    private val mb = 1_000_000L

    @Test
    fun movie_smallFilesBelow20MbAreRejected() {
        // 1 MB fake source (ExoPlayer 3003 container error)
        assertFalse(SourceMediaRules.isAcceptableSize(1 * mb, isMovie = true))
        assertFalse(SourceMediaRules.isAcceptableSize(10 * mb, isMovie = true))
        assertFalse(SourceMediaRules.isAcceptableSize(19 * mb, isMovie = true))
        assertFalse(SourceMediaRules.isAcceptableSize(19_999_999L, isMovie = true))
    }

    @Test
    fun movie_normalAndLargeFilesAreAccepted() {
        assertTrue(SourceMediaRules.isAcceptableSize(20 * mb, isMovie = true))
        assertTrue(SourceMediaRules.isAcceptableSize(25 * mb, isMovie = true))
        assertTrue(SourceMediaRules.isAcceptableSize(700 * mb, isMovie = true))
        assertTrue(SourceMediaRules.isAcceptableSize(2_500 * mb, isMovie = true))
    }

    @Test
    fun episode_smallFilesBelow5MbAreRejected() {
        assertFalse(SourceMediaRules.isAcceptableSize(1 * mb, isMovie = false))
        assertFalse(SourceMediaRules.isAcceptableSize(4 * mb, isMovie = false))
        assertFalse(SourceMediaRules.isAcceptableSize(4_999_999L, isMovie = false))
    }

    @Test
    fun episode_normalFilesAreAccepted() {
        assertTrue(SourceMediaRules.isAcceptableSize(5 * mb, isMovie = false))
        assertTrue(SourceMediaRules.isAcceptableSize(15 * mb, isMovie = false))
        assertTrue(SourceMediaRules.isAcceptableSize(250 * mb, isMovie = false))
    }

    @Test
    fun unknownSize_isNeverEliminated() {
        // Boyut bilinmiyorsa ELEME kuralı
        assertTrue(SourceMediaRules.isAcceptableSize(null, isMovie = true))
        assertTrue(SourceMediaRules.isAcceptableSize(null, isMovie = false))
        assertTrue(SourceMediaRules.isAcceptableSize(0L, isMovie = true))
        assertTrue(SourceMediaRules.isAcceptableSize(0L, isMovie = false))
        assertTrue(SourceMediaRules.isAcceptableSize(-1L, isMovie = true))
    }

    @Test
    fun mediaTypeOverloadsWorkIdentically() {
        assertFalse(SourceMediaRules.isAcceptableSize(1 * mb, MediaType.Movie))
        assertTrue(SourceMediaRules.isAcceptableSize(50 * mb, MediaType.Movie))
        assertFalse(SourceMediaRules.isAcceptableSize(2 * mb, MediaType.Tv))
        assertTrue(SourceMediaRules.isAcceptableSize(10 * mb, MediaType.Tv))
    }

    @Test
    fun playableStreamOverloadsWork() {
        val streamMovieSmall = createStream(sizeBytes = 1 * mb)
        val streamMovieOk = createStream(sizeBytes = 500 * mb)
        val streamUnknown = createStream(sizeBytes = null)

        assertFalse(SourceMediaRules.isAcceptable(streamMovieSmall, MediaType.Movie))
        assertTrue(SourceMediaRules.isAcceptable(streamMovieOk, MediaType.Movie))
        assertTrue(SourceMediaRules.isAcceptable(streamUnknown, MediaType.Movie))
    }

    private fun createStream(sizeBytes: Long?) = PlayableStream(
        source = StreamSource(1L, "TestAddon"),
        url = "https://example.com/video.mp4",
        resolution = 1080,
        sizeBytes = sizeBytes,
        headers = emptyMap(),
        subtitles = emptyList(),
    )
}
