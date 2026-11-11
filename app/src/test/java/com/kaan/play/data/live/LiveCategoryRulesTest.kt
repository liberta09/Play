package com.kaan.play.data.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCategoryRulesTest {

    @Test
    fun testAllCategoriesHaveAtLeastTwoExamples() {
        // 1) beIN
        assertEquals(LiveCategory.BeIn, LiveCategoryRules.classify("beIN Sports 1"))
        assertEquals(LiveCategory.BeIn, LiveCategoryRules.classify("beIN MOVIES PREMIERE"))

        // 2) tabii
        assertEquals(LiveCategory.Tabii, LiveCategoryRules.classify("tabii Spor 1"))
        assertEquals(LiveCategory.Tabii, LiveCategoryRules.classify("TABİİ DİZİ"))

        // 3) Tivibu
        assertEquals(LiveCategory.Tivibu, LiveCategoryRules.classify("Tivibu Spor 1"))
        assertEquals(LiveCategory.Tivibu, LiveCategoryRules.classify("TİVİBU DİZİ"))

        // 4) Exxen
        assertEquals(LiveCategory.Exxen, LiveCategoryRules.classify("Exxen 1"))
        assertEquals(LiveCategory.Exxen, LiveCategoryRules.classify("EXXENSPOR"))

        // 5) S Sport
        assertEquals(LiveCategory.SSport, LiveCategoryRules.classify("S Sport"))
        assertEquals(LiveCategory.SSport, LiveCategoryRules.classify("S SPORT 2"))

        // 6) Diğer spor
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("Eurosport 1"))
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("A Spor HD"))
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("NBA TV"))
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("TJK TV"))
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("FightBox"))

        // 7) Haber
        assertEquals(LiveCategory.News, LiveCategoryRules.classify("CNN Türk"))
        assertEquals(LiveCategory.News, LiveCategoryRules.classify("NTV Haber"))
        assertEquals(LiveCategory.News, LiveCategoryRules.classify("Halk TV"))
        assertEquals(LiveCategory.News, LiveCategoryRules.classify("Bloomberg HT"))

        // 8) Çocuk
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("Minika Çocuk"))
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("Cartoon Network"))
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("Disney Channel"))

        // 9) Belgesel
        assertEquals(LiveCategory.Documentary, LiveCategoryRules.classify("National Geographic HD"))
        assertEquals(LiveCategory.Documentary, LiveCategoryRules.classify("Discovery Channel"))
        assertEquals(LiveCategory.Documentary, LiveCategoryRules.classify("BBC Earth"))
        assertEquals(LiveCategory.Documentary, LiveCategoryRules.classify("Yaban TV"))

        // 10) Yeşilçam
        assertEquals(LiveCategory.Yesilcam, LiveCategoryRules.classify("Yeşilçam TV"))
        assertEquals(LiveCategory.Yesilcam, LiveCategoryRules.classify("YEŞİLÇAM KLASİK"))

        // 11) Sinema
        assertEquals(LiveCategory.Cinema, LiveCategoryRules.classify("Sinema TV Aksiyon"))
        assertEquals(LiveCategory.Cinema, LiveCategoryRules.classify("HBO Movies"))
        assertEquals(LiveCategory.Cinema, LiveCategoryRules.classify("Cinemax HD"))

        // 12) Müzik
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("Kral Pop TV"))
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("Power Türk TV"))
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("MTV Hits"))
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("Dream TV"))

        // 13) Yerel
        assertEquals(LiveCategory.Local, LiveCategoryRules.classify("Bursa Yerel TV"))
        assertEquals(LiveCategory.Local, LiveCategoryRules.classify("Kayseri Belediye TV"))
        assertEquals(LiveCategory.Local, LiveCategoryRules.classify("Ege Local TV"))

        // 14) Ulusal
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("TRT 1 HD"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("Kanal D"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("Show TV"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("Star TV"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("ATV HD"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("FOX TV"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("TV8"))
        assertEquals(LiveCategory.National, LiveCategoryRules.classify("NOW HD"))

        // 15) Diğer
        assertEquals(LiveCategory.Other, LiveCategoryRules.classify("Bilinmeyen Akış"))
        assertEquals(LiveCategory.Other, LiveCategoryRules.classify("Test Kanalı 123"))
    }

    @Test
    fun testPriorityOrder() {
        // beIN Sports Haber beIN olur (beIN #1, spor #6, haber #7)
        assertEquals(LiveCategory.BeIn, LiveCategoryRules.classify("beIN Sports Haber"))

        // S Sport 2 S Sport olur (S Sport #5, spor #6)
        assertEquals(LiveCategory.SSport, LiveCategoryRules.classify("S Sport 2"))

        // TRT Spor Yıldız Diğer spor olur (spor #6)
        assertEquals(LiveCategory.OtherSports, LiveCategoryRules.classify("TRT Spor Yıldız"))

        // Exxen Spor Exxen olur (Exxen #4, spor #6)
        assertEquals(LiveCategory.Exxen, LiveCategoryRules.classify("Exxen Spor 1"))

        // Tivibu Spor Tivibu olur (Tivibu #3, spor #6)
        assertEquals(LiveCategory.Tivibu, LiveCategoryRules.classify("Tivibu Spor"))
    }

    @Test
    fun testTurkishCharacterFolding() {
        // Çocuk / ÇOCUK -> Çocuk
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("Çocuk"))
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("ÇOCUK"))
        assertEquals(LiveCategory.Kids, LiveCategoryRules.classify("çocuk tv"))

        // Yeşilçam
        assertEquals(LiveCategory.Yesilcam, LiveCategoryRules.classify("yeşilçam"))
        assertEquals(LiveCategory.Yesilcam, LiveCategoryRules.classify("YEŞİLÇAM"))

        // Müzik
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("Müzik"))
        assertEquals(LiveCategory.Music, LiveCategoryRules.classify("MÜZİK TV"))

        // Sinema
        assertEquals(LiveCategory.Cinema, LiveCategoryRules.classify("SİNEMA TÜRK"))
    }

    @Test
    fun testBlankAndNullNameResolvesToOther() {
        assertEquals(LiveCategory.Other, LiveCategoryRules.classify(""))
        assertEquals(LiveCategory.Other, LiveCategoryRules.classify("   "))
        assertEquals(LiveCategory.Other, LiveCategoryRules.classify(null))
    }
}
