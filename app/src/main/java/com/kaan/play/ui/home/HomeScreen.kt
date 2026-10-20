package com.kaan.play.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.rememberPlayLayout

private val ScreenPadding = 20.dp

/**
 * Ana sayfa. Hiç eklenti yokken tek cümle + tek buton; eklenti varken kapak karuseli, "devam et" ve TMDB satırları.
 */
@Composable
fun HomeScreen(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onAddAddon: () -> Unit,
) {
    val container = rememberContainer()
    val hasSource by container.hasAnySource.collectAsStateWithLifecycle(initialValue = null)

    // Hiç addon ve hiç eklenti deposu yokken boş durum; biri varsa içerik (null: yükleniyor, boş durum yanıp sönmesin).
    when (hasSource) {
        null -> Box(Modifier.fillMaxSize())
        false -> HomeEmptyState(onAddAddon)
        true -> HomeContent(onOpenDetail, onOpenCategory, onContinue)
    }
}

@Composable
private fun HomeEmptyState(onAddAddon: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val focus = rememberInitialFocus()
        Button(onClick = onAddAddon, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.home_empty_action)) }
    }
}

@Composable
private fun HomeContent(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
) {
    // Dil değişince (etkinlik yeniden kurulur) yeni dille taze bir ViewModel: satırlar yeni dilde yüklenir.
    val vm = containerViewModel(key = "home-${AppLanguage.effective().iso}") { HomeViewModel(it.tmdb, it.continueWatching) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    // Durum kompozisyon kapsamında okunur (LazyColumn DSL bloğunda değil): değişince bu ekran yeniden çizilir.
    val continueWatching = vm.continueWatching.collectAsStateWithLifecycle().value
    val layout = rememberPlayLayout()
    val cardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp
    val heroHeight = heroHeightFor(layout.widthDp, layout.heightDp)

    val heroItems = (hero as? Loadable.Ready)?.value.orEmpty()
    val hasHero = heroItems.isNotEmpty()
    val hasContinue = continueWatching.isNotEmpty()
    // Kumanda: başlangıç odağı kapak düğmesinde (yoksa devam et / ilk satırın ilk kartında); geri dönünce son odaklanan öğe.
    // Kapak yüklenirken beklenir ki odak sonradan eklenen öğenin altına düşmesin.
    val firstReady = hero !is Loadable.Loading && (hasHero || hasContinue || rows.getValue(HomeRow.NewMovies) is Loadable.Ready)
    val focus = rememberFocusMemory(enabled = firstReady)

    // LazyColumn anahtar bazlı konumu korur: en üste sonradan eklenen "devam et" satırı ekranın üstüne itilirdi.
    val listState = rememberLazyListState()
    LaunchedEffect(hasContinue) { if (hasContinue) listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "hero") {
            when {
                hasHero -> HeroCarousel(
                    items = heroItems,
                    height = heroHeight,
                    focus = focus,
                    isDefaultFocus = true,
                    onOpen = onOpenDetail,
                )
                // Yükleniyor: aynı boyutta sönük kutu (yerleşim zıplamasın). Başarısızsa kapak hiç çıkmaz.
                hero is Loadable.Loading -> Box(Modifier.fillMaxWidth().height(heroHeight).background(MaterialTheme.colorScheme.surfaceVariant))
                else -> Spacer(Modifier.statusBarsPadding().height(8.dp))
            }
        }
        if (hasContinue) {
            item(key = "continue") {
                Section(title = stringResource(R.string.home_row_continue)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { "${it.entry.type}-${it.entry.tmdbId}" }) { item ->
                            ContinueCard(
                                item,
                                modifier = Modifier.focusMemory(
                                    focus, "c-${item.entry.type}-${item.entry.tmdbId}",
                                    isDefault = !hasHero && item === continueWatching.first(),
                                ),
                                width = cardWidth,
                            ) { onContinue(item) }
                        }
                    }
                }
            }
        }
        HomeRow.entries.forEach { row ->
            item(key = row.name) {
                Section(
                    title = stringResource(row.titleRes()),
                    onSeeAll = { onOpenCategory(row) },
                    headerModifier = Modifier.focusMemory(focus, "h-${row.name}"),
                ) {
                    when (val state = rows.getValue(row)) {
                        Loadable.Loading -> SkeletonRow(cardWidth)
                        is Loadable.Failed -> ErrorMessage(state.error, onRetry = { vm.load(row) }, modifier = Modifier.fillMaxWidth())
                        is Loadable.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.value, key = { "${it.type}-${it.id}" }) { media ->
                                PosterCard(
                                    title = media.title,
                                    subtitle = media.year?.toString(),
                                    imageUrl = media.posterUrl(),
                                    onClick = { onOpenDetail(media.type, media.id) },
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusMemory(
                                            focus,
                                            "${row.name}-${media.type}-${media.id}",
                                            isDefault = !hasHero && !hasContinue && row == HomeRow.NewMovies && media == state.value.first(),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    StatusBarScrim()
    }
}

/**
 * Kapak yüksekliği (dp): dik telefonda genişliğe yakın, geniş/yatay ekranda alçak ve en çok ekran yüksekliğinin
 * ~%62'si; çok alçak yatay ekranda bile içeriğe (logo, satır, düğme) yetecek kadar.
 */
internal fun heroHeightFor(widthDp: Int, heightDp: Int): Dp {
    val byWidth = if (widthDp < 600) widthDp * 1.1f else widthDp * 0.42f
    return byWidth.coerceAtMost(heightDp * 0.62f).coerceIn(260f, 520f).dp
}

@Composable
private fun Section(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val seeAll = stringResource(R.string.home_see_all)
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .then(if (onSeeAll != null) Modifier.clickable(onClickLabel = seeAll, onClick = onSeeAll) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Icon(PlayIcons.Forward, contentDescription = seeAll, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val entry = item.entry
    val subtitle: String
    val progress: Float?
    when (item) {
        is ContinueItem.Resume -> {
            val minutesLeft = (((entry.durationMs - entry.positionMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            subtitle = if (entry.type == MediaType.Tv) {
                stringResource(R.string.continue_left_episode, entry.season, entry.episode, minutesLeft)
            } else {
                stringResource(R.string.continue_left_movie, minutesLeft)
            }
            progress = entry.positionMs.toFloat() / entry.durationMs.toFloat()
        }
        // Sıradaki bölüm önerisi: henüz izlenmedi, ilerleme çubuğu yok.
        is ContinueItem.Next -> {
            subtitle = stringResource(R.string.continue_next_episode, item.season, item.episode)
            progress = null
        }
    }
    PosterCard(
        title = entry.title,
        subtitle = subtitle,
        imageUrl = TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster),
        onClick = onClick,
        modifier = modifier.width(width),
        progress = progress,
    )
}

/** Yüklenirken afiş biçiminde sönük kutular: yerleşim yüklenince zıplamaz. */
@Composable
private fun SkeletonRow(cardWidth: Dp) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Spacer(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

internal fun HomeRow.titleRes(): Int = when (this) {
    HomeRow.NewMovies -> R.string.home_row_new_movies
    HomeRow.NewSeries -> R.string.home_row_new_series
    HomeRow.WeeklyMovies -> R.string.home_row_weekly_movies
    HomeRow.TurkishMovies -> R.string.home_row_turkish_movies
    HomeRow.TurkishSeries -> R.string.home_row_turkish_series
}
