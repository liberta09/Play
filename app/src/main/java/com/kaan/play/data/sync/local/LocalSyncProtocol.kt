package com.kaan.play.data.sync.local

import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Wi-Fi senkronizasyonu (aynı ağdaki iki cihaz) protokol sabitleri. Ortak geliştiricinin sürümüyle uyumlu:
 * TCP sunucu 43821, UDP bulma 43822 ("PLAY_SYNC_DISCOVER" → "PLAY_SYNC_HERE"), uç noktalar GET /health, GET /profiles,
 * POST /sync; gövde JSON. /profiles ve /sync gövdeleri AES-GCM ile şifrelidir ([LocalSyncCrypto]).
 */
object LocalSyncProtocol {
    const val PORT = 43821
    const val DISCOVERY_PORT = 43822
    const val DISCOVER = "PLAY_SYNC_DISCOVER"
    const val HERE = "PLAY_SYNC_HERE"
    const val NSD_TYPE = "_playsync._tcp"

    const val PATH_HEALTH = "/health"
    const val PATH_PROFILES = "/profiles"
    const val PATH_SYNC = "/sync"

    const val HEADER_SERVER_NONCE = "X-Play-Server-Nonce"
    const val HEADER_CLIENT_NONCE = "X-Play-Client-Nonce"
    const val HEADER_PROOF = "X-Play-Proof"

    /** Gövde sınırı (istek ve yanıt). */
    const val MAX_BODY_BYTES = 5 * 1024 * 1024

    /** Kod ve sunucu bu kadar açık kalır. */
    const val CODE_TTL_MS = 3 * 60 * 1000L

    /** Bu kadar yanlış denemede sunucu kapanır, yeni kod gerekir. */
    const val MAX_ATTEMPTS = 5

    const val READ_TIMEOUT_MS = 10_000
}

/** Tek kullanımlık 6 haneli kod: her kart açılışında `SecureRandom` ile yeniden üretilir, saklanmaz. */
object LocalSyncCode {
    const val LENGTH = 6

    fun generate(random: SecureRandom = SecureRandom()): String =
        random.nextInt(1_000_000).toString().padStart(LENGTH, '0')

    /** Girilen metinden boşluk/tire atılır; tam 6 rakam değilse null. */
    fun normalize(input: String): String? =
        input.filterNot { it.isWhitespace() || it == '-' }.takeIf { it.length == LENGTH && it.all { c -> c in '0'..'9' } }
}

/** Yanlış deneme sayacı: [max]. yanlışta kilitlenir (sunucu kapanır). İş parçacığı güvenli. */
class SyncAttemptLimiter(private val max: Int = LocalSyncProtocol.MAX_ATTEMPTS) {
    private var failures = 0

    val locked: Boolean @Synchronized get() = failures >= max

    val remaining: Int @Synchronized get() = (max - failures).coerceAtLeast(0)

    /** Bir yanlış daha; kilitlendiyse true. */
    @Synchronized
    fun fail(): Boolean {
        failures++
        return failures >= max
    }
}

/** Yalnızca özel IPv4 ağları (10/8, 172.16/12, 192.168/16); internete hiçbir şey çıkmaz, dışarıdan gelen kabul edilmez. */
object LocalNetwork {

    fun isPrivateLan(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val b = address.address
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    /** Elle girilen adres yalnızca rakamlı IPv4 olabilir (DNS sorgusu yapılmaz). */
    fun parseIpv4(host: String): InetAddress? {
        val parts = host.trim().split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255 || p.isEmpty() || p.length > 3) return null
            bytes[i] = n.toByte()
        }
        return InetAddress.getByAddress(bytes)
    }
}
