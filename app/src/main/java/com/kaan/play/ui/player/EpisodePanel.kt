package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kaan.play.R
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EpisodePanel(
    activity: PlayerActivity,
    onDismiss: () -> Unit,
) {
    var episodes by remember { mutableStateOf<List<EpisodePanelRules.EpisodeRow>?>(null) }
    var loading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        episodes = activity.loadEpisodes()
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.episodes_panel_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        shadow = Shadow(color = Color.Black, blurRadius = 8f)
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (episodes.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.episodes_panel_list_failed),
                        color = Color.White,
                        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 8f))
                    )
                } else {
                    val listState = rememberLazyListState()
                    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
                    val currentIdx = episodes!!.indexOfFirst { it.isCurrent }.coerceAtLeast(0)

                    LaunchedEffect(episodes) {
                        listState.scrollToItem(currentIdx)
                        delay(100)
                        focusRequesters[currentIdx]?.requestFocus()
                    }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(episodes!!) { index, item ->
                            val fr = remember { FocusRequester() }
                            focusRequesters[index] = fr

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (item.isCurrent) Color(0x33062A3B) else Color.Transparent)
                                    .focusRing(RoundedCornerShape(8.dp))
                                    .focusRequester(fr)
                                    .clickable {
                                        if (item.isCurrent) {
                                            onDismiss()
                                        } else {
                                            onDismiss()
                                            activity.prepareEpisode(item.season, item.episode, item.dataUrl)
                                            android.widget.Toast.makeText(
                                                activity,
                                                activity.getString(R.string.episodes_panel_searching, item.season, item.episode),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .onKeyEvent { evt ->
                                        if (evt.type == KeyEventType.KeyDown) {
                                            if (evt.key == Key.DirectionLeft || evt.key == Key.DirectionRight) {
                                                // Birden çok sezon varsa sezon değiştirecek ama (yalnızca birden çok sezon varsa) eklendi
                                                // Fakat TMDB API'den tüm sezonları önceden alamadık, tek sezon yüklendi.
                                                // Bu kısmı basitleştirelim, şu an sadece yüklü sezonlar içinde oklarla.
                                                true
                                            } else false
                                        } else false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.episodes_panel_episode_format, item.episode),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.isCurrent) {
                                    Text(
                                        text = stringResource(R.string.episodes_panel_now_playing),
                                        color = Color(0xFF00E5FF),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                        )
                                    )
                                } else if (item.watched) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                        )
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
