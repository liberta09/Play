package com.kaan.play.data.settings

/**
 * Performans modu politikası (Android bağımsız).
 * Kısıtlı donanımlarda ana sayfa geçişleri, odak animasyonları, afiş boyutu, raf paralelliği
 * ve gölge/bulanıklık efektleri sınırlanır; oynatma kalitesi ve kaynak seçimi DEĞİŞMEZ.
 */
data class PerformancePolicy(
    val enabled: Boolean,
    val heroAutoAdvanceEnabled: Boolean,
    val focusScaleAnimationEnabled: Boolean,
    val focusGlowEnabled: Boolean,
    val useSmallerPosters: Boolean,
    val maxConcurrentProviderLoads: Int,
    val providerShelfBatchSize: Int,
    val shadowsAndEffectsEnabled: Boolean,
) {
    companion object {
        val Standard = PerformancePolicy(
            enabled = false,
            heroAutoAdvanceEnabled = true,
            focusScaleAnimationEnabled = true,
            focusGlowEnabled = true,
            useSmallerPosters = false,
            maxConcurrentProviderLoads = 3,
            providerShelfBatchSize = Int.MAX_VALUE,
            shadowsAndEffectsEnabled = true,
        )

        val Reduced = PerformancePolicy(
            enabled = true,
            heroAutoAdvanceEnabled = false,
            focusScaleAnimationEnabled = false,
            focusGlowEnabled = false,
            useSmallerPosters = true,
            maxConcurrentProviderLoads = 1,
            providerShelfBatchSize = 4,
            shadowsAndEffectsEnabled = false,
        )

        fun forMode(enabled: Boolean): PerformancePolicy = if (enabled) Reduced else Standard
    }
}

/** Sağlayıcı raflarını küçük gruplar hâlinde açma kuralları (Android bağımsız). */
object PerformanceBatchRules {
    fun visibleCount(totalCount: Int, completedLoads: Int, batchSize: Int): Int {
        if (totalCount <= 0) return 0
        if (batchSize == Int.MAX_VALUE || batchSize <= 0) return totalCount
        val unlockedBatches = (completedLoads.coerceAtLeast(0) / batchSize) + 1
        return minOf(totalCount, unlockedBatches * batchSize)
    }
}
