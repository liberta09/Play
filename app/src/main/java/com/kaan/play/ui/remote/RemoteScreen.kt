package com.kaan.play.ui.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.sync.local.RemoteInput
import com.kaan.play.data.sync.local.RemoteKey
import com.kaan.play.data.sync.local.RemoteSendResult
import com.kaan.play.data.sync.local.RemoteState
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * Telefonu eşleşmiş cihazın (TV) kumandası ve klavyesi olarak kullanma. Tuşlar sırayla gider (biri bitmeden sonraki
 * gönderilmez); yazı, yazmaya ara verildiğinde alanın tamamı olarak gider, klavyenin "Ara" tuşu TV'de aramayı da başlatır.
 *
 * Düzen: üstte TV'de oynayan yapım kartı (ekran açıkken saniyede bir sorulur), ortada yön halkası,
 * solunda Liste ▲/▼, sağında Ses +/−; altında Geri / Ana ekran / Sessiz / Menü ve 10 sn geri · Oynat/Duraklat · 10 sn ileri.
 * Renkler uygulama temasından.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun RemoteScreen(onBack: () -> Unit) {
    val sync = rememberContainer().localSync
    val paired by sync.pairedDevices.collectAsStateWithLifecycle()
    val peer = paired.maxByOrNull { it.lastSyncAt }?.peerName.orEmpty()
    val haptic = LocalHapticFeedback.current
    var status by remember { mutableStateOf<RemoteSendResult?>(null) }
    var tv by remember { mutableStateOf<RemoteState?>(null) }
    val queue = remember { Channel<RemoteInput>(Channel.UNLIMITED) }
    LaunchedEffect(Unit) { for (input in queue) status = sync.remoteToPeer(input) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                tv = sync.remoteStateFromPeer()
                delay(if (tv == null) STATE_RETRY_MS else STATE_POLL_MS)
            }
        }
    }
    val press: (RemoteKey) -> Unit = { key ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        queue.trySend(RemoteInput(key = key))
    }
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { text }.drop(1).debounce(TYPE_PAUSE_MS).collect { queue.trySend(RemoteInput(text = it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Açılışta odak geri tuşuna çekilmez: ekran yeniden kurulunca (ör. klavye değişimi) yazı alanındaki odak kaçıyordu.
        ScreenHeader(title = stringResource(R.string.remote_title), onBack = onBack, subtitle = peer.ifEmpty { null }, initialFocus = false)
        when (status) {
            RemoteSendResult.Unreachable -> stringResource(R.string.remote_unreachable, peer)
            RemoteSendResult.Unsupported -> stringResource(R.string.remote_unsupported, peer)
            else -> null
        }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        tv?.takeIf { !it.title.isNullOrBlank() }?.let { NowPlayingCard(it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidePill(
                top = { PillArrow(up = true, stringResource(R.string.remote_list_up)) { press(RemoteKey.ListUp) } },
                label = stringResource(R.string.remote_list),
                bottom = { PillArrow(up = false, stringResource(R.string.remote_list_down)) { press(RemoteKey.ListDown) } },
            )
            DirectionPad(press)
            val volume = tv?.takeIf { it.volume >= 0 }
            SidePill(
                top = { PillSign("+", stringResource(R.string.remote_volume_up)) { press(RemoteKey.VolumeUp) } },
                label = when {
                    volume?.muted == true -> stringResource(R.string.remote_mute)
                    volume != null -> stringResource(R.string.remote_volume_level, volume.volume)
                    else -> stringResource(R.string.remote_volume)
                },
                bottom = { PillSign("−", stringResource(R.string.remote_volume_down)) { press(RemoteKey.VolumeDown) } },
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileKey(PlayIcons.Back, stringResource(R.string.remote_back), Modifier.weight(1f)) { press(RemoteKey.Back) }
            TileKey(PlayIcons.Home, stringResource(R.string.remote_home), Modifier.weight(1f)) { press(RemoteKey.Home) }
            TileKey(PlayIcons.Volume, stringResource(R.string.remote_mute), Modifier.weight(1f)) { press(RemoteKey.Mute) }
            TileKey(PlayIcons.More, stringResource(R.string.remote_menu), Modifier.weight(1f)) { press(RemoteKey.Menu) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundKey(PlayIcons.SeekBack, stringResource(R.string.remote_rewind), size = 72.dp) { press(RemoteKey.Rewind) }
            // Oynat/Duraklat: tema zıt rengiyle büyük düğme (koyu temada açık, açık temada koyu).
            Surface(
                onClick = { press(RemoteKey.PlayPause) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (tv?.playing == true) PlayIcons.Pause else PlayIcons.PlayTriangle,
                        contentDescription = stringResource(R.string.remote_play_pause),
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            RoundKey(PlayIcons.SeekForward, stringResource(R.string.remote_forward), size = 72.dp) { press(RemoteKey.FastForward) }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(RemoteInput.MAX_TEXT) },
            label = { Text(stringResource(R.string.remote_type_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { queue.trySend(RemoteInput(text = text, submit = true)) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** TV'de oynayan yapım: afiş, ad, eklenti, durum ve ilerleme (yalnız gösterim; sarma kumandanın 10 sn tuşlarıyla). */
@Composable
private fun NowPlayingCard(state: RemoteState) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AsyncImage(
                    model = state.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 52.dp, height = 74.dp).clip(RoundedCornerShape(8.dp)).background(scheme.surface),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = if (state.playing) scheme.primary else scheme.tertiary
                    Icon(if (state.playing) PlayIcons.PlayTriangle else PlayIcons.Pause, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (state.playing) R.string.remote_state_playing else R.string.remote_state_paused),
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (state.durationMs > 0) {
                val fraction = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                Progress(fraction)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(state.positionMs), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Text(clock(state.durationMs), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** İnce ilerleme çizgisi ve yuvarlak tutamak (dokunulmaz). */
@Composable
private fun Progress(fraction: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.15f)))
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(scheme.primary))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset(x = (maxWidth - 18.dp) * fraction)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface),
            )
        }
    }
}

/** Yön halkası: dört yön ve ortada Tamam. */
@Composable
private fun DirectionPad(press: (RemoteKey) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.size(212.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            val arrow = PlayIcons.ChevronDown // aşağı bakar; diğer yönler döndürülür
            PadKey(arrow, 180f, stringResource(R.string.remote_up), Modifier.align(Alignment.TopCenter)) { press(RemoteKey.Up) }
            PadKey(arrow, 0f, stringResource(R.string.remote_down), Modifier.align(Alignment.BottomCenter)) { press(RemoteKey.Down) }
            PadKey(arrow, 90f, stringResource(R.string.remote_left), Modifier.align(Alignment.CenterStart)) { press(RemoteKey.Left) }
            PadKey(arrow, -90f, stringResource(R.string.remote_right), Modifier.align(Alignment.CenterEnd)) { press(RemoteKey.Right) }
            Surface(
                onClick = { press(RemoteKey.Ok) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.remote_ok),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PadKey(icon: ImageVector, rotation: Float, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f), modifier = modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp).rotate(rotation))
        }
    }
}

/** Yan sütun (Liste / Ses): üstte ve altta tuş, ortada ad. */
@Composable
private fun SidePill(top: @Composable () -> Unit, label: String, bottom: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(68.dp).height(176.dp),
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            top()
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            bottom()
        }
    }
}

@Composable
private fun PillArrow(up: Boolean, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(PlayIcons.ChevronDown, contentDescription = label, modifier = Modifier.size(30.dp).rotate(if (up) 180f else 0f))
        }
    }
}

@Composable
private fun PillSign(sign: String, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(sign, fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { contentDescription = label })
        }
    }
}

/** Alt sıradaki karo tuş: simge + ad. */
@Composable
private fun TileKey(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.height(76.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, size: Dp = 64.dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp)) }
    }
}

/** 75_000 → "1:15", 4_073_000 → "1:07:53". */
internal fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val TYPE_PAUSE_MS = 400L
private const val STATE_POLL_MS = 1_000L
private const val STATE_RETRY_MS = 3_000L
