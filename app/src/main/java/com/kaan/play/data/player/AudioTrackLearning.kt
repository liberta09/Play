package com.kaan.play.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.util.Locale

@Serializable
data class AudioTrackSignature(
    val trackIndex: Int,
    val mime: String,
    val channelCount: Int,
    val labelPattern: String,
    val trackCount: Int,
)

@Serializable
data class LearnedAudioTrackEntry(
    val pluginId: String,
    val contentKey: String,
    val signature: AudioTrackSignature,
    val updatedAtMs: Long,
)

/**
 * Dil bilgisi olmayan ses parçalarında kullanıcının elle seçtiği parçayı imzasıyla öğrenme kuralları (Android'den bağımsız).
 * Adres saklanmaz; kayıt 60 gün geçerli, en çok 200 kayıt tutulur.
 */
object AudioTrackLearning {

    const val MAX_ENTRIES = 200
    const val MAX_AGE_MS = 60L * 24L * 60L * 60L * 1000L
    const val MIN_PLAYBACK_MS = 30_000L

    private val DIGITS = Regex("""\d+""")

    fun labelPattern(label: String?): String =
        label.orEmpty().trim().lowercase(Locale.ROOT).replace(DIGITS, "#")

    fun pluginIdFor(stream: PlayableStream, request: StreamRequest): String {
        if (stream.source.addonId > 0L) return "id:${stream.source.addonId}"
        val reqPlugin = request.pluginId?.trim()?.takeIf { it.isNotEmpty() }
        if (reqPlugin != null) return reqPlugin
        return "name:${TurkishAudioTrackRules.fold(stream.source.addonName).trim()}"
    }

    fun contentKeyFor(request: StreamRequest): String = when {
        request.tmdbId > 0 -> "tmdb:${request.tmdbId}"
        !request.imdbId.isNullOrBlank() -> "imdb:${request.imdbId.trim().lowercase(Locale.ROOT)}"
        else -> ""
    }

    /** Desteklenen ses parçaları arasından [chosenTrackIndex] için imza üretir; bulunamazsa null. */
    fun buildSignature(tracks: List<AudioTrackInfo>, chosenTrackIndex: Int): AudioTrackSignature? {
        val supported = tracks.filter { it.isSupported }
        if (supported.isEmpty()) return null
        val pos = supported.indexOfFirst { it.index == chosenTrackIndex }
        if (pos < 0) return null
        val track = supported[pos]
        return AudioTrackSignature(
            trackIndex = pos,
            mime = track.mime.orEmpty().trim().lowercase(Locale.ROOT),
            channelCount = track.channelCount.coerceAtLeast(0),
            labelPattern = labelPattern(track.label),
            trackCount = supported.size,
        )
    }

    /** Süresi dolanları temizler, aynı `(pluginId, contentKey)` varsa günceller, 200 sınırını uygular. */
    fun pruneAndUpsert(
        entries: List<LearnedAudioTrackEntry>,
        newEntry: LearnedAudioTrackEntry,
        nowMs: Long,
    ): List<LearnedAudioTrackEntry> {
        val fresh = entries.filter { entry ->
            (nowMs - entry.updatedAtMs) in 0L..MAX_AGE_MS &&
                !(entry.pluginId == newEntry.pluginId && entry.contentKey == newEntry.contentKey)
        } + newEntry
        return fresh.sortedByDescending { it.updatedAtMs }.take(MAX_ENTRIES)
    }

    /**
     * Dil bilgisi bulunmayan çok parçalı akışta birebir eşleşen öğrenilmiş parça indeksini döndürür:
     * önce aynı eklenti + aynı içerik, yoksa aynı eklentinin başka içeriği (en güncelden eskiye).
     */
    fun findMatchingTrackIndex(
        entries: List<LearnedAudioTrackEntry>,
        pluginId: String,
        contentKey: String,
        tracks: List<AudioTrackInfo>,
        nowMs: Long,
    ): Int? {
        val supported = tracks.filter { it.isSupported }
        if (supported.size <= 1 || AudioTrackRules.hasAnyLanguageInfo(supported)) return null

        val activeForPlugin = entries
            .filter { it.pluginId == pluginId && (nowMs - it.updatedAtMs) in 0L..MAX_AGE_MS }
            .sortedByDescending { it.updatedAtMs }

        fun matches(entry: LearnedAudioTrackEntry): Int? {
            if (entry.signature.trackCount != supported.size) return null
            val candidate = supported.getOrNull(entry.signature.trackIndex) ?: return null
            val sig = buildSignature(supported, candidate.index) ?: return null
            return if (sig == entry.signature) candidate.index else null
        }

        if (contentKey.isNotBlank()) {
            for (entry in activeForPlugin) {
                if (entry.contentKey == contentKey) {
                    matches(entry)?.let { return it }
                }
            }
        }
        for (entry in activeForPlugin) {
            if (entry.contentKey != contentKey) {
                matches(entry)?.let { return it }
            }
        }
        return null
    }
}

/** Öğrenilmiş ses izlerini DataStore `audio_track_learning` anahtarında kalıcı saklar. */
class AudioTrackLearningStore(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val serializer = ListSerializer(LearnedAudioTrackEntry.serializer())

    suspend fun findMatch(
        stream: PlayableStream,
        request: StreamRequest,
        tracks: List<AudioTrackInfo>,
    ): Int? {
        val pluginId = AudioTrackLearning.pluginIdFor(stream, request)
        val contentKey = AudioTrackLearning.contentKeyFor(request)
        val entries = read()
        return AudioTrackLearning.findMatchingTrackIndex(entries, pluginId, contentKey, tracks, clock())
    }

    suspend fun record(
        stream: PlayableStream,
        request: StreamRequest,
        tracks: List<AudioTrackInfo>,
        chosenTrackIndex: Int,
    ) {
        val supported = tracks.filter { it.isSupported }
        if (supported.size <= 1 || AudioTrackRules.hasAnyLanguageInfo(supported)) return
        val signature = AudioTrackLearning.buildSignature(supported, chosenTrackIndex) ?: return
        val pluginId = AudioTrackLearning.pluginIdFor(stream, request)
        val contentKey = AudioTrackLearning.contentKeyFor(request)
        val now = clock()
        val entry = LearnedAudioTrackEntry(
            pluginId = pluginId,
            contentKey = contentKey,
            signature = signature,
            updatedAtMs = now,
        )
        try {
            store.edit { prefs ->
                val current = decode(prefs[KEY])
                val updated = AudioTrackLearning.pruneAndUpsert(current, entry, now)
                prefs[KEY] = PlayJson.encodeToString(serializer, updated)
            }
        } catch (_: IOException) {
        }
    }

    internal suspend fun read(): List<LearnedAudioTrackEntry> = try {
        decode(store.data.first()[KEY])
    } catch (_: Throwable) {
        emptyList()
    }

    private fun decode(raw: String?): List<LearnedAudioTrackEntry> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { PlayJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    companion object {
        val KEY = stringPreferencesKey("audio_track_learning")
    }
}
