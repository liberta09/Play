package com.kaan.play.data.subtitle

import android.util.Log
import com.kaan.play.data.db.SubtitleDao
import com.kaan.play.data.db.SubtitleLanguageEntity
import com.kaan.play.data.db.SubtitleOffsetEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import java.security.MessageDigest

/**
 * Altyazıyla ilgili kalıcı hafıza (Room): (1) bir film/dizide seçilen dil: dizide sonraki bölümlerde otomatik seçilir;
 * (2) (içerik, altyazı) başına elle senkron kaydırması: aynı içerik aynı altyazıyla açılınca uygulanır.
 *
 * Güvenlik zırhı: CursorWindowAllocationException ve SQLiteException koruması mevcuttur.
 * Bellek patlamasında altyazı önbellek tablosu otomatik temizlenir ve çökme engellenir.
 */
class SubtitleMemory(
    private val dao: SubtitleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private suspend fun <T> safe(fallback: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            Log.e("SubtitleMemory", "SubtitleDao işlemi başarısız: ${t.message}", t)
            if (isCursorOrSqlException(t)) {
                runCatching { dao.clearAll() }
            }
            fallback
        }
    }

    private fun isCursorOrSqlException(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            val name = cur.javaClass.name
            if (name.contains("CursorWindow") ||
                cur is android.database.sqlite.SQLiteException ||
                cur is android.database.SQLException
            ) return true
            cur = cur.cause
        }
        return false
    }

    /** Kayıtlı dil: iki harfli kod, [SubtitleSelection.REMEMBERED_OFF] ya da hiç seçilmediyse null. */
    suspend fun language(request: StreamRequest): String? = safe(null) {
        dao.language(languageKey(request))
    }

    /** [language] null ise bir şey saklanmaz (dili bilinmeyen altyazı bir dil tercihi sayılmaz). */
    suspend fun rememberLanguage(request: StreamRequest, language: String?) = safe(Unit) {
        if (language != null) {
            dao.putLanguage(SubtitleLanguageEntity(languageKey(request), language, clock()))
            dao.pruneLanguages(MAX_LANGUAGES)
        }
    }

    suspend fun offset(request: StreamRequest, subtitleUrl: String): Long = safe(0L) {
        dao.offset(offsetKey(request), urlKey(subtitleUrl)) ?: 0L
    }

    /** 0 kaydırma satırı siler; böylece tablo yalnızca gerçekten ayarlanmış değerleri tutar. */
    suspend fun saveOffset(request: StreamRequest, subtitleUrl: String, offsetMs: Long) = safe(Unit) {
        val content = offsetKey(request)
        val subtitle = urlKey(subtitleUrl)
        if (offsetMs == 0L) {
            dao.clearOffset(content, subtitle)
        } else {
            dao.putOffset(SubtitleOffsetEntity(content, subtitle, offsetMs, clock()))
            dao.pruneOffsets(MAX_OFFSETS)
        }
    }

    suspend fun pruneAll() = safe(Unit) {
        dao.pruneOffsets(MAX_OFFSETS)
        dao.pruneLanguages(MAX_LANGUAGES)
    }

    suspend fun clearAll() = safe(Unit) {
        dao.clearAll()
    }

    companion object {
        const val MAX_OFFSETS = 500
        const val MAX_LANGUAGES = 500

        /** Film `movie:603`, dizi `tv:1396` (tüm bölümler için ortak). */
        fun languageKey(request: StreamRequest): String = "${prefix(request.type)}:${request.tmdbId}"

        /** Film `movie:603`, bölüm `tv:1396:1:3`. */
        fun offsetKey(request: StreamRequest): String =
            if (request.type == MediaType.Tv) "${languageKey(request)}:${request.season ?: 0}:${request.episode ?: 0}" else languageKey(request)

        private fun prefix(type: MediaType) = if (type == MediaType.Tv) "tv" else "movie"

        internal fun urlKey(url: String): String =
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
