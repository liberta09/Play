package com.kaan.play.ui.addons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.collection.ImportedCollection
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import kotlinx.coroutines.launch

/** Ayarlar > Eklentiler > Koleksiyonlar: içe aktarılan JSON koleksiyonlarını listeler ve kaldırır. */
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    container: AppContainer,
) {
    val collections by container.collections.collections.collectAsStateWithLifecycle(initialValue = null)
    var toRemove by remember { mutableStateOf<ImportedCollection?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.collections_title),
            onBack = onBack,
            initialFocus = collections?.isEmpty() == true,
        )

        when {
            collections == null -> Unit
            collections!!.isEmpty() -> EmptyMessage(stringResource(R.string.collections_empty))
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(collections!!, key = { it.id }) { collection ->
                        CollectionRow(
                            collection = collection,
                            onRemove = { toRemove = collection },
                        )
                    }
                }
            }
        }
    }

    toRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.collections_remove_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(
                        R.string.collections_remove_message,
                        target.title.ifBlank { stringResource(R.string.collections_default_title) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.collections.removeCollection(target.id) }
                        toRemove = null
                    },
                ) {
                    Text(stringResource(R.string.collections_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun CollectionRow(
    collection: ImportedCollection,
    onRemove: () -> Unit,
) {
    val displayTitle = collection.title.ifBlank { stringResource(R.string.collections_default_title) }
    val folderText = pluralStringResource(R.plurals.collections_folders_count, collection.folders.size, collection.folders.size)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = folderText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.focusRing()) {
                Icon(
                    imageVector = PlayIcons.Trash,
                    contentDescription = stringResource(R.string.collections_remove_confirm),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
