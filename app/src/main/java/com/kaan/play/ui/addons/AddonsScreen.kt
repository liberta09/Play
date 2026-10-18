package com.kaan.play.ui.addons

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import com.kaan.play.ui.tv.focusRing
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.web.WebSite
import com.kaan.play.ui.web.WebActivity
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.SettingsRow
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.rememberCursorField
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Ayarlar > Eklentiler: yalnızca üç satır (ekle, depolar, yüklü eklentiler). Kaynak zaman aşımı
 * ikincil bir ayar olarak altta küçük durur.
 */
@Composable
fun AddonsScreen(
    startAdding: Boolean,
    onBack: () -> Unit,
    onOpenRepos: () -> Unit,
    onOpenInstalled: () -> Unit,
    onOpenCsRepos: () -> Unit,
    onAdded: (AddResult) -> Unit,
) {
    val vm = containerViewModel { AddonsViewModel(it.sourceInstaller, it.addons, it.pendingAddonUrl) }
    val addons by vm.addons.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val timeout by container.settings.sourceTimeoutSeconds
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_TIMEOUT_SECONDS)
    var showTimeout by remember { mutableStateOf(false) }
    val torrentServer by container.settings.torrentServer.collectAsStateWithLifecycle(initialValue = null)
    var showTorrentServer by remember { mutableStateOf(false) }
    var showSourceChooser by remember { mutableStateOf(false) }
    val focus = rememberFocusMemory()

    // Sade bildirim: "Eklenti eklendi (addon)" ya da "Eklenti deposu eklendi" (atlanan dosya varsa sayısıyla).
    val appContext = LocalContext.current
    LaunchedEffect(vm) {
        vm.added.collect { result ->
            val text = when (result) {
                is AddResult.Addon -> appContext.getString(R.string.add_done_addon)
                is AddResult.PluginRepo ->
                    if (result.skipped > 0) appContext.getString(R.string.add_done_repo_skipped, result.skipped)
                    else appContext.getString(R.string.add_done_repo)
                is AddResult.CsRepo ->
                    if (result.skipped > 0) appContext.getString(R.string.add_done_cs_repo_skipped, result.skipped)
                    else appContext.getString(R.string.add_done_cs_repo)
            }
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
            onAdded(result)
        }
    }

    // Ana sayfadaki ekleme kısayolu da kullanıcıyı önce kaynak türünü seçmeye götürür.
    LaunchedEffect(startAdding) { if (startAdding) showSourceChooser = true }

    val webVm = containerViewModel { WebSitesViewModel(it.webSites) }
    val sites by webVm.sites.collectAsStateWithLifecycle()
    val webDialog by webVm.dialog.collectAsStateWithLifecycle()
    var siteToRemove by remember { mutableStateOf<WebSite?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_title), onBack = onBack, initialFocus = false)

        // Satırlar ve web siteleri listesi birlikte kayar (büyük yazı boyutunda ya da çok siteyle taşmasın).
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                SettingsRow(
                    title = stringResource(R.string.addons_add),
                    modifier = Modifier.focusMemory(focus, "add", isDefault = true),
                    onClick = { showSourceChooser = true },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.addons_repos),
                    modifier = Modifier.focusMemory(focus, "repos"),
                    onClick = onOpenRepos,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.addons_installed),
                    value = addons?.let { pluralStringResource(R.plurals.addons_count, it.size, it.size) }.orEmpty(),
                    modifier = Modifier.focusMemory(focus, "installed"),
                    onClick = onOpenInstalled,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.cs_repos_title),
                    subtitle = stringResource(R.string.cs_repos_subtitle),
                    modifier = Modifier.focusMemory(focus, "csrepos"),
                    onClick = onOpenCsRepos,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.web_add),
                    subtitle = stringResource(R.string.web_add_subtitle),
                    divider = false,
                    modifier = Modifier.focusMemory(focus, "web"),
                    onClick = { webVm.openAdd() },
                )
            }
            sites?.takeIf { it.isNotEmpty() }?.let { list ->
                item {
                    Text(
                        text = stringResource(R.string.web_sites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { "site-${it.id}" }) { site ->
                    WebSiteRow(
                        site = site,
                        modifier = Modifier.focusMemory(focus, "site-${site.id}"),
                        onOpen = { context.startActivity(WebActivity.intent(context, site.name, site.url)) },
                        onRemove = { siteToRemove = site },
                    )
                }
            }
        }

        // İkincil ayarlar: altta, küçük ve soluk.
        TextButton(
            onClick = { showTimeout = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                stringResource(R.string.addons_timeout, timeout),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { showTorrentServer = true },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(
                    R.string.torrent_server_row,
                    torrentServer?.substringAfter("://") ?: stringResource(R.string.torrent_server_none),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    webDialog?.let { state ->
        AddWebSiteDialog(
            state = state,
            onNameChange = webVm::onNameChange,
            onAddressChange = webVm::onAddressChange,
            onConfirm = webVm::submit,
            onDismiss = webVm::dismissAdd,
        )
    }

    siteToRemove?.let { site ->
        AlertDialog(
            onDismissRequest = { siteToRemove = null },
            title = { Text(stringResource(R.string.web_remove_title)) },
            text = { Text(stringResource(R.string.web_remove_message, site.name)) },
            confirmButton = {
                TextButton(onClick = { webVm.remove(site); siteToRemove = null }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { siteToRemove = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showTorrentServer) {
        TorrentServerDialog(
            current = torrentServer,
            onSave = { input ->
                scope.launch {
                    val saved = container.settings.setTorrentServer(input)
                    if (input.isBlank() || saved != null) showTorrentServer = false
                }
            },
            onDismiss = { showTorrentServer = false },
        )
    }

    if (showTimeout) {
        ChoiceDialog(
            title = stringResource(R.string.addons_timeout_title),
            options = SettingsRepository.TIMEOUT_OPTIONS,
            selected = timeout,
            label = { stringResource(R.string.addons_timeout_option, it) },
            onSelect = { scope.launch { container.settings.setSourceTimeoutSeconds(it) }; showTimeout = false },
            onDismiss = { showTimeout = false },
        )
    }

    if (showSourceChooser) {
        SourceTypeChooserDialog(
            onSelect = { type ->
                showSourceChooser = false
                vm.openAdd(expectedType = type)
            },
            onDismiss = { showSourceChooser = false },
        )
    }

    dialog?.let { state ->
        AddAddonDialog(
            state = state,
            onInputChange = vm::onInputChange,
            onConfirm = vm::submit,
            onDismiss = vm::dismissAdd,
        )
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AddonRow(addon: Addon, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    val name = addon.manifest.name
    val host = remember(addon.sourceUrl) { addon.sourceUrl.toHttpUrlOrNull()?.host ?: addon.sourceUrl }
    val toggleLabel = stringResource(R.string.addons_enabled_toggle, name)

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).alpha(if (addon.enabled) 1f else 0.5f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.addons_row_info, addon.manifest.version ?: "–", host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = addon.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = toggleLabel },
        )
        IconButton(onClick = onRemove) {
            Icon(
                PlayIcons.Trash,
                contentDescription = stringResource(R.string.addons_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TorrentServerDialog(current: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(current?.substringAfter("://").orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    val field = rememberCursorField(input) { input = it; invalid = false }
    val submit = {
        if (input.isBlank() || TorrentLinks.normalizeServer(input) != null) onSave(input) else invalid = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.torrent_server_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.torrent_server_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    label = { Text(stringResource(R.string.torrent_server_field)) },
                    placeholder = { Text(stringResource(R.string.torrent_server_hint)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.torrent_server_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(field),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit) {
                Text(stringResource(R.string.torrent_server_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SourceTypeChooserDialog(
    onSelect: (SourceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addons_add_choose_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSelect(SourceType.CLOUDSTREAM) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.addons_add_cloudstream))
                }
                TextButton(onClick = { onSelect(SourceType.NUVIO_ADDON) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.addons_add_nuvio))
                }
                TextButton(onClick = { onSelect(SourceType.NUVIO_PLUGIN) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.addons_add_nuvio_plugin))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun AddAddonDialog(
    state: AddDialogState,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val field = rememberCursorField(state.input, onInputChange)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when (state.expectedType) {
                SourceType.CLOUDSTREAM -> R.string.addons_add_cloudstream
                SourceType.NUVIO_ADDON -> R.string.addons_add_nuvio
                SourceType.NUVIO_PLUGIN -> R.string.addons_add_nuvio_plugin
                null -> R.string.addons_add_title
            }
            Text(stringResource(title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    label = { Text(stringResource(R.string.addons_add_field)) },
                    placeholder = { Text(stringResource(R.string.addons_add_hint)) },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.busy && state.input.isNotBlank()) onConfirm() }),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(field),
                )
                when {
                    state.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.addons_add_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    state.error != null -> Text(
                        text = state.error.userMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.busy && state.input.isNotBlank()) {
                Text(stringResource(R.string.addons_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
