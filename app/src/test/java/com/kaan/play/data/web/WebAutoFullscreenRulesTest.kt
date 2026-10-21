package com.kaan.play.data.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAutoFullscreenRulesTest {

    @Test
    fun shouldInjectScript_whenRemoteAndFeatureSupported_returnsTrue_K12() {
        assertTrue(
            WebAutoFullscreenRules.shouldInjectScript(
                isRemoteMode = true,
                isDocumentStartScriptSupported = true
            )
        )
    }

    @Test
    fun shouldInjectScript_whenNotRemote_returnsFalse_K12_K9() {
        // Dokunmatik ekranda (telefonda) betik asla yerleştirilmez
        assertFalse(
            WebAutoFullscreenRules.shouldInjectScript(
                isRemoteMode = false,
                isDocumentStartScriptSupported = true
            )
        )
        assertFalse(
            WebAutoFullscreenRules.shouldInjectScript(
                isRemoteMode = false,
                isDocumentStartScriptSupported = false
            )
        )
    }

    @Test
    fun shouldInjectScript_whenRemoteButFeatureNotSupported_returnsFalse_K12() {
        // Özellik desteklenmiyorsa sessizce devreye girmez, hata verilmez
        assertFalse(
            WebAutoFullscreenRules.shouldInjectScript(
                isRemoteMode = true,
                isDocumentStartScriptSupported = false
            )
        )
    }

    @Test
    fun autoFullscreenScript_containsRequiredConditionsAndSafetyGuards_K13() {
        val script = WebAutoFullscreenRules.AUTO_FULLSCREEN_SCRIPT

        // Capture aşamasında play olayını dinler
        assertTrue(script.contains("addEventListener('play'"))
        assertTrue(script.contains("true);"))

        // (i) Kullanıcı etkinliği kontrolü
        assertTrue(script.contains("navigator.userActivation"))
        assertTrue(script.contains("isActive"))

        // (ii) Zaten tam ekransa çıkış
        assertTrue(script.contains("document.fullscreenElement"))

        // (iii) Süre kontrolü (60 sn ya da Infinity/canlı)
        assertTrue(script.contains("duration"))
        assertTrue(script.contains("Infinity"))
        assertTrue(script.contains(">= 60"))

        // (iv) Görünür genişlik kontrolü (%40)
        assertTrue(script.contains("getBoundingClientRect"))
        assertTrue(script.contains("0.4"))

        // Tam ekran çağrıları
        assertTrue(script.contains("requestFullscreen"))
        assertTrue(script.contains("webkitRequestFullscreen"))

        // Güvenlik: ağ isteği yok, veri toplama yok, console.log yok
        assertFalse(script.contains("console.log"))
        assertFalse(script.contains("fetch("))
        assertFalse(script.contains("XMLHttpRequest"))
        assertFalse(script.contains("send("))
    }
}
