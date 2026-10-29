package com.kaan.play.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

/** Profil seçimi ve profil ayarları. Wi-Fi senkronu ayrı [SyncScreen] ekranındadır. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileName by container.localProfile.name.collectAsStateWithLifecycle(initialValue = "Play Kullanıcısı")
    val profileId by container.localProfile.id.collectAsStateWithLifecycle(initialValue = "")
    val profiles by container.localProfile.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    var draft by remember(profileName) { mutableStateOf(profileName) }
    var newProfileDraft by remember { mutableStateOf("") }
    var newAvatar by remember { mutableStateOf("space_astro") }
    var newChild by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var pinDraft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val active = profiles.firstOrNull { it.id == profileId }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = container.localSyncBackup.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Dosya yazılamadı")
            }.onSuccess { status = "Profil yedeği dışa aktarıldı"; Toast.makeText(context, status, Toast.LENGTH_SHORT).show() }
                .onFailure { status = "Dışa aktarma başarısız: ${it.message.orEmpty()}" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Dosya okunamadı")
                container.localSyncBackup.importJson(raw).getOrThrow()
            }.onSuccess { count -> status = "$count kayıt içe aktarıldı"; Toast.makeText(context, status, Toast.LENGTH_SHORT).show() }
                .onFailure { status = "İçe aktarma başarısız: ${it.message.orEmpty()}" }
        }
    }

    LaunchedEffect(Unit) { container.localProfile.ensureId() }
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Profil seçimi", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("İzleme geçmişi, favoriler ve profil tercihleri seçtiğiniz profile göre tutulur.", style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aktif profil", style = MaterialTheme.typography.titleMedium)
                    Text(active?.name ?: profileName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Profil adı") }, singleLine = true)
                    Button(onClick = { scope.launch { container.localProfile.setName(draft); status = "Profil kaydedildi" } }) { Text("Profili kaydet") }
                    Text("Avatar")
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        profileAvatarOptions.forEach { avatar ->
                            ProfileAvatar(
                                avatar,
                                selected = active?.avatar == avatar,
                                size = 52,
                                modifier = Modifier.clickable { scope.launch { container.localProfile.setAvatar(avatar) } },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = active?.isChild == true, onCheckedChange = { value -> scope.launch { container.localProfile.setChildProfile(value) } })
                        Text("Çocuk profili")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = pinDraft, onValueChange = { pinDraft = it.filter(Char::isDigit).take(6) }, modifier = Modifier.weight(1f), label = { Text("PIN (en az 4 hane)") }, supportingText = { Text("Boş kaydetmek mevcut PIN’i kaldırır") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        Button(onClick = { scope.launch { container.localProfile.setPin(pinDraft); pinDraft = ""; status = "PIN kaydedildi" } }, enabled = pinDraft.isEmpty() || pinDraft.length >= 4) { Text("PIN kaydet") }
                    }
                }
            }
            if (active?.isAdmin == true) Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profiller arasında geçiş", style = MaterialTheme.typography.titleMedium)
                    profiles.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (item.id == profileId) "✓ ${item.name}" else item.name)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { scope.launch { container.localProfile.setProfileHidden(item.id, !item.isHidden) } }, enabled = !item.isAdmin && (item.isHidden || !item.pinHash.isNullOrBlank())) { Text(if (item.isHidden) "Göster" else if (item.pinHash.isNullOrBlank()) "PIN gerekli" else "Gizle") }
                                if (item.id != profileId) TextButton(onClick = {
                                    scope.launch {
                                        val current = container.localSyncBackup.exportJson()
                                        if (profileId.isNotBlank()) container.localProfile.saveBackup(profileId, current)
                                        container.localProfile.setActiveProfile(item)
                                        container.localProfile.backup(item.id)?.let { container.localSyncBackup.importJson(it) }
                                        status = "${item.name} profiline geçildi"
                                    }
                                }) { Text("Seç") }
                                if (profiles.size > 1 && item.id != profileId && !item.isAdmin) TextButton(onClick = {
                                    scope.launch {
                                        val user = container.localProfile.ensureUserId()
                                        container.database.watchHistoryDao().clear(user, item.id)
                                        container.database.favoriteDao().clear(user, item.id)
                                        container.localProfile.deleteProfile(item.id)
                                        status = "Profil ve profile ait geçmiş/favoriler silindi"
                                    }
                                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newProfileDraft, onValueChange = { newProfileDraft = it }, modifier = Modifier.weight(1f), label = { Text("Yeni profil") }, singleLine = true)
                        Button(onClick = {
                            scope.launch {
                                val current = container.localSyncBackup.exportJson()
                                if (profileId.isNotBlank()) container.localProfile.saveBackup(profileId, current)
                                val created = container.localProfile.createProfile(newProfileDraft)
                                container.localProfile.setActiveProfile(created)
                                container.localProfile.setAvatar(newAvatar)
                                container.localProfile.setChildProfile(newChild)
                                container.localProfile.setPin(newPin.takeIf { it.length >= 4 })
                                container.localSyncBackup.clearCurrent()
                                newProfileDraft = ""
                                newPin = ""
                                newChild = false
                                status = "${created.name} profili oluşturuldu"
                            }
                        }, enabled = newProfileDraft.isNotBlank() && (newPin.isEmpty() || newPin.length >= 4)) { Text("Ekle") }
                    }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        profileAvatarOptions.forEach { avatar ->
                            ProfileAvatar(avatar, selected = newAvatar == avatar, size = 46, modifier = Modifier.clickable { newAvatar = avatar })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newChild, onCheckedChange = { newChild = it })
                        Text("Çocuk profili")
                    }
                    OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(6) }, modifier = Modifier.fillMaxWidth(), label = { Text("Çocuk/PIN kilidi (isteğe bağlı)") }, supportingText = { Text("PIN en az 4 rakam olmalı") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
            }
            Text("Yedek dosyası", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { export.launch("play-profile-backup.json") }, modifier = Modifier.fillMaxWidth()) { Text("Geçmişi dışa aktar") }
            Button(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("Yedekten içe aktar") }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
