package com.kaan.play.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.SkipCategory
import com.kaan.play.data.settings.SkipPlaybackSettings
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

/**
 * Ayarlar > Oynatıcı: altyazı görünümü (üstte canlı önizleme) ve "her zaman kaldığım yerden devam et".
 * Değişiklikler anında DataStore'a yazılır; oynatıcı aynı akıştan okur.
 */
@Composable
fun PlayerSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val style by container.settings.subtitleStyle.collectAsStateWithLifecycle(initialValue = SubtitleStyle())
    val alwaysResume by container.settings.alwaysResume
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_ALWAYS_RESUME)

    val downloadWifiOnly by container.settings.downloadWifiOnly
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_DOWNLOAD_WIFI_ONLY)
    val subtitleLanguage by container.settings.subtitleLanguage
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_SUBTITLE_LANGUAGE)
    val autoTranslate by container.settings.autoTranslateSubtitles
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_TRANSLATE_SUBS)
    val autoSource by container.settings.autoSource
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_SOURCE)
    val preferDub by container.settings.preferDub
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_PREFER_DUB)
    val autoNext by container.settings.autoNext
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_NEXT)
    val autoNextSeconds by container.settings.autoNextSeconds
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_NEXT_SECONDS)
    val skipSettings by container.settings.skipPlayback.collectAsStateWithLifecycle(initialValue = SkipPlaybackSettings.Default)
    var pickLanguage by remember { mutableStateOf(false) }
    var pickCountdown by remember { mutableStateOf(false) }
    val uiLanguage = AppLanguage.effective().iso

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.settings_player), onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SubtitlePreview(style)
            SubtitleStyleControls(style) { transform -> scope.launch { container.settings.updateSubtitleStyle(transform) } }
            // Otomatik seçim ve çevrimiçi altyazı araması bu dile göre yapılır (sonra İngilizce).
            ValueRow(
                label = stringResource(R.string.settings_subtitle_language),
                value = SubtitleLanguages.displayName(subtitleLanguage, uiLanguage),
                onClick = { pickLanguage = true },
            )
            // Türkçe altyazı hiçbir kaynakta yoksa İngilizce, cihaz üstünde (sunucuya metin gitmeden) Türkçeye çevrilir.
            SwitchRow(stringResource(R.string.settings_auto_translate_subs), autoTranslate) {
                scope.launch { container.settings.setAutoTranslateSubtitles(it) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Kaynağı otomatik seç: kapalıyken eski davranış (önce kaynak listesi). Türkçe dublaj: dublajlı kaynak önce denenir.
            SwitchRow(stringResource(R.string.settings_auto_source), autoSource) {
                scope.launch { container.settings.setAutoSource(it) }
            }
            SwitchRow(stringResource(R.string.settings_prefer_dub), preferDub) {
                scope.launch { container.settings.setPreferDub(it) }
            }
            SwitchRow(stringResource(R.string.settings_always_resume), alwaysResume) {
                scope.launch { container.settings.setAlwaysResume(it) }
            }
            // Dizide bölüm bitmeye yaklaşınca sonraki bölüm geri sayımla açılır (geri sayım yalnızca açıkken görünür).
            SwitchRow(stringResource(R.string.settings_auto_next), autoNext) {
                scope.launch { container.settings.setAutoNext(it) }
            }
            if (autoNext) {
                ValueRow(
                    label = stringResource(R.string.settings_auto_next_countdown),
                    value = stringResource(R.string.settings_auto_next_seconds, autoNextSeconds),
                    onClick = { pickCountdown = true },
                )
            }
            // İntro/özet/jenerik/önizleme atlama (TheIntroDB + chapter): düğmeler açık, otomatik atlama kapalı gelir.
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow(stringResource(R.string.settings_skip_buttons), skipSettings.showButtons) {
                scope.launch { container.settings.setSkipButtonsEnabled(it) }
            }
            SwitchRow(stringResource(R.string.settings_auto_skip_segments), skipSettings.autoSkip) {
                scope.launch { container.settings.setAutoSkipEnabled(it) }
            }
            Text(stringResource(R.string.settings_skip_segment_types), style = MaterialTheme.typography.titleMedium)
            listOf(
                SkipCategory.Intro to R.string.settings_skip_intro,
                SkipCategory.Recap to R.string.settings_skip_recap,
                SkipCategory.Credits to R.string.settings_skip_credits,
                SkipCategory.Preview to R.string.settings_skip_preview,
            ).forEach { (category, label) ->
                SwitchRow(stringResource(label), skipSettings.isEnabled(category)) { enabled ->
                    scope.launch { container.settings.setSkipCategoryEnabled(category, enabled) }
                }
            }
            // İndirmeler (Faz 4/B): Ayarlar'a yeni ana kalem eklenmez, bu ayar Oynatıcı'nın içindedir.
            SwitchRow(stringResource(R.string.settings_download_wifi_only), downloadWifiOnly) {
                scope.launch { container.settings.setDownloadWifiOnly(it) }
            }
        }
    }

    if (pickCountdown) {
        ChoiceDialog(
            title = stringResource(R.string.settings_auto_next_countdown),
            options = SettingsRepository.AUTO_NEXT_OPTIONS,
            selected = autoNextSeconds,
            label = { stringResource(R.string.settings_auto_next_seconds, it) },
            onSelect = { scope.launch { container.settings.setAutoNextSeconds(it) }; pickCountdown = false },
            onDismiss = { pickCountdown = false },
        )
    }

    if (pickLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.settings_subtitle_language),
            options = SettingsRepository.SUBTITLE_LANGUAGES,
            selected = subtitleLanguage,
            label = { SubtitleLanguages.displayName(it, uiLanguage) },
            onSelect = { scope.launch { container.settings.setSubtitleLanguage(it) }; pickLanguage = false },
            onDismiss = { pickLanguage = false },
        )
    }
}

/** Tek satırlık seçim: solda ad, sağda geçerli değer; satırın tamamı dokunulur ve kumandayla odaklanır. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** Tek satırlık açma/kapama: satırın tamamı dokunulur ve kumandayla odaklanır (anahtar yalnızca gösterir). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
