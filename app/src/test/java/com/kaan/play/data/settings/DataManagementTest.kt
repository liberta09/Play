package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.AppContainer
import com.kaan.play.data.library.ContinueSortMode
import com.kaan.play.data.provider.LiveTvSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataManagementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun resetAppearanceAndPlayerDefaultsResetsUiPlayerKeysAndPreservesAddonsAccountData() = runBlocking {
        val store = MemoryPreferencesStore()
        val repo = SettingsRepository(store)

        // Kullanıcı görünüm ve oynatıcı tercihlerini değiştirir
        repo.setThemeMode(ThemeMode.Light)
        repo.setResizeMode(ResizeMode.Fill)
        repo.setSubtitleLanguage("en")
        repo.updateSubtitleStyle { it.copy(size = SubtitleSize.Large, bold = true) }
        repo.setAutoSource(false)
        repo.setPreferDub(false)
        repo.setAlwaysResume(false)
        repo.setAutoNext(false)
        repo.setLiveTvSortMode(LiveTvSortMode.FavoritesFirst)
        repo.setContinueSortMode(ContinueSortMode.TitleAsc)

        // Aynı DataStore içinde saklanan eklenti/hesap/seeding anahtarları dokunulmamalıdır
        val externalProvidersKey = stringPreferencesKey("selected_providers")
        store.edit { it[externalProvidersKey] = "nuvio_addon:1" }
        repo.setCatalogEnabled(addonId = 4L, type = "movie", catalogId = "top", enabled = false)
        repo.setDefaultSubtitleAddonsSeeded(true)
        repo.setTorrentServer("http://192.168.1.5:8090")
        repo.setRememberLastProfile(false)

        repo.resetAppearanceAndPlayerDefaults()

        assertEquals(SettingsRepository.DEFAULT_THEME, repo.themeMode.first())
        assertEquals(ResizeMode.Fit, repo.resizeMode.first())
        assertEquals(SettingsRepository.DEFAULT_SUBTITLE_LANGUAGE, repo.subtitleLanguage.first())
        assertEquals(SubtitleStyle(), repo.subtitleStyle.first())
        assertEquals(SettingsRepository.DEFAULT_AUTO_SOURCE, repo.autoSource.first())
        assertEquals(SettingsRepository.DEFAULT_PREFER_DUB, repo.preferDub.first())
        assertEquals(SettingsRepository.DEFAULT_ALWAYS_RESUME, repo.alwaysResume.first())
        assertEquals(SettingsRepository.DEFAULT_AUTO_NEXT, repo.autoNext.first())
        assertEquals(SettingsRepository.DEFAULT_LIVE_TV_SORT_MODE, repo.liveTvSortMode.first())
        assertEquals(SettingsRepository.DEFAULT_CONTINUE_SORT_MODE, repo.continueSortMode.first())

        // Korunanlar: eklentiler, torrent sunucusu, profil hatırlama, sağlayıcı seçimi
        assertEquals(setOf("4:movie:top"), repo.disabledCatalogs.first())
        assertTrue(repo.defaultSubtitleAddonsSeeded.first())
        assertEquals("http://192.168.1.5:8090", repo.torrentServer.first())
        assertFalse(repo.rememberLastProfile.first())
        assertEquals("nuvio_addon:1", store.data.first()[externalProvidersKey])
    }

    @Test
    fun clearDirectoryContentsDeletesCachedFilesWithoutRemovingSiblingUserDirectories() {
        val cacheRoot = tempFolder.newFolder("cache")
        val subCache = File(cacheRoot, "subtitles").apply { mkdirs() }
        val searchCache = File(cacheRoot, "subtitle-search").apply { mkdirs() }
        val protectedDir = File(cacheRoot, "downloads").apply { mkdirs() }

        File(subCache, "abc.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nhi")
        File(searchCache, "def.json").writeText("{}")
        val protectedFile = File(protectedDir, "movie.mp4").apply { writeText("video") }

        AppContainer.clearDirectoryContents(subCache)
        AppContainer.clearDirectoryContents(searchCache)

        assertTrue(subCache.listFiles().orEmpty().isEmpty())
        assertTrue(searchCache.listFiles().orEmpty().isEmpty())
        assertTrue(protectedFile.exists())
    }
}
