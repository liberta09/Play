package com.kaan.play.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.HeroItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.TitleOrLogo
import com.kaan.play.ui.tv.FocusMemory
import com.kaan.play.ui.tv.focusMemory
import kotlinx.coroutines.launch

/**
 * Ana sayfanın kapak karuseli: arka plan görseli, başlık logosu (yoksa metin başlık), "Tür · Yıl" satırı,
 * "Detayları gör" düğmesi ve sayfa noktaları. Dokunmada kaydırılır; kumandada düğmedeyken Sol/Sağ sayfa değiştirir
 * (ilk sayfada Sol / son sayfada Sağ olay dışarı bırakılır: yan menüye geçilebilir).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    items: List<HeroItem>,
    height: Dp,
    focus: FocusMemory,
    isDefaultFocus: Boolean,
    onOpen: (MediaType, Int) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Odak düğmedeyken sayfa değişince odak yeni sayfanın düğmesine taşınır (eski sayfa kompozisyondan çıkabilir).
    fun go(target: Int) {
        scope.launch {
            pager.animateScrollToPage(target)
            runCatching { requesters[target].requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (!hasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val page = pager.currentPage
                when (event.key) {
                    Key.DirectionRight -> if (page < items.lastIndex) { go(page + 1); true } else false
                    Key.DirectionLeft -> if (page > 0) { go(page - 1); true } else focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionUp -> if (topBarFocusRequester != null) {
                        runCatching { topBarFocusRequester.requestFocus() }.getOrDefault(false)
                    } else false
                    else -> false
                }
            },
    ) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            HeroPage(
                item = item,
                onOpen = { onOpen(item.media.type, item.media.id) },
                buttonModifier = Modifier
                    .focusRequester(requesters[page])
                    .let { if (page == pager.currentPage && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                    .let { if (page == pager.currentPage) it.focusMemory(focus, "hero", isDefault = isDefaultFocus) else it }
                    .let {
                        if (topBarFocusRequester != null) {
                            it.focusProperties { up = topBarFocusRequester }
                        } else it
                    },
            )
        }
        PageDots(
            count = items.size,
            selected = pager.currentPage,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun HeroPage(item: HeroItem, onOpen: () -> Unit, buttonModifier: Modifier) {
    val media = item.media
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        TmdbImages.url(media.backdropPath, TmdbImages.Size.BackdropLarge)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Üstte durum çubuğu için hafif karartma, altta sayfa rengine eriyen geçiş (yazı okunur, görsel "kesilmiş" görünmez).
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.28f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.85f to background.copy(alpha = 0.92f), 1f to background)))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TitleOrLogo(title = media.title, logoPath = item.logoPath)
            heroLine(item)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onOpen, modifier = buttonModifier.height(44.dp)) {
                Text(stringResource(R.string.home_hero_details))
            }
        }
    }
}

/** "Aksiyon · Macera · 2024": en çok iki tür ve yıl. */
@Composable
private fun heroLine(item: HeroItem): String? {
    val parts = item.genres + listOfNotNull(item.media.year?.toString())
    if (parts.isEmpty()) return null
    return parts.reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}


@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == selected) 18.dp else 6.dp, label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
            )
        }
    }
}
