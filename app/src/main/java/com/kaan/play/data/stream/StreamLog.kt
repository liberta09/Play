package com.kaan.play.data.stream

import android.util.Log
import com.kaan.play.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.Locale

/**
 * Akış adreslerini yalnızca debug derlemede ve **kısaltılmış** (şema + host + uzantı) loglar; yol/sorgu token taşıyabilir.
 * Reddedilen adresin (magnet, torrent...) türünü anlamak için.
 */
object StreamLog {
    private const val TAG = "PlayStreamUrl"

    /** `magnet`, `https://host/.m3u8`, `(http dışı)` gibi tek satırlık özet. */
    internal fun describe(url: String): String {
        val u = url.trim()
        val scheme = u.substringBefore(':', "").lowercase(Locale.ROOT).take(16)
        if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return "(adres değil)"
        if (scheme != "http" && scheme != "https") return "$scheme:"
        val rest = u.substringAfter("://", "")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').take(80)
        val path = rest.substring(host.length.coerceAtMost(rest.length)).substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT).take(8)
        return "$scheme://$host/${if (ext.isEmpty()) "" else ".$ext"}"
    }

    /**
     * Kaynak kimliği için "parmak izi": ana makine + yolun biçimi (uzun parçalar uzunlukla) + her sorgu parametresinin adı ve değerinin
     * SHA-256 özetinin ilk 6 hanesi. Adres, belirteç ya da imza yazılmaz; iki ortamda (ör. Node ve Play) aynı eklentinin aynı sonucu verip
     * vermediği özet karşılaştırmasıyla anlaşılır. `id` gibi kısa, gizli olmayan sayısal kimlikler (yalnızca rakam, ≤ 10 hane) açık yazılır.
     */
    internal fun fingerprint(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return describe(url)
        val path = parsed.pathSegments.joinToString("/") { if (it.length > 12) "<${it.length}>" else it }
        val query = (0 until parsed.querySize).joinToString(" ") { i ->
            val name = parsed.queryParameterName(i)
            val value = parsed.queryParameterValue(i).orEmpty()
            val shown = if (value.length <= 10 && value.all(Char::isDigit)) value else "#${sha6(value)}(len${value.length})"
            "$name=$shown"
        }
        return "${parsed.scheme}://${parsed.host}/$path${if (query.isEmpty()) "" else "?$query"}"
    }

    /** Yalnızca ana makine adı (günlük için); adres değilse "?". */
    private val hostCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 512
        }
    }
    private val hostLock = Any()

    fun hostOf(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "?"
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return "?"
        }
        synchronized(hostLock) {
            val cached = hostCache[trimmed]
            if (cached != null) return cached
        }
        val host = trimmed.toHttpUrlOrNull()?.host ?: "?"
        synchronized(hostLock) {
            hostCache[trimmed] = host
        }
        return host
    }

    private fun sha6(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(3).joinToString("") { "%02x".format(it) }

    fun rejected(origin: String, url: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$origin: reddedildi (http(s) değil) ${describe(url)}")
    }

    fun opening(url: String, mimeType: String?) {
        if (BuildConfig.DEBUG) Log.d(TAG, "oynatılıyor ${describe(url)} mime=${mimeType ?: "?"}")
    }
}
