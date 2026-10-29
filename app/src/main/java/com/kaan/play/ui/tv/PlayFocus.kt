package com.kaan.play.ui.tv

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

private val RingWidth = 3.dp

/**
 * Odaktaki her tıklanabilir öğeye çizilen turkuaz çerçeve (TV/kumanda/klavye). Tüm uygulamada `LocalIndication`
 * olarak verilir ([com.kaan.play.ui.theme.PlayTheme]); böylece `clickable`, `Button`, `IconButton`, `selectable`
 * satırları ek bir şey yapmadan odak halkası alır. Dokunmada görünüm değişmez; normal dalga efekti korunur.
 */
@Immutable
class PlayIndication(private val ringColor: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RingNode(interactionSource, ringColor)
    override fun equals(other: Any?) = other is PlayIndication && other.ringColor == ringColor
    override fun hashCode() = ringColor.hashCode()
}

private class RingNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : DelegatingNode(), DrawModifierNode {

    private val ripple = delegate(ripple().create(interactionSource))
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

/**
 * TV Kumandası için Merkezi Premium Odak Motoru:
 * - Pürüzsüz Scale (Öne Çıkma): Odaklanan öğe spring animasyonuyla öne çıkar (1.08f) ve [zIndex] ile diğer öğelerin üzerinde durur.
 * - Neon Ambient Glow (Parlama): Odaklanan öğenin arkasında ve çevresinde elektrik camgöbeği (#00E5FF) tonlarında yumuşak neon ışığı ve donanımsal derinlik gölgesi yayılır (elevation = 24.dp).
 * - Sert çizgiler (border/stroke) KESİNLİKLE YOKTUR.
 */
@Composable
fun Modifier.tvFocusableScaleGlow(
    scale: Float = 1.06f,
    glowColor: Color = Color(0xFF00E5FF),
    shape: Shape = RoundedCornerShape(12.dp),
    isFocused: Boolean? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    zIndex: Float = 10f,
    haloSpread: Dp = 0.dp,
    strokeWidth: Dp = 0.dp,
    fillGlowAlpha: Float = 0f,
): Modifier {
    var internalFocused by remember { mutableStateOf(false) }
    val currentFocused = isFocused ?: internalFocused

    val animatedScale by animateFloatAsState(
        targetValue = if (currentFocused) scale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tv_scale",
    )

    val focusProgress by animateFloatAsState(
        targetValue = if (currentFocused) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tv_focus_progress",
    )

    val borderModifier = if (focusProgress > 0.01f) {
        Modifier
            .border(width = 3.dp, color = Color(0xFF00E5FF).copy(alpha = 0.25f * focusProgress), shape = shape)
            .border(width = 1.dp, color = Color(0xFF80F2FF).copy(alpha = 0.85f * focusProgress), shape = shape)
    } else {
        Modifier
    }

    return this
        .onFocusChanged { state ->
            internalFocused = state.hasFocus
            onFocusChange?.invoke(state.hasFocus)
        }
        .zIndex(if (currentFocused) zIndex else 0f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            this.shape = shape
            this.clip = false
        }
        .then(borderModifier)
        .then(
            if (fillGlowAlpha > 0.001f && focusProgress > 0.01f) {
                Modifier.drawBehind {
                    val minDim = minOf(size.width, size.height)
                    val cornerRadius = when (shape) {
                        CircleShape -> CornerRadius(minDim / 2f, minDim / 2f)
                        is RoundedCornerShape -> CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        else -> CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    }
                    drawRoundRect(
                        color = Color(0xFF00E5FF).copy(alpha = fillGlowAlpha * focusProgress),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = cornerRadius,
                    )
                }
            } else {
                Modifier
            }
        )
}

/**
 * Kategori başlıkları için çizgisiz, pürüzsüz TV odak motoru:
 * - Kaba border / çizgi YOKTUR (strokeWidth = 0.dp, haloSpread = 0.dp).
 * - Odaklandığında satır çok hafifçe (1.02f) öne çıkar.
 * - Arkasında yumuşak neon cyan zemin parlaması (glow) oluşur.
 * - [LocalIndication] çerçevesini önlemek için [indication] = null olarak bağlanır.
 */
@Composable
fun Modifier.categoryHeaderFocusable(
    interactionSource: MutableInteractionSource,
    onClick: (() -> Unit)? = null,
    scale: Float = 1.02f,
    glowColor: Color = Color(0xFF00E5FF),
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    return this
        .tvFocusableScaleGlow(
            scale = scale,
            glowColor = glowColor,
            shape = shape,
            isFocused = isFocused,
            zIndex = 2f,
            haloSpread = 0.dp,
            strokeWidth = 0.dp,
            fillGlowAlpha = 0.15f,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier.focusable(interactionSource = interactionSource)
            }
        )
}

/**
 * `Modifier.focusRing`: Artık basit ve sert border yerine merkezi `tvFocusableScaleGlow` motorunu kullanır.
 * Böylece tüm mevcut `focusRing` çağrıları otomatik olarak pürüzsüz büyüme ve neon glow efekti kazanır.
 */
@Composable
fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = Color(0xFF00E5FF),
    scale: Float = 1.02f,
): Modifier {
    return this.tvFocusableScaleGlow(
        scale = scale,
        glowColor = color,
        shape = shape,
    )
}

/**
 * Bir ekranın son odaklanan öğesini hatırlar (listelerde anahtarla): ekrandan çıkıp geri dönünce (ör. Detay → Geri)
 * odak aynı öğeye döner; ilk açılışta [focusMemory]'nin `isDefault` verdiği öğeye gider. Durum `rememberSaveable`
 * ile geri yığınında saklanır. Odak yalnızca kumanda/klavye kipindeyken istenir (dokunmatikte hiçbir şey olmaz).
 */
class FocusMemory(internal val restoreKey: String? = null) {
    internal var lastKey: String? = restoreKey
    internal var forceDefault: Boolean = false
    internal val requester = FocusRequester()

    internal suspend fun requestInitial() = requester.requestWhenAttached()

    companion object {
        val Saver: Saver<FocusMemory, String> = Saver(
            save = { it.lastKey ?: "" },
            restore = { FocusMemory(it.ifEmpty { null }) },
        )
    }
}

/** Leanback/ekransız cihazlarda kumanda girdisi dokunmatik kipinde görünse bile TV odağını algılar. */
@Composable
fun isTelevisionDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val packageManager = context.packageManager
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}

/** [enabled] false ise başlangıç odağı istenmez; [forceOnTouch] TV kumandası için dokunmatik kipini yok sayar. */
@Composable
fun rememberFocusMemory(enabled: Boolean = true, forceOnTouch: Boolean = false, resetKey: Any? = null): FocusMemory {
    val memory = rememberSaveable(saver = FocusMemory.Saver) { FocusMemory() }
    memory.forceDefault = forceOnTouch
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(memory, enabled, keyboard, forceOnTouch, resetKey) {
        if (enabled && (keyboard || forceOnTouch)) memory.requestInitial()
    }
    return memory
}

/**
 * Bu öğeyi [memory]'ye bağlar: odaklanınca anahtarını kaydeder; geri dönüşte (kayıtlı anahtar buysa) ya da ilk
 * açılışta ([isDefault]) başlangıç odağını alır.
 */
@Composable
fun Modifier.focusMemory(memory: FocusMemory, key: String, isDefault: Boolean = false): Modifier {
    val isTarget = if (memory.forceDefault) isDefault else if (memory.restoreKey != null) memory.restoreKey == key else isDefault
    val tracked = this.onFocusChanged { if (it.hasFocus) memory.lastKey = key }
    return if (isTarget) tracked.focusRequester(memory.requester) else tracked
}

/**
 * Odak isteği: öğeler (tembel listelerde, açılır pencerelerde) henüz kurulmamış olabilir, bu yüzden başarana kadar
 * birkaç kare denenir.
 */
internal suspend fun FocusRequester.requestWhenAttached(frames: Int = 12) {
    repeat(frames) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return
    }
}

/**
 * Tek bir öğeye (ör. ana düğme) başlangıç odağı verir; yalnızca kumanda/klavye kipindeyken. Dönen [FocusRequester]
 * `Modifier.focusRequester` ile o öğeye bağlanır. [enabled] false iken (içerik yüklenmedi) bekler.
 */
@Composable
fun rememberInitialFocus(enabled: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(requester, enabled, keyboard) {
        if (enabled && keyboard) requester.requestWhenAttached()
    }
    return requester
}

/**
 * Tıklanabilir bir satırın (odak hedefi) içindeki denetimlere (anahtar, sil düğmesi) kumandayla geçiş. Satır odaktayken
 * yön tuşları kendi çocuklarına inmez; bu zincir satır → denetimler (SAĞ) ve geri (SOL) geçişini açıkça bağlar.
 * Kullanım: satıra [rowControlsHost], i. denetime [rowControl].
 */
class RowControls internal constructor(count: Int) {
    internal val row = FocusRequester()
    internal val items = List(count) { FocusRequester() }
}

@Composable
fun rememberRowControls(count: Int): RowControls = remember(count) { RowControls(count) }

/** Satır (tıklanabilir odak hedefi) için: SAĞ → ilk denetim. */
fun Modifier.rowControlsHost(controls: RowControls): Modifier =
    focusRequester(controls.row).focusProperties { right = controls.items.first() }

/** [index]. denetim için: SOL → önceki denetim ya da satır ([hosted]), SAĞ → sonraki denetim. */
fun Modifier.rowControl(controls: RowControls, index: Int, hosted: Boolean = true): Modifier =
    focusRequester(controls.items[index]).focusProperties {
        if (hosted) left = if (index == 0) controls.row else controls.items[index - 1]
        right = if (index < controls.items.lastIndex) controls.items[index + 1] else FocusRequester.Default
    }

/**
 * Metin alanı değeri + imleç konumu. `OutlinedTextField(value: String)` imleci dışarı vermez; bu sınıf Compose'un kendi
 * String uyarlamasıyla aynı yolu izler (metin dışarıdan gelir, seçim/yazım durumu burada tutulur) ve alanın başında olup
 * olmadığını söyler. [dpadLeavesTextField] bunu Sol tuşu için kullanır.
 */
@Stable
class CursorField internal constructor(val value: TextFieldValue, val onValueChange: (TextFieldValue) -> Unit) {
    /** İmleç metnin en başında (ve seçim yok). */
    val atStart: Boolean get() = value.selection.collapsed && value.selection.start == 0
}

@Composable
fun rememberCursorField(text: String, onTextChange: (String) -> Unit): CursorField {
    var state by remember { mutableStateOf(TextFieldValue(text)) }
    val value = state.copy(text = text)
    return CursorField(value) { next ->
        state = next
        if (next.text != text) onTextChange(next.text)
    }
}

/** Geri tuşu metin alanındayken önce Android TV ekran klavyesini kapatır. */
@Composable
fun DismissImeOnBack(textFieldFocused: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = textFieldFocused || imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }
}

/** Android TV klavyelerinin Tamam/Geri tuşlarını IME action dışındaki KeyEvent biçiminde göndermesine de dayanır. */
@Composable
fun Modifier.dismissImeOnDoneOrBack(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (event.key == Key.Enter || event.key == Key.Back || event.key == Key.Escape) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            true
        } else {
            false
        }
    }
}

/**
 * Kumanda: tek satırlık metin alanı Aşağı/Yukarı tuşunu yutar ve odak alandan çıkamaz. Bu değiştirici Aşağı/Yukarı'yı
 * odak hareketine çevirir (alanın altındaki sonuç ızgarasına, düğmelere geçilir). Sol/Sağ imleci taşımaya devam eder;
 * tek istisna: [field] verilmişse ve imleç metnin başındaysa Sol odağı sola (yan menü/rail) taşır.
 */
@Composable
fun Modifier.dpadLeavesTextField(field: CursorField? = null): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionLeft -> field != null && field.atStart && focusManager.moveFocus(FocusDirection.Left)
            else -> false
        }
    }
}
