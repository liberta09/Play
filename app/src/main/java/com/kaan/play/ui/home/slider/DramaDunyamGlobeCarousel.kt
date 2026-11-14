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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dünya küresi (DramaDünyam): afişler görünmez bir kürenin hafif eğik ekvatoru çevresinde dizilir; küre
 * yavaşça döner, öndeki afiş en büyük ve net, arkadakiler küçülüp solar. Arkada temanın `primary` renginde ince boylam ve
 * enlem çizgileriyle silik bir küre süsü, kartlarla aynı açıda döner. Birimler sahne birimi (sahne 400 birim genişlik).
 */
internal object GlobeGeometry {
    const val STAGE = 400f
    const val STAGE_H = 396f
    const val CARD_W = 148f
    const val CARD_H = 222f
    const val RING_R = 186f // afiş halkasının yarıçapı
    const val SPHERE_R = 166f // süs kürenin yarıçapı
    const val CENTER_Y = 192f // halka/küre merkezi (sahne üstünden)
    const val TILT = 16f // kameranın yukarıdan bakış açısı (derece)
    const val ROLL = -8f // ekvatorun ekran düzlemindeki eğikliği (derece)
    const val FOCAL = 380f
    const val SPHERE_FOCAL = 900f // süs kürenin hafif derinliği
    const val YAW_MAX = 38f // yan afişin dışa bakış açısı (en çok)
    const val PER_ITEM = 160f // sürüklemede bir afiş
    const val TAP_SIDE = CARD_W / 2f
    const val VEIL_MAX = 0.62f // en arkadaki afişin üstündeki zemin örtüsü

    private const val RAD = (PI / 180.0).toFloat()

    /** Halkada [q] uzaklığındaki afişin açısı (radyan): öndeki 0, tam arkadaki ±π. */
    fun angle(q: Float, count: Int): Float = (2.0 * PI).toFloat() * q / count

    /**
     * [q] uzaklığındaki afiş → [out]: 0 x, 1 y (halka merkezine göre ekran, birim), 2 ölçek, 3 saydamlık, 4 örtü, 5 dışa
     * bakış (derece, sağdaki pozitif), 6 derinlik (öndeki düzlemin arkası, birim).
     */
    fun place(q: Float, count: Int, out: FloatArray) {
        val th = angle(q, count)
        val s = sin(th)
        val c = cos(th)
        val depth = RING_R * (1f - c)
        val scale = FOCAL / (FOCAL + depth)
        val x = RING_R * s * scale
        val y = RING_R * c * sin(TILT * RAD) * scale // yakın taraf aşağıda, arka taraf yukarıda
        val r = ROLL * RAD
        out[0] = x * cos(r) - y * sin(r)
        out[1] = x * sin(r) + y * cos(r)
        out[2] = scale
        val front = (1f + c) / 2f
        out[3] = 0.28f + 0.72f * front.pow(1.2f)
        out[4] = VEIL_MAX * (1f - front)
        out[5] = YAW_MAX * s
        out[6] = depth
    }

    /** Küre üstündeki nokta (enlem, boylam derece) → [out]: 0 x, 1 y (ekran, birim), 2 önde mi (>0 önde). */
    fun spherePoint(latDeg: Float, lonDeg: Float, out: FloatArray) {
        val lat = latDeg * RAD
        val lon = lonDeg * RAD
        val t = TILT * RAD
        val x = SPHERE_R * cos(lat) * sin(lon)
        val up = SPHERE_R * sin(lat)
        val toward = SPHERE_R * cos(lat) * cos(lon)
        val front = toward * cos(t) + up * sin(t)
        val scale = SPHERE_FOCAL / (SPHERE_FOCAL - front) // kenar çizgisi (front=0) tam yarıçapta
        val y = (-up * cos(t) + toward * sin(t)) * scale
        out[0] = x * scale
        out[1] = y
        out[2] = front
    }

    /** Kartlarla aynı dönüş: [position] bir adım ilerleyince küre 360/[count] derece döner. */
    fun spin(position: Float, count: Int): Float = -position * 360f / count
}

@Composable
internal fun DramaDunyamGlobeCarousel(
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
    val turn = remember { spring<Float>(dampingRatio = 0.92f, stiffness = 38f) } // yavaş, yumuşak dönüş
    val line = MaterialTheme.colorScheme.primary
    val veil = MaterialTheme.colorScheme.background

    fun go(target: Float) {
        savedIndex = SliderMath.index(target, count)
        job?.cancel()
        job = scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, turn) }
    }

    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val unit = maxWidth / GlobeGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * GlobeGeometry.STAGE_H)
                    .clipToBounds()
                    .globeLines(position::value, count, line, unitPx)
                    .trackTouch { touching = it }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val stepPx = unitPx * GlobeGeometry.PER_ITEM
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
                            val step = SliderMath.tapStep((at.x - size.width / 2f) / unitPx, GlobeGeometry.TAP_SIDE)
                            if (step == 0) onOpen(items[centre]) else go(position.value.roundToInt() + step.toFloat())
                        }
                    },
            ) {
                items.forEachIndexed { index, item ->
                    GlobeCard(
                        item = item,
                        index = index,
                        count = count,
                        position = position::value,
                        unitPx = unitPx,
                        veil = veil,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = unit * (GlobeGeometry.CENTER_Y - GlobeGeometry.CARD_H / 2f))
                            .size(unit * GlobeGeometry.CARD_W, unit * GlobeGeometry.CARD_H),
                    )
                }
            }
            Crossfade(targetState = centre, animationSpec = tween(320), label = "globeTitle") { i ->
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

/** Tek afiş: konum, ölçek, dışa bakış ve gölge yalnız yerleşim/çizim evresinde okunur; derinliğe göre sıralanır. */
@Composable
private fun GlobeCard(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    unitPx: Float,
    veil: Color,
    modifier: Modifier,
) {
    val pose = remember { FloatArray(7) }
    val shape = remember { RoundedCornerShape(10.dp) }
    val density = LocalDensity.current
    val shadowPx = with(density) { 14.dp.toPx() }
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(index, count, unitPx, shape, shadowPx) {
        {
            GlobeGeometry.place(SliderMath.offset(index, position(), count), count, pose)
            translationX = pose[0] * unitPx
            translationY = pose[1] * unitPx
            scaleX = pose[2]
            scaleY = pose[2]
            alpha = pose[3]
            rotationY = pose[5] // Compose: artı açı sağ kenarı derine götürür (afiş küreden dışa bakar)
            cameraDistance = GlobeGeometry.FOCAL * 2f * unitPx / 72f
            shadowElevation = shadowPx * ((pose[2] - 0.95f) / 0.05f).coerceIn(0f, 1f) // yalnız öndeki
            this.shape = shape
            clip = true
        }
    }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    GlobeGeometry.place(SliderMath.offset(index, position(), count), count, pose)
                    placeable.placeWithLayer(0, 0, zIndex = -pose[6], layerBlock = layerBlock)
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                val q = SliderMath.offset(index, position(), count)
                val c = cos(GlobeGeometry.angle(q, count))
                val a = GlobeGeometry.VEIL_MAX * (1f - (1f + c) / 2f)
                if (a > 0.01f) drawRect(veil, alpha = a)
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

/** Arkadaki silik küre: tema renginde hafif ışıma, dış çember, 12 boylam ve 5 enlem; ön yüz çizgileri belirgin, arkası soluk. */
private fun Modifier.globeLines(position: () -> Float, count: Int, color: Color, unitPx: Float): Modifier {
    val a = FloatArray(3)
    val b = FloatArray(3)
    return drawBehind {
        val cx = size.width / 2f
        val cy = GlobeGeometry.CENTER_Y * unitPx
        val r = GlobeGeometry.SPHERE_R * unitPx
        val stroke = 1.dp.toPx()
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.25f,
            ),
            radius = r * 1.25f,
            center = Offset(cx, cy),
        )
        drawCircle(color.copy(alpha = 0.30f), radius = r, center = Offset(cx, cy), style = Stroke(stroke))
        val spin = GlobeGeometry.spin(position(), count)
        rotate(GlobeGeometry.ROLL, Offset(cx, cy)) {
            fun segment(lat0: Float, lon0: Float, lat1: Float, lon1: Float, strong: Float) {
                GlobeGeometry.spherePoint(lat0, lon0, a)
                GlobeGeometry.spherePoint(lat1, lon1, b)
                val front = a[2] + b[2] > 0f
                drawLine(
                    color.copy(alpha = if (front) 0.34f * strong else 0.10f * strong),
                    Offset(cx + a[0] * unitPx, cy + a[1] * unitPx),
                    Offset(cx + b[0] * unitPx, cy + b[1] * unitPx),
                    strokeWidth = stroke,
                )
            }
            for (k in 0 until 12) {
                val lon = k * 30f + spin
                var lat = -90f
                while (lat < 90f) {
                    segment(lat, lon, lat + 7.5f, lon, 1f)
                    lat += 7.5f
                }
            }
            for (lat in floatArrayOf(-60f, -30f, 0f, 30f, 60f)) {
                val strong = if (lat == 0f) 1.5f else 1f
                var lon = 0f
                while (lon < 360f) {
                    segment(lat, lon + spin, lat, lon + 7.5f + spin, strong)
                    lon += 7.5f
                }
            }
        }
    }
}
