package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Film şeridi (AnyShort): kenarları delikli koyu film negatifi yatay akar, her karede bir afiş. Şerit
 * kesintisiz tek bant olarak çizilir (delikler ve kare çizgileri konumla birlikte kayar); kayarken bant hıza göre
 * perspektifte eğilir, durunca yaylı biçimde düzelir. Ortadaki kare büyür ve parlar, yanlar loş ve biraz küçük; bandın
 * uçları temanın zeminine söner. Birimler sahne birimi (sahne 360 birim genişlik).
 */
internal object FilmStripGeometry {
    const val STAGE = 360f
    const val STEP = 136f // bir kare genişliği
    const val POSTER_W = 112f
    const val POSTER_H = 168f
    const val BAND = 24f // delik şeridi (üst ve alt)
    const val STRIP_H = POSTER_H + 2 * BAND + 8f
    const val TOP = 22f
    const val SCENE_H = STRIP_H + 2 * TOP
    const val HOLES = 8 // kare başına delik
    const val HOLE_GAP = STEP / HOLES
    const val HOLE_W = 9f
    const val HOLE_H = 12f
    const val GROW = 0.13f // ortadaki afişin büyümesi
    const val SHRINK = 0.06f // yanların küçülmesi (kare başına, en çok bir kare)
    const val DIM = 0.55f // yanların kararması
    const val MAX_Q = 2.6f
    const val MAX_TILT = 13f // kayarken bandın en çok eğilmesi (derece)
    private const val TILT_PER_SPEED = 5f // derece / (kare/sn)

    /** Kayan bandın perspektif eğimi (rotationY, derece): hıza ([speed], kare/sn) orantılı, sınırlı; dururken 0. */
    fun tilt(speed: Float): Float = (-speed * TILT_PER_SPEED).coerceIn(-MAX_TILT, MAX_TILT)

    /**
     * [position]'daki şeritte sol kenarı [from] (birim, ortaya göre) noktasında ya da hemen solunda olan delik; çizim buradan
     * başlar. Delikler kare ızgarasıyla birlikte kayar: k. delik = kare sol kenarı + (k + ½)·aralık − delik/2.
     */
    fun firstHole(position: Float, from: Float): Float {
        val origin = -position * STEP - STEP / 2f + HOLE_GAP / 2f - HOLE_W / 2f
        val k = floor((from - origin) / HOLE_GAP)
        return origin + k * HOLE_GAP
    }

    /** Ortaya göre ilk kare sınırının x'i (birim), [from] solunda kalmayan. */
    fun firstEdge(position: Float, from: Float): Float {
        val origin = -position * STEP - STEP / 2f
        return origin + ceil((from - origin) / STEP) * STEP
    }

    /** Ortaya yakınlık: ortada 1, bir kare ötede 0. */
    fun focus(q: Float): Float = (1f - abs(q)).coerceIn(0f, 1f)

    fun scale(q: Float): Float = 1f + GROW * focus(q) - SHRINK * abs(q).coerceAtMost(1f)

    /** Az karede karşı kenara atlayan kare görünmeden atlasın; çok karede en çok ±[MAX_Q]. */
    fun alpha(q: Float, count: Int): Float = ((minOf(MAX_Q, count / 2f) - abs(q)) * 4f).coerceIn(0f, 1f)
}

private val FilmBase = Color(0xFF17130F)
private val FilmEdge = Color(0xFF2A231C)
private val FilmHole = Color(0xFFEDE6D6)
private val FilmAmber = Color(0xFFE2A54A)
private val EdgeMask = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.15f to Color.Black,
    0.85f to Color.Black,
    1f to Color.Transparent,
)

@Composable
internal fun FilmStripCarousel(
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
    var dragSpeed by remember { mutableStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val centre by remember(count) { derivedStateOf { SliderMath.index(position.value, count) } }
    val springSpec = remember { spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow) }
    val glow = MaterialTheme.colorScheme.primary

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        dragSpeed = 0f
        job?.cancel()
        job = scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, springSpec) }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val unit = maxWidth / FilmStripGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        // Hız: yaylı oturma sırasında Animatable'dan, sürüklerken izleyiciden (kare/sn).
        val speed: () -> Float = { if (position.isRunning) position.velocity else dragSpeed }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * FilmStripGeometry.SCENE_H)
                    .clipToBounds()
                    .trackTouch { touching = it }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val stepPx = unitPx * FilmStripGeometry.STEP
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
                            dragSpeed = -tracker.calculateVelocity().x / stepPx
                            scope.launch { position.snapTo(position.value - dx / stepPx) }
                        }
                    }
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val step = SliderMath.tapStep((at.x - size.width / 2f) / unitPx, FilmStripGeometry.STEP / 2f)
                            if (step == 0) onOpen(items[centre]) else go(position.value.roundToInt() + step.toFloat())
                        }
                    }
                    // Bandın uçları saydamlaşarak sayfanın zeminine karışır (renk değil, saydamlık maskesi).
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(EdgeMask, blendMode = BlendMode.DstIn)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Tek katman: bant + kareler birlikte hıza göre perspektifte eğilir.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(unit * FilmStripGeometry.SCENE_H)
                        .graphicsLayer {
                            rotationY = FilmStripGeometry.tilt(speed())
                            cameraDistance = 9f * density
                        }
                        .drawBehind { drawFilmBand(position.value, unitPx) },
                    contentAlignment = Alignment.Center,
                ) {
                    items.forEachIndexed { index, item ->
                        FilmFrame(
                            item = item,
                            index = index,
                            count = count,
                            position = position::value,
                            unit = unit,
                            unitPx = unitPx,
                            glow = glow,
                        )
                    }
                }
            }
            Text(
                text = items[centre].title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Kesintisiz film bandı: koyu taban, üst/alt delik sıraları ve kare sınırlarındaki kehribar çizgiler konumla kayar. */
private fun DrawScope.drawFilmBand(position: Float, unitPx: Float) {
    val h = FilmStripGeometry.STRIP_H * unitPx
    val top = (size.height - h) / 2f
    val cx = size.width / 2f
    val half = size.width / 2f / unitPx
    drawRect(
        Brush.verticalGradient(0f to FilmEdge, 0.12f to FilmBase, 0.88f to FilmBase, 1f to FilmEdge, startY = top, endY = top + h),
        topLeft = Offset(0f, top),
        size = Size(size.width, h),
    )
    val band = FilmStripGeometry.BAND * unitPx
    val hw = FilmStripGeometry.HOLE_W * unitPx
    val hh = FilmStripGeometry.HOLE_H * unitPx
    val r = CornerRadius(2.2f * unitPx)
    var u = FilmStripGeometry.firstHole(position, -half - FilmStripGeometry.HOLE_W)
    while (u < half) {
        val x = cx + u * unitPx
        drawRoundRect(FilmHole, Offset(x, top + (band - hh) / 2f), Size(hw, hh), r, alpha = 0.92f)
        drawRoundRect(FilmHole, Offset(x, top + h - (band + hh) / 2f), Size(hw, hh), r, alpha = 0.92f)
        u += FilmStripGeometry.HOLE_GAP
    }
    var e = FilmStripGeometry.firstEdge(position, -half)
    while (e < half) {
        drawRect(FilmAmber, Offset(cx + e * unitPx, top + band + 2f * unitPx), Size(0.8f * unitPx, h - 2 * band - 4f * unitPx), alpha = 0.35f)
        e += FilmStripGeometry.STEP
    }
}

/** Tek kare (afiş). Konum yalnız yerleşim/çizim evresinde okunur (her karede yeniden oluşturma yok); ortadaki en üstte. */
@Composable
private fun FilmFrame(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    unit: Dp,
    unitPx: Float,
    glow: Color,
) {
    val shape = RoundedCornerShape(unit * 3f)
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(index, count, unitPx, shape) {
        {
            val q = SliderMath.offset(index, position(), count)
            val a = FilmStripGeometry.alpha(q, count)
            alpha = a
            if (a > 0f) {
                val s = FilmStripGeometry.scale(q)
                translationX = q * FilmStripGeometry.STEP * unitPx
                scaleX = s
                scaleY = s
                shadowElevation = 8f * unitPx * FilmStripGeometry.focus(q)
                this.shape = shape
                clip = true
            }
        }
    }
    Box(
        Modifier
            .size(unit * FilmStripGeometry.POSTER_W, unit * FilmStripGeometry.POSTER_H)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val q = SliderMath.offset(index, position(), count)
                    placeable.placeWithLayer(0, 0, zIndex = -abs(q), layerBlock = layerBlock)
                }
            }
            .background(FilmEdge)
            .drawWithContent {
                drawContent()
                val f = FilmStripGeometry.focus(SliderMath.offset(index, position(), count))
                // Yanlar loş; ortadaki kare temanın vurgu rengiyle ince bir parıltı çerçevesi ve üstten hafif ışık alır.
                if (f < 1f) drawRect(Color.Black, alpha = FilmStripGeometry.DIM * (1f - f))
                if (f > 0f) {
                    val w = 2.2f * unitPx
                    drawRect(glow, topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w), style = Stroke(w), alpha = 0.9f * f)
                    drawRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.45f),
                        alpha = f,
                    )
                }
            },
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
