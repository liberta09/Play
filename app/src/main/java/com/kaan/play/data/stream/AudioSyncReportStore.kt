package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException

/** Eklenti başına yerel "ses senkronu bozuk" bildirim sayacı (adres saklanmaz). */
class AudioSyncReportStore(private val store: DataStore<Preferences>) {

    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    suspend fun increment(source: StreamSource): Int {
        val pluginKey = WrongContentBlocker.pluginIdentity(source)
        var nextCount = 1
        try {
            store.edit { prefs ->
                val current = decode(prefs[KEY]).toMutableMap()
                nextCount = (current[pluginKey] ?: 0) + 1
                current[pluginKey] = nextCount
                prefs[KEY] = PlayJson.encodeToString(serializer, current)
            }
        } catch (_: IOException) {
        }
        return nextCount
    }

    suspend fun countFor(source: StreamSource): Int {
        val pluginKey = WrongContentBlocker.pluginIdentity(source)
        return snapshot()[pluginKey] ?: 0
    }

    suspend fun snapshot(): Map<String, Int> = try {
        decode(store.data.first()[KEY])
    } catch (_: Throwable) {
        emptyMap()
    }

    val counts: Flow<Map<String, Int>> = store.data
        .map { decode(it[KEY]) }
        .catch { emit(emptyMap()) }

    suspend fun clear() {
        try {
            store.edit { prefs -> prefs.remove(KEY) }
        } catch (_: IOException) {
        }
    }

    private fun decode(raw: String?): Map<String, Int> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { PlayJson.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())

    companion object {
        val KEY = stringPreferencesKey("audio_sync_report_counts")
    }
}
