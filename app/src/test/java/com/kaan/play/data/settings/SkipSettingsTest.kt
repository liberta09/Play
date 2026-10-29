package com.kaan.play.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Atlama ayarları: düğmeler açık, otomatik atlama kapalı, dört tür açık gelir; her biri ayrı değişir. */
class SkipSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
    }

    private val repo by lazy { SettingsRepository(store) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsShowButtonsWithoutAutomaticSkipAndAllCategories() = runBlocking {
        val settings = repo.skipPlayback.first()
        assertTrue(settings.showButtons)
        assertFalse(settings.autoSkip)
        assertEquals(SkipCategory.entries.toSet(), settings.enabledCategories)
    }

    // Her testte dosyaya en çok bir yazma (Windows'ta DataStore ardışık yazmalarda dosya yeniden adlandırmada takılır).
    @Test
    fun buttonsCanBeHidden() = runBlocking {
        repo.setSkipButtonsEnabled(false)
        assertFalse(repo.skipPlayback.first().showButtons)
    }

    @Test
    fun automaticSkipCanBeEnabled() = runBlocking {
        repo.setAutoSkipEnabled(true)
        assertTrue(repo.skipPlayback.first().autoSkip)
    }

    @Test
    fun aSingleCategoryCanBeTurnedOff() = runBlocking {
        repo.setSkipCategoryEnabled(SkipCategory.Credits, false)
        val settings = repo.skipPlayback.first()
        assertFalse(settings.isEnabled(SkipCategory.Credits))
        assertTrue(settings.isEnabled(SkipCategory.Intro))
        assertTrue(settings.isEnabled(SkipCategory.Recap))
        assertTrue(settings.isEnabled(SkipCategory.Preview))
    }
}
