package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.data.torrent.TorrentLoadProgressRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val LoadingBackgroundColor = Color(0xFF07111A)
private val AccentCyan = Color(0xFF00E5FF)
private val DimCyan = AccentCyan.copy(alpha = 0.25f)
private val StatusTextColor = AccentCyan.copy(alpha = 0.85f)

/**
 * Torrent kaynağı açılırken ilk kareye kadar gösterilen özel yükleme ekranı.
 *
 * Kurallar (bağlayıcı):
 * 1. Zemin #07111A, tek vurgu #00E5FF.
 * 2. Ortada içeriğin TMDB başlık logosu (yoksa metin başlık).
 * 3. Logo soluk başlar, ilerledikçe soldan sağa #00E5FF ile dolar (çubuk değil, logonun içi).
 * 4. Altında tek satır durum metni (teknik veri, eş, hız, magnet yok).
 * 5. En çok iki yazı boyutu.
 * 6. Motor durumu saniyede en çok 2 kez okunur, Dispatchers.IO üzerinde çalışır.
 * 7. Herhangi bir hatada sessizce standart görünüme düşülür (onFallback).
 */
@Composable
fun TorrentLoadingOverlay(
    target: PlayTarget,
    firstFrameRendered: Boolean,
    getSnapshot: () -> TorrentLoadProgressRules.TorrentLoadSnapshot,
    onFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progressState by remember { mutableStateOf(TorrentLoadProgressRules.initialState()) }
    var logoFailed by remember { mutableStateOf(false) }

    LaunchedEffect(firstFrameRendered) {
        if (firstFrameRendered) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var lastProgress = 0f
            while (isActive && !firstFrameRendered) {
                try {
                    val snapshot = getSnapshot()
                    val nextState = TorrentLoadProgressRules.safeCalculateProgress(
                        snapshot = snapshot,
                        firstFrameRendered = firstFrameRendered,
                        previousProgress = lastProgress,
                    )
                    if (nextState.fallbackToStandard) {
                        withContext(Dispatchers.Main) { onFallback() }
                        break
                    }
                    lastProgress = nextState.progress
                    withContext(Dispatchers.Main) {
                        progressState = nextState
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) { onFallback() }
                    break
                }
                delay(500L) // Saniyede en çok 2 kez
            }
        }
    }

    val logoUrl = remember(target.logoPath) {
        target.logoPath?.let { TmdbImages.url(it, TmdbImages.Size.Logo) }
    }

    val statusText = when (progressState.phase) {
        TorrentLoadProgressRules.Phase.Metadata -> stringResource(R.string.torrent_loading_connecting)
        TorrentLoadProgressRules.Phase.Buffering -> stringResource(R.string.torrent_loading_preparing)
    }

    val progressFraction = (progressState.progress / 100f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoadingBackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // Başlık logosu veya metin başlık (soldan sağa dolan)
            Box(contentAlignment = Alignment.Center) {
                if (logoUrl != null && !logoFailed) {
                    // Soluk alt katman
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = target.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(DimCyan, BlendMode.SrcIn),
                        onError = { logoFailed = true },
                        modifier = Modifier
                            .heightIn(max = 80.dp)
                            .widthIn(max = 280.dp),
                    )
                    // Soldan sağa dolan camgöbeği üst katman
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(AccentCyan, BlendMode.SrcIn),
                        modifier = Modifier
                            .heightIn(max = 80.dp)
                            .widthIn(max = 280.dp)
                            .drawWithContent {
                                clipRect(left = 0f, top = 0f, right = size.width * progressFraction, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    )
                } else {
                    // Metin başlık alternatifi (soluk zemin + soldan sağa dolan camgöbeği)
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = DimCyan,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = AccentCyan,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.drawWithContent {
                            clipRect(left = 0f, top = 0f, right = size.width * progressFraction, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    )
                }
            }

            // Tek satır durum metni
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = StatusTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
