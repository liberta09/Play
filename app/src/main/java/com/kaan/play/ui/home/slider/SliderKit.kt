package com.kaan.play.ui.home.slider

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay

/**
 * Eklenti sürgülerinin ortak davranışı. Yeni stiller bunları kullanır; ilk üç stil (yelpaze, halka, koyu kibrit
 * kutusu) kendi eski kodunu korur. Renkler daima `MaterialTheme.colorScheme`'den gelir (bkz. `play/SLIDERS.md`).
 */
internal object SliderMath {
    const val AUTOPLAY_MS = 4_500L
    const val SWIPE_FRACTION = 1f / 3f
    const val FLING = 1.2f // öğe/sn

    /** Döngüsel uzaklık: [index] öğesinin [position] merkezine göre yeri, [-count/2, count/2) aralığında. */
    fun offset(index: Int, position: Float, count: Int): Float {
        var d = (index - position) % count
        if (d < -count / 2f) d += count
        if (d >= count / 2f) d -= count
        return d
    }

    /** Kesirli konumdaki ortadaki öğe. */
    fun index(position: Float, count: Int): Int = Math.floorMod(position.roundToInt(), count)

    /**
     * Sürükleme bitince gidilecek yer: öğe payının [fraction]'ı ya da hızlı fiske ([velocity] öğe/sn) bir öğe ilerletir;
     * bir öğeden uzun sürüklemede en yakın öğe; küçük kıpırdama yerinde kalır.
     */
    fun settleTarget(start: Float, now: Float, velocity: Float, fraction: Float = SWIPE_FRACTION, fling: Float = FLING): Float {
        val from = start.roundToInt().toFloat()
        val moved = now - from
        return when {
            abs(moved) >= fraction -> if (abs(moved) < 1f) from + sign(moved) else now.roundToInt().toFloat()
            abs(velocity) >= fling -> from + sign(velocity)
            else -> from
        }
    }

    /** Ortaya göre dokunulan yer ([u], [side] ile aynı birimde): yan öğe ±1 adım, orta 0 (detayı aç). */
    fun tapStep(u: Float, side: Float): Int = when {
        u > side -> 1
        u < -side -> -1
        else -> 0
    }

    /** Sayaç metni: "02 / 06". */
    fun counter(current: Int, count: Int): Pair<String, String> =
        "%02d".format(Locale.ROOT, current + 1) to " / %02d".format(Locale.ROOT, count)
}

/** Sürgü zemin renkleri: daima tema yüzeylerinden türer (6 temanın hepsinde uyumlu). */
internal object SliderColors {
    /**
     * Masa/zemin (uzak, yakın): açık temada `surfaceVariant`/`surface` kâğıt kremine çekilir; koyu temalarda (Koyu,
     * AMOLED, Okyanus, Mor gece, Yüksek kontrast) aynı yüzeyler ılık koyuya hafifçe çekilir, temanın tonu kalır.
     */
    fun table(surface: Color, surfaceVariant: Color): Pair<Color, Color> =
        if (surface.luminance() > 0.5f) {
            lerp(surfaceVariant, PaperFar, 0.55f) to lerp(surface, PaperNear, 0.55f)
        } else {
            lerp(surfaceVariant, DuskFar, 0.3f) to lerp(surface, DuskNear, 0.3f)
        }

    private val PaperFar = Color(0xFFE2D9CC)
    private val PaperNear = Color(0xFFEDE7DE)
    private val DuskFar = Color(0xFF2E2822)
    private val DuskNear = Color(0xFF1C1814)
}

/** Sistem "animasyon süresi ölçeği" kapalıysa (0) sade geçiş: kayma yok, öğe yerinde değişir. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Otomatik ilerleme: her [intervalMs]'de [onAdvance]. [paused] iken (dokunurken) ve uygulama ön planda değilken durur,
 * sonra süre baştan sayılır. Ekran dışına kayan sürgü LazyColumn'dan atıldığı için kendiliğinden durur.
 */
@Composable
internal fun AutoAdvance(paused: Boolean, intervalMs: Long = SliderMath.AUTOPLAY_MS, onAdvance: () -> Unit) {
    val advance by rememberUpdatedState(onAdvance)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(paused, resumed, intervalMs) {
        if (paused || !resumed) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            advance()
        }
    }
}

/** Parmak ekrandayken [onTouch] true (alt öğelerin dokunuşunu tüketmeden izler). */
internal fun Modifier.trackTouch(onTouch: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            onTouch(event.changes.any { it.pressed })
        }
    }
}

/** Ortak alt nokta göstergesi (Shortizle yelpazesindeki gibi): etkin olan uzun hap `primary`, diğerleri soluk. */
@Composable
internal fun SliderDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 18.dp else 6.dp, label = "sliderDot")
            Box(
                Modifier
                    .size(width = w, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
            )
        }
    }
}

/** Sayaç "02 / 06": geçerli sayı `onSurface`, toplam `onSurfaceVariant`. */
@Composable
internal fun SliderCounter(current: Int, count: Int, modifier: Modifier = Modifier) {
    val (now, total) = SliderMath.counter(current, count)
    val strong = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = strong)) { append(now) }
            withStyle(SpanStyle(color = muted)) { append(total) }
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

/**
 * İçi boş halka ok düğmesi (isteğe bağlı): halka ve ok `onSurface` tonunda, basılıyken `primary`. Ok simgesi burada
 * çizilir (simge setine bağımlılık yok).
 */
@Composable
internal fun SliderArrowButton(
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val tint by animateColorAsState(if (pressed) MaterialTheme.colorScheme.primary else idle, label = "sliderArrow")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, tint, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.36f).padding(1.dp)) {
            val w = this.size.width
            val h = this.size.height
            val tip = if (forward) w * 0.72f else w * 0.28f
            val tail = if (forward) w * 0.32f else w * 0.68f
            val stroke = 1.6.dp.toPx()
            drawLine(tint, Offset(tail, 0f), Offset(tip, h / 2f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(tip, h / 2f), Offset(tail, h), stroke, StrokeCap.Round)
        }
    }
}
