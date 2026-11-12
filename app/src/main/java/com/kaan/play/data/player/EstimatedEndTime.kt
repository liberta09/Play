package com.kaan.play.data.player

import kotlin.math.roundToLong

/** Oynatılan konum, toplam süre ve oynatma hızına göre tahmini bitiş zamanını hesaplar (Android'den bağımsız). */
object EstimatedEndTime {

    /**
     * [nowEpochMs] anındaki duvar saatine göre tahmini bitiş epoch milisaniyesini döndürür.
     * Canlı yayında, süresi bilinmeyen içerikte veya geçersiz hızda null döner.
     */
    fun calculateEndEpochMs(
        nowEpochMs: Long,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        isLive: Boolean,
    ): Long? {
        if (isLive || durationMs <= 0L || speed <= 0f) return null
        val clampedPos = positionMs.coerceIn(0L, durationMs)
        val remainingVideoMs = durationMs - clampedPos
        val remainingRealMs = (remainingVideoMs / speed.toDouble()).roundToLong().coerceAtLeast(0L)
        return nowEpochMs + remainingRealMs
    }
}
