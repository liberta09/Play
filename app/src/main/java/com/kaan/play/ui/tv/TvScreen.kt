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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
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
import com.kaan.play.data.live.LiveCategory
import com.kaan.play.data.live.LiveCategoryRules
import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
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
object TvScreenRules {
    data class ChannelItem(
        val key: String,
        val title: String,
        val sources: List<String>,
        val category: LiveCategory,
    )

    fun calculateGridColumns(screenWidthDp: Int): Int {
        return maxOf(3, screenWidthDp / 130)
    }

    fun showSourceRow(
        userCatalogsCount: Int,
        nuvioCount: Int,
        cloudStreamCount: Int,
    ): Boolean {
        return userCatalogsCount > 0 || (nuvioCount > 0 && cloudStreamCount > 0)
    }

    fun shouldShowSourceChip(count: Int): Boolean {
        return count > 0
    }

    fun filterChannels(
        channels: List<ChannelItem>,
        source: String?,
        category: LiveCategory?,
        query: String,
    ): List<ChannelItem> {
        val needle = query.trim().lowercase()
        return channels.filter { ch ->
            val matchesSource = when (source) {
                "nuvio" -> ch.sources.any { it.startsWith("nuvio_addon:") }
                "cs" -> ch.sources.any { it.startsWith("cs:") }
                else -> true
            }
            val matchesCategory = category == null || ch.category == category
            val matchesQuery = needle.isBlank() || ch.title.lowercase().contains(needle)
            matchesSource && matchesCategory && matchesQuery
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<LiveCategory?>(null) }
    val chipFocusMemory = rememberFocusMemory()
    var selectedChannel by remember { mutableStateOf<LiveChannelGroup?>(null) }
    var addToListTarget by remember { mutableStateOf<LiveChannelGroup?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteListConfirm by remember { mutableStateOf(false) }
    var listAddedToastName by remember { mutableStateOf<String?>(null) }

    val listAddedToastText = listAddedToastName?.let { stringResource(R.string.tv_list_added, it) }
    LaunchedEffect(listAddedToastName, listAddedToastText) {
        if (listAddedToastText != null) {
            Toast.makeText(context, listAddedToastText, Toast.LENGTH_SHORT).show()
            listAddedToastName = null
        }
    }

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
    val currentCatalogChannels = remember(activeCatalog, allChannels, catalogGroups, sourceFilter) {
        if (activeCatalog != null) {
            catalogGroups
        } else {
            allChannels.filter { channel ->
                when (sourceFilter) {
                    ChannelSourceFilter.All -> true
                    ChannelSourceFilter.Nuvio -> channel.sources.any { it.item.providerId.startsWith("nuvio_addon:") }
                    ChannelSourceFilter.CloudStream -> channel.sources.any { it.item.providerId.startsWith("cs:") }
                }
            }
        }
    }
    val categoryCounts = remember(currentCatalogChannels) {
        val map = mutableMapOf<LiveCategory, Int>()
        currentCatalogChannels.forEach { ch ->
            val cat = LiveCategoryRules.classify(ch.title, ch.sources.firstOrNull()?.rowTitle)
            map[cat] = (map[cat] ?: 0) + 1
        }
        map
    }
    val visibleCategories = remember(categoryCounts) {
        LiveCategory.entries.filter { (categoryCounts[it] ?: 0) > 0 }
    }
    val visibleChannels = remember(allChannels, catalogGroups, activeCatalog, query, sourceFilter, selectedCategory, sortMode, activityByKey, orderByKey) {
        val needle = query.tvSearchKey()
        val baseChannels = if (activeCatalog != null) {
            catalogGroups
        } else {
            allChannels.filter { channel ->
                when (sourceFilter) {
                    ChannelSourceFilter.All -> true
                    ChannelSourceFilter.Nuvio -> channel.sources.any { it.item.providerId.startsWith("nuvio_addon:") }
                    ChannelSourceFilter.CloudStream -> channel.sources.any { it.item.providerId.startsWith("cs:") }
                }
            }
        }
        val categoryFiltered = if (selectedCategory == null) {
            baseChannels
        } else {
            baseChannels.filter { LiveCategoryRules.classify(it.title, it.sources.firstOrNull()?.rowTitle) == selectedCategory }
        }
        val textFiltered = if (needle.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { channel ->
                channel.title.tvSearchKey().contains(needle) || channel.sources.any { source ->
                    listOf(source.item.title, source.rowTitle, source.providerName).any { it.tvSearchKey().contains(needle) }
                }
            }
        }
        if (activeCatalog != null) {
            textFiltered
        } else {
            LiveChannelSort.sort(
                channels = textFiltered,
                mode = sortMode,
                keyOf = { it.key },
                titleOf = { it.title },
                activity = activityByKey,
                order = orderByKey,
            )
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
                Text(stringResource(R.string.tv_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = pluralStringResource(R.plurals.tv_live_channels_count, visibleChannels.size, visibleChannels.size),
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

        val categoryButtonFocusRequester = remember { FocusRequester() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field.value,
                onValueChange = field.onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                    .focusRequester(initialFocus)
                    .navBarReturnEdge(topBarFocusRequester, includeTop = true, includeBottom = allChannels.isEmpty())
                    .dpadLeavesTextField(field)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight &&
                            (field.atStart && field.value.text.isEmpty() || field.value.selection.collapsed && field.value.selection.end == field.value.text.length)
                        ) {
                            categoryButtonFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
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

            val isCategorySelected = selectedCategory != null
            val categoryButtonColor = if (isCategorySelected) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    contentColor = Color(0xFF00E5FF),
                )
            } else ButtonDefaults.outlinedButtonColors()
            val categoryBorder = if (isCategorySelected) {
                BorderStroke(1.dp, Color(0xFF00E5FF))
            } else BorderStroke(1.dp, Color(0xFF1E3A5F))

            androidx.compose.material3.OutlinedButton(
                onClick = { showCategorySheet = true },
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(categoryButtonFocusRequester)
                    .focusRing(RoundedCornerShape(14.dp)),
                colors = categoryButtonColor,
                border = categoryBorder,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(PlayIcons.Sort, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tv_category))
            }
        }

        if (selectedCategory != null) {
            InputChip(
                selected = true,
                onClick = { selectedCategory = null },
                label = { Text(stringResource(selectedCategory!!.titleRes)) },
                trailingIcon = {
                    Icon(
                        imageVector = PlayIcons.Close,
                        contentDescription = stringResource(R.string.tv_category_remove),
                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    )
                },
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    selectedLabelColor = Color(0xFF00E5FF),
                    selectedTrailingIconColor = Color(0xFF00E5FF),
                ),
                border = InputChipDefaults.inputChipBorder(
                    enabled = true,
                    selected = true,
                    borderColor = Color(0xFF00E5FF),
                    borderWidth = 1.dp,
                ),
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            )
        }

        // Çipler kendi genişliğini alır ve satır yana kayar; etiket tek satırdır.
        val showSourceRow = TvScreenRules.showSourceRow(userCatalogs.size, nuvioCount, cloudStreamCount)
        if (showSourceRow) {
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
                if (TvScreenRules.shouldShowSourceChip(nuvioCount)) {
                    FilterChip(
                        selected = activeCatalogId == null && sourceFilter == ChannelSourceFilter.Nuvio,
                        onClick = { activeCatalogId = null; sourceFilter = ChannelSourceFilter.Nuvio },
                        label = { Text(stringResource(R.string.tv_filter_nuvio, nuvioCount), maxLines = 1, softWrap = false) },
                    )
                }
                if (TvScreenRules.shouldShowSourceChip(cloudStreamCount)) {
                    FilterChip(
                        selected = activeCatalogId == null && sourceFilter == ChannelSourceFilter.CloudStream,
                        onClick = { activeCatalogId = null; sourceFilter = ChannelSourceFilter.CloudStream },
                        label = { Text(stringResource(R.string.tv_filter_cloudstream, cloudStreamCount), maxLines = 1, softWrap = false) },
                    )
                }
            }
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
                    query.isNotBlank() -> stringResource(R.string.tv_no_results_with_search)
                    else -> stringResource(R.string.tv_filtered_empty)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
            else -> {
                val activeList = activeCatalog
                val isCustomListView = activeList != null
                val columnsCount = TvScreenRules.calculateGridColumns(LocalConfiguration.current.screenWidthDp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(visibleChannels, key = { channel -> channel.key }) { channel ->
                        LiveChannelCompactCard(
                            channel = channel,
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
                            }
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
                    listAddedToastName = cat.name
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
                        listAddedToastName = newName.trim()
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

    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets.navigationBars },
        ) {
            Text(
                text = stringResource(R.string.tv_category_select),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val allItems = remember(visibleCategories) {
                    listOf<LiveCategory?>(null) + visibleCategories
                }
                allItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val first = pair[0]
                        val firstTitle = if (first == null) stringResource(R.string.live_category_all) else stringResource(first.titleRes)
                        val firstCount = if (first == null) currentCatalogChannels.size else (categoryCounts[first] ?: 0)
                        val firstSelected = selectedCategory == first
                        CategorySheetItem(
                            title = firstTitle,
                            count = firstCount,
                            isSelected = firstSelected,
                            onClick = {
                                selectedCategory = first
                                showCategorySheet = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusMemory(chipFocusMemory, if (first == null) "cat-all" else "cat-${first.id}", isDefault = firstSelected),
                        )
                        if (pair.size > 1) {
                            val second = pair[1]
                            val secondTitle = if (second == null) stringResource(R.string.live_category_all) else stringResource(second.titleRes)
                            val secondCount = if (second == null) currentCatalogChannels.size else (categoryCounts[second] ?: 0)
                            val secondSelected = selectedCategory == second
                            CategorySheetItem(
                                title = secondTitle,
                                count = secondCount,
                                isSelected = secondSelected,
                                onClick = {
                                    selectedCategory = second
                                    showCategorySheet = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusMemory(chipFocusMemory, if (second == null) "cat-all" else "cat-${second.id}", isDefault = secondSelected),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySheetItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFF0F1E2E)
    val borderColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E3A5F)
    val contentColor = if (isSelected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusRing(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveChannelCompactCard(
    channel: LiveChannelGroup,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp) // 14dp köşe
    val bgColor = Color(0xFF0F1E2E)
    val focusedBorderColor = Color(0xFF062A3B) // seçili
    val normalBorderColor = Color(0xFF1E3A5F) // çerçeve

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape, scale = 1.05f)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f) // 16:10
                .clip(shape)
                .background(bgColor)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) focusedBorderColor else normalBorderColor,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center
        ) {
            val logoUrl = channel.logoUrl
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            } else {
                val initials = channel.title.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.tv_live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
