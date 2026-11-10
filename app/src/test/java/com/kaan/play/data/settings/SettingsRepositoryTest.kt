package com.kaan.play.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.ui.nav.NavBarPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { java.io.File(tmp.root, "settings.preferences_pb") } // dosyayı DataStore kendisi oluşturur
    }

    private val repo by lazy { SettingsRepository(store) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsAreDarkThemeAnd15Seconds() = runBlocking {
        assertEquals(ThemeMode.Dark, repo.themeMode.first())
        assertEquals(15, repo.sourceTimeoutSeconds.first())
    }

    // Not: Windows'ta DataStore ikinci yazmada dosyanın üzerine yeniden adlandıramaz (Android'de sorun yok);
    // bu yüzden her testte dosyaya en çok bir kez yazılır.
    @Test
    fun themeIsStored() = runBlocking {
        repo.setThemeMode(ThemeMode.System)
        assertEquals(ThemeMode.System, repo.themeMode.first())
        assertEquals(15, repo.sourceTimeoutSeconds.first()) // diğer ayar etkilenmez
    }

    @Test
    fun timeoutIsStored() = runBlocking {
        repo.setSourceTimeoutSeconds(30)
        assertEquals(30, repo.sourceTimeoutSeconds.first())
        assertEquals(ThemeMode.Dark, repo.themeMode.first())
    }

    @Test
    fun unsupportedTimeoutIsRejected() = runBlocking {
        try {
            repo.setSourceTimeoutSeconds(7)
            fail("IllegalArgumentException bekleniyordu")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(15, repo.sourceTimeoutSeconds.first())
    }

    @Test
    fun corruptStoredValuesFallBackToDefaults() = runBlocking {
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "Neon"
            it[intPreferencesKey("source_timeout_seconds")] = 9999
        }
        assertEquals(ThemeMode.Dark, repo.themeMode.first())
        assertEquals(15, repo.sourceTimeoutSeconds.first())
    }

    @Test
    fun downloadWifiOnlyDefaultsToOn() = runBlocking {
        assertEquals(true, repo.downloadWifiOnly.first())
        assertEquals(true, SettingsRepository.DEFAULT_DOWNLOAD_WIFI_ONLY)
    }

    @Test
    fun downloadWifiOnlyCanBeSwitchedOff() = runBlocking {
        repo.setDownloadWifiOnly(false)
        assertEquals(false, repo.downloadWifiOnly.first())
        assertEquals(ThemeMode.Dark, repo.themeMode.first()) // diğer ayar etkilenmez
    }

    @Test
    fun preferredSubtitleLanguageDefaultsToTurkishAndIsStored() = runBlocking {
        assertEquals("tr", repo.subtitleLanguage.first())
        repo.setSubtitleLanguage("en")
        assertEquals("en", repo.subtitleLanguage.first())
    }

    @Test
    fun themeSelectableContainsAllSixThemesAndMapsStoredValues() {
        assertEquals(
            listOf(
                ThemeMode.Dark,
                ThemeMode.Light,
                ThemeMode.Amoled,
                ThemeMode.OceanBlue,
                ThemeMode.PurpleNight,
                ThemeMode.HighContrast,
            ),
            ThemeMode.SELECTABLE,
        )
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored("Dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStored("Light"))
        assertEquals(ThemeMode.System, ThemeMode.fromStored("System"))
        assertEquals(ThemeMode.Amoled, ThemeMode.fromStored("Amoled"))
        assertEquals(ThemeMode.OceanBlue, ThemeMode.fromStored("ocean_blue"))
        assertEquals(ThemeMode.PurpleNight, ThemeMode.fromStored("PurpleNight"))
        assertEquals(ThemeMode.HighContrast, ThemeMode.fromStored("HighContrast"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored("Unknown"))
        assertEquals(false, ThemeRules.isDark(ThemeMode.Light, systemIsDark = true))
        assertEquals(true, ThemeRules.isDark(ThemeMode.HighContrast, systemIsDark = false))
        assertEquals(ThemeMode.Amoled, ThemeRules.resolve(ThemeMode.Amoled, systemIsDark = false))
    }

    @Test
    fun newThemesAreStoredAndReadBack() = runBlocking {
        repo.setThemeMode(ThemeMode.PurpleNight)
        assertEquals(ThemeMode.PurpleNight, repo.themeMode.first())
    }

    @Test
    fun navBarPositionDefaultsToTopAndPersistsChoice() = runBlocking {
        assertEquals(NavBarPosition.Top, repo.navBarPosition.first())
        repo.setNavBarPosition(NavBarPosition.Left)
        assertEquals(NavBarPosition.Left, repo.navBarPosition.first())
    }
}
