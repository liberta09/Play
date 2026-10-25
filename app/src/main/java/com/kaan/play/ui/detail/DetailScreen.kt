package com.kaan.play.ui.detail

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.CastMember
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.AgeChip
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.OutlinedIconButton
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.RatingBadge
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.components.TitleOrLogo
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.rememberPlayLayout

private val Gutter = 20.dp

/**
 * Detay. Film: tek ana iş "Oynat". Dizi: ana iş bölüm seçmek (sezon seçici + bölüm listesi).
 * [onPlay]: kaynak isteğiyle çağrılır (film: sezon/bölüm yok; dizi: seçilen bölüm).
 */
@Composable
fun DetailScreen(
    type: MediaType,
    id: Int,
    onBack: () -> Unit,
    onOpenSimilar: (MediaType, Int) -> Unit = { _, _ -> },
    onOpenActor: (String) -> Unit = {},
    onPlay: (PlayTarget) -> Unit,
) {
    val vm = containerViewModel(key = "detail-${type.tmdbPath}-$id-${AppLanguage.effective().iso}") {
        DetailViewModel(it.tmdb, it.library, type, id)
    }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val layout = rememberPlayLayout()

    Box(Modifier.fillMaxSize()) {
        when (val d = detail) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Failed -> ErrorMessage(d.error, onRetry = vm::loadDetail, modifier = Modifier.align(Alignment.Center))
            is Loadable.Ready -> DetailContent(
                detail = d.value,
                twoPane = layout.twoPane,
                screenHeight = layout.heightDp.dp,
                isFavorite = isFavorite,
                selectedSeason = season,
                episodes = episodes,
                onToggleFavorite = vm::toggleFavorite,
                onSelectSeason = vm::selectSeason,
                onRetryEpisodes = vm::retryEpisodes,
                onOpenSimilar = onOpenSimilar,
                onOpenActor = onOpenActor,
                onPlay = { s, e ->
                    val media = d.value.summary
                    onPlay(PlayTarget(StreamRequest(media.id, d.value.imdbId, media.type, s, e), media.title, media.posterPath))
                },
            )
        }
        StatusBarScrim()
        // Geri oku: kapağın üstünde de okunsun diye yarı saydam daire içinde modern buton.
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .size(40.dp)
                .focusRing(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PlayIcons.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun DetailContent(
    detail: MediaDetail,
    isFavorite: Boolean,
    selectedSeason: Int?,
    episodes: Loadable<List<Episode>>,
    twoPane: Boolean,
    screenHeight: Dp,
    isSeries: Boolean = detail.summary.type == MediaType.Tv,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRetryEpisodes: () -> Unit,
    onOpenSimilar: (MediaType, Int) -> Unit = { _, _ -> },
    onOpenActor: (String) -> Unit = {},
    onPlay: (Int?, Int?) -> Unit,
) {
    val media = detail.summary
    val posterTemplate by rememberContainer().settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = PosterUrlTemplate.DEFAULT)
    val preferredPoster = PosterUrlTemplate.build(posterTemplate, detail.imdbId)
        ?: media.backdropPath
        ?: media.posterPath
    // Kumanda: başlangıç odağı ana işte (film: Oynat, dizi: sezon seçici).
    val primaryFocus = rememberInitialFocus()
    val header: @Composable (Dp) -> Unit = { padding ->
        HeaderBlock(detail, isFavorite, selectedSeason, primaryFocus, padding, isSeries, onToggleFavorite, onSelectSeason, onPlay)
    }

    if (twoPane) {
        // Geniş/yatay ekran: tam ekran arka plan sağda görünür, bilgiler solda koyu geçişin üstünde akar.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            WideBackdrop(preferredPoster)
            LazyColumn(
                modifier = Modifier.fillMaxHeight().width(minOf(maxWidth * 0.62f, 680.dp)),
                contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 56.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Box(Modifier.statusBarsPadding()) { header(0.dp) } }
                item { BodyBlock(detail, horizontalPadding = 0.dp) }
                trailerSection(detail.trailerKey)
                similarSection(detail.similar, detail.summary.type, 0.dp, onOpenSimilar)
                castSection(detail.cast, horizontalPadding = 0.dp, onOpenActor = onOpenActor)
                if (isSeries) episodeSection(episodes, selectedSeason, 8.dp, onRetryEpisodes, onPlay)
            }
        }
    } else {
        // Dik telefon: kapak görseli sayfayla birlikte kayar; başlık ve ana işler görselin alt kenarına biner.
        val headerHeight = (screenHeight * 0.58f).coerceIn(300.dp, 520.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val background = MaterialTheme.colorScheme.background
                Box(Modifier.fillMaxWidth().heightIn(min = headerHeight)) {
                    TmdbImages.url(preferredPoster, TmdbImages.Size.BackdropLarge)?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                    // Alt kenar sayfa rengine erir: başlık okunur, kapak "kesilmiş" görünmez.
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 0.9f to background.copy(alpha = 0.95f), 1f to background)))
                    Box(Modifier.align(Alignment.BottomStart).padding(top = 72.dp)) { header(Gutter) }
                }
            }
            item { BodyBlock(detail, horizontalPadding = Gutter) }
            trailerSection(detail.trailerKey)
            similarSection(detail.similar, detail.summary.type, Gutter, onOpenSimilar)
            castSection(detail.cast, horizontalPadding = Gutter, onOpenActor = onOpenActor)
            if (isSeries) episodeSection(episodes, selectedSeason, Gutter, onRetryEpisodes, onPlay)
        }
    }
}

/** Başlık (logo ya da metin), tür satırı ve ana işler (film: Oynat + ⋯; dizi: sezon seçici + ⋯). */
@Composable
private fun HeaderBlock(
    detail: MediaDetail,
    isFavorite: Boolean,
    selectedSeason: Int?,
    primaryFocus: FocusRequester,
    horizontalPadding: Dp,
    isSeries: Boolean = detail.summary.type == MediaType.Tv,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlay: (Int?, Int?) -> Unit,
) {
    val media = detail.summary
    Column(Modifier.padding(horizontal = horizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleOrLogo(title = media.title, logoPath = detail.logoPath, logoHeight = 72.dp)
        genreLine(detail)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isSeries) {
                SeasonPicker(
                    seasons = detail.seasons,
                    selected = selectedSeason,
                    onSelect = onSelectSeason,
                    modifier = Modifier.weight(1f),
                    focus = primaryFocus,
                )
            } else {
                Button(
                    onClick = { onPlay(null, null) },
                    modifier = Modifier.weight(1f).height(52.dp).focusRequester(primaryFocus),
                ) {
                    Icon(PlayIcons.PlayTriangle, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.detail_play), modifier = Modifier.padding(start = 8.dp))
                }
            }
            MoreButton(isFavorite, onToggleFavorite)
        }
    }
}

/** Yıl · süre, yaş sınırı çipi, TMDB puan rozeti; yönetmen/yazar; kısaltılmış özet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyBlock(detail: MediaDetail, horizontalPadding: Dp) {
    val media = detail.summary
    Column(Modifier.padding(horizontal = horizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val yearRuntime = yearRuntimeLine(detail)
        if (yearRuntime != null || detail.certification != null || media.rating != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                yearRuntime?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                detail.certification?.let { AgeChip(it) }
                media.rating?.let { RatingBadge(it) }
            }
        }
        // Dizide yaratıcı aynı zamanda yönetmen/yazar görünüyorsa üç satırda aynı ad tekrarlanmasın.
        val directors = detail.directors.filterNot { it in detail.creators }
        val writers = detail.writers.filterNot { it in detail.creators }
        if (directors.isNotEmpty()) CreditLine(stringResource(R.string.detail_director), directors)
        if (writers.isNotEmpty()) CreditLine(stringResource(R.string.detail_writer), writers)
        if (detail.creators.isNotEmpty()) CreditLine(stringResource(R.string.detail_creator), detail.creators)
        if (media.overview.isNotBlank()) Overview(media.overview)
    }
}

@Composable
private fun CreditLine(label: String, names: List<String>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append(label) }
            append("  ")
            append(names.joinToString(", "))
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Özet 3 satıra kısaltılır; sığmıyorsa "Daha fazla göster" düğmesi çıkar. */
@Composable
private fun Overview(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.offset(x = (-12).dp)) {
                Text(stringResource(if (expanded) R.string.detail_overview_less else R.string.detail_overview_more))
            }
        }
    }
}

private fun LazyListScope.trailerSection(key: String?) {
    if (key.isNullOrBlank() || youtubeTrailerEmbedHtml(key) == null) return
    item(key = "trailer") {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.detail_trailer_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Gutter))
            Surface(
                onClick = {
                    YouTubeTrailerActivity.createIntent(context, key)?.let(context::startActivity)
                },
                modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth().aspectRatio(16f / 9f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/$key/hqdefault.jpg",
                        contentDescription = stringResource(R.string.detail_trailer_play),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier.align(Alignment.Center).size(56.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(PlayIcons.PlayTriangle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.similarSection(
    items: List<com.kaan.play.data.model.MediaSummary>,
    mediaType: MediaType,
    horizontalPadding: Dp,
    onOpen: (MediaType, Int) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "similar") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (mediaType == MediaType.Tv) R.string.detail_similar_series else R.string.detail_similar_movies),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { "similar-${it.type}-${it.id}" }) { media ->
                    PosterCard(
                        title = media.title,
                        subtitle = media.year?.toString(),
                        imageUrl = TmdbImages.url(media.posterPath, TmdbImages.Size.Poster),
                        tmdbId = media.id,
                        mediaType = media.type,
                        onClick = { onOpen(media.type, media.id) },
                        modifier = Modifier.width(118.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.castSection(cast: List<CastMember>, horizontalPadding: Dp, onOpenActor: (String) -> Unit = {}) {
    if (cast.isEmpty()) return
    item(key = "cast") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.detail_cast), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = horizontalPadding))
            LazyRow(
                contentPadding = PaddingValues(start = (horizontalPadding - 10.dp).coerceAtLeast(0.dp), end = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cast, key = { it.id }) { CastItem(it, onOpenActor) }
            }
        }
    }
}

/** Yuvarlak fotoğraf + ad + rol. Dokunulacak bir işi yok ama kumandayla odaklanır (listeyi kaydırabilmek için). */
@Composable
private fun CastItem(member: CastMember, onOpenActor: (String) -> Unit = {}) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(92.dp)
            .focusRing(shape)
            .focusable()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val photo = TmdbImages.url(member.profilePath, TmdbImages.Size.Profile)
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(member.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(member.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (member.role.isNotBlank()) {
            Text(
                member.role,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.episodeSection(
    episodes: Loadable<List<Episode>>,
    selectedSeason: Int?,
    horizontalPadding: Dp,
    onRetryEpisodes: () -> Unit,
    onPlay: (Int?, Int?) -> Unit,
) {
    when (episodes) {
        Loadable.Loading -> item { CenteredLoadingRow() }
        is Loadable.Failed -> item {
            ErrorMessage(episodes.error, onRetry = onRetryEpisodes, modifier = Modifier.fillMaxWidth())
        }
        is Loadable.Ready -> if (episodes.value.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.detail_no_episodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontalPadding),
                )
            }
        } else {
            items(episodes.value, key = { it.number }) { episode ->
                EpisodeRow(episode, horizontalPadding) { onPlay(selectedSeason ?: 1, episode.number) }
            }
        }
    }
}

/** Geniş ekranda tüm ekranı kaplayan arka plan: solda koyu geçiş (yazı okunur), sağda görsel görünür. */
@Composable
private fun WideBackdrop(path: String?) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        TmdbImages.url(path, TmdbImages.Size.BackdropLarge)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to background, 0.45f to background.copy(alpha = 0.94f), 1f to background.copy(alpha = 0.3f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.65f to Color.Transparent, 1f to background)))
    }
}

/** En çok 3 tür: "Aksiyon · Macera · Bilim Kurgu". */
@Composable
private fun genreLine(detail: MediaDetail): String? {
    if (detail.genres.isEmpty()) return null
    return detail.genres.take(3).reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}

/** Yıl · süre (biliniyorsa). */
@Composable
private fun yearRuntimeLine(detail: MediaDetail): String? {
    val parts = buildList {
        detail.summary.year?.let { add(it.toString()) }
        detail.runtimeMinutes?.let { add(formatRuntime(it)) }
    }
    if (parts.isEmpty()) return null
    return parts.reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}

@Composable
private fun formatRuntime(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.runtime_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.runtime_minutes, minutes)

/** Yuvarlak "daha fazla" düğmesi; menüde favori ekle/çıkar (durum kalp simgesinde görünür). */
@Composable
private fun MoreButton(isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedIconButton(onClick = { open = true }, modifier = Modifier.size(52.dp)) {
            Icon(PlayIcons.More, contentDescription = stringResource(R.string.detail_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (isFavorite) R.string.detail_favorite_remove else R.string.detail_favorite_add)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) PlayIcons.HeartFilled else PlayIcons.Heart,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { open = false; onToggleFavorite() },
            )
        }
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<SeasonInfo>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(52.dp).focusRequester(focus)) {
            Text(
                text = stringResource(R.string.detail_season, selected ?: seasons.firstOrNull()?.number ?: 1),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(PlayIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            seasons.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_season, s.number)) },
                    onClick = { open = false; onSelect(s.number) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, horizontalPadding: Dp, onClick: () -> Unit) {
    val title = if (episode.name.isBlank()) {
        stringResource(R.string.detail_episode_fallback, episode.number)
    } else {
        stringResource(R.string.detail_episode_title, episode.number, episode.name)
    }
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
            .focusRing(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                TmdbImages.url(episode.stillPath, TmdbImages.Size.Still)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                episode.runtimeMinutes?.takeIf { it > 0 }?.let {
                    Text(formatRuntime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (episode.overview.isNotBlank()) {
                    Text(
                        episode.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoadingRow() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}
