package com.kaan.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.toBitmap
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.data.torrent.TorrentLoadProgressRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val BackgroundColor = Color(0xFF07111A)
private val AccentCyan = Color(0xFF00E5FF)

/**
 * Yalnızca torrent kaynağı açılırken ilk video karesine kadar gösterilen sade yükleme ekranı.
 *
 * Kurallar (bağlayıcı):
 * 1. Görseller (backdrop ve logo) YALNIZCA Coil BELLEK önbelleğinden (MemoryCache) gelir.
 *    Bellekte yoksa: görsel yok sayılır, düz #07111A zemin ve metin başlık (#00E5FF) gösterilir.
 * 2. Açılış anında hiçbir disk okuma, internet isteği ya da görsel çözme yapılmaz.
 * 3. Tasarım: Logo iki katman (alt katman nefes alan/0.28, üst katman soldan sağa kırpılmış).
 *    Logo yoksa film adı büyük kalın yazı, aynı iki katman ama #00E5FF camgöbeği renginde.
 * 4. Durum yazısı, hız, eş, bayt, buton vb. başka HİÇBİR ŞEY yer almaz.
 * 5. İlk kare gelince dolum %100'e tamamlanır ve ekran ~300 ms içinde şeffaflaşarak kaybolur (fade-out).
 */
@Composable
fun TorrentLoadingOverlay(
    target: PlayTarget,
    firstFrameRendered: Boolean,
    getSnapshot: () -> TorrentLoadProgressRules.TorrentLoadSnapshot,
    onFallback: () -> Unit,
    onDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var rawProgress by remember { mutableFloatStateOf(0f) }
    var hasMetadataArrived by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }

    // Arka plan yoklama döngüsü (Dispatchers.IO üzerinde, saniyede en çok 2 kez)
    LaunchedEffect(firstFrameRendered) {
        if (firstFrameRendered) {
            rawProgress = 1.0f
            delay(120L)
            overlayVisible = false
            delay(300L)
            onDismissed()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                while (isActive && !firstFrameRendered) {
                    val snapshot = getSnapshot()
                    val state = TorrentLoadProgressRules.safeCalculateProgress(
                        snapshot = snapshot,
                        firstFrameRendered = false,
                        previousProgress = rawProgress * 100f,
                    )
                    if (state.fallbackToStandard) {
                        withContext(Dispatchers.Main) {
                            onFallback()
                        }
                        break
                    }
                    rawProgress = (state.progress / 100f).coerceIn(0f, 1f)
                    if (snapshot.hasMetadata || state.phase == TorrentLoadProgressRules.Phase.Buffering) {
                        hasMetadataArrived = true
                    }
                    delay(500L)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    onFallback()
                }
            }
        }
    }

    // Yumuşak dolum animasyonu (tween 400ms)
    val animatedProgress by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1.0f else rawProgress,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "fillProgress",
    )

    // Bağlanma aşamasında (metadata yok / ilerleme 0) hafif nefes alma animasyonu (1.6 sn döngü: 800ms ileri, 800ms geri)
    val infiniteTransition = rememberInfiniteTransition(label = "breathingTransition")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingAlpha",
    )

    // İlerleme başladığında nefes alma durur, sabit 0.28f soluk katmana geçer
    val bottomLayerAlpha = if (hasMetadataArrived || animatedProgress > 0.01f) 0.28f else breathingAlpha

    val backdropUrl = remember(target.backdropPath) { TmdbImages.url(target.backdropPath, TmdbImages.Size.BackdropLarge) }
    val logoUrl = remember(target.logoPath) { TmdbImages.url(target.logoPath, TmdbImages.Size.Logo) }

    // SADECE BELLEK ÖNBELLEĞİ: MemoryCache doğrudan sorgulanır
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val cachedBackdropBitmap = remember(backdropUrl) {
        backdropUrl?.let { url ->
            val cache = imageLoader.memoryCache
            val value = cache?.get(MemoryCache.Key(url))
                ?: cache?.keys?.firstOrNull { it.key == url }?.let { cache.get(it) }
            runCatching { value?.image?.toBitmap()?.asImageBitmap() }.getOrNull()
        }
    }

    val cachedLogoBitmap = remember(logoUrl) {
        logoUrl?.let { url ->
            val cache = imageLoader.memoryCache
            val value = cache?.get(MemoryCache.Key(url))
                ?: cache?.keys?.firstOrNull { it.key == url }?.let { cache.get(it) }
            runCatching { value?.image?.toBitmap()?.asImageBitmap() }.getOrNull()
        }
    }

    AnimatedVisibility(
        visible = overlayVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(durationMillis = 300)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            // 1. En altta filmin arka plan görseli (YALNIZCA BELLEKTE VARSA) + %50 opak perde
            if (cachedBackdropBitmap != null) {
                Image(
                    bitmap = cachedBackdropBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundColor.copy(alpha = 0.5f)),
                )
            }

            // 2. Ortada logo (YALNIZCA BELLEKTE VARSA) veya yoksa/bellekte değilse camgöbeği metin başlık
            if (cachedLogoBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .fillMaxHeight(0.25f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Alt katman: kendi renkleriyle soluk (0.28f veya nefes alan)
                    Image(
                        bitmap = cachedLogoBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = bottomLayerAlpha },
                    )

                    // Üst katman: kendi renkleriyle tam opak, soldan sağa ilerleme yüzdesi kadar kırpılmış (clip)
                    Image(
                        bitmap = cachedLogoBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val fill = animatedProgress
                                clipRect(left = 0f, top = 0f, right = size.width * fill, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    )
                }
            } else {
                // Logo yoksa ya da bellekte değilse: film adı büyük ve kalın yazı, aynı iki katman ama #00E5FF camgöbeği renginde
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Alt katman: soluk / nefes alan camgöbeği başlık
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = bottomLayerAlpha },
                    )

                    // Üst katman: tam opak camgöbeği başlık, soldan sağa kırpılmış
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.drawWithContent {
                            val fill = animatedProgress
                            clipRect(left = 0f, top = 0f, right = size.width * fill, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    )
                }
            }
        }
    }
}
