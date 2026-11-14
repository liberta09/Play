package com.kaan.play.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3B halka: 21st.dev "Warp Carousel" görsel fikri yerel olarak yeniden yazıldı (kod kopyalanmadı).
 * Kartlar yatay bir halkada: öndeki kart dik, komşular iç kenarından menteşe gibi dönerek (q≈0.46'da ~83°) derine kaçar, sonra
 * dış kenarı etrafında arkaya döner (q≈1'de 180°). Birimler sahne birimi (sahne 640 birim genişlik), derinlik [FOCAL] ile izdüşer.
 */
internal object WarpGeometry {
    const val STAGE = 640f
    const val CARD_W = 256f
    const val CARD_H = 384f
    const val FOCAL = 734f
    const val BAND = 70f // kartların üstünde ve altında ışık şeridi payı
    const val PER_CARD = 300f // sürüklemede bir kart = 300 birim
    const val TAP_SIDE = 168f
    const val MAX_Q = 2.05f // her yanda en çok 2 kart → en çok 5 kart çizilir

    private const val HALF = CARD_W / 2f
    const val Q_A = 0.46f
    const val YAW_A = 83f
    private const val INNER_A = 166f // q=Q_A'da iç kenarın x'i
    private const val LIFT_A = 35f // q=Q_A'da iç kenarın derinliği
    private const val Q_B = 0.75f
    private const val HINGE_BX = 205f
    private const val HINGE_BZ = 355f
    private const val HINGE_V = 230f // Q_B sonrası menteşenin yana hızı (birim / kart)
    private const val Q_FLIP = 1.04f
    private const val RAD = (PI / 180.0).toFloat()

    // 1. evre sonundaki dış kenar ve hızları (2. evre bunlardan pürüzsüz devam eder).
    private val yawRateA = YAW_A * 1.1f / Q_A
    private val outerAx = INNER_A + 2f * HALF * cos(YAW_A * RAD)
    private val outerAz = LIFT_A + 2f * HALF * sin(YAW_A * RAD)
    private val outerVx = (INNER_A + HALF) * 1.3f / Q_A - 2f * HALF * sin(YAW_A * RAD) * yawRateA * RAD
    private val outerVz = LIFT_A * 4f / Q_A + 2f * HALF * cos(YAW_A * RAD) * yawRateA * RAD

    private fun hermite(t: Float, p0: Float, m0: Float, p1: Float, m1: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * p0 + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * p1 + (t3 - t2) * m1
    }

    /** Halkada [q] uzaklığındaki kartın merkezi x, derinlik z (öndeki düzlemin arkası, birim) ve dönüşü (derece) → [out]. */
    fun pose(q: Float, out: FloatArray) {
        val s = if (q < 0f) -1f else 1f
        val a = abs(q)
        if (a <= Q_A) {
            val t = a / Q_A
            val innerX = -HALF + (INNER_A + HALF) * t.pow(1.3f)
            val innerZ = LIFT_A * t * t * t * t
            val yaw = YAW_A * t.pow(1.1f)
            out[0] = s * (innerX + HALF * cos(yaw * RAD))
            out[1] = innerZ + HALF * sin(yaw * RAD)
            out[2] = s * yaw
            return
        }
        val hx: Float
        val hz: Float
        if (a <= Q_B) {
            val l = Q_B - Q_A
            val t = (a - Q_A) / l
            hx = hermite(t, outerAx, outerVx * l, HINGE_BX, HINGE_V * l)
            hz = hermite(t, outerAz, outerVz * l, HINGE_BZ, 0f)
        } else {
            hx = HINGE_BX + HINGE_V * (a - Q_B)
            hz = HINGE_BZ
        }
        val lp = Q_FLIP - Q_A
        val tp = ((a - Q_A) / lp).coerceAtMost(1f)
        val yaw = hermite(tp, YAW_A, yawRateA * lp, 180f, 0f)
        out[0] = s * (hx - HALF * cos(yaw * RAD))
        out[1] = hz - HALF * sin(yaw * RAD)
        out[2] = s * yaw
    }

    fun scaleAt(z: Float): Float = FOCAL / (FOCAL + z)

    /** Az kartta karşı kenara atlayan kart görünmeden atlasın; çok kartta en çok ±[MAX_Q]. */
    fun alpha(q: Float, count: Int): Float = ((minOf(MAX_Q, count / 2f) - abs(q)) * 4f).coerceIn(0f, 1f)

    /** Geniş açı izlenimi: kenara yaklaşan kart dikeyde biraz uzar (özgündeki kenar parlamasının ucuz yaklaşığı). */
    fun edgeStretch(screenX: Float): Float {
        val t = ((abs(screenX) - 210f) / 100f).coerceIn(0f, 1.4f)
        return 1f + 0.35f * t.pow(1.5f)
    }

    /** Otomatik dönüş eğrisi: kısa yumuşak kalkış, çok hızlı savrulma, sonra uzun yavaş süzülme. */
    fun whip(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val out = 1f - (1f - c).pow(5)
        val start = if (c < 0.08f) (c / 0.08f).let { it * it * (3f - 2f * it) } else 1f
        return out * start
    }

    /** Sürükleme bırakılınca: hız [velocity] (kart/sn) kısa bir ileri atılım ekler, en yakın karta oturur. */
    fun settleTarget(position: Float, velocity: Float): Float = (position + velocity * 0.16f).roundToInt().toFloat()

    /** Sahne birimiyle dokunulan yer: yan kart ±1 adım, orta 0 (aç). */
    fun tapStep(u: Float): Int = when {
        u > TAP_SIDE -> 1
        u < -TAP_SIDE -> -1
        else -> 0
    }

    /** Afişten ışık rengi: canlı (doygun) noktalar ağır basar, sonra parlaklık yükseltilir. */
    fun glowColor(argb: IntArray): Int {
        var r = 0f
        var g = 0f
        var b = 0f
        var w = 0f
        for (p in argb) {
            val pr = (p shr 16 and 0xFF) / 255f
            val pg = (p shr 8 and 0xFF) / 255f
            val pb = (p and 0xFF) / 255f
            val mx = maxOf(pr, pg, pb)
            val sat = if (mx <= 0f) 0f else (mx - minOf(pr, pg, pb)) / mx
            val k = sat * sat * mx + 0.02f
            r += pr * k; g += pg * k; b += pb * k; w += k
        }
        if (w <= 0f) return DEFAULT_GLOW
        r /= w; g /= w; b /= w
        val mx = maxOf(r, g, b)
        if (mx < 0.02f) return DEFAULT_GLOW
        val boost = 0.92f / mx
        fun ch(v: Float) = (v * boost * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    const val DEFAULT_GLOW = 0xFF7FD0F0.toInt()
}

/** DramaTR sitesinin eklentisi: ad (yalnız harfler, küçük) tam olarak site adı. */
internal fun isDramaTrProvider(name: String): Boolean =
    name.lowercase(Locale.ROOT).filter { it in 'a'..'z' } == "dramatr"

private const val WARP_AUTOPLAY_MS = 3_700
private const val WARP_STEP = 2
private val WhipEasing = Easing { WarpGeometry.whip(it) }

@Composable
internal fun WarpCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scope = rememberCoroutineScope()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    val position = remember { Animatable(savedIndex.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    val centre by remember(count) { derivedStateOf { FanGeometry.index(position.value, count) } }
    val whipSpec = remember { tween<Float>(WARP_AUTOPLAY_MS, easing = WhipEasing) }
    val springSpec = remember { spring<Float>(dampingRatio = 0.84f, stiffness = 60f) }
    val glows = remember(items) { mutableStateListOf<ColorFilter?>().apply { repeat(count) { add(null) } } }
    val glowImage = remember { softEllipse() }

    fun go(target: Float, spinning: Boolean) {
        savedIndex = FanGeometry.index(target, count)
        scope.launch {
            when {
                reduceMotion -> position.snapTo(target)
                spinning -> position.animateTo(target, whipSpec)
                else -> position.animateTo(target, springSpec)
            }
        }
    }

    // Otomatik dönüş: her seferinde iki kart, savrulup yavaşça oturur; dokunurken durur, bırakınca bir aralık bekler.
    LaunchedEffect(touching, count, reduceMotion) {
        if (touching || reduceMotion) return@LaunchedEffect
        delay(WARP_AUTOPLAY_MS.toLong())
        while (true) {
            val target = position.value.roundToInt() + WARP_STEP.toFloat()
            savedIndex = FanGeometry.index(target, count)
            position.animateTo(target, whipSpec)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val unit = maxWidth / WarpGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * (WarpGeometry.CARD_H + 2 * WarpGeometry.BAND))
                    .clipToBounds()
                    .warpGlows(position::value, count, glows, glowImage, unitPx)
                    .pointerInput(count) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.any { it.pressed }
                                // Dokunuş dönüşü yarıda kestiyse bırakınca en yakın karta otursun (sürükleme/dokunma bunu ezer).
                                if (touching && !pressed && !position.isRunning) {
                                    go(position.value.roundToInt().toFloat(), spinning = false)
                                }
                                touching = pressed
                            }
                        }
                    }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val perCardPx = unitPx * WarpGeometry.PER_CARD
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / perCardPx
                                go(WarpGeometry.settleTarget(position.value, v), spinning = false)
                            },
                            onDragCancel = { go(position.value.roundToInt().toFloat(), spinning = false) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { position.snapTo(position.value - dx / perCardPx) }
                        }
                    }
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val step = WarpGeometry.tapStep((at.x - size.width / 2f) / unitPx)
                            if (step == 0) onOpen(items[centre])
                            else go(position.value.roundToInt() + step.toFloat(), spinning = true)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                items.forEachIndexed { index, item ->
                    WarpCard(
                        item = item,
                        index = index,
                        count = count,
                        position = position::value,
                        unitPx = unitPx,
                        modifier = Modifier.size(unit * WarpGeometry.CARD_W, unit * WarpGeometry.CARD_H),
                        onGlow = { if (glows[index] == null) glows[index] = ColorFilter.tint(Color(it)) },
                    )
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
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { i ->
                    val w by animateDpAsState(if (i == centre) 18.dp else 6.dp, label = "warpDot")
                    Box(
                        Modifier
                            .size(width = w, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == centre) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Tek kart. Konum yalnız yerleşim/çizim evresinde okunur (her karede yeniden oluşturma yok); derinliğe göre sıralanır,
 * halkanın dışındaki kart çizilmez. 90°'yi geçen kart ayna görüntüsü olmasın diye yatayda ters çevrilir.
 */
@Composable
private fun WarpCard(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    unitPx: Float,
    modifier: Modifier,
    onGlow: (Int) -> Unit,
) {
    val context = LocalContext.current
    val pose = remember { FloatArray(3) }
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(index, count, unitPx) {
        {
            val q = FanGeometry.offset(index, position(), count)
            val a = WarpGeometry.alpha(q, count)
            alpha = a
            if (a > 0f) {
                WarpGeometry.pose(q, pose)
                val s = WarpGeometry.scaleAt(pose[1])
                val screenX = pose[0] * s
                val flip = if (cos(pose[2] * (PI / 180.0).toFloat()) < 0f) -1f else 1f
                translationX = screenX * unitPx
                scaleX = s * flip
                scaleY = s * WarpGeometry.edgeStretch(screenX)
                rotationY = -pose[2]
                cameraDistance = WarpGeometry.FOCAL * unitPx / 72f // RenderNode: 1 birim = 72 px
            }
        }
    }
    val request = remember(item.posterUrl) {
        ImageRequest.Builder(context).data(item.posterUrl).allowHardware(false).build()
    }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val q = FanGeometry.offset(index, position(), count)
                    WarpGeometry.pose(q, pose)
                    placeable.placeWithLayer(0, 0, zIndex = -pose[1], layerBlock = layerBlock)
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                val bmp = state.result.image.toBitmap()
                val px = IntArray(64)
                for (y in 0 until 8) for (x in 0 until 8) {
                    px[y * 8 + x] = bmp.getPixel((x * 2 + 1) * bmp.width / 16, (y * 2 + 1) * bmp.height / 16)
                }
                onGlow(WarpGeometry.glowColor(px))
            },
        )
    }
}

/** Halkanın üstünde ve altında, izleyiciye bakan her kartın renginden yumuşak ışık şeritleri (öndeki kart baskın). */
private fun Modifier.warpGlows(
    position: () -> Float,
    count: Int,
    glows: List<ColorFilter?>,
    image: ImageBitmap,
    unitPx: Float,
): Modifier {
    val pose = FloatArray(3)
    return drawBehind {
        val p = position()
        val bandH = (52f * unitPx).roundToInt()
        val reach = (WarpGeometry.CARD_H / 2f + 34f) * unitPx
        val cy = size.height / 2f
        for (i in 0 until count) {
            val filter = glows.getOrNull(i) ?: continue
            val q = FanGeometry.offset(i, p, count)
            val a = WarpGeometry.alpha(q, count)
            if (a <= 0f) continue
            WarpGeometry.pose(q, pose)
            val c = cos(pose[2] * (PI / 180.0).toFloat())
            if (c <= 0.02f) continue
            val s = WarpGeometry.scaleAt(pose[1])
            val cx = size.width / 2f + pose[0] * s * unitPx
            val half = (s * WarpGeometry.CARD_W / 2f * c * 1.8f * unitPx).roundToInt().coerceAtLeast(1)
            val strength = (c * s * a).coerceIn(0f, 1f)
            val left = (cx - half).roundToInt()
            for (y in floatArrayOfTopBottom) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(left, (cy + y * reach - bandH / 2f).roundToInt()),
                    dstSize = IntSize(half * 2, bandH),
                    alpha = strength,
                    colorFilter = filter,
                )
            }
        }
    }
}

private val floatArrayOfTopBottom = floatArrayOf(-1f, 1f)

/** Beyaz, kenarlara doğru sönen elips (renk her kartın filtresinden gelir). Bir kez üretilir. */
private fun softEllipse(): ImageBitmap {
    val image = ImageBitmap(96, 96)
    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(48f, 48f),
            radius = 48f,
            colors = listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f), Color.Transparent),
            colorStops = listOf(0f, 0.35f, 0.7f, 1f),
        )
    }
    Canvas(image).drawRect(0f, 0f, 96f, 96f, paint)
    return image
}
