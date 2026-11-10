package com.kaan.play.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.settings.ThemeRules
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.PlayCardRow
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.NavBarPosition
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.theme.schemeFor
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

/** Tema seçimi ekranı; alt bölümlerde gezinti çubuğu konumu ve performans ayarları bulunur. */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.Dark)
    val navPosition by container.settings.navBarPosition.collectAsStateWithLifecycle(initialValue = NavBarPosition.DEFAULT)
    val resolvedTheme = ThemeRules.resolve(theme, isSystemInDarkTheme())
    var pickNavPosition by remember { mutableStateOf(false) }
    val focus = rememberFocusMemory()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.settings_theme), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.SELECTABLE.forEachIndexed { index, option ->
                val optionScheme = schemeFor(option)
                val isSelected = resolvedTheme == option
                PlayCardRow(
                    title = stringResource(option.labelRes()),
                    icon = PlayIcons.Palette,
                    iconTint = optionScheme.primary,
                    selected = isSelected,
                    modifier = Modifier.focusMemory(
                        focus,
                        key = "theme_${option.name}",
                        isDefault = isSelected || (index == 0 && resolvedTheme !in ThemeMode.SELECTABLE),
                    ),
                    trailing = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(optionScheme.background)
                                    .border(1.dp, optionScheme.outline, CircleShape),
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(optionScheme.primary)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                        }
                    },
                    onClick = { scope.launch { container.settings.setThemeMode(option) } },
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            PlayCardRow(
                title = stringResource(R.string.nav_bar_position_title),
                value = stringResource(navPosition.labelRes()),
                icon = PlayIcons.Sort,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.focusMemory(focus, key = "nav_bar_position"),
                onClick = { pickNavPosition = true },
            )
        }
    }

    if (pickNavPosition) {
        ChoiceDialog(
            title = stringResource(R.string.nav_bar_position_title),
            options = NavBarPosition.entries,
            selected = navPosition,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                scope.launch { container.settings.setNavBarPosition(it) }
                pickNavPosition = false
            },
            onDismiss = { pickNavPosition = false },
        )
    }
}

private fun NavBarPosition.labelRes(): Int = when (this) {
    NavBarPosition.Top -> R.string.nav_bar_position_top
    NavBarPosition.Bottom -> R.string.nav_bar_position_bottom
    NavBarPosition.Left -> R.string.nav_bar_position_left
    NavBarPosition.Right -> R.string.nav_bar_position_right
}
