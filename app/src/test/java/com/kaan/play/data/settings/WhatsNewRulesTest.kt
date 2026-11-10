package com.kaan.play.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewRulesTest {

    @Test
    fun freshInstallRecordsVersionSilentlyWithoutDialog() {
        val decision = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = null,
            updateCheckCompleted = true,
            hasUpdatePopup = false,
        )
        assertEquals(WhatsNewDecision.RecordSilently("1.3.0"), decision)
        // İlk kurulumda pencere gösterilmediği için duyuru aynı açılışta gösterilebilir.
        assertTrue(WhatsNewRules.shouldShowAnnouncementThisLaunch(hasUpdatePopup = false, whatsNewShownThisLaunch = false))
    }

    @Test
    fun upgradedVersionShowsOnceAndDefersWhenUpdateIsAvailable() {
        val deferred = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = "1.2.9",
            updateCheckCompleted = true,
            hasUpdatePopup = true,
        )
        assertEquals(WhatsNewDecision.DeferToNextLaunch, deferred)
        assertFalse(WhatsNewRules.shouldShowAnnouncementThisLaunch(hasUpdatePopup = true, whatsNewShownThisLaunch = false))

        // Sonraki açılışta güncelleme yok -> Neler Yeni gösterilir, Duyuru ertelenir.
        val show = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = "1.2.9",
            updateCheckCompleted = true,
            hasUpdatePopup = false,
        )
        assertEquals(WhatsNewDecision.ShowDialog, show)
        assertFalse(WhatsNewRules.shouldShowAnnouncementThisLaunch(hasUpdatePopup = false, whatsNewShownThisLaunch = true))

        // Üçüncü açılışta aynı sürüm kaydedilmiş -> Neler Yeni çıkmaz, Duyuru gösterilebilir.
        val done = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = "1.3.0",
            updateCheckCompleted = true,
            hasUpdatePopup = false,
        )
        assertEquals(WhatsNewDecision.None, done)
        assertTrue(WhatsNewRules.shouldShowAnnouncementThisLaunch(hasUpdatePopup = false, whatsNewShownThisLaunch = false))
    }

    @Test
    fun waitsForUpdateCheckBeforeDecidingAndSupportsDebugForce() {
        assertEquals(
            WhatsNewDecision.Wait,
            WhatsNewRules.decide("1.3.0", "1.2.9", updateCheckCompleted = false, hasUpdatePopup = false),
        )
        assertEquals(
            WhatsNewDecision.ShowDialog,
            WhatsNewRules.decide("1.3.0", "1.3.0", updateCheckCompleted = false, hasUpdatePopup = false, debugForceShow = true),
        )
    }
}
