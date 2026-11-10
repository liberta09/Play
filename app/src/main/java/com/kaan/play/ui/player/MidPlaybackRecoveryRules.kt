package com.kaan.play.ui.player

import com.kaan.play.data.stream.PlayableStream
import java.util.Locale

/**
 * Film/dizi oynarken (SourcePhase.Playing) kaynak kopması veya ölümcül hata durumunda
 * kurtarma ve sıradaki kaynağa geçiş kurallarını yöneten saf karar sınıfı.
 */
object MidPlaybackRecoveryRules {
    const val MAX_SWITCHES = 3

    private val NON_NETWORK_MARKERS = listOf(
        "DECODER",
        "DECODING",
        "PARSING",
        "DRM",
        "AUDIO_TRACK",
        "UNSUPPORTED",
        "FORMAT",
        "SIZE-TOO-SMALL",
        "MISMATCH",
    )

    fun isNetworkError(detail: String): Boolean {
        val upper = detail.trim().uppercase(Locale.ROOT)
        if (upper.isEmpty()) return true
        return NON_NETWORK_MARKERS.none { upper.contains(it) }
    }

    sealed interface Decision {
        /** Aynı kaynağı kaldığı saniyeden bir kez yeniden dene. */
        data class RetrySame(val stream: PlayableStream) : Decision

        /** Sıradaki uygun yedek kaynağa geç. */
        data class Switch(val candidate: PlayableStream, val notice: NoticeType) : Decision

        /** Geçiş sınırı doldu ya da aday kalmadı; hata ekranı göster. */
        data object Fail : Decision
    }

    enum class NoticeType {
        STREAM_DROPPED_TRYING_NEXT, // "Kaynak koptu, sıradaki deneniyor"
        SOURCE_SWITCHED,            // "Kaynak değiştirildi"
    }

    /**
     * @param phase Mevcut SourcePhase (yalnızca Playing aşamasında kurtarma yapılır)
     * @param currentStream Şu an oynatılan kaynak
     * @param hasRetriedCurrentStream Mevcut kaynak bu oturumda daha önce bir kez yeniden denendi mi
     * @param switchCount Şu ana kadar yapılan oynama-esnası otomatik geçiş sayısı (en çok MAX_SWITCHES)
     * @param candidates Sıralanmış ve kabul edilebilir yedek kaynak listesi
     * @param isManual Kullanıcı bu kaynağı elle mi seçti
     * @param isOffline İndirilmiş (çevrimdışı) dosya mı oynatılıyor
     * @param isLeaving Kullanıcı oynatıcıdan çıkıyor mu
     * @param isLive Canlı TV yayınında konumdan yeniden deneme yapılmaz, doğrudan yedek/hata akışına geçilir
     * @param isNetworkError Yalnızca ağ/bağlantı kopmasında aynı kaynak bir kez yeniden denenir
     */
    fun evaluate(
        phase: SourcePhase,
        currentStream: PlayableStream?,
        hasRetriedCurrentStream: Boolean,
        switchCount: Int,
        candidates: List<PlayableStream>,
        isManual: Boolean = false,
        isOffline: Boolean = false,
        isLeaving: Boolean = false,
        isLive: Boolean = false,
        isNetworkError: Boolean = true,
    ): Decision {
        if (isLeaving || isOffline || currentStream == null) return Decision.Fail
        if (phase != SourcePhase.Playing) return Decision.Fail
        if (switchCount >= MAX_SWITCHES) return Decision.Fail

        if (!isLive && isNetworkError && !hasRetriedCurrentStream) {
            return Decision.RetrySame(currentStream)
        }

        val next = candidates.firstOrNull() ?: return Decision.Fail
        val notice = if (isManual) NoticeType.SOURCE_SWITCHED else NoticeType.STREAM_DROPPED_TRYING_NEXT
        return Decision.Switch(next, notice)
    }
}
