package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

@Serializable
data class WrongContentBlockEntry(
    val key: String,
    val addedAtMs: Long,
    val addonId: Long = 0L,
    val addonName: String? = null,
    val contentTitle: String? = null,
    val year: Int? = null,
)

/**
 * Yanlış içerik veren eklenti/kaynak engel anahtarlarını ve 500 kayıt sınırını yöneten saf kurallar.
 * Anahtar = SHA-256(içerikKimliği + "|" + eklentiKimliği). Dizide sezon/bölümden bağımsızdır.
 */
object WrongContentBlocker {

    const val MAX_ENTRIES = 500

    private val COMBINING_MARKS = Regex("""\p{M}+""")
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

    /** NFD ayrıştırma, `\p{M}` silme, ROOT küçük harf, `[^\p{L}\p{N}]+` → boşluk, trim. */
    fun fold(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val noMarks = COMBINING_MARKS.replace(nfd, "")
        val lower = noMarks.lowercase(Locale.ROOT)
        return NON_ALNUM.replace(lower, " ").trim()
    }

    /** `{movie|tv}:tmdb:ID`, yoksa `imdb:tt…`, o da yoksa `title:{katlanmış başlık}:{yıl}`. */
    fun contentIdentity(
        request: StreamRequest,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): String {
        if (request.tmdbId > 0) {
            val kind = if (request.type == MediaType.Tv) "tv" else "movie"
            return "$kind:tmdb:${request.tmdbId}"
        }
        val imdb = request.imdbId?.trim()?.lowercase(Locale.ROOT)
        if (!imdb.isNullOrBlank() && imdb.startsWith("tt")) {
            return "imdb:$imdb"
        }
        val foldedTitle = fold(request.title?.takeIf { it.isNotBlank() } ?: fallbackTitle)
        val year = (request.year ?: fallbackYear)?.takeIf { it > 0 }?.toString().orEmpty()
        return "title:$foldedTitle:$year"
    }

    /** `id:{addonId}` (>0 ise), yoksa `name:{katlanmış eklenti adı}`. */
    fun pluginIdentity(source: StreamSource): String =
        if (source.addonId > 0L) "id:${source.addonId}" else "name:${fold(source.addonName)}"

    fun blockKey(
        request: StreamRequest,
        source: StreamSource,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): String = sha256Hex("${contentIdentity(request, fallbackTitle, fallbackYear)}|${pluginIdentity(source)}")

    fun upsertAndTrim(
        entries: List<WrongContentBlockEntry>,
        key: String,
        nowMs: Long,
        addonId: Long = 0L,
        addonName: String? = null,
        contentTitle: String? = null,
        year: Int? = null,
    ): List<WrongContentBlockEntry> {
        val entry = WrongContentBlockEntry(
            key = key,
            addedAtMs = nowMs,
            addonId = addonId,
            addonName = addonName?.trim()?.takeIf { it.isNotEmpty() }?.take(64),
            contentTitle = contentTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
            year = year?.takeIf { it > 0 },
        )
        val next = entries.filterNot { it.key == key } + entry
        return next.sortedByDescending { it.addedAtMs }.take(MAX_ENTRIES)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/** Yanlış içerik engellerini DataStore `wrong_content_blocks` anahtarında kalıcı saklar (en çok 500 kayıt). */
class WrongContentBlockStore(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val serializer = ListSerializer(WrongContentBlockEntry.serializer())

    @Volatile
    private var memoryKeys: Set<String> = emptySet()

    @Volatile
    private var loaded: Boolean = false

    suspend fun ensureLoaded(): Set<String> {
        if (!loaded) {
            val entries = read()
            synchronized(this) {
                memoryKeys = (memoryKeys + entries.map { it.key }).toSet()
                loaded = true
            }
        }
        return memoryKeys
    }

    fun isBlockedCached(
        request: StreamRequest,
        source: StreamSource,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): Boolean {
        val key = WrongContentBlocker.blockKey(request, source, fallbackTitle, fallbackYear)
        return key in memoryKeys
    }

    suspend fun isBlocked(
        request: StreamRequest,
        source: StreamSource,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): Boolean {
        ensureLoaded()
        return isBlockedCached(request, source, fallbackTitle, fallbackYear)
    }

    fun markBlockedInMemory(
        request: StreamRequest,
        source: StreamSource,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): String {
        val key = WrongContentBlocker.blockKey(request, source, fallbackTitle, fallbackYear)
        synchronized(this) {
            memoryKeys = memoryKeys + key
        }
        return key
    }

    val entriesFlow: Flow<List<WrongContentBlockEntry>> = store.data
        .map { decode(it[KEY]) }
        .catch { emit(emptyList()) }

    suspend fun block(
        request: StreamRequest,
        source: StreamSource,
        fallbackTitle: String? = null,
        fallbackYear: Int? = null,
    ): String {
        val key = markBlockedInMemory(request, source, fallbackTitle, fallbackYear)
        val now = clock()
        val displayTitle = (fallbackTitle ?: request.title)
        val displayYear = fallbackYear ?: request.year
        try {
            store.edit { prefs ->
                val current = decode(prefs[KEY])
                val updated = WrongContentBlocker.upsertAndTrim(
                    entries = current,
                    key = key,
                    nowMs = now,
                    addonId = source.addonId,
                    addonName = source.addonName,
                    contentTitle = displayTitle,
                    year = displayYear,
                )
                prefs[KEY] = PlayJson.encodeToString(serializer, updated)
                synchronized(this) {
                    memoryKeys = updated.mapTo(HashSet()) { it.key }
                    loaded = true
                }
            }
        } catch (_: IOException) {
        }
        return key
    }

    suspend fun remove(key: String) {
        synchronized(this) {
            memoryKeys = memoryKeys - key
        }
        try {
            store.edit { prefs ->
                val updated = decode(prefs[KEY]).filterNot { it.key == key }
                prefs[KEY] = PlayJson.encodeToString(serializer, updated)
                synchronized(this) {
                    memoryKeys = updated.mapTo(HashSet()) { it.key }
                    loaded = true
                }
            }
        } catch (_: IOException) {
        }
    }

    internal suspend fun entries(): List<WrongContentBlockEntry> = read()

    private suspend fun read(): List<WrongContentBlockEntry> = try {
        decode(store.data.first()[KEY])
    } catch (_: Throwable) {
        emptyList()
    }

    private fun decode(raw: String?): List<WrongContentBlockEntry> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { PlayJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    companion object {
        val KEY = stringPreferencesKey("wrong_content_blocks")
    }
}
