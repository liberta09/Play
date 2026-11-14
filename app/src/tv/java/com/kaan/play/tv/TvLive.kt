package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.ui.res.stringResource
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaan.play.PlayApp
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogResult
import com.kaan.play.data.provider.CatalogRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Oynatıcının kanal listesi paneli için son açılan listedeki kanallar (Canlı TV ekranında görünen sırayla). Bellekte tutulur;
 * uygulama süreci yeniden başlarsa boş kalır ve panel açılmaz.
 */
internal object LiveChannels {
    @Volatile var list: List<CatalogItem> = emptyList()
}

/** Canlı TV kanal listesi durumu: [rows] null → henüz hiç sonuç yok. */
data class LiveState(val rows: List<CatalogRow>? = null, val loading: Boolean = false, val error: Boolean = false)

/**
 * Canlı TV: Play'in canlı kataloğu (açık Nuvio addon'larının tv/channel katalogları + CloudStream eklentilerinin Live API'leri;
 * `CatalogRepository.loadLiveCatalogUpdates`, telefondaki TV sekmesiyle aynı). Sonuçlar eklentiler bittikçe gelir; bölüm açık
 * kalmasa da (etkinlik ömrü boyunca) liste korunur.
 */
class TvLiveModel(app: Application) : AndroidViewModel(app) {
    private val container get() = getApplication<PlayApp>().container
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state
    private var job: Job? = null

    /** Ayarlarda depo/eklenti değişince: liste bayat; Canlı TV'ye bir sonraki girişte yeniden yüklenir. */
    fun invalidate() {
        job?.cancel()
        _state.value = LiveState()
    }

    fun load(force: Boolean = false) {
        if (job?.isActive == true || (!force && _state.value.rows != null)) return
        lateinit var self: Job
        self = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            try {
                container.catalogRepository.loadLiveCatalogUpdates(forceRefresh = force).collect { r ->
                    when (r) {
                        is CatalogResult.Success -> _state.value = _state.value.copy(rows = r.rows.filter { it.items.isNotEmpty() })
                        else -> if (_state.value.rows == null) _state.value = _state.value.copy(rows = emptyList())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(error = true, rows = _state.value.rows ?: emptyList())
            } finally {
                // invalidate() sonrası başlayan yeni yükleme sürerken iptal edilen eskisi "aranıyor"u kapatmasın.
                if (job === self) _state.value = _state.value.copy(loading = false)
            }
        }
        job = self
    }
}

/**
 * "Kanal Listesi" düzeni: başlık + kanal arama, kategori çipleri (Tümü + eklentilerin kanal bölümleri), 5 sütunlu ızgara
 * ([numara | logo], altında kanal adı). OK kanalı TV oynatıcısında açar; kaynak bulunamazsa oynatıcı söyler.
 */
@Composable
fun TvLiveScreen(onPlay: PlayLauncher) {
    val model: TvLiveModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.load() }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    // İlk odak yalnız bir kez verilir; liste/arama/kategori değişince ilk kanal odağı çalmasın.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val rows = state.rows.orEmpty()
    val categories = rows.map { it.title }.distinct()
    val needle = query.trim().lowercase()
    val channels = remember(rows, category, needle) {
        rows.filter { category == null || it.title == category }
            .flatMap { it.items }
            .distinctBy { "${it.providerId}|${it.rawUrl ?: it.id}" }
            .filter { needle.isEmpty() || it.title.lowercase().contains(needle) }
    }

    Column(Modifier.fillMaxSize().padding(start = Gutter, end = Gutter, top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.tv_section_live))
            Spacer(Modifier.weight(1f))
            TvTextField(
                value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.tv_live_search_placeholder), onDone = {},
                // Kanallar gelmeden kullanıcı aramaya/çiplere geçtiyse ilk kanal odağı artık çalmaz.
                modifier = Modifier.width(260.dp).onFocusChanged { if (it.hasFocus) initialFocusDone = true },
            )
        }
        Spacer(Modifier.height(14.dp))
        if (categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.onFocusChanged { if (it.hasFocus) initialFocusDone = true },
            ) {
                item(key = "__all__") { Chip(stringResource(R.string.tv_live_all), selected = category == null) { category = null } }
                items(categories, key = { it }) { c -> Chip(c, selected = category == c) { category = c } }
            }
            Spacer(Modifier.height(14.dp))
        }
        val status = when {
            state.rows == null || (state.loading && rows.isEmpty()) -> stringResource(R.string.tv_live_searching)
            rows.isEmpty() && state.error -> stringResource(R.string.tv_live_load_failed)
            rows.isEmpty() -> stringResource(R.string.tv_live_no_addons)
            channels.isEmpty() -> stringResource(R.string.tv_live_no_match, query)
            else -> null
        }
        if (status != null) {
            Text(status, color = TextDim, fontSize = TvText.Body)
            if (!state.loading && rows.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                val retry = remember { FocusRequester() }
                var retryFocused by remember { mutableStateOf(false) }
                Button(onClick = { model.load(force = true) }, modifier = Modifier.focusRequester(retry).onFocusChanged { retryFocused = it.isFocused }) {
                    Text(stringResource(R.string.tv_home_retry), fontSize = TvText.Body)
                }
                InitialFocus(retry) { retryFocused }
            }
            return@Column
        }
        if (state.loading) Text(stringResource(R.string.tv_live_more_searching), color = TextDim, fontSize = TvText.Label, modifier = Modifier.padding(bottom = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(channels, key = { _, c -> "${c.providerId}|${c.rawUrl ?: c.id}" }) { i, c ->
                ChannelTile(
                    number = i + 1, channel = c, requestInitialFocus = i == 0 && !initialFocusDone,
                    onFocused = { initialFocusDone = true },
                ) { LiveChannels.list = channels; onPlay.playLive(c) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) // FilterChip: tv-material3'te tek seçim çipi
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick) {
        Text(label, fontSize = TvText.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelTile(number: Int, channel: CatalogItem, requestInitialFocus: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusRequester(requester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape)),
            colors = CardDefaults.colors(containerColor = Surface1),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(30.dp).background(Bg.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text("$number", color = TextDim, fontSize = TvText.Label)
                }
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    if (channel.posterUrl != null) {
                        AsyncImage(
                            model = channel.posterUrl, contentDescription = channel.title, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            channel.title, color = TextMain, fontSize = TvText.Meta, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Text(
            channel.title, color = if (focused) TextMain else TextDim, fontSize = TvText.Label, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
    if (requestInitialFocus) InitialFocus(requester) { focused }
}
