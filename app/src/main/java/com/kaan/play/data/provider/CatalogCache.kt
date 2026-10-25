package com.kaan.play.data.provider

import java.util.concurrent.ConcurrentHashMap

/**
 * Sağlayıcı katalog satırları için bellek içi önbellek.
 * Varsayılan süre ~5 dakikadır; kısa ömürlü kısmi sonuçlar daha kısa TTL ile saklanabilir.
 */
class CatalogCache(
    private val ttlMs: Long = 5 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val rows: List<CatalogRow>, val timestamp: Long, val ttlMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun get(key: String): List<CatalogRow>? {
        val entry = cache[key] ?: return null
        if (clock() - entry.timestamp > entry.ttlMs) {
            cache.remove(key, entry)
            return null
        }
        return entry.rows
    }

    fun put(key: String, rows: List<CatalogRow>, ttlMs: Long = this.ttlMs) {
        cache[key] = Entry(rows, clock(), ttlMs.coerceAtLeast(0L))
    }

    fun clear(key: String? = null) {
        if (key != null) cache.remove(key) else cache.clear()
    }

    fun removeForAddon(addonId: Long) {
        val target = "nuvio_addon:$addonId"
        cache.keys.removeIf { it.contains(target) }
    }
}
