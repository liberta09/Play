package com.kaan.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kaan.play.data.settings.ThemeMode

/**
 * Ana sayfa zemini ve hap menü yüzeyi. Koyu temada sinematik görünüm korunur; diğer temalarda
 * [MaterialTheme.colorScheme] ve [LocalPlayThemeMode] renklerinden türetilir.
 */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun homeBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val colors = when (LocalPlayThemeMode.current) {
        ThemeMode.Dark -> listOf(Color(0xFF1A1A24), Color(0xFF0A0D14), Color(0xFF040508))
        ThemeMode.Amoled, ThemeMode.HighContrast -> listOf(scheme.background, scheme.background)
        ThemeMode.OceanBlue, ThemeMode.PurpleNight -> listOf(scheme.surfaceVariant, scheme.surface, scheme.background)
        ThemeMode.Light, ThemeMode.System -> listOf(scheme.surface, scheme.background, scheme.surfaceVariant)
    }
    return Brush.radialGradient(colors = colors, radius = 1500f)
}

/** Hap gezinti çubuğunun dolgu ve çerçeve rengi. */
@Composable
fun topBarSurfaceColor(): Color = when (LocalPlayThemeMode.current) {
    ThemeMode.Dark -> Color(0xE60A131F)
    ThemeMode.HighContrast -> MaterialTheme.colorScheme.background
    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
}

@Composable
fun topBarBorderColor(): Color = when (LocalPlayThemeMode.current) {
    ThemeMode.Dark -> Color(0x33FFFFFF)
    else -> MaterialTheme.colorScheme.outline
}

/** Profil seçimi ekranının zemini; koyu temada sinematik, diğer temalarda colorScheme'den. */
@Composable
fun profileBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val colors = when (LocalPlayThemeMode.current) {
        ThemeMode.Dark -> listOf(Color(0xFF3A1027), Color(0xFF190B14), Color(0xFF050505))
        ThemeMode.Amoled, ThemeMode.HighContrast -> listOf(scheme.surface, scheme.background, scheme.background)
        else -> listOf(scheme.surfaceContainer, scheme.background, scheme.background)
    }
    return Brush.verticalGradient(colors)
}
