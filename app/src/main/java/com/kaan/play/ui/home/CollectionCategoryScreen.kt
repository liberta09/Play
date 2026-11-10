package com.kaan.play.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import com.kaan.play.data.collection.CollectionFolderShelf
import com.kaan.play.data.collection.CollectionSourceKind
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** İçe aktarılan koleksiyon klasörünün tam afiş ızgarası ("Tümünü göster"). */
@Composable
fun CollectionCategoryScreen(
    shelf: CollectionFolderShelf,
    onBack: () -> Unit,
    onOpenDetail: (MediaType, Int) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val lang = AppLanguage.effective().tmdbTag
    var items by remember(shelf.collectionId, shelf.folder.id, lang) { mutableStateOf<List<MediaSummary>>(emptyList()) }
    var loading by remember(shelf.collectionId, shelf.folder.id, lang) { mutableStateOf(true) }
    var error by remember(shelf.collectionId, shelf.folder.id, lang) { mutableStateOf<TmdbException?>(null) }
    var page by remember(shelf.collectionId, shelf.folder.id, lang) { mutableIntStateOf(1) }
    var totalPages by remember(shelf.collectionId, shelf.folder.id, lang) { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }

    val supportsPagination = remember(shelf.folder.sources) {
        shelf.folder.sources.any { it.kind == CollectionSourceKind.Discover }
    }

    suspend fun loadInitial() {
        loading = true
        error = null
        try {
            val res = container.tmdb.collectionFolder(shelf.folder, page = 1)
            items = res.items
            page = res.page
            totalPages = res.totalPages
            loading = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: TmdbException) {
            loading = false
            error = e
        } catch (t: Throwable) {
            loading = false
            error = TmdbException(TmdbException.Kind.Network, path = "collection", cause = t)
        }
    }

    LaunchedEffect(shelf.collectionId, shelf.folder.id, lang) {
        loadInitial()
    }

    val focus = rememberFocusMemory(enabled = items.isNotEmpty())
    val grid = rememberLazyGridState()

    LaunchedEffect(grid, items.size, page, totalPages, supportsPagination) {
        if (!supportsPagination || page >= totalPages) return@LaunchedEffect
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= items.size - 6 }
            .collect {
                if (!loadingMore && page < totalPages) {
                    loadingMore = true
                    runCatching {
                        val next = container.tmdb.collectionFolder(shelf.folder, page + 1)
                        items = (items + next.items).distinctBy { it.type to it.id }
                        page = next.page
                        totalPages = maxOf(totalPages, next.totalPages)
                    }
                    loadingMore = false
                }
            }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = shelf.folder.title,
            onBack = onBack,
            initialFocus = items.isEmpty(),
        )
        Box(Modifier.weight(1f)) {
            when {
                loading -> CenteredLoading()
                error != null && items.isEmpty() -> ErrorMessage(error!!, onRetry = { scope.launch { loadInitial() } }, modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items, key = { "${it.type}-${it.id}" }) { media ->
                        PosterCard(
                            title = media.title,
                            subtitle = media.year?.toString(),
                            imageUrl = media.posterUrl(),
                            tmdbId = media.id,
                            mediaType = media.type,
                            onClick = { onOpenDetail(media.type, media.id) },
                            modifier = Modifier.focusMemory(focus, "${media.type}-${media.id}", isDefault = media == items.first()),
                        )
                    }
                }
            }
        }
    }
}
