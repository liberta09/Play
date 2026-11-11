package com.kaan.play.ui.player

import java.util.Locale

/**
 * İnternet kesintisinde oynatmanın beklemesi ve ağ geri geldiğinde (NET_CAPABILITY_VALIDATED)
 * aynı kaynaktan aynı saniyeden otomatik devam etmesi kurallarını yöneten saf mantık sınıfı.
 */
object NetworkResumeRules {
    /** İnternet bekleme zaman aşımı: 10 dakika */
    const val WAIT_TIMEOUT_MS: Long = 10 * 60 * 1000L

    /**
     * Oynatma ağ hatasıyla durduğunda internet beklenip beklenmeyeceğine karar verir.
     *
     * @param isNetworkError Hatası bir IOException veya HttpDataSource hatası mı
     * @param isDeviceOffline Cihaz şu an çevrimdışı mı
     * @param isDownloadedCopy İndirilmiş (çevrimdışı) kopyada bu akış devreye girmez
     * @param alreadyRetriedOnNetworkReturn Bu oturumda ağ dönünce zaten 1 kez yeniden denendi mi
     */
    fun shouldWaitForNetwork(
        isNetworkError: Boolean,
        isDeviceOffline: Boolean,
        isDownloadedCopy: Boolean,
        alreadyRetriedOnNetworkReturn: Boolean = false,
    ): Boolean {
        if (isDownloadedCopy) return false
        if (alreadyRetriedOnNetworkReturn) return false
        return isNetworkError && isDeviceOffline
    }

    /**
     * Ağ geri geldiğinde (NET_CAPABILITY_VALIDATED) aynı kaynağın yeniden denenip denenmeyeceğine karar verir.
     *
     * @param alreadyRetriedOnNetworkReturn Bu oturumda ağ dönünce zaten 1 kez yeniden denendi mi
     * @param elapsedWaitingMs Bekleme başlangıcından bu yana geçen süre (ms)
     * @param timeoutMs İzin verilen en uzun bekleme süresi
     */
    fun shouldRetryOnNetworkReturn(
        alreadyRetriedOnNetworkReturn: Boolean,
        elapsedWaitingMs: Long,
        timeoutMs: Long = WAIT_TIMEOUT_MS,
    ): Boolean {
        if (alreadyRetriedOnNetworkReturn) return false
        if (elapsedWaitingMs > timeoutMs) return false
        return true
    }

    /**
     * Bekleme süresinin dolup dolmadığını (10 dakika) kontrol eder.
     */
    fun isWaitTimedOut(elapsedWaitingMs: Long, timeoutMs: Long = WAIT_TIMEOUT_MS): Boolean {
        return elapsedWaitingMs > timeoutMs
    }

    private val NETWORK_MARKERS = listOf(
        "IOEXCEPTION",
        "HTTPDATASOURCE",
        "NETWORK",
        "SOCKET",
        "TIMEOUT",
        "UNKNOWNHOST",
        "CONNECT",
        "-1004",
        "CONNECTION_CLOSED",
        "CONNECTION_RESET",
        "OFFLINE",
    )

    /**
     * Verilen hata detayının ağ/bağlantı hatası olup olmadığını tespit eder.
     */
    fun isNetworkError(detail: String): Boolean {
        val upper = detail.trim().uppercase(Locale.ROOT)
        if (upper.isEmpty()) return true
        if (NETWORK_MARKERS.any { upper.contains(it) }) return true
        return MidPlaybackRecoveryRules.isNetworkError(detail)
    }
}
