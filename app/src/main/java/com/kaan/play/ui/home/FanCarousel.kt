package com.kaan.play.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Yelpaze: 21st.dev "Card Fan Carousel" görsel fikri yerel olarak yeniden yazıldı (kod
 * kopyalanmadı). Ortadaki kart dik ve büyük; her yanda en çok 3 kart ±7/±14/±21° döner, küçülür, yana ve biraz aşağı kayar.
 */
internal object FanGeometry {
    const val HALF = 3
    private val ROT = floatArrayOf(0f, 7f, 14f, 21f)
    private val SCALE = floatArrayOf(1f, 0.9346f, 0.8498f, 0.7756f)
    // Özgün yerleşim birimleri (rem); çağıran kart genişliğine göre ölçekler.
    private val X = floatArrayOf(0f, 11f, 22f, 30f)
    private val Y = floatArrayOf(0f, 1.3f, 4.0f, 7.3f)

    data class Slot(val rotation: Float, val scale: Float, val x: Float, val y: Float, val z: Float, val alpha: Float)

    /** Kartın merkeze göre (kesirli) uzaklığından yerleşimi; tam sayılar arası doğrusal geçiş. */
    fun slot(offset: Float, count: Int): Slot {
        val a = abs(offset)
        val s = sign(offset)
        val i = floor(a).toInt().coerceAtMost(HALF - 1)
        val t = (a - i).coerceAtMost(1f)
        fun lerp(v: FloatArray) = v[i] + (v[i + 1] - v[i]) * t
        // Çemberde karşı kenara atlayan kart görünmeden atlasın: yelpazenin dışına doğru solar.
        val limit = minOf(HALF + 0.5f, count / 2f)
        return Slot(
            rotation = s * lerp(ROT),
            scale = lerp(SCALE),
            x = s * lerp(X),
            y = lerp(Y),
            z = HALF + 1 - a,
            alpha = ((limit - a) * 2f).coerceIn(0f, 1f),
        )
    }

    /** Döngüsel uzaklık: [index] kartının [position] merkezine göre yeri, [-count/2, count/2) aralığında. */
    fun offset(index: Int, position: Float, count: Int): Float {
        var d = (index - position) % count
        if (d < -count / 2f) d += count
        if (d >= count / 2f) d -= count
        return d
    }

    /** Sürükleme bitince gidilecek kart: küçük kıpırdama yerinde kalır, her kaydırma tek kart ilerler (özgündeki oklar gibi). */
    fun settleTarget(start: Float, now: Float): Float {
        val moved = now - start
        val from = start.roundToInt().toFloat()
        return when {
            abs(moved) < 0.15f -> from
            moved > 0 -> from + 1f
            else -> from - 1f
        }
    }

    fun index(position: Float, count: Int): Int = Math.floorMod(position.roundToInt(), count)
}

/** Shortizle sitesinin eklentisi: adında (Türkçe harfler sadeleşmiş) "shortizle" geçer. */
internal fun isShortizleProvider(name: String): Boolean =
    name.replace('İ', 'i').replace('ı', 'i').lowercase(Locale.ROOT).filter { it in 'a'..'z' }.contains("shortizle")

private const val FAN_AUTOPLAY_MS = 5_000L

@Composable
internal fun FanCarousel(
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
    var introDone by rememberSaveable { mutableStateOf(false) }
    val position = remember { Animatable(savedIndex.toFloat()) }
    val spread = remember { Animatable(if (introDone || reduceMotion) 1f else 0f) }
    var touching by remember { mutableStateOf(false) }
    val springSpec = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    fun settle(target: Float) {
        savedIndex = FanGeometry.index(target, count)
        scope.launch { if (reduceMotion) position.snapTo(target) else position.animateTo(target, springSpec) }
    }

    LaunchedEffect(Unit) {
        // Açılışta kartlar ortadan yelpaze gibi açılır (yaylı).
        spread.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        introDone = true
    }
    // Yavaş otomatik kaydırma; dokunurken durur, bırakınca süre baştan sayılır.
    LaunchedEffect(touching, count, reduceMotion) {
        if (touching || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(FAN_AUTOPLAY_MS)
            settle(position.value.roundToInt() + 1f)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = minOf(maxWidth * 0.40f, 220.dp)
        val cardHeight = cardWidth * 1.5f
        val unit = cardWidth * (0.85f / 30f) // 30 rem → en dıştaki kart merkezi ≈ 0.85 kart genişliği
        val stepPx = with(LocalDensity.current) { (cardWidth * 0.8f).toPx() }
        val centre = FanGeometry.index(position.value, count)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight + unit * 7.3f + 24.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                touching = event.changes.any { it.pressed }
                            }
                        }
                    }
                    .pointerInput(count, stepPx) {
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { start = position.value },
                            onDragEnd = { settle(FanGeometry.settleTarget(start, position.value)) },
                            onDragCancel = { settle(FanGeometry.settleTarget(start, position.value)) },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { position.snapTo(position.value - dx / stepPx) }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                items.forEachIndexed { index, item ->
                    val d = FanGeometry.offset(index, position.value, count)
                    val slot = FanGeometry.slot(d, count)
                    if (slot.alpha <= 0f) return@forEachIndexed
                    val k = spread.value
                    Box(
                        Modifier
                            .zIndex(slot.z)
                            .padding(top = 8.dp)
                            .size(cardWidth, cardHeight)
                            .graphicsLayer {
                                translationX = (unit * slot.x * k).toPx()
                                translationY = (unit * slot.y * k).toPx()
                                rotationZ = slot.rotation * k
                                val sc = 1f - (1f - slot.scale) * k
                                scaleX = sc
                                scaleY = sc
                                alpha = slot.alpha
                            }
                            .shadow(10.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val step = d.roundToInt()
                                if (step == 0) onOpen(item) else settle(position.value.roundToInt() + step.toFloat())
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
                    val w by animateDpAsState(if (i == centre) 18.dp else 6.dp, label = "fanDot")
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
