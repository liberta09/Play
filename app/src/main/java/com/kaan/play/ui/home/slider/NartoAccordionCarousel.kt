package com.kaan.play.ui.home.slider

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Akordeon şeritler (Narto): yan yana dar dikey şeritler, her biri afişin orta dilimi ve dikey başlık;
 * seçilen şerit yaylı biçimde genişleyip afişin tamamını gösterir, diğerleri daralır. Konum sürekli bir sayıdır: iki tam
 * sayı arasında açık şerit kapanırken sıradaki aynı oranda açılır (toplam genişlik sabit). Sonuncudan ilke geçişte de
 * yalnız bu iki şerit değişir (döngü).
 */
internal object AccordionGeometry {
    const val OPEN_FRACTION = 0.56f // açık şeridin genişliği / toplam
    const val POSTER_RATIO = 1.5f // açık şerit afişin tamamını gösterir (2:3)
    const val GAP = 3f // dp
    const val DRAG_STEP = 0.5f // sürüklemede bir adım = açık şeridin yarısı

    /** [index] şeridinin açıklığı: ortadaki 1, bir adım ötedeki 0; iki şerit arası doğrusal (toplam her an 1). */
    fun openness(index: Int, position: Float, count: Int): Float =
        (1f - abs(SliderMath.offset(index, position, count))).coerceIn(0f, 1f)

    /** Kapalı şerit genişliği: açık şerit ve boşluklardan kalan eşit paylaşılır. */
    fun closedWidth(total: Float, open: Float, gap: Float, count: Int): Float =
        ((total - open - gap * (count - 1)) / (count - 1)).coerceAtLeast(0f)

    /** Şeritlerin sol kenarları [lefts] ve genişlikleri [widths] (aynı birim). */
    fun layout(position: Float, count: Int, total: Float, gap: Float, lefts: FloatArray, widths: FloatArray) {
        val open = total * OPEN_FRACTION
        val closed = closedWidth(total, open, gap, count)
        var x = 0f
        for (i in 0 until count) {
            val w = closed + openness(i, position, count) * (open - closed)
            lefts[i] = x
            widths[i] = w
            x += w + gap
        }
    }

    /** [x] noktasındaki şerit (boşluğa düşerse en yakını). */
    fun stripAt(x: Float, count: Int, lefts: FloatArray, widths: FloatArray): Int {
        for (i in 0 until count) if (x < lefts[i] + widths[i]) return i
        return count - 1
    }

    /** [position]'dan [index] şeridine en kısa yoldan varılacak tam konum. */
    fun targetFor(index: Int, position: Float, count: Int): Float {
        val from = position.roundToInt()
        return from + SliderMath.offset(index, from.toFloat(), count).roundToInt().toFloat()
    }

    fun number(index: Int): String = "%02d".format(Locale.ROOT, index + 1)
}

@Composable
internal fun NartoAccordionCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    val position = remember { Animatable(savedIndex.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val centre by remember(count) { derivedStateOf { SliderMath.index(position.value, count) } }
    val bounce = remember { spring<Float>(dampingRatio = 0.62f, stiffness = 140f) } // yaylı açılış
    val lefts = remember(count) { FloatArray(count) }
    val widths = remember(count) { FloatArray(count) }

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, bounce) }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val density = LocalDensity.current
        val openW = maxWidth * AccordionGeometry.OPEN_FRACTION
        val height = openW * AccordionGeometry.POSTER_RATIO
        val gapPx = with(density) { AccordionGeometry.GAP.dp.toPx() }
        val stepPx = with(density) { (openW * AccordionGeometry.DRAG_STEP).toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Layout(
                content = {
                    items.forEachIndexed { index, item ->
                        AccordionStrip(item, index, count, position::value, openW, height)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .trackTouch { touching = it }
                    .pointerInput(count, stepPx) {
                        val tracker = VelocityTracker()
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                start = position.value
                                tracker.resetTracking()
                                job?.cancel()
                            },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                go(SliderMath.settleTarget(start, position.value, v))
                            },
                            onDragCancel = { go(SliderMath.settleTarget(start, position.value, 0f)) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { position.snapTo(position.value - dx / stepPx) }
                        }
                    }
                    .pointerInput(items, gapPx) {
                        detectTapGestures { at ->
                            AccordionGeometry.layout(position.value, count, size.width.toFloat(), gapPx, lefts, widths)
                            val hit = AccordionGeometry.stripAt(at.x, count, lefts, widths)
                            if (hit == centre && AccordionGeometry.openness(hit, position.value, count) > 0.5f) {
                                onOpen(items[hit])
                            } else {
                                go(AccordionGeometry.targetFor(hit, position.value, count))
                            }
                        }
                    },
            ) { measurables, constraints ->
                // Konum yalnız yerleşimde okunur: şerit genişlikleri her karede yeniden ölçülür, yeniden oluşturma yok.
                val total = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight
                AccordionGeometry.layout(position.value, count, total, gapPx, lefts, widths)
                val placeables = measurables.mapIndexed { i, m ->
                    val l = lefts[i].roundToInt()
                    val r = (lefts[i] + widths[i]).roundToInt()
                    m.measure(Constraints.fixed((r - l).coerceAtLeast(0), h))
                }
                layout(constraints.maxWidth, h) {
                    placeables.forEachIndexed { i, p -> p.place(lefts[i].roundToInt(), 0) }
                }
            }
            Crossfade(targetState = centre, animationSpec = tween(300), label = "accordionTitle") { i ->
                Text(
                    text = items[i].title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 12.dp, end = 12.dp),
                )
            }
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        }
    }
}

/**
 * Tek şerit: afiş açık şerit ölçüsünde ortalanır ve kırpılır (genişlerken afiş ölçeklenmez, ortadan açılır). Kapalıyken
 * tema yüzeyinin örtüsü, üstte sıra numarası ve dikey başlık; açıldıkça örtü söner, şerit boyca uzar, gölge ve altta
 * `primary` çizgi belirir.
 */
@Composable
private fun AccordionStrip(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    openW: Dp,
    height: Dp,
) {
    val shape = remember { RoundedCornerShape(12.dp) }
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val shadowPx = with(LocalDensity.current) { 10.dp.toPx() }
    Box(
        Modifier
            .graphicsLayer {
                val o = AccordionGeometry.openness(index, position(), count)
                scaleY = 0.9f + 0.1f * o
                shadowElevation = shadowPx * o
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.requiredSize(openW, height),
        )
        // Kapalı görünüm: örtü + numara + dikey başlık (açıldıkça söner).
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - AccordionGeometry.openness(index, position(), count) }
                .background(
                    Brush.verticalGradient(listOf(surface.copy(alpha = 0.55f), surface.copy(alpha = 0.82f))),
                ),
        ) {
            Text(
                text = AccordionGeometry.number(index),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
            Text(
                text = item.title,
                color = onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .verticalUpward(maxLength = height - 48.dp),
            )
        }
        // Açık şeridin altındaki tema çizgisi.
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val o = AccordionGeometry.openness(index, position(), count)
                    if (o > 0.01f) {
                        val h = 4.dp.toPx()
                        val w = size.width * 0.4f * o
                        drawRect(accent.copy(alpha = o), Offset((size.width - w) / 2f, size.height - h), Size(w, h))
                    }
                },
        )
    }
}

/** Metni 90° sola çevirir (aşağıdan yukarı okunur); yerleşimde en ve boy yer değiştirir. */
private fun Modifier.verticalUpward(maxLength: Dp): Modifier = this
    .layout { measurable, _ ->
        val p = measurable.measure(Constraints(maxWidth = maxLength.roundToPx().coerceAtLeast(0)))
        layout(p.height, p.width) {
            p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2)
        }
    }
    .graphicsLayer { rotationZ = -90f }
