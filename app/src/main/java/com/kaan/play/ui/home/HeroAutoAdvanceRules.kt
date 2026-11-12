package com.kaan.play.ui.home

/**
 * Kapak karuselinin otomatik geçiş ve zamanlama kuralları (Android'den bağımsız saf Kotlin).
 */
object HeroAutoAdvanceRules {
    /** Fragmansız kapağın ekranda kalma süresi (ms). */
    const val NO_TRAILER_DURATION_MS: Long = 6_000L

    /** Fragmanlı kapağın ekranda kalacağı azami süre (ms). */
    const val WITH_TRAILER_MAX_DURATION_MS: Long = 14_000L

    /** Sayfa ekranda durduktan sonra fragmanın başlatılma gecikmesi (ms). */
    const val TRAILER_START_DELAY_MS: Long = 2_000L

    /** Fragman yükleme / oynatma zaman aşımı (ms) (PLAYING gelmezse iptal edilir). */
    const val TRAILER_PLAY_TIMEOUT_MS: Long = 8_000L

    /** Odak veya sürükleme bırakıldıktan sonra geçişin devam etme süresi (ms). */
    const val RESUME_DELAY_MS: Long = 6_000L

    /**
     * Karuselin otomatik geçiş yapıp yapamayacağını denetler.
     * Kumandada odak düğmedeyken (veya karuselde odak varken) ASLA kendiliğinden kaymaz.
     * Kullanıcı sürüklerken, ekran RESUMED değilken, ayar kapalıyken veya performans modunda geçiş durur.
     */
    fun shouldAutoAdvance(
        hasUserFocus: Boolean,
        isScrollInProgress: Boolean,
        isResumed: Boolean,
        autoAdvanceSetting: Boolean,
        perfPolicyEnabled: Boolean,
    ): Boolean {
        if (!autoAdvanceSetting) return false
        if (!perfPolicyEnabled) return false
        if (!isResumed) return false
        if (hasUserFocus) return false
        if (isScrollInProgress) return false
        return true
    }

    /**
     * Verilen mevcut sayfa ve toplam sayfa sayısına göre sıradaki sayfayı hesaplar.
     * Sondan sonra başa (0) döner.
     */
    fun nextPageIndex(currentPage: Int, totalPages: Int): Int {
        if (totalPages <= 1) return 0
        return (currentPage + 1) % totalPages
    }

    /**
     * Fragman durumuna göre bekleme süresini verir.
     */
    fun pageDurationMs(hasTrailer: Boolean): Long =
        if (hasTrailer) WITH_TRAILER_MAX_DURATION_MS else NO_TRAILER_DURATION_MS
}
