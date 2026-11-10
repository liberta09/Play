package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.speedtest.InternetSpeedResult
import com.kaan.play.data.speedtest.InternetSpeedTest
import com.kaan.play.data.speedtest.SpeedTestError
import com.kaan.play.data.speedtest.SpeedTestOutcome
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.PlayCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SpeedTestScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val focus = rememberFocusMemory()
    val locale = remember { Locale.forLanguageTag(AppLanguage.effective().iso) }

    var activeJob by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(InternetSpeedTest.TARGET_BYTES) }
    var result by remember { mutableStateOf<InternetSpeedResult?>(null) }
    var failed by remember { mutableStateOf<SpeedTestError?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            activeJob = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_speed_test),
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.speed_test_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(18.dp))

        failed?.let { error ->
            val reason = when (error) {
                SpeedTestError.Unreachable -> stringResource(R.string.speed_test_error_unreachable)
                SpeedTestError.Timeout -> stringResource(R.string.speed_test_error_timeout)
                is SpeedTestError.Other -> error.detail
            }
            val displayText = if (error is SpeedTestError.Other) {
                stringResource(R.string.speed_test_failed, reason)
            } else {
                reason
            }
            Text(
                text = displayText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        if (running) {
            PlayCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fraction = (downloadedBytes.toFloat() / totalBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            R.string.speed_test_progress_mb,
                            InternetSpeedTest.formatMegabytes(downloadedBytes, locale),
                            InternetSpeedTest.formatMegabytes(totalBytes, locale),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        result?.let {
            SpeedResultCard(result = it, locale = locale)
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (running) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.speed_test_testing))
                }
                OutlinedButton(
                    onClick = {
                        activeJob?.cancel()
                        activeJob = null
                        running = false
                    },
                    modifier = Modifier.focusMemory(focus, "speed_cancel", isDefault = true),
                ) {
                    Text(stringResource(R.string.speed_test_cancel))
                }
            }
        } else {
            Button(
                onClick = {
                    if (activeJob == null) {
                        running = true
                        downloadedBytes = 0L
                        totalBytes = InternetSpeedTest.TARGET_BYTES
                        result = null
                        failed = null
                        activeJob = scope.launch {
                            try {
                                when (
                                    val res = container.internetSpeedTest.runTest { down, total ->
                                        downloadedBytes = down
                                        totalBytes = total
                                    }
                                ) {
                                    is SpeedTestOutcome.Failure -> failed = res.error
                                    is SpeedTestOutcome.Success -> result = res.result
                                }
                            } catch (_: CancellationException) {
                                // Kullanıcı iptal etti.
                            } finally {
                                running = false
                                activeJob = null
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusMemory(focus, "speed_start", isDefault = true),
            ) {
                Text(stringResource(if (result != null) R.string.speed_test_retry else R.string.speed_test_start))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SpeedResultCard(result: InternetSpeedResult, locale: Locale) {
    PlayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.speed_test_result_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.speed_test_download_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = InternetSpeedTest.formatMbps(result.downloadMbps, locale),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.speed_test_downloaded_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = InternetSpeedTest.formatMegabytes(result.bytesDownloaded, locale),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
