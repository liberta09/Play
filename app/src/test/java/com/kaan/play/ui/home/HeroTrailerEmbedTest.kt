package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroTrailerEmbedTest {

    @Test
    fun isValidKey_validates11CharacterAlphaNumericAndDashUnderscore() {
        assertFalse(HeroTrailerEmbed.isValidKey(null))
        assertFalse(HeroTrailerEmbed.isValidKey(""))
        assertFalse(HeroTrailerEmbed.isValidKey("   "))
        assertFalse(HeroTrailerEmbed.isValidKey("shortKey")) // 8 chars
        assertFalse(HeroTrailerEmbed.isValidKey("tooLongVideoKey123")) // >11 chars
        assertFalse(HeroTrailerEmbed.isValidKey("bad<script>")) // illegal chars

        assertTrue(HeroTrailerEmbed.isValidKey("dQw4w9WgXcQ"))
        assertTrue(HeroTrailerEmbed.isValidKey("a1B2c3D4e5_"))
        assertTrue(HeroTrailerEmbed.isValidKey("a1B2c3D4e5-"))
    }

    @Test
    fun silentTrailerHtml_rejectsInvalidKeys() {
        assertNull(HeroTrailerEmbed.silentTrailerHtml(""))
        assertNull(HeroTrailerEmbed.silentTrailerHtml("invalid<key>"))
        assertNull(HeroTrailerEmbed.silentTrailerHtml("123"))
    }

    @Test
    fun silentTrailerHtml_containsRequiredParametersAndJsBridge() {
        val html = HeroTrailerEmbed.silentTrailerHtml("dQw4w9WgXcQ")
        assertNotNull(html)
        assertTrue(html!!.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("'mute': 1"))
        assertTrue(html.contains("'controls': 0"))
        assertTrue(html.contains("'playsinline': 1"))
        assertTrue(html.contains("'rel': 0"))
        assertTrue(html.contains("'fs': 0"))
        assertTrue(html.contains("'disablekb': 1"))
        assertTrue(html.contains("'enablejsapi': 1"))
        assertTrue(html.contains("youtube-nocookie.com"))
        assertTrue(html.contains("background:transparent !important"))
        assertTrue(html.contains("getCurrentTime"))
        assertTrue(html.contains("HeroTrailerBridge.onPlaying()"))
        assertTrue(html.contains("HeroTrailerBridge.onEnded()"))
        assertTrue(html.contains("HeroTrailerBridge.onError()"))
    }

    @Test
    fun shouldBlockTrailerNavigation_allowsNocookieHttpsAndBlocksOthers() {
        // Subframes are not blocked
        assertFalse(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = false, scheme = "http", host = "evil.com"))

        // Main frame allows https youtube-nocookie.com
        assertFalse(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = "https", host = "youtube-nocookie.com"))
        assertFalse(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = "https", host = "www.youtube-nocookie.com"))

        // Blocks http
        assertTrue(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = "http", host = "youtube-nocookie.com"))

        // Blocks other domains
        assertTrue(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = "https", host = "youtube.com"))
        assertTrue(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = "https", host = "evil.com"))
        assertTrue(HeroTrailerEmbed.shouldBlockTrailerNavigation(isMainFrame = true, scheme = null, host = null))
    }
}
