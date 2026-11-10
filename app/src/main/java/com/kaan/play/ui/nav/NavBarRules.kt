package com.kaan.play.ui.nav

import java.util.Locale

/** Ana gezinti çubuğunun ekrandaki konumu. Varsayılan [Top] (bugünkü yüzen üst hap). */
enum class NavBarPosition {
    Top,
    Bottom,
    Left,
    Right;

    companion object {
        val DEFAULT = Top

        fun fromStored(raw: String?): NavBarPosition {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return DEFAULT
            return entries.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                ?: when (clean.lowercase(Locale.ROOT)) {
                    "ust", "üst" -> Top
                    "alt" -> Bottom
                    "sol" -> Left
                    "sag", "sağ" -> Right
                    else -> DEFAULT
                }
        }
    }
}

/** Kumanda / D-pad kenar geçiş yönü. */
enum class EdgeDirection { Up, Down, Left, Right }

/** Gezinti çubuğu konumuna göre içerik alanının kenarlardan bırakacağı boşluk (dp). */
data class NavContentPaddingDp(
    val top: Int,
    val bottom: Int,
    val start: Int,
    val end: Int,
)

/** Gezinti çubuğu yerleşimi, kenar boşlukları ve kumanda köprü kuralları (Android bağımsız). */
object NavBarRules {
    const val TOP_BAR_NON_HOME_RESERVE_DP = 58
    const val BOTTOM_BAR_RESERVE_DP = 68
    const val SIDE_STRIP_RESERVE_DP = 88

    fun isVerticalStrip(position: NavBarPosition): Boolean =
        position == NavBarPosition.Left || position == NavBarPosition.Right

    /** Odak çubuktayken hangi yön tuşu içeriğe geri döndürür. */
    fun menuExitDirection(position: NavBarPosition): EdgeDirection = when (position) {
        NavBarPosition.Top -> EdgeDirection.Down
        NavBarPosition.Bottom -> EdgeDirection.Up
        NavBarPosition.Left -> EdgeDirection.Right
        NavBarPosition.Right -> EdgeDirection.Left
    }

    /** Odak içerik kenarındayken hangi yön tuşu çubuğa çıkarır. */
    fun contentReturnDirection(position: NavBarPosition): EdgeDirection = when (position) {
        NavBarPosition.Top -> EdgeDirection.Up
        NavBarPosition.Bottom -> EdgeDirection.Down
        NavBarPosition.Left -> EdgeDirection.Left
        NavBarPosition.Right -> EdgeDirection.Right
    }

    /** Ana sayfada profil simgesi ile "Eklenti seç" hapı arasındaki yön köprüsü. */
    fun profileToPillDirection(position: NavBarPosition): EdgeDirection = when (position) {
        NavBarPosition.Right -> EdgeDirection.Left
        NavBarPosition.Left, NavBarPosition.Top, NavBarPosition.Bottom -> EdgeDirection.Right
    }

    /** Dikey yan şeritte kısa etiket yalnızca odaklanınca veya seçilince görünür. */
    fun showStripItemLabel(focused: Boolean, selected: Boolean): Boolean = focused || selected

    fun contentPaddingDp(position: NavBarPosition, showNav: Boolean, isHome: Boolean): NavContentPaddingDp {
        if (!showNav) return NavContentPaddingDp(top = 0, bottom = 0, start = 0, end = 0)
        return when (position) {
            NavBarPosition.Top -> NavContentPaddingDp(
                top = if (isHome) 0 else TOP_BAR_NON_HOME_RESERVE_DP,
                bottom = 0,
                start = 0,
                end = 0,
            )
            NavBarPosition.Bottom -> NavContentPaddingDp(
                top = 0,
                bottom = BOTTOM_BAR_RESERVE_DP,
                start = 0,
                end = 0,
            )
            NavBarPosition.Left -> NavContentPaddingDp(
                top = 0,
                bottom = 0,
                start = SIDE_STRIP_RESERVE_DP,
                end = 0,
            )
            NavBarPosition.Right -> NavContentPaddingDp(
                top = 0,
                bottom = 0,
                start = 0,
                end = SIDE_STRIP_RESERVE_DP,
            )
        }
    }

    /** Ana sayfadaki sağ alt "Eklenti seç" hapının alt kenardan dikey uzaklığı (dp). */
    fun homeProviderPillBottomPaddingDp(position: NavBarPosition): Int = when (position) {
        NavBarPosition.Bottom -> 20 + BOTTOM_BAR_RESERVE_DP
        else -> 20
    }

    /** Ana sayfa liste alt dolgusuna eklenecek ekstra boşluk (dp). */
    fun homeListExtraBottomDp(position: NavBarPosition): Int = when (position) {
        NavBarPosition.Bottom -> BOTTOM_BAR_RESERVE_DP
        else -> 0
    }
}
