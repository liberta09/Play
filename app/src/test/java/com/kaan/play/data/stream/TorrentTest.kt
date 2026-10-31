package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentTest {

    private val hash = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"

    @Test
    fun magnetIsBuiltFromHashAndAllowedTrackersOnly() {
        val magnet = TorrentLinks.magnet(hash, listOf("udp://t.example:1337/announce", "ftp://x/y", "bad tracker", "udp://t.example:1337/announce"))
        assertEquals(
            "magnet:?xt=urn:btih:${hash.lowercase()}&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce",
            magnet,
        )
        assertEquals(hash.lowercase(), TorrentLinks.hashOf(magnet!!))
        assertNull(TorrentLinks.magnet("not-a-hash"))
        assertNull(TorrentLinks.magnet(null))
    }

    @Test
    fun appendTrackersAddsMissingTrackersWithoutDuplication() {
        val base = "magnet:?xt=urn:btih:${hash.lowercase()}&tr=udp%3A%2F%2Ft1.example%3A1337%2Fannounce"
        val appended = TorrentLinks.appendTrackers(base, listOf("udp://t1.example:1337/announce", "udp://t2.example:1337/announce"))
        assertEquals(
            "magnet:?xt=urn:btih:${hash.lowercase()}&tr=udp%3A%2F%2Ft1.example%3A1337%2Fannounce&tr=udp%3A%2F%2Ft2.example%3A1337%2Fannounce",
            appended,
        )
        assertEquals("not-a-magnet", TorrentLinks.appendTrackers("not-a-magnet", listOf("udp://t.example:1337/announce")))
    }

    @Test
    fun nuvioTrackersMatchExactly() {
        val expectedNuvioTrackers = listOf(
            "udp://zer0day.ch:1337/announce",
            "udp://tracker.publictracker.xyz:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "http://tracker.renfei.net:8080/announce",
            "udp://udp.tracker.projectk.org:23333/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker.startwork.cv:1337/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.plx.im:6969/announce",
            "udp://tracker.nyaa.vc:6969/announce",
            "udp://tracker.iperson.xyz:6969/announce",
            "udp://tracker.gmi.gd:6969/announce",
            "udp://tracker.fnix.net:6969/announce",
            "udp://tracker.flatuslifir.is:6969/announce",
            "udp://tracker.ducks.party:1984/announce",
            "udp://tracker.bluefrog.pw:2710/announce",
        )
        assertEquals(20, expectedNuvioTrackers.size)
        assertEquals(20, expectedNuvioTrackers.distinct().size)
        assertEquals(expectedNuvioTrackers, com.kaan.play.data.torrent.TorrentEngine.PUBLIC_TRACKERS)
    }

    @Test
    fun serverAddressIsNormalizedOrRejected() {
        assertEquals("http://192.168.1.20:8090", TorrentLinks.normalizeServer("192.168.1.20:8090"))
        assertEquals("https://ts.example.com", TorrentLinks.normalizeServer(" https://ts.example.com/ "))
        assertEquals("http://nas.local:8090/ts", TorrentLinks.normalizeServer("http://nas.local:8090/ts/"))
        assertNull(TorrentLinks.normalizeServer(""))
        assertNull(TorrentLinks.normalizeServer("ftp://x"))
        assertNull(TorrentLinks.normalizeServer("a b"))
        assertNull(TorrentLinks.normalizeServer(null))
    }

    @Test
    fun serverUrlUsesOneBasedFileIndex() {
        val magnet = "magnet:?xt=urn:btih:${hash.lowercase()}"
        assertEquals(
            "http://h:8090/stream?link=magnet%3A%3Fxt%3Durn%3Abtih%3A${hash.lowercase()}&index=3&play",
            TorrentLinks.serverUrl("http://h:8090", magnet, 2),
        )
        assertTrue(TorrentLinks.serverUrl("http://h:8090", magnet, null).contains("&index=1&"))
    }

    @Test
    fun parserTurnsInfoHashAndMagnetIntoMagnetStreams() {
        val source = StreamSource(1, "A")
        val body = """
            {"streams":[
              {"name":"T 1080p","infoHash":"$hash","fileIdx":4,"sources":["tracker:udp://t.example:80","dht:$hash"]},
              {"name":"M","url":"magnet:?xt=urn:btih:${hash.lowercase()}"},
              {"name":"Bad","infoHash":"zzz"}
            ]}
        """
        val streams = StreamParser.parse(source, body)
        assertEquals(2, streams.size)
        assertEquals("magnet:?xt=urn:btih:${hash.lowercase()}&tr=udp%3A%2F%2Ft.example%3A80", streams[0].url)
        assertEquals(4, streams[0].torrentFileIdx)
        assertEquals(1080, streams[0].resolution)
        assertNull(streams[1].torrentFileIdx)
    }

    private val request = StreamRequest(tmdbId = 1, imdbId = "tt1", type = MediaType.Movie)
    private val source = StreamSource(1, "A")

    private fun stream(url: String, idx: Int? = null) =
        PlayableStream(source, url, null, null, emptyMap(), emptyList(), torrentFileIdx = idx)

    private fun provider(server: String?) = TorrentStreamProvider(
        inner = { flowOf(StreamResult.Found(source, listOf(stream("https://cdn/a.mp4"), stream("magnet:?xt=urn:btih:${hash.lowercase()}", 1)))) },
        server = { server },
    )

    @Test
    fun withServerMagnetStreamsBecomePlainHttpUrls() = runTest {
        val found = provider("192.168.1.20:8090").streams(request).toList().single() as StreamResult.Found
        assertEquals(2, found.streams.size)
        assertEquals("https://cdn/a.mp4", found.streams[0].url)
        assertTrue(found.streams[1].url.startsWith("http://192.168.1.20:8090/stream?link="))
        assertTrue(found.streams[1].url.endsWith("&index=2&play"))
        assertNull(found.streams[1].torrentFileIdx)
    }

    @Test
    fun withoutServerMagnetStreamsStayForTheBuiltInEngine() = runTest {
        var warmed = 0
        val provider = TorrentStreamProvider(
            inner = { flowOf(StreamResult.Found(source, listOf(stream("https://cdn/a.mp4"), stream("magnet:?xt=urn:btih:${hash.lowercase()}", 1)))) },
            server = { null },
            onBuiltIn = { warmed++ },
        )
        val found = provider.streams(request).toList().single() as StreamResult.Found
        assertEquals(listOf("https://cdn/a.mp4", "magnet:?xt=urn:btih:${hash.lowercase()}"), found.streams.map { it.url })
        assertEquals(1, found.streams[1].torrentFileIdx)
        assertEquals(1, warmed)
    }

    @Test
    fun hexHashOfConvertsBase32() {
        // base32("ABCDEF0123456789ABCDEF0123456789ABCDEF01" baytları)
        assertEquals(hash.lowercase(), TorrentLinks.hexHashOf("magnet:?xt=urn:btih:VPG66AJDIVTYTK6N54ASGRLHRGV433YB&dn=x"))
        assertEquals(hash.lowercase(), TorrentLinks.hexHashOf("magnet:?xt=urn:btih:$hash"))
        assertNull(TorrentLinks.hexHashOf("magnet:?dn=x"))
    }

    @Test
    fun directLinksAreRecommendedBeforeTorrents() {
        val torrent = stream("magnet:?xt=urn:btih:${hash.lowercase()}").copy(resolution = 1080)
        val direct = stream("https://cdn/a.mp4").copy(resolution = 720)
        assertEquals(direct, listOf(torrent, direct).recommended())
        assertEquals(torrent, listOf(torrent).recommended())
    }
}
