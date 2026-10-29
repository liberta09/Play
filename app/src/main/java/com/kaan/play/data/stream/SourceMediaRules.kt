package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType

/**
 * Sahte ya da bozuk çok küçük kaynakları (ör. 1080p 1 MB container desteklenmiyor/3003 hatası verenler)
 * boyut eşiğine göre filtreler (Android'den bağımsız, birim testli).
 *
 * Kurallar:
 * - Canlı yayınlar, HLS / DASH manifestleri (m3u8, mpd, master.txt vb.) veya boyutu bilinmeyenler (null ya da <= 0): ELENMEZ (true döner).
 * - Düz video dosyasının boyutu biliniyorsa (> 0):
 *   - Film için [MIN_MOVIE_BYTES] (20 MB) altındaysa kaynak listeye girmez (elenir, false döner).
 *   - Dizi bölümü için [MIN_EPISODE_BYTES] (5 MB) altındaysa kaynak listeye girmez (elenir, false döner).
 */
object SourceMediaRules {

    /** Film için minimum dosya boyutu (20 MB = 20_000_000 bayt). */
    const val MIN_MOVIE_BYTES = 20_000_000L

    /** Dizi bölümü için minimum dosya boyutu (5 MB = 5_000_000 bayt). */
    const val MIN_EPISODE_BYTES = 5_000_000L

    /**
     * Akış adresinin ya da içerik türünün parça listesi / canlı manifest (HLS, DASH vb.) olup olmadığını belirler.
     * Bu dosyalar oynatma listesi olduğundan boyutları birkaç yüz bayt olabilir ve boyut filtresiyle elenmemelidir.
     */
    fun isStreamingManifest(url: String?, mimeType: String? = null): Boolean {
        if (url != null) {
            val lower = url.lowercase(java.util.Locale.ROOT)
            if (lower.contains(".m3u8") ||
                lower.contains("/hls/") ||
                lower.contains(".mpd") ||
                lower.contains("/txt/") ||
                lower.endsWith(".txt") ||
                lower.contains("molystream") ||
                lower.contains("vidmoly")
            ) {
                return true
            }
        }
        if (mimeType != null) {
            val lower = mimeType.lowercase(java.util.Locale.ROOT)
            if (lower.contains("mpegurl") || lower.contains("dash") || lower.contains("mpd")) {
                return true
            }
        }
        return false
    }

    /**
     * [sizeBytes] biliniyorsa eşikten küçük mü kontrol eder.
     * Manifestler ve boyutu bilinmeyenler (null veya <= 0) için true döner (elenmez).
     */
    fun isAcceptableSize(
        sizeBytes: Long?,
        isMovie: Boolean,
        url: String? = null,
        mimeType: String? = null,
    ): Boolean {
        if (isStreamingManifest(url, mimeType)) return true
        if (sizeBytes == null || sizeBytes <= 0) return true
        val min = if (isMovie) MIN_MOVIE_BYTES else MIN_EPISODE_BYTES
        return sizeBytes >= min
    }

    fun isAcceptableSize(
        sizeBytes: Long?,
        mediaType: MediaType,
        url: String? = null,
        mimeType: String? = null,
    ): Boolean = isAcceptableSize(sizeBytes, mediaType == MediaType.Movie, url, mimeType)

    fun isAcceptable(stream: PlayableStream, isMovie: Boolean): Boolean =
        isAcceptableSize(stream.sizeBytes, isMovie, stream.url, stream.mimeType)

    fun isAcceptable(stream: PlayableStream, mediaType: MediaType): Boolean =
        isAcceptableSize(stream.sizeBytes, mediaType == MediaType.Movie, stream.url, stream.mimeType)
}
