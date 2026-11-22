package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioLanguagePolicyTest {

    /** Desteklenen parçalara sırayla dizin verir (oynatıcıdaki gibi); desteklenmeyenin dizini -1. */
    private fun tracks(vararg spec: Triple<String?, Boolean, Boolean>): List<AudioTrackInfo> {
        var next = 0
        return spec.map { (lang, supported, selected) ->
            AudioTrackInfo(
                index = if (supported) next++ else -1,
                language = lang,
                label = null,
                isSelected = selected,
                isSupported = supported,
            )
        }
    }

    private fun t(lang: String?, selected: Boolean = false, supported: Boolean = true) = Triple(lang, supported, selected)

    @Test
    fun `a - Hintce varsayilan ve Ingilizce varsa Ingilizce secilir`() {
        val list = tracks(t("hi", selected = true), t("ta"), t("en"))
        assertEquals(2, AudioLanguagePolicy.choose(list, preferDub = true))
        assertEquals(2, AudioLanguagePolicy.choose(list, preferDub = false))
    }

    @Test
    fun `b - Turkce varsa ve dublaj onceligi aciksa Turkce secilir`() {
        val list = tracks(t("hi", selected = true), t("en"), t("tr"))
        assertEquals(2, AudioLanguagePolicy.choose(list, preferDub = true))
    }

    @Test
    fun `c - dublaj onceligi kapaliysa Turkce olsa da Ingilizce secilir`() {
        val list = tracks(t("hi", selected = true), t("en"), t("tr"))
        assertEquals(1, AudioLanguagePolicy.choose(list, preferDub = false))
    }

    @Test
    fun `d - yalnizca Hintce ve Tamilce varsa dokunulmaz`() {
        val list = tracks(t("hi", selected = true), t("ta"))
        assertNull(AudioLanguagePolicy.choose(list, preferDub = true))
    }

    @Test
    fun `e - Ingilizce desteksizse Hintce kalir`() {
        val list = tracks(t("hi", selected = true), t("en", supported = false))
        assertNull(AudioLanguagePolicy.choose(list, preferDub = true))
    }

    @Test
    fun `f - hicbir parcada dil bilgisi yoksa dokunulmaz`() {
        val list = tracks(t(null, selected = true), t("und"), t(null))
        assertNull(AudioLanguagePolicy.choose(list, preferDub = true))
    }

    @Test
    fun `g - eng ve English Ingilizce sayilir`() {
        assertEquals(1, AudioLanguagePolicy.choose(tracks(t("hi", selected = true), t("eng")), preferDub = true))
        assertEquals(1, AudioLanguagePolicy.choose(tracks(t("hi", selected = true), t("English")), preferDub = true))
    }

    @Test
    fun `hedef zaten seciliyse dokunulmaz`() {
        assertNull(AudioLanguagePolicy.choose(tracks(t("hi"), t("en", selected = true)), preferDub = true))
    }

    @Test
    fun `tercih edilen dil sirasi`() {
        assertEquals(listOf("tr", "en"), AudioLanguagePolicy.preferredLanguages(preferDub = true))
        assertEquals(listOf("en"), AudioLanguagePolicy.preferredLanguages(preferDub = false))
    }
}
