package com.kaan.play.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResumeRulesTest {

    @Test
    fun testShouldWaitForNetwork_onlyWhenOfflineAndNetworkErrorAndNotDownloaded() {
        // Normal durum: ağ hatası + cihaz çevrimdışı + indirilmemiş -> bekle
        assertTrue(
            NetworkResumeRules.shouldWaitForNetwork(
                isNetworkError = true,
                isDeviceOffline = true,
                isDownloadedCopy = false,
            ),
        )

        // İndirilmiş kopyada devreye girmez
        assertFalse(
            NetworkResumeRules.shouldWaitForNetwork(
                isNetworkError = true,
                isDeviceOffline = true,
                isDownloadedCopy = true,
            ),
        )

        // Cihaz çevrimdışı değilse (ör. sunucu 500 verdi) kaynak tüketmeye devam edilir
        assertFalse(
            NetworkResumeRules.shouldWaitForNetwork(
                isNetworkError = true,
                isDeviceOffline = false,
                isDownloadedCopy = false,
            ),
        )

        // Çözücü/format hatasıysa internet beklenmez
        assertFalse(
            NetworkResumeRules.shouldWaitForNetwork(
                isNetworkError = false,
                isDeviceOffline = true,
                isDownloadedCopy = false,
            ),
        )

        // Bu oturumda ağ dönünce zaten 1 kez yeniden denendiyse tekrar beklenmez
        assertFalse(
            NetworkResumeRules.shouldWaitForNetwork(
                isNetworkError = true,
                isDeviceOffline = true,
                isDownloadedCopy = false,
                alreadyRetriedOnNetworkReturn = true,
            ),
        )
    }

    @Test
    fun testShouldRetryOnNetworkReturn_onlyOnceAndWithin10Minutes() {
        val tenMinutesMs = 10 * 60 * 1000L

        // İlk kez ve süre dolmamış: yeniden dene
        assertTrue(
            NetworkResumeRules.shouldRetryOnNetworkReturn(
                alreadyRetriedOnNetworkReturn = false,
                elapsedWaitingMs = 5_000L,
            ),
        )

        // Tam sınırda: yeniden dene
        assertTrue(
            NetworkResumeRules.shouldRetryOnNetworkReturn(
                alreadyRetriedOnNetworkReturn = false,
                elapsedWaitingMs = tenMinutesMs,
            ),
        )

        // 10 dakika aşıldı: yeniden deneme
        assertFalse(
            NetworkResumeRules.shouldRetryOnNetworkReturn(
                alreadyRetriedOnNetworkReturn = false,
                elapsedWaitingMs = tenMinutesMs + 1L,
            ),
        )

        // Zaten bir kez denendi: ikinci kez deneme
        assertFalse(
            NetworkResumeRules.shouldRetryOnNetworkReturn(
                alreadyRetriedOnNetworkReturn = true,
                elapsedWaitingMs = 5_000L,
            ),
        )
    }

    @Test
    fun testIsNetworkError_detectsVariousNetworkErrors() {
        assertTrue(NetworkResumeRules.isNetworkError("java.io.IOException: Cleartext HTTP traffic not permitted"))
        assertTrue(NetworkResumeRules.isNetworkError("HttpDataSourceException: response 404"))
        assertTrue(NetworkResumeRules.isNetworkError("ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)"))
        assertTrue(NetworkResumeRules.isNetworkError("SocketTimeoutException: timeout"))
        assertTrue(NetworkResumeRules.isNetworkError("Device is offline"))

        assertFalse(NetworkResumeRules.isNetworkError("DECODER_INIT_FAILED"))
        assertFalse(NetworkResumeRules.isNetworkError("PARSING_ERROR"))
        assertFalse(NetworkResumeRules.isNetworkError("DRM_ERROR"))
    }
}
