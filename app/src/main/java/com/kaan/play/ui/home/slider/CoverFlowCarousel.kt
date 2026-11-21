package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Kapak akışı (FullDrama): ortadaki afiş düz ve büyük; yanlardakiler ortaya bakacak biçimde 3B açıyla
 * dönük, geriye itilmiş ve sık dizili (ortaya yakın olan üstte); her afişin altında aynada silik yansıması. Kaydırma
 * parmağı izler, bırakınca yaylı oturur; hızlı fiske birkaç kapak birden geçer. Ölçüler sahne genişliğinin kesri.
 */
internal object CoverFlowGeometry {
    const val CARD_W = 0.5f
    const val CARD_H = CARD_W * 1.5f
    const val TOP = 0.03f
    const val REFLECT = 0.15f // yansımanın görünen boyu
    const val SCENE_H = TOP + CARD_H + REFLECT + 0.01f
    const val ROT = 62f // yan kapakların dönüşü
    const val X1 = 0.34f // ilk yan kapağın merkeze uzaklığı
    const val GAP = 0.085f // sonraki yan kapaklar arası
    const val SIDE_SCALE = 0.78f
    const val HALF = 3.5f // her yanda çizilen en çok kapak
    const val FLICK = 3f // bu hızdan (kapak/sn) sonra fiske birkaç kapak geçer
    const val FLICK_TIME = 0.3f
    const val FLICK_MAX = 4

    data class Pose(val x: Float, val rotation: Float, val scale: Float, val alpha: Float)

    /** Merkeze [q] uzaklığındaki kapağın duruşu; x sahne merkezine göre, dönüş derece (sağdakiler +). */
    fun pose(q: Float, count: Int): Pose {
        val a = abs(q)
        val s = sign(q)
        val k = a.coerceAtMost(1f)
        // İlk adımda yumuşak çıkış (merkezden ayrılırken hızlanır, yana yaklaşınca yavaşlar).
        val e = k * k * (3f - 2f * k)
        val x = if (a <= 1f) X1 * (0.35f * k + 0.65f * e) else X1 + (a - 1f) * GAP
        val limit = minOf(HALF, count / 2f)
        return Pose(
            x = s * x,
            rotation = s * ROT * e,
            scale = 1f - (1f - SIDE_SCALE) * e,
            alpha = ((limit - a) * 2f).coerceIn(0f, 1f),
        )
    }

    /** Bırakınca gidilecek kapak: yavaşsa ortak eşik, hızlı fiskede hızla orantılı (en çok [FLICK_MAX]). */
    fun settle(start: Float, now: Float, velocity: Float): Float {
        if (abs(velocity) < FLICK) return SliderMath.settleTarget(start, now, velocity)
        val from = start.roundToInt()
        val to = (now + velocity * FLICK_TIME).roundToInt().coerceIn(from - FLICK_MAX, from + FLICK_MAX)
        return to.toFloat()
    }

    /** Merkeze göre dokunulan yer [u] (genişlik kesri): 0 ortadaki, ±n o yandaki n. kapak. */
    fun tapStep(u: Float): Int {
        val a = abs(u)
        if (a <= CARD_W / 2f) return 0
        val n = (((a - X1) / GAP).roundToInt() + 1).coerceIn(1, 3)
        return if (u > 0f) n else -n
    }
}

@Composable
internal fun CoverFlowCarousel(
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
    // Çizim sırası (ortaya yakın üstte): yalnız sıra değişince yeniden oluşturur.
    val layers by remember(count) {
        derivedStateOf { List(count) { (-abs(SliderMath.offset(it, position.value, count)) * 2f).roundToInt() } }
    }
    val scheme = MaterialTheme.colorScheme
    val glow = scheme.primary

    fun go(target: Float, velocity: Float = 0f) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch {
            if (reduceMotion) position.snapTo(target)
            else position.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 170f), initialVelocity = velocity)
        }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        val wPx = with(LocalDensity.current) { w.toPx() }
        val cardW = w * CoverFlowGeometry.CARD_W
        val cardH = w * CoverFlowGeometry.CARD_H
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(w * CoverFlowGeometry.SCENE_H)
                    .clipToBounds()
                    .drawBehind {
                        // Ortadaki kapağın arkasında tema renginde yumuşak ışık; altta parlak zemin çizgisi.
                        val cy = (CoverFlowGeometry.TOP + CoverFlowGeometry.CARD_H * 0.5f) * size.width
                        drawRect(
                            Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(size.width / 2f, cy),
                                radius = size.width * 0.55f,
                            ),
                        )
                        val floor = (CoverFlowGeometry.TOP + CoverFlowGeometry.CARD_H) * size.width
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, glow.copy(alpha = 0.35f), Color.Transparent),
                            ),
                            topLeft = Offset(0f, floor),
                            size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                        )
                    }
                    .trackTouch { touching = it }
                    .pointerInput(count, wPx) {
                        val tracker = VelocityTracker()
                        val stepPx = wPx * CoverFlowGeometry.X1
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                job?.cancel()
                                start = position.value
                                tracker.resetTracking()
                            },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                go(CoverFlowGeometry.settle(start, position.value, v), v)
                            },
                            onDragCancel = { go(SliderMath.settleTarget(start, position.value, 0f)) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { position.snapTo(position.value - dx / stepPx) }
                        }
                    }
                    .pointerInput(items, wPx) {
                        detectTapGestures { at ->
                            val step = CoverFlowGeometry.tapStep((at.x - size.width / 2f) / wPx)
                            if (step != 0) go(position.value.roundToInt() + step.toFloat())
                            else if (abs(position.value - position.value.roundToInt()) < 0.15f) onOpen(items[centre])
                            else go(position.value.roundToInt().toFloat())
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                items.forEachIndexed { index, item ->
                    CoverWithReflection(
                        item = item,
                        cardH = cardH,
                        modifier = Modifier
                            .zIndex(layers[index].toFloat())
                            .padding(top = w * CoverFlowGeometry.TOP)
                            .size(cardW, cardH + w * CoverFlowGeometry.REFLECT)
                            .graphicsLayer {
                                val p = CoverFlowGeometry.pose(SliderMath.offset(index, position.value, count), count)
                                translationX = p.x * wPx
                                rotationY = p.rotation
                                scaleX = p.scale
                                scaleY = p.scale
                                alpha = p.alpha
                                // Döndürme ve ölçek kapağın orta hattından (yansıma onunla birlikte döner).
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    0.5f,
                                    (cardH / 2f) / (cardH + w * CoverFlowGeometry.REFLECT),
                                )
                                cameraDistance = 4.5f * cardW.toPx() / 72f
                            },
                    )
                }
            }
            Text(
                text = items[centre].title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        }
    }
}

/** Kapak + altında aynadaki silik yansıması (aşağı doğru söner). */
@Composable
private fun CoverWithReflection(item: CatalogItem, cardH: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardH)
                .graphicsLayer {
                    shadowElevation = 12.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .graphicsLayer {
                    scaleY = -1f
                    alpha = 0.42f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Ayna sönümü: kapağa yakın kısım görünür, aşağı doğru kaybolur (çevrilmeden önceki koordinatta alt).
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 1f to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
                    .requiredHeight(cardH)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}
