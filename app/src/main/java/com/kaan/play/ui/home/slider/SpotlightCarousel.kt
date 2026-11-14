package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Spot ışığı (LiderDrama): karanlık sahnede afişler yan yana; tavandaki lambadan konik ışık ortadaki
 * afişe düşer, yerde ışık havuzu. Geçiş: önce ışık bir sonraki afişe kayar (lamba döner), sonra sıra kayıp aydınlanan
 * afişi ortaya getirir. Işık dışındakiler loş. Açık temada sahne temanın yüzey tonunda, ışık kehribar ve yine belirgin.
 * Birimler sahne birimi (360 genişlik).
 */
internal object SpotlightGeometry {
    const val STAGE = 360f
    const val SCENE_H = 340f
    const val POSTER_W = 126f
    const val POSTER_H = 189f
    const val STEP = 142f
    const val FLOOR_Y = 300f // afişlerin bastığı zemin çizgisi
    const val LAMP_Y = 14f
    const val SIDE_SCALE = 0.8f
    const val MAX_Q = 2.6f
    const val LIGHT_MS = 460
    const val TAP_SIDE = POSTER_W / 2f
    const val BEAM_TOP = 9f // koni tepesi yarı genişliği
    const val BEAM_FOOT = 92f // koni zemindeki yarı genişliği

    /** Sırada [q] uzaklığındaki afişin x'i (birim): ilk komşu tam adım, ötekiler biraz sıkışır. */
    fun x(q: Float): Float {
        val a = abs(q)
        val v = if (a <= 1f) a * STEP else STEP + (a - 1f) * STEP * 0.78f
        return if (q < 0f) -v else v
    }

    fun scale(q: Float): Float = 1f - (1f - SIDE_SCALE) * abs(q).coerceAtMost(1f) - 0.06f * (abs(q) - 1f).coerceIn(0f, 1f)

    /** Işığa yakınlık: [q] afişin, [beam] ışığın sıradaki yeri; ışık altında 1, 0,65 afiş ötede 0 (yumuşak). */
    fun lit(q: Float, beam: Float): Float {
        val t = (1f - abs(q - beam) / 0.65f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Lambanın dönüşü (derece): ışığın düştüğü x'e (birim) doğru, düşey 0. */
    fun lampAngle(beamX: Float): Float =
        (atan2(beamX.toDouble(), (FLOOR_Y - LAMP_Y).toDouble()) * 180.0 / PI).toFloat()

    fun alpha(q: Float, count: Int): Float = ((minOf(MAX_Q, count / 2f) - abs(q)) * 4f).coerceIn(0f, 1f)
}

private val LampMetal = Color(0xFF26262B)
private val LampLens = Color(0xFFFFF6DE)
private val WarmWhite = Color(0xFFFFF1CF)
private val Amber = Color(0xFFFFB648)
private val LightEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
internal fun SpotlightCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    val position = remember { Animatable(savedIndex.toFloat()) } // sıranın kayması
    val light = remember { Animatable(savedIndex.toFloat()) } // ışığın aydınlattığı yer (aynı ölçek)
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val lit by remember(count) { derivedStateOf { SliderMath.index(light.value, count) } }
    val rowSpring = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 140f) }
    val scheme = MaterialTheme.colorScheme
    val lightTheme = scheme.background.luminance() > 0.5f
    val stageTop = if (lightTheme) scheme.surfaceVariant else lerp(scheme.background, Color.Black, 0.55f)
    val stageBottom = if (lightTheme) lerp(scheme.surfaceVariant, scheme.surface, 0.5f) else lerp(scheme.surface, Color.Black, 0.25f)
    val beamColor = if (lightTheme) Amber else WarmWhite
    val dim = if (lightTheme) 0.42f else 0.62f

    /** [sweep]: önce ışık kayar, sonra sıra (otomatik, oklar, dokunma). Değilse ikisi birlikte (sürükleme bırakılınca). */
    fun go(target: Float, sweep: Boolean) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch {
            if (reduceMotion) {
                light.snapTo(target); position.snapTo(target)
                return@launch
            }
            if (sweep) {
                light.animateTo(target, tween(SpotlightGeometry.LIGHT_MS, easing = LightEasing))
                position.animateTo(target, rowSpring)
            } else {
                launch { light.animateTo(target, rowSpring) }
                position.animateTo(target, rowSpring)
            }
        }
    }

    AutoAdvance(paused = touching) { go(light.value.roundToInt() + 1f, sweep = true) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val unit = maxWidth / SpotlightGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        val beam: () -> Float = { light.value - position.value } // ışığın sıradaki yeri (ortaya göre)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * SpotlightGeometry.SCENE_H)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(stageTop, stageBottom)))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .clipToBounds()
                    .trackTouch { touching = it }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val stepPx = unitPx * SpotlightGeometry.STEP
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                job?.cancel()
                                start = position.value
                                tracker.resetTracking()
                                scope.launch { light.snapTo(position.value) }
                            },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                go(SliderMath.settleTarget(start, position.value, v), sweep = false)
                            },
                            onDragCancel = { go(SliderMath.settleTarget(start, position.value, 0f), sweep = false) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch {
                                val p = position.value - dx / stepPx
                                position.snapTo(p)
                                light.snapTo(p)
                            }
                        }
                    }
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val u = (at.x - size.width / 2f) / unitPx
                            val step = SliderMath.tapStep(u, SpotlightGeometry.TAP_SIDE)
                            if (step == 0) onOpen(items[lit]) else go(light.value.roundToInt() + step.toFloat(), sweep = true)
                        }
                    }
                    .drawWithCache {
                        val cone = Path()
                        val floorY = SpotlightGeometry.FLOOR_Y * unitPx
                        val lampY = SpotlightGeometry.LAMP_Y * unitPx
                        // Koni: lambada yoğun, zemine doğru söner; iki kat (geniş soluk hale + dar çekirdek) yumuşak kenar verir.
                        val coneBrush = Brush.verticalGradient(
                            listOf(beamColor.copy(alpha = if (lightTheme) 0.42f else 0.3f), beamColor.copy(alpha = if (lightTheme) 0.12f else 0.05f)),
                            startY = lampY,
                            endY = floorY,
                        )
                        val haloBrush = Brush.verticalGradient(
                            listOf(beamColor.copy(alpha = if (lightTheme) 0.16f else 0.1f), Color.Transparent),
                            startY = lampY,
                            endY = floorY,
                        )
                        val halo = Path()
                        val vignette = Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = if (lightTheme) 0.16f else 0.4f)),
                            center = Offset(size.width / 2f, size.height * 0.6f),
                            radius = size.width * 0.75f,
                        )
                        onDrawWithContent {
                            val b = beam()
                            val bx = size.width / 2f + SpotlightGeometry.x(b) * unitPx
                            // Arka: sahne kararması, ışık konisi, yerdeki ışık havuzu.
                            drawRect(vignette)
                            cone.reset()
                            val top = SpotlightGeometry.BEAM_TOP * unitPx
                            val foot = SpotlightGeometry.BEAM_FOOT * unitPx
                            cone.moveTo(size.width / 2f - top, lampY)
                            cone.lineTo(size.width / 2f + top, lampY)
                            cone.lineTo(bx + foot, floorY)
                            cone.lineTo(bx - foot, floorY)
                            cone.close()
                            halo.reset()
                            halo.moveTo(size.width / 2f - top * 1.6f, lampY)
                            halo.lineTo(size.width / 2f + top * 1.6f, lampY)
                            halo.lineTo(bx + foot * 1.35f, floorY)
                            halo.lineTo(bx - foot * 1.35f, floorY)
                            halo.close()
                            drawPath(halo, haloBrush)
                            drawPath(cone, coneBrush)
                            drawOval(
                                Brush.radialGradient(
                                    listOf(beamColor.copy(alpha = if (lightTheme) 0.7f else 0.55f), Color.Transparent),
                                    center = Offset(bx, floorY),
                                    radius = foot * 1.25f,
                                ),
                                topLeft = Offset(bx - foot * 1.25f, floorY - foot * 0.28f),
                                size = Size(foot * 2.5f, foot * 0.56f),
                            )
                            drawContent()
                            // Ön: afişlerin üstüne ince ışık (aydınlatılan afiş parlar) ve lamba.
                            drawPath(cone, beamColor.copy(alpha = if (lightTheme) 0.1f else 0.08f))
                            val angle = SpotlightGeometry.lampAngle(SpotlightGeometry.x(b))
                            rotate(-angle, pivot = Offset(size.width / 2f, lampY)) {
                                val lw = 30f * unitPx
                                val lh = 18f * unitPx
                                drawRoundRect(LampMetal, Offset(size.width / 2f - lw / 2f, lampY - lh), Size(lw, lh), CornerRadius(5f * unitPx))
                                drawOval(LampLens, Offset(size.width / 2f - top * 1.4f, lampY - 3f * unitPx), Size(top * 2.8f, 6f * unitPx))
                            }
                            drawRect(LampMetal, Offset(size.width / 2f - 1.5f * unitPx, 0f), Size(3f * unitPx, lampY - 14f * unitPx))
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                items.forEachIndexed { index, item ->
                    StagePoster(
                        item = item,
                        index = index,
                        count = count,
                        position = position::value,
                        beam = beam,
                        unit = unit,
                        unitPx = unitPx,
                        dim = dim,
                    )
                }
            }
            Text(
                text = items[lit].title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            SliderDots(count = count, current = lit, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Sahnedeki tek afiş: tabanı zemin çizgisinde; ışıktan uzaklığına göre loş, ışık altındaki biraz yükselir. */
@Composable
private fun StagePoster(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    beam: () -> Float,
    unit: Dp,
    unitPx: Float,
    dim: Float,
) {
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(index, count, unitPx) {
        {
            val q = SliderMath.offset(index, position(), count)
            val a = SpotlightGeometry.alpha(q, count)
            alpha = a
            if (a > 0f) {
                val s = SpotlightGeometry.scale(q)
                val l = SpotlightGeometry.lit(q, beam())
                translationX = SpotlightGeometry.x(q) * unitPx
                translationY = -6f * l * unitPx
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
                shadowElevation = (4f + 14f * l) * unitPx
                shape = RoundedCornerShape(4.dp)
                clip = true
            }
        }
    }
    Box(
        Modifier
            .padding(top = unit * (SpotlightGeometry.FLOOR_Y - SpotlightGeometry.POSTER_H))
            .size(unit * SpotlightGeometry.POSTER_W, unit * SpotlightGeometry.POSTER_H)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val q = SliderMath.offset(index, position(), count)
                    placeable.placeWithLayer(0, 0, zIndex = -abs(q), layerBlock = layerBlock)
                }
            }
            .background(Color.Black)
            .drawWithContent {
                drawContent()
                val l = SpotlightGeometry.lit(SliderMath.offset(index, position(), count), beam())
                if (l < 1f) drawRect(Color.Black, alpha = dim * (1f - l))
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
