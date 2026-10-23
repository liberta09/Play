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
import com.kaan.play.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.theme.PlayColors
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
    onOpenNuvioRepos: () -> Unit,
    onOpenNuvioPlugins: () -> Unit,
) {
    val vm = containerViewModel { AddonsViewModel(it.sourceInstaller, it.addons, it.pendingAddonUrl) }
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val container = rememberContainer()
    val installedCount by container.installedSourcesCount.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val timeout by container.settings.sourceTimeoutSeconds
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_TIMEOUT_SECONDS)
    var showTimeout by remember { mutableStateOf(false) }
    val torrentServer by container.settings.torrentServer.collectAsStateWithLifecycle(initialValue = null)
    var showTorrentServer by remember { mutableStateOf(false) }
    val focus = rememberFocusMemory()

    // Başarılı ekleme bildirimi ve otomatik doğru ekrana yönlendirme
    val appContext = LocalContext.current
    LaunchedEffect(vm) {
        vm.added.collect { result ->
            when (result) {
                is AddResult.CsRepo -> {
                    val text = if (result.skipped > 0) {
                        appContext.getString(R.string.add_done_cs_repo_skipped, result.skipped)
                    } else {
                        appContext.getString(R.string.deeplink_repo_added)
                    }
                    Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
                    onOpenCsRepos()
                }
                is AddResult.Addon -> {
                    Toast.makeText(appContext, appContext.getString(R.string.deeplink_repo_added), Toast.LENGTH_SHORT).show()
                    onOpenNuvioRepos()
                }
                is AddResult.PluginRepo -> {
                    val text = if (result.skipped > 0) {
                        appContext.getString(R.string.add_done_repo_skipped, result.skipped)
                    } else {
                        appContext.getString(R.string.deeplink_plugin_added)
                    }
                    Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
                    onOpenNuvioPlugins()
                }
            }
        }
    }

    // Ana sayfadaki "eklenti ekle" düğmesinden geldiyse pencere hemen açılır.
    LaunchedEffect(startAdding) { if (startAdding) vm.startAddingOnce() }

    val webVm = containerViewModel { WebSitesViewModel(it.webSites) }
    val sites by webVm.sites.collectAsStateWithLifecycle()
    val webDialog by webVm.dialog.collectAsStateWithLifecycle()
    var siteToRemove by remember { mutableStateOf<WebSite?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_title), onBack = onBack)

        // Satırlar ve web siteleri listesi birlikte kayar (büyük yazı boyutunda ya da çok siteyle taşmasın).
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.addons_add),
                    icon = PlayIcons.AddCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "add", isDefault = true),
                    onClick = { vm.openAdd() },
                )
            }
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.addons_repos),
                    icon = PlayIcons.Database,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "repos"),
                    onClick = onOpenRepos,
                )
            }
            item {
                val badgeText = installedCount?.let { pluralStringResource(R.plurals.addons_count, it, it) }
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.addons_installed),
                    badge = badgeText,
                    icon = PlayIcons.Extension,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "installed"),
                    onClick = onOpenInstalled,
                )
            }
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.cs_repos_title),
                    subtitle = stringResource(R.string.cs_repos_subtitle),
                    icon = PlayIcons.Cloud,
                    iconTint = PlayColors.CloudStreamBlue,
                    modifier = Modifier.focusMemory(focus, "csrepos"),
                    onClick = onOpenCsRepos,
                )
            }
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.nuvio_repos_title),
                    subtitle = stringResource(R.string.nuvio_repos_subtitle),
                    icon = PlayIcons.NuvioLogo,
                    iconTint = PlayColors.NuvioPurple,
                    modifier = Modifier.focusMemory(focus, "nuviorepos"),
                    onClick = onOpenNuvioRepos,
                )
            }
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.nuvio_plugins_title),
                    subtitle = stringResource(R.string.nuvio_plugins_subtitle),
                    icon = PlayIcons.Code,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "nuvioplugins"),
                    onClick = onOpenNuvioPlugins,
                )
            }
            item {
                com.kaan.play.ui.components.PlayCardRow(
                    title = stringResource(R.string.web_add),
                    subtitle = stringResource(R.string.web_add_subtitle),
                    icon = PlayIcons.Globe,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "web"),
                    onClick = { webVm.openAdd() },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.kaan.play.ui.components.PlaySplitCard(
                        title = stringResource(R.string.addons_timeout, timeout),
                        icon = PlayIcons.Clock,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .focusMemory(focus, "timeout"),
                        onClick = { showTimeout = true },
                    )
                    com.kaan.play.ui.components.PlaySplitCard(
                        title = stringResource(
                            R.string.torrent_server_row,
                            torrentServer?.substringAfter("://") ?: stringResource(R.string.torrent_server_none),
                        ),
                        icon = PlayIcons.Server,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .focusMemory(focus, "torrentserver"),
                        onClick = { showTorrentServer = true },
                    )
                }
            }
            sites?.takeIf { it.isNotEmpty() }?.let { list ->
                item {
                    Text(
                        text = stringResource(R.string.web_sites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
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
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.web_remove_title), style = MaterialTheme.typography.titleLarge) },
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

    when (val state = dialog) {
        is AddDialogState.Choice -> {
            AddChoiceDialog(
                onSelect = vm::selectType,
                onDismiss = vm::dismissAdd,
            )
        }
        is AddDialogState.Input -> {
            AddSourceInputDialog(
                state = state,
                onInputChange = vm::onInputChange,
                onConfirm = vm::submit,
                onAcceptSuggestion = vm::acceptSuggestion,
                onDismiss = vm::dismissAdd,
            )
        }
        null -> Unit
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
internal fun AddonRow(
    addon: Addon,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val name = addon.manifest.name
    val host = remember(addon.sourceUrl) { addon.sourceUrl.toHttpUrlOrNull()?.host ?: addon.sourceUrl }
    val toggleLabel = stringResource(R.string.addons_enabled_toggle, name)
    val typeLabel = when {
        !addon.manifest.providesStreams && addon.manifest.providesCatalogs -> stringResource(R.string.addon_type_catalog)
        !addon.manifest.providesStreams && addon.manifest.providesSubtitles -> stringResource(R.string.addon_type_subtitles)
        else -> null
    }
    val detail = if (typeLabel != null) "$typeLabel • $host" else host

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.focusRing(RoundedCornerShape(14.dp)) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).alpha(if (addon.enabled) 1f else 0.5f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.addons_row_info, addon.manifest.version ?: "—", detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Icon(
                    PlayIcons.Forward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Switch(
                checked = addon.enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
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
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.torrent_server_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.torrent_server_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    shape = RoundedCornerShape(14.dp),
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
private fun AddChoiceDialog(
    onSelect: (SourceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.addons_choice_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        PlayIcons.Close,
                        contentDescription = stringResource(R.string.common_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceOptionCard(
                    title = stringResource(R.string.addons_choice_cs_title),
                    subtitle = stringResource(R.string.addons_choice_cs_subtitle),
                    icon = PlayIcons.Cloud,
                    accentColor = PlayColors.CloudStreamBlue,
                    onClick = { onSelect(SourceType.CLOUDSTREAM) },
                )
                ChoiceOptionCard(
                    title = stringResource(R.string.addons_choice_nuvio_repo_title),
                    subtitle = stringResource(R.string.addons_choice_nuvio_repo_subtitle),
                    icon = PlayIcons.Nuvio,
                    accentColor = PlayColors.NuvioPurple,
                    onClick = { onSelect(SourceType.NUVIO_REPO) },
                )
                ChoiceOptionCard(
                    title = stringResource(R.string.addons_choice_nuvio_plugin_title),
                    subtitle = stringResource(R.string.addons_choice_nuvio_plugin_subtitle),
                    icon = PlayIcons.Extension,
                    accentColor = PlayColors.NuvioGreen,
                    onClick = { onSelect(SourceType.NUVIO_PLUGIN) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ChoiceOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = PlayIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AddSourceInputDialog(
    state: AddDialogState.Input,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onDismiss: () -> Unit,
) {
    val field = rememberCursorField(state.input, onInputChange)
    val clipboardManager = LocalClipboardManager.current
    val title = when (state.type) {
        SourceType.CLOUDSTREAM -> stringResource(R.string.addons_choice_cs_title)
        SourceType.NUVIO_REPO -> stringResource(R.string.addons_choice_nuvio_repo_title)
        SourceType.NUVIO_PLUGIN -> stringResource(R.string.addons_choice_nuvio_plugin_title)
        else -> stringResource(R.string.addons_choice_cs_title)
    }
    val hint = when (state.type) {
        SourceType.CLOUDSTREAM -> stringResource(R.string.addons_add_cs_hint)
        SourceType.NUVIO_REPO -> stringResource(R.string.addons_add_nuvio_repo_hint)
        SourceType.NUVIO_PLUGIN -> stringResource(R.string.addons_add_nuvio_plugin_hint)
        else -> stringResource(R.string.addons_add_cs_hint)
    }
    val accentColor = when (state.type) {
        SourceType.CLOUDSTREAM -> PlayColors.CloudStreamBlue
        SourceType.NUVIO_REPO -> PlayColors.NuvioPurple
        SourceType.NUVIO_PLUGIN -> PlayColors.NuvioGreen
        else -> PlayColors.CloudStreamBlue
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.addons_add_field)) },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text?.trim()
                                if (!clip.isNullOrBlank()) onInputChange(clip)
                            },
                            enabled = !state.busy,
                        ) {
                            Icon(
                                PlayIcons.ContentPaste,
                                contentDescription = stringResource(R.string.paste_from_clipboard),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
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
                    state.error != null -> {
                        if (state.suggestedType != null) {
                            val suggestedTypeName = when (state.suggestedType) {
                                SourceType.CLOUDSTREAM -> stringResource(R.string.source_type_cloudstream)
                                SourceType.NUVIO_REPO -> stringResource(R.string.source_type_nuvio_repo)
                                SourceType.NUVIO_PLUGIN -> stringResource(R.string.source_type_nuvio_plugin)
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.addon_suggest_type, suggestedTypeName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Button(
                                    onClick = onAcceptSuggestion,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.addon_suggest_add_action, suggestedTypeName))
                                }
                            }
                        } else {
                            Text(
                                text = state.error.userMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.busy && state.input.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.addons_add_confirm),
                    color = if (!state.busy && state.input.isNotBlank()) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

