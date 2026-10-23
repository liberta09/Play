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

    /** Test ve doğrulama için release modunu simüle etme bayrağı (yalnızca debug'da am start ile açılabilir). */
    var simulateRelease: Boolean = false

    /** Test için yapay hata üretme bayrağı (ilk kaynakta tek seferlik hata tetikler). */
    var simulateSkipOnce: Boolean = false

    /** Test ve ekran görüntüsü doğrulaması için yapay bildirim gösterme bayrağı. */
    var forceNoticeForTest: Boolean = false

    /** Arabellek deneyi için büyük tampon bayrağı (debug'da am start ile açılabilir). */
    var testBigBuffer: Boolean = false

    /**
     * Hata bildirimindeki teknik ayrıntının arayüzde gösterilip gösterilmeyeceğini belirler.
     * [isDebug] true ise ve [detail] doluysa true döner; release ([isDebug]=false veya [simulateRelease]=true) modunda her zaman false döner.
     */
    fun shouldShowDetail(isDebug: Boolean, detail: String? = null): Boolean {
        if (!isDebug || simulateRelease) return false
        return !detail.isNullOrBlank()
    }
}
