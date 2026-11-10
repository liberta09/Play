package com.kaan.play.data.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.util.Locale
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

    private fun endpoint(path: String = "__down") =
        SpeedEndpoint(path) { bytes -> server.url("/$path?bytes=$bytes").toString() }

    private fun speedTest(
        client: OkHttpClient = OkHttpClient(),
        targetBytes: Long = 9_000_000L,
        timeoutMs: Long = 20_000L,
    ) = InternetSpeedTest(
        client = client,
        endpoint = endpoint(),
        targetBytes = targetBytes,
        totalTimeoutMs = timeoutMs,
        dispatcher = Dispatchers.Unconfined,
        log = { logs += it },
    )

    private fun body(bytes: Int) = MockResponse.Builder().body(Buffer().write(ByteArray(bytes))).build()

    @Test
    fun mbpsAndLocaleFormatting() {
        // 1 000 000 bayt 1 sn'de = 8 Mbps; yarım saniyede 16 Mbps.
        assertEquals(8.0, InternetSpeedTest.mbps(1_000_000, 1_000_000_000), 1e-9)
        assertEquals(16.0, InternetSpeedTest.mbps(1_000_000, 500_000_000), 1e-9)
        // Sıfır süre bölme hatası vermez.
        assertTrue(InternetSpeedTest.mbps(10, 0) > 0)

        assertEquals("42,3 Mbps", InternetSpeedTest.formatMbps(42.34, Locale("tr", "TR")))
        assertEquals("42.3 Mbps", InternetSpeedTest.formatMbps(42.34, Locale.US))
        assertEquals("9,0 MB", InternetSpeedTest.formatMegabytes(9_000_000L, Locale("tr", "TR")))
    }

    @Test
    fun classifiesErrors() {
        assertEquals(SpeedTestError.Timeout, InternetSpeedTest.classify(SocketTimeoutException()))
        assertEquals(SpeedTestError.Timeout, InternetSpeedTest.classify(InterruptedIOException("timeout")))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(InterruptedIOException("interrupted")))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(UnknownHostException()))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(ConnectException()))
        assertEquals(SpeedTestError.Other("HTTP 503"), InternetSpeedTest.classify(SpeedTestHttpException(503)))
        assertEquals(SpeedTestError.Unreachable, InternetSpeedTest.classify(null))
    }

    @Test
    fun describeHasClassAndCodeButNoAddress() {
        assertEquals("SpeedTestHttpException http=403", InternetSpeedTest.describe(SpeedTestHttpException(403)))
        assertEquals("UnknownHostException", InternetSpeedTest.describe(UnknownHostException("speed.example.com")))
    }

    @Test
    fun downloads9MbWithProgressWithoutWritingDisk() = runTest {
        server.enqueue(body(9_000_000))
        val progressEvents = mutableListOf<Pair<Long, Long>>()
        val outcome = speedTest().runTest { down, total -> progressEvents += (down to total) }
        assertTrue(outcome is SpeedTestOutcome.Success)
        val result = (outcome as SpeedTestOutcome.Success).result
        assertEquals(9_000_000L, result.bytesDownloaded)
        assertTrue(result.downloadMbps > 0)
        val recorded = server.takeRequest()
        assertEquals("/__down", recorded.url.encodedPath)
        assertEquals("9000000", recorded.url.queryParameter("bytes"))
        assertTrue(progressEvents.isNotEmpty())
        assertEquals(9_000_000L to 9_000_000L, progressEvents.last())
    }

    @Test
    fun capsReadingAtRequestedTargetSize() = runTest {
        server.enqueue(body(12_000_000))
        val result = (speedTest().runTest() as SpeedTestOutcome.Success).result
        assertEquals(9_000_000L, result.bytesDownloaded)
    }

    @Test
    fun slowServerReportsTimeoutAndClosedServerReportsUnreachable() = runTest {
        server.enqueue(MockResponse.Builder().body("x").headersDelay(2, TimeUnit.SECONDS).build())
        val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).build()
        assertEquals(SpeedTestOutcome.Failure(SpeedTestError.Timeout), speedTest(client = client).runTest())

        val closed = MockWebServer().also { it.start() }
        val url = closed.url("/x").toString()
        closed.close()
        val unreachable = InternetSpeedTest(
            client = OkHttpClient(),
            endpoint = SpeedEndpoint("closed") { url },
            dispatcher = Dispatchers.Unconfined,
            log = { logs += it },
        ).runTest()
        assertEquals(SpeedTestOutcome.Failure(SpeedTestError.Unreachable), unreachable)
        assertEquals("speed test failed: endpoint=closed error=ConnectException", logs.last())
    }

    @Test
    fun cancellationStopsTestCleanly() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(1_000_000))).headersDelay(3, TimeUnit.SECONDS).build())
        val job = launch {
            speedTest(timeoutMs = 20_000L).runTest()
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }
}
