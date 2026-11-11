package com.kaan.play.ui.home

/**
 * Sessiz fragman oynatma koşullarının ve zamanlamasının kuralları (Android'den bağımsız saf Kotlin).
 */
object HeroTrailerRules {
    const val SEEK_START_THRESHOLD_SECONDS: Long = 40L
    const val SEEK_START_MS: Long = 8_000L
    const val MAX_PLAY_DURATION_MS: Long = 12_000L
    const val FIRST_FRAME_TIMEOUT_MS: Long = 5_000L
    const val RESOLVE_TIMEOUT_MS: Long = 8_000L
    const val TRANSITION_CROSSFADE_MS: Int = 400

    /**
     * Fragman oynatılıp oynatılamayacağını denetler.
     * Koşulların hepsi sağlanmalıdır:
     * - Kullanıcı ayarı açık (hero_trailer_enabled)
     * - Kapak otomatik geçişi açık (hero_auto_advance)
     * - Performans modu kapalı (heroAutoAdvanceEnabled == true)
     * - Cihaz düşük bellekli değil (isLowRamDevice == false)
     * - Ağ Wi-Fi veya Ethernet (mobil veride ASLA)
     */
    fun canPlayTrailer(
        trailerSettingEnabled: Boolean,
        heroAutoAdvanceSetting: Boolean,
        perfPolicyHeroEnabled: Boolean,
        isLowRamDevice: Boolean,
        isWifiOrEthernet: Boolean,
    ): Boolean {
        if (!trailerSettingEnabled) return false
        if (!heroAutoAdvanceSetting) return false
        if (!perfPolicyHeroEnabled) return false
        if (isLowRamDevice) return false
        if (!isWifiOrEthernet) return false
        return true
    }

    /**
     * Fragman 40 sn'den uzunsa 8. saniyeden başla, aksi halde baştan (0).
     */
    fun startPositionMs(durationSeconds: Long): Long =
        if (durationSeconds > SEEK_START_THRESHOLD_SECONDS) SEEK_START_MS else 0L

    /**
     * Yeni video 5 sn içinde ilk kareyi çizmezse o kapak atlanır.
     */
    fun shouldSkipCover(hasRenderedFirstFrame: Boolean, elapsedSinceLoadMs: Long): Boolean =
        !hasRenderedFirstFrame && elapsedSinceLoadMs >= FIRST_FRAME_TIMEOUT_MS

    /**
     * Sıradaki adayı döngüsel olarak seçer.
     */
    fun nextCandidateIndex(currentIndex: Int, totalItems: Int): Int {
        if (totalItems <= 1) return 0
        return (currentIndex + 1) % totalItems
    }
}
