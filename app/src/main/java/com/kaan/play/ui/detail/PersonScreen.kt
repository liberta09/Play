package com.kaan.play.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.PersonCreditSort
import com.kaan.play.data.model.PersonCreditTab
import com.kaan.play.data.model.PersonDetail
import com.kaan.play.data.tmdb.PersonCreditsRules
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.components.loadable
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PersonViewModel(
    private val tmdb: TmdbRepository,
    private val personId: Int,
) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<PersonDetail>>(Loadable.Loading)
    val state: StateFlow<Loadable<PersonDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Loadable.Loading
        viewModelScope.launch {
            _state.value = loadable { tmdb.person(personId) }
        }
    }
}

/** Oyuncu / ekip üyesi sayfası: fotoğraf, ad, kısa biyografi ve Filmografi ızgarası. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonScreen(
    personId: Int,
    initialName: String,
    onBack: () -> Unit,
    onOpenDetail: (MediaType, Int) -> Unit,
) {
    val vm = containerViewModel(key = "person-$personId-${AppLanguage.effective().iso}") { PersonViewModel(it.tmdb, personId) }
    val state by vm.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable(personId) { mutableStateOf(PersonCreditTab.Movie) }
    var selectedSort by rememberSaveable(personId) { mutableStateOf(PersonCreditSort.Popular) }

    val loadedPerson = (state as? Loadable.Ready)?.value
    val displayTitle = loadedPerson?.name?.takeIf { it.isNotBlank() } ?: initialName
    val focus = rememberFocusMemory(enabled = loadedPerson != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = displayTitle,
            onBack = onBack,
            initialFocus = loadedPerson == null,
        )

        Box(Modifier.weight(1f)) {
            when (val s = state) {
                Loadable.Loading -> CenteredLoading()
                is Loadable.Failed -> ErrorMessage(s.error, onRetry = vm::load, modifier = Modifier.align(Alignment.Center))
                is Loadable.Ready -> {
                    val person = s.value
                    val baseCredits = when (selectedTab) {
                        PersonCreditTab.Movie -> person.movieCredits
                        PersonCreditTab.Tv -> person.tvCredits
                    }
                    val sorted = remember(baseCredits, selectedSort) {
                        PersonCreditsRules.sort(baseCredits, selectedSort)
                    }
                    var bioExpanded by rememberSaveable(personId) { mutableStateOf(false) }
                    var bioOverflows by remember { mutableStateOf(false) }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val photo = TmdbImages.url(person.profilePath, TmdbImages.Size.Profile)
                                        if (photo != null) {
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Text(
                                                text = displayTitle.take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = displayTitle,
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        val bioText = person.biography.ifBlank { stringResource(R.string.person_bio_empty) }
                                        Text(
                                            text = bioText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                                            overflow = TextOverflow.Ellipsis,
                                            onTextLayout = { if (!bioExpanded) bioOverflows = it.hasVisualOverflow },
                                        )
                                        if (bioOverflows || bioExpanded) {
                                            TextButton(
                                                onClick = { bioExpanded = !bioExpanded },
                                                modifier = Modifier
                                                    .offset(x = (-12).dp)
                                                    .focusMemory(focus, "bio_toggle"),
                                            ) {
                                                Text(stringResource(if (bioExpanded) R.string.detail_overview_less else R.string.detail_overview_more))
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.person_filmography),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterPill(
                                        text = stringResource(R.string.person_tab_movies, person.movieCredits.size),
                                        selected = selectedTab == PersonCreditTab.Movie,
                                        onClick = { selectedTab = PersonCreditTab.Movie },
                                        modifier = Modifier.focusMemory(focus, "tab_movie", isDefault = true),
                                    )
                                    FilterPill(
                                        text = stringResource(R.string.person_tab_series, person.tvCredits.size),
                                        selected = selectedTab == PersonCreditTab.Tv,
                                        onClick = { selectedTab = PersonCreditTab.Tv },
                                        modifier = Modifier.focusMemory(focus, "tab_tv"),
                                    )
                                    FilterPill(
                                        text = stringResource(R.string.person_sort_popular),
                                        selected = selectedSort == PersonCreditSort.Popular,
                                        onClick = { selectedSort = PersonCreditSort.Popular },
                                        modifier = Modifier.focusMemory(focus, "sort_popular"),
                                    )
                                    FilterPill(
                                        text = stringResource(R.string.person_sort_date),
                                        selected = selectedSort == PersonCreditSort.Date,
                                        onClick = { selectedSort = PersonCreditSort.Date },
                                        modifier = Modifier.focusMemory(focus, "sort_date"),
                                    )
                                }
                            }
                        }

                        if (sorted.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = stringResource(
                                        if (selectedTab == PersonCreditTab.Movie) R.string.person_empty_movies else R.string.person_empty_series,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            itemsIndexed(sorted, key = { _, item -> "${item.media.type}-${item.media.id}" }) { _, credit ->
                                val media = credit.media
                                PosterCard(
                                    title = media.title,
                                    subtitle = media.year?.toString(),
                                    imageUrl = media.posterUrl(),
                                    tmdbId = media.id,
                                    mediaType = media.type,
                                    onClick = { onOpenDetail(media.type, media.id) },
                                    modifier = Modifier.focusMemory(focus, "${media.type}-${media.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
