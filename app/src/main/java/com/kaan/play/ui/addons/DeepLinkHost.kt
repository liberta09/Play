package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.PlayApp
import com.kaan.play.R
import com.kaan.play.data.cloudstream.PluginRepoEvent
import com.kaan.play.data.cloudstream.PluginRepoRequestHandler
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.DeepLinkEvent
import com.kaan.play.data.plugin.DeepLinkHandler
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.flow.Flow

/**
 * `play://add` bağlantısı: önce onay penceresi (adres ve tür görünür, "Ekle" / "Vazgeç"), sonra sonucu
 * (eklendi / hata / geçersiz) alttan kısa bir Snackbar ile gösterir.
 *
 * Eklentilerden gelen toplu depo ekleme istekleri de ([PluginRepoRequestHandler]) burada onay penceresiyle
 * karşılanır.
 */
@Composable
fun DeepLinkHost(
    handler: DeepLinkHandler,
    repoRequests: PluginRepoRequestHandler? = null,
) {
    val context = LocalContext.current
    val actualRepoRequests = repoRequests ?: (context.applicationContext as? PlayApp)?.container?.pluginRepoRequests

    val pending by handler.pending.collectAsStateWithLifecycle()
    val busy by handler.busy.collectAsStateWithLifecycle()
    pending?.let { payload ->
        val focus = rememberInitialFocus()
        val typeName = when (payload.expectedType) {
            SourceType.CLOUDSTREAM -> stringResource(R.string.addons_choice_cs_title)
            SourceType.NUVIO_REPO -> stringResource(R.string.addons_choice_nuvio_repo_title)
            SourceType.NUVIO_PLUGIN -> stringResource(R.string.addons_choice_nuvio_plugin_title)
            null -> stringResource(R.string.addons_title)
        }
        AlertDialog(
            onDismissRequest = { if (!busy) handler.dismiss() },
            title = { Text(stringResource(R.string.deeplink_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.deeplink_confirm_message, "${payload.url}\n\n[$typeName]"),
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.addons_add_checking),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = handler::confirm, enabled = !busy, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.deeplink_confirm_add))
                }
            },
            dismissButton = {
                TextButton(onClick = handler::dismiss, enabled = !busy) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    DeepLinkSnackbar(handler.events)

    actualRepoRequests?.let { requests ->
        val pendingBatch by requests.pendingRequest.collectAsStateWithLifecycle()
        val repoBusy by requests.busy.collectAsStateWithLifecycle()
        pendingBatch?.let { items ->
            val focus = rememberInitialFocus()
            AlertDialog(
                onDismissRequest = { if (!repoBusy) requests.dismiss() },
                title = { Text(stringResource(R.string.plugin_repo_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.plugin_repo_confirm_message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            items.take(8).forEach { item ->
                                Text(
                                    text = "• ${item.name.ifBlank { item.url }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (items.size > 8) {
                                Text(
                                    text = stringResource(R.string.plugin_repo_and_more, items.size - 8),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (repoBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(R.string.addons_add_checking),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = requests::confirm, enabled = !repoBusy, modifier = Modifier.focusRequester(focus)) {
                        Text(stringResource(R.string.plugin_repo_confirm_add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = requests::dismiss, enabled = !repoBusy) {
                        Text(stringResource(R.string.plugin_repo_confirm_decline))
                    }
                },
            )
        }
        PluginRepoSnackbar(requests.events)
    }
}

@Composable
private fun DeepLinkSnackbar(events: Flow<DeepLinkEvent>) {
    val host = remember { SnackbarHostState() }
    var current by remember { mutableStateOf<DeepLinkEvent?>(null) }
    LaunchedEffect(events) { events.collect { current = it } }

    val text = when (val e = current) {
        null -> null
        is DeepLinkEvent.Added -> when (e.result) {
            is AddResult.Addon -> stringResource(R.string.deeplink_plugin_added)
            is AddResult.PluginRepo, is AddResult.CsRepo -> stringResource(R.string.deeplink_repo_added)
        }
        is DeepLinkEvent.Failed -> e.error.userMessage()
        DeepLinkEvent.Invalid -> stringResource(R.string.deeplink_invalid)
    }
    LaunchedEffect(current, text) {
        if (text != null) {
            host.showSnackbar(text)
            current = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = host,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
        )
    }
}

@Composable
private fun PluginRepoSnackbar(events: Flow<PluginRepoEvent>) {
    val host = remember { SnackbarHostState() }
    var current by remember { mutableStateOf<PluginRepoEvent?>(null) }
    LaunchedEffect(events) { events.collect { current = it } }

    val text = when (val e = current) {
        null -> null
        is PluginRepoEvent.Completed -> {
            if (e.successCount > 0) {
                stringResource(R.string.plugin_repo_added_result, e.successCount)
            } else {
                stringResource(R.string.plugin_repo_add_failed)
            }
        }
    }
    LaunchedEffect(current, text) {
        if (text != null) {
            host.showSnackbar(text)
            current = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = host,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
        )
    }
}
