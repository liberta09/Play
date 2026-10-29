package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType

/**
 * Sahte ya da bozuk çok küçük kaynakları (ör. 1080p 1 MB container desteklenmiyor/3003 hatası verenler)
 * boyut eşiğine göre filtreler (Android'den bağımsız, birim testli).
 *
 * Kurallar:
 * - Boyut biliniyorsa (> 0):
 *   - Film için [MIN_MOVIE_BYTES] (20 MB) altındaysa kaynak listeye girmez (elenir, false döner).
 *   - Dizi bölümü için [MIN_EPISODE_BYTES] (5 MB) altındaysa kaynak listeye girmez (elenir, false döner).
 * - Boyut bilinmiyorsa (null ya da <= 0): ELENMEZ (true döner).
 */
object SourceMediaRules {

    /** Film için minimum dosya boyutu (20 MB = 20_000_000 bayt). */
    const val MIN_MOVIE_BYTES = 20_000_000L

    /** Dizi bölümü için minimum dosya boyutu (5 MB = 5_000_000 bayt). */
    const val MIN_EPISODE_BYTES = 5_000_000L

    /**
     * [sizeBytes] biliniyorsa eşikten küçük mü kontrol eder.
     * Bilinmiyorsa (null veya <= 0) true döner (elenmez).
     */
    fun isAcceptableSize(sizeBytes: Long?, isMovie: Boolean): Boolean {
        if (sizeBytes == null || sizeBytes <= 0) return true
        val min = if (isMovie) MIN_MOVIE_BYTES else MIN_EPISODE_BYTES
        return sizeBytes >= min
    }

    fun isAcceptableSize(sizeBytes: Long?, mediaType: MediaType): Boolean =
        isAcceptableSize(sizeBytes, mediaType == MediaType.Movie)

    fun isAcceptable(stream: PlayableStream, isMovie: Boolean): Boolean =
        isAcceptableSize(stream.sizeBytes, isMovie)

    fun isAcceptable(stream: PlayableStream, mediaType: MediaType): Boolean =
        isAcceptableSize(stream.sizeBytes, mediaType == MediaType.Movie)
}
