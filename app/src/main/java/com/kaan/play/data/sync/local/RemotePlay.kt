package com.kaan.play.data.sync.local

import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamResult
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Eşleşmiş cihaza ([LocalSyncProtocol.PATH_PLAY]) giden oynatma isteği. [streams] boşsa karşı cihaz içeriği kendi
 * eklentisiyle açar; doluysa (karşıda eklenti yok) bu cihazın bulduğu doğrudan akışları sırayla dener. [pluginName]:
 * eklentinin cihazdan bağımsız adı (CS `internalName`); karşı cihaz numarayı tüm eklentileri yüklemeden bununla çevirir.
 */
@Serializable
data class RemotePlay(val target: PlayTarget, val streams: List<PlayableStream> = emptyList(), val pluginName: String? = null)

/** [LocalSyncController.playOnPeer] sonucu. */
sealed interface CastResult {
    data class Sent(val peer: String) : CastResult

    /** Karşı cihazda bu içeriğin eklentisi yok: içerik değil, doğrudan akış gönderilmeli. */
    data class NoPlugin(val peer: String) : CastResult

    data object Unreachable : CastResult
}

object RemotePlayStreams {
    const val MAX_STREAMS = 5
    const val TIMEOUT_MS = 25_000L

    /**
     * Başka cihazda da açılabilir mi: düz http(s) adresi, bu cihaza özel yerel adres değil (torrent motoru, yerel vekil) ve
     * eklentinin çalışma zamanı ara katmanını gerektirmiyor (başlıklar akışla birlikte gider).
     */
    fun castable(s: PlayableStream): Boolean {
        if (s.httpInterceptor != null || s.providerTransportRequired) return false
        val uri = runCatching { URI(s.url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        return host != "localhost" && !host.startsWith("127.") && host != "0.0.0.0" && host != "[::1]" && host != "::1"
    }

    /** Karşıdan gelen akışta tutulan başlıklar (küçük harf); diğerleri atılır. */
    val ALLOWED_HEADERS = setOf("user-agent", "referer", "origin", "accept", "accept-language", "cookie")

    /**
     * Eşleşmiş cihazdan gelen doğrudan akışları alıcıda yeniden denetler: [castable], adres düz http(s) ve yerel/özel/
     * loopback/link-local değil ([com.kaan.play.data.cloudstream.PlayNetworkGuard.isBlockedHost]), başlıklar
     * [ALLOWED_HEADERS] ile süzülür, altyazılar da aynı adres kuralına uymalı. Uymayan akış atılır; en çok [MAX_STREAMS].
     */
    fun acceptIncoming(streams: List<PlayableStream>): List<PlayableStream> = streams.asSequence()
        .filter { castable(it) && safeRemoteUrl(it.url) }
        .map { s ->
            s.copy(
                headers = s.headers.filterKeys { it.lowercase() in ALLOWED_HEADERS },
                subtitles = s.subtitles.filter { safeRemoteUrl(it.url) }
                    .map { it.copy(headers = it.headers.filterKeys { k -> k.lowercase() in ALLOWED_HEADERS }) },
            )
        }
        .take(MAX_STREAMS)
        .toList()

    /**
     * http(s), kullanıcı bilgisi yok, sunucu adı yerel/özel ağ adresi değil. Sayısal sabitler (IPv6 ya da düz dört parçalı
     * IPv4) DNS'e gitmeden çözülüp denetlenir ([isPublicLiteral]); başka sayısal biçimler (`2130706433`, `0x7f.1`, `127.1`,
     * baştaki sıfırlar) ve geçerli DNS adı olmayanlar reddedilir. DNS ile çözülen adlar burada denetlenmez (bağlantı anında
     * da denetlenmez; akış yalnız kullanıcının onayladığı eşli cihazdan gelir).
     */
    fun safeRemoteUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
        if (host.isEmpty() || host == "localhost" || host.endsWith(".local") || host.endsWith(".localhost")) return false
        if (com.kaan.play.data.cloudstream.PlayNetworkGuard.isBlockedHost(host, uri.port)) return false
        return when {
            ':' in host -> isPublicLiteral(host)
            NUMERIC_HOST.matches(host) -> DOTTED_QUAD.matches(host) && isPublicLiteral(host)
            else -> DNS_NAME.matches(host)
        }
    }

    /** Rakam/nokta/0x'ten oluşan ad: sayısal bir IPv4 biçimi (yalnız [DOTTED_QUAD] kabul edilir). */
    private val NUMERIC_HOST = Regex("^(?:0x[0-9a-f]*|[0-9]+)(?:\\.(?:0x[0-9a-f]*|[0-9]*))*\\.?$")

    /** Düz dört parçalı ondalık IPv4, baştaki sıfırsız, her parça 0–255. */
    private val DOTTED_QUAD = Regex("^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$")

    /** Geçerli DNS adı: harf/rakam/tire etiketleri, en az iki etiket, son etiket harfle başlar. */
    private val DNS_NAME = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?\\.?$")

    /**
     * Sayısal sabit ([InetAddress.getByName] sabitte DNS sorgusu yapmaz) genel bir adres mi: IPv4-eşlemeli/uyumlu IPv6 içteki
     * IPv4'e indirilir; loopback, özel (site-local), link-local, belirsiz, çoklu yayın, ULA (fc00::/7), CGNAT (100.64/10),
     * 0.0.0.0/8 ve genel yayın reddedilir.
     */
    private fun isPublicLiteral(literal: String): Boolean {
        val parsed = runCatching { java.net.InetAddress.getByName(literal) }.getOrNull() ?: return false
        var bytes = parsed.address
        if (bytes.size == 16 && bytes.copyOfRange(0, 10).all { it.toInt() == 0 } &&
            ((bytes[10].toInt() and 0xff) == 0xff && (bytes[11].toInt() and 0xff) == 0xff ||
                bytes[10].toInt() == 0 && bytes[11].toInt() == 0)
        ) {
            bytes = bytes.copyOfRange(12, 16)
        }
        val address = java.net.InetAddress.getByAddress(bytes)
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress
        ) return false
        val b0 = bytes[0].toInt() and 0xff
        val b1 = bytes[1].toInt() and 0xff
        return if (bytes.size == 4) {
            !(b0 == 0 || b0 == 127 || (b0 == 100 && (b1 and 0xc0) == 64) || bytes.all { (it.toInt() and 0xff) == 0xff })
        } else {
            (b0 and 0xfe) != 0xfc
        }
    }

    /** Bu cihazın kaynaklarından gönderilebilir ilk [MAX_STREAMS] akış (en çok [TIMEOUT_MS] beklenir). */
    suspend fun collect(provider: StreamProvider, target: PlayTarget): List<PlayableStream> {
        val out = mutableListOf<PlayableStream>()
        withTimeoutOrNull(TIMEOUT_MS) {
            provider.streams(target.request).firstOrNull { r ->
                if (r is StreamResult.Found) out += r.streams.filter(::castable).take(MAX_STREAMS - out.size)
                out.size >= MAX_STREAMS
            }
        }
        return out.toList()
    }
}
