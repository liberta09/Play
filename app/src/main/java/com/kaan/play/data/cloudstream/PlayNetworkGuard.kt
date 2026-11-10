package com.kaan.play.data.cloudstream

import com.kaan.play.data.plugin.NetworkPolicy
import okhttp3.Interceptor
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp isteği [PlayNetworkGuard] tarafından engellendi. IOException'dır: OkHttp yalnızca IOException'ı
 * çağırana (`onFailure` / `execute`) iletir; başka bir istisna arka plan iş parçacığında uygulamayı çökertir.
 */
class BlockedAddressException(message: String?, cause: Throwable? = null) : IOException(message, cause)

/**
 * CS plugin'lerine karşı ağ güvenliği: yerel/özel IP'ler reddedilir.
 * Loopback (127.0.0.0/8, localhost) yalnızca bu oturumda izin verilen
 * portlara (TorrentHttpServer, eklenti yerel m3u8 proxy'si) açıktır.
 * 0.0.0.0 ve ::1 koşulsuz engellenir.
 * "fc"/"fd" ULA IPv6 kontrolleri yalnızca IPv6 sabitlerine uygulanır;
 * fcbarcelona.com gibi alan adları engellenmez.
 */
object PlayNetworkGuard {
    private val allowedLoopbackPorts = ConcurrentHashMap.newKeySet<Int>()

    private val IPV4_PRIVATE_PREFIXES = listOf(
        "10.", "192.168.", "169.254.", "100.64.",
        "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
        "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.",
        "172.31."
    )

    fun allowLoopbackPort(port: Int) {
        if (port in 1..65535) {
            allowedLoopbackPorts.add(port)
        }
    }

    fun disallowLoopbackPort(port: Int) {
        allowedLoopbackPorts.remove(port)
    }

    fun clearAllowedLoopbackPorts() {
        allowedLoopbackPorts.clear()
    }

    fun isLoopbackPortAllowed(port: Int): Boolean = allowedLoopbackPorts.contains(port)

    /**
     * OkHttp istemcileri için koruma: engellenen istek [BlockedAddressException] (IOException) ile reddedilir,
     * sıradan bir ağ hatası gibi işlenir ve uygulamayı çökertmez. [checkUrl] SecurityException fırlatmaya devam eder.
     */
    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        try {
            checkUrl(url)
            chain.connection()?.socket()?.inetAddress?.let { resolvedIp ->
                if (isBlockedResolvedAddress(resolvedIp, request.url.port)) {
                    throw SecurityException("CS plugin: resolved private address blocked")
                }
            }
        } catch (e: SecurityException) {
            throw BlockedAddressException(e.message, e)
        }
        chain.proceed(request)
    }

    /** DNS veya DoH ile çözülmüş IP adresini (yeniden bağlama / DNS rebind saldırısına karşı) denetler. */
    fun isBlockedResolvedAddress(address: InetAddress, port: Int = -1): Boolean {
        if (address.isLoopbackAddress) {
            return port !in allowedLoopbackPorts
        }
        return RESOLVED_IP_POLICY.isBlocked(address)
    }

    private val RESOLVED_IP_POLICY = NetworkPolicy()

    fun checkUrl(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw SecurityException("CS plugin: only http(s) allowed: $url")
        }
        val parsed = try { URL(url) } catch (_: Exception) { return }
        val host = parsed.host ?: return
        val port = if (parsed.port != -1) parsed.port else parsed.defaultPort

        if (isBlockedHost(host, port)) {
            throw SecurityException("CS plugin: private address blocked: $host")
        }
    }

    fun isBlockedHost(host: String, port: Int = -1): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").trim().lowercase()
        if (clean.isEmpty()) return true

        // 1. Koşulsuz engelli özel adresler (0.0.0.0 ve IPv6 loopback / unspecified)
        if (clean == "0.0.0.0" || clean == "::" || clean == "::1" || clean == "0:0:0:0:0:0:0:1") {
            return true
        }

        // 2. IPv6 sabitleri denetimi (yalnızca iki nokta ':' içeren adresler)
        if (clean.contains(':')) {
            // ::1 zaten yukarıda kontrol edildi
            // ULA (Unique Local Address fc00::/7 -> fc.. veya fd..)
            if (clean.startsWith("fc") || clean.startsWith("fd")) return true
            // Link-local fe80::/10
            if (clean.startsWith("fe8") || clean.startsWith("fe9") || clean.startsWith("fea") || clean.startsWith("feb")) return true
            return false
        }

        // 3. Loopback denetimi (localhost ve 127.0.0.0/8)
        val isLoopback = clean == "localhost" || isIpv4Loopback(clean)
        if (isLoopback) {
            // YALNIZCA bu oturumda izin listesine kaydedilmiş portlar geçer
            return port !in allowedLoopbackPorts
        }

        // 4. IPv4 özel adres blokları
        if (isIpv4Literal(clean)) {
            if (IPV4_PRIVATE_PREFIXES.any { clean.startsWith(it) }) return true
        }

        return false
    }

    private fun isIpv4Loopback(host: String): Boolean {
        if (!host.startsWith("127.")) return false
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun isIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }
}
