package com.kaan.play.data.sync.local

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayStreamsTest {
    private val source = StreamSource(1, "AnyShort")

    private fun stream(url: String, transport: Boolean = false, interceptor: Interceptor? = null) = PlayableStream(
        source = source, url = url, resolution = 720, sizeBytes = null,
        headers = mapOf("Referer" to "https://site.example/"), subtitles = emptyList(),
        providerTransportRequired = transport, httpInterceptor = interceptor,
    )

    private val target = PlayTarget(
        StreamRequest(tmdbId = 0, imdbId = null, type = MediaType.Tv, season = 1, episode = 1, pluginId = "cs:105", contentUrl = "https://site.example/d/1"),
        title = "Gizli Güç", posterPath = null,
    )

    @Test
    fun incomingStreamsAreCheckedAgainOnTheReceiver() {
        val ok = stream("https://cdn.example/v/1.m3u8").copy(
            headers = mapOf("Referer" to "https://site.example/", "X-Forwarded-For" to "1.2.3.4", "Host" to "evil"),
            subtitles = listOf(
                com.kaan.play.data.stream.StreamSubtitle("https://cdn.example/s.vtt", "tr"),
                com.kaan.play.data.stream.StreamSubtitle("http://172.16.1.5/s.vtt", "tr"),
            ),
        )
        val bad = listOf(
            stream("http://172.16.1.5/v.m3u8"), stream("http://10.1.2.3/v.m3u8"), stream("http://169.254.1.1/v.m3u8"),
            stream("http://127.0.0.1:8090/v"), stream("http://[::1]/v"), stream("http://[fe80::1]/v"),
            stream("http://printer.local/v"), stream("file:///sdcard/v.mp4"), stream("https://user:pw@cdn.example/v"),
            stream("https://cdn.example/v", transport = true),
        )
        val accepted = RemotePlayStreams.acceptIncoming(listOf(ok) + bad)
        assertEquals(1, accepted.size)
        assertEquals(mapOf("Referer" to "https://site.example/"), accepted[0].headers)
        assertEquals(listOf("https://cdn.example/s.vtt"), accepted[0].subtitles.map { it.url })
        assertTrue(RemotePlayStreams.acceptIncoming(bad).isEmpty())
        assertEquals(RemotePlayStreams.MAX_STREAMS, RemotePlayStreams.acceptIncoming(List(9) { ok }).size)
    }

    @Test
    fun receiverRejectsObfuscatedPrivateAddresses() {
        val rejected = listOf(
            "http://[::ffff:172.16.1.1]/v", "http://[::ffff:7f00:1]/v", "http://[::ffff:a9fe:101]/v", "http://[fd12::1]/v",
            "http://[::]/v", "http://[ff02::1]/v", "http://2130706433/v", "http://0x7f.1/v", "http://0x7f000001/v",
            "http://127.1/v", "http://017.0.0.1/v", "http://100.64.0.1/v", "http://100.127.255.254/v", "http://0.0.0.0/v",
            "http://255.255.255.255/v", "http://224.0.0.1/v", "http://1.2.3.4.5/v", "http://bad_host.example/v",
        )
        for (url in rejected) assertFalse(url, RemotePlayStreams.safeRemoteUrl(url))
        val accepted = listOf(
            "https://cdn.example.com/v/1.m3u8", "http://video.example.org:8080/a.mp4", "https://8.8.8.8/v",
            "https://[2606:4700::1111]/v", "https://100.128.0.1/v", "https://cdn-1.example.co.uk./v",
        )
        for (url in accepted) assertTrue(url, RemotePlayStreams.safeRemoteUrl(url))
    }

    @Test
    fun castable_onlyPlainRemoteHttp() {
        assertTrue(RemotePlayStreams.castable(stream("https://cdn.example/v/1.m3u8")))
        assertTrue(RemotePlayStreams.castable(stream("http://cdn.example/v/1.mp4")))
        assertFalse(RemotePlayStreams.castable(stream("magnet:?xt=urn:btih:abc")))
        assertFalse(RemotePlayStreams.castable(stream("http://127.0.0.1:8090/stream/1"))) // telefondaki yerel motor/vekil
        assertFalse(RemotePlayStreams.castable(stream("http://localhost:8080/a.m3u8")))
        assertFalse(RemotePlayStreams.castable(stream("https://cdn.example/v/1.m3u8", transport = true)))
        assertFalse(RemotePlayStreams.castable(stream("https://cdn.example/v/1.m3u8", interceptor = Interceptor { it.proceed(it.request()) })))
    }

    @Test
    fun collect_takesCastableStreamsUpToLimit() = runBlocking {
        val provider = StreamProvider {
            flow {
                emit(StreamResult.Found(source, listOf(stream("http://127.0.0.1/x"), stream("https://cdn.example/1.m3u8"))))
                emit(StreamResult.Found(source, (2..9).map { stream("https://cdn.example/$it.m3u8") }))
            }
        }
        val out = RemotePlayStreams.collect(provider, target)
        assertEquals(RemotePlayStreams.MAX_STREAMS, out.size)
        assertEquals("https://cdn.example/1.m3u8", out.first().url)
    }

    @Test
    fun remotePlay_roundTripsWithHeaders() {
        val body = PlayJson.encodeToString(RemotePlay.serializer(), RemotePlay(target, listOf(stream("https://cdn.example/1.m3u8"))))
        val back = PlayJson.decodeFromString(RemotePlay.serializer(), body)
        assertEquals("cs:105", back.target.request.pluginId)
        assertEquals(mapOf("Referer" to "https://site.example/"), back.streams.single().headers)
        // Eski biçim (yalnız hedef, akış yok) da okunur: streams varsayılanı boş.
        val plain = PlayJson.decodeFromString(RemotePlay.serializer(), """{"target":${PlayJson.encodeToString(PlayTarget.serializer(), target)}}""")
        assertTrue(plain.streams.isEmpty())
    }
}
