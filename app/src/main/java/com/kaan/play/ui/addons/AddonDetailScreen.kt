package com.kaan.play.ui.addons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.CatalogVisibility
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.stream.fetchAddonText
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.home.ProviderCategoryScreen
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Duration.Companion.seconds

private sealed interface CatalogCountState {
    data object Loading : CatalogCountState
    data class Success(val count: Int) : CatalogCountState
    data object Error : CatalogCountState
}

@Composable
fun AddonDetailScreen(
    addonId: Long,
    onBack: () -> Unit,
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenProviderDetail: (providerId: String, url: String) -> Unit,
    onOpenSearch: (String) -> Unit,
    container: AppContainer,
) {
    val addons by container.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    val addon = remember(addons, addonId) { addons?.firstOrNull { it.id == addonId } }
    val disabledCatalogs by container.settings.disabledCatalogs.collectAsStateWithLifecycle(initialValue = emptySet())

    var activeCategoryRow by remember { mutableStateOf<CatalogRow?>(null) }

    val currentCategory = activeCategoryRow
    if (currentCategory != null) {
        BackHandler { activeCategoryRow = null }
        ProviderCategoryScreen(
            row = currentCategory,
            onBack = { activeCategoryRow = null },
            onOpenDetail = onOpenDetail,
            onOpenProviderDetail = onOpenProviderDetail,
            onOpenSearch = onOpenSearch,
        )
        return
    }

    if (addon == null) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = stringResource(R.string.addon_detail_title), onBack = onBack)
            if (addons != null) {
                EmptyMessage(stringResource(R.string.addons_empty))
            }
        }
        return
    }

    AddonDetailContent(
        addon = addon,
        disabledCatalogs = disabledCatalogs,
        onBack = onBack,
        onToggleCatalog = { cat, enabled ->
            container.appScope.launch {
                container.settings.setCatalogEnabled(addon.id, cat.type, cat.id, enabled)
                container.catalogRepository.cache.clear()
            }
        },
        onOpenCatalog = { row -> activeCategoryRow = row },
        container = container,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddonDetailContent(
    addon: Addon,
    disabledCatalogs: Set<String>,
    onBack: () -> Unit,
    onToggleCatalog: (ManifestCatalog, Boolean) -> Unit,
    onOpenCatalog: (CatalogRow) -> Unit,
    container: AppContainer,
) {
    val scope = rememberCoroutineScope()
    val manifest = addon.manifest
    val catalogs = manifest.catalogs

    val catalogCounts = remember { mutableStateMapOf<String, CatalogCountState>() }
    val catalogItems = remember { mutableStateMapOf<String, List<CatalogItem>>() }

    LaunchedEffect(addon.id, catalogs) {
        val semaphore = Semaphore(3)
        for (cat in catalogs) {
            val key = "${cat.type}:${cat.id}"
            catalogCounts[key] = CatalogCountState.Loading
            scope.launch {
                semaphore.withPermit {
                    withContext(Dispatchers.IO) {
                        try {
                            val urlString = "${addon.baseUrl}/catalog/${cat.type}/${cat.id}.json"
                            val httpUrl = urlString.toHttpUrlOrNull()
                            if (httpUrl == null) {
                                catalogCounts[key] = CatalogCountState.Error
                                return@withContext
                            }
                            val jsonText = fetchAddonText(container.httpClient, httpUrl, 10.seconds, 5L * 1024 * 1024)
                            val response = PlayJson.decodeFromString(StremioCatalogResponse.serializer(), jsonText)
                            val items = response.metas.distinctBy { it.id }.map { meta ->
                                CatalogItem(
                                    id = meta.id,
                                    title = meta.name,
                                    subtitle = when (meta.type.lowercase()) {
                                        "series" -> "Dizi"
                                        "tv", "channel" -> if (java.util.Locale.getDefault().language == "tr") "Canlı" else "Live"
                                        else -> null
                                    },
                                    posterUrl = meta.poster,
                                    posterHeaders = null,
                                    mediaType = when (meta.type.lowercase()) {
                                        "series", "tv", "channel" -> MediaType.Tv
                                        else -> MediaType.Movie
                                    },
                                    providerId = "nuvio_addon:${addon.id}",
                                    rawUrl = null,
                                    addonType = meta.type.lowercase(),
                                )
                            }
                            catalogItems[key] = items
                            catalogCounts[key] = CatalogCountState.Success(items.size)
                        } catch (_: Throwable) {
                            catalogCounts[key] = CatalogCountState.Error
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = manifest.name,
            subtitle = manifest.version?.let { "v$it" },
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Açıklama
            manifest.description?.takeIf { it.isNotBlank() }?.let { desc ->
                item(key = "desc") {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Etiketler (Türler ve Kaynaklar)
            item(key = "badges") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // İçerik Türleri
                    for (t in manifest.types.distinct()) {
                        val label = when (t.lowercase()) {
                            "movie" -> stringResource(R.string.addon_badge_movie)
                            "series" -> stringResource(R.string.addon_badge_series)
                            "tv", "channel" -> stringResource(R.string.addon_badge_live_tv)
                            else -> t.replaceFirstChar { it.uppercase() }
                        }
                        AddonMetaChip(text = label)
                    }
                    // Kaynak Türleri
                    for (r in manifest.resources.map { it.name.lowercase() }.distinct()) {
                        val label = when (r) {
                            "catalog" -> stringResource(R.string.addon_badge_catalog)
                            "stream" -> stringResource(R.string.addon_badge_stream)
                            "subtitles" -> stringResource(R.string.addon_badge_subtitles)
                            "meta" -> stringResource(R.string.addon_badge_meta)
                            else -> r.replaceFirstChar { it.uppercase() }
                        }
                        AddonMetaChip(text = label)
                    }
                }
            }

            // Kataloglar Bölümü
            item(key = "catalogs_header") {
                Text(
                    text = stringResource(R.string.addon_catalogs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (catalogs.isEmpty()) {
                item(key = "no_catalogs") {
                    Text(
                        text = stringResource(R.string.addon_no_catalogs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                items(catalogs, key = { "${it.type}:${it.id}" }) { cat ->
                    val key = "${cat.type}:${cat.id}"
                    val isEnabled = CatalogVisibility.isVisible(addon.id, cat.type, cat.id, disabledCatalogs)
                    val countState = catalogCounts[key]
                    val catTitle = cat.name ?: when (cat.id) {
                        "top" -> if (cat.type == "series") "Popüler Diziler" else "Popüler Filmler"
                        else -> cat.id.replaceFirstChar { it.uppercase() }
                    }

                    CatalogManageRow(
                        title = catTitle,
                        type = when (cat.type.lowercase()) {
                            "movie" -> stringResource(R.string.addon_badge_movie)
                            "series" -> stringResource(R.string.addon_badge_series)
                            "tv", "channel" -> stringResource(R.string.addon_badge_live_tv)
                            else -> cat.type.replaceFirstChar { it.uppercase() }
                        },
                        enabled = isEnabled,
                        countState = countState,
                        onToggle = { onToggleCatalog(cat, it) },
                        onClick = {
                            val items = catalogItems[key].orEmpty()
                            onOpenCatalog(
                                CatalogRow(
                                    id = key,
                                    title = catTitle,
                                    items = items,
                                    hasNext = items.size >= 10,
                                    page = 1,
                                    providerId = "nuvio_addon:${addon.id}",
                                    providerName = manifest.name,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonMetaChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CatalogManageRow(
    title: String,
    type: String,
    enabled: Boolean,
    countState: CatalogCountState?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                when (countState) {
                    is CatalogCountState.Loading, null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.player_sources_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is CatalogCountState.Success -> {
                        Text(
                            text = stringResource(R.string.addon_items_count, countState.count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is CatalogCountState.Error -> {
                        Text(
                            text = stringResource(R.string.addon_items_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Icon(
                PlayIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .focusRing(CircleShape)
                    .semantics { contentDescription = title },
            )
        }
    }
}

@Serializable
private data class StremioCatalogResponse(
    val metas: List<StremioMeta> = emptyList(),
)

@Serializable
private data class StremioMeta(
    val id: String,
    val type: String = "movie",
    val name: String,
    val poster: String? = null,
    val description: String? = null,
)
