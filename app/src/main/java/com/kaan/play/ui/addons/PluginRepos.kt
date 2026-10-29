package com.kaan.play.ui.addons

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.plugin.InstalledPlugin
import com.kaan.play.data.plugin.PluginRepo
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.launch

/** "Kurulu tüm depolar"da bölüm başlığı (Nuvio / CloudStream). Odak almaz; uzun metinde alt satıra geçer. */
@Composable
private fun RepoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Üçüncü taraf bilgi notu: eklentileri Play yazmaz ve içerik sağlamaz. */
@Composable
private fun ThirdPartyNote() {
    Text(
        text = stringResource(R.string.plugins_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * "Kurulu tüm depolar" (kullanıcı kararı 2026-10-21): üç ekranda görünen her şey tek listede, iki bölümde.
 * - **Nuvio:** "Nuvio Depoları"ndaki addon'lar ([AddonRow]: aç/kapat + sil) ve "Nuvio Pluginler"deki depolar
 *   ([RepoRow]: sürüm + eklenti sayısı, güncelleme denetimi + sil).
 * - **CloudStream:** "CloudStream Depoları"ndaki depolar ([CsRepoRow]).
 * Satırlar ve veri kaynakları o ekranlarınkiyle aynıdır; boş bölüm gösterilmez.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RepositoriesScreen(onBack: () -> Unit, onOpenAddon: (Long) -> Unit = {}) {
    val container = rememberContainer()
    val vm = containerViewModel { RepositoriesViewModel(it.plugins) }
    val csVm = containerViewModel { CsReposViewModel(it.csRepos, it.csRepoCatalog) }
    val repos by vm.repos.collectAsStateWithLifecycle()
    val addons by container.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    val csRepos by csVm.repos.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<PluginRepo?>(null) }
    var addonToRemove by remember { mutableStateOf<Addon?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Bildirimler tek seferliktir (Toast): "güncel", "güncellendi", hata.
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val text = when (event) {
                RepoEvent.UpToDate -> context.getString(R.string.repos_up_to_date)
                is RepoEvent.Updated -> context.getString(R.string.repos_updated)
                is RepoEvent.Failed -> errorText(context, event.error)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_repos), onBack = onBack)
        ThirdPartyNote()

        val pluginRepos = repos
        val addonList = addons
        val csList = csRepos
        when {
            pluginRepos == null || addonList == null || csList == null -> Unit // yükleniyor
            pluginRepos.isEmpty() && addonList.isEmpty() && csList.isEmpty() ->
                EmptyMessage(stringResource(R.string.addons_repos_empty))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                // CloudStream satırı kendi yatay boşluğunu taşır; Nuvio satırlarına aynı boşluk aşağıda verilir.
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (addonList.isNotEmpty() || pluginRepos.isNotEmpty()) {
                    item(key = "section-nuvio") { RepoSectionTitle(stringResource(R.string.repos_section_nuvio)) }
                    items(addonList, key = { "na-${it.id}" }) { addon ->
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            AddonRow(
                                addon = addon,
                                onToggle = { scope.launch { container.addons.setEnabled(addon.id, it) } },
                                onRemove = { addonToRemove = addon },
                                onClick = { onOpenAddon(addon.id) },
                            )
                        }
                    }
                    items(pluginRepos, key = { "pr-${it.id}" }) { repo ->
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            RepoRow(
                                repo = repo,
                                busy = busy == repo.id,
                                enabled = busy == null,
                                onCheck = { vm.check(repo) },
                                onRemove = { toRemove = repo },
                            )
                        }
                    }
                }
                if (csList.isNotEmpty()) {
                    item(key = "section-cs") { RepoSectionTitle(stringResource(R.string.repos_section_cloudstream)) }
                    items(csList, key = { "cs-${it.id}" }) { repo ->
                        CsRepoRow(repo = repo, onRemove = { csVm.remove(repo) })
                    }
                }
            }
        }
    }

    addonToRemove?.let { addon ->
        AddonRemoveDialog(
            addon = addon,
            onConfirm = {
                scope.launch { container.addons.remove(addon.id) }
                addonToRemove = null
            },
            onDismiss = { addonToRemove = null },
        )
    }

    plan?.let { (repo, found) ->
        AlertDialog(
            onDismissRequest = vm::dismissPlan,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.repos_update_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(pluralStringResource(R.plurals.repos_update_message, found.count, found.count, repo.name)) },
            confirmButton = { TextButton(onClick = vm::applyPlan) { Text(stringResource(R.string.repos_update)) } },
            dismissButton = { TextButton(onClick = vm::dismissPlan) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    toRemove?.let { repo ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.repos_remove_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.repos_remove_message, repo.name)) },
            confirmButton = {
                TextButton(onClick = { vm.remove(repo); toRemove = null }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun RepoRow(repo: PluginRepo, busy: Boolean, enabled: Boolean, onCheck: () -> Unit, onRemove: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusRing(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .focusable()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(text = repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(
                        R.string.addons_row_info,
                        repo.version ?: "–",
                        pluralStringResource(R.plurals.plugins_count, repo.pluginCount, repo.pluginCount),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(
                    onClick = onCheck,
                    enabled = enabled,
                    modifier = Modifier.focusRing(CircleShape),
                ) {
                    Icon(
                        PlayIcons.Refresh,
                        contentDescription = stringResource(R.string.repos_check),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.focusRing(CircleShape),
            ) {
                Icon(
                    PlayIcons.Trash,
                    contentDescription = stringResource(R.string.repos_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Yüklü eklentiler": addon'lar (aç/kapat/kaldır) ve eklenti depolarındaki eklentiler (depo adı altında, yalnızca aç/kapat).
 * Depo eklentileri **varsayılan kapalıdır**; kullanıcı bilinçle açar. Depoyu silmek "Kurulu tüm depolar"dandır.
 */
@Composable
fun InstalledAddonsScreen(onBack: () -> Unit, onOpenPlugin: (Long) -> Unit, onOpenAddon: (Long) -> Unit = {}) {
    val vm = containerViewModel { InstalledAddonsViewModel(it.addons, it.plugins, it.csRepos) }
    val addons by vm.addons.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val csGroups by vm.csGroups.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<Addon?>(null) }

    val addonList = addons
    val groupList = groups
    val csGroupList = csGroups

    val totalCount = (addonList?.size ?: 0) +
        (groupList?.sumOf { it.plugins.size } ?: 0) +
        (csGroupList?.sumOf { it.plugins.size } ?: 0) +
        (csGroupList?.size ?: 0)
    val allEnabled = totalCount > 0 &&
        (addonList?.all { it.enabled } != false) &&
        (groupList?.all { g -> g.plugins.all { it.enabled } } != false) &&
        (csGroupList?.all { g -> g.plugins.all { it.enabled } } != false)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.addons_installed),
            onBack = onBack,
            action = if (totalCount > 0) {
                {
                    TextButton(onClick = { vm.setAllEnabled(!allEnabled) }) {
                        Text(
                            text = stringResource(if (allEnabled) R.string.addons_disable_all else R.string.addons_enable_all),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else null,
        )

        when {
            addonList == null || groupList == null || csGroupList == null -> Unit // yükleniyor: boş mesaj yanıp sönmesin
            addonList.isEmpty() && groupList.isEmpty() && csGroupList.isEmpty() -> EmptyMessage(stringResource(R.string.addons_empty))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (groupList.isNotEmpty()) item(key = "note") { Text(
                    text = stringResource(R.string.plugins_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) }
                items(addonList, key = { "a-${it.id}" }) { addon ->
                    AddonRow(addon = addon, onToggle = { vm.setEnabled(addon, it) }, onRemove = { toRemove = addon }, onClick = { onOpenAddon(addon.id) })
                }
                groupList.forEach { group ->
                    item(key = "g-${group.repo.id}") {
                        val groupAllEnabled = group.plugins.isNotEmpty() && group.plugins.all { it.enabled }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.repo.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (group.plugins.size > 1) {
                                TextButton(onClick = { vm.setRepoPluginsEnabled(group.repo.id, !groupAllEnabled) }) {
                                    Text(
                                        text = stringResource(if (groupAllEnabled) R.string.addons_disable_all else R.string.addons_enable_all),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                    items(group.plugins, key = { "p-${it.id}" }) { plugin ->
                        PluginRow(plugin = plugin, onToggle = { vm.setPluginEnabled(plugin, it) }, onOpen = { onOpenPlugin(plugin.id) })
                    }
                }
                // CloudStream .cs3 eklentileri
                csGroupList.forEach { group ->
                    item(key = "cs-${group.repo.id}") {
                        val groupAllEnabled = group.plugins.isNotEmpty() && group.plugins.all { it.enabled }
                        val toggleLabel = stringResource(R.string.addons_enabled_toggle, group.repo.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.repo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.cs_plugins_count, group.plugins.size, group.plugins.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = groupAllEnabled,
                                enabled = group.plugins.isNotEmpty(),
                                onCheckedChange = { vm.setCsRepoPluginsEnabled(group.repo.id, it) },
                                modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = toggleLabel },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    if (group.plugins.isNotEmpty()) {
                        items(group.plugins, key = { "csp-${it.id}" }) { plugin ->
                            CsPluginRow(plugin = plugin, onToggle = { vm.setCsPluginEnabled(plugin, it) })
                        }
                    }
                }
            }
        }
    }

    toRemove?.let { addon ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.addons_remove_title)) },
            text = { Text(stringResource(R.string.addons_remove_message, addon.manifest.name)) },
            confirmButton = {
                TextButton(onClick = { vm.remove(addon); toRemove = null }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
internal fun PluginRow(plugin: InstalledPlugin, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val toggleLabel = stringResource(R.string.addons_enabled_toggle, plugin.name)
    val movie = stringResource(R.string.type_movie)
    val series = stringResource(R.string.type_series)
    val types = plugin.supportedTypes.joinToString(", ") { if (it == "movie") movie else series }
    val detail = plugin.contentLanguage.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.uppercase() } ?: types

    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (plugin.hasSettings) {
                            Modifier
                                .focusRing(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpen)
                        } else {
                            Modifier
                                .focusRing(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .focusable()
                        }
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).alpha(if (plugin.enabled) 1f else 0.6f)) {
                    Text(text = plugin.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = stringResource(R.string.addons_row_info, plugin.version ?: "–", detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (plugin.hasSettings) {
                    Icon(
                        PlayIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = plugin.enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = toggleLabel },
            )
        }
    }
}

/** Hata türünden yerelleştirilmiş metin (Toast için: kompozisyon dışı). */
private fun errorText(context: android.content.Context, e: com.kaan.play.data.addon.AddonException): String = when (e.kind) {
    com.kaan.play.data.addon.AddonException.Kind.Network -> context.getString(R.string.addon_error_network)
    com.kaan.play.data.addon.AddonException.Kind.Http -> context.getString(R.string.addon_error_http, e.detail.orEmpty())
    com.kaan.play.data.addon.AddonException.Kind.TooLarge -> context.getString(R.string.addon_error_too_large)
    com.kaan.play.data.addon.AddonException.Kind.NoPlugins -> context.getString(R.string.addon_error_no_plugins)
    com.kaan.play.data.addon.AddonException.Kind.PluginFileFailed ->
        context.getString(R.string.addon_error_plugin_file, e.detail.orEmpty())
    else -> context.getString(R.string.addon_error_not_json)
}
