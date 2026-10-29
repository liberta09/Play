package com.kaan.play.data.sync

import com.kaan.play.data.model.MediaType

/** Çocuk profilinde TMDB film/dizi sertifikalarını değerlendirir. Derecelendirme yoksa güvenli tarafta engeller. */
object AgeRatingPolicy {
    private val allowedLabels = setOf("G", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "PG", "0", "6", "7", "7A", "12")
    private val childMovieGenres = setOf(16, 10751) // Animasyon, Aile
    private val childTvGenres = setOf(16, 10751, 10762) // Animasyon, Aile, Çocuk

    fun allowsChildGenres(type: MediaType, genreIds: List<Int>, adult: Boolean = false): Boolean {
        if (adult) return false
        val allowed = if (type == MediaType.Movie) childMovieGenres else childTvGenres
        return genreIds.any(allowed::contains)
    }

    fun allowsChild(certification: String?): Boolean {
        val value = certification?.trim()?.uppercase()?.replace(" ", "") ?: return false
        if (value in allowedLabels) return true
        val numeric = Regex("(\\d{1,2})").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return numeric != null && numeric <= 12 && !value.contains("18") && !value.contains("16") && !value.contains("15") && !value.contains("13")
    }
}
