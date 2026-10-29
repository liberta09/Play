package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaan.play.ui.components.ScreenHeader

@Composable
fun SyncScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Senkronizasyon",
            onBack = onBack,
        )

        Text(
            text = "Bulut Eşitlemesi",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Hesabınıza giriş yaparak izleme geçmişinizi, profillerinizi ve favorilerinizi bulutta saklayabilir, tüm cihazlarınızda kaldığınız yerden devam edebilirsiniz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AccountAccessPanel()

        Spacer(modifier = Modifier.height(16.dp))
    }
}
