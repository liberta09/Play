package com.kaan.play.ui.tv

/**
 * TV kumandasında üst menüden Aşağı basıldığında veya menüdeyken Geri basıldığında
 * içeriğe dönüş mantığını yöneten saf karar sınıfı.
 */
object TopBarFocusRules {
    enum class Action {
        REQUEST_TARGET,
        SCROLL_TO_TOP_AND_FOCUS,
        MOVE_FOCUS_DOWN,
    }

    /**
     * @param targetAttached Hedef FocusRequester şu an bağlı ve odak kabul edebilir mi
     * @param isScrolled Liste ilk öğenin ötesine kaydırılmış mı
     * @param canScrollToTop Başa kaydırma yeteneği mevcut mu
     */
    fun decide(
        targetAttached: Boolean,
        isScrolled: Boolean,
        canScrollToTop: Boolean = true,
    ): Action {
        return when {
            targetAttached && !isScrolled -> Action.REQUEST_TARGET
            isScrolled && canScrollToTop -> Action.SCROLL_TO_TOP_AND_FOCUS
            targetAttached -> Action.REQUEST_TARGET
            else -> Action.MOVE_FOCUS_DOWN
        }
    }
}
