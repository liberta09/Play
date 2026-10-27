package com.kaan.play.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.play.R
import com.kaan.play.data.speedtest.InternetSpeedResult
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

@Composable
fun SpeedTestScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<InternetSpeedResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "İnternet Hız Testi",
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Cloudflare CDN üzerinden ölçülen yaklaşık hız.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (failed) {
            Text(
                text = "Test tamamlanamadı. Bağlantınızı kontrol edip tekrar deneyin.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        result?.let {
            SpeedResultCard(it)
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = {
                if (!running) {
                    scope.launch {
                        running = true
                        failed = false
                        val res = container.internetSpeedTest.runTest()
                        running = false
                        if (res == null) {
                            failed = true
                        } else {
                            result = res
                        }
                    }
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Test ediliyor...")
            } else {
                Text(if (result != null) "Tekrar Test Et" else "Hız testini başlat")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Yaklaşık 9 MB veri kullanır. Bu test sunucusuna olan bağlantınızı ölçer, belirli bir video kaynağının hızını ölçmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SpeedResultCard(result: InternetSpeedResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Sonucunuz",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "İndirme Hızı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "%.1f Mbps".format(result.downloadMbps),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Gecikme (Ping)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${result.pingMs} ms",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
