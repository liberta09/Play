package com.kaan.play.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.data.tmdb.TmdbImages

/**
 * Başlık logosu (TMDB); yoksa, yüklenemezse ya da [logoVisible] false ise metin başlık. TMDB logoları çoğunlukla
 * beyazdır; aydınlık zeminde görünmez olacağı için oradaki çağıran [logoVisible] = false verir.
 */
@Composable
fun TitleOrLogo(title: String, logoPath: String?, modifier: Modifier = Modifier, logoHeight: Dp = 64.dp, logoVisible: Boolean = true) {
    val url = TmdbImages.url(logoPath, TmdbImages.Size.Logo).takeIf { logoVisible }
    var failed by remember(url) { mutableStateOf(false) }
    if (url != null && !failed) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = modifier.heightIn(max = logoHeight).widthIn(max = 260.dp),
        )
    } else {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}
