package com.kaan.play.data.stream

import java.util.concurrent.ConcurrentHashMap

/**
 * Takılma nedeniyle terk edilen yavaş kaynak sunucularını (host) bellekte tutar.
 *
 * Kurallar:
 * - Karar çıkan kaynağın SUNUCU ADI (host) 30 dk "yavaş" işaretlenir.
 * - Diske yazılmaz (yalnızca bellek içi).
 * - Günlüğe adres yazılmaz; tanı yalnızca eklenti adıyla tutulur.
 * - 30 dk dolunca yavaş işareti kendiliğinden kalkar.
 */
object SlowHostMemory {
    /** Yavaş işaretinin bellekte kalma süresi (30 dakika). */
    const val SLOW_HOST_EXPIRY_MS = 30 * 60 * 1000L

    private val slowHosts = ConcurrentHashMap<String, Long>()

    fun markSlow(url: String, nowMs: Long) {
        val host = StreamLog.hostOf(url).takeIf { it != "?" && it.isNotBlank() } ?: return
        slowHosts[host] = nowMs + SLOW_HOST_EXPIRY_MS
    }

    fun isSlow(url: String, nowMs: Long): Boolean {
        val host = StreamLog.hostOf(url).takeIf { it != "?" && it.isNotBlank() } ?: return false
        val expiry = slowHosts[host] ?: return false
        return if (nowMs < expiry) {
            true
        } else {
            slowHosts.remove(host, expiry)
            false
        }
    }

    fun isHostSlow(host: String, nowMs: Long): Boolean {
        if (host == "?" || host.isBlank()) return false
        val expiry = slowHosts[host] ?: return false
        return if (nowMs < expiry) {
            true
        } else {
            slowHosts.remove(host, expiry)
            false
        }
    }

    fun clear() {
        slowHosts.clear()
    }
}
