package com.kaan.play.ui.addons

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.data.plugin.CatalogInstallOutcome
import com.kaan.play.data.plugin.RepoManagerEntry
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import kotlinx.coroutines.launch
import java.util.Locale



fun canonicalCatalogUrl(url: String): String {
    return url.trim().removeSuffix("/").lowercase(Locale.ROOT)
}

@Composable
fun RepoManagerCatalogScreen(
    onBack: () -> Unit,
    container: AppContainer,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installedAddons by container.addons.addons.collectAsStateWithLifecycle(initialValue = emptyList())
    val installedPlugins by container.plugins.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    val installedCsRepos by container.csRepos.repos.collectAsStateWithLifecycle(initialValue = emptyList())

    val installedUrls = remember(installedAddons, installedPlugins, installedCsRepos) {
        val urls = HashSet<String>()
        installedAddons.forEach { urls.add(canonicalCatalogUrl(it.sourceUrl)) }
        installedPlugins.forEach { urls.add(canonicalCatalogUrl(it.sourceUrl)) }
        installedCsRepos.forEach { urls.add(canonicalCatalogUrl(it.sourceUrl)) }
        urls
    }

    var entries by remember { mutableStateOf<List<RepoManagerEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val installingUrl by container.catalogInstallCoordinator.installingUrl.collectAsStateWithLifecycle()
    val outcome by container.catalogInstallCoordinator.outcome.collectAsStateWithLifecycle()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                entries = container.repoManagerCatalog.refresh()
            } catch (e: Exception) {
                error = e.message ?: "Katalog yüklenemedi"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(outcome) {
        when (val res = outcome) {
            is CatalogInstallOutcome.Success -> {
                Toast.makeText(context, "Depo başarıyla eklendi", Toast.LENGTH_SHORT).show()
                container.catalogInstallCoordinator.clearOutcome()
            }
            is CatalogInstallOutcome.Failure -> {
                Toast.makeText(context, "Hata: ${res.message}", Toast.LENGTH_LONG).show()
                container.catalogInstallCoordinator.clearOutcome()
            }
            null -> Unit
        }
    }

    val filteredEntries = remember(entries, query) {
        if (query.isBlank()) entries
        else {
            val q = query.lowercase(Locale.ROOT).trim()
            entries.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q) ||
                it.url.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Depo Kataloğu",
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Depo veya kategori ara...") },
            leadingIcon = {
                Icon(
                    imageVector = PlayIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "Bir hata oluştu",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { refresh() }) {
                            Text("Tekrar Dene")
                        }
                    }
                }
            }
            filteredEntries.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "Depo bulunamadı" else "Sonuç bulunamadı",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = filteredEntries,
                        key = { it.url + "|" + it.sourceType.name },
                    ) { entry ->
                        val isInstalled = installedUrls.contains(canonicalCatalogUrl(entry.url))
                        val isInstalling = installingUrl == entry.url
                        CatalogEntryCard(
                            entry = entry,
                            installed = isInstalled,
                            installing = isInstalling,
                            onInstall = {
                                container.catalogInstallCoordinator.install(entry.url, entry.sourceType)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogEntryCard(
    entry: RepoManagerEntry,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = label(entry.sourceType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (entry.category.isNotBlank() && entry.category != "Diğer") {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                installing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                installed -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Yüklü",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Yükle",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

private fun label(sourceType: SourceType): String = when (sourceType) {
    SourceType.CLOUDSTREAM -> "CloudStream"
    SourceType.NUVIO_REPO -> "Nuvio Repo"
    SourceType.NUVIO_PLUGIN -> "Nuvio Eklenti"
}
