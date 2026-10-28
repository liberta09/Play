package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.sync.SourceTransferItem
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.focusRing

/*
 * Wi-Fi senkronizasyonu kartı ve Bulut Hesabı paneli bu tek onay bileşenini paylaşır: karşı cihazda/bulutta olup bu
 * cihazda olmayan kaynaklar kendiliğinden kurulmaz.
 */

/** "Şunlar eklensin mi?": adresler görünür; "Hepsini ekle" ya da tek tek seçim. Yalnızca onaylananlar eklenir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingSourcesList(
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
