package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.kaan.play.AppContainer
import com.kaan.play.PlayApp
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.tmdb.PosterUrlTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Bir satırın "Tümünü gör" ızgarası (Play `ProviderCategoryScreen` / TMDB `CategoryScreen` karşılığı). TvApp tutar:
 * detaya gidip dönünce yüklenen sayfalar ve odaktaki kart korunur.
 */
class CategoryGrid(val row: CatalogRow, val landscape: Boolean) {
    var items by mutableStateOf(row.items)
        private set
    // Play ile aynı tahmin: eklenti "sonraki sayfa var" demese de dolu bir ilk sayfa devam edebilir.
    var hasMore by mutableStateOf(row.hasNext || row.items.size >= 10)
        private set
    var loading by mutableStateOf(false)
        private set
    private var page = row.page
    var focusedIndex = 0

    /** Sonraki sayfa: TMDB satırı TMDB'den, eklenti/addon satırı Play'in `loadMore`'u ile. Boş ya da hatalı sayfa sonu demektir. */
    suspend fun loadMore(container: AppContainer) {
        if (loading || !hasMore) return
        loading = true
        val next = page + 1
        val more = try {
            if (row.providerId == TMDB_PROVIDER) container.tmdbPage(row.id, next).orEmpty()
            else container.catalogRepository.loadMore(row.providerId, row.id, next)
        } catch (e: CancellationException) {
            loading = false // ekran kapandı (ör. detay açıldı): sayfa sonu sayılmaz, dönünce yeniden istenir
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
        val known = items.mapTo(HashSet()) { it.id }
        val fresh = more.filter { known.add(it.id) }
        if (fresh.isEmpty()) hasMore = false else { items = items + fresh; page = next }
        loading = false
    }
}

/** Izgara: üstte satır adı, altta kumandayla gezilen kartlar; son kartlara yaklaşınca sonraki sayfa yüklenir. */
@Composable
fun TvCategoryScreen(grid: CategoryGrid, onOpen: (DetailRef) -> Unit) {
    val container = (LocalContext.current.applicationContext as PlayApp).container
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = grid.focusedIndex)
    // Play gibi: görünen son kart listenin sonuna yaklaşınca (yaklaşık iki sıra kala) sonraki sayfa istenir.
    val ahead = if (grid.landscape) 8 else 14
    LaunchedEffect(grid) {
        snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to grid.items.size }
            .distinctUntilChanged()
            .filter { (last, size) -> grid.hasMore && last >= size - ahead }
            .collect { grid.loadMore(container) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Gutter, end = Gutter, top = 28.dp, bottom = 10.dp)) {
            ScreenTitle(grid.row.title)
        }
        LazyVerticalGrid(
            state = state,
            columns = GridCells.FixedSize(cardWidth(grid.landscape)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(grid.items, key = { i, item -> "${item.id}|$i" }) { index, item: CatalogItem ->
                PosterCard(
                    title = item.title,
                    imageUrl = item.posterUrl,
                    landscape = grid.landscape,
                    requestInitialFocus = index == grid.focusedIndex,
                    onFocused = { grid.focusedIndex = index },
                    onClick = { item.toRef(grid.landscape)?.let(onOpen) },
                    posterId = PosterUrlTemplate.findImdbId(item.id, item.rawUrl),
                )
            }
            if (grid.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.tv_common_loading), color = TextDim, fontSize = TvText.Body, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Satırın sonundaki "Tümünü gör" kartı: afiş boyutunda, odakta vurgu çerçeve. */
@Composable
internal fun SeeAllCard(landscape: Boolean, requestInitialFocus: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth(landscape))
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .focusRequester(requester)
            .onFocusChanged { hasFocus = it.isFocused; if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(containerColor = Surface1),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape)),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.tv_home_see_all) + "  →", color = if (hasFocus) Accent else TextMain,
                fontSize = TvText.Body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (requestInitialFocus) InitialFocus(requester) { hasFocus }
}
