package com.kaan.play.ui.player

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kaan.play.R
import com.kaan.play.data.subtitle.SubtitleGroup
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleOffset
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.rememberInitialFocus
import java.util.Locale

/**
 * Oynatıcının altyazı menüsü: "Kapalı", dile göre gruplu seçenekler (videonun içindekiler, akışın, dosyadan eklenenler ve
 * addon'lar; satırda kaynağın adı), "Çevrimiçi altyazılar" (tercih edilen dil, sonra İngilizce), "Dosyadan altyazı ekle",
 * birden çok ses parçası varsa "Ses", "Görünüm" ve (harici
 * bir altyazı açıkken) "Senkron". Grup başlığına dokunmak/Tamam açar-kapatır; uygulama dilinin grubu ve seçili olanın grubu
 * açık başlar (yüzlerce satırlık listelerde kumandayla gezmek için).
 */
@Composable
fun SubtitleMenu(
    subtitles: SubtitleController,
    /** Videonun ses parçaları; birden çoksa menüde "Ses" bölümü çıkar. */
    audio: List<AudioTrack>,
    onAudio: (Int) -> Unit,
    /** Çevrimiçi arama yapılıyor mu (indirilmiş kopyada yapılmaz, bölüm gizlenir). */
    online: Boolean,
    onAddFile: () -> Unit,
    onAppearance: () -> Unit,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uiLanguage = AppLanguage.effective().iso
    val otherName = stringResource(R.string.subtitle_language_other)
    val selected = subtitles.selected
    val preferred = subtitles.menuLanguages.ifEmpty { listOf(uiLanguage, "en").distinct() }
    val onlineItems = remember(subtitles.items) {
        subtitles.items.filter { it is SubtitleItem.External && it.track.kind == SubtitleKind.Online }
    }
    val groups = remember(subtitles.items, preferred, uiLanguage, otherName) {
        SubtitleSelection.groups(
            items = subtitles.items.filter { it !in onlineItems },
            preferred = preferred,
            nameOf = { SubtitleLanguages.displayName(it, uiLanguage) },
            unknownName = otherName,
        )
    }
    // Varsayılan açık gruplar; kullanıcının açıp kapattıkları bunun tersine çevrilir.
    val selectedOnline = selected != null && selected in onlineItems
    val defaultOpen = setOfNotNull(
        groups.firstOrNull()?.key(),
        selected?.takeIf { !selectedOnline }?.let { it.language ?: UNKNOWN_KEY },
        ONLINE_KEY,
    )
    var flipped by remember { mutableStateOf(emptySet<String>()) }
    val focus = rememberInitialFocus()
    val canSync = selected is SubtitleItem.External && subtitles.status == SubtitleStatus.Idle

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.player_subtitles), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                OptionRow(
                    selected = selected == null,
                    label = stringResource(R.string.player_subtitles_off),
                    indented = false,
                    modifier = if (selected == null) Modifier.focusRequester(focus) else Modifier,
                    onClick = { subtitles.choose(null); onDismiss() },
                )
                if (subtitles.searching) SoftLine(stringResource(R.string.subtitle_searching))

                groups.forEach { group ->
                    val key = group.key()
                    val open = (key in defaultOpen) != (key in flipped)
                    GroupHeader(
                        name = group.name,
                        count = group.items.size,
                        open = open,
                        highlighted = selected != null && selected in group.items,
                        onClick = { flipped = if (key in flipped) flipped - key else flipped + key },
                    )
                    if (open) {
                        val labels = rowLabels(group)
                        group.items.forEachIndexed { i, item ->
                            OptionRow(
                                selected = item == selected,
                                label = labels[i],
                                indented = true,
                                modifier = if (item == selected) Modifier.focusRequester(focus) else Modifier,
                                onClick = { subtitles.choose(item); onDismiss() },
                            )
                        }
                    }
                }
                subtitles.failedSources.forEach { SoftLine(stringResource(R.string.sources_failed, it.addonName)) }

                if (online) {
                    val open = (ONLINE_KEY in defaultOpen) != (ONLINE_KEY in flipped)
                    GroupHeader(
                        name = stringResource(R.string.subtitle_online),
                        count = onlineItems.size,
                        open = open,
                        highlighted = selectedOnline,
                        onClick = { flipped = if (ONLINE_KEY in flipped) flipped - ONLINE_KEY else flipped + ONLINE_KEY },
                    )
                    if (open) {
                        if (onlineItems.isEmpty() && !subtitles.searching) SoftLine(stringResource(R.string.subtitle_online_none))
                        onlineItems.forEach { item ->
                            val track = (item as SubtitleItem.External).track
                            val language = track.language?.let { SubtitleLanguages.displayName(it, uiLanguage) } ?: otherName
                            OptionRow(
                                selected = item == selected,
                                label = track.label?.let { stringResource(R.string.dot_join, language, it) } ?: language,
                                indented = true,
                                modifier = if (item == selected) Modifier.focusRequester(focus) else Modifier,
                                onClick = { subtitles.choose(item); onDismiss() },
                            )
                        }
                    }
                }
                ActionRow(stringResource(R.string.subtitle_add_file), onAddFile)

                if (audio.size > 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.player_audio),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    audio.forEach { track ->
                        val name = track.language?.let(SubtitleLanguages::normalize)?.let { SubtitleLanguages.displayName(it, uiLanguage) }
                        val label = listOfNotNull(name, track.label?.takeIf(String::isNotBlank)).distinct()
                        OptionRow(
                            selected = track.selected,
                            label = when (label.size) {
                                0 -> stringResource(R.string.player_audio_track, track.index + 1)
                                1 -> label[0]
                                else -> stringResource(R.string.dot_join, label[0], label[1])
                            },
                            indented = true,
                            modifier = Modifier,
                            onClick = { onAudio(track.index); onDismiss() },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                ActionRow(stringResource(R.string.player_subtitle_appearance), onAppearance)
                if (canSync) ActionRow(stringResource(R.string.subtitle_sync), onSync, trailing = offsetText(subtitles.offsetMs).takeIf { subtitles.offsetMs != 0L })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private const val UNKNOWN_KEY = "?"
private const val ONLINE_KEY = "online"

private fun SubtitleGroup.key(): String = language ?: UNKNOWN_KEY

/**
 * Satır adları: videonun içindekiler "Videonun içinde" (parçanın kendi adı varsa "Videonun içinde · Forced"), diğerleri kaynak
 * adı; aynı addan birden çoksa "Ad · 2".
 */
@Composable
private fun rowLabels(group: SubtitleGroup): List<String> {
    val seen = HashMap<String, Int>()
    val embedded = stringResource(R.string.subtitle_embedded)
    val joined = R.string.dot_join
    val bases = group.items.map {
        when (it) {
            is SubtitleItem.External ->
                if (it.track.kind == SubtitleKind.Translated) {
                    stringResource(R.string.subtitle_auto_translated, group.name)
                } else {
                    it.track.label?.let { lbl -> "${it.track.origin} ($lbl)" } ?: it.track.origin
                }
            is SubtitleItem.Embedded -> it.label?.let { label -> stringResource(joined, embedded, label) } ?: embedded
        }
    }
    return bases.map { base ->
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        if (n > 1) stringResource(joined, base, n.toString()) else base
    }
}

@Composable
private fun OptionRow(selected: Boolean, label: String, indented: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (indented) 16.dp else 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun GroupHeader(name: String, count: Int, open: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Icon(
            imageVector = PlayIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (open) 0f else -90f),
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SoftLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** "+0,5 sn" / "-1,2 sn" (arayüz dilinin ondalık ayırıcısıyla); 0 için "0,0 sn". */
@Composable
internal fun offsetText(offsetMs: Long): String = stringResource(R.string.subtitle_sync_value, signedSeconds(offsetMs))

@Composable
private fun signedSeconds(offsetMs: Long): String {
    val locale = Locale.forLanguageTag(AppLanguage.effective().iso)
    val seconds = offsetMs / 1000.0
    return if (offsetMs == 0L) String.format(locale, "%.1f", 0.0) else String.format(locale, "%+.1f", seconds)
}

/**
 * Elle senkron paneli: ekranın üstünde, videoyu karartmadan durur (altyazının değişimi görülsün). Büyük adım ±0,5 sn, ince adım
 * ±0,1 sn, sıfırla; geçerli kaydırma değeri büyük yazılır. Her düğme anında uygulanır ve kalıcı saklanır. Düğmeler
 * kumandayla (Sol/Sağ gezer, Tamam basar) kullanılır.
 */
@Composable
fun SubtitleSyncPanel(subtitles: SubtitleController, onDismiss: () -> Unit) {
    val focus = rememberInitialFocus()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.padding(top = 24.dp).widthIn(max = 560.dp).fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.subtitle_sync), style = MaterialTheme.typography.titleMedium)
                Text(offsetText(subtitles.offsetMs), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.subtitle_sync_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton(-SubtitleOffset.COARSE_MS, subtitles, Modifier.weight(1f).focusRequester(focus))
                    StepButton(-SubtitleOffset.FINE_MS, subtitles, Modifier.weight(1f))
                    StepButton(SubtitleOffset.FINE_MS, subtitles, Modifier.weight(1f))
                    StepButton(SubtitleOffset.COARSE_MS, subtitles, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = subtitles::resetOffset) { Text(stringResource(R.string.subtitle_sync_reset)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
                }
            }
        }
    }
}

@Composable
private fun StepButton(deltaMs: Long, subtitles: SubtitleController, modifier: Modifier) {
    val locale = Locale.forLanguageTag(AppLanguage.effective().iso)
    OutlinedButton(onClick = { subtitles.adjustOffset(deltaMs) }, modifier = modifier) {
        Text(String.format(locale, "%+.1f", deltaMs / 1000.0))
    }
}
