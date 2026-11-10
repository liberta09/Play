package com.kaan.play.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.net.CustomDohResult
import com.kaan.play.data.net.DnsCheckResult
import com.kaan.play.data.net.DnsMode
import com.kaan.play.data.net.DnsRules
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.PlayCard
import com.kaan.play.ui.components.PlayCardRow
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberCursorField
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

/**
 * Ağ ve İnternet ayarları (Ayarlar > Eklentiler içinden açılır):
 * Uygulama içi şifreli DNS (DoH), DNS sunucu denetimi, hız testi ve yavaş bağlantı kısayolu.
 */
@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenSlowConnection: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val focus = rememberFocusMemory()

    val dnsMode by container.settings.dnsMode.collectAsStateWithLifecycle(initialValue = DnsMode.Auto)
    val customDohUrl by container.settings.customDohUrl.collectAsStateWithLifecycle(initialValue = "")

    var checkingDns by remember { mutableStateOf(false) }
    var dnsCheckResult by remember { mutableStateOf<DnsCheckResult?>(null) }

    var showCustomDialog by remember { mutableStateOf(false) }
    var customDraft by remember { mutableStateOf("") }
    var customInvalid by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.network_settings_title),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "info") {
                PlayCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.network_settings_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "dns_check") {
                PlayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.network_dns_section) + ": " + stringResource(dnsMode.titleRes()),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = {
                                if (!checkingDns) {
                                    checkingDns = true
                                    dnsCheckResult = null
                                    scope.launch {
                                        val res = container.playDns.checkExampleCom(dnsMode, customDohUrl)
                                        dnsCheckResult = res
                                        checkingDns = false
                                    }
                                }
                            },
                            enabled = !checkingDns,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusMemory(focus, "dns_check_btn", isDefault = true),
                        ) {
                            if (checkingDns) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.network_dns_check_running))
                            } else {
                                Text(stringResource(R.string.network_dns_check_button))
                            }
                        }

                        dnsCheckResult?.let { result ->
                            val (resId, color) = when (result) {
                                DnsCheckResult.Resolved -> R.string.network_dns_check_resolved to MaterialTheme.colorScheme.primary
                                DnsCheckResult.Empty -> R.string.network_dns_check_empty to MaterialTheme.colorScheme.secondary
                                DnsCheckResult.Failed -> R.string.network_dns_check_failed to MaterialTheme.colorScheme.error
                            }
                            Text(
                                text = stringResource(resId),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = color,
                            )
                        }

                        Text(
                            text = stringResource(R.string.network_dns_check_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "speed_test") {
                PlayCardRow(
                    title = stringResource(R.string.settings_speed_test),
                    subtitle = stringResource(R.string.settings_speed_test_summary),
                    icon = PlayIcons.Globe,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "network_speed_test"),
                    onClick = onOpenSpeedTest,
                )
            }

            item(key = "slow_mode_shortcut") {
                PlayCardRow(
                    title = stringResource(R.string.settings_slow_connection_mode),
                    subtitle = stringResource(R.string.network_slow_mode_shortcut_subtitle),
                    icon = PlayIcons.Clock,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.focusMemory(focus, "network_slow_mode"),
                    onClick = onOpenSlowConnection,
                )
            }

            item(key = "dns_header") {
                Text(
                    text = stringResource(R.string.network_dns_section),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }

            itemsIndexed(DnsMode.SELECTABLE, key = { _, mode -> "dns_${mode.name}" }) { _, option ->
                val isSelected = dnsMode == option
                val subtitleText = if (option == DnsMode.Custom && customDohUrl.isNotBlank()) {
                    customDohUrl
                } else {
                    stringResource(option.descRes())
                }
                PlayCardRow(
                    title = stringResource(option.titleRes()),
                    subtitle = subtitleText,
                    icon = PlayIcons.Globe,
                    iconTint = MaterialTheme.colorScheme.primary,
                    selected = isSelected,
                    modifier = Modifier.focusMemory(focus, "dns_${option.name}"),
                    trailing = {
                        if (isSelected) {
                            Icon(
                                imageVector = PlayIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        if (option == DnsMode.Custom) {
                            customDraft = customDohUrl
                            customInvalid = false
                            showCustomDialog = true
                        } else {
                            dnsCheckResult = null
                            container.playDns.updateConfig(option, customDohUrl)
                            scope.launch { container.settings.setDnsMode(option) }
                        }
                    },
                )
            }
        }
    }

    if (showCustomDialog) {
        val submitCustom: () -> Unit = {
            when (val res = DnsRules.normalizeCustomDohUrl(customDraft)) {
                is CustomDohResult.Invalid -> {
                    customInvalid = true
                }
                is CustomDohResult.Valid -> {
                    customInvalid = false
                    showCustomDialog = false
                    dnsCheckResult = null
                    container.playDns.updateConfig(DnsMode.Custom, res.url)
                    scope.launch { container.settings.setDnsConfig(DnsMode.Custom, res.url) }
                }
            }
        }

        val cursorField = rememberCursorField(customDraft) {
            customDraft = it
            if (customInvalid) customInvalid = false
        }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(stringResource(R.string.network_dns_custom_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cursorField.value,
                        onValueChange = cursorField.onValueChange,
                        singleLine = true,
                        isError = customInvalid,
                        placeholder = { Text(stringResource(R.string.network_dns_custom_hint)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitCustom() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadLeavesTextField(cursorField),
                    )
                    if (customInvalid) {
                        Text(
                            text = stringResource(R.string.network_dns_custom_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = submitCustom) {
                    Text(stringResource(R.string.network_dns_custom_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.network_dns_custom_cancel))
                }
            },
        )
    }
}

@StringRes
private fun DnsMode.titleRes(): Int = when (this) {
    DnsMode.Auto -> R.string.network_dns_auto
    DnsMode.Cloudflare -> R.string.network_dns_cloudflare
    DnsMode.Google -> R.string.network_dns_google
    DnsMode.Quad9 -> R.string.network_dns_quad9
    DnsMode.AdGuard -> R.string.network_dns_adguard
    DnsMode.Mullvad -> R.string.network_dns_mullvad
    DnsMode.DnsSb -> R.string.network_dns_dnssb
    DnsMode.CanadianShield -> R.string.network_dns_canadian_shield
    DnsMode.CleanBrowsingFamily -> R.string.network_dns_cleanbrowsing
    DnsMode.Custom -> R.string.network_dns_custom
}

@StringRes
private fun DnsMode.descRes(): Int = when (this) {
    DnsMode.Auto -> R.string.network_dns_auto_desc
    DnsMode.Cloudflare -> R.string.network_dns_cloudflare_desc
    DnsMode.Google -> R.string.network_dns_google_desc
    DnsMode.Quad9 -> R.string.network_dns_quad9_desc
    DnsMode.AdGuard -> R.string.network_dns_adguard_desc
    DnsMode.Mullvad -> R.string.network_dns_mullvad_desc
    DnsMode.DnsSb -> R.string.network_dns_dnssb_desc
    DnsMode.CanadianShield -> R.string.network_dns_canadian_shield_desc
    DnsMode.CleanBrowsingFamily -> R.string.network_dns_cleanbrowsing_desc
    DnsMode.Custom -> R.string.network_dns_custom_desc_empty
}
