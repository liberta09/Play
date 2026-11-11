package com.kaan.play.ui.home

/**
 * Sessiz fragman oynatma koşullarının kuralları (Android'den bağımsız saf Kotlin).
 */
object HeroTrailerRules {

    /**
     * Fragman oynatılıp oynatılamayacağını denetler.
     * Koşulların hepsi sağlanmalıdır:
     * - Kullanıcı ayarı açık
     * - Kapak otomatik geçişi açık
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
}
