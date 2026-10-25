package com.kaan.play.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ekran genişliğine/yüksekliğine (dp) göre yerleşim kuralları. Android'den bağımsız tutulur ki birim testlenebilsin.
 * Telefon dikeyde hiçbir şey değişmez: alt menü, tek sütun.
 */
@Immutable
data class PlayLayout(val widthDp: Int, val heightDp: Int) {

    /** Geniş ekranda alt menü yerine soldaki yan menü (NavigationRail). Alçak yatay telefon alt menüde kalır. */
    val useRail: Boolean get() = widthDp >= RAIL_MIN_WIDTH && heightDp >= RAIL_MIN_HEIGHT

    /** Detay: solda afiş, sağda bilgiler. Geniş ekranda ya da yatayda. */
    val twoPane: Boolean get() = widthDp >= RAIL_MIN_WIDTH || widthDp > heightDp

    companion object {
        const val RAIL_MIN_WIDTH = 600
        const val RAIL_MIN_HEIGHT = 480

        /** Yan menü (NavigationRail) kapalı ve açık genişlikleri. */
        const val RAIL_COLLAPSED_WIDTH_DP = 72
        const val RAIL_EXPANDED_WIDTH_DP = 184

        /** Ayarlar/Eklentiler gibi metin ağırlıklı ekranların okunur en büyük genişliği. */
        const val READABLE_WIDTH_DP = 720

        /** Kütüphane gibi kapak ızgarası olan ekranların en büyük genişliği. */
        const val GRID_WIDTH_DP = 1040
    }
}

@Composable
fun rememberPlayLayout(): PlayLayout {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp) { PlayLayout(c.screenWidthDp, c.screenHeightDp) }
}

/** İçeriği ortalar ve [maxWidth]'ten geniş yaymaz (geniş ekranda okunur satır uzunluğu). Telefonda hiçbir etkisi yoktur. */
@Composable
fun ReadableWidth(maxWidth: Dp = PlayLayout.READABLE_WIDTH_DP.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}
