package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Saçılmış anlık fotoğraflar (İzleGeç): masaya farklı açılarla atılmış beyaz çerçeveli anlık
 * fotoğraflar, altlarında el yazısıyla dizi adı. Öndeki büyük ve düz; geçişte öndeki sola savrulur, yarı yolda yığının
 * altına geçer ve kendi açısıyla yığının dibine yerleşir; sıradaki düzelip öne gelir. Ölçüler sahne genişliğinin kesri.
 */
internal object SnapshotsGeometry {
    const val FRAME_W = 0.56f
    const val BORDER = 0.032f
    const val PHOTO_W = FRAME_W - 2 * BORDER
    const val PHOTO_H = PHOTO_W * 4f / 3f
    const val CAPTION = 0.13f
    const val FRAME_H = BORDER + PHOTO_H + CAPTION
    const val SCENE_H = 1.0f
    const val CENTER_Y = 0.49f
    const val BEHIND_SCALE = 0.84f
    const val DEEP_SCALE = 0.025f // her derin kat biraz daha küçük
    const val VISIBLE = 5 // önün arkasında görünen en çok fotoğraf
    const val FLING_X = 0.62f // savrulma: öndeki bu kadar yana gider
    const val FLING_ROT = -22f
    const val STEP = 1.2f // sürüklemede bir fotoğraf = 1,2 sahne genişliği

    // Her fotoğrafın masadaki kendi açısı ve kayması (dağınık ama dengeli).
    private val ANGLE = floatArrayOf(-11f, 8f, -5f, 13f, -14f, 6f, -8f, 11f, -3f, 12f)
    private val DX = floatArrayOf(-0.17f, 0.15f, -0.08f, 0.2f, -0.21f, 0.1f, -0.13f, 0.18f, -0.05f, 0.13f)
    private val DY = floatArrayOf(0.03f, -0.04f, 0.05f, 0.02f, -0.02f, 0.04f, -0.05f, 0.01f, 0.05f, -0.03f)

    data class Pose(val x: Float, val y: Float, val rotation: Float, val scale: Float, val alpha: Float)

    /** Yığındaki derinlik: 0 öndeki, count−1'den büyükse öndeki savruluyor (ya da geri geliyor). */
    fun depth(index: Int, position: Float, count: Int): Float {
        val d = (index - position) % count
        return if (d < 0f) d + count else d
    }

    /** [depth] (0..count−1) katındaki [index] fotoğrafının duruşu; x, y sahne merkezine göre. */
    fun rest(index: Int, depth: Float): Pose {
        val k = depth.coerceIn(0f, 1f)
        val deep = (depth - 1f).coerceIn(0f, 3f)
        val i = Math.floorMod(index, ANGLE.size)
        return Pose(
            x = DX[i] * k * (1f + 0.12f * deep),
            y = DY[i] * k,
            rotation = ANGLE[i] * k,
            scale = 1f - (1f - BEHIND_SCALE) * k - DEEP_SCALE * deep,
            alpha = (VISIBLE + 1f - depth).coerceIn(0f, 1f),
        )
    }

    /** Fotoğrafın duruşu; öndeki savrulurken yay çizerek yana gider, yarı yolda yığının altına geçer. */
    fun pose(index: Int, position: Float, count: Int): Pose {
        val d = depth(index, position, count)
        if (d <= count - 1f) return rest(index, d)
        val u = count - d // 0: önde, 1: yığının dibinde
        val a = rest(index, 0f)
        val b = rest(index, count - 1f)
        val arc = sin(PI.toFloat() * u)
        fun mix(p: Float, q: Float) = p + (q - p) * u
        return Pose(
            x = mix(a.x, b.x) - FLING_X * arc,
            y = mix(a.y, b.y) - 0.05f * arc,
            rotation = mix(a.rotation, b.rotation) + FLING_ROT * arc,
            scale = mix(a.scale, b.scale) * (1f + 0.04f * arc),
            alpha = if (u < 0.5f) 1f else 1f + (b.alpha - 1f) * (u - 0.5f) * 2f,
        )
    }

    /** Çizim sırası (büyük önde): savrulan ilk yarıda en üstte, ikinci yarıda en altta. */
    fun layer(index: Int, position: Float, count: Int): Int {
        val d = depth(index, position, count)
        if (d > count - 1f) return if (count - d < 0.5f) count + 1 else 0
        return count - d.toInt()
    }
}

private val TossEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
private val FramePaper = Color(0xFFFBFAF6)
private val FrameEdge = Color(0xFFEDEAE2)
private val PenInk = Color(0xFF2B3050)

@Composable
internal fun SnapshotsCarousel(
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
    // Çizim sırası yalnız sıra değişince yeniden oluşturur (kare başına değil).
    val layers by remember(count) {
        derivedStateOf { List(count) { SnapshotsGeometry.layer(it, position.value, count) } }
    }
    val scheme = MaterialTheme.colorScheme
    val (tableFar, tableNear) = SliderColors.table(scheme.surface, scheme.surfaceVariant)

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch {
            if (reduceMotion) position.snapTo(target)
            else position.animateTo(target, tween((900 * abs(target - position.value).coerceIn(0.4f, 1.6f)).roundToInt(), easing = TossEasing))
        }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val w = maxWidth
        val wPx = with(LocalDensity.current) { w.toPx() }
        val fontSize = with(LocalDensity.current) { (w * 0.05f).toSp() }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(tableFar, tableNear)))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(w * SnapshotsGeometry.SCENE_H)
                    .trackTouch { touching = it }
                    .pointerInput(count, wPx) {
                        val tracker = VelocityTracker()
                        val stepPx = wPx * SnapshotsGeometry.STEP
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                job?.cancel()
                                start = position.value
                                tracker.resetTracking()
                            },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                go(SliderMath.settleTarget(start, position.value, v * 2f))
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
                            val u = (at.x - size.width / 2f) / wPx
                            val step = SliderMath.tapStep(u, SnapshotsGeometry.FRAME_W / 2f)
                            if (step != 0) go(position.value.roundToInt() + step.toFloat())
                            else if (abs(position.value - position.value.roundToInt()) < 0.15f) onOpen(items[centre])
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                items.forEachIndexed { index, item ->
                    Snapshot(
                        item = item,
                        modifier = Modifier
                            .zIndex(layers[index].toFloat())
                            .size(w * SnapshotsGeometry.FRAME_W, w * SnapshotsGeometry.FRAME_H)
                            .graphicsLayer {
                                val p = SnapshotsGeometry.pose(index, position.value, count)
                                translationX = p.x * wPx
                                translationY = (SnapshotsGeometry.CENTER_Y + p.y) * wPx - size.height / 2f
                                rotationZ = p.rotation
                                scaleX = p.scale
                                scaleY = p.scale
                                alpha = p.alpha
                                val front = (1f - SnapshotsGeometry.depth(index, position.value, count)).coerceIn(0f, 1f)
                                shadowElevation = (4f + 10f * front) * density
                                shape = RectangleShape
                            },
                        border = w * SnapshotsGeometry.BORDER,
                        photoHeight = w * SnapshotsGeometry.PHOTO_H,
                        fontSize = fontSize,
                    )
                }
            }
            SliderDots(count = count, current = centre, modifier = Modifier.padding(top = 2.dp, bottom = 18.dp))
        }
    }
}

/** Tek anlık fotoğraf: beyaz kâğıt çerçeve, 3:4 afiş, altta el yazısı başlık. */
@Composable
private fun Snapshot(
    item: CatalogItem,
    modifier: Modifier,
    border: androidx.compose.ui.unit.Dp,
    photoHeight: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(FramePaper, FrameEdge)))
            .padding(start = border, end = border, top = border),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(photoHeight)
                .background(Color(0xFF26262B)),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Anlık fotoğraf parlaklığı: üstten hafif ışık, kenarlarda yumuşak kararma.
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.12f))))
                    },
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = item.title,
                color = PenInk,
                fontSize = fontSize,
                lineHeight = fontSize * 1.05f,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { rotationZ = -1.5f },
            )
        }
    }
}
