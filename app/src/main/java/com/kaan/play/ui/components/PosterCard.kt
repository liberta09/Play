package com.kaan.play.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as AndroidKeyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.LongPressKeyMachine
import com.kaan.play.ui.tv.tvFocusableScaleGlow
import androidx.compose.ui.zIndex
import java.util.concurrent.ConcurrentHashMap

private data class CachedImdbId(val value: String?, val checkedAtMs: Long)
private val tmdbImdbCache = ConcurrentHashMap<String, CachedImdbId>()
private const val EMPTY_IMDB_CACHE_TTL_MS = 2 * 60 * 1_000L
private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Play'in kart biçimi: ince çerçeveli 2:3 afiş (12dp köşe), altında başlık ve soluk alt satır.
 * [progress] (0..1) verilirse afişin altında ince ilerleme çizgisi çıkar ("devam et").
 * [overlay]: afişin üstüne çizilen küçük eylem (ör. favoriden çıkar düğmesi).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PosterCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
    /** Addon öğelerindeki IMDb kimliği; özel poster şablonu bununla doldurulur. */
    posterId: String? = null,
    /** TMDB kartlarında IMDb eşleştirmesi için isteğe bağlı kimlik. */
    tmdbId: Int? = null,
    mediaType: MediaType? = null,
    /** Başlık satırının yanında (odak yakalayıcısının dışında) gösterilen ikincil eylem düğmesi (ör. '⋯'). */
    trailingAction: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val resolvedImageUrl = resolvePosterImageUrl(imageUrl, posterId, tmdbId, mediaType)
    val shape = MaterialTheme.shapes.medium
    // Odak halkası yalnızca afişte (kart yazısını ezmez): tıklama göstergesi dalga olarak kalır, halka afişe çizilir.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Tüm sütunu yuvarlak kırpmayız: alt satırın ilk harfini keserdi. Yalnızca afiş kırpılır.
    val scope = rememberCoroutineScope()
    val machine = remember { LongPressKeyMachine() }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var currentPress by remember { mutableStateOf<PressInteraction.Press?>(null) }

    DisposableEffect(onLongClick) {
        onDispose {
            longPressJob?.cancel()
            longPressJob = null
            currentPress = null
            machine.reset()
        }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = onClick,
        )
    }

    val keyModifier = if (onLongClick != null) {
        Modifier
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    longPressJob?.cancel()
                    longPressJob = null
                    currentPress?.let { p -> scope.launch { interaction.emit(PressInteraction.Cancel(p)) } }
                    currentPress = null
                    machine.onFocusLost()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Menu) {
                    if (event.type == KeyEventType.KeyDown) {
                        longPressJob?.cancel()
                        longPressJob = null
                        currentPress?.let { p -> scope.launch { interaction.emit(PressInteraction.Cancel(p)) } }
                        currentPress = null
                        machine.reset()
                        onLongClick()
                    }
                    true
                } else if (event.key in OK_KEYS) {
                    if (event.type == KeyEventType.KeyDown) {
                        val native = event.nativeKeyEvent
                        val isNativeLong = native.isLongPress || (native.flags and AndroidKeyEvent.FLAG_LONG_PRESS) != 0
                        if (isNativeLong) {
                            longPressJob?.cancel()
                            longPressJob = null
                        }
                        val action = machine.onKeyDown(repeatCount = native.repeatCount, isLongPress = isNativeLong)
                        if (action is LongPressKeyMachine.Action.Press) {
                            val press = PressInteraction.Press(Offset.Zero)
                            currentPress = press
                            scope.launch { interaction.emit(press) }
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(machine.longPressThresholdMs)
                                machine.onTimeout()
                            }
                        }
                        true
                    } else if (event.type == KeyEventType.KeyUp) {
                        longPressJob?.cancel()
                        longPressJob = null
                        currentPress?.let { p ->
                            scope.launch { interaction.emit(PressInteraction.Release(p)) }
                            currentPress = null
                        }
                        val action = machine.onKeyUp()
                        when (action) {
                            is LongPressKeyMachine.Action.ShowDialogOnRelease -> {
                                onLongClick()
                                true
                            }
                            is LongPressKeyMachine.Action.Click -> {
                                onClick()
                                true
                            }
                            else -> true
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    } else {
        Modifier
    }

    @Composable
    fun PosterImage() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .tvFocusableScaleGlow(
                    scale = 1.06f,
                    glowColor = MaterialTheme.colorScheme.primary,
                    shape = shape,
                    isFocused = focused,
                    zIndex = 10f,
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (resolvedImageUrl != null) {
                AsyncImage(
                    model = resolvedImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            overlay()
            if (progress != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }

    val clickableModifier = modifier.then(keyModifier).then(clickModifier)

    if (trailingAction == null) {
        Column(modifier = clickableModifier.zIndex(if (focused) 10f else 0f)) {
            PosterImage()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        // Çağıranın modifier'ı (focusRequester/focusMemory/width) doğrudan odaklanabilir afiş sütununa gider;
        // alttaki başlık+eylem satırı afişin ölçülen genişliğine sabitlenir.
        Layout(
            modifier = Modifier.zIndex(if (focused) 10f else 0f),
            content = {
                Column(modifier = clickableModifier) {
                    PosterImage()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    trailingAction()
                }
            },
        ) { measurables, constraints ->
            val posterPlaceable = measurables[0].measure(constraints)
            val rowMaxHeight = if (constraints.hasBoundedHeight) {
                (constraints.maxHeight - posterPlaceable.height).coerceAtLeast(0)
            } else {
                Constraints.Infinity
            }
            val rowPlaceable = measurables[1].measure(
                Constraints(
                    minWidth = posterPlaceable.width,
                    maxWidth = posterPlaceable.width,
                    minHeight = 0,
                    maxHeight = rowMaxHeight,
                ),
            )
            layout(posterPlaceable.width, posterPlaceable.height + rowPlaceable.height) {
                posterPlaceable.placeRelative(0, 0)
                rowPlaceable.placeRelative(0, posterPlaceable.height)
            }
        }
    }
}

/** Özel poster şablonunu çözer; TMDB → IMDb eşleştirmesi bellekte önbelleğe alınır. */
@Composable
fun resolvePosterImageUrl(
    fallbackUrl: String?,
    posterId: String?,
    tmdbId: Int?,
    mediaType: MediaType?,
): String? {
    val container = rememberContainer()
    val posterTemplate by container.settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = PosterUrlTemplate.DEFAULT)
    val direct = PosterUrlTemplate.build(
        posterTemplate,
        PosterUrlTemplate.findImdbId(posterId, fallbackUrl),
    )
    val customPoster by produceState<String?>(null, direct, posterTemplate, tmdbId, mediaType) {
        if (direct != null) {
            value = direct
            return@produceState
        } else if (tmdbId != null && mediaType != null && posterTemplate.isNotBlank()) {
            val cacheKey = "${mediaType.name}:$tmdbId"
            val cached = tmdbImdbCache[cacheKey]
            val now = System.currentTimeMillis()
            val imdbId = when {
                !cached?.value.isNullOrBlank() -> cached.value
                cached != null && now - cached.checkedAtMs < EMPTY_IMDB_CACHE_TTL_MS -> null
                else -> {
                    val resolved = runCatching { container.tmdb.detail(mediaType, tmdbId).imdbId }
                        .getOrNull()
                        ?.let(PosterUrlTemplate::findImdbId)
                    tmdbImdbCache[cacheKey] = CachedImdbId(resolved, System.currentTimeMillis())
                    resolved
                }
            }
            value = PosterUrlTemplate.build(posterTemplate, imdbId)
        }
    }
    return direct ?: customPoster ?: fallbackUrl
}

/** Kart için "2019 · Dizi" gibi soluk satır. Yıl yoksa yalnızca tür. */
@Composable
fun MediaSummary.cardSubtitle(): String {
    val typeLabel = stringResource(if (type == MediaType.Movie) R.string.type_movie else R.string.type_series)
    return year?.let { stringResource(R.string.dot_join, it.toString(), typeLabel) } ?: typeLabel
}

@Composable
fun MediaSummary.posterUrl(): String? = TmdbImages.url(posterPath, TmdbImages.Size.Poster)
