package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Kart destesi (DramaCix): üst üste yığılmış afiş kartları; arkadakiler hafif küçük, aşağı ve yana
 * kaymış, kendi açılarıyla eğik. Üstteki kart parmağı izler ve eğilir; bırakınca eşiği geçtiyse dönerek kaydırma yönüne
 * fırlar ve destenin dibine geçer, alttaki öne çıkar. Ölçüler sahne genişliğinin kesri; sürükleme de genişlik kesri.
 */
internal object CardDeckGeometry {
    const val CARD_W = 0.58f
    const val CARD_H = CARD_W * 1.5f
    const val TOP = 0.03f
    const val PEEK = 0.042f // her arka kart bu kadar aşağıdan görünür
    const val SHRINK = 0.055f
    const val SHOWN = 3 // üsttekinin arkasında görünen kart
    const val SCENE_H = TOP + CARD_H + PEEK * SHOWN + 0.03f
    const val FLY = 1.35f // fırlayan kartın varış yeri
    const val THROW = 0.28f // bu kadar sürükleme (ya da fiske) kartı fırlatır
    const val THROW_SPEED = 1.1f // genişlik/sn
    const val LEAN = 20f // bir genişlik sürüklemede eğim (derece)
    const val RISE = 0.45f // bu kadar sürüklemede alttaki kart tam öne gelir

    private val TILT = floatArrayOf(-2.6f, 2.1f, -1.5f, 2.9f, -2.2f, 1.4f, -2.7f, 1.9f, -1.2f, 2.4f)

    data class Pose(val x: Float, val y: Float, val rotation: Float, val scale: Float, val alpha: Float)

    /** Destedeki sıra: 0 üstteki. */
    fun depth(index: Int, top: Int, count: Int): Int = Math.floorMod(index - top, count)

    /** [depth] (kesirli olabilir) katındaki kartın duruşu; x, y kartın yerinden kayma. */
    fun rest(index: Int, depth: Float): Pose {
        val d = depth.coerceAtLeast(0f)
        val k = d.coerceAtMost(1f)
        val t = TILT[Math.floorMod(index, TILT.size)]
        return Pose(
            x = sign(t) * 0.022f * d,
            y = PEEK * d,
            rotation = t * k,
            scale = 1f - SHRINK * d,
            alpha = (SHOWN + 1f - d).coerceIn(0f, 1f),
        )
    }

    /** Üstteki kartın sürüklenme/fırlama duruşu ([drag] genişlik kesri). */
    fun thrown(drag: Float): Pose = Pose(
        x = drag,
        y = -0.02f * abs(drag).coerceAtMost(1f),
        rotation = LEAN * drag,
        scale = 1f,
        alpha = 1f,
    )

    /** Üstteki ne kadar uzaklaştı: alttakiler o kadar öne kayar (0..1). */
    fun rise(drag: Float): Float = (abs(drag) / RISE).coerceIn(0f, 1f)

    /** Bırakınca: fırlat (±1) ya da yerine dön (0). */
    fun release(drag: Float, velocity: Float): Int = when {
        abs(drag) >= THROW -> sign(drag).toInt()
        abs(velocity) >= THROW_SPEED && sign(velocity) == sign(drag) -> sign(velocity).toInt()
        abs(velocity) >= THROW_SPEED * 1.6f -> sign(velocity).toInt()
        else -> 0
    }
}

@Composable
internal fun CardDeckCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var top by rememberSaveable { mutableIntStateOf(0) }
    val drag = remember { Animatable(0f) } // üstteki kartın yatay kayması (genişlik kesri)
    val backIn = remember { Animatable(1f) } // dibe geçen kartın belirmesi
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scheme = MaterialTheme.colorScheme
    val current = Math.floorMod(top, count)

    /** Fırlama bitti: üstteki dibe geçer, alttaki artık üstte (görünüm değişmez). */
    suspend fun land() {
        top = Math.floorMod(top + 1, count)
        drag.snapTo(0f)
        scope.launch {
            backIn.snapTo(0f)
            backIn.animateTo(1f, tween(360))
        }
    }

    fun throwCard(dir: Int, velocity: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            if (abs(drag.value) >= CardDeckGeometry.RISE) land() // önceki fırlama yarım kaldıysa bitir
            if (reduceMotion) {
                top = Math.floorMod(top + 1, count); drag.snapTo(0f)
                return@launch
            }
            drag.animateTo(
                dir * CardDeckGeometry.FLY,
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.002f),
                initialVelocity = velocity,
            )
            land()
        }
    }

    // Otomatik: kart sırayla sola ve sağa fırlar.
    AutoAdvance(paused = touching) { throwCard(if (top % 2 == 0) -1 else 1, 0f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        val wPx = with(LocalDensity.current) { w.toPx() }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(w * CardDeckGeometry.SCENE_H)
                    .trackTouch { touching = it }
                    .pointerInput(count, wPx) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                // Fırlamakta olan kart varsa hemen dibe geçsin, yeni kartla başla.
                                val flying = abs(drag.value) >= CardDeckGeometry.RISE
                                job?.cancel()
                                if (flying) scope.launch { land() }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x / wPx
                                when (val dir = CardDeckGeometry.release(drag.value, v)) {
                                    0 -> {
                                        job = scope.launch {
                                            drag.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialVelocity = v)
                                        }
                                    }
                                    else -> throwCard(dir, v)
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.55f)) } },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { drag.snapTo(drag.value + dx / wPx) }
                        }
                    }
                    .pointerInput(items, wPx) {
                        detectTapGestures { at ->
                            val u = (at.x - size.width / 2f) / wPx
                            if (abs(u) <= CardDeckGeometry.CARD_W / 2f && abs(drag.value) < 0.05f) {
                                onOpen(items[Math.floorMod(top, count)])
                            } else if (abs(u) > CardDeckGeometry.CARD_W / 2f) {
                                throwCard(if (u < 0f) -1 else 1)
                            }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                items.forEachIndexed { index, item ->
                    val depth = CardDeckGeometry.depth(index, current, count)
                    DeckCard(
                        item = item,
                        modifier = Modifier
                            .zIndex((count - depth).toFloat())
                            .padding(top = w * CardDeckGeometry.TOP)
                            .size(w * CardDeckGeometry.CARD_W, w * CardDeckGeometry.CARD_H)
                            .graphicsLayer {
                                val p = if (depth == 0) {
                                    CardDeckGeometry.thrown(drag.value)
                                } else {
                                    CardDeckGeometry.rest(index, depth - CardDeckGeometry.rise(drag.value))
                                }
                                // Eğim kartın altına yakın bir noktadan: üst kenar parmak yönüne yatar.
                                transformOrigin = if (depth == 0) TransformOrigin(0.5f, 1.15f) else TransformOrigin.Center
                                translationX = p.x * wPx
                                translationY = p.y * wPx
                                rotationZ = p.rotation
                                scaleX = p.scale
                                scaleY = p.scale
                                // Dibe yeni geçen kart (yalnız görünen en arkadaysa) yumuşakça belirir.
                                alpha = p.alpha * if (depth == count - 1) backIn.value else 1f
                                shadowElevation = (if (depth == 0) 16f else 8f) * density
                                shape = RoundedCornerShape(18.dp)
                                clip = true
                            },
                    )
                }
            }
            Text(
                text = items[current].title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SliderDots(count = count, current = current, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        }
    }
}

/** Tek afiş kartı: yüklenirken tema yüzeyi, üstte ince ışık kenarı. */
@Composable
private fun DeckCard(item: CatalogItem, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.18f))))
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
        )
    }
}
