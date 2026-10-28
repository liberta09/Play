package com.kaan.play.ui.tv

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
        if (!focused) return
        val stroke = RingWidth.toPx()
        val inset = stroke / 2f
        val minDim = minOf(size.width, size.height)
        // Küçük öğeler (ikon düğmeleri, hap düğmeler) yuvarlak uçlu; büyük öğeler (kart, satır) 12dp köşeli.
        val radius = if (minDim <= 64.dp.toPx()) minDim / 2f else 12.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
            style = Stroke(stroke),
        )
    }
}

/**
 * `LocalIndication` kullanmayan öğeler (alt/yan menü öğesi, anahtar) için aynı halka. [shape] öğenin biçimidir.
 * [color]: turkuaz zemin üzerindeki (dolu) düğmelerde yazı rengi gibi ters bir renk verilir. Kullanım: `Modifier.focusRing(shape)` + normal tıklama/seçme değiştiricisi bunun ardından gelir.
 */
@Composable
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(12.dp), color: Color = MaterialTheme.colorScheme.primary): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(RingWidth, color, shape) else Modifier)
}

/**
 * Bir ekranın son odaklanan öğesini hatırlar (listelerde anahtarla): ekrandan çıkıp geri dönünce (ör. Detay → Geri)
 * odak aynı öğeye döner; ilk açılışta [focusMemory]'nin `isDefault` verdiği öğeye gider. Durum `rememberSaveable`
 * ile geri yığınında saklanır. Odak yalnızca kumanda/klavye kipindeyken istenir (dokunmatikte hiçbir şey olmaz).
 */
class FocusMemory(internal val restoreKey: String? = null) {
    internal var lastKey: String? = restoreKey
    internal val requester = FocusRequester()

    internal suspend fun requestInitial() = requester.requestWhenAttached()

    companion object {
        val Saver: Saver<FocusMemory, String> = Saver(
            save = { it.lastKey ?: "" },
            restore = { FocusMemory(it.ifEmpty { null }) },
        )
    }
}

/** [enabled] false ise (ör. ekran yüklenirken) başlangıç odağı istenmez. */
@Composable
fun rememberFocusMemory(enabled: Boolean = true): FocusMemory {
    val memory = rememberSaveable(saver = FocusMemory.Saver) { FocusMemory() }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(memory, enabled, keyboard) {
        if (enabled && keyboard) memory.requestInitial()
    }
    return memory
}

/**
 * Bu öğeyi [memory]'ye bağlar: odaklanınca anahtarını kaydeder; geri dönüşte (kayıtlı anahtar buysa) ya da ilk
 * açılışta ([isDefault]) başlangıç odağını alır.
 */
@Composable
fun Modifier.focusMemory(memory: FocusMemory, key: String, isDefault: Boolean = false): Modifier {
    val isTarget = if (memory.restoreKey != null) memory.restoreKey == key else isDefault
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
