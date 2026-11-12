package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Accent = Color(0xFF00E5FF)
private val CurrentFill = Color(0x66062A3B)
private val TextShadow = Shadow(color = Color.Black, blurRadius = 8f)

/**
 * Oynatıcıda dizinin bölümleri: sağdan açılan, arka plansız liste (video arkada oynar, karartma yok).
 * Kumanda: açılınca odak oynayan bölümde; Yukarı/Aşağı gezer, Tamam bölümü açar, Sol/Sağ sezon değiştirir, Geri kapatır.
 * Dokunmatikte satıra dokunmak Tamam'la aynıdır; sezon okları dokunmayla çalışır (kumandada Sol/Sağ).
 */
@Composable
fun EpisodePanel(
    activity: PlayerActivity,
    onDismiss: () -> Unit,
) {
    val playingSeason = remember { activity.playingSeason() }
    var shownSeason by remember { mutableIntStateOf(playingSeason ?: 1) }
    var rows by remember { mutableStateOf<List<EpisodePanelRules.EpisodeRow>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var switching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rows = activity.loadEpisodes(shownSeason)
        loading = false
    }

    // Sezon değişimi: yeni sezon gerçekten bölüm döndürürse geçilir; yoksa (son sezon / alınamadı) olduğu yerde kalınır.
    fun switchSeason(forward: Boolean) {
        if (switching || loading) return
        val target = EpisodePanelRules.neighborSeason(shownSeason, forward) ?: return
        switching = true
        scope.launch {
            val loaded = activity.loadEpisodes(target)
            if (!loaded.isNullOrEmpty()) {
                shownSeason = target
                rows = loaded
            }
            switching = false
        }
    }

    fun choose(row: EpisodePanelRules.EpisodeRow) {
        onDismiss()
        if (EpisodePanelRules.shouldOpen(row)) activity.openEpisode(row.season, row.episode, row.dataUrl)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.setDimAmount(0f)
            window?.setGravity(android.view.Gravity.END)
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .padding(16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { switchSeason(forward = false); true }
                        Key.DirectionRight -> { switchSeason(forward = true); true }
                        else -> false
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.episodes_panel_title),
                        style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    // Sezon okları yalnızca dokunma içindir; kumandada Sol/Sağ aynı işi yapar (odak listeden çıkmaz).
                    Icon(
                        imageVector = PlayIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(180f)
                            .focusProperties { canFocus = false }
                            .clickable { switchSeason(forward = false) },
                    )
                    Text(
                        text = stringResource(R.string.episodes_panel_season_format, shownSeason),
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                        color = Color.White,
                    )
                    Icon(
                        imageVector = PlayIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .focusProperties { canFocus = false }
                            .clickable { switchSeason(forward = true) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val list = rows
                if (loading) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (list.isNullOrEmpty()) {
                    // Liste alınamadı: odak panelde kalsın ki Geri paneli kapatsın.
                    Text(
                        text = stringResource(R.string.episodes_panel_list_failed),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                        modifier = Modifier.focusable(),
                    )
                } else {
                    val listState = rememberLazyListState()
                    val requesters = remember(list) { List(list.size) { FocusRequester() } }

                    LaunchedEffect(list) {
                        val start = EpisodePanelRules.initialIndex(list)
                        listState.scrollToItem(start)
                        delay(FOCUS_DELAY_MS)
                        runCatching { requesters[start].requestFocus() }
                    }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { _, row -> "${row.season}:${row.episode}:${row.dataUrl}" }) { index, row ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(shape)
                                    .background(if (row.isCurrent) CurrentFill else Color.Transparent)
                                    .focusRequester(requesters[index])
                                    .focusRing(shape)
                                    .clickable { choose(row) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.episodes_panel_episode_format, row.episode),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge.copy(shadow = TextShadow),
                                    modifier = Modifier.weight(1f),
                                )
                                if (row.isCurrent) {
                                    Text(
                                        text = stringResource(R.string.episodes_panel_now_playing),
                                        color = Accent,
                                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                                    )
                                } else if (row.watched) {
                                    Icon(
                                        imageVector = PlayIcons.Check,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Liste ilk çizildikten sonra odak istenir (öğe henüz yerleşmemişse istek boşa gider). */
private const val FOCUS_DELAY_MS = 120L
