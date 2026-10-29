package com.kaan.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Ana sayfa zemini ve üst hap menü yüzeyi. Koyu temada sinematik görünüm sabit kalır; açık temada değerler
 * [MaterialTheme.colorScheme]'den türetilir (sabit koyu renk açık temayı bozmasın).
 */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun homeBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val colors = if (isDarkScheme()) {
        listOf(Color(0xFF1A1A24), Color(0xFF0A0D14), Color(0xFF040508))
    } else {
        listOf(scheme.surface, scheme.background, scheme.surfaceVariant)
    }
    return Brush.radialGradient(colors = colors, radius = 1500f)
}

/** Üst hap menünün dolgu ve çerçeve rengi. */
@Composable
fun topBarSurfaceColor(): Color =
    if (isDarkScheme()) Color(0xE60A131F) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

@Composable
fun topBarBorderColor(): Color =
    if (isDarkScheme()) Color(0x33FFFFFF) else MaterialTheme.colorScheme.outline

/** Profil seçimi ekranının zemini; koyu temada sabit, açık temada colorScheme'den. */
@Composable
fun profileBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val colors = if (isDarkScheme()) {
        listOf(Color(0xFF3A1027), Color(0xFF190B14), Color(0xFF050505))
    } else {
        listOf(scheme.surfaceContainer, scheme.background, scheme.background)
    }
    return Brush.verticalGradient(colors)
}
