package com.kaan.play.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** "Sonraki bölümü otomatik oynat" anahtarı ve geri sayım süresi (5/10/15). */
class AutoNextSettingsTest {

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
    fun defaultsAreOnAndTenSeconds() = runBlocking {
        assertTrue(repo.autoNext.first())
        assertEquals(10, repo.autoNextSeconds.first())
    }

    @Test
    fun canBeTurnedOff() = runBlocking {
        repo.setAutoNext(false)
        assertFalse(repo.autoNext.first())
    }

    // Her testte dosyaya en çok bir yazma (Windows'ta DataStore ardışık yazmalarda dosya yeniden adlandırmada takılır).
    @Test
    fun countdownCanBeFive() = runBlocking {
        repo.setAutoNextSeconds(5)
        assertEquals(5, repo.autoNextSeconds.first())
    }

    @Test
    fun countdownCanBeFifteen() = runBlocking {
        repo.setAutoNextSeconds(15)
        assertEquals(15, repo.autoNextSeconds.first())
    }

    @Test
    fun otherCountdownValuesAreRejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setAutoNextSeconds(7) } }
        assertEquals(10, repo.autoNextSeconds.first())
    }

    @Test
    fun unknownStoredCountdownFallsBackToTheDefault() = runBlocking {
        store.edit { it[intPreferencesKey("auto_next_seconds")] = 99 }
        assertEquals(SettingsRepository.DEFAULT_AUTO_NEXT_SECONDS, repo.autoNextSeconds.first())
    }
}
