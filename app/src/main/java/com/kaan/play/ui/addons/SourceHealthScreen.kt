package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.diag.AppExitDiagnostics
import com.kaan.play.data.diag.AppExitSnapshot
import com.kaan.play.data.diag.ExitAttribution
import com.kaan.play.data.diag.ExitCategory
import com.kaan.play.data.stream.SourceHealthRules
import com.kaan.play.data.stream.SourceHealthState
import com.kaan.play.data.stream.WrongContentBlockEntry
import com.kaan.play.data.stream.WrongContentBlocker
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.PlayCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Kaynak sağlığı ekranı (Ayarlar > Eklentiler içinden açılır):
 * sorunlu sunucuları, geçici kara listeyi, güvenilirliği, yanlış içerik engellerini,
 * eklenti yanıt sağlığını ve Android 11+ kapanma teşhislerini yerel olarak gösterir.
 */
@Composable
fun SourceHealthScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = rememberFocusMemory()
    val locale = Locale.forLanguageTag(AppLanguage.effective().iso)

    val healthState by container.sourceHealth.state.collectAsStateWithLifecycle(initialValue = SourceHealthState())
    val wrongBlocks by container.wrongContentBlocks.entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val audioSyncCounts by container.audioSyncReports.counts.collectAsStateWithLifecycle(initialValue = emptyMap())
    var exitSnapshot by remember { mutableStateOf(AppExitSnapshot(supported = false, exits = emptyList())) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.sourceHealth.ensureLoaded()
        container.wrongContentBlocks.ensureLoaded()
        exitSnapshot = AppExitDiagnostics.read(context)
    }

    val nowMs = remember(healthState) { System.currentTimeMillis() }
    val blacklisted = remember(healthState, nowMs) { SourceHealthRules.activeBlacklists(healthState, nowMs) }
    val recentErrors = remember(healthState) { SourceHealthRules.recentErrors(healthState) }
    val worstReliability = remember(healthState) { SourceHealthRules.worstReliability(healthState) }
    val worstPlugins = remember(healthState) { SourceHealthRules.worstPlugins(healthState) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.source_health_title),
            subtitle = stringResource(R.string.source_health_subtitle),
            onBack = onBack,
            initialFocus = false,
            backModifier = Modifier.focusMemory(focus, "back", isDefault = true),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // P3) Kara liste
            item(key = "sec-blacklist") { SectionTitle(stringResource(R.string.source_health_section_blacklist)) }
            if (blacklisted.isEmpty()) {
                item(key = "empty-blacklist") { EmptySectionLine() }
            } else {
                items(blacklisted, key = { "blk-${it.host}" }) { rec ->
                    val remaining = formatRemainingTime(rec.remainingMs(nowMs))
                    PlayCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.host,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.dot_join, rec.lastAddonName, remaining),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { scope.launch { container.sourceHealth.reenableHost(rec.host) } },
                                modifier = Modifier.focusMemory(focus, "blk-${rec.host}"),
                            ) {
                                Text(stringResource(R.string.source_health_reenable))
                            }
                        }
                    }
                }
            }

            // P1) Hata geçmişi (en yeni üstte, en çok 8)
            item(key = "sec-errors") { SectionTitle(stringResource(R.string.source_health_section_errors)) }
            if (recentErrors.isEmpty()) {
                item(key = "empty-errors") { EmptySectionLine() }
            } else {
                itemsIndexed(recentErrors, key = { idx, it -> "err-$idx-${it.timestampMs}-${it.host}" }) { idx, rec ->
                    val errText = localizedErrorText(rec.error)
                    val dateText = formatDateTime(rec.timestampMs, locale)
                    PlayCard(
                        onClick = {},
                        modifier = Modifier.focusMemory(focus, "err-$idx"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.dot_join, rec.addonName, rec.host),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.dot_join, errText, dateText),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // P2) Güvenilirlik + ses senkronu bildirimi
            item(key = "sec-reliability") { SectionTitle(stringResource(R.string.source_health_section_reliability)) }
            if (worstReliability.isEmpty()) {
                item(key = "empty-reliability") { EmptySectionLine() }
            } else {
                items(worstReliability, key = { "rel-${it.pluginKey}-${it.host}" }) { rec ->
                    val syncCount = audioSyncCounts[rec.pluginKey]
                        ?: audioSyncCounts["name:" + WrongContentBlocker.fold(rec.addonName)]
                        ?: 0
                    val attemptsLine = pluralStringResource(
                        R.plurals.source_health_reliability_line,
                        rec.totalAttempts,
                        rec.successPercent,
                        rec.totalAttempts,
                    )
                    val subtitle = if (syncCount > 0) {
                        val syncPart = pluralStringResource(R.plurals.source_health_audio_sync_count, syncCount, syncCount)
                        stringResource(R.string.dot_join, attemptsLine, syncPart)
                    } else {
                        attemptsLine
                    }
                    PlayCard(
                        onClick = {},
                        modifier = Modifier.focusMemory(focus, "rel-${rec.pluginKey}-${rec.host}"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.dot_join, rec.addonName, rec.host),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // P6) Eklenti sağlığı (son 20 çağrı)
            item(key = "sec-plugins") { SectionTitle(stringResource(R.string.source_health_section_plugins)) }
            if (worstPlugins.isEmpty()) {
                item(key = "empty-plugins") { EmptySectionLine() }
            } else {
                items(worstPlugins, key = { "plug-${it.pluginKey}" }) { rec ->
                    val mainLine = pluralStringResource(
                        R.plurals.source_health_plugin_unresponsive,
                        rec.unresponsiveCount,
                        rec.unresponsiveCount,
                    )
                    val countsLine = stringResource(
                        R.string.source_health_plugin_breakdown,
                        rec.returnedStreamsCount,
                        rec.noStreamsCount,
                    )
                    PlayCard(
                        onClick = {},
                        modifier = Modifier.focusMemory(focus, "plug-${rec.pluginKey}"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = rec.addonName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = mainLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = countsLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // P4) Yanlış içerik engelleri listesi
            item(key = "sec-wrong") { SectionTitle(stringResource(R.string.source_health_section_wrong_content)) }
            if (wrongBlocks.isEmpty()) {
                item(key = "empty-wrong") { EmptySectionLine() }
            } else {
                items(wrongBlocks, key = { "wc-${it.key}" }) { entry ->
                    WrongContentBlockRow(
                        entry = entry,
                        locale = locale,
                        modifier = Modifier.focusMemory(focus, "wc-${entry.key}"),
                        onRemove = { scope.launch { container.wrongContentBlocks.remove(entry.key) } },
                    )
                }
            }

            // P5) Çökme / kapanma teşhisi (Android 11+ / API 30+)
            if (exitSnapshot.supported) {
                item(key = "sec-exits") { SectionTitle(stringResource(R.string.source_health_section_exits)) }
                if (exitSnapshot.exits.isEmpty()) {
                    item(key = "empty-exits") { EmptySectionLine() }
                } else {
                    itemsIndexed(exitSnapshot.exits, key = { idx, it -> "exit-$idx-${it.timestampMs}" }) { idx, exit ->
                        val reasonLabel = when (exit.category) {
                            ExitCategory.Anr -> stringResource(R.string.source_health_exit_anr)
                            ExitCategory.Crash -> stringResource(R.string.source_health_exit_crash)
                            ExitCategory.NativeCrash -> stringResource(R.string.source_health_exit_native)
                            ExitCategory.InitFailure -> stringResource(R.string.source_health_exit_init)
                            ExitCategory.ExcessiveResource -> stringResource(R.string.source_health_exit_resource)
                            ExitCategory.LowMemory -> stringResource(R.string.source_health_exit_low_mem)
                            ExitCategory.SystemOrOther -> stringResource(R.string.source_health_exit_system)
                        }
                        val attrLabel = when (exit.attribution) {
                            ExitAttribution.App -> stringResource(R.string.source_health_exit_attr_app)
                            ExitAttribution.System -> stringResource(R.string.source_health_exit_attr_system)
                        }
                        val dateText = formatDateTime(exit.timestampMs, locale)
                        PlayCard(
                            onClick = {},
                            modifier = Modifier.focusMemory(focus, "exit-$idx"),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = reasonLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.dot_join, attrLabel, dateText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // P7) Kayıtları temizle (P4'e dokunmaz)
            item(key = "clear-logs") {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusMemory(focus, "clear"),
                ) {
                    Text(stringResource(R.string.source_health_clear_logs))
                }
            }
        }
    }

    if (confirmClear) {
        val cancelFocus = rememberInitialFocus()
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.source_health_clear_logs), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.source_health_clear_confirm), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            container.sourceHealth.clearHealthLogs()
                            container.audioSyncReports.clear()
                        }
                    },
                ) {
                    Text(stringResource(R.string.source_health_clear_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    modifier = Modifier.focusRequester(cancelFocus),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun WrongContentBlockRow(
    entry: WrongContentBlockEntry,
    locale: Locale,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentLabel = when {
        !entry.contentTitle.isNullOrBlank() && (entry.year ?: 0) > 0 ->
            stringResource(R.string.source_health_title_with_year, entry.contentTitle, entry.year!!)
        !entry.contentTitle.isNullOrBlank() -> entry.contentTitle
        else -> stringResource(R.string.source_health_unknown_content)
    }
    val addonLabel = entry.addonName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.source_health_unknown_addon)
    val dateLabel = formatDateTime(entry.addedAtMs, locale)

    PlayCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.dot_join, addonLabel, dateLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onRemove,
                modifier = modifier,
            ) {
                Text(stringResource(R.string.source_health_remove))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptySectionLine() {
    Text(
        text = stringResource(R.string.source_health_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun localizedErrorText(rawCode: String): String = when (rawCode) {
    SourceHealthRules.ERR_TIMEOUT -> stringResource(R.string.source_health_err_timeout)
    SourceHealthRules.ERR_FORMAT -> stringResource(R.string.source_health_err_format)
    SourceHealthRules.ERR_NETWORK -> stringResource(R.string.source_health_err_network)
    SourceHealthRules.ERR_NOT_MEDIA -> stringResource(R.string.source_health_err_not_media)
    SourceHealthRules.ERR_UNKNOWN -> stringResource(R.string.source_health_err_unknown)
    else -> rawCode
}

@Composable
private fun formatRemainingTime(remainingMs: Long): String {
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.source_health_remaining_hours_mins, hours, minutes)
    } else {
        stringResource(R.string.source_health_remaining_mins, minutes)
    }
}

private fun formatDateTime(epochMs: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(epochMs))
