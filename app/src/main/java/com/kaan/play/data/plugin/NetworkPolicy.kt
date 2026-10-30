package com.kaan.play.data.plugin

import com.kaan.play.data.net.await
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException

/**
 * Üçüncü taraf JS'in (ve depodan indirilen dosyaların) ağa çıkış kuralı. Amaç SSRF'yi engellemek: eklenti,
 * kullanıcının yerel ağındaki cihazlara (yönlendirici, NAS, yerel addon, bulut meta verisi) istek atamasın.
 *
 * - Yalnızca `http`/`https`.
 * - Çözümlenen **her** adres denetlenir ([Dns] sarmalayıcısı bağlantıdan önce, tek çözümleme: DNS yeniden bağlama
 *   saldırısına açık değil): loopback, any-local, link-local (169.254/16, fe80::/10), özel ağ (10/8, 172.16/12,
 *   192.168/16, fc00::/7), CGNAT (100.64/10), çok noktaya yayın, ayrılmış aralıklar. Gömülü IPv4'lü IPv6 da çözülür.
 * - IP **sabit değeri** (`http://127.0.0.1/`) OkHttp'de DNS'e uğramaz; bu yüzden adres ayrıca elle denetlenir.
 * - Yönlendirmeler OkHttp'ye bırakılmaz; her adım [execute] içinde aynı denetimden geçer.
 * - Sistem proxy'si kullanılmaz.
 *
 * @param allowPrivate yalnızca testler için: her adrese izin verir (yerel sahte sunucu).
 * @param allowPrivateHosts özel adrese izin verilen ana makine adları (kullanıcının kendi LAN deposu).
 */
class NetworkPolicy(
    private val allowPrivate: Boolean = false,
    private val allowPrivateHosts: Set<String> = emptySet(),
) {

    /** [base]'i bu kurala bağlar: kendi DNS'imiz, proxy yok, yönlendirme elle. */
    fun apply(base: OkHttpClient.Builder): OkHttpClient.Builder = base
        .proxy(Proxy.NO_PROXY)
        .dns(PolicyDns())
        .followRedirects(false)
        .followSslRedirects(false)

    /** @return reddedilecekse neden; izinliyse null. Ağa çıkmadan önce çağrılır. */
    fun checkUrl(url: HttpUrl): String? {
        if (url.scheme != "http" && url.scheme != "https") return "scheme"
        val host = url.host
        if (host.isBlank()) return "host"
        if (allowPrivate || host in allowPrivateHosts) return null
        val lower = host.lowercase().trimEnd('.')
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return "private"
        }
        literalAddress(host)?.let { if (isBlocked(it)) return "private" }
        return null
    }

    fun isBlocked(address: InetAddress): Boolean {
        if (allowPrivate) return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        val b = address.address
        return when (address) {
            is Inet4Address -> {
                val a0 = b[0].toInt() and 0xFF
                val a1 = b[1].toInt() and 0xFF
                a0 == 0 || // 0.0.0.0/8
                    (a0 == 100 && a1 in 64..127) || // CGNAT
                    (a0 == 192 && a1 == 0 && (b[2].toInt() and 0xFF) == 0) || // 192.0.0.0/24
                    (a0 == 198 && a1 in 18..19) || // benchmark
                    a0 >= 240 // ayrılmış + yayın
            }
            is Inet6Address -> {
                val first = b[0].toInt() and 0xFF
                if ((first and 0xFE) == 0xFC) return true // fc00::/7 benzersiz yerel
                // NAT64 (64:ff9b::/96) ve IPv4-uyumlu: gömülü IPv4'ü ayrıca denetle.
                val nat64 = b.copyOfRange(0, 12).contentEquals(NAT64_PREFIX)
                val compat = b.copyOfRange(0, 12).all { it.toInt() == 0 }
                if (nat64 || compat) {
                    val v4 = InetAddress.getByAddress(b.copyOfRange(12, 16))
                    return isBlocked(v4)
                }
                false
            }
            else -> true
        }
    }

    /**
     * İstek çalıştırır; yönlendirmeleri elle (en çok [maxRedirects]) ve her adımı denetleyerek izler.
     * @param client [apply] ile kurulmuş istemci.
     * @param follow false ise ilk yanıt (3xx dahil) olduğu gibi döner.
     * @throws IOException engellenen adres, çok fazla yönlendirme ya da ağ hatası.
     */
    suspend fun execute(client: OkHttpClient, request: Request, follow: Boolean = true, maxRedirects: Int = 5): Response {
        var current = request
        var hops = 0
        while (true) {
            checkUrl(current.url)?.let { throw BlockedAddressException(it) }
            val response = client.newCall(current).await()
            val code = response.code
            val location = response.header("Location")
            if (!follow || code !in REDIRECT_CODES || location == null) return response
            if (++hops > maxRedirects) {
                response.close()
                throw IOException("Too many redirects")
            }
            val next = response.request.url.resolve(location)
            response.close()
            if (next == null) throw BlockedAddressException("scheme")
            val keepBody = code == 307 || code == 308
            val builder = current.newBuilder().url(next)
            if (!keepBody) {
                builder.method("GET", null)
                builder.removeHeader("Content-Type").removeHeader("Content-Length")
            } else if (current.body == null && current.method != "GET" && current.method != "HEAD") {
                builder.method(current.method, ByteArray(0).toRequestBody())
            }
            if (next.host != current.url.host) {
                // Başka siteye giderken kimlik bilgisi taşıyan başlıklar bırakılır.
                builder.removeHeader("Authorization").removeHeader("Cookie")
            }
            current = builder.build()
        }
    }

    private inner class PolicyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // IPv4 önce (IPv6 yedekte kalır): IPv6'sı yanıt vermeyen ağlarda/emülatörlerde ilk bağlantı saniyelerce beklemesin
            // ya da "network error" ile düşmesin. Yalnızca sıra değişir, adres denetimi aşağıda aynen sürer.
            val all = com.kaan.play.data.net.Ipv4FirstDns.order(Dns.SYSTEM.lookup(hostname))
            if (allowPrivate || hostname in allowPrivateHosts) return all
            val allowed = all.filterNot { isBlocked(it) }
            if (allowed.isEmpty()) throw UnknownHostException("blocked")
            return allowed
        }
    }

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val NAT64_PREFIX = byteArrayOf(0, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)

        /** `host` bir IP sabit değeriyse (DNS yok) adrese çevirir; alan adıysa null. */
        internal fun literalAddress(host: String): InetAddress? {
            val h = host.removePrefix("[").removeSuffix("]")
            val v4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(h)
            if (!v4 && ':' !in h) return null
            return try {
                InetAddress.getByName(h) // sabit değerde DNS sorgusu yapmaz
            } catch (_: UnknownHostException) {
                null
            }
        }
    }
}

/** Ağ kuralı (SSRF) nedeniyle reddedilen istek. */
class BlockedAddressException(val reason: String) : IOException("Blocked by network policy: $reason")
