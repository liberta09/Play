package com.kaan.play.data.download

import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detay'daki indir düğmesi ve kaynak listesinin indirme modu. */
class DownloadPickTest {

    private val src = StreamSource(1, "Eklenti")
    private fun stream(url: String, res: Int? = 1080) =
        PlayableStream(source = src, url = url, resolution = res, sizeBytes = null, headers = emptyMap(), subtitles = emptyList())

    private val uhd = stream("https://a/4k.mp4", 2160)
    private val hd = stream("https://a/1080.m3u8", 1080)
    private val sd = stream("https://a/720.mp4", 720)
    private val magnet = stream("magnet:?xt=urn:btih:abc", 1080)
    private val ftp = stream("ftp://a/x.mp4", 1080)

    @Test
    fun torrentsAndNonHttpAreNeverCandidates() {
        assertEquals(listOf(uhd, hd, sd), DownloadPick.candidates(listOf(uhd, magnet, hd, ftp, sd)))
    }

    @Test
    fun onlyProbedDownloadableAreShownRecommendedFirst() {
        val all = listOf(uhd, hd, magnet, sd)
        val probed = mapOf(uhd.url to true, hd.url to true, sd.url to true)
        // Önerilen (≤1080p doğrudan) en üstte, diğerleri sıralı listedeki sırayla.
        assertEquals(listOf(hd, uhd, sd), DownloadPick.visible(all, probed))
    }

    @Test
    fun recommendedIsChosenAmongDownloadableOnes() {
        val all = listOf(uhd, hd, sd)
        // 1080p indirilemiyor: önerilen indirilebilirler arasından (720p) seçilir.
        val probed = mapOf(uhd.url to true, hd.url to false, sd.url to true)
        assertEquals(listOf(sd, uhd), DownloadPick.visible(all, probed))
    }

    @Test
    fun nothingDownloadableGivesEmptyListAfterProbing() {
        val all = listOf(hd, magnet)
        assertTrue(DownloadPick.pending(all, emptyMap()))
        val probed = mapOf(hd.url to false)
        assertFalse(DownloadPick.pending(all, probed))
        assertEquals(emptyList<PlayableStream>(), DownloadPick.visible(all, probed))
    }

    private fun row(status: DownloadStatus) = DownloadEntity(
        id = 1, tmdbId = 1, imdbId = "tt1", type = MediaType.Tv, season = 1, episode = 1, title = "Dizi", posterPath = null,
        sourceName = "Eklenti", sourceUrl = "https://a/1.mp4", status = status, createdAt = 1,
    )

    @Test
    fun contentBadgeIgnoresSourceUrl() {
        assertEquals(DownloadBadge.None, DownloadRules.contentBadge(null))
        assertEquals(DownloadBadge.Active, DownloadRules.contentBadge(row(DownloadStatus.Queued)))
        assertEquals(DownloadBadge.Active, DownloadRules.contentBadge(row(DownloadStatus.Downloading)))
        assertEquals(DownloadBadge.Active, DownloadRules.contentBadge(row(DownloadStatus.Paused)))
        assertEquals(DownloadBadge.Done, DownloadRules.contentBadge(row(DownloadStatus.Completed)))
        assertEquals(DownloadBadge.Failed, DownloadRules.contentBadge(row(DownloadStatus.Failed)))
    }
}
