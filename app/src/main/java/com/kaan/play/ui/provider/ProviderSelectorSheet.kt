package com.kaan.play.ui.provider

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.provider.ProviderConstants
import com.kaan.play.data.provider.ProviderItem
import com.kaan.play.data.provider.ProviderRules
import com.kaan.play.data.provider.ProviderSectionKind
import com.kaan.play.data.provider.ProviderTab
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberCursorField
import com.kaan.play.ui.tv.rememberInitialFocus

/**
 * "Bir uzantı seçin" seçici ekranı.
 * Tek dokunuş: tek sağlayıcıya geçip kapatır.
 * Uzun basma (0.5s): çoklu seçim moduna girer, üstte "Uygula" düğmesi çıkar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectorSheet(
    providers: List<ProviderItem>,
    selectedIds: Set<String>,
    onSelectSingle: (String) -> Unit,
    onApplyMulti: (Set<String>) -> Unit,
    onTogglePin: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddExtension: () -> Unit,
    isLoading: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ProviderTab.All) }
    var isMultiSelect by remember { mutableStateOf(false) }
    var draftSelected by remember(selectedIds) { mutableStateOf(selectedIds - ProviderConstants.ALL_PROVIDERS_ID) }

    // Önce Nuvio, sonra CloudStream; sekme ve arama iki bölümü birlikte süzer (ProviderRules.selectorSections).
    val sections = remember(providers, selectedTab, query) {
        ProviderRules.selectorSections(providers, selectedTab, query)
    }

    val inputMode = LocalInputModeManager.current.inputMode
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = if (inputMode == InputMode.Touch) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Üst başlık alanı ve çoklu seçim modu eylemleri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.provider_selector_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isMultiSelect) {
                            stringResource(R.string.provider_selected_count, draftSelected.size)
                        } else {
                            stringResource(R.string.provider_selector_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMultiSelect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isMultiSelect) {
                    Button(
                        onClick = {
                            onApplyMulti(draftSelected)
                            onDismiss()
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(stringResource(R.string.provider_apply))
                    }
                } else if (providers.isNotEmpty()) {
                    // Kumanda/TV: çoklu seçim yalnızca basılı tutmaya bağlı kalmasın (CLAUDE.md TV kuralı).
                    TextButton(
                        onClick = { isMultiSelect = true },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(stringResource(R.string.provider_multi_select))
                    }
                }
            }

            if (isLoading && providers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.provider_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (providers.isEmpty()) {
                // Hiç sağlayıcı yokken boş durum
                EmptyProvidersView(
                    onAddExtension = {
                        onDismiss()
                        onAddExtension()
                    },
                )
            } else {
                // Arama kutusu
                val field = rememberCursorField(query) { query = it }
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    placeholder = { Text(stringResource(R.string.provider_search_hint)) },
                    leadingIcon = { Icon(PlayIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(PlayIcons.Close, contentDescription = stringResource(R.string.provider_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .dpadLeavesTextField(field),
                )

                // Sekme çipleri: Hepsi / CloudStream / Nuvio
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "tab-all") {
                        FilterPill(
                            text = stringResource(R.string.provider_tab_all),
                            selected = selectedTab == ProviderTab.All,
                            onClick = { selectedTab = ProviderTab.All },
                        )
                    }
                    item(key = "tab-cs") {
                        FilterPill(
                            text = stringResource(R.string.provider_tab_cloudstream),
                            selected = selectedTab == ProviderTab.CloudStream,
                            onClick = { selectedTab = ProviderTab.CloudStream },
                        )
                    }
                    item(key = "tab-nuvio") {
                        FilterPill(
                            text = stringResource(R.string.provider_tab_nuvio),
                            selected = selectedTab == ProviderTab.Nuvio,
                            onClick = { selectedTab = ProviderTab.Nuvio },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                val allProvidersTitle = stringResource(R.string.provider_all_providers)

                // Sağlayıcılar listesi
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // En üstte "Tüm sağlayıcılar" satırı (arama boşken veya eşleşirken gösterilir)
                    if (query.isBlank() || allProvidersTitle.lowercase().contains(query.lowercase())) {
                        item(key = "provider_all") {
                            val isAllSelected = if (isMultiSelect) {
                                draftSelected.isEmpty()
                            } else {
                                ProviderRules.isAllProviders(selectedIds)
                            }
                            AllProvidersRow(
                                isSelected = isAllSelected,
                                onClick = {
                                    if (isMultiSelect) {
                                        draftSelected = emptySet()
                                    } else {
                                        onSelectSingle(ProviderConstants.ALL_PROVIDERS_ID)
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }

                    sections.forEach { section ->
                        // Bölüm başlığı (Nuvio / CloudStream)
                        item(key = "header_${section.kind.name}") {
                            Text(
                                text = stringResource(
                                    when (section.kind) {
                                        ProviderSectionKind.Nuvio -> R.string.provider_section_nuvio
                                        ProviderSectionKind.CloudStream -> R.string.provider_section_cloudstream
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                            )
                        }

                        // Eklentiler listesi
                        items(section.items, key = { it.id }) { item ->
                            val isSelected = item.hasHomeCatalog && if (isMultiSelect) {
                                item.id in draftSelected
                            } else {
                                item.id in selectedIds && !ProviderRules.isAllProviders(selectedIds)
                            }

                            ProviderRow(
                                item = item,
                                isSelected = isSelected,
                                isMultiSelect = isMultiSelect,
                                selectable = item.hasHomeCatalog,
                                onClick = {
                                    if (isMultiSelect) {
                                        draftSelected = ProviderRules.toggleMultiSelect(draftSelected, item.id)
                                    } else {
                                        onSelectSingle(item.id)
                                        onDismiss()
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelect) {
                                        isMultiSelect = true
                                        draftSelected = setOf(item.id)
                                    }
                                },
                                onTogglePin = { onTogglePin(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProvidersView(onAddExtension: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.provider_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val initialFocus = rememberInitialFocus()
        Button(
            onClick = onAddExtension,
            modifier = Modifier.focusRequester(initialFocus),
        ) {
            Text(stringResource(R.string.provider_empty_action))
        }
    }
}

@Composable
private fun AllProvidersRow(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .focusRing(shape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.provider_all_providers),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.provider_all_providers_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = PlayIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderRow(
    item: ProviderItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (selectable) Modifier.focusRing(shape).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ) else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!selectable) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.provider_no_home_catalog),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // İğneleme (sabitleme) düğmesi
        if (selectable) {
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.focusRing(CircleShape),
            ) {
                Icon(
                    imageVector = if (item.isPinned) PlayIcons.PinFilled else PlayIcons.Pin,
                    contentDescription = stringResource(if (item.isPinned) R.string.provider_unpin else R.string.provider_pin),
                    tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Seçim göstergesi
        if (selectable && isSelected) {
            Icon(
                imageVector = PlayIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (selectable && isMultiSelect) {
            // Çoklu seçim modunda seçilmemiş öğeler için boş daire
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}
