package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class AdHostMatcherTest {

    @Test
    fun blocksExactDomain() {
        val matcher = AdHostMatcher(listOf("example.com", "doubleclick.net"))
        assertTrue(matcher.isBlocked("example.com"))
        assertTrue(matcher.isBlocked("doubleclick.net"))
    }

    @Test
    fun blocksSubdomainsAtAnyDepth() {
        val matcher = AdHostMatcher(listOf("example.com"))
        assertTrue(matcher.isBlocked("reklam.example.com"))
        assertTrue(matcher.isBlocked("a.b.c.example.com"))
        assertTrue(matcher.isBlocked("tracker.sub.example.com"))
    }

    @Test
    fun ignoresCaseInDomainAndQuery() {
        val matcher = AdHostMatcher(listOf("ExAmPLe.CoM"))
        assertTrue(matcher.isBlocked("example.com"))
        assertTrue(matcher.isBlocked("EXAMPLE.COM"))
        assertTrue(matcher.isBlocked("Sub.ExAmPLe.CoM"))
        assertTrue(matcher.isBlocked("SUB.EXAMPLE.COM"))
    }

    @Test
    fun ignoresLeadingWwwInQuery() {
        val matcher = AdHostMatcher(listOf("example.com"))
        assertTrue(matcher.isBlocked("www.example.com"))
        assertTrue(matcher.isBlocked("www.reklam.example.com"))
    }

    @Test
    fun ignoresLeadingWwwInList() {
        val matcher = AdHostMatcher(listOf("www.badsite.com"))
        assertTrue(matcher.isBlocked("badsite.com"))
        assertTrue(matcher.isBlocked("www.badsite.com"))
        assertTrue(matcher.isBlocked("ad.badsite.com"))
    }

    @Test
    fun ignoresTrailingDots() {
        val matcher = AdHostMatcher(listOf("example.com."))
        assertTrue(matcher.isBlocked("example.com"))
        assertTrue(matcher.isBlocked("example.com."))
        assertTrue(matcher.isBlocked("sub.example.com."))
    }

    @Test
    fun doesNotBlockUnrelatedDomain() {
        val matcher = AdHostMatcher(listOf("example.com"))
        assertFalse(matcher.isBlocked("notexample.com"))
        assertFalse(matcher.isBlocked("goodsite.org"))
        assertFalse(matcher.isBlocked("com"))
        assertFalse(matcher.isBlocked("myexample.com"))
    }

    @Test
    fun doesNotBlockParentDomain() {
        val matcher = AdHostMatcher(listOf("ads.example.com"))
        assertTrue(matcher.isBlocked("ads.example.com"))
        assertTrue(matcher.isBlocked("sub.ads.example.com"))
        assertFalse(matcher.isBlocked("example.com"))
        assertFalse(matcher.isBlocked("other.example.com"))
    }

    @Test
    fun doesNotBlockLookalikeDomain() {
        val matcher = AdHostMatcher(listOf("example.com"))
        assertFalse(matcher.isBlocked("example.com.evil.org"))
        assertFalse(matcher.isBlocked("sub.example.com.attacker.net"))
    }

    @Test
    fun handlesNullAndEmpty() {
        val matcher = AdHostMatcher(listOf("example.com"))
        assertFalse(matcher.isBlocked(null))
        assertFalse(matcher.isBlocked(""))
        assertFalse(matcher.isBlocked("   "))
        assertFalse(matcher.isBlocked("."))
    }

    @Test
    fun exceptionRuleIsNotIndexed() {
        assertNull(AdHostMatcher.extractDomain("@@||whitelist.com^"))
        assertNull(AdHostMatcher.extractDomain("@@||whitelist.com^\$document"))

        val content = """
            ||badsite.com^
            @@||goodsite.com^
            @@||another-good.com^
        """.trimIndent()

        val matcher = AdHostMatcher(emptyList())
        matcher.loadFromReader(StringReader(content))

        assertTrue(matcher.isBlocked("badsite.com"))
        assertFalse(matcher.isBlocked("goodsite.com"))
        assertFalse(matcher.isBlocked("another-good.com"))
    }

    @Test
    fun elementHidingRuleIsNotIndexed() {
        assertNull(AdHostMatcher.extractDomain("example.com##.ad-banner"))
        assertNull(AdHostMatcher.extractDomain("example.com#@#.ad-banner"))
        assertNull(AdHostMatcher.extractDomain("example.com#?#.ad-banner"))

        val content = """
            ||tracker.com^
            example.com##.ad-banner
            news.com##div[class="ad"]
        """.trimIndent()

        val matcher = AdHostMatcher(emptyList())
        matcher.loadFromReader(StringReader(content))

        assertTrue(matcher.isBlocked("tracker.com"))
        assertFalse(matcher.isBlocked("example.com"))
        assertFalse(matcher.isBlocked("news.com"))
    }

    @Test
    fun adblockFormatParsedCorrectly() {
        assertEquals("adserver.org", AdHostMatcher.extractDomain("||adserver.org^"))
        assertEquals("popup.net", AdHostMatcher.extractDomain("||popup.net^"))
        assertEquals("clean.io", AdHostMatcher.extractDomain("clean.io"))

        val content = """
            ||adserver.org^
            ||sub.popup.net^
        """.trimIndent()

        val matcher = AdHostMatcher(emptyList())
        matcher.loadFromReader(StringReader(content))

        assertTrue(matcher.isBlocked("adserver.org"))
        assertTrue(matcher.isBlocked("banner.adserver.org"))
        assertTrue(matcher.isBlocked("sub.popup.net"))
    }

    @Test
    fun commentsAndBlankLinesIgnored() {
        val content = """
            # Baslik
            ! EasyList Header
            
            # Yorum satiri
            ||adsite.com^
            ! Baska bir yorum
            
        """.trimIndent()

        val matcher = AdHostMatcher(emptyList())
        matcher.loadFromReader(StringReader(content))

        assertTrue(matcher.isBlocked("adsite.com"))
        assertFalse(matcher.isBlocked("Baslik"))
    }

    @Test
    fun preservesAndMergesAdDomains() {
        val content = """
            ||custom-blocked.com^
        """.trimIndent()

        val matcher = AdHostMatcher()
        // AdDomains.all'da bulunan doubleclick.net başlangıçta vardır
        assertTrue(matcher.isBlocked("doubleclick.net"))

        matcher.loadFromReader(StringReader(content))

        // Yüklendikten sonra hem AdDomains.all hem yeni kayıt vardır
        assertTrue(matcher.isBlocked("doubleclick.net"))
        assertTrue(matcher.isBlocked("googlesyndication.com"))
        assertTrue(matcher.isBlocked("custom-blocked.com"))
        assertTrue(matcher.isBlocked("sub.custom-blocked.com"))
        assertFalse(matcher.isBlocked("safe-site.com"))
    }
}
