package com.kaan.play.ui.sources

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.data.download.DownloadBadge
import com.kaan.play.data.download.DownloadPick
import com.kaan.play.data.download.DownloadRules
import com.kaan.play.data.download.Probe
import com.kaan.play.data.download.StartResult
import com.kaan.play.data.stream.PlayTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.kaan.play.ui.tv.focusRing
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.TorrentLinks

import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Kaynak seçimi: alttan açılan liste. Addon'lar paralel sorulur, kaynaklar geldikçe listeye girer; her addon kendi
 * başlığı altında toplanır (cevabı sürenlerde "Alınıyor…"). Üstte yenile düğmesi ve (birden çok addon varsa) addon
 * süzgeci vardır. En uygun kaynak işaretlidir ve herhangi bir satıra tek dokunuş seçer. Satırda addon adı ile
 * "kalite · boyut · dil · altyazı" bulunur (codec/HDR yok).
 *
 * [downloadMode] (Detay'daki indir düğmesi): yalnızca indirilebilir doğrudan kaynaklar ([SourceProbe] ile denetlenir,
 * torrent yok) tek liste halinde, önerilen en üstte ve seçili gelir; satıra dokunmak indirir, indirme başlayınca liste kapanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(target: PlayTarget, onDismiss: () -> Unit, onPicked: (PlayableStream) -> Unit, downloadMode: Boolean = false) {
    val request = target.request
    val container = rememberContainer()
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf<Long?>(null) }

    // ---- indirme ----
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by container.downloads.rows.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadOfContent = downloads.firstOrNull {
        it.tmdbId == request.tmdbId && it.type == request.type && it.season == (request.season ?: 0) && it.episode == (request.episode ?: 0)
    }
    val starting = remember { mutableStateListOf<String>() }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var pendingDownload by remember { mutableStateOf<PlayableStream?>(null) }

    fun beginDownload(stream: PlayableStream, notificationsOff: Boolean) {
        if (stream.url in starting) return
        starting += stream.url
        scope.launch {
            val result = container.downloads.start(target, stream)
            starting -= stream.url
            if (downloadMode && result is StartResult.Started) {
                onDismiss()
                return@launch
            }
            notice = when (result) {
                is StartResult.Started ->
                    Notice(if (notificationsOff) R.string.download_notifications_off else R.string.download_started)
                is StartResult.AlreadyDownloaded -> Notice(R.string.download_already)
                is StartResult.NotDownloadable ->
                    Notice(if (result.reason == Probe.Reason.Network) R.string.download_unreachable else R.string.download_unavailable)
                is StartResult.NoSpace -> Notice(
                    R.string.download_no_space,
                    Formatter.formatShortFileSize(context, result.neededBytes),
                    Formatter.formatShortFileSize(context, result.freeBytes),
                )
            }
        }
    }

    // Android 13+: bildirim izni ilk indirmede istenir. Reddedilse de indirme başlar (yalnızca bildirimde ilerleme görünmez).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingDownload?.let { beginDownload(it, notificationsOff = !granted) }
        pendingDownload = null
    }
    fun onDownloadClicked(stream: PlayableStream) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = stream
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginDownload(stream, notificationsOff = false)
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }
    val state by produceState(SourcesState(), request, refresh) {
        value = SourcesState()
        // Yenile (refresh > 0) eklenti önbelleğini yok sayar: sonuçlar yeniden çalıştırılarak alınır.
        val asked = request.copy(refresh = refresh > 0)
        value = value.copy(asked = container.streams.sources(asked))
        container.streams.streams(asked)
            .onCompletion { cause -> if (cause == null) value = value.finished() }
            .collect { value += it }
    }
    // İndirme modu: adaylar geldikçe arka planda yoklanır (aynı adres bir kez); sonuç adres → indirilebilir mi.
    val probed = remember(request, refresh) { mutableStateMapOf<String, Boolean>() }
    if (downloadMode) {
        LaunchedEffect(request, refresh) {
            val started = HashSet<String>()
            val gate = Semaphore(PROBE_PARALLEL)
            snapshotFlow { DownloadPick.candidates(state.streams) }.collect { list ->
                list.filter { started.add(it.url) }.forEach { stream ->
                    launch {
                        val ok = gate.withPermit {
                            try {
                                container.sourceProbe.probe(stream.url, stream.headers) is Probe.Ok
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                false
                            }
                        }
                        probed[stream.url] = ok
                    }
                }
            }
        }
    }
    val downloadable = if (downloadMode) DownloadPick.visible(state.streams, probed) else emptyList()
    val checking = downloadMode && (state.loading || DownloadPick.pending(state.streams, probed))

    val addons = state.addons
    // Yenilemede ya da süzülen addon artık listede yokken "Tümü"ne dön.
    val activeFilter = filter?.takeIf { id -> addons.any { it.addonId == id } }
    val visible = addons.filter { activeFilter == null || it.addonId == activeFilter }
    val visibleStreams = visible.flatMap { state.streamsOf(it.addonId) }

    // Kumanda: kaynaklar gelince önerilen satır (yoksa ilk satır) odak alır; Tamam seçer.
    val focus = rememberInitialFocus(enabled = if (downloadMode) downloadable.isNotEmpty() else state.streams.isNotEmpty())
    val focusStream = state.recommended?.takeIf { it in visibleStreams } ?: visibleStreams.firstOrNull()
    val pendingVisible = state.pending.filter { activeFilter == null || it.addonId == activeFilter }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        // Sürükleme tutamacı yalnızca dokunmada: kumandada odak ona takılmasın, doğrudan kaynak satırına gelsin.
        dragHandle = if (LocalInputModeManager.current.inputMode == InputMode.Touch) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (downloadMode) R.string.download_pick_title else R.string.sources_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { filter = null; refresh++ }) {
                    Icon(PlayIcons.Refresh, contentDescription = stringResource(R.string.sources_refresh))
                }
            }
            notice?.let { SoftLine(stringResource(it.res, *it.args.toTypedArray())) }
            if (!downloadMode && addons.size >= 2) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") { FilterPill(stringResource(R.string.sources_all), activeFilter == null, onClick = { filter = null }) }
                    items(addons, key = { it.addonId }) { addon ->
                        FilterPill(addon.addonName, activeFilter == addon.addonId, onClick = { filter = addon.addonId })
                    }
                }
            }
            // Yükleme çubuğu kalkınca liste zıplamasın diye yer her zaman ayrılır.
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(4.dp)) {
                if (state.loading || checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (downloadMode) {
                DownloadPickList(
                    streams = downloadable,
                    checking = checking,
                    isEpisode = request.episode != null,
                    badgeOf = { stream -> DownloadRules.badge(downloadOfContent, stream.url, starting = stream.url in starting) },
                    fractionOf = { stream ->
                        downloadOfContent?.takeIf { it.sourceUrl == stream.url }
                            ?.let { DownloadRules.fraction(it.downloadedBytes, it.totalBytes) }
                    },
                    onDownload = ::onDownloadClicked,
                    focus = focus,
                )
                return@Column
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(min = 300.dp, max = 460.dp)) {
                visible.forEach { addon ->
                    val own = state.streamsOf(addon.addonId)
                    val waiting = pendingVisible.any { it.addonId == addon.addonId }
                    if (own.isEmpty() && !waiting) return@forEach
                    item(key = "h-${addon.addonId}") { AddonHeader(addon.addonName, waiting) }
                    items(own) { stream ->
                        SourceRow(
                            stream,
                            isRecommended = stream == state.recommended,
                            badge = DownloadRules.badge(downloadOfContent, stream.url, starting = stream.url in starting),
                            downloadFraction = downloadOfContent?.takeIf { it.sourceUrl == stream.url }
                                ?.let { DownloadRules.fraction(it.downloadedBytes, it.totalBytes) },
                            onClick = { onPicked(stream) },
                            onDownload = { onDownloadClicked(stream) },
                            modifier = if (stream == focusStream) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
                if (pendingVisible.isNotEmpty() && visibleStreams.isNotEmpty()) {
                    item(key = "more") { SoftLine(stringResource(R.string.sources_checking_more)) }
                }
                items(state.failed.filter { activeFilter == null || it.addonId == activeFilter }) { source ->
                    SoftLine(stringResource(R.string.sources_failed, source.addonName))
                }
                if (!state.loading && visibleStreams.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.sources_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Addon başlığı; cevabı sürüyorsa altında "Alınıyor…". */
@Composable
private fun AddonHeader(name: String, waiting: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (waiting) {
            Text(
                text = stringResource(R.string.sources_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SoftLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Kısa süreli, biçim argümanlı bildirim satırı (indirme sonuçları). */
private class Notice(@androidx.annotation.StringRes val res: Int, vararg args: Any) {
    val args: List<Any> = args.toList()
}

private const val NOTICE_MS = 5_000L

/** İndirme modunda aynı anda yoklanan en çok kaynak. */
private const val PROBE_PARALLEL = 4

/**
 * İndirme modunun listesi: önerilen (ilk satır) seçili ve odakta; satıra dokunmak indirir. Yoklama sürerken altta
 * "denetleniyor", hiçbir kaynak indirilemiyorsa tek cümle.
 */
@Composable
private fun DownloadPickList(
    streams: List<PlayableStream>,
    checking: Boolean,
    isEpisode: Boolean,
    badgeOf: (PlayableStream) -> DownloadBadge,
    fractionOf: (PlayableStream) -> Float?,
    onDownload: (PlayableStream) -> Unit,
    focus: FocusRequester,
) {
    LazyColumn(Modifier.heightIn(min = 300.dp, max = 460.dp)) {
        items(streams, key = { it.url }) { stream ->
            val first = stream == streams.first()
            SourceRow(
                stream,
                isRecommended = first,
                badge = badgeOf(stream),
                downloadFraction = fractionOf(stream),
                onClick = { onDownload(stream) },
                onDownload = { onDownload(stream) },
                downloadOnTap = true,
                modifier = if (first) Modifier.focusRequester(focus) else Modifier,
            )
        }
        if (checking) {
            item(key = "checking") { SoftLine(stringResource(R.string.download_pick_checking)) }
        } else if (streams.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(if (isEpisode) R.string.download_pick_empty_episode else R.string.download_pick_empty_movie),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    stream: PlayableStream,
    isRecommended: Boolean,
    badge: DownloadBadge,
    downloadFraction: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    downloadOnTap: Boolean = false,
) {
    val shape = MaterialTheme.shapes.medium
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = shape,
        color = if (isRecommended) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            if (isRecommended) 1.5.dp else 1.dp,
            if (isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .focusRing(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stream.source.addonName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceLabel(stream),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isRecommended) {
                Icon(PlayIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            // Torrent kaynağı yalnızca izlenir (yerleşik motor geçici indirir); çevrimdışı indirme yok.
            // İndirme modunda satırın kendisi indirir: simge yalnızca durumu gösterir, ayrıca odaklanmaz.
            if (downloadOnTap) {
                DownloadStateIcon(badge, downloadFraction)
            } else if (!TorrentLinks.isMagnet(stream.url)) {
                DownloadButton(badge, downloadFraction, onDownload)
            }
        }
    }
}

/**
 * Satır sonundaki küçük indirme simgesi. İndirilmemiş: aşağı ok (dokununca indirir). İniyor/sırada/duraklatılmış: ilerleme
 * halkası (yönetim Kütüphane'de). İndirildi: onay işaretli tepsi. Başarısız: hata renginde ok, dokununca yeniden dener.
 * Satıra dokunmak yine OYNATIR; bu düğme ayrı odaklanır (kumandada Sağ).
 */
@Composable
private fun DownloadButton(badge: DownloadBadge, fraction: Float?, onClick: () -> Unit) {
    when (badge) {
        DownloadBadge.None, DownloadBadge.Failed -> IconButton(onClick = onClick) {
            Icon(
                PlayIcons.Download,
                contentDescription = stringResource(R.string.download_action),
                tint = if (badge == DownloadBadge.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DownloadBadge.Active -> {
            val label = stringResource(R.string.download_in_progress)
            Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                if (fraction != null && fraction > 0f) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
        DownloadBadge.Done -> {
            val label = stringResource(R.string.download_done)
            Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                Icon(PlayIcons.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Yalnızca durum gösteren (dokunulmayan) indirme simgesi: aşağı ok / ilerleme halkası / tik / hata renginde ok. */
@Composable
fun DownloadStateIcon(badge: DownloadBadge, fraction: Float?, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (badge) {
            DownloadBadge.None, DownloadBadge.Failed -> Icon(
                PlayIcons.Download,
                contentDescription = null,
                tint = if (badge == DownloadBadge.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DownloadBadge.Active -> if (fraction != null && fraction > 0f) {
                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            DownloadBadge.Done -> Icon(PlayIcons.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "1080p · 2,1 GB · EN · Altyazılı": kalite, (biliniyorsa) boyut, ses dili ve altyazı var mı. Codec/HDR yok. */
@Composable
fun sourceLabel(stream: PlayableStream): String {
    val quality = when (val h = stream.resolution) {
        null -> stringResource(R.string.sources_quality_unknown)
        2160 -> stringResource(R.string.quality_4k)
        else -> stringResource(R.string.quality_p, h)
    }
    val size = stream.sizeBytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
    val subtitled = if (stream.subtitles.isNotEmpty()) stringResource(R.string.sources_subtitled) else null
    return listOfNotNull(quality, size, stream.language, subtitled)
        .reduce { acc, part -> stringResource(R.string.dot_join, acc, part) }
}
