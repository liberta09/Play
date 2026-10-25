package com.kaan.play.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

private enum class OpenDialog { Theme, Language, Poster }

/**
 * Ayarlar: tam beş kalem (Eklentiler, Oynatıcı, Tema, Dil, Hakkında), kart düzeninde
 * (koyu mavi yarı saydam kartlar, sol ikon, sağ ok).
 */
@Composable
fun SettingsScreen(onOpenAddons: () -> Unit, onOpenPlayer: () -> Unit, onOpenAbout: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val addonCount by container.installedSourcesCount
        .collectAsStateWithLifecycle(initialValue = null)
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.Dark)
    val posterTemplate by container.settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = PosterUrlTemplate.DEFAULT)
    var language by remember { mutableStateOf(AppLanguage.choice()) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }
    var posterDraft by remember { mutableStateOf(PosterUrlTemplate.DEFAULT) }
    val focus = rememberFocusMemory()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.addons_title),
            badge = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) },
            icon = PlayIcons.Extension,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "addons", isDefault = true),
            onClick = onOpenAddons,
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.settings_player),
            icon = PlayIcons.PlayCircle,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "player"),
            onClick = onOpenPlayer,
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.settings_theme),
            value = stringResource(theme.labelRes()),
            icon = PlayIcons.Palette,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "theme"),
            onClick = { dialog = OpenDialog.Theme },
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.settings_language),
            value = stringResource(language.labelRes()),
            icon = PlayIcons.Globe,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "language"),
            onClick = { dialog = OpenDialog.Language },
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.settings_poster_url),
            value = stringResource(
                if (posterTemplate.isBlank()) R.string.settings_poster_url_default
                else R.string.settings_poster_url_value,
            ),
            icon = PlayIcons.Library,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "poster-url"),
            onClick = {
                posterDraft = posterTemplate
                dialog = OpenDialog.Poster
            },
        )
        com.kaan.play.ui.components.PlayCardRow(
            title = stringResource(R.string.settings_about),
            value = BuildConfig.VERSION_NAME,
            icon = PlayIcons.InfoCircle,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.focusMemory(focus, "about"),
            onClick = onOpenAbout,
        )
    }

    when (dialog) {
        OpenDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = theme,
            label = { stringResource(it.labelRes()) },
            onSelect = { scope.launch { container.settings.setThemeMode(it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        OpenDialog.Language -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageChoice.entries,
            selected = language,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                language = it
                dialog = null
                AppLanguage.apply(it)
            },
            onDismiss = { dialog = null },
        )
        OpenDialog.Poster -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_poster_url_title)) },
            text = {
                OutlinedTextField(
                    value = posterDraft,
                    onValueChange = { posterDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_poster_url)) },
                    placeholder = { Text(stringResource(R.string.settings_poster_url_hint)) },
                    supportingText = { Text(stringResource(R.string.settings_poster_url_help)) },
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { container.settings.setPosterUrlTemplate(posterDraft) }
                    dialog = null
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
        null -> Unit
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.System -> R.string.common_system
}

private fun LanguageChoice.labelRes(): Int = when (this) {
    LanguageChoice.System -> R.string.common_system
    LanguageChoice.English -> R.string.language_english
    LanguageChoice.Turkish -> R.string.language_turkish
}
