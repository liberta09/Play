package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dikey kaydırma (LiderShort): kısa video uygulamaları gibi afişler alt
 * alta dizili. Sıradaki afiş aşağıdan yukarı kayarak gelir, öndeki hafif küçülüp kararır ve yukarıdan çıkar; altta
 * sıradakinin üst kenarı görünür. Parmakla yukarı/aşağı kaydırma yalnız ortadaki afişin üstünde: afişin yanından
 * kaydırınca ana sayfa kayar (sürgü sayfayı kilitlemez). Birimler sahne birimi (sahne 400 birim genişlik).
 */
internal object FeedGeometry {
    const val STAGE = 400f
    const val CARD_W = 210f
    const val CARD_H = 315f
    const val TOP = 10f
    const val GAP = 14f
    const val PEEK = 46f // sıradaki afişin görünen üst kısmı
    const val STAGE_H = TOP + CARD_H + GAP + PEEK
    const val STEP = CARD_H + GAP // bir afiş geçişinde kayma
    const val SHRINK = 0.08f
    const val DIM = 0.45f

    /** Afişin dikey kayması (sahne birimi): q=0 ortada, q=1 altta, q=-1 yukarıda (sahne dışında). */
    fun shift(q: Float): Float = q * STEP

    fun scale(q: Float): Float = 1f - SHRINK * abs(q).coerceAtMost(1f)

    /** Yan afişlerin tema arka planına doğru kararması. */
    fun dim(q: Float): Float = DIM * abs(q).coerceAtMost(1f)

    /** Yalnız sahnede görünebilecek afişler çizilir (yukarıda çıkan, ortadaki, alttaki). */
    fun visible(q: Float): Boolean = q > -1f && q < 2f
}

@Composable
internal fun VerticalFeedCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    val position = remember { Animatable((savedIndex % count).toFloat()) }
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val centre by remember(count) { derivedStateOf { SliderMath.index(position.value, count) } }
    val slide = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 140f) }
    val fade = MaterialTheme.colorScheme.background

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, slide) }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val unit = maxWidth / FeedGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * FeedGeometry.STAGE_H)
                    .clipToBounds()
                    // Alttaki afiş saydamlaşarak silinir (zemin rengi boyanmaz; sayfa zemini neyse o görünür).
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val peek = FeedGeometry.PEEK * unitPx
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - peek, endY = size.height),
                            topLeft = Offset(0f, size.height - peek),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // Kart yuvası: dokunma ve dikey kaydırma yalnız burada.
                Box(
                    Modifier
                        .padding(top = unit * FeedGeometry.TOP)
                        .size(unit * FeedGeometry.CARD_W, unit * FeedGeometry.CARD_H)
                        .trackTouch { touching = it }
                        .pointerInput(count, unitPx) {
                            val tracker = VelocityTracker()
                            val stepPx = unitPx * FeedGeometry.STEP
                            var start = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    start = position.value
                                    tracker.resetTracking()
                                    job?.cancel()
                                },
                                onDragEnd = {
                                    val v = -tracker.calculateVelocity().y / stepPx
                                    go(SliderMath.settleTarget(start, position.value, v))
                                },
                                onDragCancel = { go(SliderMath.settleTarget(start, position.value, 0f)) },
                            ) { change, dy ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { position.snapTo(position.value - dy / stepPx) }
                            }
                        }
                        .pointerInput(items) { detectTapGestures { onOpen(items[centre]) } },
                ) {
                    items.forEachIndexed { index, item ->
                        FeedCard(item, index, count, position::value, unitPx, fade)
                    }
                }
            }
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}

/** Tek afiş: konum, boyut ve kararma çizim evresinde okunur (geçişte yeniden oluşturma yok). */
@Composable
private fun FeedCard(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    unitPx: Float,
    fade: Color,
) {
    val shape = remember { RoundedCornerShape(18.dp) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val q = SliderMath.offset(index, position(), count)
                if (!FeedGeometry.visible(q)) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = 1f
                translationY = FeedGeometry.shift(q) * unitPx
                val s = FeedGeometry.scale(q)
                scaleX = s
                scaleY = s
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                val a = FeedGeometry.dim(SliderMath.offset(index, position(), count))
                if (a > 0.01f) drawRect(fade.copy(alpha = a))
            },
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Alt yazı: kısa video uygulamalarındaki gibi afişin altında, karartma üstünde (her temada okunur).
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 14.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
