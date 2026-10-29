package com.kaan.play.ui.nav

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.kaan.play.ui.tv.tvFocusableScaleGlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.statusBarsPadding
import com.kaan.play.ui.settings.ProfileAvatar
import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.SideEffect
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
import com.kaan.play.ui.addons.RepoManagerCatalogScreen
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
import com.kaan.play.ui.settings.SpeedTestScreen
import com.kaan.play.ui.settings.SettingsScreen
import com.kaan.play.ui.settings.ProfileScreen
import com.kaan.play.ui.settings.SyncScreen
import com.kaan.play.ui.settings.ProfileChooserScreen
import com.kaan.play.ui.settings.ProfileSetupScreen
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LastProfileRestoration
import kotlinx.coroutines.flow.first
import com.kaan.play.ui.sources.SourcesSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ADDONS_ROUTE = "settings/addons?add={add}"
private const val ADDONS_REPOS_ROUTE = "settings/addons/repos"
private const val ADDONS_INSTALLED_ROUTE = "settings/addons/installed"
private const val CS_REPOS_ROUTE = "settings/addons/csrepos"
private const val REPO_MANAGER_CATALOG_ROUTE = "settings/addons/catalog"
private const val NUVIO_REPOS_ROUTE = "settings/addons/nuviorepos"
private const val NUVIO_PLUGINS_ROUTE = "settings/addons/nuvioplugins"
private const val PLUGIN_ROUTE = "settings/addons/plugin/{id}"
private const val ADDON_DETAIL_ROUTE = "settings/addons/detail/{id}"
private const val PLAYER_SETTINGS_ROUTE = "settings/player"
private const val SPEED_TEST_ROUTE = "settings/speed-test"
private const val ABOUT_ROUTE = "settings/about"
private const val PROFILE_ROUTE = "settings/profile"
private const val SYNC_ROUTE = "settings/sync"
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
    var pendingAddedNavigation by remember { mutableStateOf<AddResult?>(null) }
    val localProfiles by container.localProfile.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfileId by container.localProfile.id.collectAsStateWithLifecycle(initialValue = "")
    val activeProfileIsAdmin = localProfiles.any { it.id == activeProfileId && it.isAdmin }
    val activeProfileIsChild = localProfiles.any { it.id == activeProfileId && it.isChild }
    val profileSetupComplete by container.localProfile.profileSetupComplete.collectAsStateWithLifecycle(initialValue = false)
    val rememberLastProfile by container.settings.rememberLastProfile.collectAsStateWithLifecycle(initialValue = false)
    var profileGate by remember { mutableStateOf(false) }
    var managingProfiles by remember { mutableStateOf(false) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var pinProfile by remember { mutableStateOf<LocalProfileSummary?>(null) }
    var pinDraft by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val homeFocusRequester = remember { FocusRequester() }
    val tvFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val libraryFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val topBarFocusRequester = remember { FocusRequester() }

    val currentContentFocusRequester = when {
        currentRoute == Tab.Tv.route -> tvFocusRequester
        currentRoute == Tab.Search.route || currentRoute?.startsWith("search") == true -> searchFocusRequester
        currentRoute == Tab.Library.route -> libraryFocusRequester
        currentRoute == Tab.Settings.route -> settingsFocusRequester
        else -> homeFocusRequester
    }

    suspend fun activateProfile(profile: LocalProfileSummary) {
        val currentId = container.localProfile.id.first()
        if (currentId.isNotBlank() && currentId != profile.id) {
            container.localProfile.saveBackup(currentId, container.localSyncBackup.exportJson())
        }
        container.localProfile.setActiveProfile(profile)
        container.localProfile.backup(profile.id)?.let { container.localSyncBackup.importJson(it) }
        profileGate = false
    }

    LaunchedEffect(Unit) {
        val user = container.localProfile.ensureUserId()
        container.localProfile.ensureSyncCode()

        val lastActiveId = container.localProfile.lastActiveProfileId.first()
        val setupComplete = container.localProfile.profileSetupComplete.first()
        val allProfiles = container.localProfile.profiles.first()

        // 1. İLK KURULUM: Hiç profil yoksa zorunlu kurulum ekranına al
        if (allProfiles.isEmpty()) {
            showProfileSetup = true
            profileGate = false
        } else {
            // 2. BYPASS MANTIĞI: Profil varsa (son aktif profil veya ilk uygun profil) KESİNLİKLE ATLA ve direkt Ana Sayfaya geç
            val matched = allProfiles.firstOrNull { it.id == lastActiveId && !it.isHidden }
                ?: allProfiles.firstOrNull { !it.isHidden }
                ?: allProfiles.first()
            profileGate = false
            showProfileSetup = false
            if (matched.pinHash.isNullOrBlank()) {
                activateProfile(matched)
            } else {
                pinProfile = matched
                pinDraft = ""
                pinError = false
            }
            container.database.watchHistoryDao().adoptLegacy(user, matched.id)
            container.database.favoriteDao().adoptLegacy(user, matched.id)
        }
    }

    // Profil kapısı kapalıysa önceki seçime dokunulmaz; açıksa yerel eşitleme çalıştırılır.
    LaunchedEffect(profileGate) {
        if (profileGate) {
            container.localProfile.ensureSyncCode()
            container.startLocalSync()
        }
    }

    pinProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pinProfile = null },
            title = { Text("${profile.name} PIN") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinDraft,
                        onValueChange = { pinDraft = it.filter(Char::isDigit).take(6); pinError = false },
                        label = { Text("PIN") },
                        singleLine = true,
                    )
                    if (pinError) Text("PIN hatalı", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (container.localProfile.verifyPin(profile, pinDraft)) {
                            activateProfile(profile)
                            pinProfile = null
                        } else pinError = true
                    }
                }) { Text("Aç") }
            },
            dismissButton = { TextButton(onClick = { pinProfile = null }) { Text("İptal") } },
        )
    }

    fun openTab(tab: Tab) {
        // Profil kapısı açıkken NavHost gizlidir; bu sırada gezinme grafiği henüz kurulmamış olabilir.
        if (backStack == null) return
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
        if (backStack == null) return
        openTab(Tab.Settings)
        navController.navigate(addonsRoute(startAdding)) { launchSingleTop = true }
    }

    // https eklenti bağlantısıyla açılınca (MainActivity -> pendingAddonUrl) ekleme ekranına git.
    val pendingAddonUrl by container.pendingAddonUrl.collectAsStateWithLifecycle()
    var routedAddonUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingAddonUrl, profileGate, showProfileSetup, backStack) {
        val url = pendingAddonUrl
        if (url == null) {
            routedAddonUrl = null
        } else if (url != routedAddonUrl && !profileGate && !showProfileSetup && backStack != null) {
            routedAddonUrl = url
            openAddons(startAdding = false)
        }
    }

    // play://add onayından sonra ilgili bölüme geç. Onay, NavHost görünmeden önce tamamlanabilir;
    // sonucu sıraya alıp profil kapısı ve başlangıç rotası hazır olduğunda uygula.
    LaunchedEffect(container.deepLinks) {
        container.deepLinks.events.collect { event ->
            if (event is DeepLinkEvent.Added) pendingAddedNavigation = event.result
        }
    }
    LaunchedEffect(pendingAddedNavigation, profileGate, showProfileSetup, backStack) {
        val result = pendingAddedNavigation ?: return@LaunchedEffect
        if (profileGate || showProfileSetup || backStack == null) return@LaunchedEffect
        pendingAddedNavigation = null
        openTab(Tab.Settings)
        when (result) {
            is AddResult.CsRepo -> navController.navigate(CS_REPOS_ROUTE) { launchSingleTop = true }
            is AddResult.Addon -> navController.navigate(NUVIO_REPOS_ROUTE) { launchSingleTop = true }
            is AddResult.PluginRepo -> navController.navigate(NUVIO_PLUGINS_ROUTE) { launchSingleTop = true }
        }
    }

    // Oynat: aktif eklenti yoksa tek yol eklenti eklemektir. Aynı içerik yarım izlenmişse önce
    // "devam / baştan" sorulur ([startAtMs] verilmişse soru sorulmaz); sonra kaynak seçimi, sonra oynatıcı.
    var sourcesFor by remember { mutableStateOf<PlayStart?>(null) }
    var downloadFor by remember { mutableStateOf<PlayTarget?>(null) }
    var resumePrompt by remember { mutableStateOf<ResumeAsk?>(null) }
    val context = LocalContext.current
    val activityWindow = (context as? Activity)?.window
    val defaultSoftInputMode = remember(activityWindow) {
        activityWindow?.attributes?.softInputMode ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
    }
    SideEffect {
        activityWindow?.setSoftInputMode(
            if (profileGate || showProfileSetup) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else defaultSoftInputMode,
        )
    }

    // Kaynak seçimi yerine indirilmiş kopyadan (çevrimdışı) oynatır: aynı hedef, aynı geçmiş satırı.
    fun playOffline(download: DownloadEntity, startMs: Long, autoResumed: Boolean) {
        context.startActivity(PlaySession.offline(download, startMs, autoResumed).toIntent(context))
    }

    // Oynat: aktif eklenti yoksa tek yol eklenti eklemektir. [download] verilirse (indirilen) eklenti gerekmez ve kaynak seçimi
    // atlanır. Aynı içerik yarım izlenmişse önce "devam / baştan" sorulur ([startAtMs] verilmişse sorulmaz).
    fun play(target: PlayTarget, startAtMs: Long? = null, autoResumed: Boolean = false, download: DownloadEntity? = null) {
        scope.launch {
            if (container.localProfile.activeProfile()?.isChild == true) {
                val request = target.request
                val allowed = request.tmdbId > 0 && container.tmdb.isAllowedForCurrentProfile(request.type, request.tmdbId)
                if (!allowed) {
                    Toast.makeText(context, "Bu içerik çocuk profiline uygun değil veya yaş bilgisi doğrulanamadı.", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
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
    val isSubScreen = currentRoute == DETAIL_ROUTE ||
        currentRoute == PROVIDER_DETAIL_ROUTE ||
        currentRoute?.startsWith("detail/") == true ||
        currentRoute?.startsWith("provider_detail/") == true ||
        currentRoute?.startsWith("home/category") == true ||
        currentRoute?.startsWith("settings/") == true
    val showNav = !isSubScreen
    val currentProfile = localProfiles.firstOrNull { it.id == activeProfileId }
    val avatarKey = currentProfile?.avatar ?: "space_astro"

    fun selected(tab: Tab) = currentRoute == tab.route || currentRoute?.startsWith(tab.route + "/") == true || currentRoute?.startsWith(tab.route + "?") == true

    // Ana sayfa ve NuvioTopBar durum çubuğunun arkasına uzanır; tüm sekmelerde üst menü sabit konumda kalır.
    val bleedsUnderStatusBar = showNav || currentRoute == Tab.Home.route || currentRoute == DETAIL_ROUTE
    if (!profileGate && !showProfileSetup) {
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
            // İçerik alanı: tüm genişliği kullanır
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
                    onPlayHistory = { entry ->
                        val isSeries = entry.type == MediaType.Tv
                        val startAt = if (WatchRules.isWatched(entry.positionMs, entry.durationMs)) 0L else entry.positionMs
                        playEntry(entry, entry.season.takeIf { isSeries }, entry.episode.takeIf { isSeries }, startAt)
                    },
                    onAddAddon = { openAddons(startAdding = true) },
                    onOpenSearch = { query ->
                        navController.navigate("search?q=${Uri.encode(query)}") {
                            launchSingleTop = true
                        }
                    },
                    onOpenProfile = {
                        profileGate = true
                        managingProfiles = false
                    },
                    contentFocusRequester = homeFocusRequester,
                    topBarFocusRequester = topBarFocusRequester,
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
                        onOpenProfile = {
                            managingProfiles = false
                            showProfileSetup = false
                            profileGate = true
                        },
                        onOpenSync = { navController.navigate(SYNC_ROUTE) },
                        onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                        contentFocusRequester = settingsFocusRequester,
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }
            }
            composable(PLAYER_SETTINGS_ROUTE) { ReadableWidth { PlayerSettingsScreen(onBack = { navController.popBackStack() }) } }
            composable(SPEED_TEST_ROUTE) { ReadableWidth { SpeedTestScreen(onBack = { navController.popBackStack() }) } }
            composable(PROFILE_ROUTE) { ReadableWidth { ProfileScreen(onBack = { navController.popBackStack() }) } }
            composable(SYNC_ROUTE) { ReadableWidth { SyncScreen(onBack = { navController.popBackStack() }) } }
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
                        onOpenRepoManagerCatalog = { navController.navigate(REPO_MANAGER_CATALOG_ROUTE) },
                        onOpenNuvioRepos = { navController.navigate(NUVIO_REPOS_ROUTE) },
                        onOpenNuvioPlugins = { navController.navigate(NUVIO_PLUGINS_ROUTE) },
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
            composable(REPO_MANAGER_CATALOG_ROUTE) {
                ReadableWidth { RepoManagerCatalogScreen(onBack = { navController.popBackStack() }, container = rememberContainer()) }
            }
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
                    onDownload = { target -> downloadFor = target },
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
                if (activeProfileIsChild) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Çocuk profillerinde yalnızca yaş derecesi doğrulanmış aile içerikleri kullanılabilir.")
                    }
                } else ProviderDetailScreen(
                    providerId = providerId,
                    url = rawUrl,
                    onBack = { navController.popBackStack() },
                    onPlay = { target -> play(target) },
                    onDownload = { target -> downloadFor = target },
                )
            }
        }
            }

            // Nuvio Tarzı Yüzen Üst Menü (Floating Pill Top Bar)
            if (showNav) {
                NuvioTopBar(
                    currentRoute = currentRoute,
                    avatarKey = avatarKey,
                    onOpenTab = ::openTab,
                    onOpenProfile = {
                        profileGate = true
                        managingProfiles = false
                    },
                    contentFocusRequester = currentContentFocusRequester,
                    topBarFocusRequester = topBarFocusRequester,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                        .zIndex(100f),
                )
            }
        }
        }
    }

    // Profil ekranlarını aynı Activity içinde tam ekran çiz: IME yeni pencere oluşturup arka planı açığa çıkaramaz.
    if (showProfileSetup) {
        ProfileSetupScreen(
            onCreate = { name, avatar, child, pin ->
                scope.launch {
                    val created = container.localProfile.createProfile(name, avatar, child, pin)
                    container.localProfile.setActiveProfile(created)
                    container.localProfile.completeProfileSetup(created.id)
                    activateProfile(created)
                    profileGate = false
                    managingProfiles = false
                    showProfileSetup = false
                }
            },
            onCancel = {
                if (localProfiles.isNotEmpty() && profileSetupComplete) {
                    showProfileSetup = false
                }
            },
        )
    } else if (profileGate) {
        ProfileChooserScreen(
            profiles = localProfiles.takeIf { profileSetupComplete } ?: emptyList(),
            onSelect = { profile ->
                if (profile.pinHash.isNullOrBlank()) scope.launch { activateProfile(profile) }
                else { pinProfile = profile; pinDraft = ""; pinError = false }
            },
            isAdmin = activeProfileIsAdmin || !profileSetupComplete,
            managingProfiles = managingProfiles && (activeProfileIsAdmin || !profileSetupComplete),
            onToggleManage = { if (activeProfileIsAdmin || !profileSetupComplete) managingProfiles = !managingProfiles },
            onCreateProfile = { showProfileSetup = true },
            onToggleHidden = { profile ->
                scope.launch { runCatching { container.localProfile.setProfileHidden(profile.id, !profile.isHidden) } }
            },
            onDeleteProfile = { profile ->
                scope.launch {
                    val user = container.localProfile.ensureUserId()
                    container.database.watchHistoryDao().clear(user, profile.id)
                    container.database.favoriteDao().clear(user, profile.id)
                    container.localProfile.deleteProfile(profile.id)
                }
            },
            onEditProfile = { profile, name, avatar, child, pin ->
                scope.launch {
                    container.localProfile.renameProfile(profile.id, name)
                    container.localProfile.setProfileAvatar(profile.id, avatar)
                    container.localProfile.setProfileChild(profile.id, child)
                    if (pin.isNotBlank()) container.localProfile.setProfilePin(profile.id, pin)
                }
            },
            onClose = if (profileSetupComplete && localProfiles.any { it.id == activeProfileId }) {
                { profileGate = false; managingProfiles = false }
            } else null,
        )
    }

    sourcesFor?.let { start ->
        SourcesSheet(target = start.target, onDismiss = { sourcesFor = null }, onPicked = ::onSourcePicked)
    }

    downloadFor?.let { target ->
        SourcesSheet(
            target = target,
            onDismiss = { downloadFor = null },
            onPicked = {},
            downloadOnly = true,
        )
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
 * Nuvio tarzı ekranın üst ortasında yüzen (floating pill) yatay kapsül menü.
 * Sırasıyla: Ana Sayfa, Arama, Canlı TV, Kütüphane, Ayarlar ve Profil Avatarı.
 * D-Pad ile sağa-sola geçiş ve içerik alanına aşağı-yukarı odak köprüsü (FocusRequester / focusProperties) tam uyumludur.
 */
@Composable
fun NuvioTopBar(
    currentRoute: String?,
    avatarKey: String,
    onOpenTab: (Tab) -> Unit,
    onOpenProfile: () -> Unit,
    contentFocusRequester: FocusRequester,
    topBarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
        color = Color(0xE60A131F),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .focusProperties {
                    down = contentFocusRequester
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1. Ana Sayfa (Home)
            val isHomeSelected = currentRoute == Tab.Home.route || currentRoute == null
            NuvioTopBarItem(
                icon = PlayIcons.Home,
                contentDescription = stringResource(R.string.tab_home),
                selected = isHomeSelected,
                contentFocusRequester = contentFocusRequester,
                itemFocusRequester = if (isHomeSelected) topBarFocusRequester else null,
                onClick = { onOpenTab(Tab.Home) },
            )

            // 2. Arama (Search)
            val isSearchSelected = currentRoute == Tab.Search.route || currentRoute?.startsWith("search") == true
            NuvioTopBarItem(
                icon = PlayIcons.Search,
                contentDescription = stringResource(R.string.tab_search),
                selected = isSearchSelected,
                contentFocusRequester = contentFocusRequester,
                itemFocusRequester = if (isSearchSelected) topBarFocusRequester else null,
                onClick = { onOpenTab(Tab.Search) },
            )

            // 3. Canlı TV (TV / Live TV rotası)
            val isTvSelected = currentRoute == Tab.Tv.route || currentRoute?.startsWith("tv") == true
            NuvioTopBarItem(
                icon = PlayIcons.Tv,
                contentDescription = stringResource(R.string.tab_tv),
                selected = isTvSelected,
                contentFocusRequester = contentFocusRequester,
                itemFocusRequester = if (isTvSelected) topBarFocusRequester else null,
                onClick = { onOpenTab(Tab.Tv) },
            )

            // 4. Kütüphane (Library)
            val isLibrarySelected = currentRoute == Tab.Library.route || currentRoute?.startsWith("library") == true
            NuvioTopBarItem(
                icon = PlayIcons.Folder,
                contentDescription = stringResource(R.string.tab_library),
                selected = isLibrarySelected,
                contentFocusRequester = contentFocusRequester,
                itemFocusRequester = if (isLibrarySelected) topBarFocusRequester else null,
                onClick = { onOpenTab(Tab.Library) },
            )

            // 5. Ayarlar (Settings - Dişli çark)
            val isSettingsSelected = currentRoute == Tab.Settings.route || currentRoute?.startsWith("settings") == true
            NuvioTopBarItem(
                icon = PlayIcons.Gear,
                contentDescription = stringResource(R.string.tab_settings),
                selected = isSettingsSelected,
                contentFocusRequester = contentFocusRequester,
                itemFocusRequester = if (isSettingsSelected) topBarFocusRequester else null,
                onClick = { onOpenTab(Tab.Settings) },
            )

            // 6. Profil Avatarı (En sağda, Ayarlar ikonunun hemen yanında)
            NuvioTopBarAvatar(
                avatarKey = avatarKey,
                contentFocusRequester = contentFocusRequester,
                onClick = onOpenProfile,
            )
        }
    }
}

@Composable
private fun NuvioTopBarItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    contentFocusRequester: FocusRequester,
    itemFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var isBoxFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    val isFocused = isBoxFocused || isInteractionFocused

    val iconTint = when {
        isFocused -> Color(0xFF00E5FF)
        selected -> Color(0xFF00E5FF)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .then(if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester) else Modifier)
            .focusProperties {
                down = contentFocusRequester
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val handled = runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                    handled
                } else {
                    false
                }
            }
            .onFocusChanged { isBoxFocused = it.isFocused }
            .tvFocusableScaleGlow(
                scale = 1.15f,
                glowColor = Color(0xFF00E5FF),
                shape = CircleShape,
                isFocused = isFocused,
                zIndex = 10f,
                fillGlowAlpha = 0.20f,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun NuvioTopBarAvatar(
    avatarKey: String,
    contentFocusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var isBoxFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    val isFocused = isBoxFocused || isInteractionFocused

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .focusProperties {
                down = contentFocusRequester
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val handled = runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                    handled
                } else {
                    false
                }
            }
            .onFocusChanged { isBoxFocused = it.isFocused }
            .tvFocusableScaleGlow(
                scale = 1.15f,
                glowColor = Color(0xFF00E5FF),
                shape = CircleShape,
                isFocused = isFocused,
                zIndex = 10f,
                fillGlowAlpha = 0.20f,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        ProfileAvatar(
            avatar = avatarKey,
            size = 34,
        )
    }
}
