package com.kaan.play.data.player

/**
 * Otomatik kaynak deneme sayısı sınırı kuralları.
 * [UNLIMITED] (`0`) veya `null` değerleri sınırsız deneme anlamına gelir.
 */
object AutoRetryPolicy {
    const val UNLIMITED = 0
    val OPTIONS = listOf(3, 5, 10, UNLIMITED)

    fun normalize(value: Int?): Int =
        if (value != null && value in OPTIONS) value else UNLIMITED

    fun limitOrNull(stored: Int): Int? =
        stored.takeIf { it > 0 && it in OPTIONS }

    fun hasReachedLimit(attemptsMade: Int, limit: Int?): Boolean {
        if (limit == null || limit <= 0) return false
        return attemptsMade >= limit
    }

    fun canTryAnother(attemptsMade: Int, limit: Int?): Boolean =
        !hasReachedLimit(attemptsMade, limit)
}
