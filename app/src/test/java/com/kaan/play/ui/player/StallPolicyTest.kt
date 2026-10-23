package com.kaan.play.ui.player

import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.PlayableStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StallPolicyTest {

    private val source1 = StreamSource(1, "Enel")
    private val source2 = StreamSource(2, "Dragon")
    private val source3 = StreamSource(3, "Ace")

    private fun stream(
        source: StreamSource,
        url: String,
        dubbed: Boolean = false,
    ) = PlayableStream(
        source = source,
        url = url,
        resolution = 1080,
        sizeBytes = 2_000_000_000L,
        headers = emptyMap(),
        subtitles = emptyList(),
        dubbed = dubbed,
    )

    private val dubbedCandidate = stream(source2, "https://dragon.example/dub", dubbed = true)
    private val subbedCandidate = stream(source3, "https://ace.example/sub", dubbed = false)

    @Test
    fun beforeFirstFrameNoDecision() {
        val decision = StallPolicy.evaluate(
            nowMs = 15_000L,
            firstFrameRendered = false,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, decision)
    }

    @Test
    fun whileSeekingNoDecision() {
        val decision = StallPolicy.evaluate(
            nowMs = 15_000L,
            firstFrameRendered = true,
            isSeeking = true,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, decision)
    }

    @Test
    fun notCurrentlyBufferingNoDecision() {
        val decision = StallPolicy.evaluate(
            nowMs = 150_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = null,
            pastStalls = listOf(StallInterval(100_000L, 120_000L)),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, decision)
    }

    @Test
    fun singleStallExactBoundary() {
        // 9_999 ms -> None
        val under = StallPolicy.evaluate(
            nowMs = 9_999L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, under)

        // 10_000 ms -> Switch(Single)
        val exact = StallPolicy.evaluate(
            nowMs = 10_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.Switch(dubbedCandidate, SwitchReason.Single), exact)
    }

    @Test
    fun windowStallExactBoundary() {
        // Geçmiş takılma 8 sn: [100_000, 108_000]
        val past = listOf(StallInterval(100_000L, 108_000L))
        // Mevcut takılma 6.999 sn: [110_000, 116_999] -> toplam 14.999 sn (< 15 sn)
        val under = StallPolicy.evaluate(
            nowMs = 116_999L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 110_000L,
            pastStalls = past,
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, under)

        // Mevcut takılma 7.000 sn: [110_000, 117_000] -> toplam 15.000 sn (== 15 sn)
        val exact = StallPolicy.evaluate(
            nowMs = 117_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 110_000L,
            pastStalls = past,
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.Switch(dubbedCandidate, SwitchReason.Window), exact)
    }

    @Test
    fun oldStallsOutsideTwoMinuteWindowArePruned() {
        val now = 200_000L
        // 2 dakika öncesi = 80_000 ms.
        // Tamamen dışarıda olan takılma: [50_000, 70_000] (20 sn sürmüş ama 2 dk öncesi)
        // Kısmen pencereye sarkan takılma: [75_000, 85_000] (yalnızca 80_000-85_000 arası 5 sn pencerede)
        val past = listOf(
            StallInterval(50_000L, 70_000L),
            StallInterval(75_000L, 85_000L),
        )
        // Mevcut takılma 9.000 ms: [191_000, 200_000]. Toplam pencere = 5 sn + 9 sn = 14 sn (< 15 sn).
        val decisionUnder = StallPolicy.evaluate(
            nowMs = now,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 191_000L,
            pastStalls = past,
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.None, decisionUnder)

        // Mevcut takılma 10.000 ms olursa tek takılma eşiğinden geçiş yapar
        val decisionExact = StallPolicy.evaluate(
            nowMs = now + 1_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 191_000L,
            pastStalls = past,
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.Switch(dubbedCandidate, SwitchReason.Single), decisionExact)
    }

    @Test
    fun dubbedStreamPrefersDubbedCandidateOverSubbed() {
        val decision = StallPolicy.evaluate(
            nowMs = 10_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = listOf(subbedCandidate, dubbedCandidate),
        )
        // Sıralamada altyazılı önde gelse dahi dublajlı seçilir
        assertEquals(StallDecision.Switch(dubbedCandidate, SwitchReason.Single), decision)
    }

    @Test
    fun dubbedStreamDoesNotSwitchToSubtitledBeforeTwentySeconds() {
        // Dublajlı aday yok, yalnızca altyazılı aday var.
        val candidates = listOf(subbedCandidate)

        // 10 sn tek takılma: Dublajlı yoksa 20 sn beklenmeli -> None
        val at10s = StallPolicy.evaluate(
            nowMs = 10_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = candidates,
        )
        assertEquals(StallDecision.None, at10s)

        // 19_999 ms: Hâlâ 20 sn dolmadı -> None
        val at19s = StallPolicy.evaluate(
            nowMs = 19_999L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = candidates,
        )
        assertEquals(StallDecision.None, at19s)

        // 20_000 ms: Kesintisiz 20 sn doldu -> Switch(Subtitled)
        val at20s = StallPolicy.evaluate(
            nowMs = 20_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = candidates,
        )
        assertEquals(StallDecision.Switch(subbedCandidate, SwitchReason.Subtitled), at20s)
    }

    @Test
    fun windowStallDoesNotTriggerPrematureSubtitledSwitchForDubbedStream() {
        // Pencere toplamı 16 sn olsa bile mevcut takılma 6 sn ise altyazılıya düşmez
        val past = listOf(StallInterval(10_000L, 20_000L)) // 10 sn
        val candidates = listOf(subbedCandidate)
        val decision = StallPolicy.evaluate(
            nowMs = 36_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 30_000L, // 6 sn
            pastStalls = past,
            currentIsDubbed = true,
            isManual = false,
            candidates = candidates,
        )
        assertEquals(StallDecision.None, decision)
    }

    @Test
    fun subtitledStreamSwitchesToFirstCandidate() {
        val decision = StallPolicy.evaluate(
            nowMs = 10_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = false,
            isManual = false,
            candidates = listOf(subbedCandidate),
        )
        assertEquals(StallDecision.Switch(subbedCandidate, SwitchReason.Single), decision)
    }

    @Test
    fun noCandidatesReturnsNone() {
        val decision = StallPolicy.evaluate(
            nowMs = 15_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = false,
            candidates = emptyList(),
        )
        assertEquals(StallDecision.None, decision)
    }

    @Test
    fun manualSelectionReturnsNotifyManualSlowWithoutSwitching() {
        val decision = StallPolicy.evaluate(
            nowMs = 12_000L,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = 0L,
            pastStalls = emptyList(),
            currentIsDubbed = true,
            isManual = true,
            candidates = listOf(dubbedCandidate),
        )
        assertEquals(StallDecision.NotifyManualSlow, decision)
    }
}
