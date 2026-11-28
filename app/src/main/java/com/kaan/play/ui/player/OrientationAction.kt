package com.kaan.play.ui.player

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kaan.play.R
import com.kaan.play.data.settings.ResizeMode
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons

/*
 * Oynatıcının dikey/yatay tam ekran parçaları. PlayerScreen'in dev composable'ından ayrı tutulur: R8 çıktısı Android 9
 * doğrulayıcısından geçsin (VerifyError, 1.3.1 dersi). Bkz. [episodesBarAction].
 */

/** Üst çubuktaki döndürme düğmesi; [onToggle] null ise (TV/kumanda, dokunmatik yok) hiçbir şey çizilmez. */
@Composable
internal fun OrientationToggleButton(portraitMode: Boolean, onToggle: (() -> Unit)?, tint: Color) {
    if (onToggle == null) return
    IconButton(onClick = onToggle) {
        Icon(
            PlayIcons.RotateScreen,
            contentDescription = stringResource(
                if (portraitMode) R.string.player_landscape_fullscreen else R.string.player_portrait_fullscreen,
            ),
            tint = tint,
        )
    }
}

/** Dikeyde seçilen sığdırma kipi yalnızca o görünüme aittir; yatay "Yeniden boyutlandır" tercihi değişmez. */
@Composable
internal fun rememberPortraitResize(portraitMode: Boolean): MutableState<ResizeMode> =
    remember(portraitMode) { mutableStateOf(ResizeMode.Fit) }

internal object OrientationResize {
    /** Ekranda uygulanan kip: dikeyde yerel seçim, yatayda kalıcı ayar. */
    fun display(portraitMode: Boolean, portraitResize: ResizeMode, landscapeResize: ResizeMode): ResizeMode =
        if (portraitMode) portraitResize else landscapeResize

    fun next(current: ResizeMode): ResizeMode = ResizeMode.entries[(current.ordinal + 1) % ResizeMode.entries.size]
}
