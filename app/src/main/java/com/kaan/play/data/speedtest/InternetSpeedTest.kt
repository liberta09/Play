package com.kaan.play.data.speedtest

import android.util.Log
import com.kaan.play.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/** Hız testinin ölçüm sunucusu: [url] istenen bayt sayısı için indirme adresini verir. */
data class SpeedEndpoint(val name: String, val url: (bytes: Long) -> String)

/** Testin neden tamamlanamadığı (arayüzde kısa sebep olarak gösterilir). */
enum class SpeedTestError { Unreachable, Timeout }

sealed interface SpeedTestOutcome {
    data class Success(val result: InternetSpeedResult) : SpeedTestOutcome
    data class Failure(val error: SpeedTestError) : SpeedTestOutcome
}

/** Sunucu isteği reddetti (2xx dışı yanıt). */
class SpeedTestHttpException(val code: Int) : IOException("HTTP $code")

/**
 * İnternet hız testi. Uygulamanın ortak istemcisi yerine **sade** bir OkHttp istemcisi kullanır: eklenti ağ koruması,
 * site düzeltme interceptor'ları ve User-Agent değişikliği ölçümü etkilemesin. İlk ölçüm sunucusu reddederse (ya da
 * ulaşılamazsa) sıradakine geçilir. Gövde IO iş parçacığında okunur (ana iş parçacığında soket okumak
 * `NetworkOnMainThreadException` atar; eski sürümde bu hata yutulup "Test tamamlanamadı" görünüyordu).
 */
class InternetSpeedTest(
    private val client: OkHttpClient = plainClient(),
    private val endpoints: List<SpeedEndpoint> = DEFAULT_ENDPOINTS,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    private data class DownloadSample(
        val mbps: Double,
        val durationMs: Long,
        val bytes: Long,
    )

    /** Sırayla her sunucuyu dener; hepsi başarısızsa son hata sınıflandırılarak döner. İptal yutulmaz. */
    suspend fun runTest(): SpeedTestOutcome {
        var last: Throwable? = null
        for (endpoint in endpoints) {
            try {
                return SpeedTestOutcome.Success(measure(endpoint))
            } catch (e: TimeoutCancellationException) {
                last = e
                log("speed test failed: endpoint=${endpoint.name} error=${describe(e)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                log("speed test failed: endpoint=${endpoint.name} error=${describe(e)}")
            }
        }
        return SpeedTestOutcome.Failure(classify(last))
    }

    suspend fun measure(endpoint: SpeedEndpoint): InternetSpeedResult = withContext(Dispatchers.IO) {
        val ping = measureMedianPing(endpoint)
        val quick = measureDownload(endpoint, QUICK_SAMPLE_BYTES)
        val finalSample = if (quick.durationMs < LARGE_SAMPLE_THRESHOLD_MS) {
            measureDownload(endpoint, LARGE_SAMPLE_BYTES)
        } else {
            quick
        }
        val totalBytes = quick.bytes + if (finalSample === quick) 0L else finalSample.bytes
        InternetSpeedResult(
            downloadMbps = finalSample.mbps,
            pingMs = ping,
            bytesDownloaded = totalBytes,
        )
    }

    private suspend fun measureMedianPing(endpoint: SpeedEndpoint): Long {
        val samples = mutableListOf<Long>()
        repeat(PING_SAMPLES) {
            val start = System.nanoTime()
            val req = Request.Builder()
                .url(endpoint.url(1))
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(req).await().use { resp ->
                if (!resp.isSuccessful) throw SpeedTestHttpException(resp.code)
                val elapsed = (System.nanoTime() - start) / 1_000_000L
                samples.add(elapsed.coerceAtLeast(1L))
            }
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    /** En çok [bytes] okunur; sunucu `Range`'i yok sayıp daha büyük dosya gönderirse okuma sınırda kesilir. */
    private suspend fun measureDownload(endpoint: SpeedEndpoint, bytes: Long): DownloadSample {
        val req = Request.Builder()
            .url(endpoint.url(bytes))
            .header("Range", "bytes=0-${bytes - 1}")
            .build()
        val response = client.newCall(req).await()
        return response.use { resp ->
            if (!resp.isSuccessful) throw SpeedTestHttpException(resp.code)
            val source = resp.body.source()
            val buffer = Buffer()
            var downloaded = 0L
            val startNano = System.nanoTime()
            while (downloaded < bytes) {
                val read = source.read(buffer, minOf(READ_BUFFER_BYTES, bytes - downloaded))
                if (read == -1L) break
                downloaded += read
                buffer.clear()
            }
            val elapsedNanos = (System.nanoTime() - startNano).coerceAtLeast(1L)
            if (downloaded <= 0L) {
                throw IOException("No data received during speed test")
            }
            DownloadSample(
                mbps = mbps(downloaded, elapsedNanos),
                durationMs = (elapsedNanos / NANOS_PER_MILLISECOND).roundToLong().coerceAtLeast(1L),
                bytes = downloaded,
            )
        }
    }

    companion object {
        private const val TAG = "PlaySpeed"
        const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        const val FALLBACK_URL = "https://proof.ovh.net/files/10Mb.dat"
        const val READ_BUFFER_BYTES = 65536L
        const val QUICK_SAMPLE_BYTES = 1_000_000L
        const val LARGE_SAMPLE_BYTES = 8_000_000L
        const val LARGE_SAMPLE_THRESHOLD_MS = 1_200L
        const val PING_SAMPLES = 3
        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0

        /** Cloudflare önce; reddederse OVH'nin herkese açık deneme dosyası (`Range` ile istenen kadarı okunur). */
        val DEFAULT_ENDPOINTS = listOf(
            SpeedEndpoint("cloudflare") { bytes -> "$DOWNLOAD_URL?bytes=$bytes" },
            SpeedEndpoint("ovh") { _ -> FALLBACK_URL },
        )

        fun plainClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun mbps(bytes: Long, elapsedNanos: Long): Double =
            (bytes.toDouble() * BITS_PER_BYTE * NANOS_PER_SECOND / elapsedNanos.coerceAtLeast(1L).toDouble()) / BITS_PER_MEGABIT

        /** Zaman aşımı ayrı; ulaşılamayan, reddeden ya da başka bir hata "sunucuya ulaşılamadı" olur. */
        fun classify(error: Throwable?): SpeedTestError = when (error) {
            is SocketTimeoutException, is TimeoutCancellationException -> SpeedTestError.Timeout
            is InterruptedIOException ->
                if (error.message?.contains("timeout", ignoreCase = true) == true) SpeedTestError.Timeout else SpeedTestError.Unreachable
            else -> SpeedTestError.Unreachable
        }

        /** Günlük için: yalnızca hata sınıfı ve (varsa) HTTP kodu; adres yazılmaz. */
        fun describe(error: Throwable): String = when (error) {
            is SpeedTestHttpException -> "${error.javaClass.simpleName} http=${error.code}"
            else -> error.javaClass.simpleName
        }
    }
}
