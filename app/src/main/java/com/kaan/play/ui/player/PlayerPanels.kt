package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kaan.play.ui.tv.focusRing
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kaan.play.R
import com.kaan.play.data.player.PlaybackSpeeds
import com.kaan.play.data.stream.AudioFilter
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SizeText
import com.kaan.play.data.stream.SourceRanking
import com.kaan.play.data.stream.SourceSort
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Panelin iki sütunlu çerçevesi: başlık, gövde (sütunlar), altta İptal / Uygula. Geri tuşu ve dışarı dokunmak İptal'dir. */
@Composable
private fun PanelFrame(
    title: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
    body: @Composable RowScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f).navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                header()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { body() }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApply) { Text(stringResource(R.string.panel_apply)) }
                }
            }
        }
    }
}

@Composable
private fun PanelColumn(title: String, modifier: Modifier, content: LazyListScope.() -> Unit) {
    Column(modifier.fillMaxHeight().focusGroup()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
private fun OptionRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp), content = content)
    }
}

@Composable
private fun SoftText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, emphasized: Boolean = false, busy: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().selectable(selected = false, role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = if (emphasized) FontWeight.Bold else null,
            modifier = Modifier.weight(1f),
        )
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun resolutionText(stream: PlayableStream): String? = when (val h = stream.resolution) {
    null -> null
    2160 -> stringResource(R.string.quality_4k)
    else -> stringResource(R.string.quality_p, h)
}

// ---------------------------------------------------------------------------------------------------------------------
// Kaynak ve altyazı paneli
// ---------------------------------------------------------------------------------------------------------------------

/** Altyazı seçimi (Uygula'ya kadar bekleyen): değişmedi / kapalı / bir parça. */
private sealed interface SubChoice {
    data object Keep : SubChoice

    data object Off : SubChoice

    data class Item(val item: SubtitleItem) : SubChoice
}

/**
 * "Kaynak" düğmesinin açtığı iki sütunlu panel. **Sol:** kaynaklar eklenti adına göre gruplu (Dublajlı/Altyazılı etiketi, çözünürlük,
 * dosya boyutu, torrent/doğrudan, çalan işaretli); üstte sıralama (Önerilen / Boyut / Çözünürlük) ve Tümü/Dublajlı/Altyazılı süzgeci;
 * yüklenen kaynaklar listeye canlı eklenir. **Sağ:** altyazılar (Altyazı yok, videonun içindekiler dil adıyla Türkçe öncelikli,
 * ardından Dosyadan yükle / İnternetten yükle / İlk kullanılabiliri yükle). Seçimler **Uygula**'ya basınca geçerli olur; İptal'de hiçbir
 * şey değişmez. [coordinator] null ise (indirilmiş kopya) sol sütunda yalnızca çalan kopya görünür ve çevrimiçi eylemler yoktur.
 */
@Composable
fun SourcePanel(
    coordinator: SourceCoordinator?,
    offlineStream: PlayableStream?,
    subtitles: SubtitleController,
    embeddedDub: Boolean,
    online: Boolean,
    onAddFile: () -> Unit,
    onAppearance: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uiLanguage = AppLanguage.effective().iso
    val locale = Locale.forLanguageTag(uiLanguage)
    val scope = rememberCoroutineScope()
    // Çalan kaynak: yalnızca oynuyor ya da açılıyorsa (tüm kaynaklar başarısızken son denenen "çalıyor" sayılmaz).
    val current = if (coordinator == null) offlineStream else coordinator.current?.takeIf {
        coordinator.phase == SourcePhase.Playing || coordinator.phase == SourcePhase.Starting
    }
    var staged by remember { mutableStateOf(current) }
    var subChoice by remember { mutableStateOf<SubChoice>(SubChoice.Keep) }
    var sort by remember { mutableStateOf(SourceSort.Recommended) }
    var filter by remember { mutableStateOf(AudioFilter.All) }
    var onlineView by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val noneFound = stringResource(R.string.subs_first_none)
    val otherLanguage = stringResource(R.string.subtitle_language_other)

    // Panel açılınca kaynaklar (henüz yüklenmediyse) yüklenir.
    LaunchedEffect(coordinator) { coordinator?.ensureSources() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }

    val effectiveSub: SubtitleItem? = when (val c = subChoice) {
        SubChoice.Keep -> subtitles.selected
        SubChoice.Off -> null
        is SubChoice.Item -> c.item
    }

    PanelFrame(
        title = stringResource(R.string.panel_title_sources),
        onDismiss = onDismiss,
        onApply = {
            onDismiss()
            val pick = staged
            if (coordinator != null && pick != null && pick.url != current?.url) coordinator.pick(pick)
            when (val c = subChoice) {
                SubChoice.Keep -> Unit
                SubChoice.Off -> subtitles.choose(null)
                is SubChoice.Item -> if (c.item != subtitles.selected) subtitles.choose(c.item)
            }
        },
        header = {
            if (coordinator != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterPill(stringResource(R.string.sort_recommended), sort == SourceSort.Recommended, onClick = { sort = SourceSort.Recommended })
                    FilterPill(stringResource(R.string.sort_size), sort == SourceSort.SizeDesc, onClick = { sort = SourceSort.SizeDesc })
                    FilterPill(stringResource(R.string.sort_resolution), sort == SourceSort.ResolutionDesc, onClick = { sort = SourceSort.ResolutionDesc })
                    Spacer(Modifier.width(12.dp))
                    FilterPill(stringResource(R.string.sources_all), filter == AudioFilter.All, onClick = { filter = AudioFilter.All })
                    FilterPill(stringResource(R.string.tag_dubbed), filter == AudioFilter.Dubbed, onClick = { filter = AudioFilter.Dubbed })
                    FilterPill(stringResource(R.string.tag_subbed), filter == AudioFilter.Subbed, onClick = { filter = AudioFilter.Subbed })
                    if (coordinator.loading) {
                        CircularProgressIndicator(Modifier.padding(start = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
    ) {
        // ---- sol sütun: kaynaklar ----
        val focus = rememberInitialFocus()
        PanelColumn(stringResource(R.string.panel_sources), Modifier.weight(1.25f)) {
            if (coordinator == null) {
                offlineStream?.let { s ->
                    item { SourceRow(s, selected = true, playing = true, embeddedDub = embeddedDub, sizeText = SizeText.format(s.sizeBytes, locale), onClick = {}, modifier = Modifier.focusRequester(focus)) }
                }
            } else {
                val ctx = coordinator.ctx
                val shown = SourceRanking.filtered(SourceRanking.sorted(coordinator.streams, sort, ctx), filter)
                val groups = shown.groupBy { it.source.addonId }
                groups.forEach { (_, rows) ->
                    item(key = "h-${rows.first().source.addonId}") {
                        Text(
                            rows.first().source.addonName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(rows, key = { it.url }) { s ->
                        SourceRow(
                            s,
                            selected = staged?.url == s.url,
                            playing = current?.url == s.url,
                            embeddedDub = embeddedDub,
                            sizeText = if (ctx.isLive) null else SizeText.format(SourceRanking.sizeOf(s, ctx.sizes), locale),
                            onClick = { staged = s },
                            modifier = if (staged?.url == s.url) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
                val pending = coordinator.asked.filter { it.addonId !in coordinator.answered }
                if (coordinator.loading) {
                    item(key = "loading") { SoftText(stringResource(R.string.sources_checking_more) + if (pending.isNotEmpty()) " (${pending.joinToString { it.addonName }})" else "") }
                }
                val uniqueFailed = coordinator.failedSources.distinctBy { it.addonId }
                itemsIndexed(uniqueFailed, key = { index, item -> "f-${item.addonId}_$index" }) { _, it ->
                    SoftText(stringResource(R.string.sources_failed, it.addonName))
                }
                if (!coordinator.loading && shown.isEmpty()) item(key = "empty") { SoftText(stringResource(R.string.sources_empty)) }
            }
        }

        // ---- sağ sütun: altyazılar ----
        PanelColumn(stringResource(R.string.panel_subtitles), Modifier.weight(1f)) {
            val preferred = subtitles.menuLanguages.ifEmpty { listOf(uiLanguage, "en").distinct() }
            val offline = !online
            val nonOnline = subtitles.items.filter { !(it is SubtitleItem.External && it.track.kind == SubtitleKind.Online) }
            val nothing = nonOnline.isEmpty()

            if (onlineView && online) {
                item(key = "back") {
                    Row(Modifier.fillMaxWidth().selectable(false, role = Role.Button, onClick = { onlineView = false }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.subs_load_online), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                val results = subtitles.onlineItems()
                if (subtitles.searching) item(key = "searching") { SoftText(stringResource(R.string.subtitle_searching)) }
                if (results.isEmpty() && !subtitles.searching) {
                    item(key = "none") {
                        SoftText(stringResource(if (subtitles.failedSources.isNotEmpty()) R.string.subs_online_failed else R.string.subtitle_online_none))
                    }
                }
                items(results, key = { it.track.url }) { item ->
                    val language = item.track.language?.let { SubtitleLanguages.displayName(it, uiLanguage) } ?: otherLanguage
                    OptionRow(selected = item == effectiveSub, onClick = { subChoice = SubChoice.Item(item) }) {
                        Text(language, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(item.track.origin, item.track.label).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                // Hiç altyazı yoksa çevrimiçi arama satırı öne çıkar.
                if (nothing && online) {
                    item(key = "search-first") {
                        ActionRow(
                            stringResource(R.string.subs_search_online),
                            onClick = { onlineView = true; subtitles.searchOnline() },
                            emphasized = true,
                            busy = subtitles.searching,
                        )
                    }
                }
                item(key = "off") {
                    OptionRow(selected = effectiveSub == null, onClick = { subChoice = SubChoice.Off }) {
                        Text(stringResource(R.string.subs_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                // Seçili altyazı çevrimiçi (ya da dosyadan) ise listede görünür kalır: aksi hâlde hiçbir satır seçili görünmezdi.
                val hiddenSelected = effectiveSub?.takeIf { it !in nonOnline }
                if (hiddenSelected is SubtitleItem.External) {
                    item(key = "selected-online") {
                        val language = hiddenSelected.track.language?.let { SubtitleLanguages.displayName(it, uiLanguage) } ?: otherLanguage
                        OptionRow(selected = true, onClick = { subChoice = SubChoice.Item(hiddenSelected) }) {
                            Text(language, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(hiddenSelected.track.origin.takeIf { hiddenSelected.track.kind != SubtitleKind.Local }, hiddenSelected.track.label)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val groups = SubtitleSelection.groups(
                    items = nonOnline,
                    preferred = preferred,
                    nameOf = { SubtitleLanguages.displayName(it, uiLanguage) },
                    unknownName = otherLanguage,
                )
                groups.forEach { group ->
                    items(group.items, key = { subtitleKey(it) }) { item ->
                        OptionRow(selected = item == effectiveSub, onClick = { subChoice = SubChoice.Item(item) }) {
                            val label = when (item) {
                                is SubtitleItem.Embedded -> stringResource(R.string.subs_embedded_row, group.name)
                                is SubtitleItem.External ->
                                    if (item.track.kind == SubtitleKind.Translated) {
                                        stringResource(R.string.subtitle_auto_translated, group.name)
                                    } else {
                                        val displayOrigin = item.track.label?.let { "${item.track.origin} ($it)" } ?: item.track.origin
                                        stringResource(R.string.dot_join, group.name, displayOrigin)
                                    }
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (subtitles.failedSources.isNotEmpty()) item(key = "failed") { SoftText(stringResource(R.string.subs_online_failed)) }
                if (message != null) item(key = "message") { SoftText(message.orEmpty()) }
                item(key = "div") { HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp)) }
                item(key = "file") { ActionRow(stringResource(R.string.subs_load_file), onClick = onAddFile) }
                if (online) {
                    item(key = "online") {
                        ActionRow(
                            stringResource(R.string.subs_load_online),
                            onClick = { onlineView = true; if (subtitles.onlineItems().isEmpty()) subtitles.searchOnline() },
                        )
                    }
                    item(key = "first") {
                        ActionRow(
                            stringResource(R.string.subs_load_first),
                            busy = busy,
                            onClick = {
                                if (busy) return@ActionRow
                                busy = true
                                scope.launch {
                                    if (subtitles.onlineItems().isEmpty() && !subtitles.searching) subtitles.searchOnline()
                                    // Arama sürüyorsa bitmesini (en çok ~15 sn) bekle.
                                    var waited = 0
                                    while (subtitles.searching && waited < 15_000) {
                                        delay(200)
                                        waited += 200
                                    }
                                    var found: SubtitleItem.External? = null
                                    for (language in preferred) {
                                        found = subtitles.firstUsableOnline(language)
                                        if (found != null) break
                                    }
                                    if (found != null) subChoice = SubChoice.Item(found) else message = noneFound
                                    busy = false
                                }
                            },
                        )
                    }
                }
                item(key = "appearance") { ActionRow(stringResource(R.string.player_subtitle_appearance), onClick = onAppearance) }
                if (offline) item(key = "pad") { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

private fun subtitleKey(item: SubtitleItem): String = when (item) {
    is SubtitleItem.Embedded -> "e-${item.index}"
    is SubtitleItem.External -> "x-${item.track.url}"
}

@Composable
private fun SourceRow(
    stream: PlayableStream,
    selected: Boolean,
    playing: Boolean,
    embeddedDub: Boolean,
    sizeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = when {
        playing && embeddedDub -> stringResource(R.string.tag_dubbed_embedded)
        stream.dubbed -> stringResource(R.string.tag_dubbed)
        else -> stringResource(R.string.tag_subbed)
    }
    val kind = stringResource(if (TorrentLinks.isMagnet(stream.url)) R.string.tag_torrent else R.string.tag_direct)
    val details = listOfNotNull(resolutionText(stream), sizeText, kind).reduce { acc, part -> stringResource(R.string.dot_join, acc, part) }
    OptionRow(selected = selected, onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tag,
                style = MaterialTheme.typography.titleMedium,
                color = if (stream.dubbed || (playing && embeddedDub)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (playing) {
                Icon(PlayIcons.Check, contentDescription = stringResource(R.string.panel_playing), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Parçalar paneli
// ---------------------------------------------------------------------------------------------------------------------

/**
 * "Parçalar" düğmesinin açtığı iki sütunlu panel: **Video** (oynatıcının sunduğu çözünürlük/bitrate seçenekleri, "Otomatik" dahil) ve
 * **Ses** (dil • kanal • codec, örn. "Türkçe • Stereo • AAC"). Parça yoksa sütunda "Seçenek yok". Seçimler **Uygula**'da geçerli olur.
 */
@Composable
fun TracksPanel(
    controller: PlaybackController,
    tracksVersion: Int,
    onAudioChosen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiLanguage = AppLanguage.effective().iso
    val locale = Locale.forLanguageTag(uiLanguage)
    val video = remember(tracksVersion) { controller.videoOptions() }
    val audio = remember(tracksVersion) { controller.audioTracks() }
    val pinned = remember(tracksVersion) { controller.videoPinned() }
    val currentSpeed = controller.speed
    // Bekleyen seçimler: video null = Otomatik; ses = parça sırası; hız = float.
    var stagedVideo by remember { mutableStateOf(if (pinned) video.firstOrNull { it.selected } else null) }
    var stagedAudio by remember { mutableStateOf(audio.firstOrNull { it.selected }?.index) }
    var stagedSpeed by remember { mutableStateOf(currentSpeed) }
    val focus = rememberInitialFocus()

    PanelFrame(
        title = stringResource(R.string.tracks_title),
        onDismiss = onDismiss,
        onApply = {
            val currentPinned = if (pinned) video.firstOrNull { it.selected } else null
            if (stagedVideo != currentPinned) controller.selectVideo(stagedVideo)
            val chosen = stagedAudio
            if (chosen != null && chosen != audio.firstOrNull { it.selected }?.index) onAudioChosen(chosen)
            if (stagedSpeed != currentSpeed) {
                controller.speed = stagedSpeed
            }
            onDismiss()
        },
    ) {
        PanelColumn(stringResource(R.string.tracks_video), Modifier.weight(1f)) {
            if (video.isEmpty()) {
                item(key = "none") { SoftText(stringResource(R.string.tracks_none)) }
            } else {
                item(key = "auto") {
                    OptionRow(selected = stagedVideo == null, onClick = { stagedVideo = null }, modifier = if (stagedVideo == null) Modifier.focusRequester(focus) else Modifier) {
                        Text(stringResource(R.string.tracks_auto), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(video, key = { "v-${it.group}-${it.track}" }) { option ->
                    val label = TrackLabels.video(option.height, option.bitrate, locale).ifBlank { "${option.width}×${option.height}" }
                    OptionRow(
                        selected = stagedVideo?.let { it.group == option.group && it.track == option.track } == true,
                        onClick = { stagedVideo = option },
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        SizeText.dimensions(option.width, option.height)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        PanelColumn(stringResource(R.string.player_audio), Modifier.weight(1f)) {
            if (audio.isEmpty()) {
                item(key = "none") { SoftText(stringResource(R.string.tracks_none)) }
            } else {
                items(audio, key = { "a-${it.index}" }) { track ->
                    val name = track.language?.let(SubtitleLanguages::normalize)?.let { SubtitleLanguages.displayName(it, uiLanguage) }
                    val fallback = stringResource(R.string.player_audio_track, track.index + 1)
                    OptionRow(
                        selected = stagedAudio == track.index,
                        onClick = { stagedAudio = track.index },
                        modifier = if (video.isEmpty() && stagedAudio == track.index) Modifier.focusRequester(focus) else Modifier,
                    ) {
                        Text(
                            TrackLabels.audio(name, track.label, track.channels, track.mime, fallback),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        PanelColumn(stringResource(R.string.player_speed), Modifier.weight(1f)) {
            items(PlaybackSpeeds.ALL, key = { "speed-$it" }) { sp ->
                val label = if (sp == PlaybackSpeeds.DEFAULT) {
                    stringResource(R.string.player_speed_normal_with_ratio)
                } else {
                    PlaybackSpeeds.format(sp)
                }
                OptionRow(
                    selected = stagedSpeed == sp,
                    onClick = { stagedSpeed = sp },
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
