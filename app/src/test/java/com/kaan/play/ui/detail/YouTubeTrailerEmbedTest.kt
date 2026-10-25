package com.kaan.play.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTrailerEmbedTest {
    @Test
    fun validVideoIdProducesTheDirectOfficialEmbedUrl() {
        val url = youtubeTrailerEmbedUrl("dQw4w9WgXcQ")

        assertNotNull(url)
        assertTrue(url!!.startsWith("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertTrue(url.contains("playsinline=1"))
        assertTrue(url.contains("controls=1"))
        assertTrue(url.contains("origin=https%3A%2F%2Fwww.youtube-nocookie.com"))
    }

    @Test
    fun validVideoIdProducesTheOfficialPrivacyEnhancedEmbedWithoutRedirectUrl() {
        val html = youtubeTrailerEmbedHtml("dQw4w9WgXcQ")

        assertNotNull(html)
        assertTrue(html!!.contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertTrue(html.contains("playsinline=1"))
        assertTrue(html.contains("fs=0"))
        assertTrue(html.contains("controls=1"))
        assertTrue(html.contains("<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\""))
        assertTrue(html.contains("referrerpolicy=\"strict-origin-when-cross-origin\""))
        assertTrue(html.contains("allow=\"autoplay; encrypted-media"))
        assertFalse(html.contains("youtube.com/watch"))
    }

    @Test
    fun malformedVideoIdsAreRejectedInsteadOfInterpolatedIntoHtml() {
        assertNull(youtubeTrailerEmbedHtml("short"))
        assertNull(youtubeTrailerEmbedHtml("dQw4w9WgXcQ\"><script>alert(1)</script>"))
        assertNull(youtubeTrailerEmbedHtml(""))
    }

    @Test
    fun iframeSubframesCanLoadGooglePlayerResourcesButExternalTopLevelNavigationIsBlocked() {
        assertFalse(shouldBlockTrailerNavigation(false, "https", "www.youtube.com"))
        assertFalse(shouldBlockTrailerNavigation(false, "https", "googlevideo.com"))
        assertFalse(shouldBlockTrailerNavigation(true, "https", "www.youtube-nocookie.com"))
        assertTrue(shouldBlockTrailerNavigation(true, "https", "example.org"))
        assertTrue(shouldBlockTrailerNavigation(true, "http", "www.youtube-nocookie.com"))
    }
}
