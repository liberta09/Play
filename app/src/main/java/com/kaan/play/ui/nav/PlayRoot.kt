package com.kaan.play.ui.nav

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.download.toTarget
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.ui.player.PlaySession
import com.kaan.play.ui.player.formatTime
import com.kaan.play.ui.addons.AddonsScreen
import com.kaan.play.ui.addons.CsReposScreen
import com.kaan.play.ui.addons.InstalledAddonsScreen
import com.kaan.play.ui.addons.InstalledAddonsSection
import com.kaan.play.ui.addons.PluginSettingsScreen
import com.kaan.play.ui.addons.RepositoriesScreen
import com.kaan.play.ui.detail.DetailScreen
import com.kaan.play.ui.home.CategoryScreen
import com.kaan.play.ui.home.HomeRow
import com.kaan.play.ui.home.HomeScreen
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.search.SearchScreen
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.ui.library.LibraryScreen
import com.kaan.play.ui.settings.AboutScreen
import com.kaan.play.ui.settings.PlayerSettingsScreen
import com.kaan.play.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import com.kaan.play.ui.sources.SourcesSheet
import kotlinx.coroutines.launch

private const val ADDONS_ROUTE = "settings/addons?add={add}"
private const val ADDONS_REPOS_ROUTE = "settings/addons/repos"
private const val ADDONS_INSTALLED_ROUTE = "settings/addons/installed"
private const val ADDONS_INSTALLED_NUVIO_ROUTE = "settings/addons/installed/nuvio"
private const val ADDONS_INSTALLED_NUVIO_PLUGINS_ROUTE = "settings/addons/installed/nuvio-plugins"
private const val CS_REPOS_ROUTE = "settings/addons/csrepos"
private const val PLUGIN_ROUTE = "settings/addons/plugin/{id}"
private const val PLAYER_SETTINGS_ROUTE = "settings/player"
private const val ABOUT_ROUTE = "settings/about"
private const val DETAIL_ROUTE = "detail/{type}/{id}"
private const val CATEGORY_ROUTE = "home/category/{row}"

/** Kaynak seçimine giden iş: hedef, başlangıç konumu ve konumun sormadan (otomatik) seçilip seçilmediği. */
private data class PlayStart(val target: PlayTarget, val startMs: Long, val autoResumed: Boolean)

/** "Devam / baştan" sorusu; [download] doluysa seçim indirilmiş kopya için. */
private data class ResumeAsk(val target: PlayTarget, val positionMs: Long, val download: DownloadEntity?)

private fun addonsRoute(startAdding: Boolean) = "settings/addons?add=$startAdding"
private fun detailRoute(type: MediaType, id: Int) = "detail/${type.tmdbPath}/$id"
private fun categoryRoute(row: HomeRow) = "home/category/${row.name}"

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
    Library("library", R.string.tab_library, PlayIcons.Library),
    Settings("settings", R.string.tab_settings, PlayIcons.Settings),
}

@Composable
fun PlayRoot() {
    val navController = rememberNavController()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun openTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openDetail(type: MediaType, id: Int) = navController.navigate(detailRoute(type, id))

    // Eklentiler ekranı Ayarlar sekmesine aittir: nereden gelinirse gelinsin önce o sekmeye geçilir,
    // yoksa ekran çağıran sekmenin yığınına girer ve o sekme hep onu açar.
    fun openAddons(startAdding: Boolean) {
        openTab(Tab.Settings)
        navController.navigate(addonsRoute(startAdding)) { launchSingleTop = true }
    }

    // Başarılı ekleme, kaynak türüne göre doğrudan ilgili listeyi açar.
    fun openAddedSource(result: AddResult) {
        openTab(Tab.Settings)
        val destination = when (result) {
            is AddResult.CsRepo -> CS_REPOS_ROUTE
            is AddResult.PluginRepo -> ADDONS_INSTALLED_NUVIO_PLUGINS_ROUTE
            is AddResult.Addon -> ADDONS_INSTALLED_NUVIO_ROUTE
        }
        navController.navigate(destination) { launchSingleTop = true }
    }

    // https eklenti bağlantısıyla açılınca (MainActivity -> pendingAddonUrl) ekleme ekranına git.
    val pendingAddonUrl by container.pendingAddonUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAddonUrl) {
        if (pendingAddonUrl != null) openAddons(startAdding = false)
    }

    // Repo Manager deep link'i başarıyla kurulduğunda aynı yönlendirmeyi uygula.
    val installedSource by container.deepLinks.installedSource.collectAsStateWithLifecycle()
    LaunchedEffect(installedSource) {
        val result = installedSource ?: return@LaunchedEffect
        container.deepLinks.consumeInstalledSource()
        openAddedSource(result)
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
            if (download == null && !container.hasEnabledSources()) {
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

    // Geçmiş/"devam et" kaydından oynatma. IMDb kimliği için TMDB'ye bir kez sorulur; olmazsa detay açılır.
    fun playEntry(entry: WatchHistoryEntity, season: Int?, episode: Int?, startAt: Long) {
        scope.launch {
            // İçerik indirilmişse internete ve TMDB'ye hiç gerek yok: indirilmiş kopyadan oynar (çevrimdışı "devam et").
            val request = StreamRequest(entry.tmdbId, null, entry.type, season, episode)
            container.downloads.completedFor(request)?.let { download ->
                play(download.toTarget(), startAt, autoResumed = true, download = download)
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
            is ContinueItem.Next -> playEntry(entry, item.season, item.episode, 0L)
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
    // Detay tam ekran: alt menü/yan menü gizli.
    val showNav = currentRoute != DETAIL_ROUTE
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val railColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    fun selected(tab: Tab) = currentRoute == tab.route || currentRoute?.startsWith(tab.route + "/") == true

    // Ana sayfa (kapak karuseli) ve Detay (arka plan görseli) durum çubuğunun arkasına kadar uzanır; diğer ekranlar altına girmez.
    val bleedsUnderStatusBar = currentRoute == Tab.Home.route || currentRoute == DETAIL_ROUTE
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (bleedsUnderStatusBar) {
            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {
            // Telefon (ve alçak yatay telefon): alt menü. Geniş ekranda yan menü kullanılır.
            if (showNav && !layout.useRail) CappedFontScale {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected(tab),
                            onClick = { openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                            alwaysShowLabel = true,
                            label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = navColors,
                            modifier = Modifier.focusRing(RoundedCornerShape(16.dp)),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding)) {
        if (showNav && layout.useRail) CappedFontScale {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                // Menü öğeleri dikeyde ortalanır; kumandada Yukarı/Aşağı ile gezilir, Sağ içeriğe geçer.
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selected(tab),
                        onClick = { openTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                        alwaysShowLabel = true,
                        label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = railColors,
                        modifier = Modifier.focusRing(RoundedCornerShape(16.dp)).padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.weight(1f),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenDetail = ::openDetail,
                    onOpenCategory = { navController.navigate(categoryRoute(it)) },
                    onContinue = ::continueWatching,
                    onAddAddon = { openAddons(startAdding = true) },
                )
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
            composable(Tab.Search.route) { ReadableWidth(PlayLayout.GRID_WIDTH_DP.dp) { SearchScreen(onOpenDetail = ::openDetail) } }
            composable(Tab.Library.route) {
                ReadableWidth(PlayLayout.GRID_WIDTH_DP.dp) { LibraryScreen(onOpenDetail = ::openDetail, onPlayEntry = ::playHistory, onPlayDownload = ::playDownload) }
            }
            composable(Tab.Settings.route) {
                ReadableWidth {
                    SettingsScreen(
                        onOpenAddons = { navController.navigate(addonsRoute(false)) },
                        onOpenPlayer = { navController.navigate(PLAYER_SETTINGS_ROUTE) },
                        onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
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
                        onAdded = { result -> openAddedSource(result) },
                    )
                }
            }
            composable(ADDONS_REPOS_ROUTE) { ReadableWidth { RepositoriesScreen(onBack = { navController.popBackStack() }) } }
            composable(CS_REPOS_ROUTE) { ReadableWidth { CsReposScreen(onBack = { navController.popBackStack() }, container = rememberContainer()) } }
            composable(ADDONS_INSTALLED_ROUTE) {
                ReadableWidth {
                    InstalledAddonsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlugin = { navController.navigate("settings/addons/plugin/$it") },
                    )
                }
            }
            composable(ADDONS_INSTALLED_NUVIO_ROUTE) {
                ReadableWidth {
                    InstalledAddonsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlugin = { navController.navigate("settings/addons/plugin/$it") },
                        section = InstalledAddonsSection.NUVIO_ADDONS,
                    )
                }
            }
            composable(ADDONS_INSTALLED_NUVIO_PLUGINS_ROUTE) {
                ReadableWidth {
                    InstalledAddonsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlugin = { navController.navigate("settings/addons/plugin/$it") },
                        section = InstalledAddonsSection.NUVIO_PLUGINS,
                    )
                }
            }
            composable(PLUGIN_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ReadableWidth {
                    PluginSettingsScreen(pluginId = entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
                }
            }
            composable(ABOUT_ROUTE) { ReadableWidth { AboutScreen(onBack = { navController.popBackStack() }) } }
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
                    onPlay = { target -> play(target) },
                )
            }
        }
        }
    }

    sourcesFor?.let { start ->
        SourcesSheet(target = start.target, onDismiss = { sourcesFor = null }, onPicked = ::onSourcePicked)
    }

    resumePrompt?.let { (target, positionMs, download) ->
        AlertDialog(
            onDismissRequest = { resumePrompt = null },
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
