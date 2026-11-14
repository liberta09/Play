package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.WatchRules
import kotlinx.coroutines.launch
import com.kaan.play.PlayApp
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages

/** Devam et listesi; ilk değer veritabanından gelene kadar null (açılış odağı bunu bekler). */
@Composable
internal fun rememberContinueItemsOrNull(): List<ContinueItem>? {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as PlayApp).container.continueWatching.items }
    val all by flow.collectAsStateWithLifecycle(initialValue = null)
    return remember(all) { all?.filter { it.openableOnTv() } }
}

@Composable
internal fun rememberContinueItems(): List<ContinueItem> {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as PlayApp).container.continueWatching.items }
    val all by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return remember(all) { all.filter { it.openableOnTv() } }
}

/**
 * "İzlemeye devam et" satırı: kartın altında bölüm ve kalan süre; OK oynatır, uzun basış menü açar (Detayı aç / Devam et
 * listesinden kaldır — Play'deki uzun basışla kaldırma gibi; kaldırma Play'in silinenler kaydını da yazar, eşitlemede geri gelmez).
 */
@Composable
internal fun ContinueRow(
    items: List<ContinueItem>,
    onPlay: PlayLauncher,
    onOpen: (DetailRef) -> Unit,
    initialKey: String? = null,
    onFocused: (String) -> Unit = {},
) {
    // Devam et satırı karışıktır (dikey dramalar + 16:9 filmler): yönü her kart kendi görselinden seçer (§4.1).
    val landscapeOf = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<ContinueItem?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var refocusIndex by remember { mutableStateOf<Int?>(null) }
    RefocusAfterRemoval(items, refocusIndex, { requesters[continueKey(it)] }) { refocusIndex = null }
    Column {
        Text(stringResource(R.string.tv_home_continue), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Gutter, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { continueKey(it) }) { item ->
                val e = item.entry
                val key = continueKey(item)
                val landscape = landscapeOf[key] ?: (e.pluginId == null)
                Column(Modifier.width(cardWidth(landscape))) {
                    PosterCard(
                        modifier = Modifier.focusRequester(requesters.getOrPut(key) { FocusRequester() }),
                        title = e.title, imageUrl = TmdbImages.url(e.posterPath, TmdbImages.Size.Poster), landscape = landscape,
                        progress = if (item is ContinueItem.Resume) e.positionMs.toFloat() / e.durationMs.coerceAtLeast(1) else null,
                        requestInitialFocus = key == initialKey,
                        onFocused = { onFocused(key) },
                        onImageSize = { w, h -> landscapeOf[key] = w > h * 1.2f },
                        onClick = { onPlay.resume(item, onOpen) },
                        onLongClick = { menuFor = item },
                    )
                    Spacer(Modifier.height(8.dp)) // odakta büyüyen kart yazının üstüne binmesin
                    Text(caption(item), color = TextDim, fontSize = TvText.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    menuFor?.let { item ->
        ActionDialog(
            title = item.entry.title,
            actions = listOf(
                stringResource(R.string.tv_library_open_detail) to { onPlay.openDetail(item.entry, onOpen) },
                stringResource(R.string.tv_library_continue_remove) to {
                    refocusIndex = items.indexOf(item)
                    scope.launch { (context.applicationContext as PlayApp).container.library.removeContinueWatching(item) }
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

/**
 * Odaktaki kart silinince odak yan menüye kaçmasın: liste yenilenince aynı sıradaki (son kart silindiyse bir öncekine) karta
 * verilir. [index] null ise bir şey yapmaz.
 */
@Composable
private fun <T> RefocusAfterRemoval(items: List<T>, index: Int?, requesterOf: (T) -> FocusRequester?, done: () -> Unit) {
    LaunchedEffect(items) {
        val i = index ?: return@LaunchedEffect
        items.getOrNull(i.coerceAtMost(items.lastIndex))?.let { target ->
            withFrameNanos { }
            runCatching { requesterOf(target)?.requestFocus() }
        }
        done()
    }
}

/** Kumandayla seçilen eylem penceresi; [focusCancel] ise (silme onayı) ilk odak İptal'dedir. */
@Composable
internal fun ActionDialog(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    message: String? = null,
    focusCancel: Boolean = false,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(520.dp).background(Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            message?.let { Text(it, color = TextDim, fontSize = TvText.Meta) }
            actions.forEachIndexed { i, (label, action) ->
                Button(
                    onClick = { onDismiss(); action() },
                    modifier = Modifier.fillMaxWidth().let { if (i == 0 && !focusCancel) it.focusRequester(first) else it },
                ) { Text(label) }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().let { if (focusCancel) it.focusRequester(first) else it }) {
                Text(stringResource(R.string.tv_cancel))
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

internal fun continueKey(item: ContinueItem) = "continue|${item.entry.id}"

@Composable
private fun caption(item: ContinueItem): String {
    val e = item.entry
    return when (item) {
        is ContinueItem.Next -> stringResource(R.string.tv_library_next, item.episode)
        is ContinueItem.Resume -> {
            val left = ((e.durationMs - e.positionMs) / 60_000).coerceAtLeast(1)
            if (e.type == MediaType.Tv) stringResource(R.string.tv_library_left_episode, e.episode, left)
            else stringResource(R.string.tv_library_left_movie, left)
        }
    }
}

/** Kütüphane: devam et, favoriler (Play'in favorileri) ve geçmiş (Play Kütüphane > Geçmiş gibi: tek kayıt silme, hepsini temizle). */
@Composable
fun TvLibraryScreen(onOpen: (DetailRef) -> Unit, onPlay: PlayLauncher) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PlayApp).container }
    val favorites by remember { TvFavorites.get(context) }.items.collectAsStateWithLifecycle()
    val continueItems = rememberContinueItems()
    val historyAll by remember { container.library.watchHistory }.collectAsStateWithLifecycle(initialValue = emptyList())
    val history = remember(historyAll) { historyAll.filter { it.openableOnTv() } }
    if (favorites.isEmpty() && continueItems.isEmpty() && history.isEmpty()) {
        EmptyNote(stringResource(R.string.tv_library_empty_title), stringResource(R.string.tv_library_empty_body))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "title") { ScreenTitle(stringResource(R.string.tv_section_library), Modifier.padding(start = Gutter)) }
        if (continueItems.isNotEmpty()) {
            item(key = "continue") { ContinueRow(continueItems, onPlay, onOpen, initialKey = continueKey(continueItems.first())) }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favorites") {
                var landscape by remember { mutableStateOf(false) }
                Column {
                    Text(stringResource(R.string.tv_library_favorites), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = Gutter, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(favorites, key = { "${it.providerId}|${it.url}" }) { ref ->
                            PosterCard(
                                title = ref.title, imageUrl = ref.imageUrl, landscape = landscape,
                                requestInitialFocus = continueItems.isEmpty() && ref == favorites.first(),
                                onImageSize = { w, h -> if (!landscape && w > h * 1.2f) landscape = true },
                                onClick = { onOpen(ref.copy(landscape = landscape)) },
                            )
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "history") {
                HistoryRow(history, onPlay, onOpen, requestInitialFocus = continueItems.isEmpty() && favorites.isEmpty())
            }
        }
    }
}

/** Geçmiş satırı: her film ve bölüm ayrı kart, son izlenen önde. OK oynatır; uzun basış menü (Detayı aç / Geçmişten kaldır). */
@Composable
private fun HistoryRow(history: List<WatchHistoryEntity>, onPlay: PlayLauncher, onOpen: (DetailRef) -> Unit, requestInitialFocus: Boolean) {
    val context = LocalContext.current
    val library = remember { (context.applicationContext as PlayApp).container.library }
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<WatchHistoryEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val landscapeOf = remember { mutableStateMapOf<Long, Boolean>() }
    val requesters = remember { mutableMapOf<Long, FocusRequester>() }
    var refocusIndex by remember { mutableStateOf<Int?>(null) }
    RefocusAfterRemoval(history, refocusIndex, { requesters[it.id] }) { refocusIndex = null }
    Column {
        Row(
            Modifier.padding(start = Gutter, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.tv_library_history), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
            Button(onClick = { confirmClear = true }) { Text(stringResource(R.string.tv_library_history_clear)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history, key = { "history|${it.id}" }) { e ->
                val landscape = landscapeOf[e.id] ?: (e.pluginId == null)
                Column(Modifier.width(cardWidth(landscape))) {
                    PosterCard(
                        modifier = Modifier.focusRequester(requesters.getOrPut(e.id) { FocusRequester() }),
                        title = e.title, imageUrl = TmdbImages.url(e.posterPath, TmdbImages.Size.Poster), landscape = landscape,
                        progress = e.positionMs.toFloat() / e.durationMs.coerceAtLeast(1),
                        requestInitialFocus = requestInitialFocus && e == history.first(),
                        onImageSize = { w, h -> landscapeOf[e.id] = w > h * 1.2f },
                        onClick = { onPlay.playHistory(e, onOpen) },
                        onLongClick = { menuFor = e },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(historyCaption(e), color = TextDim, fontSize = TvText.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    menuFor?.let { e ->
        ActionDialog(
            title = e.title,
            actions = listOf(
                stringResource(R.string.tv_library_open_detail) to { onPlay.openDetail(e, onOpen) },
                stringResource(R.string.tv_library_history_remove) to {
                    refocusIndex = history.indexOf(e)
                    scope.launch { library.removeHistory(e.id) }
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
    if (confirmClear) {
        ActionDialog(
            title = stringResource(R.string.tv_library_history_clear),
            message = stringResource(R.string.tv_library_history_clear_message),
            actions = listOf(stringResource(R.string.tv_library_history_clear_confirm) to { scope.launch { library.clearHistory() } }),
            onDismiss = { confirmClear = false },
            focusCancel = true,
        )
    }
}

@Composable
private fun historyCaption(e: WatchHistoryEntity): String {
    val state = if (WatchRules.isWatched(e.positionMs, e.durationMs)) stringResource(R.string.tv_library_history_watched)
        else stringResource(R.string.tv_library_history_percent, (e.positionMs * 100 / e.durationMs.coerceAtLeast(1)).toInt())
    return if (e.type == MediaType.Tv) stringResource(R.string.tv_library_history_episode, e.season, e.episode) + "\n" + state else state
}
