package com.kaan.play.data.stream

import com.kaan.play.data.stream.StreamSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SlowHostMemoryTest {

    private val source1 = StreamSource(1, "Enel")
    private val source2 = StreamSource(2, "Dragon")
    private val source3 = StreamSource(3, "Ace")

    private fun stream(
        source: StreamSource,
        url: String,
        dubbed: Boolean = false,
        res: Int = 1080,
    ) = PlayableStream(
        source = source,
        url = url,
        resolution = res,
        sizeBytes = 2_000_000_000L,
        headers = emptyMap(),
        subtitles = emptyList(),
        dubbed = dubbed,
    )

    @Before
    @After
    fun cleanUp() {
        SlowHostMemory.clear()
    }

    @Test
    fun marksHostSlowForThirtyMinutes() {
        val url = "https://slow-cdn.example/video.mp4?token=secret"
        val startMs = 1_000_000L

        assertFalse(SlowHostMemory.isSlow(url, startMs))

        SlowHostMemory.markSlow(url, startMs)

        // 29. dakikada hâlâ yavaş
        assertTrue(SlowHostMemory.isSlow(url, startMs + 29 * 60 * 1000L))

        // 30. dakika dolduğunda artık yavaş değil
        assertFalse(SlowHostMemory.isSlow(url, startMs + 30 * 60 * 1000L + 1L))
    }

    @Test
    fun invalidOrMagnetUrlHandledSafely() {
        assertFalse(SlowHostMemory.isSlow("magnet:?xt=urn:btih:123", 1000L))
        SlowHostMemory.markSlow("magnet:?xt=urn:btih:123", 1000L)
        assertFalse(SlowHostMemory.isSlow("magnet:?xt=urn:btih:123", 1000L))

        assertFalse(SlowHostMemory.isSlow("not-a-valid-url", 1000L))
        SlowHostMemory.markSlow("not-a-valid-url", 1000L)
        assertFalse(SlowHostMemory.isSlow("not-a-valid-url", 1000L))
    }

    @Test
    fun slowHostDemotedBehindFastHostInSameDubbingGroup() {
        val slowDub = stream(source1, "https://slow.example/dub.mp4", dubbed = true, res = 1080)
        val fastDub = stream(source2, "https://fast.example/dub.mp4", dubbed = true, res = 1080)
        val fastSub = stream(source3, "https://fast.example/sub.mp4", dubbed = false, res = 1080)

        val nowMs = 100_000L
        SlowHostMemory.markSlow(slowDub.url, nowMs)

        val ctx = RankContext(
            preferDub = true,
            isSlowHost = { url -> SlowHostMemory.isSlow(url, nowMs) },
        )

        val ranked = SourceRanking.recommended(listOf(slowDub, fastDub, fastSub), ctx)

        // Sıralama: 1. fastDub (dublajlı + hızlı), 2. slowDub (dublajlı + yavaş), 3. fastSub (altyazılı)
        assertEquals(listOf(fastDub, slowDub, fastSub), ranked)
    }

    @Test
    fun slowHostInSubbedGroupDemotedBehindFastSubbed() {
        val slowSub = stream(source1, "https://slow.example/sub.mp4", dubbed = false, res = 1080)
        val fastSub = stream(source2, "https://fast.example/sub.mp4", dubbed = false, res = 1080)

        val nowMs = 100_000L
        SlowHostMemory.markSlow(slowSub.url, nowMs)

        val ctx = RankContext(
            preferDub = true,
            isSlowHost = { url -> SlowHostMemory.isSlow(url, nowMs) },
        )

        val ranked = SourceRanking.recommended(listOf(slowSub, fastSub), ctx)

        assertEquals(listOf(fastSub, slowSub), ranked)
    }

    @Test
    fun dubbingPriorityStrictlyPreservedEvenIfDubbedHostIsSlow() {
        val slowDub = stream(source1, "https://slow.example/dub.mp4", dubbed = true, res = 1080)
        val fastSub = stream(source2, "https://fast.example/sub.mp4", dubbed = false, res = 1080)

        val nowMs = 100_000L
        SlowHostMemory.markSlow(slowDub.url, nowMs)

        val ctx = RankContext(
            preferDub = true,
            isSlowHost = { url -> SlowHostMemory.isSlow(url, nowMs) },
        )

        val ranked = SourceRanking.recommended(listOf(slowDub, fastSub), ctx)

        // Dublaj önceliği açıkken yavaş dublajlı kaynak, hızlı altyazılı kaynağın ÖNÜNDEDİR (grup önceliği bozulmaz)
        assertEquals(listOf(slowDub, fastSub), ranked)
    }
}
