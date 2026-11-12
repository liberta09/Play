package com.kaan.play.data.speedtest

import android.util.Log
import com.kaan.play.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/** Hız testinin ölçüm adresi: [url] istenen bayt sayısı için indirme adresini verir. */
data class SpeedEndpoint(val name: String, val url: (bytes: Long) -> String)

sealed interface SpeedTestError {
    data object Unreachable : SpeedTestError
    data object Timeout : SpeedTestError
    data class Other(val detail: String) : SpeedTestError
}

sealed interface SpeedTestOutcome {
    data class Success(val result: InternetSpeedResult) : SpeedTestOutcome
    data class Failure(val error: SpeedTestError) : SpeedTestOutcome
}

/** Sunucu isteği reddetti (2xx dışı yanıt). */
class SpeedTestHttpException(val code: Int) : IOException("HTTP $code")

/**
 * İnternet hız testi (~9 MB tek indirme, disk yazımı yok, toplam 20 sn sınır).
 * Ana [OkHttpClient] üzerinden çalışır; uygulama içi DNS seçimi geçerlidir.
 */
class InternetSpeedTest(
    private val client: OkHttpClient = plainClient(),
    private val endpoint: SpeedEndpoint = DEFAULT_ENDPOINT,
    private val targetBytes: Long = TARGET_BYTES,
    private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {

    suspend fun runTest(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): SpeedTestOutcome = try {
        val result = withContext(dispatcher) {
            withTimeout(totalTimeoutMs) {
                measureDownload(onProgress)
            }
        }
        SpeedTestOutcome.Success(result)
    } catch (e: TimeoutCancellationException) {
        log("speed test failed: endpoint=${endpoint.name} error=${describe(e)}")
        SpeedTestOutcome.Failure(SpeedTestError.Timeout)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("speed test failed: endpoint=${endpoint.name} error=${describe(e)}")
        SpeedTestOutcome.Failure(classify(e))
    }

    private suspend fun measureDownload(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): InternetSpeedResult {
        val req = Request.Builder()
            .url(endpoint.url(targetBytes))
            .get()
            .build()
        val call = client.newCall(req)
        val completionHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            val response = call.await()
            return response.use { resp ->
                if (!resp.isSuccessful) throw SpeedTestHttpException(resp.code)
                val source = resp.body.source()
                val buffer = Buffer()
                var downloaded = 0L
                val startNano = System.nanoTime()
                onProgress(0L, targetBytes)
                while (downloaded < targetBytes) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer, minOf(READ_BUFFER_BYTES, targetBytes - downloaded))
                    if (read == -1L) break
                    downloaded += read
                    // Veri bellekte yalnızca sayılır ve hemen temizlenir; diske yazılmaz.
                    buffer.clear()
                    onProgress(downloaded, targetBytes)
                }
                val elapsedNanos = (System.nanoTime() - startNano).coerceAtLeast(1L)
                if (downloaded <= 0L) {
                    throw IOException("No data received during speed test")
                }
                InternetSpeedResult(
                    downloadMbps = mbps(downloaded, elapsedNanos),
                    bytesDownloaded = downloaded,
                    durationMs = (elapsedNanos / NANOS_PER_MILLISECOND).roundToLong().coerceAtLeast(1L),
                )
            }
        } finally {
            completionHandle?.dispose()
        }
    }

    companion object {
        private const val TAG = "PlaySpeed"
        const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        const val TARGET_BYTES = 9_000_000L
        const val TOTAL_TIMEOUT_MS = 20_000L
        const val READ_BUFFER_BYTES = 65_536L
        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val BYTES_PER_MEGABYTE = 1_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0

        val DEFAULT_ENDPOINT = SpeedEndpoint("cloudflare") { bytes -> "$DOWNLOAD_URL?bytes=$bytes" }

        fun plainClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun mbps(bytes: Long, elapsedNanos: Long): Double =
            (bytes.toDouble() * BITS_PER_BYTE * NANOS_PER_SECOND / elapsedNanos.coerceAtLeast(1L).toDouble()) / BITS_PER_MEGABIT

        fun megabytes(bytes: Long): Double = bytes.toDouble() / BYTES_PER_MEGABYTE

        fun formatMbps(mbps: Double, locale: Locale = Locale.getDefault()): String =
            String.format(locale, "%.1f Mbps", mbps)

        fun formatMegabytes(bytes: Long, locale: Locale = Locale.getDefault()): String =
            String.format(locale, "%.1f MB", megabytes(bytes))

        fun classify(error: Throwable?): SpeedTestError = when (error) {
            is SocketTimeoutException, is TimeoutCancellationException -> SpeedTestError.Timeout
            is InterruptedIOException ->
                if (error.message?.contains("timeout", ignoreCase = true) == true) SpeedTestError.Timeout else SpeedTestError.Unreachable
            is UnknownHostException, is ConnectException -> SpeedTestError.Unreachable
            is SpeedTestHttpException -> SpeedTestError.Other("HTTP ${error.code}")
            null -> SpeedTestError.Unreachable
            else -> SpeedTestError.Unreachable
        }

        /** Günlük için: yalnızca hata sınıfı ve (varsa) HTTP kodu; adres yazılmaz. */
        fun describe(error: Throwable): String = when (error) {
            is SpeedTestHttpException -> "${error.javaClass.simpleName} http=${error.code}"
            else -> error.javaClass.simpleName
        }
    }
}
