package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.settings.WhatsNewCoordinator
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.tv.rememberInitialFocus

/** Uygulama güncellemesinden sonraki ilk açılışta tek seferlik "Bu sürümde neler yeni?" penceresi. */
@Composable
fun WhatsNewDialog(coordinator: WhatsNewCoordinator) {
    val visible by coordinator.visible.collectAsStateWithLifecycle()
    if (!visible) return

    val scope = rememberCoroutineScope()
    val dismissFocus = rememberInitialFocus()

    AlertDialog(
        onDismissRequest = { coordinator.dismiss(scope) },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_version, coordinator.versionName),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.whats_new_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { coordinator.dismiss(scope) },
                modifier = Modifier.focusRequester(dismissFocus),
            ) {
                Text(stringResource(R.string.whats_new_dismiss))
            }
        },
    )
}
