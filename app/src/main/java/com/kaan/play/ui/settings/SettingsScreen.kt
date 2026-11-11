package com.kaan.play.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.notify.NewEpisodeScheduler
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.PlayCardRow
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.NavBarPosition
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.navBarReturnEdge
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.launch

private enum class OpenDialog { Language, NavBarPosition, Poster }
private enum class DataConfirmAction { ClearCache, ClearHistory, ClearFavorites, ResetSettings }

private data class ConfirmSpec(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val doneMessage: String,
    val isDestructive: Boolean = false,
)

/**
 * Gruplanmış tek kaydırılabilir Ayarlar ekranı.
 * 6 grup altında 22 ayar kalemi barındırır.
 */
@Composable
fun SettingsScreen(
    onOpenAddons: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenWebSites: () -> Unit,
    onOpenSourceHealth: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenWifiSync: () -> Unit,
    onOpenWatchParty: () -> Unit = {},
    onOpenAbout: () -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // State collections
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.Dark)
    val navPosition by container.settings.navBarPosition.collectAsStateWithLifecycle(initialValue = NavBarPosition.DEFAULT)
    val performanceMode by container.settings.performanceMode.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_PERFORMANCE_MODE,
    )
    val heroAutoAdvance by container.settings.heroAutoAdvance.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_HERO_AUTO_ADVANCE,
    )
    val heroTrailerEnabled by container.settings.heroTrailerEnabled.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_HERO_TRAILER_ENABLED,
    )
    val posterTemplate by container.settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = PosterUrlTemplate.DEFAULT)
    val newEpisodeNotifications by container.settings.newEpisodeNotifications.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_NEW_EPISODE_NOTIFICATIONS,
    )
    val addonCount by container.installedSourcesCount.collectAsStateWithLifecycle(initialValue = null)
    val collections by container.collections.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfile by container.localProfile.activeProfile.collectAsStateWithLifecycle(initialValue = null)
    val rememberLastProfile by container.settings.rememberLastProfile.collectAsStateWithLifecycle(initialValue = true)

    var language by remember { mutableStateOf(AppLanguage.choice()) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }
    var posterDraft by remember { mutableStateOf(PosterUrlTemplate.DEFAULT) }
    var pendingConfirm by remember { mutableStateOf<DataConfirmAction?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Permission callback */ }

    val focus = rememberFocusMemory()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )

            // ==========================================
            // 1. Eklentiler ve kaynaklar
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_addons))

            // Eklentiler
            PlayCardRow(
                title = stringResource(R.string.addons_title),
                badge = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) },
                icon = PlayIcons.Extension,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                    .navBarReturnEdge(topBarFocusRequester, includeTop = true, includeBottom = false)
                    .focusMemory(focus, "addons", isDefault = true),
                onClick = onOpenAddons,
            )

            // Koleksiyonlar
            PlayCardRow(
                title = stringResource(R.string.collections_title),
                subtitle = stringResource(R.string.collections_subtitle),
                badge = collections.size.takeIf { it > 0 }?.toString(),
                icon = PlayIcons.Folder,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "collections"),
                onClick = onOpenCollections,
            )

            // Web siteleri
            PlayCardRow(
                title = stringResource(R.string.web_sites),
                subtitle = stringResource(R.string.web_sites_summary),
                icon = PlayIcons.Code,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "web_sites"),
                onClick = onOpenWebSites,
            )

            // Kaynak hata geçmişi ve kara liste
            PlayCardRow(
                title = stringResource(R.string.source_health_title),
                subtitle = stringResource(R.string.source_health_subtitle),
                icon = PlayIcons.Server,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "source_health"),
                onClick = onOpenSourceHealth,
            )

            // ==========================================
            // 2. Görünüm ve kişiselleştirme
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_appearance))

            // Tema
            PlayCardRow(
                title = stringResource(R.string.settings_theme),
                value = stringResource(theme.labelRes()),
                icon = PlayIcons.Palette,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "theme"),
                onClick = onOpenTheme,
            )

            // Dil
            PlayCardRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(language.labelRes()),
                icon = PlayIcons.Globe,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "language"),
                onClick = { dialog = OpenDialog.Language },
            )

            // Gezinti çubuğu konumu
            PlayCardRow(
                title = stringResource(R.string.nav_bar_position_title),
                value = stringResource(navPosition.labelRes()),
                icon = PlayIcons.Resize,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "nav_bar_position"),
                onClick = { dialog = OpenDialog.NavBarPosition },
            )

            // Performans modu
            PlayCardRow(
                title = stringResource(R.string.settings_performance_title),
                subtitle = stringResource(R.string.settings_performance_summary),
                icon = PlayIcons.Clock,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "performance_mode"),
                onClick = { scope.launch { container.settings.setPerformanceMode(!performanceMode) } },
                trailing = {
                    Switch(
                        checked = performanceMode,
                        onCheckedChange = { scope.launch { container.settings.setPerformanceMode(it) } },
                    )
                },
            )

            // Poster şablonu
            PlayCardRow(
                title = stringResource(R.string.settings_poster_url),
                value = stringResource(
                    if (posterTemplate.isBlank()) R.string.settings_poster_url_default
                    else R.string.settings_poster_url_value,
                ),
                icon = PlayIcons.Library,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "poster_url"),
                onClick = {
                    posterDraft = posterTemplate
                    dialog = OpenDialog.Poster
                },
            )

            // Kapakları otomatik değiştir
            PlayCardRow(
                title = stringResource(R.string.settings_hero_auto_advance),
                icon = PlayIcons.PlayCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "hero_auto_advance"),
                onClick = { scope.launch { container.settings.setHeroAutoAdvance(!heroAutoAdvance) } },
                trailing = {
                    Switch(
                        checked = heroAutoAdvance,
                        onCheckedChange = { scope.launch { container.settings.setHeroAutoAdvance(it) } },
                    )
                },
            )

            // Ana sayfada fragman oynat
            if (heroAutoAdvance) {
                PlayCardRow(
                    title = stringResource(R.string.settings_hero_trailer),
                    subtitle = stringResource(R.string.settings_hero_trailer_desc),
                    icon = PlayIcons.PlayTriangle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                        .focusMemory(focus, "hero_trailer"),
                    onClick = { scope.launch { container.settings.setHeroTrailerEnabled(!heroTrailerEnabled) } },
                    trailing = {
                        Switch(
                            checked = heroTrailerEnabled,
                            onCheckedChange = { scope.launch { container.settings.setHeroTrailerEnabled(it) } },
                        )
                    },
                )
            }

            // ==========================================
            // 2. Oynatma
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_playback))

            // Oynatıcı ayarları
            PlayCardRow(
                title = stringResource(R.string.settings_player),
                icon = PlayIcons.PlayCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "player_settings"),
                onClick = onOpenPlayer,
            )

            // Yeni bölüm bildirimleri
            PlayCardRow(
                title = stringResource(R.string.settings_new_episode_notifications),
                subtitle = stringResource(R.string.settings_new_episode_notifications_summary),
                icon = PlayIcons.Star,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "new_episode_notifications"),
                onClick = {
                    val next = !newEpisodeNotifications
                    scope.launch { container.settings.setNewEpisodeNotifications(next) }
                    if (next) {
                        NewEpisodeScheduler.schedule(context)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        NewEpisodeScheduler.cancel(context)
                    }
                },
                trailing = {
                    Switch(
                        checked = newEpisodeNotifications,
                        onCheckedChange = { next ->
                            scope.launch { container.settings.setNewEpisodeNotifications(next) }
                            if (next) {
                                NewEpisodeScheduler.schedule(context)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                NewEpisodeScheduler.cancel(context)
                            }
                        },
                    )
                },
            )



            // ==========================================
            // 4. Profil ve senkronizasyon
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_profile_sync))

            // Profil seçimi
            PlayCardRow(
                title = stringResource(R.string.settings_profile_selection),
                subtitle = stringResource(R.string.settings_profile_selection_subtitle),
                value = activeProfile?.name,
                icon = PlayIcons.Pin,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "profile_selection"),
                onClick = onOpenProfile,
            )

            // Son profili hatırla
            PlayCardRow(
                title = stringResource(R.string.settings_remember_last_profile),
                subtitle = stringResource(
                    if (rememberLastProfile) R.string.settings_remember_last_profile_summary_on
                    else R.string.settings_remember_last_profile_summary_off,
                ),
                icon = PlayIcons.Lock,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "remember_profile"),
                onClick = { scope.launch { container.settings.setRememberLastProfile(!rememberLastProfile) } },
                trailing = {
                    Switch(
                        checked = rememberLastProfile,
                        onCheckedChange = { scope.launch { container.settings.setRememberLastProfile(it) } },
                    )
                },
            )

            // Bulut hesabı ve senkronizasyon
            PlayCardRow(
                title = stringResource(R.string.settings_cloud_sync),
                subtitle = stringResource(R.string.settings_cloud_sync_summary),
                icon = PlayIcons.Cloud,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "cloud_sync"),
                onClick = onOpenSync,
            )

            // Wi-Fi senkronizasyonu
            PlayCardRow(
                title = stringResource(R.string.settings_wifi_sync),
                subtitle = stringResource(R.string.settings_wifi_sync_summary),
                icon = PlayIcons.SubtitleSync,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "wifi_sync"),
                onClick = onOpenWifiSync,
            )

            // Watch Party
            PlayCardRow(
                title = stringResource(R.string.watch_party_title),
                subtitle = stringResource(R.string.watch_party_subtitle),
                icon = PlayIcons.SubtitleSync,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "watch_party"),
                onClick = onOpenWatchParty,
            )

            // ==========================================
            // 5. Bağlantı ve veri
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_connection_data))

            // Ağ ve İnternet
            PlayCardRow(
                title = stringResource(R.string.network_settings_title),
                subtitle = stringResource(R.string.network_settings_subtitle),
                icon = PlayIcons.Database,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "network_settings"),
                onClick = onOpenNetwork,
            )

            // İnternet hız testi
            PlayCardRow(
                title = stringResource(R.string.settings_speed_test),
                subtitle = stringResource(R.string.settings_speed_test_summary),
                icon = PlayIcons.Download,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "speed_test"),
                onClick = onOpenSpeedTest,
            )

            // Önbelleği temizle
            PlayCardRow(
                title = stringResource(R.string.data_clear_cache),
                subtitle = stringResource(R.string.data_clear_cache_summary),
                icon = PlayIcons.Refresh,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "clear_cache"),
                onClick = { pendingConfirm = DataConfirmAction.ClearCache },
            )

            // İzleme geçmişini sil
            PlayCardRow(
                title = stringResource(R.string.data_clear_history),
                subtitle = stringResource(R.string.data_clear_history_summary),
                icon = PlayIcons.SeekBack,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "clear_history"),
                onClick = { pendingConfirm = DataConfirmAction.ClearHistory },
            )

            // Favorileri sil
            PlayCardRow(
                title = stringResource(R.string.data_clear_favorites),
                subtitle = stringResource(R.string.data_clear_favorites_summary),
                icon = PlayIcons.Heart,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "clear_favorites"),
                onClick = { pendingConfirm = DataConfirmAction.ClearFavorites },
            )

            // Tüm ayarları varsayılana döndür
            PlayCardRow(
                title = stringResource(R.string.data_reset_settings),
                subtitle = stringResource(R.string.data_reset_settings_summary),
                icon = PlayIcons.Trash,
                iconTint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = false)
                    .focusMemory(focus, "reset_settings"),
                onClick = { pendingConfirm = DataConfirmAction.ResetSettings },
            )

            // ==========================================
            // 6. Hakkında
            // ==========================================
            SettingsGroupHeader(title = stringResource(R.string.settings_group_about))

            // Hakkında
            PlayCardRow(
                title = stringResource(R.string.settings_about),
                value = BuildConfig.VERSION_NAME,
                icon = PlayIcons.InfoCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .navBarReturnEdge(topBarFocusRequester, includeTop = false, includeBottom = true)
                    .focusMemory(focus, "about"),
                onClick = onOpenAbout,
            )

            Row(modifier = Modifier.padding(bottom = 24.dp)) {}
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    // Dialogs
    when (dialog) {
        OpenDialog.Language -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageChoice.entries,
            selected = language,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                language = it
                dialog = null
                AppLanguage.apply(it)
            },
            onDismiss = { dialog = null },
        )
        OpenDialog.NavBarPosition -> ChoiceDialog(
            title = stringResource(R.string.nav_bar_position_title),
            options = NavBarPosition.entries,
            selected = navPosition,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                scope.launch { container.settings.setNavBarPosition(it) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        OpenDialog.Poster -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_poster_url_title)) },
            text = {
                OutlinedTextField(
                    value = posterDraft,
                    onValueChange = { posterDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_poster_url)) },
                    placeholder = { Text(stringResource(R.string.settings_poster_url_hint)) },
                    supportingText = { Text(stringResource(R.string.settings_poster_url_help)) },
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { container.settings.setPosterUrlTemplate(posterDraft) }
                    dialog = null
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
        null -> Unit
    }

    // Confirmation dialogs
    pendingConfirm?.let { action ->
        val cancelFocus = rememberInitialFocus()
        val spec = when (action) {
            DataConfirmAction.ClearCache -> ConfirmSpec(
                title = stringResource(R.string.data_clear_cache),
                body = stringResource(R.string.data_clear_cache_confirm),
                confirmLabel = stringResource(R.string.data_confirm_clear),
                doneMessage = stringResource(R.string.data_clear_cache_done),
            )
            DataConfirmAction.ClearHistory -> ConfirmSpec(
                title = stringResource(R.string.data_clear_history),
                body = stringResource(R.string.data_clear_history_confirm),
                confirmLabel = stringResource(R.string.data_confirm_delete),
                doneMessage = stringResource(R.string.data_clear_history_done),
                isDestructive = true,
            )
            DataConfirmAction.ClearFavorites -> ConfirmSpec(
                title = stringResource(R.string.data_clear_favorites),
                body = stringResource(R.string.data_clear_favorites_confirm),
                confirmLabel = stringResource(R.string.data_confirm_delete),
                doneMessage = stringResource(R.string.data_clear_favorites_done),
                isDestructive = true,
            )
            DataConfirmAction.ResetSettings -> ConfirmSpec(
                title = stringResource(R.string.data_reset_settings),
                body = stringResource(R.string.data_reset_settings_confirm),
                confirmLabel = stringResource(R.string.data_reset_settings_action),
                doneMessage = stringResource(R.string.data_reset_settings_done),
                isDestructive = true,
            )
        }

        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(spec.title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(spec.body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirm = null
                        scope.launch {
                            when (action) {
                                DataConfirmAction.ClearCache -> {
                                    container.clearCaches()
                                }
                                DataConfirmAction.ClearHistory -> {
                                    container.library.clearHistory()
                                    container.liveChannels.clearWatchHistory()
                                }
                                DataConfirmAction.ClearFavorites -> {
                                    container.library.clearFavorites()
                                    container.liveChannels.clearFavorites()
                                }
                                DataConfirmAction.ResetSettings -> {
                                    container.settings.resetAll()
                                    if (AppLanguage.choice() != LanguageChoice.System) {
                                        AppLanguage.apply(LanguageChoice.System)
                                    }
                                }
                            }
                            snackbarHostState.showSnackbar(spec.doneMessage)
                        }
                    },
                ) {
                    Text(
                        text = spec.confirmLabel,
                        color = if (spec.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }, modifier = Modifier.focusRequester(cancelFocus)) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp),
    )
}

internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Amoled -> R.string.theme_amoled
    ThemeMode.OceanBlue -> R.string.theme_ocean_blue
    ThemeMode.PurpleNight -> R.string.theme_purple_night
    ThemeMode.HighContrast -> R.string.theme_high_contrast
    ThemeMode.System -> R.string.common_system
}

private fun LanguageChoice.labelRes(): Int = when (this) {
    LanguageChoice.System -> R.string.common_system
    LanguageChoice.English -> R.string.language_english
    LanguageChoice.Turkish -> R.string.language_turkish
}
