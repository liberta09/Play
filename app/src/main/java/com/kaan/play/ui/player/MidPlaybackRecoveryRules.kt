package com.kaan.play.ui.player

import com.kaan.play.data.stream.PlayableStream

/**
 * Film/dizi oynarken (SourcePhase.Playing) kaynak kopması veya ölümcül hata durumunda
 * kurtarma ve sıradaki kaynağa geçiş kurallarını yöneten saf karar sınıfı.
 */
object MidPlaybackRecoveryRules {
    const val MAX_SWITCHES = 3

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
    ): Decision {
        if (isLeaving || isOffline || currentStream == null) return Decision.Fail
        if (phase != SourcePhase.Playing) return Decision.Fail
        if (switchCount >= MAX_SWITCHES) return Decision.Fail

        if (!hasRetriedCurrentStream) {
            return Decision.RetrySame(currentStream)
        }

        val next = candidates.firstOrNull() ?: return Decision.Fail
        val notice = if (isManual) NoticeType.SOURCE_SWITCHED else NoticeType.STREAM_DROPPED_TRYING_NEXT
        return Decision.Switch(next, notice)
    }
}
