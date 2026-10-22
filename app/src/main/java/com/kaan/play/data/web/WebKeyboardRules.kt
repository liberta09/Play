package com.kaan.play.data.web

/**
 * TV / kumanda kipinde web sayfasında metin kutusu tıklamaları ve ekran klavyesi açma kuralları.
 * Android'den bağımsız birim testli mantık.
 */
object WebKeyboardRules {

    /**
     * WebView.HitTestResult.EDIT_TEXT_TYPE sabitinin değeri (9).
     */
    const val HIT_TYPE_EDIT_TEXT = 9

    /**
     * Tıklanan öğenin türüne göre ekran klavyesinin açılmasının gerekip gerekmediğini belirler (K4).
     *
     * @param hitTestType WebView.hitTestResult.type değeri
     * @return Tıklanan öğe metin giriş alanı ise true
     */
    fun shouldRequestKeyboard(hitTestType: Int?): Boolean {
        return hitTestType == HIT_TYPE_EDIT_TEXT
    }

    /**
     * Geri tuşuna basıldığında izlenecek eylem sırası (K4, K6).
     * Sıra: Klavye açıksa kapat -> Tam ekran açıksa çık -> Sayfa geçmişi varsa geri git -> Ekranı kapat.
     */
    enum class BackAction {
        HIDE_KEYBOARD,
        EXIT_FULLSCREEN,
        NAVIGATE_BACK,
        FINISH
    }

    /**
     * Verilen duruma göre Geri tuşunun tetiklemesi gereken eylemi belirler (K4, K6).
     *
     * @param isKeyboardVisible Ekran klavyesinin açık olup olmadığı
     * @param isFullscreen Tam ekran videonun aktif olup olmadığı
     * @param canGoBack WebView sayfa geçmişinde geri gidilip gidilemeyeceği
     */
    fun resolveBackAction(
        isKeyboardVisible: Boolean,
        isFullscreen: Boolean,
        canGoBack: Boolean
    ): BackAction = when {
        isKeyboardVisible -> BackAction.HIDE_KEYBOARD
        isFullscreen -> BackAction.EXIT_FULLSCREEN
        canGoBack -> BackAction.NAVIGATE_BACK
        else -> BackAction.FINISH
    }
}
