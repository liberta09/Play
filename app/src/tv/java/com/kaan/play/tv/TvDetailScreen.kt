package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import com.kaan.play.ui.nav.PlayIcons
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.CastMember
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages

/**
 * TV detayı: solda görsel, sağda ad, bilgi, özet ve düğmeler; dizide altta sezonlar ve yatay bölüm satırı.
 * Görsel yönü gerçek görsel oranından (§4.1): dikey afiş 2:3, film/dizi karesi 16:9. Filmde bölüm satırı yok.
 */
@Composable
fun TvDetailScreen(ref: DetailRef, onPlay: PlayLauncher, onOpen: (DetailRef) -> Unit = {}, onSearch: (String) -> Unit = {}) {
    val model: TvDetailModel = viewModel(key = "detail")
    LaunchedEffect(ref.providerId, ref.url) { model.load(ref.providerId, ref.url) }
    val state by model.state.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Düzen: görsel sağa yaslı, sola ve aşağı zemine geçiş (16:9 kare tam parlaklık, dikey afiş karartılmış).
        val ready = (state as? DetailState.Ready)?.data
        // Dikey afişte Play'deki gibi Poster URL şablonu (IMDb kimliği varsa); 16:9 karede görsel olduğu gibi kalır.
        val base = ready?.posterUrl ?: ref.imageUrl
        val backdrop = if (ref.landscape) base else templatedPoster(base, ready?.imdbId)
        if (backdrop != null) {
            AsyncImage(
                backdrop, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.70f).align(Alignment.TopEnd).alpha(if (ref.landscape) 0.9f else 0.35f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.32f to Bg, 0.6f to Bg.copy(alpha = 0.55f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 0.72f to Bg)))
        when (val s = state) {
            DetailState.Loading -> Centered {
                Text(ref.title, color = TextMain, fontSize = TvText.Hero, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tv_common_loading), color = TextDim, fontSize = TvText.Body)
            }
            is DetailState.Failed -> Centered {
                Text(ref.title, color = TextMain, fontSize = TvText.Hero, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(s.message), color = TextMain, fontSize = TvText.Body)
                Spacer(Modifier.height(20.dp))
                val retry = remember { FocusRequester() }
                var focused by remember { mutableStateOf(false) }
                Button(onClick = model::retry, modifier = Modifier.focusRequester(retry).onFocusChanged { focused = it.isFocused }) {
                    Text(stringResource(R.string.tv_detail_retry))
                }
                InitialFocus(retry) { focused }
            }
            is DetailState.Ready -> Ready(ref, s.data, history, model, onPlay, onOpen, onSearch)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // BringIntoViewSpec
@Composable
private fun Ready(
    ref: DetailRef, d: DetailData, history: List<WatchHistoryEntity>, model: TvDetailModel, onPlay: PlayLauncher,
    onOpen: (DetailRef) -> Unit, onSearch: (String) -> Unit,
) {
    val context = LocalContext.current
    val favorites = remember { TvFavorites.get(context) }
    val favKeys by favorites.keys.collectAsStateWithLifecycle()
    val isFavorite = favorites.isFavorite(ref, d.tmdbId, d.type, favKeys)

    val progress: EpisodeProgress = remember(history) {
        history.associate { (it.season to it.episode) to if (WatchRules.isWatched(it.positionMs, it.durationMs)) 1f else (it.positionMs.toFloat() / it.durationMs.coerceAtLeast(1)) }
    }
    val action = remember(d, history) { primaryAction(d, history.firstOrNull()) }
    var season by remember(d) { mutableIntStateOf(action.episode?.season ?: d.seasons.firstOrNull() ?: 1) }
    var imageAspect by remember(d) { mutableFloatStateOf(if (ref.landscape) 16f / 9f else 2f / 3f) }

    val primary = remember { FocusRequester() }
    var primaryFocused by remember { mutableStateOf(false) }

    val last = history.firstOrNull()
    val current = action.episode
    // Alt satırlar eklenince detay kaydırılabilir oldu: TV'nin varsayılan odak kaydırması odaktaki düğmeyi ekranın üçte birine
    // çekip başlığı kesiyordu. Dikeyde yalnız gerektiği kadar kaydırılır (satır başlığı görünür kalır); yatay satırlar varsayılanla.
    val listState = rememberLazyListState()
    val rowSpec = LocalBringIntoViewSpec.current
    val titleSpacePx = with(LocalDensity.current) { 36.dp.toPx() }
    val captionSpacePx = with(LocalDensity.current) { 56.dp.toPx() }
    val columnSpec = remember(titleSpacePx, captionSpacePx) { MinimalScrollSpec(titleSpacePx, captionSpacePx) }
    LaunchedEffect(primaryFocused) { if (primaryFocused) listState.animateScrollToItem(0) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides columnSpec) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 36.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "head") {
            Row {
                Column(Modifier.weight(1f).padding(end = 24.dp)) {
                    Text(d.title, color = TextMain, fontSize = TvText.Hero, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    // Bilgi satırı: "2026 | Dram, Romantik | 24 Bölüm"
                    val meta = listOfNotNull(
                        d.year?.toString(),
                        d.tags.take(2).joinToString(", ").ifBlank { null },
                        if (d.isSeries) (if (d.seasons.size > 1) pluralStringResource(R.plurals.tv_detail_seasons_count, d.seasons.size, d.seasons.size) else pluralStringResource(R.plurals.tv_detail_episodes_count, d.episodes.size, d.episodes.size)) else stringResource(R.string.tv_detail_movie),
                    )
                    Text(meta.joinToString("  |  "), color = TextMain.copy(alpha = 0.85f), fontSize = TvText.Meta, fontWeight = FontWeight.Medium, maxLines = 1)
                    // Play detayındaki yönetmen / yazar / yaratıcı satırları, TV'de tek satır.
                    val credits = listOfNotNull(
                        d.directors.filterNot { it in d.creators }.takeIf { it.isNotEmpty() }?.let { stringResource(com.kaan.play.R.string.detail_director) + ": " + it.joinToString(", ") },
                        d.writers.filterNot { it in d.creators }.takeIf { it.isNotEmpty() }?.let { stringResource(com.kaan.play.R.string.detail_writer) + ": " + it.joinToString(", ") },
                        d.creators.takeIf { it.isNotEmpty() }?.let { stringResource(com.kaan.play.R.string.detail_creator) + ": " + it.joinToString(", ") },
                    )
                    if (credits.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(credits.joinToString("  ·  "), color = TextDim, fontSize = TvText.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (d.isSeries && current != null) {
                        Spacer(Modifier.height(12.dp))
                        val name = current.name?.takeIf { it.isNotBlank() && !it.contains("Bölüm", ignoreCase = true) }
                        Text(
                            stringResource(R.string.tv_detail_season_episode, current.season, current.episode) + (name?.let { ": $it" } ?: ""),
                            color = TextMain, fontSize = TvText.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    d.plot?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = TextMain.copy(alpha = 0.85f), fontSize = TvText.Body, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (action.resume && last != null && last.durationMs > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(150.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TextDim.copy(alpha = 0.4f))) {
                                Box(Modifier.fillMaxWidth((last.positionMs.toFloat() / last.durationMs).coerceIn(0f, 1f)).height(4.dp).background(Accent))
                            }
                            Spacer(Modifier.width(10.dp))
                            val left = ((last.durationMs - last.positionMs) / 60_000L).coerceAtLeast(1)
                            Text(stringResource(R.string.tv_detail_minutes_left, left.toInt()), color = TextMain, fontSize = TvText.Label)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlay.play(model.target(d, action.episode), startAtMs = if (action.resume) null else 0L) },
                            modifier = Modifier.focusRequester(primary).onFocusChanged { primaryFocused = it.isFocused },
                            shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                            colors = ButtonDefaults.colors(containerColor = Accent, contentColor = OnAccent, focusedContainerColor = AccentFocused, focusedContentColor = OnAccent),
                        ) { Text("▶  " + action.text(), fontSize = TvText.Body, fontWeight = FontWeight.SemiBold) }
                        if (action.resume) {
                            RoundButton(PlayIcons.Refresh, stringResource(R.string.tv_detail_start_over)) { onPlay.play(model.target(d, action.episode), startAtMs = 0L) }
                        }
                        RoundButton(if (isFavorite) PlayIcons.HeartFilled else PlayIcons.Heart, stringResource(if (isFavorite) R.string.tv_detail_favorite_remove else R.string.tv_detail_favorite_add)) {
                            // Play favorisi: TMDB içeriğinde TMDB afiş yolu (telefon ve bulut onu okur), eklentide afiş adresi.
                            favorites.toggle(ref.copy(title = d.title, imageUrl = d.posterUrl ?: ref.imageUrl), d.tmdbId, d.type, d.tmdbPosterPath ?: d.posterUrl ?: ref.imageUrl)
                        }
                        // Play'deki "Fragman": YouTube fragmanı TV oynatıcısında (ana sayfa önizlemesiyle aynı çözücü).
                        val trailer = d.trailerKey ?: youtubeKey(d.trailerUrl)
                        if (trailer != null) {
                            Button(
                                onClick = { context.startActivity(TvTrailerActivity.intent(context, trailer, d.title)) },
                                modifier = Modifier.height(44.dp),
                                shape = ButtonDefaults.shape(RoundedCornerShape(22.dp)),
                                colors = ButtonDefaults.colors(containerColor = Surface1.copy(alpha = 0.85f), contentColor = TextMain, focusedContainerColor = TextMain, focusedContentColor = Bg),
                            ) {
                                Icon(PlayIcons.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(com.kaan.play.R.string.detail_trailer_title), fontSize = TvText.Body)
                            }
                        }
                    }
                }
                // Dikey drama afişi sağda kendisi durur (arka plan karartılmış); 16:9 karede arka plan zaten görselin kendisi.
                if (!ref.landscape && imageAspect < 1f) {
                    AsyncImage(
                        templatedPoster(d.posterUrl ?: ref.imageUrl, d.imdbId), d.title, contentScale = ContentScale.Crop,
                        onSuccess = { r -> imageAspect = if (r.result.image.width > r.result.image.height * 1.2f) 16f / 9f else 2f / 3f },
                        modifier = Modifier.height(260.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            InitialFocus(primary) { primaryFocused }
        }
        if (d.isSeries && d.episodes.isNotEmpty()) {
            item(key = "tabs") {
                Column {
                    Text(stringResource(R.string.tv_detail_episodes), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(top = 4.dp).width(72.dp).height(2.dp).background(Accent))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TextDim.copy(alpha = 0.3f)))
                }
            }
            if (d.seasons.size > 1) {
                item(key = "seasons") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(d.seasons) { s -> SeasonChip(stringResource(R.string.tv_detail_season, s), selected = s == season) { season = s } }
                    }
                    }
                }
            }
            item(key = "episodes-$season") {
                val list = d.episodes.filter { it.season == season }
                val rowState = rememberLazyListState(
                    initialFirstVisibleItemIndex = list.indexOfFirst { it == action.episode }.coerceAtLeast(0),
                )
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(list, key = { "${it.season}-${it.episode}-${it.data.hashCode()}" }) { ep ->
                        EpisodeCard(
                            ep = ep, fallbackImage = d.posterUrl,
                            progress = progress[ep.season to ep.episode],
                            current = ep == action.episode,
                            onClick = { onPlay.play(model.target(d, ep)) },
                        )
                    }
                }
                }
            }
        }
        // Play detayındaki "Benzer filmler/diziler" ve "Oyuncular". Benzer kart TMDB detayını açar (Geri bu detaya döner);
        // oyuncu kartı Play'deki gibi oyuncunun adıyla aramayı açar.
        if (d.similar.isNotEmpty()) {
            item(key = "similar") {
                DetailRow(rowSpec, stringResource(if (d.type == MediaType.Tv) com.kaan.play.R.string.detail_similar_series else com.kaan.play.R.string.detail_similar_movies)) {
                    items(d.similar, key = { "similar-${it.type}-${it.id}" }) { m ->
                        PosterCard(
                            title = m.title, imageUrl = TmdbImages.url(m.posterPath ?: m.backdropPath, TmdbImages.Size.Poster), landscape = false,
                            onClick = { onOpen(DetailRef(TMDB_PROVIDER, "${m.type.tmdbPath}:${m.id}", true, m.title, TmdbImages.url(m.backdropPath ?: m.posterPath, TmdbImages.Size.Backdrop))) },
                        )
                    }
                }
            }
        }
        if (d.cast.isNotEmpty()) {
            item(key = "cast") {
                DetailRow(rowSpec, stringResource(com.kaan.play.R.string.detail_cast)) {
                    items(d.cast, key = { "cast-${it.id}" }) { CastCard(it) { onSearch(it.name) } }
                }
            }
        }
    }
    }
}

/**
 * Dikey kaydırma: öğe görünürse kaydırma yok; üstte kalırsa satır başlığı ([titleSpacePx]) de görünecek kadar, altta kalırsa
 * kartın altındaki yazı (oyuncu adı ve rolü, [captionSpacePx]) da görünecek kadar.
 */
@OptIn(ExperimentalFoundationApi::class)
private class MinimalScrollSpec(private val titleSpacePx: Float, private val captionSpacePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset >= 0f && offset + size + captionSpacePx <= containerSize -> 0f
        offset < 0f || size > containerSize -> offset - titleSpacePx
        else -> offset + size + captionSpacePx - containerSize
    }
}

/** Detayın alt satırı: başlık + yatay kart listesi (kumandayla sağa-sola). */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DetailRow(rowSpec: BringIntoViewSpec, title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        Text(title, color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp), content = content)
        }
    }
}

/** Oyuncu kartı: yuvarlak fotoğraf (yoksa baş harf), ad ve rol; OK oyuncunun adıyla aramayı açar. */
@Composable
private fun CastCard(member: CastMember, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(84.dp).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent), shape = CircleShape)),
            colors = CardDefaults.colors(containerColor = Surface1),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val photo = TmdbImages.url(member.profilePath, TmdbImages.Size.Profile)
                if (photo != null) AsyncImage(photo, member.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(member.name.take(1).uppercase(), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            member.name, color = if (focused) TextMain else TextMain.copy(alpha = 0.85f), fontSize = TvText.Meta, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        if (member.role.isNotBlank()) Text(member.role, color = TextDim, fontSize = TvText.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp),
        shape = ButtonDefaults.shape(CircleShape),
        colors = ButtonDefaults.colors(containerColor = Surface1.copy(alpha = 0.85f), contentColor = TextMain, focusedContainerColor = TextMain, focusedContentColor = Bg),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) // FilterChip
@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick) { Text(label, fontSize = TvText.Meta) }
}

/** Bölüm kartı: 16:9 görsel + ilerleme; altında "B3: Ad". Oynayan/sıradaki bölüm ince vurgu çerçeveli. */
@Composable
private fun EpisodeCard(ep: DetailEpisode, fallbackImage: String?, progress: Float?, current: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(190.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(
                border = if (current) Border(BorderStroke(1.dp, Accent.copy(alpha = 0.7f)), shape = shape) else Border.None,
                focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
            ),
            colors = CardDefaults.colors(containerColor = Surface1),
        ) {
            Box(Modifier.fillMaxSize()) {
                val img = ep.posterUrl ?: fallbackImage
                if (img != null) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (ep.posterUrl != null) 1f else 0.45f))
                if (ep.posterUrl == null) {
                    Text("${ep.episode}", color = TextMain, fontSize = TvText.Hero, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
                if (progress != null && progress >= 0.999f) {
                    Text("✓", color = Accent, fontSize = TvText.Row, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
                if (progress != null && progress in 0.01f..0.999f) {
                    Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f))) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Accent))
                    }
                }
            }
        }
        val name = ep.name?.takeIf { it.isNotBlank() && !it.contains("Bölüm", ignoreCase = true) && it != "Episode ${ep.episode}" }
        Text(
            name?.let { stringResource(R.string.tv_detail_episode_named, ep.episode, it) } ?: stringResource(R.string.tv_detail_episode_n, ep.episode),
            color = if (focused || current) TextMain else TextDim, fontSize = TvText.Meta, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Ana düğme: kaldığı bölümden devam, biten bölümün sıradakini, hiç izlenmediyse ilk bölümü (filmde filmi) oynatır. */
private data class PrimaryAction(@StringRes val label: Int, val episode: DetailEpisode?, val resume: Boolean)

/** Düğme yazısı: eylem, bölüm varsa "· N. Bölüm" ile (dil değişince yeniden çözülsün diye kaynak kimliği tutulur). */
@Composable
private fun PrimaryAction.text(): String {
    val verb = stringResource(label)
    val ep = episode ?: return verb
    return stringResource(R.string.tv_detail_action_episode, verb, stringResource(R.string.tv_detail_episode_n, ep.episode))
}

private fun primaryAction(d: DetailData, last: WatchHistoryEntity?): PrimaryAction {
    if (!d.isSeries) {
        val resume = last != null && WatchRules.isResumable(last.positionMs, last.durationMs)
        return PrimaryAction(if (resume) R.string.tv_detail_resume else R.string.tv_detail_play, null, resume)
    }
    val first = d.episodes.firstOrNull() ?: return PrimaryAction(R.string.tv_detail_play, null, false)
    if (last == null) return PrimaryAction(R.string.tv_detail_play, first, false)
    val index = d.episodes.indexOfFirst { it.season == last.season && it.episode == last.episode }
    if (index < 0) return PrimaryAction(R.string.tv_detail_play, first, false)
    val ep = d.episodes[index]
    if (WatchRules.isResumable(last.positionMs, last.durationMs)) return PrimaryAction(R.string.tv_detail_resume, ep, true)
    val next = d.episodes.getOrNull(index + 1) ?: return PrimaryAction(R.string.tv_detail_rewatch, ep, false)
    return PrimaryAction(R.string.tv_detail_up_next, next, false)
}
