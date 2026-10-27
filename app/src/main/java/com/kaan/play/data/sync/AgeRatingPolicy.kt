package com.kaan.play.data.sync

import com.kaan.play.data.model.MediaType
import java.util.Locale

object AgeRatingPolicy {
    private val allowedLabels: Set<String> = setOf(
        "G", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "PG", "0", "6", "7", "7A", "12"
    )
    private val childMovieGenres: Set<Int> = setOf(16, 10751)
    private val childTvGenres: Set<Int> = setOf(16, 10751, 10762)

    fun allowsChild(label: String?): Boolean {
        if (label == null) return false
        val clean = label.trim().uppercase(Locale.ROOT).replace(" ", "")
        if (clean.isEmpty()) return false
        if (clean in allowedLabels) return true
        val match = Regex("(\\d{1,2})").find(clean)
        val num = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (num != null && num <= 12 && !clean.contains("18") && !clean.contains("16") && !clean.contains("13")) {
            return true
        }
        if (clean.contains("GENEL") || clean.contains("FAMILY") || clean.contains("AILE")) {
            return true
        }
        return false
    }

    fun allowsChildGenres(type: MediaType, genreIds: List<Int>, adult: Boolean = false): Boolean {
        if (adult) return false
        val allowed = if (type == MediaType.Movie) childMovieGenres else childTvGenres
        return genreIds.any { it in allowed }
    }
}
