package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.plugin.RepoManagerEntry
import com.kaan.play.data.plugin.CatalogInstallOutcome
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RepoManagerCatalogScreen(onBack: () -> Unit, container: AppContainer) {
    val addons by container.addons.addons.collectAsStateWithLifecycle(initialValue = emptyList())
    val nuvioRepos by container.plugins.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val cloudStreamRepos by container.csRepos.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val installedUrls = remember(addons, nuvioRepos, cloudStreamRepos) {
        (addons.map { it.sourceUrl } + nuvioRepos.map { it.sourceUrl } + cloudStreamRepos.map { it.sourceUrl })
            .mapTo(HashSet()) { canonicalCatalogUrl(it) }
    }

    var entries by remember { mutableStateOf<List<RepoManagerEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val installingUrl by container.catalogInstallCoordinator.installingUrl.collectAsStateWithLifecycle()
    val installOutcome by container.catalogInstallCoordinator.outcome.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var entryToRemove by remember { mutableStateOf<RepoManagerEntry?>(null) }
    var removingUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                entries = container.repoManagerCatalog.refresh()
                notice = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(installOutcome) {
        when (val outcome = installOutcome) {
            is CatalogInstallOutcome.Success -> {
                val name = when (val result = outcome.result) {
                    is com.kaan.play.data.plugin.AddResult.Addon -> result.name
                    is com.kaan.play.data.plugin.AddResult.PluginRepo -> result.name
                    is com.kaan.play.data.plugin.AddResult.CsRepo -> result.name
                }
                notice = context.getString(R.string.repo_manager_catalog_success, name)
                container.catalogInstallCoordinator.clearOutcome()
            }
            is CatalogInstallOutcome.Failure -> {
                notice = context.getString(R.string.repo_manager_catalog_error, outcome.message)
                container.catalogInstallCoordinator.clearOutcome()
            }
            null -> Unit
        }
    }

    val filtered = remember(entries, query) {
        val term = query.trim().lowercase()
        if (term.isBlank()) entries else entries.filter {
            it.name.lowercase().contains(term) || it.category.lowercase().contains(term) || it.url.lowercase().contains(term)
        }
    }

    val backFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.repo_manager_catalog_title),
            onBack = onBack,
            backFocusRequester = backFocusRequester,
            backModifier = Modifier.focusProperties {
                right = refreshFocusRequester
                down = searchFocusRequester
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.repo_manager_catalog_count, entries.size), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = ::refresh,
                enabled = !loading,
                modifier = Modifier
                    .focusRequester(refreshFocusRequester)
                    .focusProperties {
                        left = backFocusRequester
                        down = searchFocusRequester
                    }
                    .focusRing(),
            ) { Text(stringResource(R.string.repo_manager_catalog_refresh)) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(searchFocusRequester)
                .focusProperties {
                    up = refreshFocusRequester
                    down = firstCardFocusRequester
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                firstCardFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionUp -> {
                                refreshFocusRequester.requestFocus()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            singleLine = true,
            maxLines = 1,
            label = { Text(stringResource(R.string.repo_manager_catalog_search)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { firstCardFocusRequester.requestFocus() }),
        )
        error?.let { Text(stringResource(R.string.repo_manager_catalog_error, it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) }
        when {
            loading && entries.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            entries.isEmpty() && error == null -> Text(stringResource(R.string.repo_manager_catalog_empty), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            filtered.isEmpty() -> Text(stringResource(R.string.repo_manager_catalog_no_results), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filtered, key = { _, it -> it.id + "|" + it.url }) { index, entry ->
                    val isInstalled = canonicalCatalogUrl(entry.url) in installedUrls
                    CatalogEntryCard(
                        entry = entry,
                        installed = isInstalled,
                        installing = installingUrl == entry.url,
                        removing = removingUrl == entry.url,
                        onInstall = {
                            if (installingUrl == null && !isInstalled) {
                                notice = null
                                container.catalogInstallCoordinator.install(entry.url, entry.sourceType)
                            }
                        },
                        onRemove = { entryToRemove = entry },
                        buttonModifier = if (index == 0) {
                            Modifier
                                .focusRequester(firstCardFocusRequester)
                                .focusProperties { up = searchFocusRequester }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }

    entryToRemove?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToRemove = null },
            title = { Text(stringResource(R.string.repo_manager_catalog_remove_title)) },
            text = { Text(stringResource(R.string.repo_manager_catalog_remove_message, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    entryToRemove = null
                    removingUrl = entry.url
                    scope.launch {
                        try {
                            val url = canonicalCatalogUrl(entry.url)
                            val removed = when (entry.sourceType) {
                                SourceType.NUVIO_REPO -> addons.firstOrNull { canonicalCatalogUrl(it.sourceUrl) == url }
                                    ?.let { container.addons.remove(it.id); true } ?: false
                                SourceType.NUVIO_PLUGIN -> nuvioRepos.firstOrNull { canonicalCatalogUrl(it.sourceUrl) == url }
                                    ?.let { container.plugins.remove(it.id); true } ?: false
                                SourceType.CLOUDSTREAM -> cloudStreamRepos.firstOrNull { canonicalCatalogUrl(it.sourceUrl) == url }
                                    ?.let { container.csRepos.remove(it.id); true } ?: false
                            }
                            notice = context.getString(
                                if (removed) R.string.repo_manager_catalog_remove_success
                                else R.string.repo_manager_catalog_remove_missing,
                                entry.name,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            notice = context.getString(R.string.repo_manager_catalog_remove_error, e.message ?: e.javaClass.simpleName)
                        } finally {
                            removingUrl = null
                        }
                    }
                }) {
                    Text(stringResource(R.string.repo_manager_catalog_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRemove = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun CatalogEntryCard(
    entry: RepoManagerEntry,
    installed: Boolean,
    installing: Boolean,
    removing: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    buttonModifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.repo_manager_catalog_meta, entry.category, entry.sourceType.label()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                installing || removing -> CircularProgressIndicator(strokeWidth = 2.dp)
                installed -> {
                    Text(stringResource(R.string.repo_manager_catalog_installed), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onRemove, modifier = Modifier.focusRing().then(buttonModifier)) {
                        Icon(
                            imageVector = com.kaan.play.ui.nav.PlayIcons.Trash,
                            contentDescription = stringResource(R.string.repo_manager_catalog_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> Button(onClick = onInstall, modifier = Modifier.focusRing().then(buttonModifier)) {
                    Text(stringResource(R.string.repo_manager_catalog_install))
                }
            }
        }
    }
}

@Composable
private fun SourceType.label(): String = when (this) {
    SourceType.CLOUDSTREAM -> stringResource(R.string.repo_manager_platform_cloudstream)
    SourceType.NUVIO_REPO -> stringResource(R.string.repo_manager_platform_nuvio_addon)
    SourceType.NUVIO_PLUGIN -> stringResource(R.string.repo_manager_platform_nuvio_plugin)
}

private fun canonicalCatalogUrl(url: String) = url.trim().removeSuffix("/").lowercase()
