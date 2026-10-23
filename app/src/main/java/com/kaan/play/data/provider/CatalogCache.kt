package com.kaan.play.data.provider

import java.util.concurrent.ConcurrentHashMap

/**
 * Sağlayıcı katalog satırları için ~5 dakikalık bellek içi önbellek.
 */
class CatalogCache(
    private val ttlMs: Long = 5 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val rows: List<CatalogRow>, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun get(key: String): List<CatalogRow>? {
        val entry = cache[key] ?: return null
        if (clock() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return null
        }
        return entry.rows
    }

    fun put(key: String, rows: List<CatalogRow>) {
        cache[key] = Entry(rows, clock())
    }

    fun clear(key: String? = null) {
        if (key != null) cache.remove(key) else cache.clear()
    }

    fun removeForAddon(addonId: Long) {
        val target = "nuvio_addon:$addonId"
        cache.keys.removeIf { it.contains(target) }
    }
}
