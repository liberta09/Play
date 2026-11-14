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
