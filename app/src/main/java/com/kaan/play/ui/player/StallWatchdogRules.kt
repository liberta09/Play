package com.kaan.play.ui.player

/**
 * TV kutuları ve oynatıcıda sarma/yükleme takılmalarını izleyen watchdog karar mantığı (Android'den bağımsız).
 *
 * Kurallar:
 * - Tampon pozisyonu veya oynatma konumu ilerliyorsa (ağ yavaş olsa dahi veri akıyorsa): BEKLE.
 * - Geçen süre 8 saniyeden azsa: BEKLE.
 * - Son 8 saniyede hem tampon hem oynatma konumu HİÇ ilerlemediyse (veri akmıyorsa / donmuşsa) ve
 *   deneme hakkı varsa (deneme sayısı < 1): YENİDEN_HAZIRLA.
 * - Yükleme/sarma başına en çok 1 yeniden hazırlama yapılır. Deneme hakkı bittiğinde: VAZGEÇ.
 */
object StallWatchdogRules {
    const val TIMEOUT_MS = 8_000L
    const val MAX_REPREPARE_ATTEMPTS = 1

    enum class Decision {
        BEKLE,
        YENIDEN_HAZIRLA,
        VAZGEC,
    }

    /**
     * @param initialBufferedPositionMs İzleme başlangıcındaki tamponlanmış konum (ms)
     * @param currentBufferedPositionMs Geçerli tamponlanmış konum (ms)
     * @param initialPositionMs İzleme başlangıcındaki oynatma konumu (ms)
     * @param currentPositionMs Geçerli oynatma konumu (ms)
     * @param elapsedMs Başlangıçtan bu yana geçen süre (ms)
     * @param reprepareCount Bu sarma/yükleme oturumunda yapılmış yeniden hazırlama sayısı
     * @return [Decision] (BEKLE, YENİDEN_HAZIRLA veya VAZGEÇ)
     */
    fun decide(
        initialBufferedPositionMs: Long,
        currentBufferedPositionMs: Long,
        initialPositionMs: Long,
        currentPositionMs: Long,
        elapsedMs: Long,
        reprepareCount: Int,
    ): Decision {
        if (reprepareCount >= MAX_REPREPARE_ATTEMPTS) {
            return Decision.VAZGEC
        }
        if (elapsedMs < TIMEOUT_MS) {
            return Decision.BEKLE
        }
        // Tampon veya pozisyon ilerliyorsa (ağ yavaş olsa da veri akıyor): DOKUNMA, bekle
        val bufferProgressed = currentBufferedPositionMs > initialBufferedPositionMs
        val positionProgressed = currentPositionMs > initialPositionMs
        if (bufferProgressed || positionProgressed) {
            return Decision.BEKLE
        }
        // Veri hiç akmıyor ve süre eşiği aşıldı: 1 kez yeniden hazırla
        return Decision.YENIDEN_HAZIRLA
    }
}
