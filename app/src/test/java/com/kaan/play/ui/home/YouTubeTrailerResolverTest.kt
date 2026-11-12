package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.VideoStream

class YouTubeTrailerResolverTest {

    @Test
    fun isValidKey_validates11CharacterAlphaNumericAndDashUnderscore() {
        assertFalse(YouTubeTrailerResolver.isValidKey(null))
        assertFalse(YouTubeTrailerResolver.isValidKey(""))
        assertFalse(YouTubeTrailerResolver.isValidKey("   "))
        assertFalse(YouTubeTrailerResolver.isValidKey("shortKey")) // 8 chars
        assertFalse(YouTubeTrailerResolver.isValidKey("tooLongVideoKey123")) // >11 chars
        assertFalse(YouTubeTrailerResolver.isValidKey("bad<script>")) // illegal chars

        assertTrue(YouTubeTrailerResolver.isValidKey("dQw4w9WgXcQ"))
        assertTrue(YouTubeTrailerResolver.isValidKey("a1B2c3D4e5_"))
        assertTrue(YouTubeTrailerResolver.isValidKey("a1B2c3D4e5-"))
    }

    @Test
    fun pickBestStream_prioritizesAvc1UpTo720p() {
        val itag1080 = ItagItem.getItag(137).apply { setHeight(1080) }
        val itag720 = ItagItem.getItag(136).apply { setHeight(720) }
        val itag480 = ItagItem.getItag(135).apply { setHeight(480) }

        val s1080 = VideoStream.Builder()
            .setId(org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN)
            .setContent("http://test/1080", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setIsVideoOnly(true)
            .setResolution("1080p")
            .setItagItem(itag1080)
            .build()
        val s720 = VideoStream.Builder()
            .setId(org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN)
            .setContent("http://test/720", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setIsVideoOnly(true)
            .setResolution("720p")
            .setItagItem(itag720)
            .build()
        val s480 = VideoStream.Builder()
            .setId(org.schabi.newpipe.extractor.stream.Stream.ID_UNKNOWN)
            .setContent("http://test/480", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setIsVideoOnly(true)
            .setResolution("480p")
            .setItagItem(itag480)
            .build()

        val chosenNormal = YouTubeTrailerResolver.pickBestStream(listOf(s1080, s720, s480), isLowPerf = false)
        assertNotNull(chosenNormal)
        assertEquals(720, chosenNormal!!.height)
        assertEquals("http://test/720", chosenNormal.content)

        val chosenLowPerf = YouTubeTrailerResolver.pickBestStream(listOf(s1080, s720, s480), isLowPerf = true)
        assertNotNull(chosenLowPerf)
        assertEquals(480, chosenLowPerf!!.height)
        assertEquals("http://test/480", chosenLowPerf.content)
    }

    @Test
    fun pickBestStream_handlesNullOrEmptyList() {
        assertNull(YouTubeTrailerResolver.pickBestStream(null, isLowPerf = false))
        assertNull(YouTubeTrailerResolver.pickBestStream(emptyList(), isLowPerf = false))
    }
}
