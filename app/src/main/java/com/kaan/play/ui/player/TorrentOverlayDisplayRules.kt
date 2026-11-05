package com.kaan.play.ui.player

/**
 * Torrent yükleme ekranının ne zaman gösterilip ne zaman gösterilmeyeceğini belirleyen saf kural sınıfı (Android bağımsız).
 *
 * Kurallar:
 * 1. Doğrudan (direct/CloudStream/Nuvio) kaynaklarda ÇIKMAZ.
 * 2. Oynatıcı ilk açıldığında torrent kaynağı hazırlanıyorsa ÇIKAR.
 * 3. Farklı bir torrent kaynağına geçiş yapılıyorsa (kullanıcı seçimi veya otomatik yedek) ÇIKAR.
 * 4. İlk video karesi ekrana çizildikten sonra ÇIKMAZ (ekran kaybolur); ancak çıkış animasyonu (dolum tamamlama + 300ms fade-out) sürerken görünür kalır.
 * 5. Aynı kaynakta ileri/geri sarma yapılırken ÇIKMAZ.
 * 6. Oynatma sırasında geçici tamponlama durumunda ÇIKMAZ.
 * 7. Aynı akışın MidPlaybackRecovery ile yeniden hazırlanması durumunda ÇIKMAZ.
 */
object TorrentOverlayDisplayRules {

    enum class PlaybackAction {
        InitialOpen,
        SwitchSource,
        RetrySameStream,
        Seek,
        BufferingDuringPlayback,
    }

    /**
     * Torrent yükleme ekranının durum geçişlerini deterministik ve saf olarak yöneten durum modeli.
     */
    data class TorrentOverlayState(
        val currentStreamUrl: String? = null,
        val isTorrent: Boolean = false,
        val firstFrameRendered: Boolean = false,
        val isDismissing: Boolean = false,
        val dismissed: Boolean = false,
        val fallback: Boolean = false,
    ) {
        /**
         * Yeni veya mevcut bir akış seçildiğinde durumu günceller.
         */
        fun onStreamSelected(url: String?, isTorrent: Boolean): TorrentOverlayState {
            if (url == null) return TorrentOverlayState()
            if (url == currentStreamUrl) {
                // Aynı akış: seek, retry veya tamponlama; mevcut durumu koru
                return this
            }
            return TorrentOverlayState(
                currentStreamUrl = url,
                isTorrent = isTorrent,
                firstFrameRendered = false,
                isDismissing = false,
                dismissed = false,
                fallback = false,
            )
        }

        /**
         * Oynatıcı ilk video karesini çizdiğinde çağrılır.
         */
        fun onFirstFrameRendered(): TorrentOverlayState {
            if (!isTorrent || dismissed) return this
            return copy(firstFrameRendered = true, isDismissing = true)
        }

        /**
         * Çıkış animasyonu (dolum tamamlama + 300 ms fade-out) bittiğinde çağrılır.
         */
        fun onDismissFinished(): TorrentOverlayState {
            return copy(isDismissing = false, dismissed = true)
        }

        /**
         * Yükleme ekranı kritik hata aldığında sessizce standart yükleyiciye düşer.
         */
        fun onFallback(): TorrentOverlayState {
            return copy(fallback = true)
        }

        /**
         * Ekranın o an gösterilip gösterilmeyeceğini belirler.
         */
        fun shouldShow(
            isSeeking: Boolean = false,
            isBuffering: Boolean = false,
            hasError: Boolean = false,
        ): Boolean {
            if (hasError || fallback || !isTorrent || dismissed) return false
            if (isSeeking) return false
            if (isBuffering && dismissed) return false
            if (firstFrameRendered && !isDismissing) return false
            return true
        }
    }

    /**
     * @param isTorrent Kaynağın torrent (magnet/infoHash, TorrentEngine) olup olmadığı
     * @param action Oynatıcıda o an gerçekleşen aksiyon
     * @param firstFrameRendered Yeni kaynağın ilk karesi ekrana çizildi mi
     * @param isDismissing İlk kare sonrası çıkış animasyonu (dolum tamamlama + 300ms fade-out) sürüyor mu
     * @return true ise özel torrent yükleme ekranı gösterilir
     */
    fun shouldShow(
        isTorrent: Boolean,
        action: PlaybackAction,
        firstFrameRendered: Boolean,
        isDismissing: Boolean = false,
    ): Boolean {
        if (!isTorrent) return false
        if (firstFrameRendered && !isDismissing) return false
        return when (action) {
            PlaybackAction.InitialOpen -> true
            PlaybackAction.SwitchSource -> true
            PlaybackAction.RetrySameStream -> false
            PlaybackAction.Seek -> false
            PlaybackAction.BufferingDuringPlayback -> false
        }
    }

    /**
     * Akış URL'lerine göre yeni/farklı bir akış başlatılıp başlatılmadığını belirler.
     *
     * @param currentStreamUrl Oynatılmak istenen akışın URL'si
     * @param lastRenderedStreamUrl En son ilk karesi çizilmiş akışın URL'si (henüz hiç çizilmediyse null)
     * @return true ise ilk açılış veya farklı bir kaynağa geçiş söz konusudur
     */
    fun isNewStream(currentStreamUrl: String?, lastRenderedStreamUrl: String?): Boolean {
        if (currentStreamUrl == null) return false
        if (lastRenderedStreamUrl == null) return true
        return currentStreamUrl != lastRenderedStreamUrl
    }

    /**
     * Mevcut durum değişkenlerine göre yükleme ekranının gösterilme durumunu hesaplar.
     */
    fun evaluate(
        isTorrent: Boolean,
        isNewStream: Boolean,
        firstFrameRendered: Boolean,
        isDismissing: Boolean = false,
        isSeeking: Boolean = false,
        isSameStreamRetry: Boolean = false,
        isBufferingDuringPlayback: Boolean = false,
    ): Boolean {
        if (!isTorrent) return false
        if (firstFrameRendered && !isDismissing) return false
        if (isSeeking) return false
        if (isSameStreamRetry) return false
        if (isBufferingDuringPlayback) return false
        return isNewStream
    }
}
