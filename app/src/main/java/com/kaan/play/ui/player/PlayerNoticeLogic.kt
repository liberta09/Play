package com.kaan.play.ui.player

/**
 * Kaynak hata bildirimi için teknik ayrıntının gösterilme kararı (Android'den bağımsız).
 *
 * CLAUDE.md Kural 3: "Teknik ayrıntı yok".
 * Release derlemelerinde kullanıcı arayüzünde teknik ayrıntı (hata kodu, istisna sınıfı vb.) gizlenir;
 * yalnızca kullanıcı dostu ana ileti ("Bir kaynak açılmadı, sıradakine geçildi.") gösterilir.
 * Teknik ayrıntı yalnızca debug (geliştirici) derlemesinde gösterilir ve her durumda PlayMatch günlüğünde kalır.
 */
object PlayerNoticeLogic {

    /**
     * Hata bildirimindeki teknik ayrıntının arayüzde gösterilip gösterilmeyeceğini belirler.
     * [isDebug] true ise ve [detail] doluysa true döner; release ([isDebug]=false) modunda her zaman false döner.
     */
    fun shouldShowDetail(isDebug: Boolean, detail: String? = null): Boolean {
        if (!isDebug) return false
        return !detail.isNullOrBlank()
    }
}
