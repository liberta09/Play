package com.kaan.play.ui.tv

/**
 * TV kumandasında üst menüden Aşağı basıldığında veya menüdeyken Geri basıldığında
 * içeriğe dönüş mantığını yöneten saf karar sınıfı.
 */
object TopBarFocusRules {

    enum class Step {
        INITIAL_TARGET,
        SCROLL_AND_RETRY,
        MOVE_FOCUS_DOWN,
    }

    /**
     * returnFocusToContent akışında hangi adımın işletileceğini belirler.
     *
     * @param targetAttached Hedef FocusRequester bağlı mı / odak denenebilir mi
     * @param focusTaken Odak başarıyla alındı mı (runCatching { requestFocus() }.getOrDefault(false))
     * @param isHome Ana sayfa rotasında mı (currentRoute == Tab.Home.route || currentRoute == null)
     * @param isScrolled Liste ilk öğenin ötesine kaydırılmış mı / başa kaydırma aksiyonu var mı
     * @return Yürütülecek odak adımı ([Step])
     */
    fun decideStep(
        targetAttached: Boolean,
        focusTaken: Boolean,
        isHome: Boolean,
        isScrolled: Boolean,
    ): Step {
        if (targetAttached && focusTaken) {
            return Step.INITIAL_TARGET
        }
        if (isHome && isScrolled) {
            return Step.SCROLL_AND_RETRY
        }
        return Step.MOVE_FOCUS_DOWN
    }
}
