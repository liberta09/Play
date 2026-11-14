package com.kaan.play.tv

import com.kaan.play.R
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.kaan.play.PlayApp
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.settings.ProfileAvatar
import kotlinx.coroutines.flow.first

/** Sol menünün bölümleri (kapalıyken simge, odak girince adlarıyla açılır). */
enum class Section(@StringRes val label: Int) {
    Search(R.string.tv_section_search), Home(R.string.tv_section_home), Live(R.string.tv_section_live),
    Movies(R.string.tv_section_movies), Series(R.string.tv_section_series),
    Library(R.string.tv_section_library), Settings(R.string.tv_section_settings)
}

/** Profil seçimi: açılışta karar bekleniyor / açılış kapısı (Geri uygulamadan çıkar) / menüden açıldı / kapalı. */
private enum class ProfileGate { Deciding, Launch, Menu, Off }

/** Açılan detay: hangi eklentinin hangi içeriği, kartı yatay mıydı (§4.1). */
data class DetailRef(val providerId: String, val url: String, val landscape: Boolean, val title: String, val imageUrl: String?)

@Composable
fun TvApp(onPlay: PlayLauncher) {
    // Play gibi modeller dile göre anahtarlanır: Dil değişince satır adları ve TMDB içeriği yeni dilde yüklenir.
    val lang = AppLanguage.effective().iso
    val home: TvHomeModel = viewModel(key = "home-$lang")
    val movies: TvHomeModel = viewModel(key = "movies-$lang")
    val series: TvHomeModel = viewModel(key = "series-$lang")
    val live: TvLiveModel = viewModel()
    home.configure(HomeKind.Home) // Filmler / Diziler ilk açıldıklarında yüklenir (açılışta kutu yalnız ana sayfayı yükler)
    var section by rememberSaveable { mutableStateOf(Section.Home) }
    var detailProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var detailUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var detailLandscape by rememberSaveable { mutableStateOf(false) }
    var detailTitle by rememberSaveable { mutableStateOf("") }
    var detailImage by rememberSaveable { mutableStateOf<String?>(null) }

    val open: (DetailRef) -> Unit = { ref ->
        detailProvider = ref.providerId; detailUrl = ref.url; detailLandscape = ref.landscape
        detailTitle = ref.title; detailImage = ref.imageUrl
    }
    // Detaydan açılan detaylar (benzer içerik): Geri önceki detaya döner (Play'deki gibi); boşsa detay kapanır.
    val detailBack = remember { mutableStateListOf<DetailRef>() }
    val closeDetail: () -> Unit = {
        if (detailBack.isNotEmpty()) open(detailBack.removeAt(detailBack.lastIndex)) else { detailProvider = null; detailUrl = null }
    }
    // Detaydaki oyuncu kartı: oyuncunun adıyla arama açılır; aramada Geri o detaya döner.
    var pendingSearch by remember { mutableStateOf<String?>(null) }
    var searchReturn by remember { mutableStateOf<DetailRef?>(null) }
    // "Tümünü gör" ızgarası: detay onun üstünde açılır, Geri ızgaraya döner.
    var categoryGrid by remember { mutableStateOf<CategoryGrid?>(null) }
    // Telefon kumandasının Ana sayfa tuşu / hedefsiz yazı (TvRemote): detay kapanır, bölüm açılır.
    val remoteSection by TvRemote.openSection.collectAsState()
    LaunchedEffect(remoteSection) {
        remoteSection?.let { detailBack.clear(); closeDetail(); categoryGrid = null; section = it; TvRemote.openSection.value = null }
    }

    val container = (LocalContext.current.applicationContext as PlayApp).container
    val profileRepo = container.localProfile
    val profiles by profileRepo.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfile by profileRepo.activeProfile.collectAsStateWithLifecycle(initialValue = null)
    // Poster URL şablonu burada bir kez okunur, kartlara LocalPosterTemplate ile iner.
    val posterTemplate by container.settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = PosterUrlTemplate.DEFAULT)
    var gate by rememberSaveable { mutableStateOf(ProfileGate.Deciding) }
    // Play'in "Son profili hatırla" kuralı (ProfileGateRules): açıkken son profille doğrudan girilir; kapalıyken birden çok
    // profilde her açılışta "Kim izliyor?" sorulur. Etkin profil PIN'liyse her durumda sorulur (kumandayı alan girmesin).
    LaunchedEffect(Unit) {
        if (gate != ProfileGate.Deciding) return@LaunchedEffect
        val count = profileRepo.profiles.first().size
        val pinned = profileRepo.activeProfile.first()?.hasPin == true
        val rememberLast = container.settings.rememberLastProfile.first()
        val ask = pinned || (!rememberLast && count > 1)
        gate = if (ask) ProfileGate.Launch else ProfileGate.Off
    }

    CompositionLocalProvider(LocalPosterTemplate provides posterTemplate) {
        PlayTvTheme {
            when (gate) {
                ProfileGate.Deciding -> { Box(Modifier.fillMaxSize().background(Bg)); return@PlayTvTheme }
                ProfileGate.Launch, ProfileGate.Menu -> {
                    BackHandler(enabled = gate == ProfileGate.Menu) { gate = ProfileGate.Off }
                    // Gizli profiller bu seçicide görünmez; Ayarlar → Profiller ve hesap → Etkin profil'den seçilir.
                    TvProfileChooser(profiles, activeProfile?.id, hideHidden = true, onChosen = { gate = ProfileGate.Off })
                    return@PlayTvTheme
                }
                ProfileGate.Off -> Unit
            }
            val provider = detailProvider
            val url = detailUrl
            if (provider != null && url != null) {
                BackHandler(onBack = closeDetail)
                val current = DetailRef(provider, url, detailLandscape, detailTitle, detailImage)
                TvDetailScreen(
                    ref = current,
                    onPlay = onPlay,
                    onOpen = { next -> detailBack.add(current); open(next) },
                    onSearch = { name ->
                        searchReturn = current; pendingSearch = name
                        detailProvider = null; detailUrl = null; categoryGrid = null; section = Section.Search
                    },
                )
                return@PlayTvTheme
            }
            categoryGrid?.let { grid ->
                BackHandler { categoryGrid = null }
                Box(Modifier.fillMaxSize().background(Bg)) { TvCategoryScreen(grid, onOpen = open) }
                return@PlayTvTheme
            }
            // Ana sayfa dışındaki bölümde Geri, ana sayfaya döner; ana sayfada uygulamadan çıkar.
            BackHandler(enabled = section != Section.Home) { section = Section.Home }
            // Oyuncu aramasından Geri: aramayı açan detaya dönülür. Başka bölüme geçilince unutulur.
            BackHandler(enabled = section == Section.Search && searchReturn != null) { searchReturn?.let(open); searchReturn = null }
            LaunchedEffect(section) { if (section != Section.Search) searchReturn = null }

            val drawer = rememberDrawerState(DrawerValue.Closed)
            val itemFocus = remember { Section.entries.associateWith { FocusRequester() } }
            NavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    // Menüye girince odak seçili bölümdedir, kartın hizasındaki öğede değil.
                    Column(
                        Modifier.fillMaxHeight().background(Bg).padding(horizontal = 12.dp, vertical = 24.dp)
                            .focusRestorer(itemFocus.getValue(section)).focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        val current = activeProfile
                        if (current != null) {
                            NavigationDrawerItem(
                                selected = false,
                                onClick = { gate = ProfileGate.Menu },
                                leadingContent = { ProfileAvatar(avatar = current.avatar, size = 24) },
                            ) { Text(current.name) }
                        }
                        Section.entries.forEach { s ->
                            NavigationDrawerItem(
                                selected = section == s,
                                onClick = { section = s },
                                modifier = Modifier.focusRequester(itemFocus.getValue(s)),
                                leadingContent = { Icon(s.icon(), contentDescription = null) },
                            ) { Text(stringResource(s.label)) }
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    when (section) {
                        Section.Home -> TvHomeScreen(model = home, onOpen = open, onPlay = onPlay, onSeeAll = { categoryGrid = it })
                        Section.Movies -> {
                            movies.configure(HomeKind.Movies)
                            TvHomeScreen(model = movies, onOpen = open, onPlay = onPlay, onSeeAll = { categoryGrid = it })
                        }
                        Section.Series -> {
                            series.configure(HomeKind.Series)
                            TvHomeScreen(model = series, onOpen = open, onPlay = onPlay, onSeeAll = { categoryGrid = it })
                        }
                        Section.Search -> TvSearchScreen(onOpen = open, pendingQuery = pendingSearch, onQueryUsed = { pendingSearch = null })
                        Section.Live -> TvLiveScreen(onPlay = onPlay)
                        Section.Library -> TvLibraryScreen(onOpen = open, onPlay = onPlay)
                        Section.Settings -> TvSettingsScreen(onCatalogChanged = { home.load(); movies.load(); series.load(); live.invalidate(); TvDetailLoader.forgetProviders() })
                    }
                }
            }
        }
    }
}

private fun Section.icon(): ImageVector = when (this) {
    Section.Home -> PlayIcons.Home
    Section.Live -> PlayIcons.Tv
    Section.Movies -> PlayIcons.PlayCircle
    Section.Series -> PlayIcons.NextEpisode
    Section.Search -> PlayIcons.Search
    Section.Library -> PlayIcons.Library
    Section.Settings -> PlayIcons.Settings
}

@Composable
internal fun EmptyNote(title: String, body: String? = null) {
    Centered {
        Text(title, color = TextMain, style = androidx.tv.material3.MaterialTheme.typography.titleLarge)
        if (body != null) {
            Spacer(Modifier.height(10.dp))
            Text(body, color = TextDim)
        }
    }
}
