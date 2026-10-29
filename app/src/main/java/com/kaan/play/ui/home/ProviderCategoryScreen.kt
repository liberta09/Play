package com.kaan.play.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Sağlayıcı ana sayfasındaki bir satırın ("Tümünü göster") tam ızgarası ve sonraki sayfa yüklemesi.
 */
@Composable
fun ProviderCategoryScreen(
    row: CatalogRow,
    onBack: () -> Unit,
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenProviderDetail: (providerId: String, url: String) -> Unit,
    onOpenSearch: (String) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var items by remember(row.id) { mutableStateOf(row.items) }
    var page by remember(row.id) { mutableIntStateOf(row.page) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember(row.id) { mutableStateOf(row.hasNext || row.items.size >= 10) }

    val focus = rememberFocusMemory(enabled = items.isNotEmpty())
    val grid = rememberLazyGridState()

    // Liste sonuna yaklaşıldığında sonraki sayfayı iste
    LaunchedEffect(grid, items.size, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= items.size - 6 }
            .collect {
                if (!isLoadingMore && hasMore) {
                    isLoadingMore = true
                    val nextPage = page + 1
                    val newItems = container.catalogRepository.loadMore(row.providerId, row.id, nextPage)
                    if (newItems.isEmpty()) {
                        hasMore = false
                    } else {
                        items = (items + newItems).distinctBy { it.id }
                        page = nextPage
                    }
                    isLoadingMore = false
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = row.title,
            subtitle = row.providerName.takeIf { it.isNotBlank() },
            onBack = onBack,
        )

        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}_$index" }) { index, item ->
                PosterCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    imageUrl = item.posterUrl,
                    posterId = PosterUrlTemplate.findImdbId(item.id, item.rawUrl, item.posterUrl),
                    tmdbId = item.id.toIntOrNull() ?: PosterUrlTemplate.findTmdbId(item.id, item.rawUrl, item.posterUrl),
                    mediaType = item.mediaType,
                    onClick = {
                        if (!item.rawUrl.isNullOrBlank() && (item.providerId.startsWith("cs:") || item.providerId.startsWith("nuvio_addon:"))) {
                            onOpenProviderDetail(item.providerId, item.rawUrl)
                        } else {
                            scope.launch {
                                resolveAndOpenPoster(
                                    item = item,
                                    tmdb = container.tmdb,
                                    addons = container.addons,
                                    onOpenDetail = onOpenDetail,
                                    onOpenProviderDetail = onOpenProviderDetail,
                                    onOpenSearch = onOpenSearch,
                                )
                            }
                        }
                    },
                    modifier = Modifier.focusMemory(
                        focus,
                        "pcat-${item.id}",
                        isDefault = index == 0,
                    ),
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}
