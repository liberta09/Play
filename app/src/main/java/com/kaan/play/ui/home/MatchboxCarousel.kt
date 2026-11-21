package com.kaan.play.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.ui.home.slider.SliderColors
import com.kaan.play.ui.theme.LocalPlayThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Kibrit kutusu: "Matchbox Carousel" görsel fikri yerel Compose ile yeniden yazıldı
 * (kod kopyalanmadı). Telefon biçimi: kutular masada dik durur, kılıfta dizi adı; açık kutunun çekmecesi izleyiciye doğru (aşağı)
 * kayar ve içindeki afiş görünür. Geçiş: çekmece kapanır → sıra kayar → yeni çekmece açılır. Birimler sahne birimi (sahne 360 birim
 * genişlik). Bkz. `docs/drafts/dramavakti-matchbox-istegi-2026-11-12.md`.
 */
internal object MatchboxGeometry {
    const val STAGE = 360f
    const val SLEEVE_W = 168f
    const val SLEEVE_H = 236f
    const val STEP = 196f // kutu merkezleri arası
    const val TRAY_INSET = 5f
    const val TRAY_LEN = SLEEVE_H - 2 * TRAY_INSET
    const val OPEN = TRAY_LEN * 0.86f // tam açık çekmecenin kayma boyu
    const val TOP = 34f
    const val SCENE_H = TOP + SLEEVE_H + OPEN + 26f
    const val TAP_SIDE = 100f
    const val TILT = 30f // masaya eğik bakış (derece)
    const val PIVOT_Y = 0.2f // perspektif kökeni %50 %20
    const val PERSPECTIVE = 2.2f // kamera uzaklığı = max(G, Y) × 2.2

    const val CLOSE_MS = 380
    const val SHIFT_MS = 560
    const val OPEN_MS = 620
    const val AUTOPLAY_MS = 4_500L
    private const val SWIPE_FRACTION = 1f / 3f
    private const val FLING = 1.2f // kutu/sn

    val SLEEVES = intArrayOf(
        0xFFC8553D.toInt(), 0xFF2F4B7C.toInt(), 0xFFE2B13C.toInt(), 0xFF5A7D5A.toInt(),
        0xFF1F1D1B.toInt(), 0xFFD98CA2.toInt(), 0xFF3E8A95.toInt(),
    )
    const val INK_DARK = 0xFF1F1D1B.toInt()
    const val INK_LIGHT = 0xFFF2EDE5.toInt()

    /** Kutunun sıradaki yatay yeri (merkeze göre kesirli uzaklık [q]). */
    fun boxX(q: Float): Float = q * STEP

    /** Az kutuda karşı kenara atlayan kutu görünmeden atlasın. */
    fun alpha(q: Float, count: Int): Float = ((count / 2f - abs(q)) * 4f).coerceIn(0f, 1f)

    /** Kısmen açık çekmece kalan yol oranında kısa sürede kapanır / açılır. */
    fun closeMs(open: Float): Int = (CLOSE_MS * open.coerceIn(0f, 1f)).roundToInt()
    fun openMs(open: Float): Int = (OPEN_MS * (1f - open.coerceIn(0f, 1f))).roundToInt()

    /** Sürükleme bitince: kutu payının 1/3'ü ya da hızlı fiske bir kutu ilerletir; uzun sürüklemede en yakın kutu. */
    fun settleTarget(start: Float, now: Float, velocity: Float): Float {
        val from = start.roundToInt().toFloat()
        val moved = now - from
        return when {
            abs(moved) >= SWIPE_FRACTION -> if (abs(moved) < 1f) from + sign(moved) else now.roundToInt().toFloat()
            abs(velocity) >= FLING -> from + sign(velocity)
            else -> from
        }
    }

    /** Sahne birimiyle dokunulan yer: yan kutu ±1 adım, orta 0 (aç). */
    fun tapStep(u: Float): Int = when {
        u > TAP_SIDE -> 1
        u < -TAP_SIDE -> -1
        else -> 0
    }

    fun sleeveColor(index: Int): Int = SLEEVES[Math.floorMod(index, SLEEVES.size)]

    /** Kılıf yazısı: koyu ya da açık mürekkepten kontrastı yüksek olan (WCAG göreli parlaklık). */
    fun inkOn(argb: Int): Int {
        val l = luminance(argb)
        val light = (luminance(INK_LIGHT) + 0.05f) / (l + 0.05f)
        val dark = (l + 0.05f) / (luminance(INK_DARK) + 0.05f)
        return if (dark >= light) INK_DARK else INK_LIGHT
    }

    fun luminance(argb: Int): Float {
        fun ch(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * ch(argb shr 16 and 0xFF) + 0.7152f * ch(argb shr 8 and 0xFF) + 0.0722f * ch(argb and 0xFF)
    }

    fun number(index: Int): String = "No. %02d".format(Locale.ROOT, index + 1)
    fun initial(title: String): String = title.firstOrNull { it.isLetterOrDigit() }?.uppercase(Locale("tr")) ?: "•"

    /** Kılıf başlığı punto (sahne birimi): en uzun sözcük satıra sığsın, sözcük ortadan bölünmesin (en az 15). */
    fun titleSize(title: String): Float {
        val longest = title.split(' ').maxOfOrNull { it.length } ?: 0
        return if (longest == 0) TITLE_MAX else (TITLE_LINE / (0.62f * longest)).coerceIn(15f, TITLE_MAX)
    }
    private const val TITLE_MAX = 23f
    private const val TITLE_LINE = SLEEVE_W - 2 * 7f - 2 * 9f // kılıf iç genişliği

    /** Alt satır: eklentinin kısa alt yazısı (yıl/tür) varsa o, yoksa tür. */
    fun detail(subtitle: String?, type: String): String {
        val s = subtitle?.trim().orEmpty()
        return if (s.isNotEmpty() && s.length <= 24) s else type
    }
}

/** DramaVakti sitesinin eklentisi: ad (yalnız harfler, küçük) tam olarak site adı. */
internal fun isDramaVaktiProvider(name: String): Boolean =
    name.lowercase(Locale.ROOT).filter { it in 'a'..'z' } == "dramavakti"

private val MatchEasing = CubicBezierEasing(0.32f, 0.08f, 0.18f, 1f)
private val TableTop = Color(0xFF3A2E26) // koyu ceviz masa (karanlık temaya uygun; özgündeki açık #E6DED2 yerine)
private val TableNear = Color(0xFF2A211B)
private val TrayPaper = Color(0xFFF2EDE5)
private val TrayInner = Color(0xFFE3DACB)

/** Masa renkleri ve üst/alt saydam geçiş uçları. */
private class MatchboxTable(val top: Color, val near: Color, val fadeTop: Color, val fadeNear: Color)

/**
 * Koyu temalarda (Koyu, AMOLED, Okyanus, Mor gece) koyu ceviz masa aynen kalır. Açık ve Yüksek kontrast temada masa temadan
 * türer ([SliderColors.table]) ve üst/alt geçiş sabit koyu renge değil `colorScheme.background`'a doğru söner.
 */
@Composable
private fun matchboxTable(): MatchboxTable {
    val mode = LocalPlayThemeMode.current
    if (mode != ThemeMode.Light && mode != ThemeMode.HighContrast) {
        return MatchboxTable(TableTop, TableNear, TableTop.copy(alpha = 0f), TableNear.copy(alpha = 0f))
    }
    val scheme = MaterialTheme.colorScheme
    val (top, near) = SliderColors.table(scheme.surface, scheme.surfaceVariant)
    val edge = scheme.background.copy(alpha = 0f)
    return MatchboxTable(top, near, edge, edge)
}

@Composable
internal fun MatchboxCarousel(
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
    val open = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var openIndex by remember { mutableIntStateOf(savedIndex) } // çekmecesi açık kutu (konumdan bağımsız; sürüklerken atlamasın)
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val centre by remember(count) { derivedStateOf { FanGeometry.index(position.value, count) } }
    val typeMovie = stringResource(R.string.type_movie)
    val typeSeries = stringResource(R.string.type_series)
    val table = matchboxTable()

    fun go(target: Float) {
        val next = FanGeometry.index(target, count)
        savedIndex = next
        job?.cancel()
        job = scope.launch {
            if (reduceMotion) {
                position.snapTo(target); openIndex = next; open.snapTo(1f)
                return@launch
            }
            if (openIndex != next || position.value != target) {
                open.animateTo(0f, tween(MatchboxGeometry.closeMs(open.value), easing = MatchEasing))
                position.animateTo(target, tween(MatchboxGeometry.SHIFT_MS, easing = MatchEasing))
                openIndex = next
            }
            open.animateTo(1f, tween(MatchboxGeometry.openMs(open.value), easing = MatchEasing))
        }
    }

    LaunchedEffect(Unit) { go(position.value) } // açılışta ilk çekmece açılır
    // Otomatik ilerleme; dokunurken durur, bırakınca süre baştan sayılır (ekran dışında öğe atılır → durur).
    LaunchedEffect(touching, count, reduceMotion) {
        if (touching || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(MatchboxGeometry.AUTOPLAY_MS)
            go(position.value.roundToInt() + 1f)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stageWidth = maxWidth
        val unit = stageWidth / MatchboxGeometry.STAGE
        val density = LocalDensity.current
        val unitPx = with(density) { unit.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * MatchboxGeometry.SCENE_H)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                touching = event.changes.any { it.pressed }
                            }
                        }
                    }
                    .pointerInput(count, unitPx) {
                        val tracker = VelocityTracker()
                        val stepPx = unitPx * MatchboxGeometry.STEP
                        var start = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                start = position.value
                                tracker.resetTracking()
                                job?.cancel()
                                job = scope.launch {
                                    open.animateTo(0f, tween(MatchboxGeometry.closeMs(open.value), easing = MatchEasing))
                                }
                            },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                go(MatchboxGeometry.settleTarget(start, position.value, v))
                            },
                            onDragCancel = { go(MatchboxGeometry.settleTarget(start, position.value, 0f)) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { position.snapTo(position.value - dx / stepPx) }
                        }
                    }
                    .pointerInput(items, unitPx) {
                        detectTapGestures { at ->
                            val step = MatchboxGeometry.tapStep((at.x - size.width / 2f) / unitPx)
                            when {
                                step != 0 -> go(position.value.roundToInt() + step.toFloat())
                                open.value > 0.5f -> onOpen(items[centre])
                                else -> go(position.value.roundToInt().toFloat())
                            }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // Eğik masa: tüm kutular tek katmanda 30° yatırılır; masa ekrandan geniş, kenarları görünmesin.
                Box(
                    Modifier
                        .requiredWidth(stageWidth * 1.6f)
                        .height(unit * MatchboxGeometry.SCENE_H)
                        .graphicsLayer {
                            rotationX = MatchboxGeometry.TILT
                            transformOrigin = TransformOrigin(0.5f, MatchboxGeometry.PIVOT_Y)
                            cameraDistance = MatchboxGeometry.PERSPECTIVE * maxOf(size.width / 1.6f, size.height) / 72f
                        }
                        .background(
                            Brush.verticalGradient(
                                0f to table.fadeTop,
                                0.1f to table.top,
                                0.62f to table.near,
                                0.9f to table.fadeNear, // alt kenar sert kesilmesin
                            ),
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    items.forEachIndexed { index, item ->
                        Matchbox(
                            item = item,
                            index = index,
                            count = count,
                            position = position::value,
                            open = open::value,
                            openIndex = { openIndex },
                            unitPx = unitPx,
                            unit = unit,
                            detail = MatchboxGeometry.detail(
                                item.subtitle,
                                if (item.mediaType == MediaType.Tv) typeSeries else typeMovie,
                            ),
                        )
                    }
                }
            }
            // Alt gösterge: Shortizle yelpazesindeki gibi noktalar.
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { i ->
                    val w by animateDpAsState(if (i == centre) 18.dp else 6.dp, label = "matchDot")
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
 * Tek kutu: alttaki çekmece (afiş) + üstteki kılıf. Konum ve açıklık yalnız katman evresinde okunur (karede yeniden
 * oluşturma ve ayırma yok).
 */
@Composable
private fun Matchbox(
    item: CatalogItem,
    index: Int,
    count: Int,
    position: () -> Float,
    open: () -> Float,
    openIndex: () -> Int,
    unitPx: Float,
    unit: Dp,
    detail: String,
) {
    val sleeve = remember(index) { Color(MatchboxGeometry.sleeveColor(index)) }
    val ink = remember(index) { Color(MatchboxGeometry.inkOn(MatchboxGeometry.sleeveColor(index))) }
    val density = LocalDensity.current
    Box(
        Modifier
            .padding(top = unit * MatchboxGeometry.TOP)
            .size(unit * MatchboxGeometry.SLEEVE_W, unit * (MatchboxGeometry.SLEEVE_H + MatchboxGeometry.OPEN))
            .graphicsLayer {
                val q = FanGeometry.offset(index, position(), count)
                translationX = MatchboxGeometry.boxX(q) * unitPx
                alpha = MatchboxGeometry.alpha(q, count)
            },
    ) {
        // Çekmece: kâğıt kenar, iç zemin, alçak duvarlar; afiş içinde.
        Box(
            Modifier
                .padding(horizontal = unit * MatchboxGeometry.TRAY_INSET)
                .padding(top = unit * MatchboxGeometry.TRAY_INSET)
                .size(unit * (MatchboxGeometry.SLEEVE_W - 2 * MatchboxGeometry.TRAY_INSET), unit * MatchboxGeometry.TRAY_LEN)
                .graphicsLayer {
                    translationY = if (openIndex() == index) open() * MatchboxGeometry.OPEN * unitPx else 0f
                }
                .shadow(6.dp, RoundedCornerShape(unit * 3f))
                .background(TrayPaper, RoundedCornerShape(unit * 3f))
                .padding(unit * 4f)
                .background(TrayInner)
                .padding(unit * 2f),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Kılıf: başlık büyük, "No. 01", baş harf rozeti, alt satır, ince iç çerçeve.
        Box(
            Modifier
                .size(unit * MatchboxGeometry.SLEEVE_W, unit * MatchboxGeometry.SLEEVE_H)
                .shadow(10.dp, RoundedCornerShape(unit * 4f))
                .background(sleeve, RoundedCornerShape(unit * 4f))
                .padding(unit * 7f)
                .border(1.dp, ink.copy(alpha = 0.45f), RoundedCornerShape(unit * 2f))
                .padding(horizontal = unit * 9f, vertical = unit * 10f),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MatchboxGeometry.number(index),
                        color = ink.copy(alpha = 0.85f),
                        fontSize = with(density) { (unit * 11f).toSp() },
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(unit * 26f)
                            .border(1.dp, ink.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = MatchboxGeometry.initial(item.title),
                            color = ink,
                            fontSize = with(density) { (unit * 13f).toSp() },
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                val titleSize = remember(item.title) { MatchboxGeometry.titleSize(item.title) }
                Text(
                    text = item.title,
                    color = ink,
                    fontSize = with(density) { (unit * titleSize).toSp() },
                    lineHeight = with(density) { (unit * (titleSize + 2f)).toSp() },
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.4f)))
                Text(
                    text = detail,
                    color = ink.copy(alpha = 0.85f),
                    fontSize = with(density) { (unit * 10.5f).toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = unit * 5f),
                )
            }
        }
    }
}
