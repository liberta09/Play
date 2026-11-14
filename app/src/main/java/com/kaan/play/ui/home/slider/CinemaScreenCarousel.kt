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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sinema perdesi (DirectDrama): sahnede koyu kırmızı kadife perde, perdenin ardındaki ekranda afiş.
 * Geçiş: perde iki yandan ortaya kapanır → afiş değişir → perde açılır (yeni afiş hafifçe yaklaşır). Sürüklerken perde
 * parmağı izleyerek kapanmaya başlar. Altta silik koltuk sırası ve koridor ışıkları. Birimler sahne birimi (360 genişlik).
 */
internal object CinemaGeometry {
    const val STAGE = 360f
    const val SCENE_H = 404f
    const val VALANCE = 44f // üst saçak
    const val FLOOR = 74f // alttaki koltuk alanı
    const val POSTER_W = 150f
    const val POSTER_H = 225f
    const val POSTER_TOP = VALANCE + 22f
    const val TAP_SIDE = POSTER_W / 2f
    const val FOLDS = 7 // perde kanadı başına kıvrım
    const val CLOSE_MS = 430
    const val HOLD_MS = 140
    const val OPEN_MS = 760
    const val DRAG_CLOSE = 0.75f // sürüklemede perdenin en çok kapanması

    // Açık perdenin iç kenarı (sahne genişliğinin oranı): üstte geniş, bağda dar, altta biraz açılır.
    private const val OPEN_TOP = 0.2f
    private const val OPEN_TIE = 0.085f
    private const val OPEN_BOTTOM = 0.13f
    const val TIE_Y = 0.62f
    private const val CLOSED = 0.505f // iki kanat ortada az üst üste biner

    /**
     * Sol kanadın iç kenarı [y] yüksekliğinde (0 üst, 1 alt; perde yüksekliğine oran), [close] 0 açık … 1 kapalı. Sağ kanat
     * aynadır. Açık şekil üst–bağ–alt arasında yumuşak (kare) geçer.
     */
    fun innerEdge(y: Float, close: Float): Float {
        val open = if (y <= TIE_Y) {
            val t = y / TIE_Y
            OPEN_TOP + (OPEN_TIE - OPEN_TOP) * (2f * t - t * t)
        } else {
            val t = (y - TIE_Y) / (1f - TIE_Y)
            OPEN_TIE + (OPEN_BOTTOM - OPEN_TIE) * t * t
        }
        val c = close.coerceIn(0f, 1f)
        return open + (CLOSED - open) * c
    }

    /** Kısmen kapalı perde kalan yol oranında kapanır / açılır. */
    fun closeMs(close: Float): Int = (CLOSE_MS * (1f - close.coerceIn(0f, 1f))).roundToInt()
    fun openMs(close: Float): Int = (OPEN_MS * close.coerceIn(0f, 1f)).roundToInt()

    /** Sürükleme ([dx] sahne birimi) perdeyi ne kadar kapatır. */
    fun dragClose(dx: Float): Float = (abs(dx) / (STAGE * 0.45f)).coerceIn(0f, DRAG_CLOSE)

    /** Afiş perde açıldıkça hafifçe yaklaşır. */
    fun posterScale(close: Float): Float = 1f - 0.07f * close.coerceIn(0f, 1f)
}

private val VelvetDeep = Color(0xFF3E070D)
private val VelvetMid = Color(0xFF741422)
private val VelvetLight = Color(0xFFA42638)
private val Gold = Color(0xFFCFA54E)
private val AisleLight = Color(0xFFFFC56B)
private val CinemaEasing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

@Composable
internal fun CinemaScreenCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val close = remember { Animatable(0f) }
    var drag by remember { mutableStateOf(0f) } // sürükleme (sahne birimi)
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > 0.5f
    val (wallFar, wallNear) = SliderColors.table(scheme.surface, scheme.surfaceVariant)
    val seat = lerp(wallFar, Color.Black, if (light) 0.55f else 0.6f)

    fun go(step: Int) {
        drag = 0f
        job?.cancel()
        job = scope.launch {
            if (step != 0) {
                if (reduceMotion) {
                    index = Math.floorMod(index + step, count)
                    close.snapTo(0f)
                    return@launch
                }
                close.animateTo(1f, tween(CinemaGeometry.closeMs(close.value), easing = CinemaEasing))
                index = Math.floorMod(index + step, count)
                delay(CinemaGeometry.HOLD_MS.toLong())
            }
            close.animateTo(0f, tween(CinemaGeometry.openMs(close.value), easing = CinemaEasing))
        }
    }

    AutoAdvance(paused = touching) { go(+1) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val unit = maxWidth / CinemaGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        // Satır daha az kartla yeniden yüklenirse saklı sıra taşmasın.
        val item = items[index % count]
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * CinemaGeometry.SCENE_H)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(wallFar, wallNear)))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .trackTouch { touching = it }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                job?.cancel()
                                drag = 0f
                            },
                            onDragEnd = {
                                // Sahne genişliğinin yarısı bir "öğe" sayılır; eşik ve fiske ortak kuraldan.
                                val per = CinemaGeometry.STAGE * 0.5f
                                val v = -tracker.calculateVelocity().x / unitPx / per
                                val step = SliderMath.settleTarget(0f, -drag / per, v).roundToInt().coerceIn(-1, 1)
                                go(step)
                            },
                            onDragCancel = { go(0) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            drag += dx / unitPx
                            scope.launch { close.snapTo(CinemaGeometry.dragClose(drag)) }
                        }
                    }
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val step = SliderMath.tapStep((at.x - size.width / 2f) / unitPx, CinemaGeometry.TAP_SIDE)
                            when {
                                step != 0 -> go(step)
                                close.value < 0.3f -> onOpen(items[index % count])
                                else -> go(0)
                            }
                        }
                    }
                    .drawWithCache {
                        val shade = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.25f to Color.Transparent,
                            0.8f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.35f),
                        )
                        val path = Path()
                        onDrawWithContent {
                            drawContent()
                            drawCurtains(path, VelvetFolds, shade, close.value, unitPx)
                            drawValance(unitPx)
                            drawSeats(seat, unitPx)
                        }
                    },
            ) {
                // Ekran: afişin karartılmış büyük kopyası (perde ardında sinema ışığı) + ortada keskin afiş.
                val screenTop = unit * CinemaGeometry.VALANCE
                val screenH = unit * (CinemaGeometry.SCENE_H - CinemaGeometry.VALANCE - CinemaGeometry.FLOOR + 14f)
                Box(
                    Modifier
                        .offset(y = screenTop)
                        .fillMaxWidth()
                        .height(screenH)
                        .background(Color.Black),
                ) {
                    // Bulanıklaştırma (RenderEffect) kullanılmaz: GPU'suz emülatörde çizimi çökertti; çok büyütülmüş, soluk kopya renk ışığı verir.
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.3f
                                scaleX = 2.2f
                                scaleY = 2.2f
                            },
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                    radius = with(LocalDensity.current) { (unit * 260f).toPx() },
                                ),
                            ),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = unit * CinemaGeometry.POSTER_TOP)
                        .size(unit * CinemaGeometry.POSTER_W, unit * CinemaGeometry.POSTER_H)
                        .graphicsLayer {
                            val s = CinemaGeometry.posterScale(close.value)
                            scaleX = s
                            scaleY = s
                            translationX = drag * 0.12f * unitPx
                        }
                        .shadow(18.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            SliderDots(count = count, current = index % count, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Kadife kıvrımları: koyu–açık–koyu şeritler kanat genişliğine yayılır (kanat toplandıkça kıvrımlar sıklaşır). */
private val VelvetFolds: List<Color> =
    List(CinemaGeometry.FOLDS) { listOf(VelvetDeep, VelvetMid, VelvetLight, VelvetMid) }.flatten() + VelvetDeep

/** İki kanat: sol kanat çizilir, sağ kanat aynası. İç kenar [CinemaGeometry.innerEdge] boyunca örneklenir. */
private fun DrawScope.drawCurtains(path: Path, folds: List<Color>, shade: Brush, close: Float, unitPx: Float) {
    val top = CinemaGeometry.VALANCE * 0.6f * unitPx
    val h = size.height - top
    val w = size.width
    repeat(2) { side ->
        path.reset()
        path.moveTo(0f, top)
        var maxX = 0f
        val steps = 18
        for (i in 0..steps) {
            val y = i / steps.toFloat()
            val x = CinemaGeometry.innerEdge(y, close) * w
            maxX = maxOf(maxX, x)
            path.lineTo(x, top + y * h)
        }
        path.lineTo(0f, size.height)
        path.close()
        val draw: DrawScope.() -> Unit = {
            drawPath(path, Brush.horizontalGradient(folds, startX = 0f, endX = maxX))
            drawPath(path, shade)
            // Bağ: açıkken kanadı toplayan altın kordon.
            val tieA = (1f - close * 2f).coerceIn(0f, 1f)
            if (tieA > 0f) {
                val ty = top + CinemaGeometry.TIE_Y * h
                val tx = CinemaGeometry.innerEdge(CinemaGeometry.TIE_Y, close) * w
                drawLine(Gold, Offset(0f, ty - 6f * unitPx), Offset(tx + 2f * unitPx, ty), 2.4f * unitPx, alpha = tieA)
                drawCircle(Gold, 3.4f * unitPx, Offset(tx + 2f * unitPx, ty), alpha = tieA)
            }
        }
        if (side == 0) draw() else scale(-1f, 1f, pivot = Offset(w / 2f, 0f)) { draw() }
    }
}

/** Üst saçak: kıvrımlı bant, altta yarım daire dilimler ve altın şerit. */
private fun DrawScope.drawValance(unitPx: Float) {
    val h = CinemaGeometry.VALANCE * unitPx
    val band = h * 0.72f
    drawRect(Brush.verticalGradient(listOf(VelvetDeep, VelvetMid, VelvetDeep), endY = band), size = Size(size.width, band))
    val n = 9
    val r = size.width / n / 2f
    for (i in 0 until n) {
        val cx = r + i * 2 * r
        drawArc(VelvetMid, 0f, 180f, useCenter = true, topLeft = Offset(cx - r, band - r * 0.85f), size = Size(2 * r, 2 * r * 0.7f))
        drawArc(Gold, 0f, 180f, useCenter = false, topLeft = Offset(cx - r, band - r * 0.85f), size = Size(2 * r, 2 * r * 0.7f), style = Stroke(1.4f * unitPx), alpha = 0.8f)
    }
    drawRect(Gold, Offset(0f, band - 2.2f * unitPx), Size(size.width, 1.6f * unitPx), alpha = 0.85f)
}

/** Alt: iki sıra koltuk silueti (öndeki daha büyük ve koyu) ve koridor ışıkları. */
private fun DrawScope.drawSeats(seat: Color, unitPx: Float) {
    val floorTop = size.height - CinemaGeometry.FLOOR * unitPx
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, seat.copy(alpha = 0.55f)), startY = floorTop - 10f * unitPx, endY = size.height),
        topLeft = Offset(0f, floorTop - 10f * unitPx),
    )
    fun row(y: Float, w: Float, h: Float, gap: Float, shift: Float, alpha: Float) {
        var x = -shift
        while (x < size.width) {
            drawRoundRect(seat, Offset(x, y), Size(w, h + 20f * unitPx), CornerRadius(w * 0.32f), alpha = alpha)
            x += w + gap
        }
    }
    row(floorTop + 18f * unitPx, 30f * unitPx, 22f * unitPx, 5f * unitPx, 10f * unitPx, 0.7f)
    row(floorTop + 40f * unitPx, 40f * unitPx, 30f * unitPx, 6f * unitPx, 28f * unitPx, 0.95f)
    for (i in 0 until 7) {
        val x = size.width * (i + 0.5f) / 7f
        drawCircle(AisleLight, 1.8f * unitPx, Offset(x, size.height - 5f * unitPx), alpha = 0.85f)
        drawCircle(AisleLight, 5f * unitPx, Offset(x, size.height - 5f * unitPx), alpha = 0.18f)
    }
}
