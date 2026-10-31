package com.kaan.play.data.stream

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Torrent kaynakları (infoHash / `magnet:`). Varsayılan olarak Play'in yerleşik motoru ([com.kaan.play.data.torrent.TorrentEngine])
 * oynatır: magnet oynatma anında yerel http adresine çevrilir. Kullanıcı kendi torrent sunucusunun (TorrServer uyumlu) adresini
 * girdiyse bunun yerine o sunucunun düz http adresi kullanılır.
 */
object TorrentLinks {

    private val HEX_HASH = Regex("^[0-9a-fA-F]{40}$")
    private val BASE32_HASH = Regex("^[A-Za-z2-7]{32}$")
    private const val MAX_TRACKERS = 40

    fun isMagnet(url: String): Boolean = url.startsWith("magnet:", ignoreCase = true)

    /** 40 hane onaltılık ya da 32 hane base32 infoHash geçerliyse küçük harfli/olduğu gibi döner; yoksa null. */
    fun normalizeHash(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        return when {
            HEX_HASH.matches(v) -> v.lowercase(Locale.ROOT)
            BASE32_HASH.matches(v) -> v.uppercase(Locale.ROOT)
            else -> null
        }
    }

    /** `magnet:?xt=urn:btih:<hash>&tr=...`; geçersiz hash'te null. Yalnızca udp/http(s)/wss izleyiciler, en çok [MAX_TRACKERS]. */
    fun magnet(infoHash: String?, trackers: List<String> = emptyList()): String? {
        val hash = normalizeHash(infoHash) ?: return null
        val tr = trackers.asSequence().map { it.trim() }
            .filter { t -> listOf("udp://", "http://", "https://", "wss://").any { t.startsWith(it, ignoreCase = true) } && t.none { it.isWhitespace() } }
            .distinct().take(MAX_TRACKERS)
            .joinToString("") { "&tr=${encode(it)}" }
        return "magnet:?xt=urn:btih:$hash$tr"
    }

    /** Var olan bir magnet baglantisina eksik izleyicileri ekler; gecerli magnet degilse ya da liste bossa oldugu gibi doner. */
    fun appendTrackers(magnet: String, trackers: List<String>): String {
        if (!isMagnet(magnet) || trackers.isEmpty()) return magnet
        val validTrackers = trackers.asSequence().map { it.trim() }
            .filter { t -> listOf("udp://", "http://", "https://", "wss://").any { t.startsWith(it, ignoreCase = true) } && t.none { it.isWhitespace() } }
            .distinct()
            .take(MAX_TRACKERS)
            .toList()
        if (validTrackers.isEmpty()) return magnet
        val existing = magnet.split('&').filter { it.startsWith("tr=", ignoreCase = true) }
            .map { runCatching { URLDecoder.decode(it.substring(3), "UTF-8") }.getOrDefault("") }
            .toSet()
        val toAppend = validTrackers.filter { it !in existing }
        if (toAppend.isEmpty()) return magnet
        val prefix = if (magnet.contains('?')) "&" else "?"
        return magnet + prefix + toAppend.joinToString("&") { "tr=${encode(it)}" }
    }

    /** Magnet'in infoHash'i (geçerliyse). */
    fun hashOf(magnet: String): String? {
        val query = magnet.substringAfter('?', "")
        val xt = query.split('&').firstOrNull { it.startsWith("xt=", ignoreCase = true) } ?: return null
        return normalizeHash(xt.substringAfter("urn:btih:", "").substringBefore('&'))
    }

    /** Magnet'in infoHash'i 40 haneli küçük harf onaltılık olarak (base32 hash çevrilir); geçersizse null. */
    fun hexHashOf(magnet: String): String? {
        val hash = hashOf(magnet) ?: return null
        if (hash.length == 40) return hash
        // base32 (RFC 4648, dolgusuz): 32 karakter × 5 bit = 160 bit.
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder(40)
        var buffer = 0L
        var bits = 0
        for (c in hash) {
            buffer = (buffer shl 5) or alphabet.indexOf(c).toLong()
            bits += 5
            while (bits >= 4) {
                bits -= 4
                out.append("0123456789abcdef"[((buffer shr bits) and 0xF).toInt()])
            }
        }
        return out.toString()
    }

    /** Kullanıcının yazdığı sunucu adresini `http(s)://host[:port]` biçimine getirir; kullanılamazsa null. Şema yoksa `http://` eklenir. */
    fun normalizeServer(input: String?): String? {
        var v = input?.trim().orEmpty()
        if (v.isEmpty() || v.any { it.isWhitespace() }) return null
        if ("://" !in v) v = "http://$v"
        val uri = try { URI(v) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$scheme://$host$port${uri.path.orEmpty().trimEnd('/')}"
    }

    /**
     * TorrServer uyumlu oynatma adresi: `{sunucu}/stream?link=<magnet>&index=<1 tabanlı dosya>&play`. Addon'ların `fileIdx`
     * değeri 0 tabanlıdır, sunucunun `index` değeri 1 tabanlı; dosya belirtilmediyse sunucu varsayılanını (1) kullanır.
     */
    fun serverUrl(server: String, magnet: String, fileIdx: Int?): String {
        val index = (fileIdx?.takeIf { it >= 0 } ?: 0) + 1
        return "$server/stream?link=${encode(magnet)}&index=$index&play"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * Torrent kaynakları (adresi `magnet:` olan [PlayableStream]'ler). [server] (kullanıcının TorrServer uyumlu sunucusu) her istekte
 * yeniden okunur: ayarlıysa magnet o sunucunun http adresine çevrilir; değilse magnet olduğu gibi kalır ve oynatıcı yerleşik
 * motorla açar. Yerleşik motor kullanılacaksa [onBuiltIn] çağrılır (motor önceden ısınsın).
 */
class TorrentStreamProvider(
    private val inner: StreamProvider,
    private val server: suspend () -> String?,
    private val onBuiltIn: () -> Unit = {},
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> = inner.streams(request).map { result ->
        if (result !is StreamResult.Found || result.streams.none { TorrentLinks.isMagnet(it.url) }) return@map result
        val base = TorrentLinks.normalizeServer(server())
        if (base == null) {
            onBuiltIn()
            return@map result
        }
        result.copy(streams = result.streams.map { s ->
            if (!TorrentLinks.isMagnet(s.url)) s
            else s.copy(url = TorrentLinks.serverUrl(base, s.url, s.torrentFileIdx), torrentFileIdx = null)
        })
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> = inner.sources(request)
}
