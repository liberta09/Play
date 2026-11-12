package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class SourceErrorRecord(
    val timestampMs: Long,
    val addonName: String,
    val host: String,
    val error: String,
)

@Serializable
data class SourceReliabilityRecord(
    val pluginKey: String,
    val addonName: String,
    val host: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
) {
    val totalAttempts: Int get() = successCount + failureCount
    val successPercent: Int
        get() = if (totalAttempts <= 0) 0 else ((successCount * 100.0) / totalAttempts).roundToInt()
}

@Serializable
data class HostBlacklistRecord(
    val host: String,
    val lastAddonName: String,
    val lastPluginKey: String,
    val consecutiveFailures: Int = 0,
    val blockedUntilMs: Long = 0L,
) {
    fun remainingMs(nowMs: Long): Long = (blockedUntilMs - nowMs).coerceAtLeast(0L)
}

enum class PluginCallOutcome(val code: Char) {
    ReturnedStreams('S'),
    NoStreams('E'),
    Unresponsive('F'),
}

@Serializable
data class PluginHealthRecord(
    val pluginKey: String,
    val addonName: String,
    val recentCalls: String = "",
    val lastUpdatedMs: Long = 0L,
) {
    val returnedStreamsCount: Int get() = recentCalls.count { it == PluginCallOutcome.ReturnedStreams.code }
    val noStreamsCount: Int get() = recentCalls.count { it == PluginCallOutcome.NoStreams.code }
    val unresponsiveCount: Int get() = recentCalls.count { it == PluginCallOutcome.Unresponsive.code }
    val totalCalls: Int get() = recentCalls.length
}

@Serializable
data class SourceHealthState(
    val errors: List<SourceErrorRecord> = emptyList(),
    val reliability: Map<String, SourceReliabilityRecord> = emptyMap(),
    val hostFailures: Map<String, HostBlacklistRecord> = emptyMap(),
    val pluginHealth: Map<String, PluginHealthRecord> = emptyMap(),
)

/**
 * Kaynak sağlığı saf iş kuralları ve gizlilik/temizleme yardımcıları (Android bağımsız).
 *
 * Kural: TAM ADRES, yol, sorgu parametresi, çerez, token HİÇBİR ZAMAN saklanmaz ve gösterilmez.
 */
object SourceHealthRules {
    const val MAX_ERROR_HISTORY = 50
    const val UI_ERROR_LIMIT = 8
    const val UI_RELIABILITY_LIMIT = 10
    const val MIN_RELIABILITY_ATTEMPTS = 2
    const val BLACKLIST_CONSECUTIVE_FAILURES = 3
    const val BLACKLIST_DURATION_MS = 6L * 60L * 60L * 1000L
    const val PLUGIN_CALL_WINDOW = 20
    const val UI_PLUGIN_HEALTH_LIMIT = 10
    const val MAX_ERROR_TEXT_LENGTH = 80
    private const val MAX_ADDON_NAME_LENGTH = 64
    private const val MAX_RELIABILITY_ENTRIES = 200
    private const val MAX_BLACKLIST_ENTRIES = 200
    private const val MAX_PLUGIN_ENTRIES = 100

    /** Yerelleştirilmiş kısa hata belirteçleri (arayüzde string resource'a çevrilir). */
    const val ERR_TIMEOUT = "timeout"
    const val ERR_FORMAT = "format_unsupported"
    const val ERR_NETWORK = "network_error"
    const val ERR_NOT_MEDIA = "invalid_media"
    const val ERR_UNKNOWN = "playback_error"

    private val HTTP_EQUALS_REGEX = Regex("""(?i)\bhttp(?:_?code)?\s*=\s*(\d{3})\b""")
    private val HTTP_STATUS_REGEX = Regex("""(?i)\bhttp(?:/[\d.]+)?\s+(\d{3})\b""")
    private val URL_SCHEME_REGEX = Regex("""(?i)(?:https?|wss?|ftp|file)://\S+""")
    private val MAGNET_REGEX = Regex("""(?i)magnet:\?\S+""")
    private val SENSITIVE_KV_REGEX = Regex(
        """(?i)\b(?:set-cookie|cookie|authorization|bearer|token|sig|signature|key|expires|hdnea|wmsauthsign|session|auth|pass(?:word)?)\b\s*[:=]?\s*\S*""",
    )
    private val QUERY_PARAM_REGEX = Regex("""\?[^\s]*""")
    private val PATH_SEGMENT_REGEX = Regex("""(?:^|\s)/\S*""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /** Tam adresten yalnızca sunucu adını döndürür; magnet için `"torrent"`, geçersizse `"?"`. */
    fun hostOf(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "?"
        if (TorrentLinks.isMagnet(trimmed)) return "torrent"
        return StreamLog.hostOf(trimmed).lowercase(Locale.ROOT)
    }

    /** Yerel sunucular (127.0.0.1 torrent sunucusu vb.) ve çözülemeyen `"?"` kara listeye alınmaz. */
    fun isBlacklistEligibleHost(host: String): Boolean {
        val normalized = host.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || normalized == "?" || normalized == "torrent") return false
        if (normalized == "localhost" || normalized == "::1" || normalized == "0.0.0.0") return false
        if (normalized == "127.0.0.1" || normalized.startsWith("127.")) return false
        return true
    }

    fun reliabilityKey(source: StreamSource, host: String): String =
        "${WrongContentBlocker.pluginIdentity(source)}|${host.lowercase(Locale.ROOT)}"

    /**
     * Hata metnindeki tam adres, yol, sorgu, çerez ve token parçalarını atar; en çok 80 karakterlik kısa tür üretir.
     */
    fun sanitizeError(raw: String?): String {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return ERR_UNKNOWN

        val httpCode = (HTTP_EQUALS_REGEX.find(source) ?: HTTP_STATUS_REGEX.find(source))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (httpCode != null && httpCode in 400..599) {
            return "HTTP $httpCode"
        }

        val upper = source.uppercase(Locale.ROOT)
        return when {
            "TIMEOUT" in upper || "TIMED OUT" in upper || "TIMED_OUT" in upper || "ZAMAN AŞIMI" in upper || "ZAMAN ASIMI" in upper -> ERR_TIMEOUT
            "DECODER" in upper || "DECODING" in upper || "PARSING" in upper || "UNSUPPORTED" in upper || "FORMAT" in upper || "INVALID_HTTP_CONTENT_TYPE" in upper -> ERR_FORMAT
            "PROBE-NOT-MEDIA" in upper || "SIZE-TOO-SMALL" in upper -> ERR_NOT_MEDIA
            "ERROR_CODE_IO_NETWORK" in upper || "CONNECTION_CLOSED" in upper || "CONNECTION_FAILED" in upper -> ERR_NETWORK
            else -> {
                val cleaned = source
                    .replace(MAGNET_REGEX, " ")
                    .replace(URL_SCHEME_REGEX, " ")
                    .replace(SENSITIVE_KV_REGEX, " ")
                    .replace(QUERY_PARAM_REGEX, " ")
                    .replace(PATH_SEGMENT_REGEX, " ")
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                    .trimEnd(':', '-', ',', ';')
                    .trim()
                if (cleaned.isEmpty()) ERR_UNKNOWN else cleaned.take(MAX_ERROR_TEXT_LENGTH).trimEnd()
            }
        }
    }

    private fun cleanAddonName(name: String): String =
        name.trim().ifEmpty { "?" }.take(MAX_ADDON_NAME_LENGTH)

    /** P1 + P2 + P3: kaynak açılmadı veya oynatma sırasında koptu. */
    fun recordFailure(
        state: SourceHealthState,
        source: StreamSource,
        url: String,
        detail: String?,
        nowMs: Long,
    ): SourceHealthState {
        val host = hostOf(url)
        val addonName = cleanAddonName(source.addonName)
        val pluginKey = WrongContentBlocker.pluginIdentity(source)
        val shortError = sanitizeError(detail)

        val newError = SourceErrorRecord(
            timestampMs = nowMs,
            addonName = addonName,
            host = host,
            error = shortError,
        )
        val updatedErrors = (listOf(newError) + state.errors).take(MAX_ERROR_HISTORY)

        val updatedReliability = if (host != "?" || source.addonName.isNotBlank()) {
            val relKey = reliabilityKey(source, host)
            val prev = state.reliability[relKey]
            val next = (prev ?: SourceReliabilityRecord(pluginKey = pluginKey, addonName = addonName, host = host)).copy(
                addonName = addonName,
                failureCount = (prev?.failureCount ?: 0) + 1,
                lastUpdatedMs = nowMs,
            )
            trimReliability(state.reliability + (relKey to next))
        } else {
            state.reliability
        }

        val updatedBlacklist = if (isBlacklistEligibleHost(host)) {
            val prevHost = state.hostFailures[host]
            val expired = prevHost != null && prevHost.blockedUntilMs > 0L && nowMs >= prevHost.blockedUntilMs
            val baseCount = if (expired || prevHost == null) 0 else prevHost.consecutiveFailures
            val nextCount = baseCount + 1
            val blockedUntil = when {
                nextCount >= BLACKLIST_CONSECUTIVE_FAILURES -> nowMs + BLACKLIST_DURATION_MS
                !expired && prevHost != null -> prevHost.blockedUntilMs
                else -> 0L
            }
            val nextHost = HostBlacklistRecord(
                host = host,
                lastAddonName = addonName,
                lastPluginKey = pluginKey,
                consecutiveFailures = nextCount,
                blockedUntilMs = blockedUntil,
            )
            trimHostFailures(state.hostFailures + (host to nextHost), nowMs)
        } else {
            state.hostFailures
        }

        return state.copy(
            errors = updatedErrors,
            reliability = updatedReliability,
            hostFailures = updatedBlacklist,
        )
    }

    /** P2 + P3: ilk kare başarıyla açıldı -> başarı sayacını artırır, kara listeyi ve ardışık hatayı sıfırlar. */
    fun recordSuccess(
        state: SourceHealthState,
        source: StreamSource,
        url: String,
        nowMs: Long,
    ): SourceHealthState {
        val host = hostOf(url)
        val addonName = cleanAddonName(source.addonName)
        val pluginKey = WrongContentBlocker.pluginIdentity(source)

        val updatedReliability = if (host != "?" || source.addonName.isNotBlank()) {
            val relKey = reliabilityKey(source, host)
            val prev = state.reliability[relKey]
            val next = (prev ?: SourceReliabilityRecord(pluginKey = pluginKey, addonName = addonName, host = host)).copy(
                addonName = addonName,
                successCount = (prev?.successCount ?: 0) + 1,
                lastUpdatedMs = nowMs,
            )
            trimReliability(state.reliability + (relKey to next))
        } else {
            state.reliability
        }

        val updatedBlacklist = if (host in state.hostFailures) {
            state.hostFailures - host
        } else {
            state.hostFailures
        }

        return state.copy(
            reliability = updatedReliability,
            hostFailures = updatedBlacklist,
        )
    }

    /** P6: eklenti çağrı sonucunu son 20 kayan pencereye işler. */
    fun recordPluginCall(
        state: SourceHealthState,
        source: StreamSource,
        outcome: PluginCallOutcome,
        nowMs: Long,
    ): SourceHealthState {
        val pluginKey = WrongContentBlocker.pluginIdentity(source)
        val addonName = cleanAddonName(source.addonName)
        val prev = state.pluginHealth[pluginKey]
        val nextCalls = ((prev?.recentCalls ?: "") + outcome.code).takeLast(PLUGIN_CALL_WINDOW)
        val next = PluginHealthRecord(
            pluginKey = pluginKey,
            addonName = addonName,
            recentCalls = nextCalls,
            lastUpdatedMs = nowMs,
        )
        return state.copy(pluginHealth = trimPluginHealth(state.pluginHealth + (pluginKey to next)))
    }

    fun recordPluginResult(
        state: SourceHealthState,
        result: StreamResult,
        nowMs: Long,
    ): SourceHealthState {
        val outcome = when (result) {
            is StreamResult.Found -> if (result.streams.isNotEmpty()) PluginCallOutcome.ReturnedStreams else PluginCallOutcome.NoStreams
            is StreamResult.Failed -> PluginCallOutcome.Unresponsive
        }
        return recordPluginCall(state, result.source, outcome, nowMs)
    }

    fun isUrlBlacklisted(state: SourceHealthState, url: String, nowMs: Long): Boolean {
        val host = hostOf(url)
        return isHostBlacklisted(state, host, nowMs)
    }

    fun isHostBlacklisted(state: SourceHealthState, host: String, nowMs: Long): Boolean {
        if (!isBlacklistEligibleHost(host)) return false
        val rec = state.hostFailures[host.lowercase(Locale.ROOT)] ?: return false
        return rec.blockedUntilMs > nowMs
    }

    fun reenableHost(state: SourceHealthState, host: String): SourceHealthState =
        state.copy(hostFailures = state.hostFailures - host.lowercase(Locale.ROOT))

    fun recentErrors(state: SourceHealthState, limit: Int = UI_ERROR_LIMIT): List<SourceErrorRecord> =
        state.errors.take(limit)

    fun worstReliability(
        state: SourceHealthState,
        limit: Int = UI_RELIABILITY_LIMIT,
        minAttempts: Int = MIN_RELIABILITY_ATTEMPTS,
    ): List<SourceReliabilityRecord> =
        state.reliability.values
            .filter { it.totalAttempts >= minAttempts }
            .sortedWith(
                compareBy<SourceReliabilityRecord> { it.successCount.toDouble() / it.totalAttempts }
                    .thenByDescending { it.totalAttempts }
                    .thenByDescending { it.lastUpdatedMs },
            )
            .take(limit)

    fun activeBlacklists(state: SourceHealthState, nowMs: Long): List<HostBlacklistRecord> =
        state.hostFailures.values
            .filter { isBlacklistEligibleHost(it.host) && it.blockedUntilMs > nowMs }
            .sortedBy { it.blockedUntilMs }

    fun worstPlugins(state: SourceHealthState, limit: Int = UI_PLUGIN_HEALTH_LIMIT): List<PluginHealthRecord> =
        state.pluginHealth.values
            .filter { it.unresponsiveCount > 0 }
            .sortedWith(
                compareByDescending<PluginHealthRecord> { it.unresponsiveCount }
                    .thenByDescending { it.totalCalls }
                    .thenBy { it.addonName.lowercase(Locale.ROOT) },
            )
            .take(limit)

    private fun trimReliability(map: Map<String, SourceReliabilityRecord>): Map<String, SourceReliabilityRecord> {
        if (map.size <= MAX_RELIABILITY_ENTRIES) return map
        return map.entries
            .sortedByDescending { it.value.lastUpdatedMs }
            .take(MAX_RELIABILITY_ENTRIES)
            .associate { it.key to it.value }
    }

    private fun trimHostFailures(map: Map<String, HostBlacklistRecord>, nowMs: Long): Map<String, HostBlacklistRecord> {
        val activeOrRecent = map.filterValues { rec ->
            rec.blockedUntilMs == 0L || rec.blockedUntilMs > nowMs
        }
        if (activeOrRecent.size <= MAX_BLACKLIST_ENTRIES) return activeOrRecent
        return activeOrRecent.entries
            .sortedByDescending { it.value.blockedUntilMs }
            .take(MAX_BLACKLIST_ENTRIES)
            .associate { it.key to it.value }
    }

    private fun trimPluginHealth(map: Map<String, PluginHealthRecord>): Map<String, PluginHealthRecord> {
        if (map.size <= MAX_PLUGIN_ENTRIES) return map
        return map.entries
            .sortedByDescending { it.value.lastUpdatedMs }
            .take(MAX_PLUGIN_ENTRIES)
            .associate { it.key to it.value }
    }
}

/**
 * Yerel DataStore destekli kaynak sağlığı deposu.
 * Sadece cihaz içinde tutulur; hiçbir sunucuya gönderilmez.
 */
class SourceHealthStore(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SourceHealthState())
    val state: StateFlow<SourceHealthState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded(): SourceHealthState = mutex.withLock {
        if (!loaded) {
            val disk = read()
            _state.value = disk
            loaded = true
        }
        _state.value
    }

    fun isUrlBlacklistedCached(url: String, nowMs: Long = clock()): Boolean =
        SourceHealthRules.isUrlBlacklisted(_state.value, url, nowMs)

    suspend fun recordFailure(source: StreamSource, url: String, detail: String?) {
        mutate { current -> SourceHealthRules.recordFailure(current, source, url, detail, clock()) }
    }

    suspend fun recordSuccess(source: StreamSource, url: String) {
        mutate { current -> SourceHealthRules.recordSuccess(current, source, url, clock()) }
    }

    suspend fun recordPluginResult(result: StreamResult) {
        mutate { current -> SourceHealthRules.recordPluginResult(current, result, clock()) }
    }

    suspend fun reenableHost(host: String) {
        mutate { current -> SourceHealthRules.reenableHost(current, host) }
    }

    /** P7: P1, P2, P3, P6 kayıtlarını siler (P4 yanlış içerik engellerine dokunmaz). */
    suspend fun clearHealthLogs() {
        mutate { SourceHealthState() }
    }

    /** Yalnızca hata ayıklama/test doğrulaması için sahte kayıt üretir. */
    suspend fun seedSampleForDebug() {
        val now = clock()
        val s1 = StreamSource(901L, "WebStreamX")
        val s2 = StreamSource(902L, "HDFilmDemo")
        val s3 = StreamSource(903L, "SlowScraper")
        var sample = SourceHealthState()
        // s1: fast cdn başarısız -> 3 hata -> kara liste
        repeat(3) { idx ->
            sample = SourceHealthRules.recordFailure(
                sample,
                s1,
                "https://cdn-bad.example.com/movie-$idx.mp4?token=secret",
                if (idx == 0) "ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=403" else "timeout",
                now - (idx * 60_000L),
            )
        }
        // s2: karışık güvenilirlik (%33)
        sample = SourceHealthRules.recordSuccess(sample, s2, "https://mirror.example.org/v.mp4", now - 240_000L)
        sample = SourceHealthRules.recordFailure(sample, s2, "https://mirror.example.org/v.mp4", "ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)", now - 180_000L)
        sample = SourceHealthRules.recordFailure(sample, s2, "https://mirror.example.org/v.mp4", "ERROR_CODE_DECODER_INIT_FAILED (4001)", now - 120_000L)
        // s3: eklenti son çağrılarda yanıt vermedi
        repeat(7) { idx ->
            sample = SourceHealthRules.recordPluginCall(sample, s3, PluginCallOutcome.Unresponsive, now - (idx * 10_000L))
        }
        repeat(5) { idx ->
            sample = SourceHealthRules.recordPluginCall(sample, s3, PluginCallOutcome.ReturnedStreams, now - (idx * 10_000L))
        }
        repeat(3) { idx ->
            sample = SourceHealthRules.recordPluginCall(sample, s1, PluginCallOutcome.Unresponsive, now - (idx * 10_000L))
        }
        mutate { sample }
    }

    private suspend fun mutate(transform: (SourceHealthState) -> SourceHealthState) {
        mutex.withLock {
            val base = if (loaded) _state.value else read()
            val next = transform(base)
            _state.value = next
            loaded = true
            try {
                val encoded = PlayJson.encodeToString(SourceHealthState.serializer(), next)
                store.edit { prefs -> prefs[KEY] = encoded }
            } catch (_: IOException) {
            }
        }
    }

    private suspend fun read(): SourceHealthState = try {
        val raw = store.data.first()[KEY]
        if (raw.isNullOrBlank()) SourceHealthState()
        else PlayJson.decodeFromString(SourceHealthState.serializer(), raw)
    } catch (_: Exception) {
        SourceHealthState()
    }

    companion object {
        val KEY = stringPreferencesKey("source_health_state")
    }
}
