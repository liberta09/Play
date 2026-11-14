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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3B küp (VerticalShorts): afişler dönen bir küpün yan yüzlerinde. Geçişte küp düşey ekseni çevresinde
 * 90° döner: giden yüz sağ/sol kenarından menteşelenip derine kaçar, gelen yüz ortak kenardan açılır (iki yüz kenarda
 * birleşir); dönerken yüzler uzak kenara doğru kararır, küp hafifçe geri çekilir, altta yumuşak yer gölgesi. Sürüklerken
 * küp parmağı izler. Birimler sahne birimi (sahne 400 birim genişlik).
 */
internal object CubeGeometry {
    const val STAGE = 400f
    const val FACE_W = 236f
    const val FACE_H = 354f
    const val MARGIN = 26f // yüzün üstünde ve altında (yer gölgesi payı)
    const val STAGE_H = FACE_H + 2 * MARGIN
    const val CAMERA = 2.6f // kamera uzaklığı / yüz genişliği
    const val DIP = 0.12f // dönüş ortasında küpün geri çekilmesi
    const val SHADE = 0.6f // 90°'deki yüzün uzak kenar kararması
    const val TAP_SIDE = FACE_W / 2f

    /** Yüzün dönüş yönü: Compose'ta artı `rotationY` sağ kenarı derine götürür (küp dışarıdan görünür). */
    const val TURN = 90f

    /** [q] uzaklığındaki yüz görünür mü (giden ya da gelen). */
    fun visible(q: Float): Boolean = abs(q) < 1f

    /** Yüzün yatay kayması (yüz genişliği cinsinden): ortak kenar parmakla birlikte kayar. */
    fun shift(q: Float): Float = q

    /** Menteşe: soldaki (giden) yüz sağ kenarından, sağdaki (gelen) sol kenarından döner. */
    fun pivotX(q: Float): Float = if (q < 0f) 1f else 0f

    fun rotation(q: Float): Float = TURN * q

    /** Dönüş ortasında küp [DIP] kadar küçülür (geri çekilme), dururken tam boy. */
    fun recede(position: Float): Float = 1f - DIP * sin(PI.toFloat() * (position - floor(position)))

    /** Yüz kararması: 0 dik, 90°'de [SHADE]. */
    fun shade(q: Float): Float = SHADE * abs(q).coerceAtMost(1f)
}

@Composable
internal fun VerticalShortsCubeCarousel(
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
    val roll = remember { spring<Float>(dampingRatio = 0.8f, stiffness = 95f) }
    val floorShadow = MaterialTheme.colorScheme.onBackground

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, roll) }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val unit = maxWidth / CubeGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * CubeGeometry.STAGE_H)
                    .drawBehind {
                        // Yer gölgesi: küp geri çekildikçe daralır.
                        val s = CubeGeometry.recede(position.value)
                        val w = CubeGeometry.FACE_W * unitPx * 1.25f * s
                        val h = CubeGeometry.MARGIN * unitPx * 1.3f
                        val cy = (CubeGeometry.MARGIN + CubeGeometry.FACE_H) * unitPx + h * 0.05f
                        scale(1f, h / w, Offset(size.width / 2f, cy)) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(floorShadow.copy(alpha = 0.26f), floorShadow.copy(alpha = 0.08f), Color.Transparent),
                                    center = Offset(size.width / 2f, cy),
                                    radius = w / 2f,
                                ),
                                radius = w / 2f,
                                center = Offset(size.width / 2f, cy),
                            )
                        }
                    }
                    .trackTouch { touching = it }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val stepPx = unitPx * CubeGeometry.FACE_W
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
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val step = SliderMath.tapStep((at.x - size.width / 2f) / unitPx, CubeGeometry.TAP_SIDE)
                            if (step == 0) onOpen(items[centre]) else go(position.value.roundToInt() + step.toFloat())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Küp: dönüş ortasında bütün olarak geri çekilir (yüzlerin ortak kenarı bozulmaz).
                Box(
                    Modifier
                        .size(unit * CubeGeometry.FACE_W, unit * CubeGeometry.FACE_H)
                        .graphicsLayer {
                            val s = CubeGeometry.recede(position.value)
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    items.forEachIndexed { index, item ->
                        CubeFace(item, index, count, position::value, unitPx)
                    }
                }
            }
            Crossfade(targetState = centre, animationSpec = tween(300), label = "cubeTitle") { i ->
                Text(
                    text = items[i].title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        }
    }
}

/** Tek yüz: yalnız giden ve gelen yüz çizilir; konum, menteşe, dönüş ve kararma çizim evresinde okunur. */
@Composable
private fun CubeFace(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    unitPx: Float,
) {
    val shape = remember { RoundedCornerShape(6.dp) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val q = SliderMath.offset(index, position(), count)
                if (!CubeGeometry.visible(q)) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = 1f
                translationX = CubeGeometry.shift(q) * size.width
                transformOrigin = TransformOrigin(CubeGeometry.pivotX(q), 0.5f)
                rotationY = CubeGeometry.rotation(q)
                cameraDistance = CubeGeometry.CAMERA * CubeGeometry.FACE_W * unitPx / 72f
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                val q = SliderMath.offset(index, position(), count)
                val a = CubeGeometry.shade(q)
                if (a > 0.01f && CubeGeometry.visible(q)) {
                    // Uzak kenara doğru koyulaşan gölge; ortak (yakın) kenarda ince ışık.
                    val far = if (q > 0f) size.width else 0f
                    val near = size.width - far
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = a * 0.25f), Color.Black.copy(alpha = a)),
                            startX = near,
                            endX = far,
                        ),
                    )
                    val edge = 1.5.dp.toPx()
                    drawRect(
                        Color.White.copy(alpha = 0.35f * (1f - abs(q))),
                        Offset(if (q > 0f) 0f else size.width - edge, 0f),
                        Size(edge, size.height),
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
