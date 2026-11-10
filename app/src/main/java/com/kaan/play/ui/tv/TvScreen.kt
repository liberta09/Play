package com.kaan.play.ui.tv

import android.widget.Toast
import androidx.compose.foundation.background
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.navBarReturnEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogResult
import com.kaan.play.data.provider.LiveCatalogRules
import com.kaan.play.data.provider.LiveChannelSort
import com.kaan.play.data.provider.LiveSourceRoute
import com.kaan.play.data.provider.LiveTvCatalog
import com.kaan.play.data.provider.LiveTvCatalogRules
import com.kaan.play.data.provider.LiveTvSortMode
import com.kaan.play.data.provider.LiveZapChannel
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.ui.components.resolvePosterImageUrl
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

private enum class ChannelSourceFilter { All, Nuvio, CloudStream }

private data class LiveChannelEntry(
    val item: CatalogItem,
    val rowTitle: String,
    val providerName: String,
)

private data class LiveChannelGroup(
    val key: String,
    val title: String,
    val logoUrl: String?,
    val routes: List<LiveSourceRoute>,
    val sources: List<LiveChannelEntry>,
) {
    val primary: LiveChannelEntry get() = sources.first()
}

/** Sağlayıcıların canlı tv/channel akışlarını aranabilir tek bir ekranda toplar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvScreen(
    onPlay: (item: CatalogItem, zapChannels: List<LiveZapChannel>, zapIndex: Int, liveCatalogId: String?) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sortMode by container.settings.liveTvSortMode.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_LIVE_TV_SORT_MODE)
    val activityByKey by container.liveChannels.activityByKey.collectAsStateWithLifecycle(emptyMap())
    val orderByKey by container.liveChannels.orderByKey.collectAsStateWithLifecycle(emptyMap())
    val userCatalogs by container.liveCatalogs.catalogs.collectAsStateWithLifecycle(emptyList())
    var activeCatalogId by remember { mutableStateOf<Long?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<CatalogResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf(ChannelSourceFilter.All) }
    var selectedChannel by remember { mutableStateOf<LiveChannelGroup?>(null) }
    var addToListTarget by remember { mutableStateOf<LiveChannelGroup?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteListConfirm by remember { mutableStateOf(false) }

    val activeCatalog = remember(userCatalogs, activeCatalogId) {
        activeCatalogId?.let { id -> userCatalogs.firstOrNull { it.id == id } }
    }
    LaunchedEffect(userCatalogs, activeCatalogId) {
        if (activeCatalogId != null && userCatalogs.none { it.id == activeCatalogId }) {
            activeCatalogId = null
        }
    }

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
            .groupBy {
                LiveCatalogRules.channelGroupKey(it.item.title).ifBlank {
                    "${it.item.providerId.trim()}:${it.item.id.trim()}"
                }
            }
            .map { (key, sources) ->
                val title = sources.first().item.title
                val logoUrl = sources.firstNotNullOfOrNull { entry ->
                    entry.item.posterUrl?.trim()?.takeIf { it.isNotEmpty() }
                }
                val routes = sources.mapNotNull { LiveCatalogRules.routeFor(it.item) }.distinct()
                LiveChannelGroup(
                    key = key,
                    title = title,
                    logoUrl = logoUrl,
                    routes = routes,
                    sources = sources,
                )
            }
    }
    val allChannelsByKey = remember(allChannels) { allChannels.associateBy { it.key } }
    val catalogGroups = remember(activeCatalog, allChannelsByKey) {
        if (activeCatalog == null) emptyList()
        else activeCatalog.channels.mapNotNull { ch ->
            val live = allChannelsByKey[ch.channelKey]
            val routes = live?.routes?.takeIf { it.isNotEmpty() } ?: ch.routes
            val sources = live?.sources ?: listOfNotNull(
                LiveTvCatalogRules.catalogItemFor(ch, activeCatalog.name, routes)?.let {
                    LiveChannelEntry(it, activeCatalog.name, activeCatalog.name)
                },
            )
            if (sources.isEmpty()) null
            else LiveChannelGroup(
                key = ch.channelKey,
                title = live?.title ?: ch.title,
                logoUrl = live?.logoUrl ?: ch.logoUrl,
                routes = routes,
                sources = sources,
            )
        }
    }
    val visibleChannels = remember(allChannels, catalogGroups, activeCatalog, query, sourceFilter, sortMode, activityByKey, orderByKey) {
        val needle = query.tvSearchKey()
        if (activeCatalog != null) {
            if (needle.isBlank()) catalogGroups
            else catalogGroups.filter { channel ->
                channel.title.tvSearchKey().contains(needle) || channel.sources.any { source ->
                    listOf(source.item.title, source.rowTitle, source.providerName).any { it.tvSearchKey().contains(needle) }
                }
            }
        } else {
            val filtered = allChannels.filter { channel ->
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
            LiveChannelSort.sort(
                channels = filtered,
                mode = sortMode,
                keyOf = { it.key },
                titleOf = { it.title },
                activity = activityByKey,
                order = orderByKey,
            )
        }
    }
    val manualOrderKeys = remember(allChannels, activityByKey, orderByKey) {
        LiveChannelSort.sort(
            channels = allChannels,
            mode = LiveTvSortMode.Manual,
            keyOf = { it.key },
            titleOf = { it.title },
            activity = activityByKey,
            order = orderByKey,
        ).map { it.key }
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
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading && result != null) CircularProgressIndicator()
                OutlinedButton(onClick = { showSortDialog = true }) {
                    Icon(PlayIcons.Sort, contentDescription = null)
                    Text(stringResource(R.string.tv_sort), modifier = Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false)
                }
                Button(onClick = { refreshKey++ }, enabled = !loading) {
                    Icon(PlayIcons.Refresh, contentDescription = null)
                    Text(stringResource(R.string.tv_refresh), modifier = Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false)
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
                .navBarReturnEdge(topBarFocusRequester, includeTop = true, includeBottom = allChannels.isEmpty())
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
                selected = activeCatalogId == null && sourceFilter == ChannelSourceFilter.All,
                onClick = { activeCatalogId = null; sourceFilter = ChannelSourceFilter.All },
                label = { Text(stringResource(R.string.tv_filter_all, allChannels.size), maxLines = 1, softWrap = false) },
            )
            userCatalogs.forEach { catalog ->
                FilterChip(
                    selected = activeCatalogId == catalog.id,
                    onClick = { activeCatalogId = catalog.id; sourceFilter = ChannelSourceFilter.All },
                    label = { Text("${catalog.name} (${catalog.channels.size})", maxLines = 1, softWrap = false) },
                )
            }
            FilterChip(
                selected = activeCatalogId == null && sourceFilter == ChannelSourceFilter.Nuvio,
                onClick = { activeCatalogId = null; sourceFilter = ChannelSourceFilter.Nuvio },
                label = { Text(stringResource(R.string.tv_filter_nuvio, nuvioCount), maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = activeCatalogId == null && sourceFilter == ChannelSourceFilter.CloudStream,
                onClick = { activeCatalogId = null; sourceFilter = ChannelSourceFilter.CloudStream },
                label = { Text(stringResource(R.string.tv_filter_cloudstream, cloudStreamCount), maxLines = 1, softWrap = false) },
            )
        }

        if (activeCatalog != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showRenameDialog = true }) {
                    Text(stringResource(R.string.tv_list_rename), maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { showDeleteListConfirm = true }) {
                    Text(stringResource(R.string.tv_list_delete), color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            }
        }

        when {
            activeCatalog == null && loading && result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            activeCatalog == null && !loading && error != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.tv_load_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { refreshKey++ }) { Text(stringResource(R.string.tv_refresh)) }
            }
            visibleChannels.isEmpty() -> Text(
                text = when {
                    activeCatalog != null && catalogGroups.isEmpty() -> stringResource(R.string.tv_list_empty)
                    activeCatalog == null && allChannels.isEmpty() -> stringResource(R.string.tv_empty)
                    query.isNotBlank() -> stringResource(R.string.tv_no_results, query)
                    else -> stringResource(R.string.tv_filtered_empty)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
            else -> {
                val visibleKeys = remember(visibleChannels) { visibleChannels.map { it.key } }
                val activeList = activeCatalog
                val isCustomListView = activeList != null
                val canReorderCards = if (isCustomListView) query.isBlank() else sortMode == LiveTvSortMode.Manual
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(visibleChannels, key = { _, channel -> channel.key }) { index, channel ->
                        val isFav = activityByKey[channel.key]?.isFavorite == true
                        LiveChannelCard(
                            channel = channel,
                            isFavorite = isFav,
                            showReorder = canReorderCards,
                            showRemoveFromList = isCustomListView,
                            canMoveUp = index > 0,
                            canMoveDown = index < visibleChannels.lastIndex,
                            onClick = {
                                val zapSourceGroups = if (isCustomListView) catalogGroups else visibleChannels
                                val zapIdx = zapSourceGroups.indexOfFirst { it.key == channel.key }.coerceAtLeast(0)
                                val zapCatalogId = activeList?.let { "user_catalog:${it.id}" }
                                if (channel.sources.size == 1) {
                                    val zapList = buildZapChannels(zapSourceGroups, channel.key, LiveCatalogRules.routeFor(channel.primary.item))
                                    onPlay(channel.primary.item, zapList, zapIdx, zapCatalogId)
                                } else {
                                    selectedChannel = channel
                                }
                            },
                            onToggleFavorite = {
                                scope.launch { container.liveChannels.toggleFavorite(channel.key) }
                            },
                            onAddToList = { addToListTarget = channel },
                            onRemoveFromList = {
                                activeList?.let { list ->
                                    scope.launch { container.liveCatalogs.removeChannel(list.id, channel.key) }
                                }
                            },
                            onMoveUp = {
                                scope.launch {
                                    if (activeList != null) {
                                        container.liveCatalogs.moveChannel(activeList.id, channel.key, -1)
                                    } else {
                                        container.liveChannels.moveOrder(
                                            visibleKeys = visibleKeys,
                                            allManualKeys = manualOrderKeys,
                                            channelKey = channel.key,
                                            delta = -1,
                                        )
                                    }
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    if (activeList != null) {
                                        container.liveCatalogs.moveChannel(activeList.id, channel.key, +1)
                                    } else {
                                        container.liveChannels.moveOrder(
                                            visibleKeys = visibleKeys,
                                            allManualKeys = manualOrderKeys,
                                            channelKey = channel.key,
                                            delta = +1,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        ChoiceDialog(
            title = stringResource(R.string.tv_sort),
            options = LiveTvSortMode.entries,
            selected = sortMode,
            label = { mode ->
                when (mode) {
                    LiveTvSortMode.TitleAscending -> stringResource(R.string.tv_sort_title_asc)
                    LiveTvSortMode.TitleDescending -> stringResource(R.string.tv_sort_title_desc)
                    LiveTvSortMode.Recent -> stringResource(R.string.tv_sort_recent)
                    LiveTvSortMode.FavoritesFirst -> stringResource(R.string.tv_sort_favorites)
                    LiveTvSortMode.Manual -> stringResource(R.string.tv_sort_manual)
                }
            },
            onSelect = { chosen ->
                showSortDialog = false
                scope.launch { container.settings.setLiveTvSortMode(chosen) }
            },
            onDismiss = { showSortDialog = false },
        )
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
                            val activeList = activeCatalog
                            val zapSourceGroups = if (activeList != null) catalogGroups else visibleChannels
                            val index = zapSourceGroups.indexOfFirst { it.key == group.key }.coerceAtLeast(0)
                            val zapList = buildZapChannels(zapSourceGroups, group.key, LiveCatalogRules.routeFor(source.item))
                            onPlay(source.item, zapList, index, activeList?.let { "user_catalog:${it.id}" })
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

    addToListTarget?.let { target ->
        AddChannelToListDialog(
            channel = target,
            catalogs = userCatalogs,
            onAddToExisting = { cat ->
                addToListTarget = null
                val zapChannel = LiveZapChannel(
                    channelKey = target.key,
                    title = target.title,
                    logoUrl = target.logoUrl,
                    routes = target.routes.ifEmpty { listOfNotNull(LiveCatalogRules.routeFor(target.primary.item)) },
                    isAvailable = true,
                )
                scope.launch {
                    container.liveCatalogs.addChannel(cat.id, zapChannel)
                    Toast.makeText(context, context.getString(R.string.tv_list_added, cat.name), Toast.LENGTH_SHORT).show()
                }
            },
            onCreateAndAdd = { newName ->
                addToListTarget = null
                val zapChannel = LiveZapChannel(
                    channelKey = target.key,
                    title = target.title,
                    logoUrl = target.logoUrl,
                    routes = target.routes.ifEmpty { listOfNotNull(LiveCatalogRules.routeFor(target.primary.item)) },
                    isAvailable = true,
                )
                scope.launch {
                    val createdId = container.liveCatalogs.createAndAddChannel(newName, zapChannel)
                    if (createdId != null) {
                        Toast.makeText(context, context.getString(R.string.tv_list_added, newName.trim()), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { addToListTarget = null },
        )
    }

    if (showRenameDialog && activeCatalog != null) {
        RenameListDialog(
            currentName = activeCatalog.name,
            onRename = { nextName ->
                showRenameDialog = false
                scope.launch { container.liveCatalogs.renameCatalog(activeCatalog.id, nextName) }
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteListConfirm && activeCatalog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteListConfirm = false },
            title = { Text(stringResource(R.string.tv_list_delete_title)) },
            text = { Text(stringResource(R.string.tv_list_delete_message, activeCatalog.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = activeCatalog.id
                        showDeleteListConfirm = false
                        activeCatalogId = null
                        scope.launch { container.liveCatalogs.deleteCatalog(id) }
                    },
                ) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteListConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun LiveChannelCard(
    channel: LiveChannelGroup,
    isFavorite: Boolean,
    showReorder: Boolean,
    showRemoveFromList: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveFromList: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val entry = channel.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    fallbackUrl = channel.logoUrl,
                    posterId = PosterUrlTemplate.findImdbId(
                        entry.item.id,
                        entry.item.rawUrl,
                        channel.logoUrl,
                    ),
                    tmdbId = entry.item.id.toIntOrNull() ?: PosterUrlTemplate.findTmdbId(
                        entry.item.id,
                        entry.item.rawUrl,
                        channel.logoUrl,
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
                        text = channel.title,
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
                val routeCount = if (channel.routes.isNotEmpty()) channel.routes.size else channel.sources.size
                Text(
                    text = if (routeCount > 1) stringResource(R.string.tv_sources_count, routeCount) else entry.providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
            Text(
                text = channel.title,
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) PlayIcons.StarFilled else PlayIcons.Star,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.tv_favorite_remove else R.string.tv_favorite_add,
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onAddToList,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = PlayIcons.Plus,
                    contentDescription = stringResource(R.string.tv_list_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (showRemoveFromList) {
                IconButton(
                    onClick = onRemoveFromList,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = PlayIcons.Trash,
                        contentDescription = stringResource(R.string.tv_list_remove_channel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (showReorder) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = PlayIcons.ChevronUp,
                        contentDescription = stringResource(R.string.tv_move_up),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = PlayIcons.ChevronDown,
                        contentDescription = stringResource(R.string.tv_move_down),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddChannelToListDialog(
    channel: LiveChannelGroup,
    catalogs: List<LiveTvCatalog>,
    onAddToExisting: (LiveTvCatalog) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    val nameField = rememberCursorField(newName) { newName = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.tv_list_add_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                catalogs.forEach { cat ->
                    val alreadyIn = cat.channels.any { it.channelKey == channel.key }
                    OutlinedButton(
                        onClick = { onAddToExisting(cat) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (alreadyIn) {
                                "${cat.name} (${stringResource(R.string.tv_list_already_added)})"
                            } else {
                                "${cat.name} (${cat.channels.size})"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.tv_list_new),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = nameField.value,
                    onValueChange = nameField.onValueChange,
                    placeholder = { Text(stringResource(R.string.tv_list_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newName.isNotBlank()) onCreateAndAdd(newName)
                    }),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(nameField),
                )
                Button(
                    onClick = { if (newName.isNotBlank()) onCreateAndAdd(newName) },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tv_list_create_and_add))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun RenameListDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    val field = rememberCursorField(name) { name = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.tv_list_rename_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = field.value,
                onValueChange = field.onValueChange,
                placeholder = { Text(stringResource(R.string.tv_list_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onRename(name)
                }),
                modifier = Modifier.fillMaxWidth().dpadLeavesTextField(field),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun buildZapChannels(
    channels: List<LiveChannelGroup>,
    selectedKey: String,
    selectedRoute: LiveSourceRoute?,
): List<LiveZapChannel> = channels.map { group ->
    val baseRoutes = group.routes.ifEmpty { listOfNotNull(LiveCatalogRules.routeFor(group.primary.item)) }
    val orderedRoutes = if (group.key == selectedKey && selectedRoute != null) {
        listOf(selectedRoute) + baseRoutes.filter { it != selectedRoute }
    } else {
        baseRoutes
    }
    LiveZapChannel(
        channelKey = group.key,
        title = group.title,
        logoUrl = group.logoUrl,
        routes = orderedRoutes,
        isAvailable = orderedRoutes.isNotEmpty(),
    )
}

private fun String.tvSearchKey(): String = Normalizer.normalize(
    lowercase(Locale.forLanguageTag("tr")).replace('ı', 'i'),
    Normalizer.Form.NFD,
).replace(Regex("\\p{Mn}+"), "")
