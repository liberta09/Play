package com.kaan.play.data.torrent

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.TorrentStreamProvider
import com.kaan.play.ui.player.AutoNextRules
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TorrentSupportTest {

    private val source = StreamSource(1, "Torrentio")
    private val directStream = PlayableStream(
        source = StreamSource(2, "FilmMakinesi"),
        url = "https://cdn.example.com/movie.mp4",
        resolution = 1080,
        sizeBytes = 1_000_000_000L,
        headers = emptyMap(),
        subtitles = emptyList(),
    )
    private val magnetStream = PlayableStream(
        source = source,
        url = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        resolution = 1080,
        sizeBytes = 2_000_000_000L,
        headers = emptyMap(),
        subtitles = emptyList(),
        torrentFileIdx = 0,
    )
    private val request = StreamRequest(tmdbId = 123, imdbId = "tt123", type = MediaType.Movie, title = "Test Movie")

    @Before
    fun setUp() {
        TorrentSupport.resetForTest()
    }

    @After
    fun tearDown() {
        TorrentSupport.resetForTest()
    }

    @Test
    fun builtInSupportDependsOnSdkLevel() {
        assertTrue("API 28 ve üzeri desteklenmeli", TorrentSupport.isBuiltInSupported(sdkInt = 28))
        assertTrue("API 34 desteklenmeli", TorrentSupport.isBuiltInSupported(sdkInt = 34))
        assertFalse("API 27 desteklenmemeli", TorrentSupport.isBuiltInSupported(sdkInt = 27))
        assertFalse("API 25 (Android 7.1) desteklenmemeli", TorrentSupport.isBuiltInSupported(sdkInt = 25))
    }

    @Test
    fun debugForceFailDisablesBuiltInEngine() {
        assertTrue(TorrentSupport.isBuiltInSupported(sdkInt = 28))
        TorrentSupport.debugForceFailNative = true
        assertFalse("debugForceFailNative açıkken motor devre dışı olmalı", TorrentSupport.isBuiltInSupported(sdkInt = 28))
    }

    @Test
    fun engineUnavailableFlagDisablesBuiltInEngine() {
        assertTrue(TorrentSupport.isBuiltInSupported(sdkInt = 28))
        TorrentSupport.markUnavailable()
        assertFalse("markUnavailable sonrasında motor devre dışı olmalı", TorrentSupport.isBuiltInSupported(sdkInt = 28))
    }

    @Test
    fun torrServerAllowsTorrentEvenIfBuiltInEngineIsDisabled() {
        // API 25 kutuda yerleşik motor yok ama TorrServer varsa torrent kullanılabilir
        assertFalse(TorrentSupport.isTorrentUsable(hasTorrServer = false, sdkInt = 25))
        assertTrue(TorrentSupport.isTorrentUsable(hasTorrServer = true, sdkInt = 25))

        // Bayrak kalkıkken de TorrServer ile kullanılabilir
        TorrentSupport.markUnavailable()
        assertFalse(TorrentSupport.isTorrentUsable(hasTorrServer = false, sdkInt = 28))
        assertTrue(TorrentSupport.isTorrentUsable(hasTorrServer = true, sdkInt = 28))
    }

    @Test
    fun torrentStreamProviderHidesBuiltInMagnetStreamsWhenEngineUnavailable() = runTest {
        var warmUpCalls = 0
        val provider = TorrentStreamProvider(
            inner = { flowOf(StreamResult.Found(source, listOf(directStream, magnetStream))) },
            server = { null }, // Kullanıcı TorrServer girmemiş
            onBuiltIn = { warmUpCalls++ },
            isBuiltInAvailable = { false }, // Yerleşik motor kullanılamıyor
        )

        val result = provider.streams(request).toList().single() as StreamResult.Found
        assertEquals(1, result.streams.size)
        assertEquals("https://cdn.example.com/movie.mp4", result.streams[0].url)
        assertEquals(0, warmUpCalls)
    }

    @Test
    fun torrentStreamProviderPreservesMagnetStreamsViaTorrServerEvenWhenBuiltInUnavailable() = runTest {
        val provider = TorrentStreamProvider(
            inner = { flowOf(StreamResult.Found(source, listOf(directStream, magnetStream))) },
            server = { "http://192.168.1.50:8090" },
            isBuiltInAvailable = { false },
        )

        val result = provider.streams(request).toList().single() as StreamResult.Found
        assertEquals(2, result.streams.size)
        assertEquals("https://cdn.example.com/movie.mp4", result.streams[0].url)
        assertTrue(result.streams[1].url.startsWith("http://192.168.1.50:8090/stream?link="))
    }

    @Test
    fun autoNextRulesOrderSkipsMagnetStreamsWhenEngineUnavailable() {
        TorrentSupport.markUnavailable()
        val ordered = AutoNextRules.order(
            streams = listOf(magnetStream, directStream),
            current = directStream,
        )
        assertEquals(1, ordered.size)
        assertEquals(directStream.url, ordered[0].url)
    }

    @Test
    fun torrentEngineEnsureSessionCatchesNativeErrorAndMarksUnavailable() {
        val tempDir = File.createTempFile("torrent_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val engine = TorrentEngine(tempDir)
            TorrentSupport.debugForceFailNative = true

            val exc = assertThrows(TorrentException::class.java) {
                engine.open("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", 0)
            }
            assertEquals(TorrentException.Reason.Engine, exc.reason)
            assertTrue("Oturum boyunca tek seferlik bayrak açılmalı", TorrentSupport.isEngineUnavailable)
            assertFalse(TorrentSupport.isBuiltInSupported(sdkInt = 28))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
