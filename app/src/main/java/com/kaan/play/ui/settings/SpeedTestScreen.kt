package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.speedtest.InternetSpeedResult
import com.kaan.play.data.speedtest.SpeedTestHttpException
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import android.util.Log

@Composable
fun SpeedTestScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<InternetSpeedResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_speed_test), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.speed_test_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            result?.let { SpeedResultCard(it) }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    if (running) return@Button
                    running = true
                    errorMessage = null
                    result = null
                    scope.launch {
                        try {
                            result = container.internetSpeedTest.measure()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("PlaySpeedTest", "Speed test failed", e)
                            errorMessage = when (e) {
                                is UnknownHostException -> context.getString(R.string.speed_test_error_network)
                                is SocketTimeoutException -> context.getString(R.string.speed_test_error_timeout)
                                is SpeedTestHttpException -> context.getString(R.string.speed_test_error_server, e.statusCode)
                                else -> context.getString(R.string.speed_test_error_detail, e.message ?: e.javaClass.simpleName)
                            }
                        } finally {
                            running = false
                        }
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Text(stringResource(R.string.speed_test_running))
                } else {
                    Text(stringResource(R.string.speed_test_start))
                }
            }
            Text(
                text = stringResource(R.string.speed_test_data_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SpeedResultCard(result: InternetSpeedResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.speed_test_result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.speed_test_download_label),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.speed_test_download_value, result.downloadMbps),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.speed_test_ping_label),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.speed_test_ping_value, result.pingMs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = stringResource(R.string.speed_test_result_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
