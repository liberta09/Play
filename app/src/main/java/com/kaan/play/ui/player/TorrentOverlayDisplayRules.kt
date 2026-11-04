package com.kaan.play.ui.player

/**
 * Torrent yükleme ekranının ne zaman gösterilip ne zaman gösterilmeyeceğini belirleyen saf kural sınıfı (Android bağımsız).
 *
 * Kurallar:
 * 1. Doğrudan (direct/CloudStream/Nuvio) kaynaklarda ÇIKMAZ.
 * 2. Oynatıcı ilk açıldığında torrent kaynağı hazırlanıyorsa ÇIKAR.
 * 3. Farklı bir torrent kaynağına geçiş yapılıyorsa (kullanıcı seçimi veya otomatik yedek) ÇIKAR.
 * 4. İlk video karesi ekrana çizildikten sonra ÇIKMAZ (ekran kaybolur).
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
     * @param isTorrent Kaynağın torrent (magnet/infoHash, TorrentEngine) olup olmadığı
     * @param action Oynatıcıda o an gerçekleşen aksiyon
     * @param firstFrameRendered Yeni kaynağın ilk karesi ekrana çizildi mi
     * @return true ise özel torrent yükleme ekranı gösterilir
     */
    fun shouldShow(
        isTorrent: Boolean,
        action: PlaybackAction,
        firstFrameRendered: Boolean,
    ): Boolean {
        if (!isTorrent) return false
        if (firstFrameRendered) return false
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
     *
     * @param isTorrent Kaynak torrent mi
     * @param isNewStream Yeni/farklı bir akış mı
     * @param firstFrameRendered İlk kare çizildi mi
     * @param isSeeking Aynı kaynakta sarma yapılıyor mu
     * @param isSameStreamRetry Aynı akış yeniden mi hazırlanıyor
     * @param isBufferingDuringPlayback Oynarken geçici tamponlama mı
     */
    fun evaluate(
        isTorrent: Boolean,
        isNewStream: Boolean,
        firstFrameRendered: Boolean,
        isSeeking: Boolean = false,
        isSameStreamRetry: Boolean = false,
        isBufferingDuringPlayback: Boolean = false,
    ): Boolean {
        if (!isTorrent) return false
        if (firstFrameRendered) return false
        if (isSeeking) return false
        if (isSameStreamRetry) return false
        if (isBufferingDuringPlayback) return false
        return isNewStream
    }
}
