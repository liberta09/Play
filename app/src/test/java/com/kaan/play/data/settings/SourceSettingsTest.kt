package com.kaan.play.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SourceMemory
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * "Kaynağı otomatik seç", "Türkçe dublajı öncele", yeniden boyutlandırma seçimi ve elle seçilen kaynağın hatırlanması
 * (her testte dosyaya en çok bir yazma: Windows'ta DataStore ardışık yazmalarda dosya yeniden adlandırmada takılır).
 */
class SourceSettingsTest {

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
    fun defaultsAreAutoSourceOnPreferDubOnAndFitResize() = runBlocking {
        assertTrue(repo.autoSource.first())
        assertTrue(repo.preferDub.first())
        assertTrue(repo.trySourcesUntilTurkishAudio.first())
        assertEquals(7, repo.audioProbeTimeoutSeconds.first())
        assertEquals(ResizeMode.Fit, repo.resizeMode.first())
    }

    @Test
    fun trySourcesUntilTurkishAudioCanBeTurnedOff() = runBlocking {
        repo.setTrySourcesUntilTurkishAudio(false)
        assertFalse(repo.trySourcesUntilTurkishAudio.first())
    }

    @Test
    fun audioProbeTimeoutIsSaved() = runBlocking {
        repo.setAudioProbeTimeoutSeconds(10)
        assertEquals(10, repo.audioProbeTimeoutSeconds.first())
    }

    @Test
    fun autoSourceCanBeTurnedOff() = runBlocking {
        repo.setAutoSource(false)
        assertFalse(repo.autoSource.first())
        assertTrue(repo.preferDub.first())
    }

    @Test
    fun preferDubCanBeTurnedOff() = runBlocking {
        repo.setPreferDub(false)
        assertFalse(repo.preferDub.first())
        assertTrue(repo.autoSource.first())
    }

    @Test
    fun resizeModeIsSaved() = runBlocking {
        repo.setResizeMode(ResizeMode.Ratio16x9)
        assertEquals(ResizeMode.Ratio16x9, repo.resizeMode.first())
    }

    @Test
    fun rememberedSourceIsFoundForTheSameShowAndNotForOthers() = runBlocking {
        val memory = SourceMemory(store)
        val request = StreamRequest(1396, "tt0903747", MediaType.Tv, 1, 2)
        assertNull(memory.get(request))

        val stream = PlayableStream(StreamSource(7, "Sahte B"), "https://b.example/file.mkv", 1080, null, emptyMap(), emptyList())
        memory.remember(request, stream)

        // Dizinin sonraki bölümü aynı tercihi görür; başka içerik ya da film görmez.
        val pref = memory.get(request.copy(episode = 3))
        assertNotNull(pref)
        assertEquals(7L, pref!!.addonId)
        assertEquals("Sahte B", pref.addonName)
        assertEquals("b.example", pref.host)
        assertFalse(pref.torrent)
        assertNull(memory.get(request.copy(tmdbId = 1)))
        assertNull(memory.get(StreamRequest(1396, "tt0903747", MediaType.Movie)))
    }
}
