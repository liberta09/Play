package com.kaan.play.data.plugin

import com.kaan.play.data.db.AddonType

/**
 * Yeni depo eklenirken kurulan eklentilerin ilk açık/kapalı durumunu belirler (Android'den bağımsız).
 *
 * Kullanıcı kararı (2026-10-23): Yeni eklenen bir depoda (Nuvio eklenti deposu ve CloudStream deposu)
 * kurulan eklentiler artık varsayılan olarak AÇIK (enabled = true) gelir.
 */
object RepoInstallPolicy {

    /**
     * Yeni bir depo eklenirken kurulan eklentinin ilk enabled değeri.
     * Şimdilik her iki depo türü (NUVIO_PLUGIN_REPO, CLOUDSTREAM_REPO) için `true`.
     */
    fun initialPluginEnabled(type: AddonType = AddonType.NUVIO_PLUGIN_REPO): Boolean = when (type) {
        AddonType.NUVIO_PLUGIN_REPO -> true
        AddonType.CLOUDSTREAM_REPO -> true
        else -> true
    }
}
