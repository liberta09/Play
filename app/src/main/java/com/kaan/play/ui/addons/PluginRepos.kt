package com.kaan.play.ui.addons

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing

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

/** "Kurulu tüm depolar": depo adı + sürüm + eklenti sayısı; güncelleme denetimi ve kaldırma düğmeleri. */
@Composable
fun RepositoriesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { RepositoriesViewModel(it.plugins) }
    val repos by vm.repos.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<PluginRepo?>(null) }
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

        val list = repos
        when {
            list == null -> Unit // yükleniyor
            list.isEmpty() -> EmptyMessage(stringResource(R.string.addons_repos_empty))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(list, key = { it.id }) { repo ->
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
    }

    plan?.let { (repo, found) ->
        AlertDialog(
            onDismissRequest = vm::dismissPlan,
            title = { Text(stringResource(R.string.repos_update_title)) },
            text = { Text(pluralStringResource(R.plurals.repos_update_message, found.count, found.count, repo.name)) },
            confirmButton = { TextButton(onClick = vm::applyPlan) { Text(stringResource(R.string.repos_update)) } },
            dismissButton = { TextButton(onClick = vm::dismissPlan) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    toRemove?.let { repo ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.repos_remove_title)) },
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
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onCheck, enabled = enabled) {
                Icon(
                    PlayIcons.Refresh,
                    contentDescription = stringResource(R.string.repos_check),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                PlayIcons.Trash,
                contentDescription = stringResource(R.string.repos_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Yüklü eklentiler": addon'lar (aç/kapat/kaldır) ve eklenti depolarındaki eklentiler (depo adı altında, yalnızca aç/kapat).
 * Depo eklentileri **varsayılan kapalıdır**; kullanıcı bilinçle açar. Depoyu silmek "Kurulu tüm depolar"dandır.
 */
enum class InstalledAddonsSection {
    ALL,
    NUVIO_ADDONS,
    NUVIO_PLUGINS,
}

@Composable
fun InstalledAddonsScreen(
    onBack: () -> Unit,
    onOpenPlugin: (Long) -> Unit,
    section: InstalledAddonsSection = InstalledAddonsSection.ALL,
) {
    val vm = containerViewModel { InstalledAddonsViewModel(it.addons, it.plugins, it.csRepos) }
    val addons by vm.addons.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val csGroups by vm.csGroups.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<Addon?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        val title = when (section) {
            InstalledAddonsSection.ALL -> R.string.addons_installed
            InstalledAddonsSection.NUVIO_ADDONS -> R.string.addons_nuvio_title
            InstalledAddonsSection.NUVIO_PLUGINS -> R.string.addons_nuvio_plugins_title
        }
        ScreenHeader(title = stringResource(title), onBack = onBack)

        val addonList = addons
        val groupList = groups
        val csGroupList = csGroups
        when {
            addonList == null || groupList == null || csGroupList == null -> Unit // yükleniyor: boş mesaj yanıp sönmesin
            else -> {
                val visibleAddons = if (section != InstalledAddonsSection.NUVIO_PLUGINS) addonList else emptyList()
                val visibleGroups = if (section != InstalledAddonsSection.NUVIO_ADDONS) groupList else emptyList()
                val visibleCsGroups = if (section == InstalledAddonsSection.ALL) csGroupList else emptyList()
                if (visibleAddons.isEmpty() && visibleGroups.isEmpty() && visibleCsGroups.isEmpty()) {
                    val emptyText = when (section) {
                        InstalledAddonsSection.ALL -> R.string.addons_empty
                        InstalledAddonsSection.NUVIO_ADDONS -> R.string.addons_nuvio_empty
                        InstalledAddonsSection.NUVIO_PLUGINS -> R.string.addons_nuvio_plugins_empty
                    }
                    EmptyMessage(stringResource(emptyText))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (visibleGroups.isNotEmpty()) item(key = "note") { Text(
                            text = stringResource(R.string.plugins_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) }
                        items(visibleAddons, key = { "a-${it.id}" }) { addon ->
                            AddonRow(addon = addon, onToggle = { vm.setEnabled(addon, it) }, onRemove = { toRemove = addon })
                        }
                        visibleGroups.forEach { group ->
                            item(key = "g-${group.repo.id}") {
                                Text(
                                    text = group.repo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                                )
                            }
                            items(group.plugins, key = { "p-${it.id}" }) { plugin ->
                                PluginRow(plugin = plugin, onToggle = { vm.setPluginEnabled(plugin, it) }, onOpen = { onOpenPlugin(plugin.id) })
                            }
                        }
                        // CloudStream .cs3 eklentileri yalnızca genel "Yüklü eklentiler" ekranında gösterilir.
                        visibleCsGroups.forEach { group ->
                            item(key = "cs-${group.repo.id}") {
                                Text(
                                    text = group.repo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                                )
                            }
                            items(group.plugins, key = { "csp-${it.id}" }) { plugin ->
                                CsPluginRow(plugin = plugin, onToggle = { vm.setCsPluginEnabled(plugin, it) })
                            }
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
private fun PluginRow(plugin: InstalledPlugin, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val toggleLabel = stringResource(R.string.addons_enabled_toggle, plugin.name)
    val movie = stringResource(R.string.type_movie)
    val series = stringResource(R.string.type_series)
    val types = plugin.supportedTypes.joinToString(", ") { if (it == "movie") movie else series }
    val detail = plugin.contentLanguage.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.uppercase() } ?: types

    // Ayarı olan eklentiye dokununca küçük ayrıntı sayfası açılır (aç/kapat + eklentinin kendi ayar formu).
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .then(if (plugin.hasSettings) Modifier.clickable(onClick = onOpen) else Modifier),
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
            Icon(PlayIcons.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = plugin.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = toggleLabel },
        )
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
