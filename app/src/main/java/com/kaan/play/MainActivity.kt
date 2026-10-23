package com.kaan.play

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.plugin.DeepLink
import com.kaan.play.data.subtitle.DebugEndpoints
import com.kaan.play.ui.player.PlayerNoticeLogic
import com.kaan.play.data.update.UpdateNotifications
import com.kaan.play.ui.components.SplashOverlay
import com.kaan.play.ui.nav.PlayRoot
import com.kaan.play.ui.theme.PlayTheme
import com.kaan.play.ui.addons.DeepLinkHost
import com.kaan.play.ui.update.UpdateDialog
import com.kaan.play.ui.announcement.AnnouncementDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// AppCompatActivity: uygulama başına dil (AppCompatDelegate.setApplicationLocales) için gerekli.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PlayApp).container
        val settings = container.settings
        // Arka planda, oturumda bir kez; hata/zaman aşımı sessiz (bkz. UpdateChecker).
        // Yalnızca debug derlemede, sahte release sunucusuyla denemek için: --es play_update_endpoint <adres>.
        val debugEndpoint = if (BuildConfig.DEBUG) intent.getStringExtra("play_update_endpoint") else null
        container.updateChecker.checkOnce(container.appScope, debugEndpoint)
        val debugAnnounce = if (BuildConfig.DEBUG) intent.getStringExtra("play_announcements_endpoint") else null
        container.announcementChecker.checkOnce(container.appScope, container.updateChecker, debugAnnounce)
        // Yalnızca debug derlemede, sahte altyazı servisiyle denemek için: --es play_subs_endpoint http://host:port (SubDL + OpenSubtitles).
        if (BuildConfig.DEBUG) {
            intent.getStringExtra("play_subs_endpoint")?.let { DebugEndpoints.subsBase = it.trimEnd('/') }
            if (intent.hasExtra("simulate_release")) {
                PlayerNoticeLogic.simulateRelease = intent.getBooleanExtra("simulate_release", false)
            }
            if (intent.hasExtra("test_simulate_skip")) {
                PlayerNoticeLogic.simulateSkipOnce = intent.getBooleanExtra("test_simulate_skip", false)
            }
            if (intent.hasExtra("test_force_notice")) {
                PlayerNoticeLogic.forceNoticeForTest = intent.getBooleanExtra("test_force_notice", false)
            }
            if (intent.hasExtra("test_big_buffer")) {
                PlayerNoticeLogic.testBigBuffer = intent.getBooleanExtra("test_big_buffer", false)
            }
            if (intent.hasExtra("test_force_stall")) {
                PlayerNoticeLogic.testForceStall = intent.getBooleanExtra("test_force_stall", false)
            }
        }
        if (BuildConfig.DEBUG) {
            // Duman testi: oynatıcıyı test videosuyla doğrudan aç (geçmişe yazılmaz). --es play_test_video <adres>
            intent.getStringExtra("play_test_video")?.let { url ->
                startActivity(com.kaan.play.ui.player.PlaySession.testVideo(url).toIntent(this))
            }
            intent.getStringExtra("select_provider")?.let { raw ->
                val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                container.appScope.launch { container.providers.setSelectedProviders(ids) }
            }
            val runDiag = intent.getBooleanExtra("play_diag", false) || intent.getStringExtra("play_diag") == "true"
            if (runDiag) {
                val filter = intent.getStringExtra("diag_plugins")?.split(",")?.map { it.trim() }
                container.appScope.launch {
                    com.kaan.play.data.diag.PlayDiagRunner.runDiagnostics(this@MainActivity, container, filter)
                }
            }
        }
        // Açılışta tek seferlik okuma (milisaniyeler): kayıtlı tema ilk karede doğru çıksın, yanıp sönmesin.
        val initialTheme = runBlocking { settings.themeMode.first() }

        // Yalnızca ilk açılışta: dil değişimi gibi yeniden oluşturmalarda aynı bağlantı tekrar işlenmesin.
        if (savedInstanceState == null) {
            handleLink(intent)
            handleUpdateIntent(intent)
        }

        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = initialTheme)
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Durum/gezinme çubuğu ikonları temaya uysun.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            PlayTheme(darkTheme = dark) {
                Box {
                    PlayRoot()
                    SplashOverlay()
                    UpdateDialog(container.updateFlow)
                    AnnouncementDialog(container.announcementChecker, container.updateFlow)
                    DeepLinkHost(container.deepLinks)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
        handleUpdateIntent(intent)
        if (BuildConfig.DEBUG) {
            intent.getStringExtra("select_provider")?.let { raw ->
                val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val container = (application as PlayApp).container
                container.appScope.launch { container.providers.setSelectedProviders(ids) }
            }
            val runDiag = intent.getBooleanExtra("play_diag", false) || intent.getStringExtra("play_diag") == "true"
            if (runDiag) {
                val container = (application as PlayApp).container
                val filter = intent.getStringExtra("diag_plugins")?.split(",")?.map { it.trim() }
                container.appScope.launch {
                    com.kaan.play.data.diag.PlayDiagRunner.runDiagnostics(this@MainActivity, container, filter)
                }
            }
        }
    }

    /**
     * "Güncelleme indirildi" bildirimine dokunuldu: doğrulanmış APK hazırsa kurulum ekranı açılır
     * (bkz. [com.kaan.play.data.update.UpdateFlow.restoreReady]).
     */
    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action != UpdateNotifications.ACTION_INSTALL) return
        val info = UpdateNotifications.decode(intent.getStringExtra(UpdateNotifications.EXTRA_INFO)) ?: return
        (application as PlayApp).container.updateFlow.restoreReady(info)
    }

    /**
     * Tarayıcıdan/başka uygulamadan gelen bağlantı: https manifest bağlantısı "eklenti ekle" akışını başlatır,
     * `play://add?repo=` / `play://add?nuvio=` önce onay penceresi açar, onaylanırsa ekler (sonuç Snackbar ile).
     */
    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        val container = (application as PlayApp).container
        when (uri.scheme) {
            "https" -> container.pendingAddonUrl.value = uri.toString()
            DeepLink.SCHEME -> {
                // Opak ya da hiyerarşik olmayan bağlantıda (play:add?...) getQueryParameter fırlatır.
                val repo = if (uri.isHierarchical) uri.getQueryParameter("repo") else null
                val nuvio = if (uri.isHierarchical) uri.getQueryParameter("nuvio") else null
                val nuvioPlugin = if (uri.isHierarchical) uri.getQueryParameter("nuvio_plugin") else null
                container.deepLinks.handle(uri.scheme, uri.host, repo, nuvio, nuvioPlugin)
            }
        }
    }
}
