package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.ui.home.MatchboxGeometry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Açık kibrit kutusu (MiniDiziİzle): 21st.dev "Matchbox Carousel" (Neebarp) örneğinin açık görünümü
 * yerel Compose ile yeniden yazıldı (kod kopyalanmadı). Kutu ölçüleri, geçiş süreleri ve eğrisi DramaVakti'nin koyu
 * sürümüyle ortaktır ([MatchboxGeometry]); farklar: temadan türeyen masa paneli ([SliderColors.table]), düz yazı, sığ
 * eğim, çekmece önü, altta sayaç + halka oklar. DramaVakti'nin koyu sürümü ayrı dosyada aynen kalır.
 */
internal object OpenMatchboxGeometry {
    const val LIP = 10f // çekmecenin kılıf altından görünen önü (sahne birimi)
    const val TILT = 12f // masaya sığ bakış (derece)
    const val SIDE = 5f // yan kutuların içe bakan yan yüzü (en çok)
    const val SCENE_H = MatchboxGeometry.TOP + MatchboxGeometry.SLEEVE_H + LIP + MatchboxGeometry.OPEN + 16f

    // Örnekteki kılıf renkleri: adaçayı, turuncu-kırmızı, kömür, somon, mavi, gri-yeşil.
    val SLEEVES = intArrayOf(
        0xFF93A68C.toInt(), 0xFFCB5A3C.toInt(), 0xFF2B2926.toInt(),
        0xFFD0705F.toInt(), 0xFF5561C1.toInt(), 0xFFA0B2AC.toInt(),
    )

    fun sleeveColor(index: Int): Int = SLEEVES[Math.floorMod(index, SLEEVES.size)]

    fun number(index: Int): String = "NO. %02d".format(Locale.ROOT, index + 1)

    /** Düz yazı başlık punto (sahne birimi): en uzun sözcük satıra sığsın (en az 15, en çok 28). */
    fun titleSize(title: String): Float {
        val longest = title.split(' ').maxOfOrNull { it.length } ?: 0
        return if (longest == 0) TITLE_MAX else (TITLE_LINE / (0.6f * longest)).coerceIn(15f, TITLE_MAX)
    }
    private const val TITLE_MAX = 28f
    private const val TITLE_LINE = MatchboxGeometry.SLEEVE_W - 2 * 7f - 2 * 9f

    /** Yan kutunun içe bakan yan yüzünün genişliği: ortada 0, bir adım ötede tam. */
    fun sideWidth(q: Float): Float = SIDE * abs(q).coerceAtMost(1f)
}

private val MatchEasing = CubicBezierEasing(0.32f, 0.08f, 0.18f, 1f)
private val TrayPaper = Color(0xFFF4F0E9)
private val TrayLip = Color(0xFFFBF9F5)
private val TrayInner = Color(0xFFE3DACB)

@Composable
internal fun OpenMatchboxCarousel(
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
    val open = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var openIndex by remember { mutableIntStateOf(savedIndex) } // çekmecesi açık kutu (sürüklerken atlamasın)
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val centre by remember(count) { derivedStateOf { SliderMath.index(position.value, count) } }
    val typeMovie = stringResource(R.string.type_movie)
    val typeSeries = stringResource(R.string.type_series)
    val (tableFar, tableNear) = SliderColors.table(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
    )

    fun go(target: Float) {
        val next = SliderMath.index(target, count)
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
    AutoAdvance(paused = touching) { go(position.value.roundToInt() + 1f) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val stageWidth = maxWidth
        val unit = stageWidth / MatchboxGeometry.STAGE
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(tableFar, tableNear)))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * OpenMatchboxGeometry.SCENE_H)
                    .clipToBounds()
                    .trackTouch { touching = it }
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
                            val step = SliderMath.tapStep((at.x - size.width / 2f) / unitPx, MatchboxGeometry.TAP_SIDE)
                            when {
                                step != 0 -> go(position.value.roundToInt() + step.toFloat())
                                open.value > 0.5f -> onOpen(items[centre])
                                else -> go(position.value.roundToInt().toFloat())
                            }
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // Kutular tek katmanda sığ eğimle masaya yatırılır (yakındaki alt kısım biraz büyür).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(unit * OpenMatchboxGeometry.SCENE_H)
                        .graphicsLayer {
                            rotationX = OpenMatchboxGeometry.TILT
                            transformOrigin = TransformOrigin(0.5f, MatchboxGeometry.PIVOT_Y)
                            cameraDistance = MatchboxGeometry.PERSPECTIVE * maxOf(size.width, size.height) / 72f
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    items.forEachIndexed { index, item ->
                        OpenMatchbox(
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
            // Alt: sayaç + içi boş halka iki ok (tema renklerinde).
            Row(
                Modifier.padding(top = 4.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SliderCounter(current = centre, count = count, modifier = Modifier.padding(end = 8.dp))
                SliderArrowButton(
                    forward = false,
                    contentDescription = stringResource(R.string.slider_previous),
                    onClick = { go(position.value.roundToInt() - 1f) },
                )
                SliderArrowButton(
                    forward = true,
                    contentDescription = stringResource(R.string.slider_next),
                    onClick = { go(position.value.roundToInt() + 1f) },
                )
            }
        }
    }
}

/** Tek kutu: alttaki çekmece (afiş + beyaz ön) + üstteki kılıf; yan kutularda içe bakan yan yüz. */
@Composable
private fun OpenMatchbox(
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
    val sleeveArgb = OpenMatchboxGeometry.sleeveColor(index)
    val sleeve = remember(index) { Color(sleeveArgb) }
    val side = remember(index) { lerp(Color(sleeveArgb), Color.Black, 0.28f) }
    val ink = remember(index) { Color(MatchboxGeometry.inkOn(sleeveArgb)) }
    val density = LocalDensity.current
    val sleeveH = unit * MatchboxGeometry.SLEEVE_H
    Box(
        Modifier
            .padding(top = unit * MatchboxGeometry.TOP)
            .size(unit * MatchboxGeometry.SLEEVE_W, unit * (MatchboxGeometry.SLEEVE_H + OpenMatchboxGeometry.LIP + MatchboxGeometry.OPEN))
            .graphicsLayer {
                val q = SliderMath.offset(index, position(), count)
                translationX = MatchboxGeometry.boxX(q) * unitPx
                alpha = MatchboxGeometry.alpha(q, count)
            }
            .drawBehind {
                // Yan yüz: sağdaki kutunun sol, soldakinin sağ kenarı görünür (kamera ortada).
                val q = SliderMath.offset(index, position(), count)
                val w = OpenMatchboxGeometry.sideWidth(q) * unitPx
                if (w > 0.5f) {
                    val x = if (q > 0) -w else size.width
                    val h = (MatchboxGeometry.SLEEVE_H + OpenMatchboxGeometry.LIP * 0.7f) * unitPx
                    drawRect(side, Offset(x, 0f), Size(w, h))
                }
            },
    ) {
        // Çekmece: kâğıt duvarlar, iç zemin, afiş; altta kılıftan taşan beyaz ön.
        Column(
            Modifier
                .padding(horizontal = unit * MatchboxGeometry.TRAY_INSET)
                .padding(top = unit * MatchboxGeometry.TRAY_INSET)
                .size(
                    unit * (MatchboxGeometry.SLEEVE_W - 2 * MatchboxGeometry.TRAY_INSET),
                    unit * (MatchboxGeometry.SLEEVE_H - MatchboxGeometry.TRAY_INSET + OpenMatchboxGeometry.LIP),
                )
                .graphicsLayer {
                    translationY = if (openIndex() == index) open() * MatchboxGeometry.OPEN * unitPx else 0f
                }
                .shadow(8.dp, RoundedCornerShape(unit * 2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrayPaper)
                    .padding(start = unit * 4f, end = unit * 4f, top = unit * 4f)
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(unit * OpenMatchboxGeometry.LIP)
                    .background(Brush.verticalGradient(listOf(TrayLip, TrayPaper))),
            )
        }
        // Kılıf: "NO. 01", baş harf halkası, büyük düz başlık, alt satır, ince iç çerçeve.
        Box(
            Modifier
                .size(unit * MatchboxGeometry.SLEEVE_W, sleeveH)
                .shadow(6.dp, RoundedCornerShape(unit * 1.5f))
                .background(sleeve, RoundedCornerShape(unit * 1.5f))
                .padding(unit * 7f)
                .border(1.dp, ink.copy(alpha = 0.35f))
                .padding(horizontal = unit * 9f, vertical = unit * 10f),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = OpenMatchboxGeometry.number(index),
                        color = ink.copy(alpha = 0.85f),
                        fontSize = with(density) { (unit * 10f).toSp() },
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.18.em,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(unit * 25f)
                            .border(1.dp, ink.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = MatchboxGeometry.initial(item.title),
                            color = ink,
                            fontSize = with(density) { (unit * 12f).toSp() },
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                }
                Spacer(Modifier.weight(0.8f))
                val titleSize = remember(item.title) { OpenMatchboxGeometry.titleSize(item.title) }
                Text(
                    text = item.title,
                    color = ink,
                    fontSize = with(density) { (unit * titleSize).toSp() },
                    lineHeight = with(density) { (unit * (titleSize * 1.08f)).toSp() },
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.01).em,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = detail,
                    color = ink.copy(alpha = 0.85f),
                    fontSize = with(density) { (unit * 10.5f).toSp() },
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.04.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
