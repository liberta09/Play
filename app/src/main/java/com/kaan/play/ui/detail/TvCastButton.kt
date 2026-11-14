package com.kaan.play.ui.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusRing

/**
 * Detay ekranının sağ üstünde "TV'de oynat" anahtarı (yalnız Wi-Fi ile eşleşmiş cihaz varken görünür). Açıkken bu detaydan
 * başlatılan oynatma telefonda değil, eşleşmiş cihazda açılır ([com.kaan.play.data.sync.local.LocalSyncController.playOnPeer]).
 */
@Composable
fun TvCastButton(modifier: Modifier = Modifier) {
    val sync = rememberContainer().localSync
    val paired by sync.pairedDevices.collectAsStateWithLifecycle()
    val on by sync.castToTv.collectAsStateWithLifecycle()
    if (paired.isEmpty()) return
    val context = LocalContext.current
    val peer = paired.maxByOrNull { it.lastSyncAt }?.peerName.orEmpty()
    val onText = stringResource(R.string.cast_tv_on, peer)
    val offText = stringResource(R.string.cast_tv_off)
    Surface(
        onClick = {
            sync.setCastToTv(!on)
            Toast.makeText(context, if (!on) onText else offText, Toast.LENGTH_SHORT).show()
        },
        shape = CircleShape,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .size(40.dp)
            .focusRing(CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = PlayIcons.Tv,
                contentDescription = stringResource(R.string.cast_tv_label),
                tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
