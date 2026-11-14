package com.kaan.play.ui.home.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.ui.home.MatchboxGeometry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kitap rafı (DramaDizilerim): rafta dik duran renkli ciltler, sırtlarında dizi adı. Seçilen kitap
 * raftan yukarı çekilir, menteşesi (sırt–kapak köşesi) etrafında döner ve ön kapağında afişle rafın önüne gelir; geçişte
 * aynı yoldan rafa geri girer, sıradaki çıkar. Tüm ölçüler sahne genişliğinin kesri (0..1).
 */
internal object BookshelfGeometry {
    const val SIDE = 0.05f // rafın yan payı
    const val GAP = 0.007f // ciltler arası
    const val SPINE_MAX_W = 0.085f
    const val SPINE_H = 0.34f // en uzun cildin yüksekliği
    const val SHELF_Y = 1.0f // ciltlerin durduğu tahtanın üstü
    const val PLANK = 0.05f
    const val SCENE_H = SHELF_Y + PLANK + 0.035f
    const val COVER_W = 0.45f
    const val COVER_H = COVER_W * 1.5f
    const val COVER_TOP = 0.045f
    const val LIFT = 0.55f // çekilirken cildin kendi boyunun bu kadarı yukarı kayar
    const val PULL_END = 0.34f // 0..PULL_END raftan çekme, sonrası dönerek öne gelme
    const val TURN_START = 0.2f
    const val TILT = 28f // sürüklerken kapağın en çok dönüşü (derece)

    // Cilt bezleri: bordo, lacivert, orman yeşili, hardal, petrol, mürdüm, kömür, kiremit, zeytin, arduvaz.
    private val CLOTH = intArrayOf(
        0xFF7A2430.toInt(), 0xFF1F3A5F.toInt(), 0xFF2F5B3F.toInt(), 0xFFC08A2E.toInt(), 0xFF1D6B6E.toInt(),
        0xFF5B2C5E.toInt(), 0xFF34312E.toInt(), 0xFFA4492C.toInt(), 0xFF6B6B2F.toInt(), 0xFF4A5868.toInt(),
    )
    private val HEIGHT = floatArrayOf(1f, 0.9f, 0.96f, 0.86f, 0.98f, 0.92f, 0.88f, 1f, 0.9f, 0.95f)

    fun cloth(index: Int): Int = CLOTH[Math.floorMod(index, CLOTH.size)]

    fun spineWidth(count: Int): Float = minOf(SPINE_MAX_W, (1f - 2 * SIDE - GAP * (count - 1)) / count)

    fun spineHeight(index: Int): Float = SPINE_H * HEIGHT[Math.floorMod(index, HEIGHT.size)]

    fun spineLeft(index: Int, count: Int): Float {
        val w = spineWidth(count)
        val total = count * w + (count - 1) * GAP
        return 0.5f - total / 2f + index * (w + GAP)
    }

    /** Raftaki [x] (kesir) hangi cildin üstünde; boşluksa en yakını, rafın dışındaysa null. */
    fun slotAt(x: Float, count: Int): Int? {
        val w = spineWidth(count)
        val left = spineLeft(0, count)
        val right = spineLeft(count - 1, count) + w
        if (x < left - GAP || x > right + GAP) return null
        return ((x - left) / (w + GAP)).toInt().coerceIn(0, count - 1)
    }

    /** Tam boy kitabın kalınlığı: raftayken sırtı tam [spineWidth] görünsün diye cilde göre. */
    fun thickness(index: Int, count: Int): Float = spineWidth(count) * COVER_H / spineHeight(index)

    /** Çekilen kitabın duruşu: menteşe x, üst y, ölçek ve kapağın dönüşü (−90 sırt bize dönük, 0 kapak bize dönük). */
    data class Pull(val hingeX: Float, val top: Float, val scale: Float, val turn: Float)

    fun pull(p: Float, index: Int, count: Int): Pull {
        val sh = spineHeight(index)
        val s0 = sh / COVER_H
        val lift = smooth(p / PULL_END)
        val fly = smooth((p - TURN_START) / (1f - TURN_START))
        val slotHinge = spineLeft(index, count) + spineWidth(count)
        val slotTop = SHELF_Y - sh - LIFT * sh * lift
        return Pull(
            hingeX = mix(slotHinge, 0.5f - COVER_W / 2f, fly),
            top = mix(slotTop, COVER_TOP, fly),
            scale = mix(s0 * (1f + 0.06f * lift), 1f, fly),
            turn = -90f * (1f - fly),
        )
    }

    /** Yüz kenarı bize dönmüşse (≈90°) hiç çizilmez: çizgi kadar ince yüz ve bozuk izdüşüm olmaz. */
    fun faceAlpha(rotationY: Float): Float = if (abs(rotationY) >= EDGE_ON) 0f else 1f
    const val EDGE_ON = 88f

    /** Kapatma/açma süresi (ms): kalan yol kadar. */
    fun closeMs(p: Float): Int = (520 * p).roundToInt()
    fun openMs(p: Float): Int = (780 * (1f - p)).roundToInt()

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
}

private val ShelfEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
private val CoverShape = RoundedCornerShape(4.dp)
private val Gilt = Color(0xFFE2C26E)

@Composable
internal fun BookshelfCarousel(
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    if (count < 3) return
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var openIndex by remember { mutableIntStateOf(savedIndex.coerceIn(0, count - 1)) }
    val pull = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val tilt = remember { Animatable(0f) } // sürükleme: −1..1 adım
    var touching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scheme = MaterialTheme.colorScheme
    val (wallFar, wallNear) = SliderColors.table(scheme.surface, scheme.surfaceVariant)
    val lightTheme = scheme.surface.luminance() > 0.5f
    // Raf ahşabı: açık temada meşe, koyu temalarda ceviz.
    val woodLight = if (lightTheme) Color(0xFFD9B488) else Color(0xFF6E4C34)
    val woodDark = if (lightTheme) Color(0xFFA97C51) else Color(0xFF3B281B)
    val measurer = rememberTextMeasurer()

    fun go(next: Int) {
        val target = Math.floorMod(next, count)
        savedIndex = target
        job?.cancel()
        job = scope.launch {
            launch { tilt.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
            if (reduceMotion) {
                openIndex = target; pull.snapTo(1f)
                return@launch
            }
            if (openIndex != target) {
                pull.animateTo(0f, tween(BookshelfGeometry.closeMs(pull.value), easing = ShelfEasing))
                openIndex = target
            }
            pull.animateTo(1f, tween(BookshelfGeometry.openMs(pull.value), easing = ShelfEasing))
        }
    }

    LaunchedEffect(Unit) { go(savedIndex) } // açılışta ilk kitap raftan çıkar
    AutoAdvance(paused = touching) { go(savedIndex + 1) }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val w = maxWidth
        val wPx = with(LocalDensity.current) { w.toPx() }
        val spineFontPx = (wPx * BookshelfGeometry.spineWidth(count) * 0.36f).coerceAtMost(wPx * 0.03f)
        val spineFont = with(LocalDensity.current) { spineFontPx.toSp() }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(wallFar, wallNear)))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(w * BookshelfGeometry.SCENE_H)
                    .trackTouch { touching = it }
                    .pointerInput(count, wPx) {
                        val tracker = VelocityTracker()
                        val stepPx = wPx * 0.5f
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = {
                                val v = -tracker.calculateVelocity().x / stepPx
                                val step = SliderMath.settleTarget(0f, -tilt.value, v).roundToInt().coerceIn(-1, 1)
                                if (step == 0) {
                                    scope.launch { tilt.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
                                } else {
                                    go(savedIndex + step)
                                }
                            },
                            onDragCancel = { scope.launch { tilt.animateTo(0f) } },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { tilt.snapTo((tilt.value + dx / stepPx).coerceIn(-1f, 1f)) }
                        }
                    }
                    .pointerInput(items, wPx) {
                        detectTapGestures { at ->
                            val x = at.x / wPx
                            val y = at.y / wPx
                            val onCover = abs(x - 0.5f) <= BookshelfGeometry.COVER_W / 2f &&
                                y in BookshelfGeometry.COVER_TOP..(BookshelfGeometry.COVER_TOP + BookshelfGeometry.COVER_H)
                            val slot = if (y >= BookshelfGeometry.SHELF_Y - BookshelfGeometry.SPINE_H) {
                                BookshelfGeometry.slotAt(x, count)
                            } else {
                                null
                            }
                            when {
                                onCover && pull.value > 0.6f -> onOpen(items[Math.floorMod(openIndex, count)])
                                slot != null && slot != savedIndex -> go(slot)
                                slot != null && pull.value > 0.6f -> onOpen(items[Math.floorMod(openIndex, count)])
                                else -> {
                                    val step = SliderMath.tapStep(x - 0.5f, BookshelfGeometry.COVER_W / 2f)
                                    if (step != 0) go(savedIndex + step)
                                }
                            }
                        }
                    },
            ) {
                // Raf: duvar önünde bölme, ciltler (çekilen hariç), tahta. Tek tuvalde; çizim yalnız openIndex'i okur.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val spines = items.mapIndexed { i, item ->
                                spineText(measurer, item.title, i, spineFont, size.width * BookshelfGeometry.spineHeight(i))
                            }
                            onDrawBehind {
                                drawShelf(count, woodLight, woodDark, spines) { openIndex }
                            }
                        },
                )
                // Çekilen kitap: sırt + kapak, menteşe ortak (sırtın sağ kenarı = kapağın sol kenarı).
                val index = Math.floorMod(openIndex, count)
                val item = items[index]
                val thickness = w * BookshelfGeometry.thickness(index, count)
                val coverW = w * BookshelfGeometry.COVER_W
                val coverH = w * BookshelfGeometry.COVER_H
                val bigFont = spineFont / (BookshelfGeometry.spineHeight(index) / BookshelfGeometry.COVER_H)
                Row(
                    Modifier
                        .size(thickness + coverW, coverH)
                        .graphicsLayer {
                            val p = BookshelfGeometry.pull(pull.value, index, count)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = p.scale
                            scaleY = p.scale
                            translationX = (p.hingeX * wPx) - thickness.toPx() * p.scale + tilt.value * wPx * 0.06f
                            translationY = p.top * wPx
                        },
                ) {
                    Box(
                        Modifier
                            .size(thickness, coverH)
                            .graphicsLayer {
                                val p = BookshelfGeometry.pull(pull.value, index, count)
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                rotationY = p.turn + 90f - tilt.value * BookshelfGeometry.TILT
                                alpha = BookshelfGeometry.faceAlpha(rotationY)
                                cameraDistance = 3.2f * coverW.toPx() / 72f
                            }
                            .drawWithCache {
                                val text = spineText(measurer, item.title, index, bigFont, size.height)
                                onDrawBehind { drawSpine(Offset.Zero, size, index, text) }
                            },
                    )
                    Box(
                        Modifier
                            .size(coverW, coverH)
                            .graphicsLayer {
                                val p = BookshelfGeometry.pull(pull.value, index, count)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = p.turn - tilt.value * BookshelfGeometry.TILT
                                alpha = BookshelfGeometry.faceAlpha(rotationY)
                                cameraDistance = 3.2f * coverW.toPx() / 72f
                                // Gölge yalnız kapak bize dönmüşken. Köşeler EŞİT olmalı: eşit olmayan köşeli (yol) gölge 3B dönen
                                // katmanda emülatörün çizim iş parçacığını çökertti (Android 16 tablet emülatörü).
                                shadowElevation = 14.dp.toPx() * ((p.turn + 60f) / 60f).coerceIn(0f, 1f)
                                shape = CoverShape
                                clip = true
                            }
                            .background(Color(BookshelfGeometry.cloth(index))),
                    ) {
                        Text(
                            text = item.title,
                            color = Color(MatchboxGeometry.inkOn(BookshelfGeometry.cloth(index))),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Menteşe kıvrımı: sol kenarda ince gölge + ışık.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val crease = size.width * 0.07f
                                    val brush = Brush.horizontalGradient(
                                        0f to Color.Black.copy(alpha = 0.45f),
                                        0.35f to Color.White.copy(alpha = 0.18f),
                                        0.6f to Color.Black.copy(alpha = 0.12f),
                                        1f to Color.Transparent,
                                        endX = crease,
                                    )
                                    onDrawBehind { drawRect(brush, size = Size(crease, size.height)) }
                                },
                        )
                    }
                }
            }
            Text(
                text = items[savedIndex.coerceIn(0, count - 1)].title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SliderDots(count = count, current = savedIndex.coerceIn(0, count - 1), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        }
    }
}

/** Sırt yazısı: cilt boyuna sığan tek satır (sonra −90° döndürülür, aşağıdan yukarı okunur). */
private fun spineText(measurer: TextMeasurer, title: String, index: Int, font: TextUnit, lengthPx: Float): TextLayoutResult {
    val ink = Color(MatchboxGeometry.inkOn(BookshelfGeometry.cloth(index)))
    return measurer.measure(
        text = title.uppercase(java.util.Locale("tr")),
        style = TextStyle(
            color = ink,
            fontSize = font,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.em,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = (lengthPx * 0.76f).roundToInt().coerceAtLeast(1)),
    )
}

/** Tek cilt sırtı: bez (yuvarlak sırt gölgesi), yaldız bantlar, dik başlık. */
private fun DrawScope.drawSpine(topLeft: Offset, size: Size, index: Int, text: TextLayoutResult) {
    val cloth = Color(BookshelfGeometry.cloth(index))
    val shade = lerp(cloth, Color.Black, 0.35f)
    val shine = lerp(cloth, Color.White, 0.16f)
    drawRoundRect(
        Brush.horizontalGradient(
            0f to shade, 0.22f to cloth, 0.45f to shine, 0.7f to cloth, 1f to shade,
            startX = topLeft.x, endX = topLeft.x + size.width,
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(size.width * 0.12f),
    )
    val band = size.height * 0.012f
    for (f in floatArrayOf(0.07f, 0.1f, 0.9f, 0.93f)) {
        drawRect(Gilt.copy(alpha = 0.85f), Offset(topLeft.x + size.width * 0.12f, topLeft.y + size.height * f), Size(size.width * 0.76f, band))
    }
    val cx = topLeft.x + size.width / 2f
    val cy = topLeft.y + size.height / 2f
    rotate(-90f, Offset(cx, cy)) {
        drawText(text, topLeft = Offset(cx - text.size.width / 2f, cy - text.size.height / 2f))
    }
}

/** Raf bölmesi + ciltler + tahta. [hidden]: o an raftan çekilmiş cilt (yeri boş). */
private fun DrawScope.drawShelf(
    count: Int,
    woodLight: Color,
    woodDark: Color,
    spines: List<TextLayoutResult>,
    hidden: () -> Int,
) {
    val w = size.width
    val shelfY = BookshelfGeometry.SHELF_Y * w
    val backTop = shelfY - (BookshelfGeometry.SPINE_H + 0.06f) * w
    // Bölmenin arka yüzü: ahşabın koyusu, üstten gölge.
    drawRect(
        Brush.verticalGradient(
            listOf(woodDark.copy(alpha = 0.55f), woodDark.copy(alpha = 0.32f)),
            startY = backTop,
            endY = shelfY,
        ),
        topLeft = Offset(w * 0.02f, backTop),
        size = Size(w * 0.96f, shelfY - backTop),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), startY = backTop, endY = backTop + w * 0.05f),
        topLeft = Offset(w * 0.02f, backTop),
        size = Size(w * 0.96f, w * 0.05f),
    )
    val sw = BookshelfGeometry.spineWidth(count) * w
    val skip = hidden()
    for (i in 0 until count) {
        if (i == skip) continue
        val sh = BookshelfGeometry.spineHeight(i) * w
        val left = BookshelfGeometry.spineLeft(i, count) * w
        drawSpine(Offset(left, shelfY - sh), Size(sw, sh), i, spines[i])
    }
    // Tahta: üst yüz ışıklı, ön yüz degrade, altında gölge.
    val plank = BookshelfGeometry.PLANK * w
    drawRect(woodLight, Offset(0f, shelfY - w * 0.004f), Size(w, w * 0.012f))
    drawRect(
        Brush.verticalGradient(listOf(woodLight, woodDark), startY = shelfY, endY = shelfY + plank),
        topLeft = Offset(0f, shelfY + w * 0.008f),
        size = Size(w, plank - w * 0.008f),
    )
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
            startY = shelfY + plank,
            endY = shelfY + plank + w * 0.03f,
        ),
        topLeft = Offset(0f, shelfY + plank),
        size = Size(w, w * 0.03f),
    )
}
