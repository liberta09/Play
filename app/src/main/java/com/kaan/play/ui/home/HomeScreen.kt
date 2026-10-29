package com.kaan.play.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.kaan.play.ui.settings.ProfileAvatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kaan.play.BuildConfig
import com.kaan.play.MainActivity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.kaan.play.R
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.ContinueSortMode
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.library.sortContinueItems
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.ProviderRules
import com.kaan.play.data.addon.CatalogVisibility
import com.kaan.play.ui.cloudflare.CloudflareVerifyActivity
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.provider.ProviderSelectorSheet
import com.kaan.play.ui.rememberContainer
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.kaan.play.ui.tv.categoryHeaderFocusable
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.rememberPlayLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.kaan.play.ui.tv.FocusMemory

import androidx.compose.runtime.mutableIntStateOf
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogResult
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.provider.ProviderItem
import com.kaan.play.data.tmdb.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ScreenPadding = 20.dp

/**
 * Ana sayfa. Hiç eklenti yokken tek cümle + tek buton; eklenti varken kapak karuseli, "devam et" ve TMDB satırları.
 * Sağlayıcı seçildiğinde seçili sağlayıcı(lar)ın kendi kataloğu gösterilir (Faz B).
 */
@Composable
fun HomeScreen(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    onAddAddon: () -> Unit,
    onOpenSearch: (String) -> Unit = {},
    onOpenProviderDetail: (providerId: String, url: String) -> Unit = { _, _ -> },
    onOpenProfile: () -> Unit = {},
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val hasSource by container.hasAnySource.collectAsStateWithLifecycle(initialValue = null)
    val selectedProviders by container.providers.selectedProviders.collectAsStateWithLifecycle(initialValue = emptySet())
    val activeProfileId by container.localProfile.id.collectAsStateWithLifecycle(initialValue = "")
    val watchHistory by container.library.watchHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedProfiles by container.localProfile.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val childProfile = savedProfiles.firstOrNull { it.id == activeProfileId }?.isChild == true
    // null: liste henüz yükleniyor (boş listeyle karıştırılmasın; bkz. ProviderRules.resolveSelection).
    val loadedProviders by container.providers.allProviders.collectAsStateWithLifecycle(initialValue = null)
    val allProviders = loadedProviders.orEmpty()
    var showProviderSelector by remember { mutableStateOf(false) }
    var activeProviderRow by remember { mutableStateOf<CatalogRow?>(null) }

    val currentProviderRow = activeProviderRow
    if (currentProviderRow != null && !childProfile) {
        BackHandler { activeProviderRow = null }
        ProviderCategoryScreen(
            row = currentProviderRow,
            onBack = { activeProviderRow = null },
            onOpenDetail = onOpenDetail,
            onOpenProviderDetail = onOpenProviderDetail,
            onOpenSearch = onOpenSearch,
        )
        return
    }

    val selection = remember(loadedProviders, selectedProviders) {
        ProviderRules.resolveSelection(selectedProviders, loadedProviders)
    }
    val validSelected = selection.valid

    // Silinen/kapatılan sağlayıcı seçiliyse sessizce düşür; liste yüklenirken hiçbir şey düşürülmez.
    LaunchedEffect(selection.stale) {
        if (selection.stale.isNotEmpty()) {
            container.providers.setSelectedProviders(selectedProviders - selection.stale)
        }
    }

    val effectiveIsAll = selection.showAll
    val pillText = when {
        effectiveIsAll || validSelected.isEmpty() -> stringResource(R.string.provider_pill_select)
        validSelected.size == 1 -> validSelected.first().name
        else -> stringResource(R.string.provider_pill_multiple, validSelected.first().name, validSelected.size - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A24),
                        Color(0xFF0A0D14),
                        Color(0xFF040508),
                    ),
                    radius = 1500f,
                )
            )
    ) {
        when {
            childProfile -> HomeContent(
                onOpenDetail = onOpenDetail,
                onOpenCategory = onOpenCategory,
                onContinue = onContinue,
                watchHistory = watchHistory,
                onPlayHistory = onPlayHistory,
                onOpenProviderDetail = onOpenProviderDetail,
                onOpenProviderCategory = { activeProviderRow = it },
                onOpenSearch = onOpenSearch,
                showProviderCatalogs = false,
                childProfile = true,
                contentFocusRequester = contentFocusRequester,
                topBarFocusRequester = topBarFocusRequester,
            )
            hasSource == null -> Box(Modifier.fillMaxSize())
            !effectiveIsAll -> ProviderCatalogView(
                selectedIds = selectedProviders,
                validSelected = validSelected,
                onOpenDetail = onOpenDetail,
                onOpenProviderDetail = onOpenProviderDetail,
                onOpenSearch = onOpenSearch,
                onOpenCategory = onOpenCategory,
                onContinue = onContinue,
                onPlayHistory = onPlayHistory,
                watchHistory = watchHistory,
                onOpenProviderCategory = { activeProviderRow = it },
                contentFocusRequester = contentFocusRequester,
                topBarFocusRequester = topBarFocusRequester,
            )
            hasSource == false -> HomeEmptyState(
                onAddAddon = onAddAddon,
                watchHistory = watchHistory,
                onPlayHistory = onPlayHistory,
                contentFocusRequester = contentFocusRequester,
                topBarFocusRequester = topBarFocusRequester,
            )
            else -> HomeContent(
                onOpenDetail = onOpenDetail,
                onOpenCategory = onOpenCategory,
                onContinue = onContinue,
                watchHistory = watchHistory,
                onPlayHistory = onPlayHistory,
                onOpenProviderDetail = onOpenProviderDetail,
                onOpenProviderCategory = { activeProviderRow = it },
                onOpenSearch = onOpenSearch,
                contentFocusRequester = contentFocusRequester,
                topBarFocusRequester = topBarFocusRequester,
            )
        }

        StatusBarScrim()

        // Yüzen hap buton (sağ altta, alt menünün hemen üzerinde)
        if (!childProfile) ProviderPillButton(
            text = pillText,
            isSelected = !effectiveIsAll,
            onClick = { showProviderSelector = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )

        if (showProviderSelector && !childProfile) {
            ProviderSelectorSheet(
                providers = if (BuildConfig.DEBUG && MainActivity.testManyProviders) {
                    allProviders + (1..60).map {
                        ProviderItem(
                            id = "cs:fake_$it",
                            name = "Test Sağlayıcı #$it",
                            subtitle = "CloudStream · Test Repo",
                            kind = com.kaan.play.data.provider.ProviderKind.CloudStream,
                        )
                    }
                } else {
                    allProviders
                },
                selectedIds = selectedProviders,
                // Uygulama kapsamında yazılır: seçici kapanırken ya da ekran değişirken yazma iptal olmasın.
                onSelectSingle = { id ->
                    container.appScope.launch { container.providers.setSelectedProviders(ProviderRules.singleSelect(id)) }
                },
                onApplyMulti = { ids ->
                    container.appScope.launch { container.providers.setSelectedProviders(ids) }
                },
                onTogglePin = { id ->
                    scope.launch { container.providers.togglePin(id) }
                },
                onDismiss = { showProviderSelector = false },
                onAddExtension = onAddAddon,
                isLoading = loadedProviders == null,
            )
        }
    }
}

@Composable
private fun HomeEmptyState(
    onAddAddon: () -> Unit,
    watchHistory: List<WatchHistoryEntity>,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val focus = rememberInitialFocus()
        Button(
            onClick = onAddAddon,
            modifier = Modifier
                .focusRequester(focus)
                .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                .let { if (topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it },
        ) { Text(stringResource(R.string.home_empty_action)) }
    }
}

@Composable
private fun HomeContent(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    watchHistory: List<WatchHistoryEntity>,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    onOpenProviderDetail: (providerId: String, url: String) -> Unit = { _, _ -> },
    onOpenProviderCategory: (CatalogRow) -> Unit = {},
    onOpenSearch: (String) -> Unit = {},
    showProviderCatalogs: Boolean = true,
    childProfile: Boolean = false,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val homeProviders by container.providers.homeProviders.collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleHomeProviders = if (showProviderCatalogs) homeProviders else emptyList()
    val loadSemaphore = remember { Semaphore(3) }

    // Dil değişince (etkinlik yeniden kurulur) yeni dille taze bir ViewModel: satırlar yeni dilde yüklenir.
    val vm = containerViewModel(key = "home-${AppLanguage.effective().iso}") { HomeViewModel(it.tmdb, it.continueWatching) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    LaunchedEffect(childProfile) {
        if (childProfile) {
            vm.loadHero()
            HomeRow.entries.forEach(vm::load)
        }
    }
    // Durum kompozisyon kapsamında okunur (LazyColumn DSL bloğunda değil): değişince bu ekran yeniden çizilir.
    val continueWatching = vm.continueWatching.collectAsStateWithLifecycle().value
    var continueSort by rememberSaveable { mutableStateOf(ContinueSortMode.RecentFirst) }
    val sortedContinueWatching = remember(continueWatching, continueSort) { sortContinueItems(continueWatching, continueSort) }
    val layout = rememberPlayLayout()
    val cardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp
    val heroHeight = heroHeightFor(layout.widthDp, layout.heightDp)

    val heroItems = (hero as? Loadable.Ready)?.value.orEmpty()
    val hasHero = heroItems.isNotEmpty()
    val hasContinue = continueWatching.isNotEmpty()
    // Kumanda: başlangıç odağı kapak düğmesinde (yoksa devam et / ilk satırın ilk kartında); geri dönünce son odaklanan öğe.
    // Kapak yüklenirken beklenir ki odak sonradan eklenen öğenin altına düşmesin.
    val firstReady = hero !is Loadable.Loading && (hasHero || hasContinue || rows.getValue(HomeRow.TrendingMovies) is Loadable.Ready)
    val focus = rememberFocusMemory(enabled = firstReady)

    // LazyColumn anahtar bazlı konumu korur: en üste sonradan eklenen "devam et" satırı ekranın üstüne itilirdi.
    val listState = rememberLazyListState()
    LaunchedEffect(hasContinue) { if (hasContinue) listState.scrollToItem(0) }

    // Sağlayıcı raflarıyla aynı adı taşıyan TMDB rafı (ör. "Popüler filmler") ikinci kopya olarak gizlenir;
    // yalnızca gerçekten görünen sağlayıcı rafları sayılır (addon rafı yüklenemezse TMDB rafı kalır).
    val shownProviderTitles = remember { mutableStateMapOf<String, List<String>>() }
    val tmdbTitles = HomeRow.entries.associateWith { stringResource(it.titleRes()) }
    val providerIds = visibleHomeProviders.mapTo(HashSet()) { it.id }
    val hiddenTmdbRows = ProviderRules.duplicateTmdbRows(
        tmdbTitles,
        shownProviderTitles.filterKeys { it in providerIds }.values.flatten(),
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "hero") {
            when {
                hasHero -> HeroCarousel(
                    items = heroItems,
                    height = heroHeight,
                    focus = focus,
                    isDefaultFocus = true,
                    onOpen = onOpenDetail,
                    contentFocusRequester = contentFocusRequester,
                    topBarFocusRequester = topBarFocusRequester,
                )
                // Yükleniyor: aynı boyutta sönük kutu (yerleşim zıplamasın). Başarısızsa kapak hiç çıkmaz.
                hero is Loadable.Loading -> Box(Modifier.fillMaxWidth().height(heroHeight).background(MaterialTheme.colorScheme.surfaceVariant))
                else -> Spacer(Modifier.statusBarsPadding().height(8.dp))
            }
        }
        if (hasContinue) {
            item(key = "continue") {
                Section(
                    title = stringResource(R.string.home_row_continue),
                    headerAction = { ContinueSortControl(continueSort) { continueSort = it } },
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedContinueWatching, key = { "${it.entry.type}-${it.entry.tmdbId}" }) { item ->
                            val isFirstContinue = !hasHero && item == sortedContinueWatching.firstOrNull()
                            ContinueCard(
                                item,
                                modifier = Modifier
                                    .let { if (isFirstContinue && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                                    .let { if (isFirstContinue && topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                                    .focusMemory(
                                        focus, "c-${item.entry.type}-${item.entry.tmdbId}",
                                        isDefault = isFirstContinue,
                                    ),
                                width = cardWidth,
                            ) { onContinue(item) }
                        }
                    }
                }
            }
        }
        visibleHomeProviders.forEach { provider ->
            item(key = "home_provider_${provider.id}") {
                LazyProviderSection(
                    provider = provider,
                    cardWidth = cardWidth,
                    focus = focus,
                    loadSemaphore = loadSemaphore,
                    onOpenDetail = onOpenDetail,
                    onOpenProviderDetail = onOpenProviderDetail,
                    onOpenProviderCategory = onOpenProviderCategory,
                    onOpenSearch = onOpenSearch,
                    onShownTitles = { titles -> shownProviderTitles[provider.id] = titles },
                )
            }
        }
        HomeRow.entries.filterNot { it in hiddenTmdbRows }.forEach { row ->
            item(key = row.name) {
                Section(
                    title = stringResource(row.titleRes()),
                    onSeeAll = { onOpenCategory(row) },
                    headerModifier = Modifier.focusMemory(focus, "h-${row.name}"),
                ) {
                    when (val state = rows.getValue(row)) {
                        Loadable.Loading -> SkeletonRow(cardWidth)
                        is Loadable.Failed -> ErrorMessage(state.error, onRetry = { vm.load(row) }, modifier = Modifier.fillMaxWidth())
                        is Loadable.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.value, key = { "${it.type}-${it.id}" }) { media ->
                                val isFirstTrending = !hasHero && !hasContinue && row == HomeRow.TrendingMovies && media == state.value.first()
                                PosterCard(
                                    title = media.title,
                                    subtitle = media.year?.toString(),
                                    imageUrl = media.posterUrl(),
                                    tmdbId = media.id,
                                    mediaType = media.type,
                                    onClick = { onOpenDetail(media.type, media.id) },
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .let { if (isFirstTrending && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                                        .let { if (isFirstTrending && topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                                        .focusMemory(
                                            focus,
                                            "${row.name}-${media.type}-${media.id}",
                                            isDefault = isFirstTrending,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Kapak yüksekliği (dp): dik telefonda genişliğe yakın, geniş/yatay ekranda alçak ve en çok ekran yüksekliğinin
 * ~%62'si; çok alçak yatay ekranda bile içeriğe (logo, satır, düğme) yetecek kadar.
 */
internal fun heroHeightFor(widthDp: Int, heightDp: Int): Dp {
    val byWidth = if (widthDp < 600) widthDp * 1.1f else widthDp * 0.42f
    return byWidth.coerceAtMost(heightDp * 0.62f).coerceIn(260f, 520f).dp
}

@Composable
private fun Section(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val seeAll = stringResource(R.string.home_see_all)
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .categoryHeaderFocusable(
                    interactionSource = interactionSource,
                    onClick = onSeeAll,
                    scale = 1.02f,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = seeAll,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = PlayIcons.Forward,
                        contentDescription = seeAll,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            headerAction?.invoke()
        }
        content()
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val entry = item.entry
    val container = rememberContainer()
    val actionContext = LocalContext.current.applicationContext
    var showActions by remember(entry.id) { mutableStateOf(false) }
    val subtitle: String
    val progress: Float?
    when (item) {
        is ContinueItem.Resume -> {
            val minutesLeft = (((entry.durationMs - entry.positionMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            subtitle = if (entry.type == MediaType.Tv) {
                stringResource(R.string.continue_left_episode, entry.season, entry.episode, minutesLeft)
            } else {
                stringResource(R.string.continue_left_movie, minutesLeft)
            }
            progress = entry.positionMs.toFloat() / entry.durationMs.toFloat()
        }
        // Sıradaki bölüm önerisi: henüz izlenmedi, ilerleme çubuğu yok.
        is ContinueItem.Next -> {
            subtitle = stringResource(R.string.continue_next_episode, item.season, item.episode)
            progress = null
        }
    }
    PosterCard(
        title = entry.title,
        subtitle = subtitle,
        imageUrl = TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster),
        tmdbId = entry.tmdbId,
        mediaType = entry.type,
        onClick = onClick,
        onLongClick = { showActions = true },
        modifier = modifier.width(width),
        progress = progress,
    )

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(entry.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.history_actions_hint))
                    TextButton(onClick = {
                        showActions = false
                        container.appScope.launch {
                            try {
                                container.library.addHistoryToFavorites(entry)
                                withContext(Dispatchers.Main.immediate) {
                                    Toast.makeText(actionContext, actionContext.getString(R.string.history_added_favorite), Toast.LENGTH_SHORT).show()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main.immediate) {
                                    Toast.makeText(actionContext, actionContext.getString(R.string.history_action_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_action_favorite)) }
                    TextButton(onClick = {
                        showActions = false
                        container.appScope.launch {
                            try {
                                val marked = container.library.markHistoryWatched(entry)
                                withContext(Dispatchers.Main.immediate) {
                                    val message = if (marked) R.string.history_marked_watched else R.string.history_mark_watched_unavailable
                                    Toast.makeText(actionContext, actionContext.getString(message), Toast.LENGTH_SHORT).show()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main.immediate) {
                                    Toast.makeText(actionContext, actionContext.getString(R.string.history_action_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }, enabled = entry.durationMs > 0, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_action_watched)) }
                    TextButton(onClick = {
                        showActions = false
                        container.appScope.launch {
                            try {
                                container.library.removeContentHistory(entry)
                                withContext(Dispatchers.Main.immediate) {
                                    Toast.makeText(actionContext, actionContext.getString(R.string.history_removed), Toast.LENGTH_SHORT).show()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main.immediate) {
                                    Toast.makeText(actionContext, actionContext.getString(R.string.history_action_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_action_remove), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { showActions = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** Yüklenirken afiş biçiminde sönük kutular: yerleşim yüklenince zıplamaz. */

@Composable
private fun SkeletonRow(cardWidth: Dp) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Spacer(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

internal fun HomeRow.titleRes(): Int = when (this) {
    HomeRow.TrendingMovies -> R.string.home_row_trending_movies
    HomeRow.TrendingSeries -> R.string.home_row_trending_series
    HomeRow.PopularMovies -> R.string.home_row_popular_movies
    HomeRow.PopularSeries -> R.string.home_row_popular_series
}


private sealed interface ProviderSectionUiState {
    data object Loading : ProviderSectionUiState
    data class Ready(val rows: List<CatalogRow>) : ProviderSectionUiState
    data object Hidden : ProviderSectionUiState
}

@Composable
private fun LazyProviderSection(
    provider: ProviderItem,
    cardWidth: Dp,
    focus: FocusMemory,
    loadSemaphore: Semaphore,
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenProviderDetail: (providerId: String, url: String) -> Unit,
    onOpenProviderCategory: (CatalogRow) -> Unit,
    onOpenSearch: (String) -> Unit,
    onShownTitles: (List<String>) -> Unit = {},
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    val disabledCatalogs by container.settings.disabledCatalogs.collectAsStateWithLifecycle(initialValue = emptySet())
    var state by remember(provider.id, disabledCatalogs) {
        val cached = container.catalogRepository.cache.get(provider.id)
        val visible = cached?.let { ProviderRules.withoutLiveRows(CatalogVisibility.filterRows(it, disabledCatalogs)) }
        mutableStateOf<ProviderSectionUiState>(
            when {
                visible == null -> ProviderSectionUiState.Loading
                visible.isEmpty() -> ProviderSectionUiState.Hidden
                else -> ProviderSectionUiState.Ready(visible)
            }
        )
    }

    LaunchedEffect(provider.id, disabledCatalogs) {
        val cached = container.catalogRepository.cache.get(provider.id)
        val visible = cached?.let { ProviderRules.withoutLiveRows(CatalogVisibility.filterRows(it, disabledCatalogs)) }
        if (visible != null) {
            state = if (visible.isNotEmpty()) ProviderSectionUiState.Ready(visible) else ProviderSectionUiState.Hidden
            return@LaunchedEffect
        }
        loadSemaphore.withPermit {
            try {
                val result = container.catalogRepository.loadCatalog(setOf(provider.id))
                // Sunucu kapalı / hata / boş: bölüm sessizce gizlenir (ProviderRules.homeSectionRows).
                val valid = ProviderRules.homeSectionRows(result)
                val filtered = ProviderRules.withoutLiveRows(CatalogVisibility.filterRows(valid, disabledCatalogs))
                state = if (filtered.isNotEmpty()) ProviderSectionUiState.Ready(filtered) else ProviderSectionUiState.Hidden
            } catch (_: Throwable) {
                state = ProviderSectionUiState.Hidden
            }
        }
    }

    // Görünen raf adlarını ana sayfaya bildir (aynı adlı TMDB raflarını gizlemek için).
    LaunchedEffect(state, disabledCatalogs) {
        val rows = (state as? ProviderSectionUiState.Ready)?.rows
        val visible = rows?.let { CatalogVisibility.filterRows(it, disabledCatalogs) }
        onShownTitles(visible?.map { it.title }.orEmpty())
    }

    when (val current = state) {
        is ProviderSectionUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(20.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                SkeletonRow(cardWidth = cardWidth)
            }
        }
        is ProviderSectionUiState.Hidden -> {
            // Sessizce gizlenir (ekran hata kartlarıyla dolmasın)
        }
        is ProviderSectionUiState.Ready -> {
            val visibleRows = remember(current.rows, disabledCatalogs) {
                CatalogVisibility.filterRows(current.rows, disabledCatalogs)
            }
            if (visibleRows.isEmpty()) return

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                visibleRows.forEach { row ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val headerInteraction = remember(row.providerId, row.id) { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding)
                                .focusMemory(focus, "header_${row.providerId}_${row.id}")
                                .categoryHeaderFocusable(
                                    interactionSource = headerInteraction,
                                    onClick = { onOpenProviderCategory(row) },
                                    scale = 1.02f,
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = row.providerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.provider_catalog_show_all),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = PlayIcons.Forward,
                                    contentDescription = stringResource(R.string.provider_catalog_show_all),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(row.items, key = { index, item -> "${row.providerId}_${row.id}_${item.id}_$index" }) { _, item ->
                                PosterCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    imageUrl = item.posterUrl,
                                    posterId = PosterUrlTemplate.findImdbId(item.id, item.rawUrl, item.posterUrl),
                                    tmdbId = item.id.toIntOrNull() ?: PosterUrlTemplate.findTmdbId(item.id, item.rawUrl, item.posterUrl),
                                    mediaType = item.mediaType,
                                    onClick = {
                                        if (!item.rawUrl.isNullOrBlank() && (item.providerId.startsWith("cs:") || item.providerId.startsWith("nuvio_addon:"))) {
                                            onOpenProviderDetail(item.providerId, item.rawUrl)
                                        } else {
                                            scope.launch {
                                                resolveAndOpenPoster(
                                                    item = item,
                                                    tmdb = container.tmdb,
                                                    addons = container.addons,
                                                    onOpenDetail = onOpenDetail,
                                                    onOpenProviderDetail = onOpenProviderDetail,
                                                    onOpenSearch = onOpenSearch,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusMemory(focus, "${row.providerId}_${row.id}_${item.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sağlayıcı kataloğu arayüz durumları.
 */
private sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val rows: List<CatalogRow>) : CatalogUiState
    data object NoCatalogProvided : CatalogUiState
    data object Empty : CatalogUiState
    data class Error(
        val message: String? = null,
        val isBotBlocked: Boolean = false,
        val verifyUrl: String? = null,
        val verifyHost: String? = null,
    ) : CatalogUiState
}

/**
 * Seçili sağlayıcı(lar)ın kendi kataloğunu gösteren ana sayfa bileşeni (Faz B).
 */
@Composable
private fun ProviderCatalogView(
    selectedIds: Set<String>,
    validSelected: List<ProviderItem>,
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenProviderDetail: (providerId: String, url: String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    watchHistory: List<WatchHistoryEntity>,
    onOpenProviderCategory: (CatalogRow) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val layout = rememberPlayLayout()
    val cardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp

    var state by remember(selectedIds) { mutableStateOf<CatalogUiState>(CatalogUiState.Loading) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val verifyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            refreshKey++
        }
    }

    val disabledCatalogs by container.settings.disabledCatalogs.collectAsStateWithLifecycle(initialValue = emptySet())
    val continueWatching by container.continueWatching.items.collectAsStateWithLifecycle(initialValue = emptyList())
    var continueSort by rememberSaveable { mutableStateOf(ContinueSortMode.RecentFirst) }
    val sortedContinueWatching = remember(continueWatching, continueSort) { sortContinueItems(continueWatching, continueSort) }
    val continueCardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp
    LaunchedEffect(selectedIds, refreshKey, disabledCatalogs) {
        state = CatalogUiState.Loading
        val result = container.catalogRepository.loadCatalog(selectedIds, forceRefresh = refreshKey > 0)
        state = when (result) {
            is CatalogResult.Success -> CatalogUiState.Success(ProviderRules.withoutLiveRows(result.rows))
            is CatalogResult.NoCatalogProvided -> CatalogUiState.NoCatalogProvided
            is CatalogResult.Empty -> CatalogUiState.Empty
            is CatalogResult.Error -> CatalogUiState.Error(
                message = result.message,
                isBotBlocked = result.isBotBlocked,
                verifyUrl = result.verifyUrl,
                verifyHost = result.verifyHost,
            )
        }
    }

    when (val current = state) {
        is CatalogUiState.Loading -> {
            ProviderCatalogSkeleton(
                providerNames = validSelected.map { it.name },
                cardWidth = cardWidth,
                modifier = modifier,
            )
        }
        is CatalogUiState.NoCatalogProvided -> {
            Column(modifier = modifier.fillMaxSize()) {
                // Bilgilendirme şeridi
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = ScreenPadding, vertical = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.provider_catalog_no_catalog),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // TMDB akışına düş
                HomeContent(
                    onOpenDetail = onOpenDetail,
                    onOpenCategory = onOpenCategory,
                    onContinue = onContinue,
                    watchHistory = watchHistory,
                    onPlayHistory = onPlayHistory,
                    onOpenProviderDetail = onOpenProviderDetail,
                    onOpenProviderCategory = onOpenProviderCategory,
                    onOpenSearch = onOpenSearch,
                )
            }
        }
        is CatalogUiState.Empty -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.provider_catalog_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { refreshKey++ },
                    modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                ) {
                    Text(stringResource(R.string.provider_catalog_retry))
                }
            }
        }
        is CatalogUiState.Error -> {
            val errorText = if (current.isBotBlocked) {
                stringResource(R.string.provider_verification_required)
            } else {
                stringResource(R.string.provider_catalog_error)
            }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current.isBotBlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                if (current.isBotBlocked) {
                    Text(
                        text = stringResource(R.string.provider_bot_protection_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                val verifyUrl = current.verifyUrl ?: "https://${current.verifyHost.orEmpty()}"
                                val providerName = validSelected.firstOrNull()?.name.orEmpty()
                                val intent = CloudflareVerifyActivity.intent(
                                    context = context,
                                    url = verifyUrl,
                                    host = current.verifyHost.orEmpty(),
                                    name = providerName,
                                )
                                verifyLauncher.launch(intent)
                            },
                            modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                        ) {
                            Text(stringResource(R.string.provider_verify_action))
                        }
                        Button(
                            onClick = { refreshKey++ },
                            modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                        ) {
                            Text(stringResource(R.string.provider_catalog_retry))
                        }
                    }
                } else {
                    Button(
                        onClick = { refreshKey++ },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                    ) {
                        Text(stringResource(R.string.provider_catalog_retry))
                    }
                }
            }
        }
        is CatalogUiState.Success -> {
            val rows = remember(current.rows, disabledCatalogs) {
                CatalogVisibility.filterRows(current.rows, disabledCatalogs)
            }
            if (rows.isEmpty()) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.provider_catalog_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { refreshKey++ },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                    ) {
                        Text(stringResource(R.string.provider_catalog_retry))
                    }
                }
            } else {
                val listState = rememberLazyListState()

                LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (continueWatching.isNotEmpty()) {
                    item(key = "provider_continue") {
                        Section(
                            title = stringResource(R.string.home_row_continue),
                            headerAction = { ContinueSortControl(continueSort) { continueSort = it } },
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = ScreenPadding),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(sortedContinueWatching, key = { "provider-continue-${it.entry.id}" }) { item ->
                                    ContinueCard(item, width = continueCardWidth) { onContinue(item) }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(rows, key = { index, row -> "${row.providerId}_${row.id}_$index" }) { _, row ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val headerInteraction = remember(row.providerId, row.id) { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding)
                                .categoryHeaderFocusable(
                                    interactionSource = headerInteraction,
                                    onClick = { onOpenProviderCategory(row) },
                                    scale = 1.02f,
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (validSelected.size > 1) {
                                    Text(
                                        text = row.providerName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.provider_catalog_show_all),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = PlayIcons.Forward,
                                    contentDescription = stringResource(R.string.provider_catalog_show_all),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(row.items, key = { index, item -> "${row.id}_${item.id}_$index" }) { _, item ->
                                PosterCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    imageUrl = item.posterUrl,
                                    posterId = PosterUrlTemplate.findImdbId(item.id, item.rawUrl, item.posterUrl),
                                    tmdbId = item.id.toIntOrNull() ?: PosterUrlTemplate.findTmdbId(item.id, item.rawUrl, item.posterUrl),
                                    mediaType = item.mediaType,
                                    onClick = {
                                        if (!item.rawUrl.isNullOrBlank() && (item.providerId.startsWith("cs:") || item.providerId.startsWith("nuvio_addon:"))) {
                                            onOpenProviderDetail(item.providerId, item.rawUrl)
                                        } else {
                                            scope.launch {
                                                resolveAndOpenPoster(
                                                    item = item,
                                                    tmdb = container.tmdb,
                                                    addons = container.addons,
                                                    onOpenDetail = onOpenDetail,
                                                    onOpenProviderDetail = onOpenProviderDetail,
                                                    onOpenSearch = onOpenSearch,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.width(cardWidth),
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

/**
 * Sağlayıcı kataloğu yüklenirken gösterilen iskelet shimmer yer tutucusu.
 */
@Composable
private fun ProviderCatalogSkeleton(
    providerNames: List<String>,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val isVerifying by CloudflareSolver.isVerifying.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val title = if (providerNames.size <= 1) {
                    providerNames.firstOrNull() ?: stringResource(R.string.provider_selector_title)
                } else {
                    providerNames.joinToString(", ")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isVerifying) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.provider_verifying_site),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        items(3) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = ScreenPadding)
                        .width(130.dp)
                        .height(18.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                SkeletonRow(cardWidth = cardWidth)
            }
        }
    }
}

/**
 * Katalog öğesi başlığındaki eklentiye özgü bölüm/sezon/kalite etiketlerini temizler:
 * "S4B11 - LINK CLICK" -> "LINK CLICK"
 * "1. Sezon 5. Bölüm - Stranger Things" -> "Stranger Things"
 * "Dizi Adı - 12. Bölüm" -> "Dizi Adı"
 * "Film (Türkçe Dublaj)" -> "Film"
 */
internal fun cleanCatalogTitle(raw: String): String = com.kaan.play.data.stream.TitleCleaner.clean(raw)

/**
 * Afişe dokunulduğunda:
 * 1. `tmdb:...` ise doğrudan TMDB Detay ekranını aç (başlık araması yapmadan).
 * 2. `tt...` ise TMDB find API ile IMDb kimliğini çöz ve TMDB Detay ekranını aç.
 * 3. Başka id ise addon'un `meta` kaynağıyla sağlayıcı detay ekranını aç.
 * 4. O da yoksa veya çözülemezse başlıkla TMDB'de ara.
 */
internal suspend fun resolveAndOpenPoster(
    item: CatalogItem,
    tmdb: TmdbRepository,
    addons: com.kaan.play.data.addon.AddonRepository? = null,
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenProviderDetail: ((providerId: String, url: String) -> Unit)? = null,
    onOpenSearch: (String) -> Unit,
) {
    // 1. tmdb:... ise başlık araması yapmadan doğrudan TMDB Detay ekranını aç
    if (item.id.startsWith("tmdb:")) {
        val idPart = item.id.removePrefix("tmdb:")
        val (mediaType, parsedId) = when {
            idPart.startsWith("movie:") -> MediaType.Movie to idPart.removePrefix("movie:").toIntOrNull()
            idPart.startsWith("series:") || idPart.startsWith("tv:") -> MediaType.Tv to idPart.removePrefix("series:").removePrefix("tv:").toIntOrNull()
            else -> item.mediaType to idPart.toIntOrNull()
        }
        if (parsedId != null && parsedId > 0) {
            onOpenDetail(mediaType, parsedId)
            return
        }
    }

    // 2. tt... ise TMDB find ile IMDb kimliğinden TMDB Detay ekranını aç
    val imdbId = if (item.id.startsWith("tt")) item.id else PosterUrlTemplate.findImdbId(item.id, item.rawUrl, item.posterUrl)
    if (imdbId != null && imdbId.startsWith("tt")) {
        val found = withContext(Dispatchers.IO) { runCatching { tmdb.findByImdb(imdbId) }.getOrNull() }
        if (found != null) {
            onOpenDetail(found.first, found.second)
            return
        }
    }

    // 3. Başka id ise addon'un meta kaynağıyla basit detay
    if (item.providerId.startsWith("nuvio_addon:") && onOpenProviderDetail != null && addons != null) {
        val addonRowId = item.providerId.removePrefix("nuvio_addon:").toLongOrNull() ?: 0L
        val addon = withContext(Dispatchers.IO) { runCatching { addons.byId(addonRowId) }.getOrNull() }
        val hasMeta = addon?.manifest?.resources?.any { it.name == com.kaan.play.data.addon.AddonManifest.META } == true
        val isLive = item.addonType in listOf("tv", "channel")
        if (hasMeta || isLive) {
            val typeStr = item.addonType ?: when (item.mediaType) {
                MediaType.Tv -> "series"
                MediaType.Movie -> "movie"
            }
            onOpenProviderDetail(item.providerId, "$typeStr/${item.id}")
            return
        }
    }

    // Doğrudan provider rawUrl varsa sağlayıcı detayını aç
    if (!item.rawUrl.isNullOrBlank() && onOpenProviderDetail != null) {
        onOpenProviderDetail(item.providerId, item.rawUrl)
        return
    }

    // Canlı kanalda (tv/channel) TMDB başlık araması yapılmaz; yanlış içerik açar
    if (item.addonType in listOf("tv", "channel")) {
        if (item.providerId.startsWith("nuvio_addon:") && onOpenProviderDetail != null) {
            val typeStr = item.addonType ?: "tv"
            onOpenProviderDetail(item.providerId, "$typeStr/${item.id}")
        }
        return
    }

    // 4. O da yoksa başlıkla ara
    val clean = cleanCatalogTitle(item.title)
    try {
        var search = withContext(Dispatchers.IO) { tmdb.search(clean) }
        if (search.items.isEmpty() && clean != item.title) {
            search = withContext(Dispatchers.IO) { tmdb.search(item.title) }
        }
        val results = search.items.filter { it.type == item.mediaType }
        val wantedTitle = normalizePosterTitle(clean)
        val exactTitle = results.filter { normalizePosterTitle(it.title) == wantedTitle }
        // Aynı ad farklı yıllarda kullanılabiliyor. Yıl biliniyorsa yalnızca aynı yapım seçilir;
        // yıl bilinmiyorsa tam eşleşen veya bulunan ilk yapım seçilir.
        val matched = when {
            item.year != null -> exactTitle.firstOrNull { it.year == item.year } ?: results.firstOrNull { it.year == item.year }
            exactTitle.isNotEmpty() -> exactTitle.first()
            results.isNotEmpty() -> results.first()
            else -> null
        }
        if (matched != null) {
            onOpenDetail(matched.type, matched.id)
        } else {
            onOpenSearch(clean)
        }
    } catch (_: Throwable) {
        onOpenSearch(clean)
    }
}

private fun normalizePosterTitle(value: String): String =
    value.lowercase(java.util.Locale.ROOT)
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .filter { it.isLetterOrDigit() }

/**
 * Ana sayfanın sağ altında yüzen hap buton. Seçili eklentinin adını veya "Eklenti seç" yazar.
 */
@Composable
private fun ProviderPillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .focusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = PlayIcons.ChevronDown,
            contentDescription = null,
            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
