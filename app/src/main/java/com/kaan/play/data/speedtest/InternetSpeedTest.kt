package com.kaan.play.data.speedtest

import com.kaan.play.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import java.io.IOException
import kotlin.math.roundToLong

/** İndirme hızı, ping ve testte gerçekten alınan bayt sayısı. */
data class InternetSpeedResult(
    val downloadMbps: Double,
    val pingMs: Long,
    val bytesDownloaded: Long,
)

/**
 * Video izlemeye en çok etki eden indirme yönünü ölçer. Önce küçük bir örnek alır; bağlantı hızlıysa
 * daha doğru sonuç için ikinci, daha büyük örnek indirir. Test başına en çok yaklaşık 8.25 MB kullanır.
 */
class InternetSpeedTest(private val client: OkHttpClient) {
    suspend fun measure(): InternetSpeedResult = withContext(Dispatchers.IO) {
        val ping = measureMedianPing()
        val quickSample = measureDownload(QUICK_SAMPLE_BYTES)
        val finalSample = if (quickSample.durationMs < LARGE_SAMPLE_THRESHOLD_MS) {
            try {
                measureDownload(LARGE_SAMPLE_BYTES)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Büyük örnek mobil ağda zaman aşımına uğrarsa, tamamlanan küçük örneğin sonucunu koru.
                quickSample
            }
        } else {
            quickSample
        }
        InternetSpeedResult(
            downloadMbps = finalSample.mbps,
            pingMs = ping,
            bytesDownloaded = quickSample.bytes + if (finalSample === quickSample) 0L else finalSample.bytes,
        )
    }

    private suspend fun measureMedianPing(): Long {
        val measurements = List(PING_SAMPLES) {
            val started = System.nanoTime()
            client.newCall(request(bytes = 0L)).await().use { response ->
                if (!response.isSuccessful) throw SpeedTestHttpException(response.code)
            }
            ((System.nanoTime() - started) / NANOS_PER_MILLISECOND).roundToLong().coerceAtLeast(1L)
        }
        return measurements.sorted()[measurements.size / 2]
    }

    private suspend fun measureDownload(bytes: Long): DownloadSample =
        client.newCall(request(bytes)).await().use { response ->
            if (!response.isSuccessful) throw SpeedTestHttpException(response.code)
            val body = response.body
            val source = body.source()
            val buffer = Buffer()
            var received = 0L
            val started = System.nanoTime()
            while (true) {
                val count = source.read(buffer, READ_BUFFER_BYTES)
                if (count == -1L) break
                received += count
                buffer.clear()
            }
            val elapsedNanos = (System.nanoTime() - started).coerceAtLeast(1L)
            if (received <= 0L) throw IOException("Empty speed test response")
            DownloadSample(
                mbps = received * BITS_PER_BYTE * NANOS_PER_SECOND / elapsedNanos / BITS_PER_MEGABIT,
                durationMs = (elapsedNanos / NANOS_PER_MILLISECOND).roundToLong().coerceAtLeast(1L),
                bytes = received,
            )
        }

    private fun request(bytes: Long): Request {
        val url = DOWNLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("bytes", bytes.toString())
            .addQueryParameter("_play_test", System.nanoTime().toString())
            .build()
        return Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .get()
            .build()
    }

    private data class DownloadSample(val mbps: Double, val durationMs: Long, val bytes: Long)

    private companion object {
        const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        const val QUICK_SAMPLE_BYTES = 256_000L
        const val LARGE_SAMPLE_BYTES = 8_000_000L
        const val LARGE_SAMPLE_THRESHOLD_MS = 1_200L
        const val PING_SAMPLES = 3
        const val READ_BUFFER_BYTES = 64L * 1024L
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
    }
}

class SpeedTestHttpException(val statusCode: Int) : IOException("Speed test server returned HTTP $statusCode")
