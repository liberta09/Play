package com.kaan.play.ui.tv

import androidx.compose.foundation.background
import com.kaan.play.ui.tv.focusRing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import com.kaan.play.ui.components.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogResult
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.ui.components.resolvePosterImageUrl
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.text.Normalizer
import java.util.Locale

private enum class ChannelSourceFilter { All, Nuvio, CloudStream }

private data class LiveChannelEntry(
    val item: CatalogItem,
    val rowTitle: String,
    val providerName: String,
)

private data class LiveChannelGroup(val key: String, val sources: List<LiveChannelEntry>) {
    val primary: LiveChannelEntry get() = sources.first()
}

/** Sağlayıcıların canlı tv/channel akışlarını aranabilir tek bir ekranda toplar. */
@Composable
fun TvScreen(
    onPlay: (CatalogItem) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    var refreshKey by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<CatalogResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf(ChannelSourceFilter.All) }
    var selectedChannel by remember { mutableStateOf<LiveChannelGroup?>(null) }

    LaunchedEffect(refreshKey) {
        loading = true
        try {
            container.catalogRepository.loadLiveCatalogUpdates(forceRefresh = refreshKey > 0).collect {
                result = it
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            result = CatalogResult.Error(t.message)
        } finally {
            loading = false
        }
    }

    val rows = (result as? CatalogResult.Success)?.rows.orEmpty()
    val allChannels = remember(rows) {
        rows.flatMap { row ->
            row.items.map { item -> LiveChannelEntry(item, row.title, row.providerName) }
        }.distinctBy { "${it.item.providerId}:${it.item.id}" }
            .groupBy { it.item.title.tvSearchKey().ifBlank { "${it.item.providerId}:${it.item.id}" } }
            .map { (key, sources) -> LiveChannelGroup(key, sources) }
    }
    val visibleChannels = remember(allChannels, query, sourceFilter) {
        val needle = query.tvSearchKey()
        allChannels.filter { channel ->
            val sourceMatches = when (sourceFilter) {
                ChannelSourceFilter.All -> true
                ChannelSourceFilter.Nuvio -> channel.sources.any { it.item.providerId.startsWith("nuvio_addon:") }
                ChannelSourceFilter.CloudStream -> channel.sources.any { it.item.providerId.startsWith("cs:") }
            }
            val textMatches = needle.isBlank() || channel.sources.any { source ->
                listOf(source.item.title, source.rowTitle, source.providerName).any { it.tvSearchKey().contains(needle) }
            }
            sourceMatches && textMatches
        }
    }
    val nuvioCount = allChannels.count { group -> group.sources.any { it.item.providerId.startsWith("nuvio_addon:") } }
    val cloudStreamCount = allChannels.count { group -> group.sources.any { it.item.providerId.startsWith("cs:") } }
    val keyboard = LocalSoftwareKeyboardController.current
    val initialFocus = rememberInitialFocus()
    val field = rememberCursorField(query) { query = it }
    val error = result as? CatalogResult.Error

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tv_live_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.tv_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.tv_results_count, allChannels.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading && result != null) CircularProgressIndicator()
                Button(onClick = { refreshKey++ }, enabled = !loading) {
                    Icon(PlayIcons.Refresh, contentDescription = null)
                    Text(stringResource(R.string.tv_refresh), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        OutlinedTextField(
            value = field.value,
            onValueChange = field.onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                .focusRequester(initialFocus)
                .let { if (topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                .dpadLeavesTextField(field),
            placeholder = { Text(stringResource(R.string.tv_search_hint)) },
            leadingIcon = { Icon(PlayIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(PlayIcons.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )

        // Çipler kendi genişliğini alır ve satır yana kayar; etiket tek satırdır. (Eskiden dar ekran/büyük yazıda etiket harf
        // harf sarıp satırı ekran boyu uzatıyor, `weight(1f)` ızgaraya sıfır yükseklik kalıyordu.)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = sourceFilter == ChannelSourceFilter.All,
                onClick = { sourceFilter = ChannelSourceFilter.All },
                label = { Text(stringResource(R.string.tv_filter_all, allChannels.size), maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = sourceFilter == ChannelSourceFilter.Nuvio,
                onClick = { sourceFilter = ChannelSourceFilter.Nuvio },
                label = { Text(stringResource(R.string.tv_filter_nuvio, nuvioCount), maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = sourceFilter == ChannelSourceFilter.CloudStream,
                onClick = { sourceFilter = ChannelSourceFilter.CloudStream },
                label = { Text(stringResource(R.string.tv_filter_cloudstream, cloudStreamCount), maxLines = 1, softWrap = false) },
            )
        }

        when {
            loading && result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !loading && error != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.tv_load_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { refreshKey++ }) { Text(stringResource(R.string.tv_refresh)) }
            }
            visibleChannels.isEmpty() -> Text(
                text = when {
                    allChannels.isEmpty() -> stringResource(R.string.tv_empty)
                    query.isNotBlank() -> stringResource(R.string.tv_no_results, query)
                    else -> stringResource(R.string.tv_filtered_empty)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(visibleChannels, key = { it.key }) { channel ->
                    LiveChannelCard(channel, onClick = {
                        if (channel.sources.size == 1) onPlay(channel.primary.item) else selectedChannel = channel
                    })
                }
            }
        }
    }

    selectedChannel?.let { group ->
        AlertDialog(
            onDismissRequest = { selectedChannel = null },
            title = { Text(group.primary.item.title) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.tv_source_options))
                    group.sources.forEach { source ->
                        TextButton(onClick = {
                            selectedChannel = null
                            onPlay(source.item)
                        }) {
                            Text("${source.providerName} · ${source.rowTitle}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedChannel = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun LiveChannelCard(channel: LiveChannelGroup, onClick: () -> Unit) {
    val entry = channel.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape, scale = 1.04f)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.58f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = shape,
                ),
        ) {
            val posterUrl = resolvePosterImageUrl(
                fallbackUrl = entry.item.posterUrl,
                posterId = PosterUrlTemplate.findImdbId(
                    entry.item.id,
                    entry.item.rawUrl,
                    entry.item.posterUrl,
                ),
                tmdbId = entry.item.id.toIntOrNull() ?: PosterUrlTemplate.findTmdbId(
                    entry.item.id,
                    entry.item.rawUrl,
                    entry.item.posterUrl,
                ),
                mediaType = entry.item.mediaType,
            )
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            } else {
                Text(
                    text = entry.item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp)
                    .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("●", style = MaterialTheme.typography.labelSmall, color = Color.White)
                Text(
                    stringResource(R.string.tv_live_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            Text(
                text = if (channel.sources.size > 1) stringResource(R.string.tv_sources_count, channel.sources.size) else entry.providerName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
        Text(
            text = entry.item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = entry.rowTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun String.tvSearchKey(): String = Normalizer.normalize(
    lowercase(Locale.forLanguageTag("tr")).replace('ı', 'i'),
    Normalizer.Form.NFD,
).replace(Regex("\\p{Mn}+"), "")
