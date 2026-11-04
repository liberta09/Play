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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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
 * 1. Arka plan görseli Coil ile normal AsyncImage olarak yüklenir (crossfade ile yumuşak belirir).
 *    Resim hazır değilken düz #07111A zemin görünür, resim gelince %50 perde ile belirir.
 * 2. Görsel yüklemesi video hazırlığını, ExoPlayer'ı ve torrent açılışını ASLA bekletmez veya bloklamaz.
 * 3. Logo AsyncImage ile kendi renkleriyle iki katmanlı dolar (alt katman nefes alan/0.28, üst katman soldan sağa kırpılmış).
 *    logoPath yoksa veya yükleme hata verirse camgöbeği #00E5FF film adı yedeği gösterilir.
 *    Logo yüklenirken yazı yedeği gösterilmez (yalnızca zemin görünür).
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

    var isBackdropLoaded by remember(backdropUrl) { mutableStateOf(false) }
    var isLogoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var isLogoFailed by remember(logoUrl) { mutableStateOf(false) }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isBackdropLoaded) 0.5f else 0.0f,
        animationSpec = tween(300),
        label = "scrimAlpha",
    )

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
            // 1. En altta filmin yatay arka plan görseli (AsyncImage ile crossfade) + %50 opak perde
            if (backdropUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { isBackdropLoaded = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (scrimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundColor.copy(alpha = scrimAlpha)),
                    )
                }
            }

            // 2. Ortada logo (AsyncImage ile iki katmanlı dolum) veya yoksa/hata verirse camgöbeği metin başlık
            if (logoUrl != null && !isLogoFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .fillMaxHeight(0.25f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Alt katman: kendi renkleriyle soluk (0.28f veya nefes alan)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onSuccess = { isLogoLoaded = true },
                        onError = { isLogoFailed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = bottomLayerAlpha },
                    )

                    // Üst katman: kendi renkleriyle tam opak, soldan sağa ilerleme yüzdesi kadar kırpılmış (clip)
                    if (isLogoLoaded) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(logoUrl)
                                .crossfade(false)
                                .build(),
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
                }
            } else if (logoUrl == null || isLogoFailed) {
                // Logo yoksa ya da yükleme hata verdiyse: film adı büyük ve kalın yazı, aynı iki katman ama #00E5FF camgöbeği renginde
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
