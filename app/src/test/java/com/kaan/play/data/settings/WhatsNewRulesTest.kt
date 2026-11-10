package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSettingsDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class WhatsNewRulesTest {

    @Test
    fun freshInstallRecordsVersionSilentlyWithoutDialog() {
        val decision = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = null,
            updateCheckCompleted = true,
            hasUpdatePopup = false,
            isAppUpgrade = false,
        )
        assertEquals(WhatsNewDecision.RecordSilently("1.3.0"), decision)
        // İlk kurulumda pencere gösterilmediği için duyuru aynı açılışta gösterilebilir.
        assertTrue(WhatsNewRules.shouldShowAnnouncementThisLaunch(hasUpdatePopup = false, whatsNewShownThisLaunch = false))
    }

    @Test
    fun upgradeWithoutPriorWhatsNewKeyShowsDialogAndDefersWhenUpdateExists() {
        // 1.2.8 -> 1.3.0 yükseltmesinde last_whats_new_version henüz yoktur; yükseltme algılandığı için pencere açılır.
        val show = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = null,
            updateCheckCompleted = true,
            hasUpdatePopup = false,
            isAppUpgrade = true,
        )
        assertEquals(WhatsNewDecision.ShowDialog, show)

        val deferred = WhatsNewRules.decide(
            currentVersionName = "1.3.0",
            storedSeenVersion = null,
            updateCheckCompleted = true,
            hasUpdatePopup = true,
            isAppUpgrade = true,
        )
        assertEquals(WhatsNewDecision.DeferToNextLaunch, deferred)
    }

    @Test
    fun appUpgradeDetectionMatchesTimestampsOrPriorStoreKeys() = runTest {
        assertFalse(WhatsNewRules.isAppUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 1_000L, hasPriorStoredState = false))
        assertTrue(WhatsNewRules.isAppUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 2_000L, hasPriorStoredState = false))
        assertTrue(WhatsNewRules.isAppUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 1_000L, hasPriorStoredState = true))

        // Boş DataStore -> ilk kurulum -> sessiz kayıt.
        val freshStore = FakeSettingsDataStore()
        val freshSettings = SettingsRepository(freshStore)
        assertFalse(freshSettings.hasPriorAppState())
        val freshCoord = WhatsNewCoordinator("1.3.0", freshSettings)
        freshCoord.checkOnce(this)
        freshCoord.decided.first { it }
        assertFalse(freshCoord.visible.value)
        assertEquals("1.3.0", freshSettings.lastWhatsNewVersion.first())

        // Önceden default_catalog_addon_seeded yazılmış DataStore -> güncelleme -> Neler Yeni penceresi açılır.
        val upgradedStore = FakeSettingsDataStore()
        upgradedStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[SettingsRepository.DEFAULT_CATALOG_ADDON_SEEDED] = true
            }
        }
        val upgradedSettings = SettingsRepository(upgradedStore)
        assertTrue(upgradedSettings.hasPriorAppState())
        val upgradedCoord = WhatsNewCoordinator("1.3.0", upgradedSettings)
        upgradedCoord.checkOnce(this)
        upgradedCoord.decided.first { it }
        assertTrue(upgradedCoord.visible.value)
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
