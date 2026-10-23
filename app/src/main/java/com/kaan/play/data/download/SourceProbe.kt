package com.kaan.play.data.download
import com.kaan.play.ui.player.PlaybackController

import android.util.Log
import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Kaynağın biçimi: indirici buna göre seçilir (HLS/DASH parça parça, düz dosya tek parça). */
enum class SourceKind { Progressive, Hls, Dash }

/** İndirme öncesi yoklamanın sonucu. */
sealed interface Probe {
    /** [lengthBytes]: düz dosyada sunucunun bildirdiği toplam boyut; HLS/DASH'te ya da bildirilmemişse null. */
    data class Ok(val kind: SourceKind, val lengthBytes: Long?) : Probe

    data class Rejected(val reason: Reason) : Probe

    enum class Reason {
        /** Sunucu 4xx/5xx döndü: bağlantının süresi dolmuş, yetkisiz ya da kaldırılmış olabilir. */
        Http,

        /** Sunucuya ulaşılamadı/zaman aşımı. */
        Network,

        /** Medya değil (HTML hata sayfası, JSON, torrent ...). */
        NotMedia,

        /** Korumalı (DRM) içerik: çevrimdışı oynatılamaz. */
        Drm,
    }
}

/**
 * İndirmeden önce kaynağa kısa bir istek atar ve işe yarar mı bakar: böylece "başlıyor ama hemen başarısız" yerine baştan
 * anlaşılır bir mesaj verilir ("Bu kaynak indirilemiyor, başka kaynak dene"). Aynı istek biçimi (HLS / DASH / düz dosya) ve
 * düz dosyanın boyutunu da bildirir (alan denetimi için).
 *
 * İstek, akışın `proxyHeaders`'ıyla ve `Range: bytes=0-4095` ile gider; sunucu Range'i yok sayıp 200 dönerse yalnızca ilk
 * [PEEK_BYTES] okunur, bağlantı kapatılır.
 */
class SourceProbe(
    private val client: OkHttpClient,
    private val timeoutMs: Long = 15_000,
) {

    suspend fun probe(url: String, headers: Map<String, String>, timeoutMs: Long = this.timeoutMs): Probe = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl == null || (httpUrl.scheme != "http" && httpUrl.scheme != "https")) {
            return@withContext Probe.Rejected(Probe.Reason.NotMedia)
        }
        val http = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder().url(httpUrl).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (headers.keys.none { it.equals("Range", ignoreCase = true) }) header("Range", "bytes=0-${RANGE_END}")
        }.build()
        try {
            http.newCall(request).await().use { response ->
                val code = response.code
                val contentType = response.header("Content-Type")
                val length = lengthOf(code, response.header("Content-Range"), response.header("Content-Length"))
                Log.d(TAG, "kaynak yoklama: host=${httpUrl.host} durum=$code type=$contentType uzunluk=$length")
                if (code != 200 && code != 206) return@use Probe.Rejected(Probe.Reason.Http)
                val body = response.body
                val source = body.source()
                source.request(PEEK_BYTES)
                val head = source.buffer.snapshot(minOf(source.buffer.size, PEEK_BYTES).toInt()).utf8()
                classify(
                    head = head,
                    contentType = contentType,
                    length = length,
                )
            }
        } catch (_: InterruptedIOException) { // SocketTimeoutException da bunun alt türü
            Log.d(TAG, "kaynak yoklama: host=${httpUrl.host} durum=zaman_asimi")
            Probe.Rejected(Probe.Reason.Network)
        } catch (_: IOException) {
            Log.d(TAG, "kaynak yoklama: host=${httpUrl.host} durum=ag_hatasi")
            Probe.Rejected(Probe.Reason.Network)
        }
    }

    companion object {
        private const val TAG = "PlayProbe"
        private const val RANGE_END = 4095
        private const val PEEK_BYTES = 64L * 1024

        /** Düz dosyada yanıt başlıklarından toplam boyut: 206'da `Content-Range: bytes 0-4095/12345`, 200'de `Content-Length`. */
        internal fun lengthOf(code: Int, contentRange: String?, contentLength: String?): Long? {
            if (code == 206) {
                val total = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                if (total != null && total > 0) return total
                return null
            }
            return contentLength?.trim()?.toLongOrNull()?.takeIf { it > 0 }
        }

        /** Gelen ilk baytlara ve `Content-Type`'a bakıp biçimi/uygunluğu belirler (saf, birim testli). */
        internal fun classify(head: String, contentType: String?, length: Long?): Probe {
            val text = head.trimStart('﻿', ' ', '\t', '\r', '\n')
            val type = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()

            if (text.startsWith("#EXTM3U")) {
                // Korumalı (FairPlay/Widevine/SAMPLE-AES) HLS çevrimdışı çalışmaz; düz AES-128 sorun değildir.
                return if (isProtectedHls(text)) Probe.Rejected(Probe.Reason.Drm) else Probe.Ok(SourceKind.Hls, null)
            }
            if (text.startsWith("<") && "<MPD" in text) {
                return if (isProtectedDash(text)) Probe.Rejected(Probe.Reason.Drm) else Probe.Ok(SourceKind.Dash, null)
            }
            // HTML hata sayfası, JSON, düz metin, torrent: indirilecek bir video değil.
            if (text.startsWith("<") || text.startsWith("{") || text.startsWith("[")) return Probe.Rejected(Probe.Reason.NotMedia)
            if (type.startsWith("text/") || type == "application/json" || type == "application/x-bittorrent") {
                return Probe.Rejected(Probe.Reason.NotMedia)
            }
            return Probe.Ok(SourceKind.Progressive, length)
        }

        private fun isProtectedHls(text: String): Boolean {
            val lower = text.lowercase(Locale.ROOT)
            return "method=sample-aes" in lower || "streamingkeydelivery" in lower || "urn:uuid:" in lower
        }

        private fun isProtectedDash(text: String): Boolean {
            val lower = text.lowercase(Locale.ROOT)
            return "contentprotection" in lower &&
                ("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed" in lower || "9a04f079-9840-4286-ab92-e65be0885f95" in lower)
        }
    }
}
