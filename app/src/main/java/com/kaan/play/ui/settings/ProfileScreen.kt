package com.kaan.play.ui.settings

import android.widget.Toast
import com.kaan.play.ui.tv.focusRing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.kaan.play.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.ui.components.PlayCardRow
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val activeProfile by container.localProfile.activeProfile.collectAsStateWithLifecycle(initialValue = null)

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    var showAvatarDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }

    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Profil Yönetimi",
            onBack = onBack,
        )

        activeProfile?.let { profile ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileAvatar(
                        avatar = profile.avatar,
                        size = 64,
                        modifier = Modifier.focusRing(androidx.compose.foundation.shape.CircleShape).clickable { showAvatarDialog = true },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = if (profile.isChild) "Çocuk Profili" else "Standart Profil",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (profile.hasPin) {
                            Text(
                                text = "PIN Korumalı",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Profil Ayarları",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )

        PlayCardRow(
            title = "Profil Adını Değiştir",
            value = activeProfile?.name ?: "",
            icon = PlayIcons.Settings,
            onClick = {
                renameInput = activeProfile?.name ?: ""
                showRenameDialog = true
            },
        )

        PlayCardRow(
            title = "Avatarı Değiştir",
            value = activeProfile?.avatar ?: "",
            icon = PlayIcons.Palette,
            onClick = { showAvatarDialog = true },
        )

        PlayCardRow(
            title = "PIN Kodu",
            value = if (activeProfile?.hasPin == true) "Korumalı (Değiştir/Kaldır)" else "Belirlenmedi",
            icon = PlayIcons.Lock,
            onClick = {
                pinInput = ""
                showPinDialog = true
            },
        )

        activeProfile?.let { prof ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Çocuk Profili", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Yetişkin içerikleri filtreler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = prof.isChild,
                    onCheckedChange = {
                        scope.launch { container.localProfile.setChildProfile(it) }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Yedekleme ve Aktarım",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )

        PlayCardRow(
            title = "Profili Dışa Aktar (JSON Kopyala)",
            icon = PlayIcons.Forward,
            onClick = {
                scope.launch {
                    val jsonStr = container.localSyncBackup.exportJson()
                    clipboard.setText(AnnotatedString(jsonStr))
                    Toast.makeText(context, "Yedek panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                }
            },
        )

        PlayCardRow(
            title = "Yedekten İçe Aktar (JSON Yapıştır)",
            icon = PlayIcons.Download,
            onClick = {
                importJsonInput = ""
                showImportDialog = true
            },
        )

        PlayCardRow(
            title = "Geçmiş ve Favorileri Temizle",
            icon = PlayIcons.Trash,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { showClearDialog = true },
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Profil Adını Değiştir") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Yeni İsim") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        scope.launch { container.localProfile.setName(renameInput.trim()) }
                        showRenameDialog = false
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("İptal") }
            },
        )
    }

    // PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("PIN Kodu Belirle") },
            text = {
                Column {
                    Text("Boş bırakıp kaydetmek mevcut PIN kodunu kaldırır.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4 Haneli PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        container.localProfile.setPin(pinInput.takeIf { it.isNotBlank() })
                    }
                    showPinDialog = false
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("İptal") }
            },
        )
    }

    // Avatar Dialog
    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("Avatar Seçin") },
            text = {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(profileAvatarOptions) { av ->
                        ProfileAvatar(
                            avatar = av,
                            selected = av == activeProfile?.avatar,
                            size = 56,
                            modifier = Modifier.focusRing(androidx.compose.foundation.shape.CircleShape).clickable {
                                scope.launch { container.localProfile.setAvatar(av) }
                                showAvatarDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) { Text("Kapat") }
            },
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Yedekten Geri Yükle") },
            text = {
                OutlinedTextField(
                    value = importJsonInput,
                    onValueChange = { importJsonInput = it },
                    label = { Text("JSON Metni") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (importJsonInput.isNotBlank()) {
                        scope.launch {
                            val res = container.localSyncBackup.mergeJson(importJsonInput.trim())
                            if (res.isSuccess) {
                                Toast.makeText(context, "Kayıtlar başarıyla içe aktarıldı!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Hata: Geçersiz JSON verisi.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showImportDialog = false
                    }
                }) { Text("İçe Aktar") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("İptal") }
            },
        )
    }

    // Clear Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Geçmiş ve Favorileri Temizle") },
            text = { Text("Bu profilin tüm izleme geçmişi ve favori listesi silinecektir. Devam etmek istiyor musunuz?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { container.localSyncBackup.clearCurrent() }
                        showClearDialog = false
                        Toast.makeText(context, "Temizlendi.", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Evet, Temizle", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("İptal") }
            },
        )
    }
}
