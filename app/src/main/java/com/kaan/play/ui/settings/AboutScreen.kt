package com.kaan.play.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.PlayCard
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.theme.PlayColors
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.launch

private enum class DataConfirmAction { ClearHistory, ClearFavorites, ResetSettings }

/**
 * Hakkında ekranı: Telegram Topluluğu kartı, sürüm/lisans metinleri ve Veri yönetimi bölümü.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingConfirm by remember { mutableStateOf<DataConfirmAction?>(null) }
    val telegramUrl = stringResource(R.string.about_telegram_url)
    val cardShape = RoundedCornerShape(16.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
        com.kaan.play.ui.components.ScreenHeader(
            title = stringResource(R.string.settings_about),
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Telegram Topluluk Kartı
        Surface(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
            },
            shape = cardShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .focusRing(cardShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Işıltılı Telegram dairesel ikonu
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), Color.Transparent),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PlayColors.TelegramBlue,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = PlayIcons.TelegramPlane,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_telegram_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_telegram_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Sağ dairesel ok butonu
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PlayIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Bilgi ve Lisans/Atıf Metinleri
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.about_no_content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_web_blocking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_introdb_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_crash_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_tmdb_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_license_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.data_management_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(10.dp))

            val cacheDoneMessage = stringResource(R.string.data_clear_cache_done)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataManagementRow(
                    title = stringResource(R.string.data_clear_cache),
                    subtitle = stringResource(R.string.data_clear_cache_summary),
                    icon = PlayIcons.Refresh,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        scope.launch {
                            container.clearCaches()
                            snackbarHostState.showSnackbar(cacheDoneMessage)
                        }
                    },
                )
                DataManagementRow(
                    title = stringResource(R.string.data_clear_history),
                    subtitle = stringResource(R.string.data_clear_history_summary),
                    icon = PlayIcons.Trash,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { pendingConfirm = DataConfirmAction.ClearHistory },
                )
                DataManagementRow(
                    title = stringResource(R.string.data_clear_favorites),
                    subtitle = stringResource(R.string.data_clear_favorites_summary),
                    icon = PlayIcons.Heart,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { pendingConfirm = DataConfirmAction.ClearFavorites },
                )
                DataManagementRow(
                    title = stringResource(R.string.data_reset_settings),
                    subtitle = stringResource(R.string.data_reset_settings_summary),
                    icon = PlayIcons.Settings,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { pendingConfirm = DataConfirmAction.ResetSettings },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }

    pendingConfirm?.let { action ->
        val cancelFocus = rememberInitialFocus()
        val (titleText, bodyText, confirmLabel, doneMessage) = when (action) {
            DataConfirmAction.ClearHistory -> QuadText(
                stringResource(R.string.data_clear_history),
                stringResource(R.string.data_clear_history_confirm),
                stringResource(R.string.data_confirm_delete),
                stringResource(R.string.data_clear_history_done),
            )
            DataConfirmAction.ClearFavorites -> QuadText(
                stringResource(R.string.data_clear_favorites),
                stringResource(R.string.data_clear_favorites_confirm),
                stringResource(R.string.data_confirm_delete),
                stringResource(R.string.data_clear_favorites_done),
            )
            DataConfirmAction.ResetSettings -> QuadText(
                stringResource(R.string.data_reset_settings),
                stringResource(R.string.data_reset_settings_confirm),
                stringResource(R.string.data_reset_settings_action),
                stringResource(R.string.data_reset_settings_done),
            )
        }
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(titleText, style = MaterialTheme.typography.titleLarge) },
            text = { Text(bodyText, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirm = null
                        scope.launch {
                            when (action) {
                                DataConfirmAction.ClearHistory -> {
                                    container.library.clearHistory()
                                    container.liveChannels.clearWatchHistory()
                                }
                                DataConfirmAction.ClearFavorites -> {
                                    container.library.clearFavorites()
                                    container.liveChannels.clearFavorites()
                                }
                                DataConfirmAction.ResetSettings -> {
                                    container.settings.resetAppearanceAndPlayerDefaults()
                                    if (AppLanguage.choice() != LanguageChoice.System) {
                                        AppLanguage.apply(LanguageChoice.System)
                                    }
                                }
                            }
                            snackbarHostState.showSnackbar(doneMessage)
                        }
                    },
                ) {
                    Text(
                        text = confirmLabel,
                        color = if (action == DataConfirmAction.ResetSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }, modifier = Modifier.focusRequester(cancelFocus)) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private data class QuadText(val a: String, val b: String, val c: String, val d: String)

@Composable
private fun DataManagementRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    PlayCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
