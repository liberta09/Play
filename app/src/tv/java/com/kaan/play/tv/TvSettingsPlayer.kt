package com.kaan.play.tv

import com.kaan.play.R
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.SkipCategory
import com.kaan.play.data.settings.SkipPlaybackSettings
import com.kaan.play.data.settings.SubtitleBackground
import com.kaan.play.data.settings.SubtitleColor
import com.kaan.play.data.settings.SubtitleSize
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.player.SubtitleStyling
import kotlinx.coroutines.launch

/**
 * Ayarlar → Oynatıcı (TV'ye özgü; Play'in Oynatıcı ayarlarının TV karşılığı). Yalnız TV oynatıcısının gerçekten okuduğu
 * ayarlar gösterilir: altyazı görünümü ve dili, kaynak seçimi, Türkçe dublaj önceliği, kaldığı yerden devam, sonraki bölüm,
 * intro/özet/jenerik atlama. Değerler Play'in DataStore'una anında yazılır; oynatıcı aynı akışlardan okur. Seçenekler az olduğu
 * için OK her basışta sıradaki değere geçer.
 */
@Composable
internal fun TvPlayerSettings() {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as PlayApp).container.settings }
    val scope = rememberCoroutineScope()
    val style by settings.subtitleStyle.collectAsStateWithLifecycle(initialValue = SubtitleStyle())
    val language by settings.subtitleLanguage
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_SUBTITLE_LANGUAGE)
    val preferDub by settings.preferDub.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_PREFER_DUB)
    val autoSource by settings.autoSource.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_SOURCE)
    val alwaysResume by settings.alwaysResume.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_ALWAYS_RESUME)
    val skip by settings.skipPlayback.collectAsStateWithLifecycle(initialValue = SkipPlaybackSettings.Default)
    val autoNext by settings.autoNext.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_NEXT)
    val autoNextSeconds by settings.autoNextSeconds
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_AUTO_NEXT_SECONDS)
    val updateStyle: ((SubtitleStyle) -> SubtitleStyle) -> Unit = { t -> scope.launch { settings.updateSubtitleStyle(t) } }
    // Sayfa açılınca odak ilk satıra: önceki sayfanın odaklı öğesi kalktığı için kumanda boşa düşmesin.
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }

    SettingsPageFrame(stringResource(R.string.tv_settings_player_title)) {
        item(key = "subtitle-title") { SectionTitle(stringResource(R.string.tv_psettings_subtitles)) }
        item(key = "subtitle-preview") { SubtitlePreview(style) }
        item(key = "subtitle-size") {
            ValueItem(stringResource(R.string.tv_psettings_size), style.size.label(), Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused }) {
                updateStyle { it.copy(size = SubtitleSize.entries.after(it.size)) }
            }
        }
        item(key = "subtitle-bold") { ValueItem(stringResource(R.string.tv_psettings_bold), onOff(style.bold)) { updateStyle { it.copy(bold = !it.bold) } } }
        item(key = "subtitle-italic") { ValueItem(stringResource(R.string.tv_psettings_italic), onOff(style.italic)) { updateStyle { it.copy(italic = !it.italic) } } }
        item(key = "subtitle-color") {
            ValueItem(stringResource(R.string.tv_psettings_text_color), style.color.label()) { updateStyle { it.copy(color = SubtitleColor.entries.after(it.color)) } }
        }
        item(key = "subtitle-background") {
            ValueItem(stringResource(R.string.tv_psettings_background), style.background.label()) {
                updateStyle { it.copy(background = SubtitleBackground.entries.after(it.background)) }
            }
        }
        // Oynatıcı altyazıyı önce bu dilde arar ve seçer (sonra İngilizce).
        item(key = "subtitle-language") {
            ValueItem(stringResource(R.string.tv_psettings_subtitle_language), SubtitleLanguages.displayName(language, AppLanguage.effective().iso)) {
                scope.launch { settings.setSubtitleLanguage(SettingsRepository.SUBTITLE_LANGUAGES.after(language)) }
            }
        }

        item(key = "playback-title") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_psettings_playback)) } }
        // Açık: başlangıç ekranı çalışan en iyi kaynağı kendisi seçer. Kapalı: kaynaklar listelenir, kullanıcı seçer.
        item(key = "auto-source") {
            ValueItem(stringResource(R.string.tv_psettings_auto_source), onOff(autoSource)) { scope.launch { settings.setAutoSource(!autoSource) } }
        }
        // Hem başlangıçtaki kaynak yoklaması hem oynatıcının ses izi seçimi Türkçe dublajı öne alır.
        item(key = "prefer-dub") {
            ValueItem(stringResource(R.string.tv_psettings_prefer_dub), onOff(preferDub)) { scope.launch { settings.setPreferDub(!preferDub) } }
        }
        // Kapalıyken yarım kalan içerik açılırken "Kaldığın yerden devam / Baştan başla" sorulur.
        item(key = "always-resume") {
            ValueItem(stringResource(R.string.tv_psettings_always_resume), onOff(alwaysResume)) { scope.launch { settings.setAlwaysResume(!alwaysResume) } }
        }
        // Dizide bölüm bitince sonraki bölüm geri sayımla açılır; geri sayım yalnız açıkken anlamlı.
        item(key = "auto-next") {
            ValueItem(stringResource(R.string.tv_psettings_auto_next), onOff(autoNext)) { scope.launch { settings.setAutoNext(!autoNext) } }
        }
        if (autoNext) item(key = "auto-next-seconds") {
            ValueItem(stringResource(R.string.tv_psettings_countdown), stringResource(R.string.tv_psettings_seconds, autoNextSeconds)) {
                scope.launch { settings.setAutoNextSeconds(SettingsRepository.AUTO_NEXT_OPTIONS.after(autoNextSeconds)) }
            }
        }

        // Play'deki gibi: segment işaretli içerikte oynatıcıda "İntroyu geç" düğmesi; otomatik atlamada segment kendiliğinden geçilir.
        item(key = "skip-title") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_psettings_skip_title)) } }
        item(key = "skip-buttons") {
            ValueItem(stringResource(R.string.tv_psettings_skip_buttons), onOff(skip.showButtons)) { scope.launch { settings.setSkipButtonsEnabled(!skip.showButtons) } }
        }
        item(key = "skip-auto") {
            ValueItem(stringResource(R.string.tv_psettings_skip_auto), onOff(skip.autoSkip)) { scope.launch { settings.setAutoSkipEnabled(!skip.autoSkip) } }
        }
        // Tür kapalıysa o bölüm için ne düğme gösterilir ne otomatik atlanır.
        SkipKinds.forEach { (category, label) ->
            item(key = "skip-${category.name}") {
                ValueItem(stringResource(label), onOff(skip.isEnabled(category))) {
                    scope.launch { settings.setSkipCategoryEnabled(category, !skip.isEnabled(category)) }
                }
            }
        }
    }
    InitialFocus(first) { firstFocused }
}

/** Bir ayar satırı: solda ad, sağda geçerli değer; OK sıradaki değere geçirir. */
@Composable
private fun ValueItem(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(label) },
        trailingContent = { Text(value, color = Accent, fontSize = TvText.Row) },
        modifier = modifier,
    )
}

/**
 * Canlı önizleme: oynatıcıdakiyle aynı Media3 SubtitleView ve biçimlendirme. Metin boyutu ekran yüksekliğine oranlanır,
 * böylece küçük kutuda yazı oynatıcıdaki gerçek boyutuyla görünür.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    val scale = LocalConfiguration.current.screenHeightDp / PreviewHeight.value
    val sample = stringResource(R.string.tv_psettings_preview_sample)
    AndroidView(
        factory = { SubtitleView(it) },
        update = { view ->
            SubtitleStyling.apply(view, style, scale)
            view.setCues(listOf(Cue.Builder().setText(sample).build()))
        },
        // Arka plan video gibi siyah: beyaz/sarı yazı ve "arka plan yok" doğru görünsün.
        modifier = Modifier.fillMaxWidth().height(PreviewHeight).clip(RoundedCornerShape(10.dp)).background(Color.Black),
    )
}

private val PreviewHeight = 140.dp

private val SkipKinds = listOf(
    SkipCategory.Intro to R.string.tv_psettings_skip_intro,
    SkipCategory.Recap to R.string.tv_psettings_skip_recap,
    SkipCategory.Credits to R.string.tv_psettings_skip_credits,
    SkipCategory.Preview to R.string.tv_psettings_skip_preview,
)

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1) % size]

@Composable
private fun onOff(on: Boolean) = if (on) stringResource(R.string.tv_on) else stringResource(R.string.tv_off)

@Composable
private fun SubtitleSize.label() = when (this) {
    SubtitleSize.Small -> stringResource(R.string.tv_psettings_size_small)
    SubtitleSize.Normal -> stringResource(R.string.tv_psettings_size_normal)
    SubtitleSize.Large -> stringResource(R.string.tv_psettings_size_large)
    SubtitleSize.ExtraLarge -> stringResource(R.string.tv_psettings_size_xlarge)
}

@Composable
private fun SubtitleColor.label() = when (this) {
    SubtitleColor.White -> stringResource(R.string.tv_psettings_color_white)
    SubtitleColor.Yellow -> stringResource(R.string.tv_psettings_color_yellow)
}

@Composable
private fun SubtitleBackground.label() = when (this) {
    SubtitleBackground.None -> stringResource(R.string.tv_psettings_bg_none)
    SubtitleBackground.Translucent -> stringResource(R.string.tv_psettings_bg_translucent)
    SubtitleBackground.Black -> stringResource(R.string.tv_psettings_bg_black)
}
