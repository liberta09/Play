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
