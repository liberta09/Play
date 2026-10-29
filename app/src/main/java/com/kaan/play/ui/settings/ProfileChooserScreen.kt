package com.kaan.play.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.sync.CloudRegistrationResult
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.theme.profileBackdropBrush
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

enum class ProfileChooserTab {
    Profiles,
    Account,
}

@Composable
fun ProfileChooserScreen(
    profiles: List<LocalProfileSummary>,
    onSelect: (LocalProfileSummary) -> Unit,
    isAdmin: Boolean = true,
    managingProfiles: Boolean = false,
    onToggleManage: () -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onToggleHidden: (LocalProfileSummary) -> Unit = {},
    onDeleteProfile: (LocalProfileSummary) -> Unit = {},
    onEditProfile: (LocalProfileSummary, String, String, Boolean, String?) -> Unit = { _, _, _, _, _ -> },
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(ProfileChooserTab.Profiles) }
    var pinDialogTarget by remember { mutableStateOf<LocalProfileSummary?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var editDialogTarget by remember { mutableStateOf<LocalProfileSummary?>(null) }
    // Kumanda: ilk profil başlangıç odağı; sekme/profil listesi değişince odak yeniden verilir.
    val focus = rememberFocusMemory(forceOnTouch = true, resetKey = "${selectedTab}:${profiles.firstOrNull()?.id}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(profileBackdropBrush())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == ProfileChooserTab.Profiles,
                onClick = { selectedTab = ProfileChooserTab.Profiles },
                modifier = Modifier.focusMemory(focus, "tab-profiles").focusRing(RoundedCornerShape(8.dp)),
                text = { Text("Profiller") },
            )
            Tab(
                selected = selectedTab == ProfileChooserTab.Account,
                onClick = { selectedTab = ProfileChooserTab.Account },
                modifier = Modifier.focusMemory(focus, "tab-account").focusRing(RoundedCornerShape(8.dp)),
                text = { Text("Bulut Hesabı") },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (selectedTab) {
            ProfileChooserTab.Profiles -> {
                Text(
                    text = "Kimin İzlediğini Seçin",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = "İzleme geçmişi, favoriler ve tercihler seçilen profile göre tutulur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onToggleManage) {
                        Text(if (managingProfiles) "Bitti" else "Profilleri Yönet")
                    }
                    Button(onClick = onCreateProfile) {
                        Icon(PlayIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeni Profil")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(profiles) { profile ->
                        ProfileChoice(
                            profile = profile,
                            modifier = Modifier.focusMemory(focus, "profile-${profile.id}", isDefault = profile == profiles.first()),
                            onClick = {
                                if (managingProfiles) {
                                    editDialogTarget = profile
                                } else if (profile.hasPin) {
                                    pinDialogTarget = profile
                                    pinInput = ""
                                    pinError = false
                                } else {
                                    onSelect(profile)
                                }
                            },
                        )
                    }
                    // Wi-Fi senkronizasyonu: kapalı başlar, açılınca sunucu başlar (açılışta hiçbir şey dinlenmez).
                    item(key = "wifi-sync", span = { GridItemSpan(maxLineSpan) }) {
                        WifiSyncCard()
                    }
                }
            }

            ProfileChooserTab.Account -> {
                // Eklenti onay listesi uzayabilir: sekme içeriği kayar.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    AccountAccessPanel()
                }
            }
        }
    }

    // PIN Dialog
    pinDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pinDialogTarget = null },
            title = { Text("PIN Kodu Girin") },
            text = {
                Column {
                    Text("${target.name} profilinin kilidini açmak için 4 haneli PIN kodunu girin:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = false
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError,
                        supportingText = { if (pinError) Text("Hatalı PIN kodu!", color = MaterialTheme.colorScheme.error) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valid = container.localProfile.verifyPin(target, pinInput)
                        if (valid) {
                            pinDialogTarget = null
                            onSelect(target)
                        } else {
                            pinError = true
                        }
                    },
                    enabled = pinInput.length == 4,
                ) {
                    Text("Giriş")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinDialogTarget = null }) {
                    Text("İptal")
                }
            },
        )
    }

    // Edit Profile Dialog
    editDialogTarget?.let { target ->
        var editName by remember(target) { mutableStateOf(target.name) }
        var editAvatar by remember(target) { mutableStateOf(target.avatar) }
        var editChild by remember(target) { mutableStateOf(target.isChild) }
        var editPin by remember(target) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editDialogTarget = null },
            title = { Text("Profili Düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Profil Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Çocuk Profili")
                        Switch(checked = editChild, onCheckedChange = { editChild = it }, modifier = Modifier.focusRing(CircleShape))
                    }

                    OutlinedTextField(
                        value = editPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) editPin = it },
                        label = { Text("Yeni PIN (Boş = Değişmez/Kalkar)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditProfile(target, editName, editAvatar, editChild, editPin.takeIf { it.isNotBlank() })
                        editDialogTarget = null
                    },
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(target)
                        editDialogTarget = null
                    },
                ) {
                    Text("Profili Sil", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
fun ProfileChoice(
    profile: LocalProfileSummary,
    onClick: () -> Unit,
    avatarSize: Int = 80,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth().focusRing(RoundedCornerShape(16.dp), scale = 1.04f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    avatar = profile.avatar,
                    size = avatarSize,
                )
                if (profile.hasPin) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            PlayIcons.Lock,
                            contentDescription = "PIN Korumalı",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (profile.isChild) {
                Text(
                    text = "Çocuk",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun AccountAccessPanel() {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val account by container.cloudSync.account.collectAsStateWithLifecycle()
    val pendingSources by container.cloudSync.pendingSources.collectAsStateWithLifecycle()
    val installingSources by container.cloudSync.installingSources.collectAsStateWithLifecycle()
    var installFailed by remember { mutableStateOf(0) }

    var email by remember { mutableStateOf(com.kaan.play.MainActivity.testSyncEmail ?: "") }
    var password by remember { mutableStateOf(com.kaan.play.MainActivity.testSyncPassword ?: "") }
    var isRegister by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (account != null) {
                Text(
                    text = "Bağlı Bulut Hesabı",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = account!!.email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.cloud_sync_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                statusMessage?.let {
                    Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                statusMessage = "Eşitleniyor..."
                                isError = false
                                val res = container.cloudSync.synchronize()
                                loading = false
                                if (res.isSuccess) {
                                    statusMessage = "Senkronizasyon başarılı!"
                                } else {
                                    isError = true
                                    statusMessage = res.exceptionOrNull()?.message ?: "Hata oluştu."
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (loading) "Eşitleniyor..." else "Şimdi Senkronize Et")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                container.cloudSync.signOut()
                                statusMessage = "Çıkış yapıldı."
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Çıkış Yap")
                    }
                }

                // Bulutta olup bu cihazda olmayan kaynaklar: Wi-Fi kartındaki aynı onay bileşeni.
                if (pendingSources.isNotEmpty()) {
                    PendingSourcesList(
                        pending = pendingSources,
                        installing = installingSources,
                        onConfirm = { items -> scope.launch { installFailed = container.cloudSync.confirmSources(items) } },
                        onSkip = container.cloudSync::dismissPendingSources,
                    )
                }
                if (installFailed > 0) {
                    Text(
                        pluralStringResource(R.plurals.wifi_sync_install_failed, installFailed, installFailed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = if (isRegister) "Hesap Oluştur" else "E-posta ile Giriş Yap",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Farklı cihazlarınız arasında izleme geçmişinizi ve profillerinizi eşitleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; statusMessage = null },
                    label = { Text("E-posta Adresi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; statusMessage = null },
                    label = { Text("Şifre") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                statusMessage?.let {
                    Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }

                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            scope.launch {
                                loading = true
                                isError = false
                                statusMessage = null
                                if (isRegister) {
                                    val res = container.cloudSync.createAccount(email, password)
                                    loading = false
                                    if (res.isSuccess) {
                                        when (res.getOrNull()) {
                                            CloudRegistrationResult.SIGNED_IN -> statusMessage = "Hesap açıldı ve giriş yapıldı!"
                                            CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED -> statusMessage = "Kayıt başarılı! Lütfen e-posta kutunuzdan onaylayın."
                                            null -> Unit
                                        }
                                    } else {
                                        isError = true
                                        statusMessage = res.exceptionOrNull()?.let { it.message?.takeIf { m -> m.isNotBlank() } ?: it.javaClass.simpleName } ?: "Kayıt başarısız."
                                    }
                                } else {
                                    val res = container.cloudSync.signIn(email, password)
                                    loading = false
                                    if (res.isSuccess) {
                                        statusMessage = "Giriş başarılı!"
                                    } else {
                                        isError = true
                                        statusMessage = res.exceptionOrNull()?.let { it.message?.takeIf { m -> m.isNotBlank() } ?: it.javaClass.simpleName } ?: "Giriş başarısız."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !loading && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isRegister) "Kayıt Ol" else "Giriş Yap")
                    }
                }

                TextButton(
                    onClick = {
                        isRegister = !isRegister
                        statusMessage = null
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (isRegister) "Zaten hesabınız var mı? Giriş Yapın" else "Hesabınız yok mu? Yeni Hesap Oluşturun")
                }
            }
        }
    }
}
