package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ListItem
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import kotlinx.coroutines.launch
import com.kaan.play.data.sync.local.LocalSyncController

/**
 * Ayarlar (TV'ye özgü tasarım; Play'in telefondaki ayar sayfalarının karşılığı, Play ekranı gömülmez):
 * ana ekranda bölümler, her bölüm kendi sayfasında. Geri: sayfadan bölüm listesine.
 * [onCatalogChanged]: eklenti eklenince/açılıp kapanınca ana sayfa yeniden yüklenir.
 */
@Composable
fun TvSettingsScreen(onCatalogChanged: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var last by rememberSaveable { mutableStateOf(SettingsPage.Addons) }
    BackHandler(enabled = page != null) { page = null }
    when (page) {
        null -> SettingsHome(focusOn = last, onOpen = { last = it; page = it })
        SettingsPage.Sync -> SettingsPageFrame(stringResource(R.string.tv_settings_sync_title)) {
            item(key = "sync") { WifiSync((LocalContext.current.applicationContext as PlayApp).container.localSync, onCatalogChanged) }
        }
        SettingsPage.Addons -> TvAddonsSettings(onCatalogChanged)
        SettingsPage.Player -> TvPlayerSettings()
        SettingsPage.Profile -> TvProfileSettings()
        SettingsPage.General -> TvGeneralSettings()
    }
}

internal enum class SettingsPage(@StringRes val title: Int, @StringRes val summary: Int) {
    Sync(R.string.tv_settings_sync_title, R.string.tv_settings_sync_summary),
    Addons(R.string.tv_settings_addons_title, R.string.tv_settings_addons_summary),
    Player(R.string.tv_settings_player_title, R.string.tv_settings_player_summary),
    Profile(R.string.tv_settings_profile_title, R.string.tv_settings_profile_summary),
    General(R.string.tv_settings_general_title, R.string.tv_settings_general_summary),
}

@Composable
private fun SettingsHome(focusOn: SettingsPage, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    val requesters = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") { ScreenTitle(stringResource(R.string.tv_section_settings)) }
        items(SettingsPage.entries, key = { it.name }) { p ->
            ListItem(
                selected = false,
                onClick = { onOpen(p) },
                headlineContent = { Text(stringResource(p.title)) },
                supportingContent = { Text(stringResource(p.summary), color = TextDim) },
                trailingContent = { Text("›", color = TextDim, fontSize = TvText.Row) },
                modifier = Modifier.focusRequester(requesters.getValue(p)),
            )
        }
        item(key = "about") {
            Column {
                Spacer(Modifier.height(10.dp))
                SectionTitle(stringResource(R.string.tv_settings_about))
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text("${stringResource(R.string.app_name)} ${version.orEmpty()}", fontSize = TvText.Body) },
                )
            }
        }
    }
    // Sayfadan dönünce odak açılan bölüme.
    LaunchedEffect(Unit) { runCatching { requesters.getValue(focusOn).requestFocus() } }
}

/** Bir ayar sayfasının çerçevesi: başlık + kaydırılan içerik (kumandayla aşağı inilir). */
@Composable
internal fun SettingsPageFrame(title: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "page-title") { ScreenTitle(title) }
        content()
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
}

/**
 * Telefondan aktarma: telefonda Play → Ayarlar → "Wi-Fi senkronizasyonu" kartı açılır, ekrandaki 6 haneli kod burada
 * girilir. Play'in yerel eşitlemesi profil, izleme geçmişi ve eklenti depolarını iki yönlü eşitler; TV'de olmayan
 * depolar onayla kurulur.
 */
@Composable
private fun WifiSync(sync: LocalSyncController, onCatalogChanged: () -> Unit) {
    val state by sync.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var installedOnce by remember { mutableStateOf(false) }

    var hosting by remember { mutableStateOf(false) }
    LaunchedEffect(state.installing) { if (!state.installing && installedOnce) onCatalogChanged() }
    // Ekrandan çıkınca TV'nin eşitleme sunucusu kapanır (Play'deki kartla aynı: açık kaldığı sürece kod geçerli).
    DisposableEffect(Unit) { onDispose { sync.dismissPending(); sync.close() } }

    Column {
        SectionTitle(stringResource(R.string.tv_settings_sync_title))
        Text(stringResource(R.string.tv_sync_body), color = TextDim, fontSize = TvText.Body)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Button(onClick = { if (hosting) { sync.close(); hosting = false } else { sync.open(); hosting = true } }) {
                Text(stringResource(if (hosting) R.string.tv_sync_hide_code else R.string.tv_sync_show_code))
            }
            val code = state.code
            if (hosting && code != null) {
                Text(stringResource(R.string.tv_sync_code, code), color = Accent, fontSize = TvText.Hero, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tv_sync_code_valid), color = TextDim, fontSize = TvText.Body)
            } else if (hosting && state.expired) {
                Text(stringResource(R.string.tv_sync_code_expired), color = TextDim, fontSize = TvText.Body)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                placeholder = stringResource(R.string.tv_sync_code_placeholder), keyboardType = KeyboardType.Number,
                onDone = { if (code.length == 6) sync.connect(code) },
                modifier = Modifier.width(240.dp).focusRequester(field).onFocusChanged { focused = it.isFocused },
            )
            Button(onClick = { if (code.length == 6) sync.connect(code) }, enabled = !state.busy) { Text(stringResource(R.string.tv_sync_connect)) }
        }
        InitialFocus(field) { focused }
        Spacer(Modifier.height(8.dp))
        Text(statusText(state), color = if (state.phase == LocalSyncController.Phase.Failed) Accent else TextDim, fontSize = TvText.Body)
        // Kalıcı eşleşme (varsayılan kapalı; telefonda da açılmalı): öneri ve gelen istek kullanıcı onayıyla.
        val container = (LocalContext.current.applicationContext as PlayApp).container
        val pairing by container.settings.wifiPairing.collectAsStateWithLifecycle(initialValue = false)
        val requests by sync.pairRequests.collectAsStateWithLifecycle()
        val pairScope = androidx.compose.runtime.rememberCoroutineScope()
        Spacer(Modifier.height(12.dp))
        ListItem(
            selected = false,
            onClick = {
                val on = !pairing
                pairScope.launch {
                    container.settings.setWifiPairing(on)
                    if (!on) sync.forgetAllPairs()
                }
            },
            headlineContent = { Text(stringResource(com.kaan.play.R.string.wifi_sync_pairing_setting)) },
            supportingContent = { Text(stringResource(com.kaan.play.R.string.wifi_sync_pairing_setting_hint), color = TextDim) },
            trailingContent = { Text(stringResource(if (pairing) R.string.tv_on else R.string.tv_off), color = if (pairing) Accent else TextDim) },
        )
        val offer = state.pairOffer
        if (pairing && offer != null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_offer, offer), color = TextMain, fontSize = TvText.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = sync::acceptPairOffer) { Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_accept)) }
                Button(onClick = sync::declinePairOffer) { Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_decline)) }
            }
        }
        if (pairing) requests.forEach { r ->
            Spacer(Modifier.height(8.dp))
            Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_request, r.peerName), color = TextMain, fontSize = TvText.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = { sync.confirmPairRequest(r.pairId) }) { Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_allow)) }
                Button(onClick = { sync.unpair(r.pairId) }) { Text(stringResource(com.kaan.play.R.string.wifi_sync_pair_reject)) }
            }
        }
        val paired by sync.pairedDevices.collectAsStateWithLifecycle()
        if (paired.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tv_sync_paired_title), color = TextMain, fontSize = TvText.Body)
            paired.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("• " + p.peerName, color = TextMain, fontSize = TvText.Body)
                    Button(onClick = { sync.unpair(p.pairId) }) { Text(stringResource(R.string.tv_sync_unpair)) }
                }
            }
        }
        if (state.pending.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tv_sync_pending_title), color = TextMain, fontSize = TvText.Body)
            state.pending.forEach { Text("• " + it.name.ifBlank { it.sourceUrl }, color = TextMain, fontSize = TvText.Body) }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { installedOnce = true; sync.confirm(state.pending) }, enabled = !state.installing) {
                Text(if (state.installing) stringResource(R.string.tv_sync_installing_button) else stringResource(R.string.tv_sync_install_all, state.pending.size))
            }
        }
    }
}

@Composable
private fun statusText(s: LocalSyncController.State): String = when {
    s.installing -> stringResource(R.string.tv_sync_status_installing)
    s.installFailed > 0 -> pluralStringResource(R.plurals.tv_sync_status_install_failed, s.installFailed, s.installFailed)
    else -> when (s.phase) {
        LocalSyncController.Phase.Idle -> ""
        LocalSyncController.Phase.Searching -> stringResource(R.string.tv_sync_status_searching)
        LocalSyncController.Phase.Connecting -> stringResource(R.string.tv_sync_status_connecting)
        LocalSyncController.Phase.Syncing -> stringResource(R.string.tv_sync_status_syncing)
        LocalSyncController.Phase.Done -> s.result?.let { stringResource(R.string.tv_sync_status_done_counts, it.repos, it.addons, it.history) }
            ?: stringResource(R.string.tv_sync_status_done)
        LocalSyncController.Phase.Failed -> when (s.error) {
            LocalSyncController.Error.InvalidCode, LocalSyncController.Error.WrongCode -> stringResource(R.string.tv_sync_error_code)
            LocalSyncController.Error.NotFound, LocalSyncController.Error.Unreachable -> stringResource(R.string.tv_sync_error_not_found)
            LocalSyncController.Error.Locked -> stringResource(R.string.tv_sync_error_locked)
            else -> stringResource(R.string.tv_sync_error_other)
        }
    }
}

