package com.kaan.play.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.kaan.play.data.settings.PerformancePolicy
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.ui.tv.PlayIndication

/** Odak halkası biçimi; Yüksek Kontrast temada 4 dp tam opak çizgiye geçer. */
@Immutable
data class PlayFocusStyle(
    val ringWidthDp: Float = 2f,
    val outerGlowWidthDp: Float = 3f,
    val opaqueRing: Boolean = false,
)

val LocalPlayThemeMode = staticCompositionLocalOf { ThemeMode.Dark }
val LocalPlayFocusStyle = staticCompositionLocalOf { PlayFocusStyle() }
val LocalPlayPerformancePolicy = staticCompositionLocalOf { PerformancePolicy.Standard }

private val DarkScheme = darkColorScheme(
    primary = PlayColors.Accent,
    onPrimary = PlayColors.OnAccent,
    primaryContainer = PlayColors.DarkSurfaceContainer,
    onPrimaryContainer = PlayColors.Accent,
    background = PlayColors.DarkBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.DarkSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.DarkSurfaceVariant,
    onSurfaceVariant = PlayColors.DarkOnMuted,
    surfaceContainer = PlayColors.DarkSurfaceContainer,
    surfaceContainerHigh = PlayColors.DarkSurfaceVariant,
    outline = PlayColors.DarkOutline,
    outlineVariant = PlayColors.DarkOutline,
    error = PlayColors.Error,
)

private val LightScheme = lightColorScheme(
    primary = PlayColors.LightAccent,
    onPrimary = PlayColors.LightOnAccent,
    primaryContainer = PlayColors.LightSurfaceContainer,
    onPrimaryContainer = PlayColors.LightAccent,
    background = PlayColors.LightBackground,
    onBackground = PlayColors.LightOnBackground,
    surface = PlayColors.LightSurface,
    onSurface = PlayColors.LightOnBackground,
    surfaceVariant = PlayColors.LightSurfaceVariant,
    onSurfaceVariant = PlayColors.LightOnMuted,
    surfaceContainer = PlayColors.LightSurfaceContainer,
    surfaceContainerHigh = PlayColors.LightSurfaceVariant,
    outline = PlayColors.LightOutline,
    outlineVariant = PlayColors.LightOutline,
    error = PlayColors.Error,
)

private val AmoledScheme = darkColorScheme(
    primary = PlayColors.AmoledAccent,
    onPrimary = PlayColors.AmoledOnAccent,
    primaryContainer = PlayColors.AmoledSurfaceContainer,
    onPrimaryContainer = PlayColors.AmoledAccent,
    background = PlayColors.AmoledBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.AmoledSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.AmoledSurfaceVariant,
    onSurfaceVariant = PlayColors.DarkOnMuted,
    surfaceContainer = PlayColors.AmoledSurfaceContainer,
    surfaceContainerHigh = PlayColors.AmoledSurfaceVariant,
    outline = PlayColors.AmoledOutline,
    outlineVariant = PlayColors.AmoledOutline,
    error = PlayColors.Error,
)

private val OceanBlueScheme = darkColorScheme(
    primary = PlayColors.OceanAccent,
    onPrimary = PlayColors.OceanOnAccent,
    primaryContainer = PlayColors.OceanSurfaceContainer,
    onPrimaryContainer = PlayColors.OceanAccent,
    background = PlayColors.OceanBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.OceanSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.OceanSurfaceVariant,
    onSurfaceVariant = PlayColors.OceanOnMuted,
    surfaceContainer = PlayColors.OceanSurfaceContainer,
    surfaceContainerHigh = PlayColors.OceanSurfaceVariant,
    outline = PlayColors.OceanOutline,
    outlineVariant = PlayColors.OceanOutline,
    error = PlayColors.Error,
)

private val PurpleNightScheme = darkColorScheme(
    primary = PlayColors.PurpleAccent,
    onPrimary = PlayColors.PurpleOnAccent,
    primaryContainer = PlayColors.PurpleSurfaceContainer,
    onPrimaryContainer = PlayColors.PurpleAccent,
    background = PlayColors.PurpleBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.PurpleSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.PurpleSurfaceVariant,
    onSurfaceVariant = PlayColors.PurpleOnMuted,
    surfaceContainer = PlayColors.PurpleSurfaceContainer,
    surfaceContainerHigh = PlayColors.PurpleSurfaceVariant,
    outline = PlayColors.PurpleOutline,
    outlineVariant = PlayColors.PurpleOutline,
    error = PlayColors.Error,
)

private val HighContrastScheme = darkColorScheme(
    primary = PlayColors.HighContrastAccent,
    onPrimary = PlayColors.HighContrastOnAccent,
    primaryContainer = PlayColors.HighContrastSurfaceContainer,
    onPrimaryContainer = PlayColors.HighContrastAccent,
    background = PlayColors.HighContrastBackground,
    onBackground = PlayColors.HighContrastOnBackground,
    surface = PlayColors.HighContrastSurface,
    onSurface = PlayColors.HighContrastOnBackground,
    surfaceVariant = PlayColors.HighContrastSurfaceVariant,
    onSurfaceVariant = PlayColors.HighContrastOnMuted,
    surfaceContainer = PlayColors.HighContrastSurfaceContainer,
    surfaceContainerHigh = PlayColors.HighContrastSurfaceVariant,
    outline = PlayColors.HighContrastOutline,
    outlineVariant = PlayColors.HighContrastOutline,
    error = PlayColors.Error,
)

fun schemeFor(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.Light -> LightScheme
    ThemeMode.Amoled -> AmoledScheme
    ThemeMode.OceanBlue -> OceanBlueScheme
    ThemeMode.PurpleNight -> PurpleNightScheme
    ThemeMode.HighContrast -> HighContrastScheme
    ThemeMode.Dark, ThemeMode.System -> DarkScheme
}

private val PlayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PlayTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    darkTheme: Boolean? = null,
    performanceMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolvedMode = when {
        darkTheme == false -> ThemeMode.Light
        darkTheme == true && themeMode == ThemeMode.Light -> ThemeMode.Dark
        themeMode == ThemeMode.System -> ThemeMode.Dark
        else -> themeMode
    }
    val scheme = schemeFor(resolvedMode)
    val focusStyle = if (resolvedMode == ThemeMode.HighContrast) {
        PlayFocusStyle(ringWidthDp = 4f, outerGlowWidthDp = 0f, opaqueRing = true)
    } else {
        PlayFocusStyle()
    }
    val performancePolicy = remember(performanceMode) { PerformancePolicy.forMode(performanceMode) }
    MaterialTheme(colorScheme = scheme, shapes = PlayShapes, typography = PlayTypography) {
        val indication = remember(scheme.primary) { PlayIndication(scheme.primary) }
        CompositionLocalProvider(
            LocalIndication provides indication,
            LocalPlayThemeMode provides resolvedMode,
            LocalPlayFocusStyle provides focusStyle,
            LocalPlayPerformancePolicy provides performancePolicy,
            content = content,
        )
    }
}
