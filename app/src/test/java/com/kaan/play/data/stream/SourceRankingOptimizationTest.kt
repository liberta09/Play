package com.kaan.play.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SourceRankingOptimizationTest {

    private val source1 = StreamSource(1, "Alpha")
    private val source2 = StreamSource(2, "Beta")
    private val source3 = StreamSource(3, "Gamma")

    private fun stream(
        source: StreamSource = source1,
        url: String,
        dubbed: Boolean = false,
        res: Int? = 1080,
        sizeBytes: Long? = 2_000_000_000L,
    ) = PlayableStream(
        source = source,
        url = url,
        resolution = res,
        sizeBytes = sizeBytes,
        headers = emptyMap(),
        subtitles = emptyList(),
        dubbed = dubbed,
    )

    private fun oldRecommended(streams: List<PlayableStream>, ctx: RankContext): List<PlayableStream> {
        fun oldResolutionTier(stream: PlayableStream): Int {
            val r = stream.resolution ?: return 2
            return if (r <= 1080) 0 else 1
        }
        val comparator = if (ctx.isLive) {
            compareBy<PlayableStream>(
                { if (SourceRanking.isTooHeavy(it, ctx)) 1 else 0 },
                { if (ctx.isSlowHost(it.url)) 1 else 0 },
                { if (SourceRanking.matchesPreference(it, ctx.preference)) 0 else 1 },
                { if (TorrentLinks.isMagnet(it.url)) 1 else 0 },
            )
        } else {
            compareBy<PlayableStream>(
                { if (SourceRanking.isTooHeavy(it, ctx)) 1 else 0 },
                { if (ctx.preferDub && !it.dubbed) 1 else 0 },
                { if (ctx.isSlowHost(it.url)) 1 else 0 },
                { if (SourceRanking.matchesPreference(it, ctx.preference)) 0 else 1 },
                { if (TorrentLinks.isMagnet(it.url)) 1 else 0 },
                { oldResolutionTier(it) },
                { -(it.resolution ?: 0) },
                { SourceRanking.sizeOf(it, ctx.sizes) ?: Long.MAX_VALUE },
            )
        }
        return streams.distinctBy { it.url }.sortedWith(comparator)
    }

    @Test
    fun hostOfReturnsQuestionMarkForMagnetAndInvalidUrlsWithoutException() {
        assertEquals("?", StreamLog.hostOf("magnet:?xt=urn:btih:1234567890abcdef"))
        assertEquals("?", StreamLog.hostOf("magnet:"))
        assertEquals("?", StreamLog.hostOf(""))
        assertEquals("?", StreamLog.hostOf("   "))
        assertEquals("?", StreamLog.hostOf("not-a-valid-url"))
        assertEquals("?", StreamLog.hostOf("file:///sdcard/video.mp4"))
        assertEquals("example.com", StreamLog.hostOf("https://example.com/video.mp4"))
        assertEquals("cdn.test.org", StreamLog.hostOf("http://cdn.test.org/stream.m3u8?token=123"))
    }

    @Test(timeout = 2000)
    fun recommendedFinishesUnder300msFor3000SourcesWithHalfMagnetsAnd200Hosts() {
        val dummySource = StreamSource(1, "Test")
        val streams = ArrayList<PlayableStream>(3000)
        // 1500 magnet kaynağı
        for (i in 0 until 1500) {
            streams.add(
                PlayableStream(
                    source = dummySource,
                    url = "magnet:?xt=urn:btih:%040x".format(i),
                    resolution = if (i % 2 == 0) 1080 else 720,
                    sizeBytes = 1_000_000_000L + i * 1_000_000L,
                    dubbed = i % 3 == 0,
                    headers = emptyMap(),
                    subtitles = emptyList(),
                )
            )
        }
        // 200 farklı ana makineye dağıtılmış 1500 HTTP kaynağı
        for (i in 0 until 1500) {
            val hostIndex = i % 200
            streams.add(
                PlayableStream(
                    source = dummySource,
                    url = "https://host$hostIndex.cdn.example.com/video/$i.mp4",
                    resolution = when (i % 4) {
                        0 -> 2160
                        1 -> 1080
                        2 -> 720
                        else -> 480
                    },
                    sizeBytes = 500_000_000L + i * 2_000_000L,
                    dubbed = i % 4 == 0,
                    headers = emptyMap(),
                    subtitles = emptyList(),
                )
            )
        }

        val ctx = RankContext(
            maxHeight = 1080,
            preferDub = true,
            isSlowHost = { host -> host.contains("host5.") || host.contains("host12.") }
        )

        // Isınma (JIT)
        SourceRanking.recommended(streams.take(100), ctx)

        val startNs = System.nanoTime()
        val result = SourceRanking.recommended(streams, ctx)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertEquals(3000, result.size)
        assertTrue(
            "recommended() 3000 kaynakta JVM'de 300 ms altında bitmeli (geçen süre: ${elapsedMs} ms)",
            elapsedMs < 300
        )
    }

    @Test
    fun oldAndNewSortingGiveExactSameResultAcross25MixedScenarios() {
        val pref1 = SourcePref(1, "Alpha")
        val pref2 = SourcePref(2, "Beta")

        val sSlow = "https://slow.cdn.net/file.mp4"
        val sFast1 = "https://fast1.cdn.net/file.mp4"
        val sFast2 = "https://fast2.cdn.net/file.mp4"
        val sMag1 = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111"
        val sMag2 = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222"

        val scenarios = listOf<Pair<List<PlayableStream>, RankContext>>(
            // 1. Boş liste
            emptyList<PlayableStream>() to RankContext(),
            // 2. Tek HTTP kaynağı
            listOf(stream(source1, sFast1)) to RankContext(),
            // 3. Tek magnet kaynağı
            listOf(stream(source1, sMag1)) to RankContext(),
            // 4. Mükerrer adresler
            listOf(stream(source1, sFast1), stream(source2, sFast1)) to RankContext(),
            // 5. VOD: Dublajlı vs Altyazılı (preferDub = true)
            listOf(stream(source1, sFast1, dubbed = false), stream(source2, sFast2, dubbed = true)) to RankContext(preferDub = true),
            // 6. VOD: Dublajlı vs Altyazılı (preferDub = false)
            listOf(stream(source1, sFast1, dubbed = false), stream(source2, sFast2, dubbed = true)) to RankContext(preferDub = false),
            // 7. VOD: Çözünürlük katmanları (4K > 1080p > 720p > null)
            listOf(
                stream(source1, "https://a/4k", res = 2160),
                stream(source1, "https://a/1080", res = 1080),
                stream(source1, "https://a/720", res = 720),
                stream(source1, "https://a/null", res = null),
            ) to RankContext(maxHeight = 1080),
            // 8. VOD: Çok büyük dosya (>15GB) geriye atılmalı
            listOf(
                stream(source1, "https://a/huge", sizeBytes = 20_000_000_000L),
                stream(source1, "https://a/normal", sizeBytes = 2_000_000_000L),
            ) to RankContext(),
            // 9. VOD: Tercih edilen kaynak eşleşmesi
            listOf(
                stream(source1, sFast1),
                stream(source2, sFast2),
            ) to RankContext(preference = pref2),
            // 10. VOD: Yavaş ana makine geriye atılmalı
            listOf(
                stream(source1, sSlow),
                stream(source2, sFast1),
            ) to RankContext(isSlowHost = { it.contains("slow") }),
            // 11. VOD: Aynı yavaş ana makineden birden çok akış
            listOf(
                stream(source1, "https://slow.cdn.net/1.mp4"),
                stream(source2, "https://slow.cdn.net/2.mp4"),
                stream(source3, sFast1),
            ) to RankContext(isSlowHost = { it.contains("slow") }),
            // 12. VOD: Magnet HTTP'den sonraya
            listOf(
                stream(source1, sMag1),
                stream(source2, sFast1),
            ) to RankContext(),
            // 13. Canlı yayın: temel akışlar
            listOf(
                stream(source1, sFast1, res = 720),
                stream(source2, sFast2, res = 1080),
            ) to RankContext(isLive = true),
            // 14. Canlı yayın: magnet ve yavaş makine
            listOf(
                stream(source1, sMag1),
                stream(source2, sSlow),
                stream(source3, sFast1),
            ) to RankContext(isLive = true, isSlowHost = { it.contains("slow") }),
            // 15. Canlı yayın: tercih edilen kaynak
            listOf(
                stream(source1, sFast1),
                stream(source2, sFast2),
            ) to RankContext(isLive = true, preference = pref2),
            // 16. Canlı yayın: çok büyük/yüksek çözünürlük
            listOf(
                stream(source1, "https://a/4k", res = 2160),
                stream(source2, "https://a/1080", res = 1080),
            ) to RankContext(isLive = true, maxHeight = 1080),
            // 17. Karışık 20 kaynak
            (0 until 20).map { i ->
                stream(
                    source = if (i % 2 == 0) source1 else source2,
                    url = if (i % 3 == 0) "magnet:?xt=urn:btih:%040x".format(i) else "https://cdn$i.example.com/v.mp4",
                    dubbed = i % 2 == 0,
                    res = when (i % 4) { 0 -> 2160; 1 -> 1080; 2 -> 720; else -> null },
                    sizeBytes = if (i % 5 == 0) null else 1_000_000_000L * (i + 1),
                )
            } to RankContext(preference = pref1, isSlowHost = { it.contains("cdn2") || it.contains("cdn7") }),
            // 18. Boyut önbelleği ile geçersiz kılma
            listOf(
                stream(source1, "https://a/1", sizeBytes = null),
                stream(source2, "https://a/2", sizeBytes = 5_000_000_000L),
            ) to RankContext(sizes = mapOf("https://a/1" to 1_000_000_000L)),
            // 19. Hepsi aynı çözünürlük, farklı boyut
            listOf(
                stream(source1, "https://a/big", sizeBytes = 8_000_000_000L),
                stream(source2, "https://a/small", sizeBytes = 1_000_000_000L),
                stream(source3, "https://a/mid", sizeBytes = 4_000_000_000L),
            ) to RankContext(),
            // 20. Hepsi aynı boyut, farklı çözünürlük
            listOf(
                stream(source1, "https://a/720", res = 720, sizeBytes = 2_000_000_000L),
                stream(source2, "https://a/1080", res = 1080, sizeBytes = 2_000_000_000L),
                stream(source3, "https://a/480", res = 480, sizeBytes = 2_000_000_000L),
            ) to RankContext(),
            // 21. Boyutu bilinmeyen sonda
            listOf(
                stream(source1, "https://a/unknown", sizeBytes = null),
                stream(source2, "https://a/known", sizeBytes = 500_000_000L),
            ) to RankContext(),
            // 22. Hepsi magnet
            listOf(stream(source1, sMag1), stream(source2, sMag2)) to RankContext(),
            // 23. Hepsi dublajlı
            listOf(stream(source1, sFast1, dubbed = true), stream(source2, sFast2, dubbed = true)) to RankContext(preferDub = true),
            // 24. Rastgele 50 akış (tohum 42)
            generateRandomStreams(50, 42) to RankContext(preferDub = true, maxHeight = 1080),
            // 25. Rastgele 50 akış canlı (tohum 99)
            generateRandomStreams(50, 99) to RankContext(isLive = true, maxHeight = 1080),
        )

        for ((index, pair) in scenarios.withIndex()) {
            val (streams, ctx) = pair
            val oldResult = oldRecommended(streams, ctx)
            val newResult = SourceRanking.recommended(streams, ctx)
            assertEquals("Senaryo #${index + 1} için eski ve yeni sıralama birebir aynı olmalı", oldResult, newResult)
        }
    }

    private fun generateRandomStreams(count: Int, seed: Long): List<PlayableStream> {
        val rand = Random(seed)
        val sources = listOf(source1, source2, source3)
        val resolutions = listOf(null, 480, 720, 1080, 2160)
        return (0 until count).map { i ->
            val isMag = rand.nextBoolean()
            val url = if (isMag) "magnet:?xt=urn:btih:%040x".format(i) else "https://cdn${rand.nextInt(10)}.net/$i.mp4"
            PlayableStream(
                source = sources[rand.nextInt(sources.size)],
                url = url,
                resolution = resolutions[rand.nextInt(resolutions.size)],
                sizeBytes = if (rand.nextBoolean()) null else rand.nextLong().coerceAtLeast(0) % 20_000_000_000L,
                headers = emptyMap(),
                subtitles = emptyList(),
                dubbed = rand.nextBoolean(),
            )
        }
    }
}
