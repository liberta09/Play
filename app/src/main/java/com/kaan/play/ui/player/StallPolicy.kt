package com.kaan.play.ui.player

import com.kaan.play.data.stream.PlayableStream

/**
 * Geçmişteki tamamlanmış bir takılma aralığı ([startMs], [endMs]).
 */
data class StallInterval(val startMs: Long, val endMs: Long)

/**
 * Takılma analizi sonucu verilen karar.
 */
sealed interface StallDecision {
    /** Hiçbir eylem yapılmaz, oynatma olduğu gibi sürer. */
    data object None : StallDecision

    /** Kullanıcının elle seçtiği kaynak yavaşladı; otomatik değiştirilmez, arayüze bildirim verilir. */
    data object NotifyManualSlow : StallDecision

    /** Yavaş kaynak terk edilip [candidate] kaynağına geçilir. */
    data class Switch(val candidate: PlayableStream, val reason: SwitchReason) : StallDecision
}

/**
 * Geçişin tetiklenme nedeni (günlükte: "neden=tek|toplam|altyazılı").
 */
enum class SwitchReason(val logValue: String) {
    /** Tek bir kesintisiz takılma 10 sn'yi geçti. */
    Single("tek"),

    /** Son 2 dakika içindeki toplam takılma 15 sn'yi geçti. */
    Window("toplam"),

    /** Dublajlı aday kalmadı, kesintisiz takılma 20 sn'yi geçti ve altyazılı adaya geçildi. */
    Subtitled("altyazılı"),
}

/**
 * Takılan kaynaktan otomatik olarak başka kaynağa geçiş mantığı (Android'den bağımsız, saf kurallar).
 *
 * Kurallar:
 * 1. Karar Switch verilir eğer:
 *    (a) tek bir takılma kesintisiz 10 sn'yi geçerse (STALL_SINGLE_MS), YA DA
 *    (b) son 2 dakika (STALL_WINDOW_MS) içindeki toplam takılma 15 sn'yi geçerse (STALL_WINDOW_TOTAL_MS).
 * 2. Sarma (seek) kaynaklı arabelleğe alma sayılmaz.
 * 3. İlk kareden önce karar verilmez (!firstFrameRendered).
 * 4. Aday seçimi:
 *    - Mevcut kaynak dublajlıysa: önce başka DUBLAJLI aday;
 *    - Dublajlı yoksa: YALNIZCA takılma kesintisiz 20 sn'yi (STALL_SUBTITLED_MS) geçtiyse altyazılı aday.
 *      (20 sn dolmadıysa veya altyazılı aday da yoksa beklenir, karar verilmez).
 *    - Mevcut kaynak altyazılıysa: sıradaki herhangi bir aday.
 *    - Aday yoksa karar yok (None), oynatma olduğu gibi sürer (hata/siyah ekran çıkarılmaz).
 * 5. Kullanıcının ELLE seçtiği kaynakta otomatik geçiş yapılmaz; NotifyManualSlow döner.
 */
object StallPolicy {
    const val STALL_SINGLE_MS = 10_000L
    const val STALL_WINDOW_MS = 120_000L
    const val STALL_WINDOW_TOTAL_MS = 15_000L
    const val STALL_SUBTITLED_MS = 20_000L

    fun evaluate(
        nowMs: Long,
        firstFrameRendered: Boolean,
        isSeeking: Boolean,
        currentStallStartMs: Long?,
        pastStalls: List<StallInterval>,
        currentIsDubbed: Boolean,
        isManual: Boolean,
        candidates: List<PlayableStream>,
    ): StallDecision {
        // İlk kareden önce veya kullanıcı sararken takılma kararı verilmez
        if (!firstFrameRendered || isSeeking) return StallDecision.None

        // Şu anda takılma sürmüyorsa geçiş kararı verilmez
        if (currentStallStartMs == null) return StallDecision.None

        val currentStallDuration = (nowMs - currentStallStartMs).coerceAtLeast(0L)

        // Son 2 dakika (STALL_WINDOW_MS) içindeki takılmaları topla
        val windowStart = nowMs - STALL_WINDOW_MS
        val pastSum = pastStalls.sumOf { interval ->
            val s = maxOf(interval.startMs, windowStart)
            val e = minOf(interval.endMs, nowMs)
            if (e > s) e - s else 0L
        }
        val ongoingStart = maxOf(currentStallStartMs, windowStart)
        val ongoingSum = if (nowMs > ongoingStart) nowMs - ongoingStart else 0L
        val windowStallMs = pastSum + ongoingSum

        val singleStall = currentStallDuration >= STALL_SINGLE_MS
        val windowStall = windowStallMs >= STALL_WINDOW_TOTAL_MS

        if (!singleStall && !windowStall) return StallDecision.None

        // Kullanıcı elle seçtiyse otomatik değiştirilmez
        if (isManual) return StallDecision.NotifyManualSlow

        // Aday seçimi
        return if (currentIsDubbed) {
            val dubbedCandidate = candidates.firstOrNull { it.dubbed }
            if (dubbedCandidate != null) {
                val reason = if (singleStall) SwitchReason.Single else SwitchReason.Window
                StallDecision.Switch(dubbedCandidate, reason)
            } else if (currentStallDuration >= STALL_SUBTITLED_MS) {
                val subCandidate = candidates.firstOrNull { !it.dubbed }
                if (subCandidate != null) {
                    StallDecision.Switch(subCandidate, SwitchReason.Subtitled)
                } else {
                    StallDecision.None
                }
            } else {
                StallDecision.None
            }
        } else {
            val candidate = candidates.firstOrNull()
            if (candidate != null) {
                val reason = if (singleStall) SwitchReason.Single else SwitchReason.Window
                StallDecision.Switch(candidate, reason)
            } else {
                StallDecision.None
            }
        }
    }
}
