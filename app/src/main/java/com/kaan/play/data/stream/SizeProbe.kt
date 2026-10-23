package com.kaan.play.data.stream
import com.kaan.play.ui.player.PlaybackController

import com.kaan.play.data.net.await
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Doğrudan HTTP kaynağın dosya boyutunu HEAD isteğinin `Content-Length`'inden bulur (akışın `proxyHeaders`'ı da gönderilir).
 * Hata, zaman aşımı, HTML cevap, 0 ya da bilinmeyen boyut sessizce null döner: oynatmayı hiçbir zaman geciktirmez ve hata göstermez.
 */
class SizeProbe(client: OkHttpClient, timeoutMs: Long = 5_000L) {

    private val http = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    suspend fun size(stream: PlayableStream): Long? {
        val url = stream.url.toHttpUrlOrNull() ?: return null
        return try {
            val request = Request.Builder().url(url).head().apply {
                header("User-Agent", stream.headers["User-Agent"] ?: PlaybackController.DEFAULT_USER_AGENT)
                stream.headers.forEach { (k, v) -> header(k, v) }
            }.build()
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                if (response.header("Content-Type")?.startsWith("text/", ignoreCase = true) == true) return null
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
