package com.kaan.play.data.player

import java.util.Locale

/**
 * Oynatıcı oynatma hızı kuralları ve seçenekleri (Android bağımsız).
 * Kullanıcı kararı 2026-10-25: 0.75×, Normal (1×), 1.25×, 1.5×, 1.75×, 2×.
 * Oturumluktur: yeni video veya sonraki bölümde kalıcı kaydedilmez, 1× başlar.
 */
object PlaybackSpeeds {
    val ALL: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    const val DEFAULT: Float = 1.0f

    /**
     * Hız seçeneğinin panelde gösterilen etiketi (örn. "0.75×", "Normal (1×)", "1.5×", "2×").
     */
    fun format(speed: Float, normalLabel: String = "Normal"): String {
        return if (speed == DEFAULT) {
            "$normalLabel (1×)"
        } else {
            formatMultiplier(speed)
        }
    }

    /**
     * Üst çubukta rozet olarak gösterilecek kısa etiket:
     * 1.0f için null (rozet gösterilmez), 1.5f için "1.5×", 2.0f için "2×".
     */
    fun badgeLabel(speed: Float): String? {
        if (speed == DEFAULT) return null
        return formatMultiplier(speed)
    }

    /** Sayıyı "1.5×", "2×", "0.75×" biçiminde çarpan sembolüyle döndürür. */
    fun formatMultiplier(speed: Float): String {
        return if (speed % 1.0f == 0f) {
            "${speed.toInt()}×"
        } else {
            String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"
        }
    }
}
