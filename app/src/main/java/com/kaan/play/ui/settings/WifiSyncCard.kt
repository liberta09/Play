package com.kaan.play.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.sync.SourceTransferItem
import com.kaan.play.data.sync.local.LocalSyncController
import com.kaan.play.data.sync.local.LocalSyncController.Error
import com.kaan.play.data.sync.local.LocalSyncController.Phase
import com.kaan.play.data.sync.local.LocalSyncData
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.theme.PlayTheme
import com.kaan.play.ui.tv.focusRing

/** Android 17 (API 37) yerel ağ izni. */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

/**
 * "Wi-Fi senkronizasyonu · Yerel" kartı (profil seçme ekranı ve Ayarlar'daki profil bölümü). Kapalı başlar; açılınca
 * tek kullanımlık kod üretilir ve sunucu başlar. Kart kapanınca ya da uygulama arka plana geçince sunucu durur, kod
 * geçersiz olur ([LocalSyncController]).
 */
@Composable
fun WifiSyncCard(modifier: Modifier = Modifier) {
    val controller = rememberContainer().localSync
    val state by controller.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) expanded = true
    }

    if (expanded) {
        DisposableEffect(Unit) {
            controller.open()
            onDispose { controller.close() }
        }
        // Uygulama arka plana geçince kart kapanır (sunucu durur, kod geçersiz).
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) expanded = false }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    WifiSyncCardContent(
        expanded = expanded,
        state = state,
        permissionDenied = permissionDenied,
        showDebugAddress = BuildConfig.DEBUG,
        onToggle = {
            when {
                expanded -> expanded = false
                Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK &&
                    ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED ->
                    permission.launch(LOCAL_NETWORK_PERMISSION)
                else -> expanded = true
            }
        },
        onConnect = { code, address -> controller.connect(code, address.takeIf { BuildConfig.DEBUG }) },
        onNewCode = {
            controller.close()
            controller.open()
        },
        onConfirm = controller::confirm,
        onSkip = controller::dismissPending,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WifiSyncCardContent(
    expanded: Boolean,
    state: LocalSyncController.State,
    permissionDenied: Boolean,
    showDebugAddress: Boolean,
    onToggle: () -> Unit,
    onConnect: (code: String, address: String?) -> Unit,
    onNewCode: () -> Unit,
    onConfirm: (List<SourceTransferItem>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                modifier = Modifier.fillMaxWidth().focusRing(RoundedCornerShape(12.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(PlayIcons.Server, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.wifi_sync_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(if (expanded) PlayIcons.ChevronDown else PlayIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            if (!expanded) {
                if (permissionDenied) ErrorLine(stringResource(R.string.wifi_sync_error_permission))
                return@Column
            }

            Text(
                text = stringResource(R.string.wifi_sync_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val code = state.code
            if (code != null) {
                Text(
                    text = stringResource(R.string.wifi_sync_code, code),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.wifi_sync_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.expired || state.error == Error.Locked || state.error == Error.PortBusy) {
                if (state.expired) ErrorLine(stringResource(R.string.wifi_sync_expired))
                TextButton(onClick = onNewCode) { Text(stringResource(R.string.wifi_sync_new_code), maxLines = 1) }
            }

            var peerCode by rememberSaveable { mutableStateOf("") }
            var address by rememberSaveable { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = peerCode,
                    onValueChange = { v -> peerCode = v.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.wifi_sync_peer_code), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                // "Bağlan" her zaman tek satır: düğme kendi genişliğini alır, kalan yer kod kutusunundur.
                Button(onClick = { onConnect(peerCode, address.takeIf { showDebugAddress && it.isNotBlank() }) }, enabled = !state.busy) {
                    Text(stringResource(R.string.wifi_sync_connect), maxLines = 1, softWrap = false)
                }
            }
            if (showDebugAddress) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { v -> address = v.filter { it.isDigit() || it == '.' }.take(15) },
                    label = { Text(stringResource(R.string.wifi_sync_debug_address), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (state.phase) {
                Phase.Searching, Phase.Connecting, Phase.Syncing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(
                            when (state.phase) {
                                Phase.Searching -> R.string.wifi_sync_searching
                                Phase.Connecting -> R.string.wifi_sync_connecting
                                else -> R.string.wifi_sync_syncing
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> Unit
            }
            state.result?.let { ResultLine(it) }
            if (state.installFailed > 0) {
                ErrorLine(pluralStringResource(R.plurals.wifi_sync_install_failed, state.installFailed, state.installFailed))
            }
            state.error?.let { ErrorLine(stringResource(it.messageRes())) }
            if (permissionDenied) ErrorLine(stringResource(R.string.wifi_sync_error_permission))

            if (state.pending.isNotEmpty()) {
                PendingSources(state.pending, state.installing, onConfirm, onSkip)
            }
        }
    }
}

@Composable
private fun ResultLine(counts: LocalSyncData.Counts) {
    Text(
        text = stringResource(
            R.string.wifi_sync_result,
            pluralStringResource(R.plurals.wifi_sync_profiles, counts.profiles, counts.profiles),
            pluralStringResource(R.plurals.wifi_sync_history, counts.history, counts.history),
            pluralStringResource(R.plurals.wifi_sync_addons, counts.addons, counts.addons),
            pluralStringResource(R.plurals.wifi_sync_repos, counts.repos, counts.repos),
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

private fun Error.messageRes(): Int = when (this) {
    Error.InvalidCode -> R.string.wifi_sync_error_invalid_code
    Error.NotFound -> R.string.wifi_sync_error_not_found
    Error.WrongCode -> R.string.wifi_sync_error_wrong_code
    Error.Unreachable -> R.string.wifi_sync_error_unreachable
    Error.Locked -> R.string.wifi_sync_error_locked
    Error.PortBusy -> R.string.wifi_sync_error_port
    Error.Failed -> R.string.wifi_sync_error_failed
}

/** "Şunlar eklensin mi?": adresler görünür; "Hepsini ekle" ya da tek tek seçim. Yalnızca onaylananlar eklenir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingSources(
    pending: List<SourceTransferItem>,
    installing: Boolean,
    onConfirm: (List<SourceTransferItem>) -> Unit,
    onSkip: () -> Unit,
) {
    var selected by remember(pending) { mutableStateOf(pending.map { it.sourceUrl }.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.wifi_sync_add_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        pending.forEach { item ->
            val checked = item.sourceUrl in selected
            val rowShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRing(rowShape)
                    .toggleable(
                        value = checked,
                        enabled = !installing,
                        role = Role.Checkbox,
                        onValueChange = { on -> selected = if (on) selected + item.sourceUrl else selected - item.sourceUrl },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = !installing)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (item.name.isNotBlank()) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        item.sourceUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (installing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.wifi_sync_installing), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfirm(pending) }) { Text(stringResource(R.string.wifi_sync_add_all), maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { onConfirm(pending.filter { it.sourceUrl in selected }) }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(R.string.wifi_sync_add_selected), maxLines = 1, softWrap = false)
                }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.wifi_sync_add_skip), maxLines = 1, softWrap = false) }
            }
        }
    }
}

// ---- önizlemeler: dar telefon, büyük yazı, TV ("Bağlan" tek satır kalmalı) ----

private val previewState = LocalSyncController.State(
    code = "123456",
    phase = Phase.Done,
    result = LocalSyncData.Counts(profiles = 2, history = 37, addons = 6, repos = 4),
    pending = listOf(
        SourceTransferItem("CLOUDSTREAM_REPO", "https://example.com/cs/repo.json", name = "Örnek depo"),
        SourceTransferItem("WEB_SITE", "https://site.example.org"),
    ),
)

@Composable
private fun PreviewCard() {
    PlayTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WifiSyncCardContent(
                expanded = true,
                state = previewState,
                permissionDenied = false,
                showDebugAddress = true,
                onToggle = {},
                onConnect = { _, _ -> },
                onNewCode = {},
                onConfirm = {},
                onSkip = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Dar telefon", widthDp = 320, heightDp = 900, locale = "tr")
@Composable
private fun WifiSyncCardNarrowPreview() = PreviewCard()

@Preview(name = "Büyük yazı", widthDp = 360, heightDp = 1200, fontScale = 2f, locale = "tr")
@Composable
private fun WifiSyncCardLargeFontPreview() = PreviewCard()

@Preview(name = "TV", widthDp = 960, heightDp = 540, device = "spec:width=1920px,height=1080px,dpi=320", locale = "tr")
@Composable
private fun WifiSyncCardTvPreview() = PreviewCard()

@Preview(name = "English, large font", widthDp = 320, heightDp = 1200, fontScale = 1.6f, locale = "en")
@Composable
private fun WifiSyncCardEnglishPreview() = PreviewCard()
