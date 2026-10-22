package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class AdHideRulesTest {

    @Test
    fun genericRulesApplyToAllDomains() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "##.adsbox",
            "###top-banner"
        ))

        val forExample = rules.getSelectorsForHost("example.com")
        assertTrue(forExample.contains(".adsbox"))
        assertTrue(forExample.contains("#top-banner"))

        val forOther = rules.getSelectorsForHost("hdfilmcehennemi.nl")
        assertTrue(forOther.contains(".adsbox"))
        assertTrue(forOther.contains("#top-banner"))
    }

    @Test
    fun domainSpecificRuleAppliesToExactDomain() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "example.com##.sponsor-banner",
            "other.com##.ad-box"
        ))

        val exSelectors = rules.getDomainSelectorsForHost("example.com")
        assertEquals(listOf(".sponsor-banner"), exSelectors)

        val otherSelectors = rules.getDomainSelectorsForHost("other.com")
        assertEquals(listOf(".ad-box"), otherSelectors)
    }

    @Test
    fun domainSpecificRuleDoesNotApplyToUnrelatedDomain() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "example.com##.sponsor-banner"
        ))

        val selectors = rules.getDomainSelectorsForHost("test.org")
        assertTrue(selectors.isEmpty())
    }

    @Test
    fun subdomainInheritsParentDomainRules() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "example.com##.parent-ad",
            "sub.example.com##.sub-ad"
        ))

        val subSelectors = rules.getDomainSelectorsForHost("sub.example.com")
        assertTrue(subSelectors.contains(".parent-ad"))
        assertTrue(subSelectors.contains(".sub-ad"))

        val deepSelectors = rules.getDomainSelectorsForHost("a.b.sub.example.com")
        assertTrue(deepSelectors.contains(".parent-ad"))
        assertTrue(deepSelectors.contains(".sub-ad"))

        val parentSelectors = rules.getDomainSelectorsForHost("example.com")
        assertTrue(parentSelectors.contains(".parent-ad"))
        assertFalse(parentSelectors.contains(".sub-ad"))
    }

    @Test
    fun wildcardDomainRuleMatchesWildcardDomains() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "hdfilmcehennemi.*##.money-wrapper",
            "hdfilmcehennemi.*##.money-item"
        ))

        val nlSelectors = rules.getDomainSelectorsForHost("hdfilmcehennemi.nl")
        assertTrue(nlSelectors.contains(".money-wrapper"))
        assertTrue(nlSelectors.contains(".money-item"))

        val lifeSelectors = rules.getDomainSelectorsForHost("hdfilmcehennemi.life")
        assertTrue(lifeSelectors.contains(".money-wrapper"))

        val subSelectors = rules.getDomainSelectorsForHost("dizi.hdfilmcehennemi.cx")
        assertTrue(subSelectors.contains(".money-wrapper"))
    }

    @Test
    fun wildcardDomainRuleDoesNotMatchNonMatchingDomains() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "hdfilmcehennemi.*##.money-wrapper",
            "hdfilmcehennemi1.*##.money-wrapper-1"
        ))

        val other = rules.getDomainSelectorsForHost("otherfilmcehennemi.com")
        assertFalse(other.contains(".money-wrapper"))

        val hd1 = rules.getDomainSelectorsForHost("hdfilmcehennemi1.com")
        assertTrue(hd1.contains(".money-wrapper-1"))
        assertFalse(hd1.contains(".money-wrapper"))
    }

    @Test
    fun multiDomainCommaSeparatedRuleAppliesToAllListedDomains() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "site1.com,site2.com,site3.org##.shared-ad"
        ))

        assertTrue(rules.getDomainSelectorsForHost("site1.com").contains(".shared-ad"))
        assertTrue(rules.getDomainSelectorsForHost("site2.com").contains(".shared-ad"))
        assertTrue(rules.getDomainSelectorsForHost("site3.org").contains(".shared-ad"))
        assertFalse(rules.getDomainSelectorsForHost("site4.com").contains(".shared-ad"))
    }

    @Test
    fun normalizationIgnoresCaseWwwAndTrailingDots() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "ExAmPLe.CoM##.banner-1",
            "www.site.com##.banner-2"
        ))

        assertTrue(rules.getDomainSelectorsForHost("example.com").contains(".banner-1"))
        assertTrue(rules.getDomainSelectorsForHost("EXAMPLE.COM").contains(".banner-1"))
        assertTrue(rules.getDomainSelectorsForHost("www.example.com.").contains(".banner-1"))

        assertTrue(rules.getDomainSelectorsForHost("site.com").contains(".banner-2"))
        assertTrue(rules.getDomainSelectorsForHost("WWW.SITE.COM").contains(".banner-2"))
    }

    @Test
    fun skipsCommentsExceptionsAndScriptlets() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "! Yorum satiri",
            "# Baska bir yorum",
            "[Adblock Plus 2.0]",
            "example.com#@#.ad-box", // istisna kuralı
            "example.com#?#.procedural", // procedur kural
            "example.com#$#body { background: none; }", // CSS enjeksiyon kuralı
            "example.com##+js(set-constant, foo, true)", // JS scriptlet kuralı
            "##:-abp-has(.ad)", // ABP özel sözde sınıfı
            "example.com##.valid-selector" // Geçerli kural
        ))

        val selectors = rules.getDomainSelectorsForHost("example.com")
        assertEquals(listOf(".valid-selector"), selectors)
        assertEquals(0, rules.genericRuleCount)
        assertEquals(1, rules.domainRuleCount)
    }

    @Test
    fun buildCssForHostFormatsDisplayNone() {
        val rules = AdHideRules()
        rules.loadFromLines(sequenceOf(
            "##.gen-ad",
            "test.com##.site-ad"
        ))

        val css = rules.buildCssForHost("test.com")
        assertNotNull(css)
        assertTrue(css!!.contains(".gen-ad"))
        assertTrue(css.contains(".site-ad"))
        assertTrue(css.endsWith("{ display: none !important; }"))

        val unstarted = AdHideRules()
        assertNull(unstarted.buildCssForHost("test.com"))
    }

    @Test
    fun realAdHideAssetLoadsAndContainsHdfilmcehennemiRules() {
        val assetFile = File("src/main/assets/adhide.txt").let {
            if (it.exists()) it else File("app/src/main/assets/adhide.txt")
        }
        assertTrue("adhide.txt bulunamadi: ${assetFile.absolutePath}", assetFile.exists())

        val rules = AdHideRules()
        val start = System.currentTimeMillis()
        assetFile.bufferedReader().use { reader ->
            rules.loadFromReader(reader)
        }
        val elapsed = System.currentTimeMillis() - start
        println("adhide.txt yukleme suresi: $elapsed ms (genel: ${rules.genericRuleCount}, alan: ${rules.domainRuleCount})")

        assertTrue(rules.isLoaded)
        assertTrue(rules.genericRuleCount > 10000)
        assertTrue(rules.domainRuleCount > 20000)

        // hdfilmcehennemi kurallarını kontrol et
        val hdSelectors = rules.getDomainSelectorsForHost("hdfilmcehennemi.nl")
        println("hdfilmcehennemi.nl icin eslesen alan kurallari: $hdSelectors")

        assertTrue("hdfilmcehennemi .money-wrapper kurali bulunmali", hdSelectors.contains(".money-wrapper"))
        assertTrue("hdfilmcehennemi .money kurali bulunmali", hdSelectors.contains(".money"))
        assertTrue("hdfilmcehennemi .money-item kurali bulunmali", hdSelectors.contains(".money-item"))
        assertTrue("hdfilmcehennemi .card-money kurali bulunmali", hdSelectors.contains(".card-money"))
        assertTrue("hdfilmcehennemi [class^=\"money\"] kurali bulunmali", hdSelectors.contains("[class^=\"money\"]"))

        // Sayfa içeriği/oynatıcı/menü seçicilerinin yanlışlıkla gizlenmediğini doğrula
        assertFalse(hdSelectors.contains("#player"))
        assertFalse(hdSelectors.contains(".player"))
        assertFalse(hdSelectors.contains(".navbar"))
        assertFalse(hdSelectors.contains("nav"))
        assertFalse(hdSelectors.contains(".episodes"))
    }

    @Test
    fun performanceOfLookupAndCssGeneration() {
        val assetFile = File("src/main/assets/adhide.txt").let {
            if (it.exists()) it else File("app/src/main/assets/adhide.txt")
        }
        if (!assetFile.exists()) return

        val rules = AdHideRules()
        assetFile.bufferedReader().use { rules.loadFromReader(it) }

        // 100 ardışık sorgu ve CSS üretiminin toplam süresini ölç
        val t0 = System.nanoTime()
        val hosts = listOf("hdfilmcehennemi.nl", "example.com", "sub.dizipal.org", "filmizletv.net", "unknown-site.org")
        for (i in 0 until 100) {
            val host = hosts[i % hosts.size]
            val css = rules.buildCssForHost(host)
            assertNotNull(css)
        }
        val totalNanos = System.nanoTime() - t0
        val avgMs = (totalNanos / 100.0) / 1_000_000.0
        println("Ortalama secici sorgu + CSS uretim suresi: $avgMs ms")
        assertTrue("Ortalama sorgu 15 ms altinda olmali, gerceklesen: $avgMs ms", avgMs < 15.0)
    }
}
