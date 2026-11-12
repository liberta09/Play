package com.kaan.play.data.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioTrackLearningTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun stream(addonId: Long = 10L, name: String = "AddonA", url: String = "https://secret.example/video.mp4") =
        PlayableStream(StreamSource(addonId, name), url, 1080, null, emptyMap(), emptyList())

    private fun tracks(label1: String = "Audio 1", label2: String = "Audio 2") = listOf(
        AudioTrackInfo(0, "und", label1, isSelected = true, mime = "audio/mp4a-latm", channelCount = 2),
        AudioTrackInfo(1, null, label2, isSelected = false, mime = "audio/eac3", channelCount = 6),
    )

    @Test
    fun signatureNormalizesDigitsAndMatchesSameAndOtherContentFromSamePlugin() {
        val sig = AudioTrackLearning.buildSignature(tracks("Ses 01", "Ses 02"), chosenTrackIndex = 1)!!
        assertEquals(1, sig.trackIndex)
        assertEquals("audio/eac3", sig.mime)
        assertEquals(6, sig.channelCount)
        assertEquals("ses #", sig.labelPattern)
        assertEquals(2, sig.trackCount)

        val now = 1_000_000_000L
        val entries = listOf(
            LearnedAudioTrackEntry(
                pluginId = "id:10",
                contentKey = "tmdb:100",
                signature = sig,
                updatedAtMs = now,
            ),
        )
        // Aynı içerikte rakamlar değişse de ("Ses 99") kalıp tuttuğu için eşleşir.
        assertEquals(
            1,
            AudioTrackLearning.findMatchingTrackIndex(entries, "id:10", "tmdb:100", tracks("Ses 4", "Ses 99"), now + 1000L),
        )
        // Başka içerik, aynı eklenti, birebir aynı parça imzası -> eşleşir.
        assertEquals(
            1,
            AudioTrackLearning.findMatchingTrackIndex(entries, "id:10", "tmdb:200", tracks("Ses 12", "Ses 34"), now + 1000L),
        )
        // Farklı eklenti -> eşleşmez.
        assertNull(
            AudioTrackLearning.findMatchingTrackIndex(entries, "id:99", "tmdb:100", tracks("Ses 1", "Ses 2"), now + 1000L),
        )
        // İmza farklıysa (ör. kanal sayısı 2) -> eşleşmez.
        val mismatch = listOf(
            AudioTrackInfo(0, null, "Ses 1", isSelected = true, mime = "audio/mp4a-latm", channelCount = 2),
            AudioTrackInfo(1, null, "Ses 2", isSelected = false, mime = "audio/eac3", channelCount = 2),
        )
        assertNull(AudioTrackLearning.findMatchingTrackIndex(entries, "id:10", "tmdb:100", mismatch, now + 1000L))
    }

    @Test
    fun enforces60DayExpiry200EntryLimitAndUpdatesExisting() {
        val now = 20_000_000_000L
        val sig0 = AudioTrackSignature(0, "audio/aac", 2, "track #", 2)
        val sig1 = AudioTrackSignature(1, "audio/aac", 2, "track #", 2)
        val expired = LearnedAudioTrackEntry("id:1", "tmdb:1", sig0, updatedAtMs = now - AudioTrackLearning.MAX_AGE_MS - 1L)
        val existingList = mutableListOf(expired)
        for (i in 1..205) {
            existingList += LearnedAudioTrackEntry("id:1", "tmdb:$i", sig0, updatedAtMs = now - (300 - i) * 1000L)
        }
        val updated = AudioTrackLearning.pruneAndUpsert(
            existingList,
            LearnedAudioTrackEntry("id:1", "tmdb:50", sig1, updatedAtMs = now),
            now,
        )
        assertEquals(AudioTrackLearning.MAX_ENTRIES, updated.size)
        // Süresi dolmuş ya da en eski kayıtlar silinmiş, tmdb:50 sig1 olarak güncellenmiş olmalı.
        assertEquals(sig1, updated.first { it.contentKey == "tmdb:50" }.signature)
        assertFalse(updated.any { it.contentKey == "tmdb:1" && it.updatedAtMs < now - AudioTrackLearning.MAX_AGE_MS })
    }

    @Test
    fun storePersistsUnderExpectedKeyWithoutSavingUrls() = runBlocking {
        var now = 50_000L
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        val learning = AudioTrackLearningStore(ds) { now }
        val req = StreamRequest(550, "tt0137523", MediaType.Movie)
        val s = stream(addonId = 7L, url = "https://private-token.example/secret-stream.m3u8")

        learning.record(s, req, tracks(), chosenTrackIndex = 1)
        val raw = ds.data.first()[AudioTrackLearningStore.KEY].orEmpty()
        assertFalse(raw.contains("private-token"))
        assertFalse(raw.contains("secret-stream"))
        assertEquals(1, learning.findMatch(s, req, tracks()))

        // Dil bilgisi olan parçalarda öğrenilmiş otomatik seçim devreye girmez.
        val labeled = listOf(
            AudioTrackInfo(0, "en", "English", isSelected = true, mime = "audio/mp4a-latm", channelCount = 2),
            AudioTrackInfo(1, "tr", "Türkçe", isSelected = false, mime = "audio/eac3", channelCount = 6),
        )
        assertNull(learning.findMatch(s, req, labeled))
    }
}
