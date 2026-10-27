package com.kaan.play.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeTitlesTest {

    /** Arayüzdeki biçimle aynı: ad varsa "N. ad", yoksa "N. Bölüm". */
    private fun title(number: Int, name: String) =
        EpisodeTitles.displayName(number, name)?.let { "$number. $it" } ?: "$number. Bölüm"

    @Test
    fun blankNameFallsBackToNumber() {
        assertNull(EpisodeTitles.displayName(1, ""))
        assertNull(EpisodeTitles.displayName(1, "   "))
        assertEquals("1. Bölüm", title(1, ""))
    }

    @Test
    fun genericTmdbNamesNeverDoubleTheNumber() {
        assertEquals("1. Bölüm", title(1, "1. Bölüm"))
        assertEquals("17. Bölüm", title(17, "17. Bölüm"))
        assertEquals("1. Bölüm", title(1, "Bölüm 1"))
        assertEquals("1. Bölüm", title(1, "Episode 1"))
        assertEquals("2. Bölüm", title(2, "episode 2"))
        assertEquals("3. Bölüm", title(3, "BÖLÜM 3"))
        assertEquals("4. Bölüm", title(4, "Ep. 4"))
    }

    @Test
    fun realNamesAreKept() {
        assertEquals("1. Extreme Aggressor", title(1, "Extreme Aggressor"))
        assertEquals("1. Pilot", title(1, "Pilot"))
        assertEquals("5. Bölüm Sonu Canavarı", title(5, "Bölüm Sonu Canavarı"))
    }

    @Test
    fun leadingNumberInNameIsNotRepeated() {
        assertEquals("1. Pilot", title(1, "1. Pilot"))
        assertEquals("2. The Fallen", title(2, "2 - The Fallen"))
        // Başka bir sayıyla başlayan gerçek ad korunur.
        assertEquals("3. 24 Saat", title(3, "24 Saat"))
    }
}
