package com.kaan.play.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.rememberCursorField
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.cardSubtitle
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.nav.PlayIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Ara: arama kutusu, tür filtresi ve sonuç ızgarası. */
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onOpenDetail: (MediaType, Int) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
) {
    val container = rememberContainer()
    val profileId by container.localProfile.id.collectAsStateWithLifecycle(initialValue = "")
    val profiles by container.localProfile.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val childProfile = profiles.firstOrNull { it.id == profileId }?.isChild == true
    val vm = containerViewModel(key = "search-${AppLanguage.effective().iso}-$profileId-$childProfile") { SearchViewModel(it.tmdb) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && state.query != initialQuery) {
            vm.onQueryChange(initialQuery)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    // Kumanda: sonuçlarla (geri dönüş) açılırsa son sonuç, aksi halde arama kutusu başlangıç odağını alır.
    val startedWithResults = remember { state.items.isNotEmpty() }
    val fieldFocus = rememberInitialFocus(enabled = !startedWithResults)
    val focus = rememberFocusMemory(enabled = startedWithResults)

    val field = rememberCursorField(state.query, vm::onQueryChange)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = field.value,
            onValueChange = field.onValueChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(PlayIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(PlayIcons.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            // Kumanda/klavye ile odaklanınca klavye kendiliğinden açılmaz; Tamam/dokunma açar. Ara: sonucu gösterir, klavyeyi kapatır.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                .focusRequester(fieldFocus)
                .let { if (topBarFocusRequester != null) it.focusProperties { up = topBarFocusRequester } else it }
                .dpadLeavesTextField(field),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchFilter.entries.forEach { filter ->
                val isSelected = state.filter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.setFilter(filter) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF0F1E2E),
                        labelColor = Color(0xFFB0BEC5),
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color(0xFF07111A),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color(0xFF1E3A5F),
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.5.dp,
                    ),
                    modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    label = {
                        Text(
                            text = stringResource(
                                when (filter) {
                                    SearchFilter.All -> R.string.search_filter_all
                                    SearchFilter.Movies -> R.string.search_filter_movies
                                    SearchFilter.Series -> R.string.search_filter_series
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CenteredLoading()
                state.error != null -> ErrorMessage(state.error!!, onRetry = vm::retry, modifier = Modifier.align(Alignment.Center))
                state.activeQuery.isEmpty() -> Hint(stringResource(R.string.search_idle))
                state.items.isEmpty() && state.allItems.isNotEmpty() && state.hasMore -> {
                    // Seçili tür ilk TMDB sayfasında yoksa filtre kullanıcıyı boşta bırakmasın; eşleşme
                    // bulunana ya da katalog bitene kadar sonraki sayfalar arka planda alınır.
                    LaunchedEffect(state.activeQuery, state.filter, state.page) { vm.loadMore() }
                    CenteredLoading()
                }
                state.items.isEmpty() -> Hint(
                    if (state.allItems.isNotEmpty()) {
                        stringResource(
                            when (state.filter) {
                                SearchFilter.All -> R.string.search_no_results
                                SearchFilter.Movies -> R.string.search_filtered_no_results_movies
                                SearchFilter.Series -> R.string.search_filtered_no_results_series
                            },
                            state.activeQuery,
                        )
                    } else {
                        stringResource(R.string.search_no_results, state.activeQuery)
                    },
                )
                else -> {
                    val grid = rememberLazyGridState()
                    // Sondan ~6 öğe kala sonraki sayfayı iste.
                    LaunchedEffect(grid, state.items.size) {
                        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged()
                            .filter { it >= state.items.size - 6 }
                            .collect { vm.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = grid,
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { "${it.type}-${it.id}" }) { media ->
                            PosterCard(
                                title = media.title,
                                subtitle = media.cardSubtitle(),
                                imageUrl = media.posterUrl(),
                                tmdbId = media.id,
                                mediaType = media.type,
                                onClick = { onOpenDetail(media.type, media.id) },
                                modifier = Modifier.focusMemory(focus, "${media.type}-${media.id}", isDefault = media == state.items.first()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
