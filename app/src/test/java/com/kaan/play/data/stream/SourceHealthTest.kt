package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthTest {

    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val srcA = StreamSource(10L, "AddonA")
    private val srcB = StreamSource(20L, "AddonB")

    @Test
    fun sanitizeErrorStripsUrlsPathsQueriesCookiesTokensAndClampsLength() {
        val httpRaw = "HTTP 403 https://secret.cdn.example.com/private/movie.mp4?token=abc123&cookie=sid=xyz"
        assertEquals("HTTP 403", SourceHealthRules.sanitizeError(httpRaw))

        val httpEqRaw = "ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=503"
        assertEquals("HTTP 503", SourceHealthRules.sanitizeError(httpEqRaw))

        val customLeaky = "Sunucu reddetti /gizli/yol/video.m3u8?auth=123 cookie=sid bearer abcdef https://leak.site/x?y=z"
        val cleaned = SourceHealthRules.sanitizeError(customLeaky)
        assertFalse(cleaned.contains("https://"))
        assertFalse(cleaned.contains("/gizli"))
        assertFalse(cleaned.contains("auth="))
        assertFalse(cleaned.contains("cookie"))
        assertFalse(cleaned.contains("bearer"))
        assertFalse(cleaned.contains("leak.site"))
        assertTrue(cleaned.length <= SourceHealthRules.MAX_ERROR_TEXT_LENGTH)

        val longText = "a".repeat(120)
        assertEquals(SourceHealthRules.MAX_ERROR_TEXT_LENGTH, SourceHealthRules.sanitizeError(longText).length)
    }

    @Test
    fun errorHistoryIsCappedAt50NewestFirstAndDisplays8() {
        var state = SourceHealthState()
        for (i in 1..65) {
            state = SourceHealthRules.recordFailure(
                state = state,
                source = srcA,
                url = "https://cdn$i.example.com/path/stream.mp4?token=t$i",
                detail = "HTTP 503",
                nowMs = i * 1_000L,
            )
        }
        assertEquals(50, state.errors.size)
        assertEquals("cdn65.example.com", state.errors.first().host)
        assertEquals("cdn16.example.com", state.errors.last().host)
        val shown = SourceHealthRules.recentErrors(state)
        assertEquals(8, shown.size)
        assertEquals("cdn65.example.com", shown.first().host)
        assertEquals("cdn58.example.com", shown.last().host)
    }

    @Test
    fun reliabilityComputesPercentageFiltersMin2AttemptsAndSortsWorstFirst() {
        var state = SourceHealthState()
        // srcA + cdn1: 1 başarı, 2 hata -> %33, 3 deneme
        state = SourceHealthRules.recordSuccess(state, srcA, "https://cdn1.example.com/a.mp4", 1L)
        state = SourceHealthRules.recordFailure(state, srcA, "https://cdn1.example.com/a.mp4", "timeout", 2L)
        state = SourceHealthRules.recordFailure(state, srcA, "https://cdn1.example.com/a.mp4", "timeout", 3L)
        // srcB + cdn2: 2 başarı, 0 hata -> %100, 2 deneme
        state = SourceHealthRules.recordSuccess(state, srcB, "https://cdn2.example.com/b.mp4", 4L)
        state = SourceHealthRules.recordSuccess(state, srcB, "https://cdn2.example.com/b.mp4", 5L)
        // srcA + cdn3: tek deneme -> listede görünmez
        state = SourceHealthRules.recordFailure(state, srcA, "https://cdn3.example.com/c.mp4", "timeout", 6L)

        val worst = SourceHealthRules.worstReliability(state)
        assertEquals(2, worst.size)
        assertEquals("cdn1.example.com", worst[0].host)
        assertEquals(33, worst[0].successPercent)
        assertEquals(3, worst[0].totalAttempts)
        assertEquals("cdn2.example.com", worst[1].host)
        assertEquals(100, worst[1].successPercent)
    }

    @Test
    fun blacklistTriggersAfter3ConsecutiveFailuresFor6HoursAndLiftsOnSuccessOrExpiryOrReenable() {
        var state = SourceHealthState()
        val url = "https://badcdn.example.com/video/1080p.mp4?token=secret"
        val t0 = 1_000_000L

        state = SourceHealthRules.recordFailure(state, srcA, url, "HTTP 503", t0)
        state = SourceHealthRules.recordFailure(state, srcA, url, "HTTP 503", t0 + 1_000L)
        assertFalse(SourceHealthRules.isUrlBlacklisted(state, url, t0 + 1_000L))

        state = SourceHealthRules.recordFailure(state, srcA, url, "HTTP 503", t0 + 2_000L)
        assertTrue(SourceHealthRules.isUrlBlacklisted(state, url, t0 + 2_000L))
        assertEquals(1, SourceHealthRules.activeBlacklists(state, t0 + 2_000L).size)

        // 6 saatten 1 ms önce hâlâ engelli, tam 6 saat dolunca kalkar
        val expiresAt = t0 + 2_000L + SourceHealthRules.BLACKLIST_DURATION_MS
        assertTrue(SourceHealthRules.isUrlBlacklisted(state, url, expiresAt - 1L))
        assertFalse(SourceHealthRules.isUrlBlacklisted(state, url, expiresAt))
        assertTrue(SourceHealthRules.activeBlacklists(state, expiresAt).isEmpty())

        // Başarılı açılış sayacı ve engeli sıfırlar
        val recovered = SourceHealthRules.recordSuccess(state, srcA, url, t0 + 3_000L)
        assertFalse(SourceHealthRules.isUrlBlacklisted(recovered, url, t0 + 3_000L))
        val failAgain = SourceHealthRules.recordFailure(recovered, srcA, url, "timeout", t0 + 4_000L)
        assertFalse(SourceHealthRules.isUrlBlacklisted(failAgain, url, t0 + 4_000L))

        // Manuel yeniden etkinleştirme de engeli kaldırır
        val manualLifted = SourceHealthRules.reenableHost(state, "badcdn.example.com")
        assertFalse(SourceHealthRules.isUrlBlacklisted(manualLifted, url, t0 + 3_000L))
    }

    @Test
    fun localTorrentHostIsNeverBlacklisted() {
        var state = SourceHealthState()
        val localUrl = "http://127.0.0.1:42069/stream.mkv"
        repeat(5) { idx ->
            state = SourceHealthRules.recordFailure(state, srcA, localUrl, "HTTP 500", idx * 1_000L)
        }
        assertFalse(SourceHealthRules.isUrlBlacklisted(state, localUrl, 10_000L))
        assertTrue(SourceHealthRules.activeBlacklists(state, 10_000L).isEmpty())
    }

    @Test
    fun pluginHealthKeepsRollingWindowOfLast20Calls() {
        var state = SourceHealthState()
        // Önce 15 kez yanıt vermedi, sonra 10 kez akış verdi => son 20 çağrıda yalnızca 10 yanıt vermedi + 10 akış verdi kalır
        repeat(15) { i ->
            state = SourceHealthRules.recordPluginCall(state, srcA, PluginCallOutcome.Unresponsive, i.toLong())
        }
        repeat(10) { i ->
            state = SourceHealthRules.recordPluginCall(state, srcA, PluginCallOutcome.ReturnedStreams, 20L + i)
        }
        val rec = state.pluginHealth.values.single()
        assertEquals(20, rec.totalCalls)
        assertEquals(10, rec.unresponsiveCount)
        assertEquals(10, rec.returnedStreamsCount)
        assertEquals(0, rec.noStreamsCount)
    }

    @Test
    fun storeClearHealthLogsClearsSourceHealthAndAudioSyncButPreservesWrongContentBlocks() = runBlocking {
        val prefs = MemoryPreferencesStore()
        val healthStore = SourceHealthStore(prefs, clock = { 10_000L })
        val audioSyncStore = AudioSyncReportStore(prefs)
        val blockStore = WrongContentBlockStore(prefs, clock = { 10_000L })
        val req = StreamRequest(1, "tt123", com.kaan.play.data.model.MediaType.Movie, title = "Matrix", year = 1999)

        repeat(3) {
            healthStore.recordFailure(srcA, "https://cdn.example.com/v.mp4", "timeout")
        }
        audioSyncStore.increment(srcA)
        blockStore.block(req, srcA)

        healthStore.clearHealthLogs()
        audioSyncStore.clear()

        assertTrue(healthStore.state.value.errors.isEmpty())
        assertTrue(healthStore.state.value.reliability.isEmpty())
        assertTrue(healthStore.state.value.hostFailures.isEmpty())
        assertTrue(audioSyncStore.counts.first().isEmpty())
        // Yanlış içerik engeli KESİNLİKLE korunur!
        assertTrue(blockStore.isBlocked(req, srcA))
    }
}
