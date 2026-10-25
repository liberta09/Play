package com.kaan.play.data.stream

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSessionCookiesTest {

    @Test
    fun cookieJarHonorsDomainPathAndSecureRules() {
        val secureCookie = Cookie.Builder()
            .name("sid")
            .value("memory-only")
            .domain("example.com")
            .path("/live")
            .secure()
            .build()
        val jar = StreamPlaybackCookieJar()
        jar.replace(listOf(secureCookie))

        assertEquals(listOf(secureCookie), jar.loadForRequest("https://cdn.example.com/live/segment.ts".toHttpUrl()))
        assertTrue(jar.loadForRequest("https://cdn.example.com/movie/segment.ts".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://cdn.example.com/live/segment.ts".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://attacker.example.net/live/segment.ts".toHttpUrl()).isEmpty())
    }

    @Test
    fun sessionCookiesAreBoundToSourceAndManifestUrl() {
        val cookie = Cookie.Builder()
            .name("sid")
            .value("private")
            .domain("example.com")
            .path("/")
            .build()
        val sourceId = -987654L
        val mediaUrl = "https://cdn.example.com/live/master.m3u8"

        StreamSessionCookies.remember(sourceId, mediaUrl, listOf(cookie))

        assertEquals(listOf(cookie), StreamSessionCookies.forPlayback(sourceId, mediaUrl))
        assertTrue(StreamSessionCookies.forPlayback(sourceId - 1, mediaUrl).isEmpty())
        assertTrue(StreamSessionCookies.forPlayback(sourceId, "https://other.example.com/live/master.m3u8").isEmpty())

        StreamSessionCookies.remember(sourceId, mediaUrl, emptyList())
        assertTrue("cookie-free refresh must invalidate the previous session", StreamSessionCookies.forPlayback(sourceId, mediaUrl).isEmpty())
    }
}
