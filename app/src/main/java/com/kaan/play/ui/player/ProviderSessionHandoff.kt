package com.kaan.play.ui.player

/** Same-process activity handoff only. Runtime plugin objects never enter Intent JSON. */
internal class ProviderSessionHandoff<T>(private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry<T>(val text: String, val value: T, val created: Long)
    private val entries = linkedMapOf<String, Entry<T>>()
    @Synchronized fun put(text: String, value: T): String {
        prune()
        while (entries.size >= 8) entries.remove(entries.keys.first())
        return java.util.UUID.randomUUID().toString().also { entries[it] = Entry(text, value, clock()) }
    }
    @Synchronized fun get(token: String, text: String): T? {
        prune()
        return entries[token]?.takeIf { it.text == text }?.value
    }
    private fun prune() { entries.entries.removeAll { clock() - it.value.created >= 10 * 60_000L } }
}
