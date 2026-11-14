package com.kaan.play.tv

import com.kaan.play.R
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.kaan.play.AppContainer
import com.kaan.play.PlayApp
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.CatalogVisibility
import com.kaan.play.data.cloudstream.CsPluginSettingsResult
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.CatalogInstallOutcome
import com.kaan.play.data.plugin.RepoManagerEntry
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.data.plugin.UpdatePlan
import com.kaan.play.data.plugin.runtime.PluginSettingField
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.TorrentLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Ayarlar → Eklentiler (TV'ye özgü; Play'in telefondaki Eklentiler sayfalarının karşılığı, Play ekranı gömülmez).
 * Bölüm listesi: ekleme kutusu, alt sayfalar (kurulu depolar, yüklü eklentiler, Nuvio eklentileri, depo kataloğu,
 * torrent sunucusu) ve kaynak zaman aşımı. Yalnız Play'in veri katmanı kullanılır; listeler veritabanından gelir,
 * eklenti kodu yüklenmez (2 GB'lık kutu). Tek istisna kullanıcının bastığı "Ayarlar": o eklentiyi yükler.
 */
@Composable
internal fun TvAddonsSettings(onCatalogChanged: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PlayApp).container }
    val scope = rememberCoroutineScope()
    val ctx = remember(container, scope, context, onCatalogChanged) { AddonsCtx(container, scope, context, onCatalogChanged) }
    val add = remember { AddState() }
    var page by remember { mutableStateOf<AddonsPage?>(null) }
    var last by remember { mutableStateOf("add") }
    var lastAddon by remember { mutableStateOf<Long?>(null) }
    // Geri: alt sayfadan bir üst sayfaya; bölüm listesindeyken Ayarlar'ın kendi geri işleyicisine düşer.
    BackHandler(enabled = page != null) { page = page?.parent }

    // Katalogdan kurulum uygulama kapsamında sürer; sonucu bu sayfa açıkken bildirilir ve ana sayfa yenilenir.
    val outcome by container.catalogInstallCoordinator.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        when (val o = outcome) {
            is CatalogInstallOutcome.Success -> { ctx.toast(addedText(ctx.context, o.result)); onCatalogChanged() }
            is CatalogInstallOutcome.Failure -> ctx.toast(ctx.context.getString(R.string.tv_addons_catalog_install_failed, o.message))
            null -> return@LaunchedEffect
        }
        container.catalogInstallCoordinator.clearOutcome()
    }

    when (val p = page) {
        null -> AddonsHome(ctx, add, focusKey = last, onOpen = { key, target -> last = key; page = target })
        AddonsPage.Repos -> ReposPage(
            ctx,
            onOpenPlugins = { page = AddonsPage.CsPlugins(it) },
            onOpenCatalogs = { page = AddonsPage.AddonCatalogs(it) },
        )
        is AddonsPage.CsPlugins -> CsPluginsPage(ctx, p.repoId)
        AddonsPage.NuvioPlugins -> NuvioPluginsPage(ctx, onOpenSettings = { page = AddonsPage.NuvioPluginSettings(it) })
        is AddonsPage.NuvioPluginSettings -> NuvioPluginSettingsPage(ctx, p.pluginId)
        AddonsPage.Catalog -> CatalogPage(ctx)
        AddonsPage.Torrent -> TorrentPage(ctx, onDone = { page = null })
        is AddonsPage.AddonCatalogs -> AddonCatalogsPage(ctx, p.addonId)
        AddonsPage.HomeCatalogs -> HomeCatalogsPage(ctx, focusAddon = lastAddon, onOpen = { lastAddon = it; page = AddonsPage.AddonCatalogs(it, fromHome = true) })
    }
}

/** Alt sayfalar. [parent]: Geri'de dönülecek sayfa (null = bölüm listesi). */
private sealed interface AddonsPage {
    val parent: AddonsPage? get() = null

    data object Repos : AddonsPage
    /** [repoId] doluysa depo satırından gelindi: liste o depoya süzülü açılır, Geri depolara döner. */
    data class CsPlugins(val repoId: Long?) : AddonsPage {
        override val parent: AddonsPage? get() = if (repoId != null) Repos else null
    }
    data object NuvioPlugins : AddonsPage
    data class NuvioPluginSettings(val pluginId: Long) : AddonsPage {
        override val parent: AddonsPage get() = NuvioPlugins
    }
    data object Catalog : AddonsPage
    data object Torrent : AddonsPage
    /** [fromHome]: "Ana sayfa katalogları" listesinden gelindi; Geri oraya döner (yoksa kurulu depolara). */
    data class AddonCatalogs(val addonId: Long, val fromHome: Boolean = false) : AddonsPage {
        override val parent: AddonsPage get() = if (fromHome) HomeCatalogs else Repos
    }
    data object HomeCatalogs : AddonsPage
}

/** Alt sayfaların ortak bağlamı. İşler sayfa düzeyindeki kapsamda sürer: alt sayfadan çıkınca kesilmez. */
private class AddonsCtx(
    val container: AppContainer,
    val scope: CoroutineScope,
    val context: Context,
    val onCatalogChanged: () -> Unit,
) {
    /** Kurulu/açık kaynakları değiştiren işlem; bitince ana sayfa yeniden yüklenir. */
    fun change(block: suspend () -> Unit) {
        scope.launch { block(); onCatalogChanged() }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Ekleme kutusunun durumu sayfa düzeyinde tutulur: alt sayfaya geçip dönünce süren kurulum ve sonucu kaybolmasın. */
private class AddState {
    var url by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

// ---------------------------------------------------------------------------------------------------------------------
// Bölüm listesi
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun AddonsHome(ctx: AddonsCtx, add: AddState, focusKey: String, onOpen: (String, AddonsPage) -> Unit) {
    val c = ctx.container
    // Sayılar depo satırlarından: 758 eklenti satırını burada toplamaya gerek yok.
    val csRepos by c.csRepos.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val nuvioRepos by c.plugins.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val addons by c.addons.addons.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeout by c.settings.sourceTimeoutSeconds.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_TIMEOUT_SECONDS)
    val torrent by c.settings.torrentServer.collectAsStateWithLifecycle(initialValue = null)
    SettingsPageFrame(stringResource(R.string.tv_settings_addons_title)) {
        item(key = "add") { AddRepo(ctx, add, rememberInitialFocus(focusKey == "add")) }
        item(key = "manage") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_addons_manage)) } }
        item(key = "repos") {
            NavRow(
                stringResource(R.string.tv_addons_installed_repos),
                stringResource(R.string.tv_addons_repos_summary, csRepos.size, addons.size, nuvioRepos.size),
                focusKey == "repos",
            ) { onOpen("repos", AddonsPage.Repos) }
        }
        item(key = "cs") {
            NavRow(
                stringResource(R.string.tv_addons_installed_plugins),
                csRepos.sumOf { it.pluginCount }.let { pluralStringResource(R.plurals.tv_addons_cs_summary, it, it) },
                focusKey == "cs",
            ) { onOpen("cs", AddonsPage.CsPlugins(null)) }
        }
        item(key = "nuvio") {
            NavRow(
                stringResource(R.string.tv_addons_nuvio_plugins),
                nuvioRepos.sumOf { it.pluginCount }.let { pluralStringResource(R.plurals.tv_addons_nuvio_summary, it, it) },
                focusKey == "nuvio",
            ) { onOpen("nuvio", AddonsPage.NuvioPlugins) }
        }
        item(key = "catalog") {
            NavRow(stringResource(R.string.tv_addons_catalog), stringResource(R.string.tv_addons_catalog_summary), focusKey == "catalog") {
                onOpen("catalog", AddonsPage.Catalog)
            }
        }
        item(key = "home-catalogs") {
            NavRow(stringResource(R.string.tv_addons_home_catalogs), stringResource(R.string.tv_addons_home_catalogs_summary), focusKey == "home-catalogs") {
                onOpen("home-catalogs", AddonsPage.HomeCatalogs)
            }
        }
        item(key = "options") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_addons_options)) } }
        item(key = "timeout") {
            // Seçenek az (10/15/30/60 sn): her basış bir sonrakine geçer, pencere gerekmez.
            ListItem(
                selected = false,
                onClick = {
                    val options = SettingsRepository.TIMEOUT_OPTIONS
                    val next = options[(options.indexOf(timeout) + 1) % options.size]
                    ctx.scope.launch { c.settings.setSourceTimeoutSeconds(next) }
                },
                headlineContent = { Text(stringResource(R.string.tv_addons_timeout)) },
                supportingContent = { Text(stringResource(R.string.tv_addons_timeout_summary), color = TextDim) },
                trailingContent = { Text(stringResource(R.string.tv_addons_seconds, timeout), color = Accent, fontSize = TvText.Row) },
            )
        }
        item(key = "torrent") {
            NavRow(
                stringResource(R.string.tv_addons_torrent),
                torrent?.substringAfter("://")?.let { stringResource(R.string.tv_addons_torrent_server, it) }
                    ?: stringResource(R.string.tv_addons_torrent_builtin),
                focusKey == "torrent",
            ) { onOpen("torrent", AddonsPage.Torrent) }
        }
    }
}

@Composable
private fun NavRow(title: String, summary: String, focus: Boolean, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { Text(summary, color = TextDim) },
        trailingContent = { Text("›", color = TextDim, fontSize = TvText.Row) },
        modifier = rememberInitialFocus(focus),
    )
}

@Composable
private fun AddRepo(ctx: AddonsCtx, s: AddState, fieldModifier: Modifier) {
    fun submit() {
        val url = s.url.trim()
        if (url.isEmpty() || s.busy) return
        s.busy = true
        s.message = ctx.context.getString(R.string.tv_addons_adding_long)
        ctx.scope.launch {
            try {
                // Tür verilmez: Play adresin içeriğinden CloudStream / Nuvio addon / Nuvio eklenti deposu olduğunu anlar.
                s.message = addedText(ctx.context, ctx.container.sourceInstaller.add(url))
                s.url = ""
                ctx.onCatalogChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AddonException) {
                s.message = errorText(ctx.context, e)
            } catch (e: Exception) {
                s.message = ctx.context.getString(R.string.tv_addons_add_failed)
            } finally {
                s.busy = false
            }
        }
    }
    Column {
        Spacer(Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.tv_addons_add_title))
        Text(
            stringResource(R.string.tv_addons_add_body),
            color = TextDim, fontSize = TvText.Meta,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvTextField(
                value = s.url, onValueChange = { s.url = it }, placeholder = "https://…/repo.json",
                keyboardType = KeyboardType.Uri, onDone = { submit() },
                modifier = Modifier.width(560.dp).then(fieldModifier),
            )
            Button(onClick = { submit() }) { Text(stringResource(if (s.busy) R.string.tv_addons_adding else R.string.tv_addons_add)) }
        }
        s.message?.let { Spacer(Modifier.height(6.dp)); Text(it, color = TextDim, fontSize = TvText.Body) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Kurulu depolar: CloudStream depoları, Nuvio addon'ları, Nuvio eklenti depoları (Play'in "Kurulu tüm depolar"ı)
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun ReposPage(ctx: AddonsCtx, onOpenPlugins: (Long) -> Unit, onOpenCatalogs: (Long) -> Unit) {
    val c = ctx.container
    val csRepos by c.csRepos.repos.collectAsStateWithLifecycle(initialValue = null)
    val addons by c.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    val pluginRepos by c.plugins.repos.collectAsStateWithLifecycle(initialValue = null)
    var checking by remember { mutableStateOf<Long?>(null) }
    var plan by remember { mutableStateOf<UpdatePlan?>(null) }
    val cs = csRepos.orEmpty()
    val na = addons.orEmpty()
    val pr = pluginRepos.orEmpty()
    val loaded = csRepos != null && addons != null && pluginRepos != null
    val firstKey = cs.firstOrNull()?.let { "cs-${it.id}" } ?: na.firstOrNull()?.let { "na-${it.id}" } ?: pr.firstOrNull()?.let { "pr-${it.id}" }

    // Nuvio eklenti deposu güncellemesi (Play'deki gibi): ilk basış denetler, değişiklik varsa ikinci basış uygular.
    fun checkOrApply(repoId: Long) {
        if (checking != null) return
        val pending = plan?.takeIf { it.repoId == repoId }
        checking = repoId
        ctx.scope.launch {
            try {
                if (pending != null) {
                    c.plugins.applyUpdate(pending)
                    plan = null
                    ctx.toast(ctx.context.getString(R.string.tv_addons_updated))
                    ctx.onCatalogChanged()
                } else {
                    val found = c.plugins.checkForUpdates(repoId)
                    if (found.isEmpty) ctx.toast(ctx.context.getString(R.string.tv_addons_up_to_date)) else plan = found
                }
            } catch (e: AddonException) {
                plan = null
                ctx.toast(errorText(ctx.context, e))
            } finally {
                checking = null
            }
        }
    }

    SettingsPageFrame(stringResource(R.string.tv_addons_installed_repos)) {
        item(key = "note") { Text(stringResource(R.string.tv_addons_third_party_note), color = TextDim, fontSize = TvText.Body) }
        if (loaded && cs.isEmpty() && na.isEmpty() && pr.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.tv_addons_repos_empty), color = TextDim, fontSize = TvText.Body)
        }
        if (cs.isNotEmpty()) {
            item(key = "cs-title") { SectionTitle(stringResource(R.string.tv_addons_cs_repos)) }
            items(cs, key = { "cs-${it.id}" }) { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ListItem(
                        selected = false,
                        onClick = { onOpenPlugins(r.id) },
                        headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(pluralStringResource(R.plurals.tv_addons_plugin_count, r.pluginCount, r.pluginCount), r.description?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = { Text("›", color = TextDim, fontSize = TvText.Row) },
                        modifier = Modifier.weight(1f).then(rememberInitialFocus(firstKey == "cs-${r.id}")),
                    )
                    RemoveButton { ctx.change { c.csRepos.remove(r.id) } }
                }
            }
        }
        if (na.isNotEmpty()) {
            item(key = "na-title") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_addons_nuvio_addons)) } }
            items(na, key = { "na-${it.id}" }) { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ListItem(
                        selected = false,
                        onClick = { ctx.change { c.addons.setEnabled(a.id, !a.enabled) } },
                        headlineContent = { Text(a.manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            val kinds = listOfNotNull(
                                stringResource(R.string.tv_addons_kind_catalog).takeIf { a.manifest.providesCatalogs },
                                stringResource(R.string.tv_addons_kind_streams).takeIf { a.manifest.providesStreams },
                                stringResource(R.string.tv_addons_kind_subtitles).takeIf { a.manifest.providesSubtitles },
                                a.sourceUrl.toHttpUrlOrNull()?.host,
                            )
                            Text(kinds.joinToString(" · "), color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { OnOff(a.enabled) },
                        modifier = Modifier.weight(1f).then(rememberInitialFocus(firstKey == "na-${a.id}")),
                    )
                    if (a.manifest.providesCatalogs) Button(onClick = { onOpenCatalogs(a.id) }) { Text(stringResource(R.string.tv_addons_catalogs)) }
                    RemoveButton { ctx.change { c.addons.remove(a.id) } }
                }
            }
        }
        if (pr.isNotEmpty()) {
            item(key = "pr-title") { Column { Spacer(Modifier.height(10.dp)); SectionTitle(stringResource(R.string.tv_addons_nuvio_plugin_repos)) } }
            items(pr, key = { "pr-${it.id}" }) { r ->
                val pending = plan?.takeIf { it.repoId == r.id }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ListItem(
                        selected = false,
                        onClick = { checkOrApply(r.id) },
                        headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOfNotNull(r.version?.let { "v$it" }, pluralStringResource(R.plurals.tv_addons_plugin_count, r.pluginCount, r.pluginCount)).joinToString(" · "), color = TextDim)
                        },
                        trailingContent = {
                            Text(
                                when {
                                    checking == r.id -> stringResource(R.string.tv_addons_wait)
                                    pending != null -> pluralStringResource(R.plurals.tv_addons_pending_changes, pending.count, pending.count)
                                    else -> stringResource(R.string.tv_addons_check_updates)
                                },
                                color = if (pending != null) Accent else TextDim, fontSize = TvText.Body,
                            )
                        },
                        modifier = Modifier.weight(1f).then(rememberInitialFocus(firstKey == "pr-${r.id}")),
                    )
                    RemoveButton { ctx.change { c.plugins.remove(r.id) } }
                }
            }
        }
    }
}

/** Addon'un katalogları (Play'deki addon ayrıntısının katalog aç/kapat kısmı): kapalı katalog ana sayfada gösterilmez. */
@Composable
private fun AddonCatalogsPage(ctx: AddonsCtx, addonId: Long) {
    val c = ctx.container
    val addons by c.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    val disabled by c.settings.disabledCatalogs.collectAsStateWithLifecycle(initialValue = emptySet())
    val addon = addons?.firstOrNull { it.id == addonId }
    SettingsPageFrame(addon?.manifest?.name?.let { stringResource(R.string.tv_addons_catalogs_of, it) } ?: stringResource(R.string.tv_addons_catalogs)) {
        if (addon == null) return@SettingsPageFrame
        val catalogs = addon.manifest.catalogs
        item(key = "note") { Text(stringResource(R.string.tv_addons_catalogs_note), color = TextDim, fontSize = TvText.Body) }
        // TV'ye özgü kolaylık: İzlemeli.com gibi ~100 katalog veren addon'da her satıra tek tek basmak gerekmesin. Play'in aynı
        // ayarını (katalog başına anahtar) yazar; hepsi açıksa hepsini kapatır, değilse hepsini açar.
        val allOn = catalogs.all { CatalogVisibility.isVisible(addon.id, it.type, it.id, disabled) }
        if (catalogs.size > 1) item(key = "all") {
            ListItem(
                selected = false,
                onClick = {
                    ctx.change {
                        for (cat in catalogs) {
                            if (CatalogVisibility.isVisible(addon.id, cat.type, cat.id, disabled) == allOn) c.settings.setCatalogEnabled(addon.id, cat.type, cat.id, !allOn)
                        }
                        c.catalogRepository.cache.clear()
                    }
                },
                headlineContent = { Text(stringResource(if (allOn) R.string.tv_addons_catalogs_all_off else R.string.tv_addons_catalogs_all_on)) },
                supportingContent = {
                    val on = catalogs.count { CatalogVisibility.isVisible(addon.id, it.type, it.id, disabled) }
                    Text(stringResource(R.string.tv_addons_catalogs_on_count, on, catalogs.size), color = TextDim)
                },
                modifier = rememberInitialFocus(true),
            )
        }
        items(catalogs, key = { "${it.type}:${it.id}" }) { cat ->
            val on = CatalogVisibility.isVisible(addon.id, cat.type, cat.id, disabled)
            ListItem(
                selected = false,
                onClick = {
                    ctx.change {
                        c.settings.setCatalogEnabled(addon.id, cat.type, cat.id, !on)
                        // Play'deki gibi: önbellekteki eski satırlar yeniden yüklemede gösterilmesin.
                        c.catalogRepository.cache.clear()
                    }
                },
                headlineContent = { Text(cat.name ?: cat.id) },
                supportingContent = { Text(typeLabel(cat.type), color = TextDim) },
                trailingContent = { OnOff(on) },
                modifier = rememberInitialFocus(catalogs.size == 1),
            )
        }
    }
}

/**
 * Ayarlar > Eklentiler > Ana sayfa katalogları: katalog veren addon'lar ve açık katalog sayısı;
 * basınca addon'un katalog aç/kapa sayfası (Play'deki addon ayrıntısının katalog anahtarları). Sıra Play ana sayfasındaki gibi
 * kalır; kapalı kataloglar ana sayfada, Filmler'de ve Diziler'de gösterilmez.
 */
@Composable
private fun HomeCatalogsPage(ctx: AddonsCtx, focusAddon: Long?, onOpen: (Long) -> Unit) {
    val c = ctx.container
    val addons by c.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    val disabled by c.settings.disabledCatalogs.collectAsStateWithLifecycle(initialValue = emptySet())
    val shown = addons.orEmpty().filter { it.enabled && it.manifest.catalogs.isNotEmpty() }
    val focusId = focusAddon?.takeIf { id -> shown.any { it.id == id } } ?: shown.firstOrNull()?.id
    SettingsPageFrame(stringResource(R.string.tv_addons_home_catalogs)) {
        item(key = "note") { Text(stringResource(R.string.tv_addons_home_catalogs_note), color = TextDim, fontSize = TvText.Body) }
        if (addons != null && shown.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.tv_addons_home_catalogs_empty), color = TextDim, fontSize = TvText.Body)
        }
        items(shown, key = { it.id }) { a ->
            val cats = a.manifest.catalogs
            val on = cats.count { CatalogVisibility.isVisible(a.id, it.type, it.id, disabled) }
            NavRow(a.manifest.name, stringResource(R.string.tv_addons_catalogs_on_count, on, cats.size), a.id == focusId) { onOpen(a.id) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Yüklü eklentiler (CloudStream): ~758 satır; tembel liste, ad ve depo süzgeci
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun CsPluginsPage(ctx: AddonsCtx, initialRepo: Long?) {
    val c = ctx.container
    val repos by c.csRepos.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val plugins by c.csRepos.plugins.collectAsStateWithLifecycle(initialValue = null)
    var query by remember { mutableStateOf("") }
    var repoId by remember { mutableStateOf(initialRepo) }
    var settingsBusy by remember { mutableStateOf<Long?>(null) }
    val repoNames = remember(repos) { repos.associate { it.id to it.name } }
    val all = plugins.orEmpty()
    val shown = remember(all, query, repoId) {
        val q = query.trim()
        all.filter { (repoId == null || it.repoId == repoId) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
    }

    SettingsPageFrame(stringResource(R.string.tv_addons_installed_plugins)) {
        item(key = "filters") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvTextField(
                    value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.tv_addons_search_plugins),
                    onDone = {}, modifier = Modifier.width(360.dp).then(rememberInitialFocus()),
                )
                // Depo süzgeci: her basış bir sonraki depoya geçer (Tümü → depo 1 → … → Tümü).
                Button(onClick = {
                    val ids = listOf<Long?>(null) + repos.map { it.id }
                    repoId = ids[(ids.indexOf(repoId) + 1) % ids.size]
                }) { Text(stringResource(R.string.tv_addons_repo_filter, repoId?.let { repoNames[it] } ?: stringResource(R.string.tv_addons_all)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val selected = repoId
                if (selected != null) {
                    val allOn = all.filter { it.repoId == selected }.let { l -> l.isNotEmpty() && l.all { it.enabled } }
                    Button(onClick = { ctx.change { c.csRepos.setRepoEnabled(selected, !allOn) } }) {
                        Text(stringResource(if (allOn) R.string.tv_addons_repo_all_off else R.string.tv_addons_repo_all_on))
                    }
                }
            }
        }
        item(key = "count") {
            Text(
                if (plugins == null) stringResource(R.string.tv_common_loading)
                else pluralStringResource(
                    R.plurals.tv_addons_cs_count, shown.size, shown.size, shown.count { it.enabled }, stringResource(R.string.tv_addons_third_party_note),
                ),
                color = TextDim, fontSize = TvText.Body,
            )
        }
        items(shown, key = { it.id }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ListItem(
                    selected = false,
                    onClick = { ctx.change { c.csRepos.setEnabled(p.id, !p.enabled) } },
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val parts = listOfNotNull(
                            p.language.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
                            p.version?.let { "v$it" },
                            repoNames[p.repoId].takeIf { repoId == null },
                        )
                        Text(parts.joinToString(" · "), color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = { OnOff(p.enabled) },
                    modifier = Modifier.weight(1f),
                )
                // Eklentinin kendi ayar penceresi (ör. DramaCix "Kaynak sitesi": sitenin koruma izni bayatlayınca site bir kez
                // açılıp kapatılır, eklenti yeniden dener). Yalnız basınca o tek eklenti yüklenir.
                if (p.enabled) Button(onClick = {
                    if (settingsBusy == null) {
                        settingsBusy = p.id
                        ctx.scope.launch {
                            try {
                                val msg = when (c.csRepos.openSettings(p.id, ctx.context)) {
                                    CsPluginSettingsResult.Opened -> null
                                    CsPluginSettingsResult.NotSupported -> R.string.tv_addons_no_settings
                                    CsPluginSettingsResult.Disabled -> R.string.tv_addons_settings_enable_first
                                    CsPluginSettingsResult.Unavailable -> R.string.tv_addons_settings_changed
                                    is CsPluginSettingsResult.Failed -> R.string.tv_addons_settings_failed
                                }
                                if (msg != null) ctx.toast(ctx.context.getString(msg))
                            } finally {
                                settingsBusy = null
                            }
                        }
                    }
                }) { Text(stringResource(if (settingsBusy == p.id) R.string.tv_addons_opening else R.string.tv_addons_settings)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Nuvio eklentileri (JS kazıyıcılar; Play'in "Nuvio Pluginler"i)
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun NuvioPluginsPage(ctx: AddonsCtx, onOpenSettings: (Long) -> Unit) {
    val c = ctx.container
    val repos by c.plugins.repos.collectAsStateWithLifecycle(initialValue = null)
    val all by c.plugins.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    // plugins akışı CloudStream satırlarını da taşır; yalnız Nuvio depolarının kazıyıcıları gruplanır.
    val groups = remember(repos, all) {
        repos.orEmpty().mapNotNull { r -> all.filter { it.repoId == r.id }.takeIf { it.isNotEmpty() }?.let { r to it } }
    }
    val allOn = groups.isNotEmpty() && groups.all { (_, list) -> list.all { it.enabled } }

    SettingsPageFrame(stringResource(R.string.tv_addons_nuvio_plugins)) {
        item(key = "note") { Text(stringResource(R.string.tv_addons_third_party_note), color = TextDim, fontSize = TvText.Body) }
        if (repos != null && groups.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.tv_addons_nuvio_empty), color = TextDim, fontSize = TvText.Body)
        }
        if (groups.isNotEmpty()) item(key = "all") {
            Button(onClick = { ctx.change { c.plugins.setAllEnabled(!allOn) } }, modifier = rememberInitialFocus()) {
                Text(stringResource(if (allOn) R.string.tv_addons_all_off else R.string.tv_addons_all_on))
            }
        }
        groups.forEach { (repo, list) ->
            item(key = "g-${repo.id}") {
                val repoOn = list.all { it.enabled }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(repo.name)
                    if (list.size > 1) Button(onClick = { ctx.change { c.plugins.setRepoEnabled(repo.id, !repoOn) } }) {
                        Text(stringResource(if (repoOn) R.string.tv_addons_repo_all_off else R.string.tv_addons_repo_all_on))
                    }
                }
            }
            items(list, key = { "np-${it.id}" }) { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ListItem(
                        selected = false,
                        onClick = { ctx.change { c.plugins.setPluginEnabled(p.id, !p.enabled) } },
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            val movie = stringResource(R.string.tv_addons_type_movie)
                            val series = stringResource(R.string.tv_addons_type_series)
                            val detail = p.contentLanguage.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.uppercase(Locale.ROOT) }
                                ?: p.supportedTypes.joinToString(", ") { if (it == "movie") movie else series }
                            Text(listOfNotNull(p.version?.let { "v$it" }, detail.ifBlank { null }).joinToString(" · "), color = TextDim)
                        },
                        trailingContent = { OnOff(p.enabled) },
                        modifier = Modifier.weight(1f),
                    )
                    // Depo kaydı eklentinin ayar bildirip bildirmediğini söyler; düğme yalnız onlarda (Play'deki gibi).
                    if (p.hasSettings) Button(onClick = { onOpenSettings(p.id) }) { Text(stringResource(R.string.tv_addons_settings)) }
                }
            }
        }
    }
}

/**
 * Nuvio eklentisinin kendi ayarları (Play'in eklenti ayrıntı sayfasının formu): düzen eklentinin `onSettings()`
 * kodundan gelir, bu yüzden yalnız bu sayfa açılınca o tek eklenti yüklenir. Değerler Play'in deposuna anında yazılır;
 * eklenti çalışırken bunları alır.
 */
@Composable
private fun NuvioPluginSettingsPage(ctx: AddonsCtx, pluginId: Long) {
    val c = ctx.container
    val plugins by c.plugins.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    val values by remember(pluginId) { c.pluginSettings.observe(pluginId) }.collectAsStateWithLifecycle(initialValue = null)
    var fields by remember { mutableStateOf<List<PluginSettingField>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        fields = null
        // Play'deki gibi: kod bozuk/eksikse ya da onSettings() hata verirse null döner.
        val layout = c.plugins.loadCode(pluginId)?.let { c.pluginRuntime.settingsLayout(it, "settings-$pluginId") }
        if (layout == null) failed = true else fields = layout
    }
    // null: anahtar silinir, eklentinin varsayılanına döner.
    fun save(key: String, value: JsonElement?) {
        ctx.scope.launch { c.pluginSettings.set(pluginId, key, value) }
    }

    val name = plugins.firstOrNull { it.id == pluginId }?.name
    SettingsPageFrame(name?.let { stringResource(R.string.tv_addons_settings_of, it) } ?: stringResource(R.string.tv_addons_plugin_settings)) {
        val list = fields
        val current = values
        when {
            failed -> item(key = "failed") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.tv_addons_settings_load_failed), color = TextDim, fontSize = TvText.Body)
                    Button(onClick = { attempt++ }, modifier = rememberInitialFocus()) { Text(stringResource(R.string.tv_addons_retry)) }
                }
            }
            // Değerler de beklenir: metin kutusu kayıtlı değerle başlasın, sonradan boşa sıfırlanmasın.
            list == null || current == null -> item(key = "loading") { Text(stringResource(R.string.tv_common_loading), color = TextDim, fontSize = TvText.Body) }
            list.isEmpty() -> item(key = "none") { Text(stringResource(R.string.tv_addons_no_settings), color = TextDim, fontSize = TvText.Body) }
            else -> {
                val firstInput = list.indexOfFirst { it !is PluginSettingField.Header && it !is PluginSettingField.Info }
                items(list.size, key = { "f-$it" }) { i ->
                    PluginFieldRow(list[i], current, rememberInitialFocus(i == firstInput), ::save)
                }
            }
        }
    }
}

@Composable
private fun PluginFieldRow(
    field: PluginSettingField,
    values: Map<String, JsonElement>,
    focusModifier: Modifier,
    onChange: (String, JsonElement?) -> Unit,
) {
    when (field) {
        is PluginSettingField.Header -> Column { Spacer(Modifier.height(10.dp)); SectionTitle(field.label) }
        is PluginSettingField.Info -> Text(field.label, color = TextDim, fontSize = TvText.Body)
        is PluginSettingField.Toggle -> {
            val on = (values[field.key] as? JsonPrimitive)?.booleanOrNull ?: field.default
            val description = field.description
            ListItem(
                selected = false,
                onClick = { onChange(field.key, JsonPrimitive(!on)) },
                headlineContent = { Text(field.label) },
                supportingContent = if (description == null) null else { { Text(description, color = TextDim) } },
                trailingContent = { OnOff(on) },
                modifier = focusModifier,
            )
        }
        is PluginSettingField.Select -> {
            // Kaynak zaman aşımı satırı gibi: her basış bir sonraki seçeneğe geçer, pencere gerekmez.
            val stored = (values[field.key] as? JsonPrimitive)?.contentOrNull ?: field.default
            val index = field.options.indexOfFirst { it.value == stored }.coerceAtLeast(0)
            ListItem(
                selected = false,
                onClick = { onChange(field.key, JsonPrimitive(field.options[(index + 1) % field.options.size].value)) },
                headlineContent = { Text(field.label) },
                supportingContent = { Text(listOfNotNull(field.description, stringResource(R.string.tv_addons_press_to_change)).joinToString(" "), color = TextDim) },
                trailingContent = { Text(field.options[index].label, color = Accent, fontSize = TvText.Row) },
                modifier = focusModifier,
            )
        }
        is PluginSettingField.Text -> {
            // Kutu kayıtlı değerle bir kez başlar; sonra her harf Play'deki gibi anında kaydedilir (boş = varsayılan).
            var text by remember(field.key) { mutableStateOf((values[field.key] as? JsonPrimitive)?.contentOrNull.orEmpty()) }
            Column {
                field.label?.let { Text(it, color = TextMain, fontSize = TvText.Row) }
                Spacer(Modifier.height(6.dp))
                TvTextField(
                    value = text,
                    onValueChange = { text = it; onChange(field.key, it.takeIf(String::isNotEmpty)?.let(::JsonPrimitive)) },
                    placeholder = field.placeholder,
                    keyboardType = if (field.isPassword) KeyboardType.Password else KeyboardType.Text,
                    onDone = {},
                    modifier = Modifier.width(560.dp).then(focusModifier),
                )
                field.description?.let { Spacer(Modifier.height(4.dp)); Text(it, color = TextDim, fontSize = TvText.Body) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Depo kataloğu (Play'in topluluk kataloğu: CloudStreamRepoManager listesi)
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun CatalogPage(ctx: AddonsCtx) {
    val c = ctx.container
    val addons by c.addons.addons.collectAsStateWithLifecycle(initialValue = emptyList())
    val nuvioRepos by c.plugins.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val csRepos by c.csRepos.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val installing by c.catalogInstallCoordinator.installingUrl.collectAsStateWithLifecycle()
    val installed = remember(addons, nuvioRepos, csRepos) {
        (addons.map { it.sourceUrl } + nuvioRepos.map { it.sourceUrl } + csRepos.map { it.sourceUrl }).map(::canonicalUrl).toSet()
    }
    var entries by remember { mutableStateOf<List<RepoManagerEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(attempt) {
        failed = false
        entries = null
        try {
            entries = c.repoManagerCatalog.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    val shown = remember(entries, query) {
        val q = query.trim()
        entries.orEmpty().filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true)
        }
    }

    SettingsPageFrame(stringResource(R.string.tv_addons_catalog)) {
        item(key = "intro") {
            Text(stringResource(R.string.tv_addons_catalog_intro, stringResource(R.string.tv_addons_third_party_note)), color = TextDim, fontSize = TvText.Body)
        }
        item(key = "search") {
            TvTextField(
                value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.tv_addons_search_repos),
                onDone = {}, modifier = Modifier.width(560.dp).then(rememberInitialFocus()),
            )
        }
        when {
            failed -> item(key = "failed") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.tv_addons_catalog_failed), color = TextDim, fontSize = TvText.Body)
                    Button(onClick = { attempt++ }) { Text(stringResource(R.string.tv_addons_retry)) }
                }
            }
            entries == null -> item(key = "loading") { Text(stringResource(R.string.tv_common_loading), color = TextDim, fontSize = TvText.Body) }
            shown.isEmpty() -> item(key = "none") {
                Text(stringResource(if (query.isBlank()) R.string.tv_addons_catalog_empty else R.string.tv_addons_no_results), color = TextDim, fontSize = TvText.Body)
            }
            else -> items(shown, key = { it.url + "|" + it.sourceType.name }) { e ->
                val isInstalled = canonicalUrl(e.url) in installed
                val isInstalling = installing == e.url
                ListItem(
                    selected = false,
                    onClick = {
                        // Kurulum Play'in uygulama kapsamında sürer (sayfadan çıkılsa da biter); aynı anda tek kurulum.
                        if (!isInstalled && !isInstalling && !c.catalogInstallCoordinator.install(e.url, e.sourceType)) {
                            ctx.toast(ctx.context.getString(R.string.tv_addons_install_busy))
                        }
                    },
                    headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val parts = listOfNotNull(sourceTypeLabel(e.sourceType), e.category.takeIf { it.isNotBlank() && it != "Diğer" }, e.url)
                        Text(parts.joinToString(" · "), color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Text(
                            stringResource(
                                when {
                                    isInstalled -> R.string.tv_addons_installed
                                    isInstalling -> R.string.tv_addons_installing
                                    else -> R.string.tv_addons_install
                                },
                            ),
                            color = if (isInstalled) Accent else TextDim, fontSize = TvText.Body,
                        )
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Torrent sunucusu
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun TorrentPage(ctx: AddonsCtx, onDone: () -> Unit) {
    val c = ctx.container
    val current by c.settings.torrentServer.collectAsStateWithLifecycle(initialValue = null)
    var input by remember(current) { mutableStateOf(current?.substringAfter("://").orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun save() {
        // Geçersiz adres kaydedilmez: Play'in kaydı geçersiz girdide ayarı siler, önce burada denetlenir.
        if (input.isNotBlank() && TorrentLinks.normalizeServer(input) == null) {
            message = ctx.context.getString(R.string.tv_addons_torrent_invalid)
            return
        }
        ctx.scope.launch { c.settings.setTorrentServer(input); onDone() }
    }

    SettingsPageFrame(stringResource(R.string.tv_addons_torrent)) {
        item(key = "info") {
            Text(
                stringResource(R.string.tv_addons_torrent_info),
                color = TextDim, fontSize = TvText.Body,
            )
        }
        item(key = "field") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvTextField(
                    value = input, onValueChange = { input = it; message = null }, placeholder = "host:port",
                    keyboardType = KeyboardType.Uri, onDone = { save() },
                    modifier = Modifier.width(420.dp).then(rememberInitialFocus()),
                )
                Button(onClick = { save() }) { Text(stringResource(R.string.tv_save)) }
                if (current != null) Button(onClick = { ctx.scope.launch { c.settings.setTorrentServer(null); onDone() } }) {
                    Text(stringResource(R.string.tv_addons_torrent_use_builtin))
                }
            }
        }
        message?.let { m -> item(key = "message") { Text(m, color = Accent, fontSize = TvText.Body) } }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Ortak parçalar
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun OnOff(on: Boolean) {
    Text(stringResource(if (on) R.string.tv_on else R.string.tv_off), color = if (on) Accent else TextDim)
}

/** İki basışlı kaldırma: ilk basış onay ister (odak aynı düğmede kalır), odak ayrılınca onay düşer. */
@Composable
private fun RemoveButton(onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    Button(
        onClick = { if (armed) { armed = false; onConfirm() } else { armed = true } },
        modifier = Modifier.onFocusChanged { if (!it.isFocused) armed = false },
    ) { Text(stringResource(if (armed) R.string.tv_addons_remove_confirm else R.string.tv_addons_remove)) }
}

/**
 * Alt sayfa açılınca odak bu öğeye: önceki sayfadaki odaklı öğe kaybolunca odak boşa düşmesin. Bölüm listesine
 * dönüşte açılan bölümün satırına verilir. [enabled] false ise etkisizdir.
 */
@Composable
private fun rememberInitialFocus(enabled: Boolean = true): Modifier {
    if (!enabled) return Modifier
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    InitialFocus(requester) { focused }
    return Modifier.focusRequester(requester).onFocusChanged { focused = it.hasFocus }
}

// [context]: ekranın bağlamı (AppCompat seçili dili ekrana uygular; uygulama bağlamı API 33 altında cihaz dilinde kalır).
private fun addedText(context: Context, r: AddResult): String {
    val res = context.resources
    fun skipped(n: Int) = if (n > 0) res.getQuantityString(R.plurals.tv_addons_added_skipped, n, n) else ""
    return when (r) {
        is AddResult.CsRepo -> res.getQuantityString(R.plurals.tv_addons_added_cs, r.installed, r.name, r.installed, skipped(r.skipped))
        is AddResult.PluginRepo -> res.getQuantityString(R.plurals.tv_addons_added_nuvio, r.installed, r.name, r.installed, skipped(r.skipped))
        is AddResult.Addon -> context.getString(R.string.tv_addons_added_addon, r.name)
    }
}

/** Sunucudan gelen metin gösterilmez; hata türüne göre sade ileti (Play'deki iletilerin kısası). */
private fun errorText(context: Context, e: AddonException): String = when (e.kind) {
    AddonException.Kind.AlreadyAdded -> context.getString(R.string.tv_addons_err_already)
    AddonException.Kind.InvalidUrl -> context.getString(R.string.tv_addons_err_invalid)
    AddonException.Kind.Network -> context.getString(R.string.tv_addons_err_network)
    AddonException.Kind.Http -> context.getString(R.string.tv_addons_err_http, e.detail.orEmpty())
    AddonException.Kind.TooLarge -> context.getString(R.string.tv_addons_err_too_large)
    AddonException.Kind.NoPlugins -> context.getString(R.string.tv_addons_err_no_plugins)
    AddonException.Kind.PluginFileFailed -> context.getString(R.string.tv_addons_err_plugin_file, e.detail?.substringBefore("::").orEmpty())
    AddonException.Kind.NoStreamResource, AddonException.Kind.UnsupportedTypes -> context.getString(R.string.tv_addons_err_no_streams)
    AddonException.Kind.UnsupportedKind, AddonException.Kind.KindMismatch -> context.getString(R.string.tv_addons_err_unsupported)
    else -> context.getString(R.string.tv_addons_err_other)
}

@Composable
private fun typeLabel(type: String): String = when (type.lowercase(Locale.ROOT)) {
    "movie" -> stringResource(R.string.tv_addons_type_movie)
    "series" -> stringResource(R.string.tv_addons_type_series)
    "tv", "channel" -> stringResource(R.string.tv_section_live)
    else -> type.replaceFirstChar { it.uppercase() }
}

@Composable
private fun sourceTypeLabel(t: SourceType): String = when (t) {
    SourceType.CLOUDSTREAM -> stringResource(R.string.tv_addons_src_cs)
    SourceType.NUVIO_REPO -> stringResource(R.string.tv_addons_src_nuvio_repo)
    SourceType.NUVIO_PLUGIN -> stringResource(R.string.tv_addons_src_nuvio_plugin)
    // Play 1.3.1: harici katalog türü (TV'de ayrı ekleme akışı yok; yalnız etiket).
    SourceType.EXTERNAL_CATALOG -> stringResource(com.kaan.play.R.string.repo_catalog_type_external_catalog)
}

/** Katalogdaki adres ile kurulu adresi karşılaştırmak için (Play'in katalog ekranıyla aynı kural). */
private fun canonicalUrl(url: String): String = url.trim().removeSuffix("/").lowercase(Locale.ROOT)
