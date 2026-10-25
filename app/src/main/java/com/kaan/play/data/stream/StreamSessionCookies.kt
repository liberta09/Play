package com.kaan.play.data.stream

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived in-memory bridge for cookies collected by one Nuvio JS plugin call.
 * Keys contain a URL hash, never the original stream URL; cookies are only returned through OkHttp's domain/path matching.
 */
internal object StreamSessionCookies {
    private data class Key(val sourceId: Long, val urlHash: String)
    private data class Entry(val cookies: List<Cookie>, val expiresAt: Long)

    private const val SESSION_TTL_MS = 6 * 60 * 1000L
    private const val MAX_ENTRIES = 200
    private val entries = ConcurrentHashMap<Key, Entry>()

    fun remember(sourceId: Long, streamUrl: String, cookies: List<Cookie>) {
        val url = streamUrl.toHttpUrlOrNull() ?: return
        val sessionKey = key(sourceId, url.toString())
        entries.remove(sessionKey)
        val now = System.currentTimeMillis()
        val scoped = cookies.asSequence()
            .filter { it.expiresAt > now && it.matches(url) }
            .distinctBy { Triple(it.name, it.domain, it.path) }
            .take(100)
            .toList()
        if (scoped.isEmpty()) return

        entries.entries.removeIf { it.value.expiresAt <= now }
        if (entries.size >= MAX_ENTRIES) {
            entries.entries.minByOrNull { it.value.expiresAt }?.key?.let(entries::remove)
        }
        if (scoped.isNotEmpty()) entries[sessionKey] = Entry(scoped, now + SESSION_TTL_MS)
    }

    fun forPlayback(sourceId: Long, streamUrl: String): List<Cookie> {
        val url = streamUrl.toHttpUrlOrNull() ?: return emptyList()
        val key = key(sourceId, url.toString())
        val entry = entries[key] ?: return emptyList()
        val now = System.currentTimeMillis()
        if (entry.expiresAt <= now) {
            entries.remove(key, entry)
            return emptyList()
        }
        return entry.cookies.filter { it.expiresAt > now && it.matches(url) }
    }

    private fun key(sourceId: Long, url: String): Key {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            digest.forEach { byte -> append("%02x".format(java.util.Locale.ROOT, byte.toInt() and 0xff)) }
        }
        return Key(sourceId, hex)
    }
}

/** Cookie jar for one active stream; OkHttp still enforces expiry, domain, path, and secure-cookie rules. */
internal class StreamPlaybackCookieJar : CookieJar {
    @Volatile private var cookies: List<Cookie> = emptyList()

    @Synchronized
    fun replace(values: List<Cookie>) {
        cookies = values.filter { it.expiresAt > System.currentTimeMillis() }.take(100)
    }

    @Synchronized
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val next = this.cookies.filter { it.expiresAt > now }.toMutableList()
        cookies.filter { it.matches(url) }.forEach { cookie ->
            next.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > now && next.size < 100) next += cookie
        }
        this.cookies = next
    }

    @Synchronized
    override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies = cookies.filter { it.expiresAt > now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies = emptyList()
    }
}
