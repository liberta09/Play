package com.kaan.play.ui.library

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.download.DownloadRules
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.resolvePosterImageUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory

private val CardMinWidth = 132.dp
private val Gutter = 20.dp

/**
 * Kütüphane: tek ana iş "benim listem". İki bölüm: Favoriler (kapak ızgarası, sütun sayısı ekran genişliğinden)
 * ve Geçmiş (son izlenen üstte, ilerleme çubuğuyla). Her silme işlemi bir düğmedir; dokunma dışı hareket gerekmez.
 */
@Composable
fun LibraryScreen(
    onOpenDetail: (MediaType, Int) -> Unit,
    onPlayEntry: (WatchHistoryEntity) -> Unit,
    onPlayDownload: (DownloadEntity) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val vm = containerViewModel { LibraryViewModel(it.library, it.downloads, it.settings) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val waitingForNetwork by vm.waitingForNetwork.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<DownloadEntity?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    // Kumanda: ilk favori (yoksa ilk geçmiş satırı) başlangıç odağı; geri dönünce son odaklanan öğe.
    val focus = rememberFocusMemory(enabled = favorites != null && history != null)
    val firstIsFavorite = !favorites.isNullOrEmpty()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardMinWidth),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidth {
            Text(
                text = stringResource(R.string.tab_library),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Yüklenirken (null) boş durum mesajı yanıp sönmesin.
        val favs = favorites
        fullWidth { SectionTitle(stringResource(R.string.library_favorites)) }
        if (favs != null && favs.isEmpty()) {
            fullWidth { EmptyText(stringResource(R.string.library_favorites_empty)) }
        } else if (favs != null) {
            items(favs, key = { "fav-${it.type}-${it.tmdbId}" }) { fav ->
                val removeLabel = stringResource(R.string.detail_favorite_remove)
                PosterCard(
                    title = fav.title,
                    subtitle = stringResource(if (fav.type == MediaType.Movie) R.string.type_movie else R.string.type_series),
                    imageUrl = TmdbImages.url(fav.posterPath, TmdbImages.Size.Poster),
                    tmdbId = fav.tmdbId,
                    mediaType = fav.type,
                    onClick = { onOpenDetail(fav.type, fav.tmdbId) },
                    modifier = Modifier
                        .let { if (fav == favs.first() && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                        .let { if (fav == favs.first() && topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                        .focusMemory(focus, "fav-${fav.type}-${fav.tmdbId}", isDefault = fav == favs.first()),
                    overlay = {
                        IconButton(
                            onClick = { vm.removeFavorite(fav) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(PlayIcons.HeartFilled, contentDescription = removeLabel, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }

        val hist = history
        fullWidth {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(R.string.library_history), Modifier.weight(1f))
                if (!hist.isNullOrEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.library_clear_history)) }
                }
            }
        }
        if (hist != null && hist.isEmpty()) {
            fullWidth { EmptyText(stringResource(R.string.library_history_empty)) }
        } else if (hist != null) {
            items(hist, key = { "hist-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                HistoryRow(
                    entry,
                    onClick = { onPlayEntry(entry) },
                    onRemove = { vm.removeHistory(entry) },
                    modifier = Modifier
                        .let { if (!firstIsFavorite && entry == hist.first() && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                        .let { if (!firstIsFavorite && entry == hist.first() && topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                        .focusMemory(focus, "hist-${entry.id}", isDefault = !firstIsFavorite && entry == hist.first()),
                )
            }
        }

        // Üçüncü bölüm: İndirilenler (çevrimdışı izleme).
        val dls = downloads
        fullWidth {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(R.string.library_downloads), Modifier.weight(1f))
                if (!dls.isNullOrEmpty()) {
                    TextButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.library_downloads_delete_all)) }
                }
            }
        }
        if (dls != null && dls.isEmpty()) {
            fullWidth { EmptyText(stringResource(R.string.library_downloads_empty)) }
        } else if (dls != null) {
            items(dls, key = { "dl-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                DownloadRow(
                    entry = entry,
                    waitingForNetwork = waitingForNetwork,
                    wifiOnly = wifiOnly,
                    onClick = {
                        when (DownloadRules.rowAction(entry.status)) {
                            DownloadRules.RowAction.Play -> onPlayDownload(entry)
                            DownloadRules.RowAction.Pause -> vm.pauseDownload(entry)
                            DownloadRules.RowAction.Resume -> vm.resumeDownload(entry)
                        }
                    },
                    onPauseResume = {
                        if (DownloadRules.rowAction(entry.status) == DownloadRules.RowAction.Pause) {
                            vm.pauseDownload(entry)
                        } else {
                            vm.resumeDownload(entry)
                        }
                    },
                    onRemove = { deleteTarget = entry },
                    modifier = Modifier.focusMemory(
                        focus,
                        "dl-${entry.id}",
                        isDefault = !firstIsFavorite && hist.isNullOrEmpty() && entry == dls.first(),
                    ),
                )
            }
        }
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.download_delete_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.download_delete_message, entry.title)) },
            confirmButton = {
                TextButton(onClick = { vm.removeDownload(entry); deleteTarget = null }) {
                    Text(stringResource(R.string.download_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.download_delete_all_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.download_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = { vm.removeAllDownloads(); confirmDeleteAll = false }) {
                    Text(stringResource(R.string.download_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.library_clear_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.library_clear_message)) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.library_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun HistoryRow(entry: WatchHistoryEntity, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val watched = WatchRules.isWatched(entry.positionMs, entry.durationMs)
    val minutesLeft = (((entry.durationMs - entry.positionMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    val isSeries = entry.type == MediaType.Tv
    val subtitle = when {
        isSeries && watched -> stringResource(R.string.library_episode_watched, entry.season, entry.episode)
        isSeries -> stringResource(R.string.continue_left_episode, entry.season, entry.episode, minutesLeft)
        watched -> stringResource(R.string.library_watched)
        else -> stringResource(R.string.continue_left_movie, minutesLeft)
    }
    val progress = (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .aspectRatio(2f / 3f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                resolvePosterImageUrl(TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster), null, entry.tmdbId, entry.type)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    PlayIcons.Close,
                    contentDescription = stringResource(R.string.library_remove_history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Bir indirme satırı: afiş + ad + "S1 B3 · 412 MB / 1,2 GB" + ilerleme çubuğu (tamamlanmamışken). Satıra dokunmak ana işi yapar
 * (tamam: oynat, iniyor: duraklat, duraklatılmış/başarısız: sürdür); yanındaki iki düğme aynı işleri açıkça verir:
 * duraklat/sürdür ve iptal/sil (iptal ile silme aynı işlemdir, simgesi duruma göre değişir).
 */
@Composable
private fun DownloadRow(
    entry: DownloadEntity,
    waitingForNetwork: Boolean,
    wifiOnly: Boolean,
    onClick: () -> Unit,
    onPauseResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val done = entry.status == DownloadStatus.Completed
    val fraction = DownloadRules.fraction(entry.downloadedBytes, entry.totalBytes)

    val episode = if (entry.type == MediaType.Tv) stringResource(R.string.download_episode, entry.season, entry.episode) else null
    val downloaded = entry.downloadedBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val total = entry.totalBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val size = when {
        done -> total
        downloaded != null && total != null -> stringResource(R.string.download_progress_of, downloaded, total)
        else -> downloaded
    }
    val state = when (entry.status) {
        DownloadStatus.Completed, DownloadStatus.Downloading -> null
        DownloadStatus.Queued -> stringResource(
            when {
                !waitingForNetwork -> R.string.download_state_queued
                wifiOnly -> R.string.download_state_wifi
                else -> R.string.download_state_network
            },
        )
        DownloadStatus.Paused -> stringResource(R.string.download_state_paused)
        DownloadStatus.Failed -> stringResource(R.string.download_state_failed)
    }
    val subtitle = listOfNotNull(episode, size, state).reduceOrNull { acc, part -> stringResource(R.string.dot_join, acc, part) }

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .aspectRatio(2f / 3f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                resolvePosterImageUrl(TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster), null, entry.tmdbId, entry.type)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!done) {
                    val barModifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = barModifier, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outline)
                    } else if (entry.status == DownloadStatus.Downloading) {
                        LinearProgressIndicator(modifier = barModifier, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outline)
                    } else {
                        LinearProgressIndicator(progress = { 0f }, modifier = barModifier, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (!done) {
                val active = DownloadRules.isActive(entry.status)
                IconButton(onClick = onPauseResume) {
                    Icon(
                        imageVector = when {
                            entry.status == DownloadStatus.Failed -> PlayIcons.Refresh
                            active -> PlayIcons.Pause
                            else -> PlayIcons.PlayTriangle
                        },
                        contentDescription = stringResource(
                            when {
                                entry.status == DownloadStatus.Failed -> R.string.common_retry
                                active -> R.string.download_pause
                                else -> R.string.download_resume
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = if (done) PlayIcons.Trash else PlayIcons.Close,
                    contentDescription = stringResource(if (done) R.string.download_remove else R.string.download_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
