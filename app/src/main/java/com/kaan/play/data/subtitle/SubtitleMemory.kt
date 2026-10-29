package com.kaan.play.data.subtitle

import com.kaan.play.data.db.SubtitleDao
import com.kaan.play.data.db.SubtitleLanguageEntity
import com.kaan.play.data.db.SubtitleOffsetEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import java.security.MessageDigest

/**
 * Altyazıyla ilgili kalıcı hafıza (Room): (1) bir film/dizide seçilen dil: dizide sonraki bölümlerde otomatik seçilir;
 * (2) (içerik, altyazı) başına elle senkron kaydırması: aynı içerik aynı altyazıyla açılınca uygulanır.
 */
class SubtitleMemory(
    private val dao: SubtitleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Kayıtlı dil: iki harfli kod, [SubtitleSelection.REMEMBERED_OFF] ya da hiç seçilmediyse null. */
    suspend fun language(request: StreamRequest): String? = dao.language(languageKey(request))

    /** [language] null ise bir şey saklanmaz (dili bilinmeyen altyazı bir dil tercihi sayılmaz). */
    suspend fun rememberLanguage(request: StreamRequest, language: String?) {
        if (language == null) return
        dao.putLanguage(SubtitleLanguageEntity(languageKey(request), language, clock()))
    }

    suspend fun offset(request: StreamRequest, subtitleUrl: String): Long =
        dao.offset(offsetKey(request), urlKey(subtitleUrl)) ?: 0L

    /** 0 kaydırma satırı siler; böylece tablo yalnızca gerçekten ayarlanmış değerleri tutar. */
    suspend fun saveOffset(request: StreamRequest, subtitleUrl: String, offsetMs: Long) {
        val content = offsetKey(request)
        val subtitle = urlKey(subtitleUrl)
        if (offsetMs == 0L) {
            dao.clearOffset(content, subtitle)
        } else {
            dao.putOffset(SubtitleOffsetEntity(content, subtitle, offsetMs, clock()))
            dao.pruneOffsets(MAX_OFFSETS)
        }
    }

    companion object {
        const val MAX_OFFSETS = 1000

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
