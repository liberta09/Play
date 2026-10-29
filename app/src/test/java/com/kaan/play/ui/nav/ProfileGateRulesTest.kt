package com.kaan.play.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileGateRulesTest {

    @Test
    fun profilYoksaKapiAcik() {
        assertTrue(shouldShowProfileGate(profileCount = 0, hasActive = false, rememberLast = true))
        assertTrue(shouldShowProfileGate(profileCount = 0, hasActive = true, rememberLast = true))
    }

    @Test
    fun ayarKapaliysaKapiAcik() {
        assertTrue(shouldShowProfileGate(profileCount = 2, hasActive = true, rememberLast = false))
    }

    @Test
    fun aktifProfilYoksaKapiAcik() {
        assertTrue(shouldShowProfileGate(profileCount = 2, hasActive = false, rememberLast = true))
    }

    @Test
    fun hepsiUygunsaKapiKapali() {
        assertFalse(shouldShowProfileGate(profileCount = 1, hasActive = true, rememberLast = true))
        assertFalse(shouldShowProfileGate(profileCount = 3, hasActive = true, rememberLast = true))
    }
}
