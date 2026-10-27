package com.kaan.play.data.speedtest

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class InternetSpeedTestTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun endpoint(path: String) = SpeedEndpoint(path) { bytes -> server.url("/$path?bytes=$bytes").toString() }

    private fun speedTest(vararg paths: String, client: OkHttpClient = OkHttpClient()) =
        InternetSpeedTest(client, paths.map(::endpoint), log = { logs += it })

    private fun body(bytes: Int) = MockResponse.Builder().body(Buffer().write(ByteArray(bytes))).build()

    @Test
    fun mbpsFromBytesAndTime() {
        // 1 000 000 bayt 1 sn'de = 8 Mbps; yarım saniyede 16 Mbps.
        assertEquals(8.0, InternetSpeedTest.mbps(1_000_000, 1_000_000_000), 1e-9)
        assertEquals(16.0, InternetSpeedTest.mbps(1_000_000, 500_000_000), 1e-9)
        // Sıfır süre bölme hatası vermez.
        assertTrue(InternetSpeedTest.mbps(10, 0) > 0)
    }

    @Test
    fun classifiesErrors() {
        assertEquals(SpeedTestError.Timeout, InternetSpeedTest.classify(SocketTimeoutException()))
        assertEquals(SpeedTestError.Timeout, InternetSpeedTest.classify(InterruptedIOException("timeout")))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(InterruptedIOException("interrupted")))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(UnknownHostException()))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(ConnectException()))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(SpeedTestHttpException(403)))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(null))
    }

    @Test
    fun describeHasClassAndCodeButNoAddress() {
        assertEquals("SpeedTestHttpException http=403", InternetSpeedTest.describe(SpeedTestHttpException(403)))
        assertEquals("UnknownHostException", InternetSpeedTest.describe(UnknownHostException("speed.example.com")))
    }

    @Test
    fun measuresWithFakeResponses() = runTest {
        repeat(3) { server.enqueue(body(1)) } // ping
        server.enqueue(body(1_000_000)) // hızlı örnek
        server.enqueue(body(8_000_000)) // büyük örnek (yerel sunucu hızlı)
        val outcome = speedTest("a").runTest()
        assertTrue(outcome is SpeedTestOutcome.Success)
        val result = (outcome as SpeedTestOutcome.Success).result
        assertEquals(9_000_000L, result.bytesDownloaded)
        assertTrue(result.downloadMbps > 0)
        assertTrue(result.pingMs >= 1)
        assertEquals("bytes=0-0", server.takeRequest().headers["Range"])
    }

    @Test
    fun stopsReadingAtRequestedSizeWhenRangeIsIgnored() = runTest {
        repeat(3) { server.enqueue(body(1)) }
        // Sunucu Range'i yok sayıp daha büyük dosya gönderiyor: yalnızca istenen kadar okunur.
        server.enqueue(body(3_000_000))
        server.enqueue(body(12_000_000))
        val result = (speedTest("a").runTest() as SpeedTestOutcome.Success).result
        assertEquals(9_000_000L, result.bytesDownloaded)
    }

    @Test
    fun fallsBackToSecondEndpointWhenFirstRejects() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build()) // ilk sunucu reddeder
        repeat(3) { server.enqueue(body(1)) }
        server.enqueue(body(1_000_000))
        server.enqueue(body(8_000_000))
        val outcome = speedTest("first", "second").runTest()
        assertTrue(outcome is SpeedTestOutcome.Success)
        assertTrue(server.takeRequest().url.encodedPath == "/first")
        assertTrue(server.takeRequest().url.encodedPath == "/second")
        assertEquals(listOf("speed test failed: endpoint=first error=SpeedTestHttpException http=403"), logs)
    }

    @Test
    fun allEndpointsRejectReportsUnreachable() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(403).build())
        val outcome = speedTest("a", "b").runTest()
        assertEquals(SpeedTestOutcome.Failure(SpeedTestError.Unreachable), outcome)
        assertEquals(2, logs.size)
        assertTrue(logs.none { it.contains("127.0.0.1") || it.contains("localhost") })
    }

    @Test
    fun slowServerReportsTimeout() = runTest {
        server.enqueue(MockResponse.Builder().body("x").headersDelay(2, TimeUnit.SECONDS).build())
        val client = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val outcome = speedTest("a", client = client).runTest()
        assertEquals(SpeedTestOutcome.Failure(SpeedTestError.Timeout), outcome)
    }

    @Test
    fun closedServerReportsUnreachable() = runTest {
        val closed = MockWebServer().also { it.start() }
        val url = closed.url("/x").toString()
        closed.close()
        val outcome = InternetSpeedTest(OkHttpClient(), listOf(SpeedEndpoint("closed") { url }), log = { logs += it }).runTest()
        assertEquals(SpeedTestOutcome.Failure(SpeedTestError.Unreachable), outcome)
        assertEquals("speed test failed: endpoint=closed error=ConnectException", logs.single())
    }
}
