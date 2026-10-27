package com.kaan.play.data.speedtest

import com.kaan.play.data.net.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import kotlin.math.roundToLong

class InternetSpeedTest(
    private val client: OkHttpClient,
) {
    private data class DownloadSample(
        val mbps: Double,
        val durationMs: Long,
        val bytes: Long,
    )

    suspend fun measure(): InternetSpeedResult {
        val ping = measureMedianPing()
        val quick = measureDownload(QUICK_SAMPLE_BYTES)
        val finalSample = if (quick.durationMs < LARGE_SAMPLE_THRESHOLD_MS) {
            measureDownload(LARGE_SAMPLE_BYTES)
        } else {
            quick
        }
        val totalBytes = quick.bytes + if (finalSample === quick) 0L else finalSample.bytes
        return InternetSpeedResult(
            downloadMbps = finalSample.mbps,
            pingMs = ping,
            bytesDownloaded = totalBytes,
        )
    }

    suspend fun runTest(): InternetSpeedResult? = runCatching { measure() }.getOrNull()

    private suspend fun measureMedianPing(): Long {
        val samples = mutableListOf<Long>()
        repeat(PING_SAMPLES) {
            val start = System.nanoTime()
            val req = Request.Builder()
                .url("$DOWNLOAD_URL?bytes=0")
                .header("User-Agent", "Play/SpeedTest")
                .build()
            client.newCall(req).await().use {
                val elapsed = (System.nanoTime() - start) / 1_000_000L
                samples.add(elapsed.coerceAtLeast(1L))
            }
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private suspend fun measureDownload(bytes: Long): DownloadSample {
        val req = Request.Builder()
            .url("$DOWNLOAD_URL?bytes=$bytes")
            .header("User-Agent", "Play/SpeedTest")
            .build()
        val response = client.newCall(req).await()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Speed test HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty speed test response")
            val source = body.source()
            val buffer = Buffer()
            var downloaded = 0L
            val startNano = System.nanoTime()
            while (true) {
                val read = source.read(buffer, READ_BUFFER_BYTES)
                if (read == -1L) break
                downloaded += read
                buffer.clear()
            }
            val elapsedNanos = (System.nanoTime() - startNano).coerceAtLeast(1L)
            if (downloaded <= 0L) {
                throw IOException("No data received during speed test")
            }
            val durationMs = (elapsedNanos / NANOS_PER_MILLISECOND).roundToLong().coerceAtLeast(1L)
            val mbps = ((downloaded.toDouble() * BITS_PER_BYTE * NANOS_PER_SECOND / elapsedNanos.toDouble()) / BITS_PER_MEGABIT)
            DownloadSample(
                mbps = mbps,
                durationMs = durationMs,
                bytes = downloaded,
            )
        }
    }

    companion object {
        const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        const val READ_BUFFER_BYTES = 65536L
        const val QUICK_SAMPLE_BYTES = 1_000_000L
        const val LARGE_SAMPLE_BYTES = 8_000_000L
        const val LARGE_SAMPLE_THRESHOLD_MS = 1_200L
        const val PING_SAMPLES = 3
        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
