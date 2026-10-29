package com.kaan.play.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import com.kaan.play.ui.settings.ProfileAvatar
import com.kaan.play.ui.tv.tvFocusableScaleGlow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.graphics.Color
import com.kaan.play.ui.theme.topBarBorderColor
import com.kaan.play.ui.theme.topBarSurfaceColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import com.kaan.play.ui.tv.ReadableWidth
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberPlayLayout
import com.kaan.play.ui.tv.PlayLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaan.play.R
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.download.toTarget
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.ui.player.PlaySession
import com.kaan.play.ui.player.formatTime
import com.kaan.play.ui.addons.AddonsScreen
import com.kaan.play.ui.addons.CsReposScreen
import com.kaan.play.ui.addons.AddonDetailScreen
import com.kaan.play.ui.addons.InstalledAddonsScreen
import com.kaan.play.ui.addons.NuvioPluginsScreen
import com.kaan.play.ui.addons.NuvioReposScreen
import com.kaan.play.ui.addons.PluginSettingsScreen
import com.kaan.play.ui.addons.RepositoriesScreen
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.DeepLinkEvent
import com.kaan.play.ui.detail.DetailScreen
import com.kaan.play.ui.detail.ProviderDetailScreen
import com.kaan.play.ui.home.CategoryScreen
import com.kaan.play.ui.home.HomeRow
import com.kaan.play.ui.home.HomeScreen
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.search.SearchScreen
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.ui.library.LibraryScreen
import com.kaan.play.ui.tv.TvScreen
import com.kaan.play.ui.settings.AboutScreen
import com.kaan.play.ui.settings.PlayerSettingsScreen
import com.kaan.play.ui.settings.SettingsScreen
import com.kaan.play.ui.settings.SpeedTestScreen
import com.kaan.play.ui.settings.ProfileScreen
import com.kaan.play.ui.settings.SyncScreen
import com.kaan.play.ui.settings.ProfileChooserScreen
import com.kaan.play.ui.settings.ProfileSetupScreen
import com.kaan.play.ui.addons.RepoManagerCatalogScreen
import com.kaan.play.data.sync.LocalProfileSummary
import kotlinx.coroutines.flow.first
import com.kaan.play.ui.sources.SourcesSheet
import kotlinx.coroutines.launch

private const val SPEED_TEST_ROUTE = "settings/speed-test"
private const val PROFILE_ROUTE = "settings/profile"
private const val SYNC_ROUTE = "settings/sync"
private const val REPO_MANAGER_CATALOG_ROUTE = "settings/addons/catalog"
private const val ADDONS_ROUTE = "settings/addons?add={add}"
private const val ADDONS_REPOS_ROUTE = "settings/addons/repos"
private const val ADDONS_INSTALLED_ROUTE = "settings/addons/installed"
private const val CS_REPOS_ROUTE = "settings/addons/csrepos"
private const val NUVIO_REPOS_ROUTE = "settings/addons/nuviorepos"
private const val NUVIO_PLUGINS_ROUTE = "settings/addons/nuvioplugins"
private const val PLUGIN_ROUTE = "settings/addons/plugin/{id}"
private const val ADDON_DETAIL_ROUTE = "settings/addons/detail/{id}"
private const val PLAYER_SETTINGS_ROUTE = "settings/player"
private const val ABOUT_ROUTE = "settings/about"
private const val DETAIL_ROUTE = "detail/{type}/{id}"
private const val PROVIDER_DETAIL_ROUTE = "provider_detail/{providerId}/{encodedUrl}"
private const val CATEGORY_ROUTE = "home/category/{row}"

/** Kaynak seçimine giden iş: hedef, başlangıç konumu ve konumun sormadan (otomatik) seçilip seçilmediği. */
private data class PlayStart(val target: PlayTarget, val startMs: Long, val autoResumed: Boolean)

/** "Devam / baştan" sorusu; [download] doluysa seçim indirilmiş kopya için. */
private data class ResumeAsk(val target: PlayTarget, val positionMs: Long, val download: DownloadEntity?)

private fun addonsRoute(startAdding: Boolean) = "settings/addons?add=$startAdding"
private fun detailRoute(type: MediaType, id: Int) = "detail/${type.tmdbPath}/$id"
private fun categoryRoute(row: HomeRow) = "home/category/${row.name}"
private fun providerDetailRoute(providerId: String, url: String): String {
    val b64 = android.util.Base64.encodeToString(url.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    val safePid = ProviderRouteCodec.encode(providerId)
    return "provider_detail/$safePid/$b64"
}


/**
 * Alt/yan menü etiketleri en çok 1.0 yazı ölçeğiyle çizilir: büyük yazıda sekme adları ("Kütüphane") kısalmaz ya da
 * sekmeden taşmaz. Menü simgeli ve kısa olduğu için okunabilirlik zarar görmez; içerik büyük yazıyı korur.
 */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, 1f)),
        content = content,
    )
}

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, PlayIcons.Home),
    Search("search", R.string.tab_search, PlayIcons.Search),
    Tv("tv", R.string.tab_tv, PlayIcons.Tv),
    Library("library", R.string.tab_library, PlayIcons.Folder),
    Settings("settings", R.string.tab_settings, PlayIcons.Gear),
}

@Composable
fun PlayRoot() {
    val navController = rememberNavController()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Kumanda: üst menü ile içerik arasındaki odak köprüleri (her sekmenin kendi içerik hedefi var).
    val homeFocusRequester = remember { FocusRequester() }
    val tvFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val libraryFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val topBarFocusRequester = remember { FocusRequester() }
    // Ana sayfada: profil simgesi SAĞ → "Eklenti seç" hapı, hapta SOL → profil simgesi.
    val profileFocusRequester = remember { FocusRequester() }
    val pillFocusRequester = remember { FocusRequester() }
    var topBarHasFocus by remember { mutableStateOf(false) }
    val currentContentFocusRequester = when {
        currentRoute == Tab.Tv.route -> tvFocusRequester
        currentRoute == Tab.Search.route || currentRoute?.startsWith("search") == true -> searchFocusRequester
        currentRoute == Tab.Library.route -> libraryFocusRequester
        currentRoute == Tab.Settings.route -> settingsFocusRequester
        else -> homeFocusRequester
    }

    val profiles by container.localProfile.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfile by container.localProfile.activeProfile.collectAsStateWithLifecycle(initialValue = null)
    val rememberLastProfile by container.settings.rememberLastProfile.collectAsStateWithLifecycle(initialValue = true)
    var profileGate by remember { mutableStateOf(false) }
    var initialGateChecked by remember { mutableStateOf(false) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var managingProfiles by remember { mutableStateOf(false) }

    LaunchedEffect(profiles, rememberLastProfile) {
        if (!initialGateChecked && profiles.isNotEmpty()) {
            if (!rememberLastProfile || activeProfile == null) {
                profileGate = true
            }
            initialGateChecked = true
        } else if (!initialGateChecked && profiles.isEmpty()) {
            profileGate = true
            initialGateChecked = true
        }
    }

    if (showProfileSetup) {
        ProfileSetupScreen(
            onCreate = { name, avatar, isChild, pin ->
                scope.launch {
                    container.localProfile.createProfile(name, avatar, isChild, pin)
                    showProfileSetup = false
                    profileGate = false
                }
            },
            onCancel = {
                if (profiles.isNotEmpty()) {
                    showProfileSetup = false
                }
            }
        )
        return
    }

    if (profileGate) {
        ProfileChooserScreen(
            profiles = profiles,
            onSelect = { profile ->
                scope.launch {
                    container.localProfile.selectProfile(profile.id)
                    profileGate = false
                }
            },
            isAdmin = true,
            managingProfiles = managingProfiles,
            onToggleManage = { managingProfiles = !managingProfiles },
            onCreateProfile = { showProfileSetup = true },
            onToggleHidden = { profile ->
                scope.launch {
                    container.localProfile.setHidden(profile.id, !profile.isHidden)
                }
            },
            onDeleteProfile = { profile ->
                scope.launch {
                    container.localProfile.deleteProfile(profile.id)
                }
            },
            onEditProfile = { profile, name, avatar, isChild, pin ->
                scope.launch {
                    container.localProfile.updateProfile(profile.id, name, avatar, isChild, pin)
                }
            }
        )
        return
    }

    fun openTab(tab: Tab) {
        if (tab == Tab.Home) {
            val popped = navController.popBackStack(Tab.Home.route, inclusive = false)
            if (!popped) {
                navController.navigate(Tab.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
            return
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openDetail(type: MediaType, id: Int) = navController.navigate(detailRoute(type, id))
    fun openProviderDetail(providerId: String, url: String) = navController.navigate(providerDetailRoute(providerId, url))

    // Eklentiler ekranı Ayarlar sekmesine aittir: nereden gelinirse gelinsin önce o sekmeye geçilir,
    // yoksa ekran çağıran sekmenin yığınına girer ve o sekme hep onu açar.
    fun openAddons(startAdding: Boolean) {
        openTab(Tab.Settings)
        navController.navigate(addonsRoute(startAdding)) { launchSingleTop = true }
    }

    // https eklenti bağlantısıyla açılınca (MainActivity -> pendingAddonUrl) ekleme ekranına git.
    val pendingAddonUrl by container.pendingAddonUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAddonUrl) {
        if (pendingAddonUrl != null) openAddons(startAdding = false)
    }

    // play://add deep link onayından sonra ilgili bölüme otomatik geçiş
    LaunchedEffect(container.deepLinks) {
        container.deepLinks.events.collect { event ->
            if (event is DeepLinkEvent.Added) {
                openTab(Tab.Settings)
                when (event.result) {
                    is AddResult.CsRepo -> navController.navigate(CS_REPOS_ROUTE) { launchSingleTop = true }
                    is AddResult.Addon -> navController.navigate(NUVIO_REPOS_ROUTE) { launchSingleTop = true }
                    is AddResult.PluginRepo -> navController.navigate(NUVIO_PLUGINS_ROUTE) { launchSingleTop = true }
                }
            }
        }
    }

    // Oynat: aktif eklenti yoksa tek yol eklenti eklemektir. Aynı içerik yarım izlenmişse önce
    // "devam / baştan" sorulur ([startAtMs] verilmişse sorulmaz); sonra kaynak seçimi, sonra oynatıcı.
    var sourcesFor by remember { mutableStateOf<PlayStart?>(null) }
    var resumePrompt by remember { mutableStateOf<ResumeAsk?>(null) }
    val context = LocalContext.current

    // Kaynak seçimi yerine indirilmiş kopyadan (çevrimdışı) oynatır: aynı hedef, aynı geçmiş satırı.
    fun playOffline(download: DownloadEntity, startMs: Long, autoResumed: Boolean) {
        context.startActivity(PlaySession.offline(download, startMs, autoResumed).toIntent(context))
    }

    // Oynat: aktif eklenti yoksa tek yol eklenti eklemektir. [download] verilirse (indirilen) eklenti gerekmez ve kaynak seçimi
    // atlanır. Aynı içerik yarım izlenmişse önce "devam / baştan" sorulur ([startAtMs] verilmişse sorulmaz).
    fun play(target: PlayTarget, startAtMs: Long? = null, autoResumed: Boolean = false, download: DownloadEntity? = null) {
        scope.launch {
            if (download == null && target.request.pluginId == null && !container.hasEnabledSources()) {
                openAddons(startAdding = true)
                return@launch
            }
            // "Kaynağı otomatik seç" açıkken (varsayılan) kaynak listesi beklenmez: oynatıcı kaynakları arka planda yükleyip uygun ilkiyle
            // başlar. Kapalıyken eski davranış: önce kaynak listesi.
            val autoSource = container.settings.autoSource.first()
            fun begin(startMs: Long, auto: Boolean) {
                when {
                    download != null -> playOffline(download, startMs, auto)
                    autoSource -> context.startActivity(
                        PlaySession(target, stream = null, startPositionMs = startMs, autoResumed = auto, autoSource = true).toIntent(context),
                    )
                    else -> sourcesFor = PlayStart(target, startMs, auto)
                }
            }
            if (startAtMs == null) {
                val saved = container.library.progress(target)
                if (saved != null && WatchRules.isResumable(saved.positionMs, saved.durationMs)) {
                    // "Her zaman kaldığım yerden devam et" açıksa (varsayılan) soru sorulmadan devam edilir.
                    if (container.settings.alwaysResume.first()) {
                        begin(saved.positionMs, auto = true)
                    } else {
                        resumePrompt = ResumeAsk(target, saved.positionMs, download)
                    }
                    return@launch
                }
            }
            begin(startAtMs ?: 0L, autoResumed && (startAtMs ?: 0L) > 0)
        }
    }

    // Kütüphane > İndirilenler: internetsiz oynar; kaldığı yerden devam ve geçmiş çevrimiçi oynatmayla ortaktır.
    fun playDownload(download: DownloadEntity) = play(download.toTarget(), download = download)

    /** TV katalog öğesi TMDB içeriği değildir; addonContentId ile doğrudan canlı akış aranır. */
    fun playLiveChannel(item: CatalogItem) {
        val target = PlayTarget(
            request = StreamRequest(
                tmdbId = 0,
                imdbId = null,
                type = MediaType.Tv,
                pluginId = item.providerId,
                addonType = item.addonType?.takeIf { it == "tv" || it == "channel" } ?: "tv",
                addonContentId = item.rawUrl ?: item.id,
                dataUrl = item.rawUrl?.takeIf { item.providerId.startsWith("cs:") },
                title = item.title,
            ),
            title = item.title,
            posterPath = null,
        )
        play(target)
    }

    // Geçmiş/"devam et" kaydından oynatma. IMDb kimliği için TMDB'ye bir kez sorulur; olmazsa detay açılır.
    fun playEntry(entry: WatchHistoryEntity, season: Int?, episode: Int?, startAt: Long) {
        scope.launch {
            // İçerik indirilmişse internete ve TMDB'ye hiç gerek yok: indirilmiş kopyadan oynar (çevrimdışı "devam et").
            val request = StreamRequest(
                tmdbId = entry.tmdbId,
                imdbId = null,
                type = entry.type,
                season = season,
                episode = episode,
                pluginId = entry.pluginId,
                dataUrl = entry.dataUrl,
                contentUrl = entry.contentUrl,
            )
            container.downloads.completedFor(request)?.let { download ->
                play(download.toTarget(), startAt, autoResumed = true, download = download)
                return@launch
            }
            if (entry.pluginId != null && entry.dataUrl != null) {
                val target = PlayTarget(request, entry.title, entry.posterPath)
                play(target, startAt, autoResumed = true)
                return@launch
            }
            val imdbId = try {

                container.tmdb.detail(entry.type, entry.tmdbId).imdbId
            } catch (_: TmdbException) {
                openDetail(entry.type, entry.tmdbId)
                return@launch
            }
            // Kart/satır kayıtlı konumdan sormadan başlatır: oynatıcıda "Baştan başla" bildirimi çıkar.
            val target = PlayTarget(StreamRequest(entry.tmdbId, imdbId, entry.type, season, episode), entry.title, entry.posterPath)
            play(target, startAt, autoResumed = true)
        }
    }

    // "İzlemeye devam et" kartı: filmde/bölümde kayıtlı konumdan, önerilen sıradaki bölümde baştan oynatır.
    fun continueWatching(item: ContinueItem) {
        val entry = item.entry
        val isSeries = entry.type == MediaType.Tv
        when (item) {
            is ContinueItem.Resume ->
                playEntry(entry, entry.season.takeIf { isSeries }, entry.episode.takeIf { isSeries }, entry.positionMs)
            is ContinueItem.Next -> {
                val pluginId = entry.pluginId
                val contentUrl = entry.contentUrl
                if (pluginId != null && !contentUrl.isNullOrBlank()) {
                    // Eklenti dizisi: kayıttaki dataUrl BİTEN bölümündür; sıradaki bölümün verisiyle oynatılır.
                    scope.launch {
                        val next = container.csNextEpisodes.next(pluginId, contentUrl, entry.dataUrl, entry.season, entry.episode)
                        if (next != null) {
                            playEntry(entry.copy(dataUrl = next.dataUrl), next.season, next.episode, 0L)
                        } else if (entry.tmdbId > 0) {
                            // Eklentinin listesi alınamadı: eski bölümün verisiyle OYNATMA (aynı bölüm tekrar açılırdı);
                            // TMDB kimliğiyle sıradaki bölümün kaynakları tüm eklentilerden aranır.
                            playEntry(entry.copy(pluginId = null, dataUrl = null, contentUrl = null), item.season, item.episode, 0L)
                        }
                    }
                } else {
                    playEntry(entry, item.season, item.episode, 0L)
                }
            }
        }
    }

    // Kütüphane > Geçmiş satırı: yarım kaldıysa kayıtlı konumdan, izlendiyse baştan.
    fun playHistory(entry: WatchHistoryEntity) {
        val isSeries = entry.type == MediaType.Tv
        val startAt = if (WatchRules.isResumable(entry.positionMs, entry.durationMs)) entry.positionMs else 0L
        playEntry(entry, entry.season.takeIf { isSeries }, entry.episode.takeIf { isSeries }, startAt)
    }

    fun onSourcePicked(stream: PlayableStream) {
        val start = sourcesFor ?: return
        sourcesFor = null
        context.startActivity(PlaySession(start.target, stream, start.startMs, start.autoResumed).toIntent(context))
    }

    val layout = rememberPlayLayout()
    // Alt sayfalarda (detay, kategori, ayar alt ekranları) üst menü gizli; yalnızca ana sekmelerde görünür.
    val isSubScreen = currentRoute == DETAIL_ROUTE ||
        currentRoute == PROVIDER_DETAIL_ROUTE ||
        currentRoute?.startsWith("detail/") == true ||
        currentRoute?.startsWith("provider_detail/") == true ||
        currentRoute?.startsWith("home/category") == true ||
        currentRoute?.startsWith("settings/") == true
    val showNav = !isSubScreen

    // Kumanda: odak üst menüdeyken Geri uygulamadan çıkarmaz, odağı içeriğe döndürür.
    BackHandler(enabled = topBarHasFocus && !isSubScreen) {
        runCatching { currentContentFocusRequester.requestFocus() }
    }
    val avatarKey = activeProfile?.avatar ?: "space_astro"
    fun selected(tab: Tab) = currentRoute == tab.route || currentRoute?.startsWith(tab.route + "/") == true || currentRoute?.startsWith(tab.route + "?") == true

    // Ana sayfa, Detay ve yüzen üst menü durum çubuğunun arkasına uzanır.
    val bleedsUnderStatusBar = showNav || currentRoute == Tab.Home.route || currentRoute == DETAIL_ROUTE
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (bleedsUnderStatusBar) {
            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {},
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // İçerik alanı: tüm genişliği kullanır; Ana sayfa dışında yüzen üst menünün altından başlar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showNav && currentRoute != Tab.Home.route) 58.dp else 0.dp),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Tab.Home.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenDetail = ::openDetail,
                    onOpenProviderDetail = ::openProviderDetail,
                    onOpenCategory = { navController.navigate(categoryRoute(it)) },
                    onContinue = ::continueWatching,
                    onAddAddon = { openAddons(startAdding = true) },
                    onOpenSearch = { query ->
                        navController.navigate("search?q=${Uri.encode(query)}") {
                            launchSingleTop = true
                        }
                    },
                    contentFocusRequester = homeFocusRequester,
                    topBarFocusRequester = topBarFocusRequester,
                    pillFocusRequester = pillFocusRequester,
                    profileFocusRequester = profileFocusRequester,
                )
            }

            composable(Tab.Tv.route) {
                ReadableWidth {
                    TvScreen(
                        onPlay = ::playLiveChannel,
                        contentFocusRequester = tvFocusRequester,
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }
            }

            composable(
                route = CATEGORY_ROUTE,
                arguments = listOf(navArgument("row") { type = NavType.StringType }),
            ) { entry ->
                val row = HomeRow.entries.firstOrNull { it.name == entry.arguments?.getString("row") } ?: HomeRow.TrendingMovies
                ReadableWidth(PlayLayout.GRID_WIDTH_DP.dp) {
                    CategoryScreen(row = row, onBack = { navController.popBackStack() }, onOpenDetail = ::openDetail)
                }
            }
            composable(
                route = "search?q={q}",
                arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val q = entry.arguments?.getString("q")
                ReadableWidth(PlayLayout.GRID_WIDTH_DP.dp) {
                    SearchScreen(
                        initialQuery = q,
                        onOpenDetail = ::openDetail,
                        contentFocusRequester = searchFocusRequester,
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }
            }
            composable(Tab.Library.route) {
                ReadableWidth(PlayLayout.GRID_WIDTH_DP.dp) {
                    LibraryScreen(
                        onOpenDetail = ::openDetail,
                        onPlayEntry = ::playHistory,
                        onPlayDownload = ::playDownload,
                        contentFocusRequester = libraryFocusRequester,
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }
            }
            composable(Tab.Settings.route) {
                ReadableWidth {
                    SettingsScreen(
                        onOpenAddons = { navController.navigate(addonsRoute(false)) },
                        onOpenPlayer = { navController.navigate(PLAYER_SETTINGS_ROUTE) },
                        onOpenSpeedTest = { navController.navigate(SPEED_TEST_ROUTE) },
                        onOpenProfile = { profileGate = true },
                        onOpenSync = { navController.navigate(SYNC_ROUTE) },
                        onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                        contentFocusRequester = settingsFocusRequester,
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }
            }
            composable(PLAYER_SETTINGS_ROUTE) { ReadableWidth { PlayerSettingsScreen(onBack = { navController.popBackStack() }) } }
            composable(
                route = ADDONS_ROUTE,
                arguments = listOf(navArgument("add") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                ReadableWidth {
                    AddonsScreen(
                        startAdding = entry.arguments?.getBoolean("add") == true,
                        onBack = { navController.popBackStack() },
                        onOpenRepos = { navController.navigate(ADDONS_REPOS_ROUTE) },
                        onOpenInstalled = { navController.navigate(ADDONS_INSTALLED_ROUTE) },
                        onOpenCsRepos = { navController.navigate(CS_REPOS_ROUTE) },
                        onOpenNuvioRepos = { navController.navigate(NUVIO_REPOS_ROUTE) },
                        onOpenNuvioPlugins = { navController.navigate(NUVIO_PLUGINS_ROUTE) },
                        onOpenCatalog = { navController.navigate(REPO_MANAGER_CATALOG_ROUTE) },
                    )
                }
            }
            composable(ADDONS_REPOS_ROUTE) {
                ReadableWidth {
                    RepositoriesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddon = { navController.navigate("settings/addons/detail/$it") },
                    )
                }
            }
            composable(CS_REPOS_ROUTE) { ReadableWidth { CsReposScreen(onBack = { navController.popBackStack() }, container = rememberContainer()) } }
            composable(NUVIO_REPOS_ROUTE) {
                ReadableWidth {
                    NuvioReposScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddon = { navController.navigate("settings/addons/detail/$it") },
                        container = rememberContainer(),
                    )
                }
            }
            composable(NUVIO_PLUGINS_ROUTE) {
                ReadableWidth {
                    NuvioPluginsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlugin = { navController.navigate("settings/addons/plugin/$it") },
                        container = rememberContainer(),
                    )
                }
            }
            composable(ADDONS_INSTALLED_ROUTE) {
                ReadableWidth {
                    InstalledAddonsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlugin = { navController.navigate("settings/addons/plugin/$it") },
                        onOpenAddon = { navController.navigate("settings/addons/detail/$it") },
                    )
                }
            }
            composable(ADDON_DETAIL_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ReadableWidth {
                    AddonDetailScreen(
                        addonId = id,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = ::openDetail,
                        onOpenProviderDetail = ::openProviderDetail,
                        onOpenSearch = { query ->
                            navController.navigate("search?q=${Uri.encode(query)}") { launchSingleTop = true }
                        },
                        container = rememberContainer(),
                    )
                }
            }
            composable(PLUGIN_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ReadableWidth {
                    PluginSettingsScreen(pluginId = entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
                }
            }
            composable(ABOUT_ROUTE) { ReadableWidth { AboutScreen(onBack = { navController.popBackStack() }) } }
            composable(SPEED_TEST_ROUTE) {
                ReadableWidth {
                    SpeedTestScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(PROFILE_ROUTE) {
                ReadableWidth {
                    ProfileScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(SYNC_ROUTE) {
                ReadableWidth {
                    SyncScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(REPO_MANAGER_CATALOG_ROUTE) {
                ReadableWidth {
                    RepoManagerCatalogScreen(
                        onBack = { navController.popBackStack() },
                        container = container,
                    )
                }
            }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val type = MediaType.fromTmdb(entry.arguments?.getString("type")) ?: MediaType.Movie
                val id = entry.arguments?.getInt("id") ?: 0
                DetailScreen(
                    type = type,
                    id = id,
                    onBack = { navController.popBackStack() },
                    onOpenSimilar = ::openDetail,
                    onOpenActor = { name -> navController.navigate("search?q=${Uri.encode(name)}") },
                    onPlay = { target -> play(target) },
                )
            }
            composable(
                route = PROVIDER_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("encodedUrl") { type = NavType.StringType },
                ),
            ) { entry ->
                val safePid = entry.arguments?.getString("providerId").orEmpty()
                val providerId = ProviderRouteCodec.decode(safePid)
                val b64 = entry.arguments?.getString("encodedUrl").orEmpty()
                val rawUrl = try {
                    String(android.util.Base64.decode(b64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                } catch (_: Exception) {
                    Uri.decode(b64)
                }
                ProviderDetailScreen(
                    providerId = providerId,
                    url = rawUrl,
                    onBack = { navController.popBackStack() },
                    onPlay = { target -> play(target) },
                )
            }
        }
            }

            // Yüzen üst menü (hap): ana sekmeler + profil. Kumandayla sağ-sol gezilir, aşağı içeriğe iner.
            if (showNav) {
                PlayTopBar(
                    currentRoute = currentRoute,
                    avatarKey = avatarKey,
                    onOpenTab = ::openTab,
                    onOpenProfile = { profileGate = true },
                    contentFocusRequester = currentContentFocusRequester,
                    topBarFocusRequester = topBarFocusRequester,
                    profileFocusRequester = profileFocusRequester,
                    pillFocusRequester = pillFocusRequester.takeIf { currentRoute == Tab.Home.route || currentRoute == null },
                    modifier = Modifier
                        .onFocusChanged { topBarHasFocus = it.hasFocus }
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                        .zIndex(100f),
                )
            }
        }
    }

    sourcesFor?.let { start ->
        SourcesSheet(target = start.target, onDismiss = { sourcesFor = null }, onPicked = ::onSourcePicked)
    }

    resumePrompt?.let { (target, positionMs, download) ->
        AlertDialog(
            onDismissRequest = { resumePrompt = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(stringResource(R.string.resume_title)) },
            confirmButton = {
                TextButton(onClick = { resumePrompt = null; play(target, positionMs, download = download) }) {
                    Text(stringResource(R.string.resume_continue, formatTime(positionMs)))
                }
            },
            dismissButton = {
                TextButton(onClick = { resumePrompt = null; play(target, 0L, download = download) }) {
                    Text(stringResource(R.string.resume_restart))
                }
            },
        )
    }
}

/**
 * Ekranın üstünde yüzen hap menü: Ana sayfa, Ara, Canlı TV, Kütüphane, Ayarlar ve profil avatarı.
 * Kumanda: sağ-sol öğeler arasında gezer, aşağı (ve içerik tarafında yukarı) [contentFocusRequester] / [topBarFocusRequester]
 * köprüsüyle içerik ile menü arasında geçer.
 */
@Composable
fun PlayTopBar(
    currentRoute: String?,
    avatarKey: String,
    onOpenTab: (Tab) -> Unit,
    onOpenProfile: () -> Unit,
    contentFocusRequester: FocusRequester,
    topBarFocusRequester: FocusRequester,
    profileFocusRequester: FocusRequester,
    pillFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(12.dp, CircleShape),
        shape = CircleShape,
        color = topBarSurfaceColor(),
        border = BorderStroke(1.dp, topBarBorderColor()),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .focusProperties { down = contentFocusRequester },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val homeSelected = currentRoute == Tab.Home.route || currentRoute == null
            val searchSelected = currentRoute == Tab.Search.route || currentRoute?.startsWith("search") == true
            val tvSelected = currentRoute == Tab.Tv.route || currentRoute?.startsWith("tv") == true
            val librarySelected = currentRoute == Tab.Library.route || currentRoute?.startsWith("library") == true
            val settingsSelected = currentRoute == Tab.Settings.route || currentRoute?.startsWith("settings") == true
            PlayTopBarItem(PlayIcons.Home, stringResource(R.string.tab_home), homeSelected, contentFocusRequester, topBarFocusRequester.takeIf { homeSelected }) { onOpenTab(Tab.Home) }
            PlayTopBarItem(PlayIcons.Search, stringResource(R.string.tab_search), searchSelected, contentFocusRequester, topBarFocusRequester.takeIf { searchSelected }) { onOpenTab(Tab.Search) }
            PlayTopBarItem(PlayIcons.Tv, stringResource(R.string.tab_tv), tvSelected, contentFocusRequester, topBarFocusRequester.takeIf { tvSelected }) { onOpenTab(Tab.Tv) }
            PlayTopBarItem(PlayIcons.Folder, stringResource(R.string.tab_library), librarySelected, contentFocusRequester, topBarFocusRequester.takeIf { librarySelected }) { onOpenTab(Tab.Library) }
            PlayTopBarItem(PlayIcons.Gear, stringResource(R.string.tab_settings), settingsSelected, contentFocusRequester, topBarFocusRequester.takeIf { settingsSelected }) { onOpenTab(Tab.Settings) }
            PlayTopBarItem(null, stringResource(R.string.tab_profile), false, contentFocusRequester, profileFocusRequester, avatarKey = avatarKey, rightFocusRequester = pillFocusRequester, onClick = onOpenProfile)
        }
    }
}

@Composable
private fun PlayTopBarItem(
    icon: ImageVector?,
    contentDescription: String,
    selected: Boolean,
    contentFocusRequester: FocusRequester,
    itemFocusRequester: FocusRequester?,
    avatarKey: String? = null,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var boxFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionFocused by interactionSource.collectIsFocusedAsState()
    val focused = boxFocused || interactionFocused
    val tint = if (focused || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .then(if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester) else Modifier)
            .focusProperties {
                down = contentFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                } else {
                    false
                }
            }
            .onFocusChanged { boxFocused = it.isFocused }
            .tvFocusableScaleGlow(
                scale = 1.15f,
                glowColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                isFocused = focused,
                zIndex = 10f,
                fillGlowAlpha = 0.20f,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        if (avatarKey != null) {
            ProfileAvatar(avatar = avatarKey, size = 34)
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
