package com.kaan.play.ui.addons

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.cloudstream.CsRepo
import com.kaan.play.data.cloudstream.CsRepoReference
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing

/** CloudStream .cs3 depoları ve keşfedilen alt repo kataloğu. */
@Composable
fun CsReposScreen(
    onBack: () -> Unit,
    container: AppContainer,
) {
    val vm = remember { CsReposViewModel(container.csRepos, container.csRepoCatalog) }
    val repos by vm.repos.collectAsStateWithLifecycle()
    val subRepositories by vm.subRepositories.collectAsStateWithLifecycle()
    val installingUrl by vm.installingUrl.collectAsStateWithLifecycle()
    val installMessage by vm.installMessage.collectAsStateWithLifecycle()
    var showSubRepositories by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.cs_repos_title), onBack = onBack)

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = { showSubRepositories = false }) {
                Text(stringResource(R.string.cs_repos_tab_installed), color = if (!showSubRepositories) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showSubRepositories = true }) {
                Text(stringResource(R.string.cs_repos_tab_discovered), color = if (showSubRepositories) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showSubRepositories) {
            if (!installMessage.isNullOrBlank()) {
                Text(installMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            if (subRepositories.isEmpty()) {
                Text(
                    text = stringResource(R.string.cs_repos_discovered_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.cs_repos_discovered_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(subRepositories, key = { it.url }) { item ->
                        CsRepoCatalogRow(item = item, isInstalling = installingUrl == item.url, onInstall = { vm.install(item) })
                    }
                }
            }
        } else when {
            repos == null -> Unit
            repos!!.isEmpty() -> {
                Text(
                    text = stringResource(R.string.cs_repos_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.plugins_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(repos!!, key = { it.id }) { repo ->
                        CsRepoRow(repo = repo, onRemove = { vm.remove(repo) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CsRepoCatalogRow(item: CsRepoReference, isInstalling: Boolean, onInstall: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name.ifBlank { item.url }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!item.description.isNullOrBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(item.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onInstall, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.cs_repo_install))
                }
            }
        }
    }
}

@Composable
internal fun CsRepoRow(repo: CsRepo, onRemove: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusGroup(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusRing(RoundedCornerShape(10.dp))
                    .focusable()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.cs_plugins_count, repo.pluginCount, repo.pluginCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!repo.description.isNullOrBlank()) {
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(
                    PlayIcons.Trash,
                    contentDescription = stringResource(R.string.repos_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.cs_repo_remove_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.cs_repo_remove_body, repo.name)) },
            confirmButton = {
                com.kaan.play.ui.components.TextButton(onClick = { confirmDelete = false; onRemove() }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                com.kaan.play.ui.components.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** CloudStream eklentileri satırı — [InstalledAddonsScreen] içinde CS depo grubu olarak kullanılır. */
@Composable
fun CsPluginRow(plugin: InstalledCsPlugin, onToggle: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusGroup(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusRing(RoundedCornerShape(10.dp))
                    .focusable()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!plugin.description.isNullOrBlank()) {
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                modifier = Modifier.focusRing(CircleShape),
            )
        }
    }
}
