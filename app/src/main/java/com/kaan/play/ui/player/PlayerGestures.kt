package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val INDICATOR_HIDE_MS = 1_000L

/**
 * Kaydırma jestinin sayılmayacağı alanlar (kontrol çubukları, düğmeler, ilerleme çubuğu). Her alan ekranda
 * olduğu sürece konumunu bildirir, ekrandan kalkınca (kontroller gizlenince) silinir.
 */
@Stable
class GestureZones {
    private val rects = HashMap<Any, Rect>()

    fun set(key: Any, rect: Rect) { rects[key] = rect }
    fun remove(key: Any) { rects.remove(key) }
    fun contains(point: Offset): Boolean = rects.values.any { it.contains(point) }
}

/** Bu öğenin kapladığı alanı kaydırma jestinden dışlar. */
@Composable
fun Modifier.gestureZone(zones: GestureZones, key: Any): Modifier {
    DisposableEffect(zones, key) { onDispose { zones.remove(key) } }
    return onGloballyPositioned { zones.set(key, it.boundsInRoot()) }
}

/** Göstergenin durumu: hangi düzey, yüzde ve parmak ekranda mı. Parmak kalkınca 1 sn sonra gizlenir. */
@Stable
class LevelIndicatorState {
    var kind by mutableStateOf<LevelKind?>(null)
        internal set
    var percent by mutableIntStateOf(0)
        internal set
    var held by mutableStateOf(false)
        internal set
    internal var version by mutableIntStateOf(0)

    internal fun show(kind: LevelKind, level: Float) {
        this.kind = kind
        percent = LevelRules.percent(level)
        held = true
        version++
    }

    internal fun release() { held = false }
}

/**
 * Ekranın sağ yarısında dikey kaydırma sesi, sol yarısında bu pencerenin parlaklığını değiştirir. Başlangıç
 * değeri gerçek ses/parlaklıktır; dokunma eşiği aşılana dek bir şey olmaz, sonra kayma orantılı ilerler.
 * [levels] null ise (TV/kumanda) hiçbir şey eklenmez. Tek dokunuşu bozmamak için `clickable`dan SONRA
 * (daha içte) eklenir: eşik aşılınca olayı tüketir, dokunma tıklama sayılmaz.
 */
@Composable
fun Modifier.levelGestures(levels: DeviceLevels?, zones: GestureZones, indicator: LevelIndicatorState): Modifier {
    if (levels == null) return this
    return pointerInput(levels, zones, indicator) {
        var kind: LevelKind? = null
        var level = 0f
        detectVerticalDragGestures(
            onDragStart = { start ->
                kind = LevelRules.kindAt(start.x, size.width.toFloat())
                    .takeIf { !zones.contains(start) && (it != LevelKind.Volume || levels.volumeAdjustable) }
                kind?.let {
                    level = levels.level(it)
                    indicator.show(it, level)
                }
            },
            onVerticalDrag = { _, dy ->
                kind?.let {
                    level = LevelRules.move(level, -dy, size.height.toFloat())
                    levels.set(it, level)
                    // Gösterge gerçek değeri gösterir (ses tamsayı basamaktır).
                    indicator.show(it, levels.level(it))
                }
            },
            onDragEnd = { kind = null; indicator.release() },
            onDragCancel = { kind = null; indicator.release() },
        )
    }
}

/** Video alanında çift dokunma: sol yarı 10 saniye geri, sağ yarı 10 saniye ileri. */
fun Modifier.doubleTapSeek(
    enabled: Boolean,
    zones: GestureZones,
    onSeek: (Long) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(zones) {
        detectTapGestures(
            onDoubleTap = { offset ->
                if (!zones.contains(offset)) {
                    onSeek(if (offset.x < size.width / 2f) -10_000L else 10_000L)
                }
            },
        )
    }
}

/** Video alanında yatay kaydırma: sağa 5 sn ileri, sola 5 sn geri; kontrol alanlarında çalışmaz. */
fun Modifier.horizontalSeekGesture(
    enabled: Boolean,
    zones: GestureZones,
    onSeek: (Long) -> Unit,
    indicator: SeekIndicatorState? = null,
    onGestureStateChange: (Boolean) -> Unit = {},
): Modifier {
    if (!enabled) return this
    return pointerInput(zones) {
        var active = false
        var remainder = 0f
        detectHorizontalDragGestures(
            onDragStart = { start ->
                active = !zones.contains(start)
                remainder = 0f
                if (active) {
                    indicator?.start()
                    onGestureStateChange(true)
                }
            },
            onHorizontalDrag = { change, amount ->
                if (!active) return@detectHorizontalDragGestures
                change.consume()
                remainder += amount
                while (abs(remainder) >= 24f) {
                    val direction = if (remainder > 0f) 1 else -1
                    val step = direction * 5_000L
                    onSeek(step)
                    indicator?.add(step)
                    remainder -= direction * 24f
                }
            },
            onDragEnd = {
                if (active) onGestureStateChange(false)
                active = false; remainder = 0f; indicator?.finish()
            },
            onDragCancel = {
                if (active) onGestureStateChange(false)
                active = false; remainder = 0f; indicator?.finish()
            },
        )
    }
}

@Stable
class SeekIndicatorState {
    var deltaMs by mutableLongStateOf(0L)
        private set
    var held by mutableStateOf(false)
        private set
    internal var version by mutableIntStateOf(0)

    internal fun start() { deltaMs = 0L; held = true; version++ }
    internal fun add(delta: Long) { deltaMs += delta; version++ }
    internal fun finish() { held = false; version++ }
    internal fun clear() { deltaMs = 0L }
}

@Composable
fun SeekIndicator(state: SeekIndicatorState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.held, state.version) {
        if (!state.held && state.deltaMs != 0L) {
            delay(800L)
            state.clear()
        }
    }
    if (state.deltaMs == 0L) return
    val seconds = kotlin.math.abs(state.deltaMs) / 1_000L
    val sign = if (state.deltaMs > 0L) "+" else "−"
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$sign${seconds} sn",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Kaydırırken çıkan küçük gösterge: ikon + yüzde, ilgili yarıda durur; jest bitince 1 sn sonra gizlenir. */
@Composable
fun LevelIndicator(state: LevelIndicatorState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.held, state.version) {
        if (!state.held && state.kind != null) {
            delay(INDICATOR_HIDE_MS)
            state.kind = null
        }
    }
    val kind = state.kind ?: return
    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 40.dp)) {
        Row(
            modifier = Modifier
                .align(if (kind == LevelKind.Volume) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (kind == LevelKind.Volume) PlayIcons.Volume else PlayIcons.Brightness,
                contentDescription = stringResource(if (kind == LevelKind.Volume) R.string.player_volume else R.string.player_brightness),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.player_level_percent, state.percent),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
