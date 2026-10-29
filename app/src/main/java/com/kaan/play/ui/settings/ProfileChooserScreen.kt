package com.kaan.play.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.kaan.play.ui.nav.PlayIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.Image
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.sync.CloudRegistrationResult
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.DismissImeOnBack
import com.kaan.play.ui.tv.dismissImeOnDoneOrBack
import kotlinx.coroutines.launch

/** Nuvio benzeri profil seçici; yönetim eylemleri de aynı ekranda kalır. */
private enum class ProfileChooserTab { Profiles, Account }

@Composable
fun ProfileChooserScreen(
    profiles: List<LocalProfileSummary>,
    onSelect: (LocalProfileSummary) -> Unit,
    isAdmin: Boolean,
    managingProfiles: Boolean,
    onToggleManage: () -> Unit,
    onCreateProfile: () -> Unit,
    onToggleHidden: (LocalProfileSummary) -> Unit,
    onDeleteProfile: (LocalProfileSummary) -> Unit,
    onEditProfile: (LocalProfileSummary, String, String, Boolean, String) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    var selectedTab by remember { mutableStateOf(ProfileChooserTab.Profiles) }
    var showHidden by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalProfileSummary?>(null) }
    var renameDraft by remember(renameTarget?.id) { mutableStateOf(renameTarget?.name.orEmpty()) }
    var editAvatar by remember(renameTarget?.id) { mutableStateOf(renameTarget?.avatar ?: "space_astro") }
    var editChild by remember(renameTarget?.id) { mutableStateOf(renameTarget?.isChild ?: false) }
    var editPin by remember(renameTarget?.id) { mutableStateOf("") }
    var textFieldFocused by remember { mutableStateOf(false) }
    val displayed = if (managingProfiles || showHidden) profiles else profiles.filterNot { it.isHidden }
    val focus = rememberFocusMemory(
        forceOnTouch = true,
        resetKey = "${managingProfiles}:${selectedTab}:${displayed.firstOrNull()?.id}:${displayed.isEmpty()}",
    )
    val editFocus = rememberFocusMemory(enabled = renameTarget != null, forceOnTouch = true, resetKey = renameTarget?.id)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val closeFocusRequester = remember { FocusRequester() }
    val tabProfilesFocusRequester = remember { FocusRequester() }
    val tabAccountFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    DismissImeOnBack(textFieldFocused)
    if (onClose != null) {
        BackHandler { onClose() }
    }
    val background = Brush.verticalGradient(colors = listOf(Color(0xFF3A1027), Color(0xFF190B14), Color(0xFF050505)))

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .focusRequester(closeFocusRequester)
                    .focusProperties {
                        down = if (selectedTab == ProfileChooserTab.Account) tabAccountFocusRequester else tabProfilesFocusRequester
                    }
                    .focusRing(CircleShape),
            ) {
                Icon(PlayIcons.Close, contentDescription = "Kapat", tint = Color.White)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 16.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            Image(painter = painterResource(R.drawable.logo), contentDescription = "Play", contentScale = ContentScale.Fit, modifier = Modifier.size(width = if (compact) 112.dp else 150.dp, height = if (compact) 42.dp else 58.dp))
            Text(
                text = when {
                    managingProfiles -> "Profilleri yönet"
                    selectedTab == ProfileChooserTab.Profiles -> "Kim izliyor?"
                    else -> "Bulut Hesabı"
                },
                style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall).copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            if (!managingProfiles) {
                val tabs = listOf("Profiller", "Bulut Hesabı")
                val currentTabIndex = if (selectedTab == ProfileChooserTab.Profiles) 0 else 1
                TabRow(
                    selectedTabIndex = currentTabIndex,
                    containerColor = Color(0xFF101F32),
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[currentTabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp,
                        )
                    },
                    divider = {
                        HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)
                    },
                    modifier = Modifier
                        .fillMaxWidth(if (compact) 1f else .72f)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = currentTabIndex == index
                        val requester = if (index == 0) tabProfilesFocusRequester else tabAccountFocusRequester
                        Tab(
                            selected = isSelected,
                            onClick = {
                                selectedTab = if (index == 0) ProfileChooserTab.Profiles else ProfileChooserTab.Account
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                )
                            },
                            modifier = Modifier
                                .focusRequester(requester)
                                .focusProperties {
                                    if (onClose != null) up = closeFocusRequester
                                    if (index == 1) {
                                        down = emailFocusRequester
                                    }
                                }
                                .focusMemory(focus, "tab-$index")
                                .focusRing(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            if (!managingProfiles && selectedTab == ProfileChooserTab.Profiles && profiles.any { it.isAdmin }) {
                Text("Admin profili: ${profiles.first { it.isAdmin }.name}", color = Color.White.copy(alpha = .64f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!managingProfiles && selectedTab == ProfileChooserTab.Account) {
                AccountAccessPanel(
                    tabAccountFocusRequester = tabAccountFocusRequester,
                    emailFocusRequester = emailFocusRequester,
                )
            } else {
                if (managingProfiles) {
                    if (profiles.isEmpty()) Text("Henüz profil yok", color = Color.White.copy(alpha = .75f))
                    profiles.forEach { profile ->
                        Surface(modifier = Modifier.fillMaxWidth().focusRing(RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = Color(0xFF101F32)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProfileAvatar(profile.avatar, size = 48)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(buildString { if (profile.isHidden) append("Gizli · "); if (profile.pinHash.isNullOrBlank()) append("PIN yok") else append("PIN korumalı") }, color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { renameTarget = profile; renameDraft = profile.name; editAvatar = profile.avatar; editChild = profile.isChild; editPin = "" }, modifier = Modifier.focusMemory(focus, "profile-${profile.id}", isDefault = profile == profiles.firstOrNull()).focusRing(RoundedCornerShape(8.dp))) { Text("Düzenle") }
                                TextButton(onClick = { onToggleHidden(profile) }, enabled = !profile.isAdmin && (profile.isHidden || !profile.pinHash.isNullOrBlank()), modifier = Modifier.focusRing(RoundedCornerShape(8.dp))) { Text(if (profile.isHidden) "Göster" else if (profile.pinHash.isNullOrBlank()) "PIN gerekli" else "Gizle") }
                                if (profiles.size > 1 && !profile.isAdmin) TextButton(onClick = { onDeleteProfile(profile) }, modifier = Modifier.focusRing(RoundedCornerShape(8.dp))) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                } else {
                    if (displayed.isEmpty()) Text(if (showHidden) "Gösterilecek profil yok" else "Henüz profil oluşturulmamış", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.titleMedium)
                    displayed.chunked(2).forEach { rowProfiles ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp), verticalAlignment = Alignment.Top) {
                            rowProfiles.forEach { profile -> ProfileChoice(profile, onClick = { onSelect(profile) }, avatarSize = if (compact) 96 else 142, compact = compact, modifier = Modifier.weight(1f).focusMemory(focus, "profile-${profile.id}", isDefault = profile == displayed.firstOrNull()).focusRing(CircleShape)) }
                            if (rowProfiles.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (isAdmin) OutlinedButton(onClick = onToggleManage, modifier = Modifier.fillMaxWidth(if (compact) 1f else .72f).height(50.dp).focusMemory(focus, "manage").focusRing(RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .45f))) {
                    Text(if (managingProfiles) "Profilleri seç" else "Profilleri yönet", color = Color.White.copy(alpha = .9f))
                }
                if (managingProfiles && isAdmin) {
                    OutlinedButton(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth(if (compact) 1f else .72f).height(48.dp).focusMemory(focus, "create", isDefault = profiles.isEmpty()).focusRing(RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .28f))) { Text("Yeni profil oluştur", color = Color.White) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profiles.any { it.isHidden }) OutlinedButton(onClick = { showHidden = !showHidden }, modifier = Modifier.focusMemory(focus, "hidden", isDefault = displayed.isEmpty() && !isAdmin).focusRing(RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .28f))) { Text(if (showHidden) "Gizlileri sakla" else "Gizli profilleri göster", color = Color.White.copy(alpha = .8f)) }
                        if (profiles.isEmpty() || isAdmin) OutlinedButton(onClick = onCreateProfile, modifier = Modifier.focusMemory(focus, "create", isDefault = displayed.isEmpty()).focusRing(RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .28f))) { Text("Profil oluştur", color = Color.White.copy(alpha = .8f)) }
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        AlertDialog(
                            onDismissRequest = { keyboard?.hide(); focusManager.clearFocus(force = true); renameTarget = null },
            title = { Text("Profili düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it.take(40) }, modifier = Modifier.focusMemory(editFocus, "rename", isDefault = true).focusRing(RoundedCornerShape(12.dp)).dpadLeavesTextField().onFocusChanged { textFieldFocused = it.isFocused }.dismissImeOnDoneOrBack(), label = { Text("Profil adı") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                    Text("Avatar", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        itemsIndexed(profileAvatarOptions, key = { _, item -> item }) { index, avatarItem ->
                            ProfileAvatar(
                                avatar = avatarItem,
                                selected = editAvatar == avatarItem,
                                size = 52,
                                modifier = Modifier
                                    .clickable { editAvatar = avatarItem }
                                    .focusMemory(editFocus, "edit-avatar-$index")
                                    .focusRing(CircleShape)
                                    .clip(CircleShape),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = editChild, onCheckedChange = { editChild = it }, modifier = Modifier.focusMemory(editFocus, "edit-child").focusRing(RoundedCornerShape(8.dp))); Text("Çocuk profili") }
                    OutlinedTextField(value = editPin, onValueChange = { editPin = it.filter(Char::isDigit).take(6) }, modifier = Modifier.focusMemory(editFocus, "edit-pin").focusRing(RoundedCornerShape(12.dp)).dpadLeavesTextField().onFocusChanged { textFieldFocused = it.isFocused }.dismissImeOnDoneOrBack(), label = { Text("Yeni PIN (boş bırak: değiştirme)") }, supportingText = { Text("En az 4 rakam") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                }
            },
            confirmButton = { TextButton(onClick = { keyboard?.hide(); focusManager.clearFocus(force = true); onEditProfile(target, renameDraft.trim(), editAvatar, editChild, editPin); renameTarget = null }, enabled = renameDraft.isNotBlank() && (editPin.isEmpty() || editPin.length >= 4), modifier = Modifier.focusMemory(editFocus, "edit-save").focusRing(RoundedCornerShape(8.dp))) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { keyboard?.hide(); focusManager.clearFocus(force = true); renameTarget = null }, modifier = Modifier.focusMemory(editFocus, "edit-cancel").focusRing(RoundedCornerShape(8.dp))) { Text("İptal") } },
        )
    }
}

@Composable
private fun AccountAccessPanel(
    tabAccountFocusRequester: FocusRequester,
    emailFocusRequester: FocusRequester,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val account by container.cloudSync.account.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    val switchModeFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DismissImeOnBack(emailFocused || passwordFocused)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD101F32)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (account != null) {
                Text("Giriş yapıldı", style = MaterialTheme.typography.titleLarge, color = Color(0xFF37D5E7))
                Text(account!!.email, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Profil, izleme geçmişi ve favoriler internet üzerinden otomatik eşitlenir.", color = Color.White.copy(alpha = .78f))
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Hesaptan çıkılıyor…"
                            try {
                                val synced = container.cloudSync.signOut()
                                status = if (synced) "Çıkış yapıldı. Bu cihazda hesapsız devam edebilirsiniz."
                                else "Çıkış yapıldı; son değişiklikler buluta aktarılamamış olabilir."
                            } catch (e: Exception) {
                                status = e.message ?: "Çıkış yapılamadı."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester)
                        .focusProperties {
                            up = tabAccountFocusRequester
                        }
                        .focusRing(RoundedCornerShape(8.dp)),
                ) { Text("Hesaptan çıkış yap") }
            } else {
                Text(if (createMode) "Yeni hesap oluştur" else "Hesabına giriş yap", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    if (createMode) "E-posta ve en az 8 karakterli bir şifreyle hesabını oluştur."
                    else "Hesabına giriş yaptığında profillerin ve izleme verilerin otomatik eşitlenir.",
                    color = Color.White.copy(alpha = .76f),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; status = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester)
                        .focusProperties {
                            up = tabAccountFocusRequester
                            down = passwordFocusRequester
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        passwordFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        tabAccountFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { emailFocused = it.isFocused }
                        .dismissImeOnDoneOrBack(),
                    label = { Text("E-posta") },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; status = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .focusProperties {
                            up = emailFocusRequester
                            down = submitFocusRequester
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        submitFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        emailFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { passwordFocused = it.isFocused }
                        .dismissImeOnDoneOrBack(),
                    label = { Text(if (createMode) "Şifre (en az 8 karakter)" else "Şifre") },
                    singleLine = true,
                    maxLines = 1,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        submitFocusRequester.requestFocus()
                    }),
                )
                Button(
                    onClick = {
                        keyboard?.hide()
                        val trimmedEmail = email.trim()
                        if (trimmedEmail.isBlank() || !trimmedEmail.contains('@')) {
                            status = "Geçerli bir e-posta adresi girin."
                            emailFocusRequester.requestFocus()
                            return@Button
                        }
                        if (createMode && password.length < 8) {
                            status = "Şifre en az 8 karakter olmalıdır."
                            passwordFocusRequester.requestFocus()
                            return@Button
                        }
                        if (!createMode && password.isBlank()) {
                            status = "Lütfen şifrenizi girin."
                            passwordFocusRequester.requestFocus()
                            return@Button
                        }
                        focusManager.clearFocus(force = true)
                        scope.launch {
                            busy = true
                            status = if (createMode) "Hesap oluşturuluyor…" else "Giriş yapılıyor…"
                            try {
                                if (createMode) {
                                    when (container.cloudSync.createAccount(trimmedEmail, password)) {
                                        CloudRegistrationResult.SIGNED_IN -> {
                                            container.startCloudAutoSync()
                                            val count = container.cloudSync.synchronize()
                                            password = ""
                                            status = "Hesap oluşturuldu ve giriş yapıldı. $count profil için otomatik eşitleme açık."
                                        }
                                        CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED -> {
                                            password = ""
                                            createMode = false
                                            status = "Hesap oluşturuldu. E-postandaki doğrulama bağlantısını aç, ardından giriş yap."
                                        }
                                    }
                                } else {
                                    container.cloudSync.signIn(trimmedEmail, password)
                                    container.startCloudAutoSync()
                                    val count = container.cloudSync.synchronize()
                                    password = ""
                                    status = "Giriş başarılı. $count profil için otomatik eşitleme sürüyor."
                                }
                            } catch (e: Exception) {
                                status = e.message ?: if (createMode) "Hesap oluşturulamadı. Bilgileri kontrol et." else "Giriş yapılamadı. Bilgileri kontrol et."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(submitFocusRequester)
                        .focusProperties {
                            up = passwordFocusRequester
                            down = switchModeFocusRequester
                        }
                        .focusRing(RoundedCornerShape(20.dp)),
                ) {
                    Text(if (busy) "Lütfen bekle…" else if (createMode) "Hesap oluştur" else "Giriş yap")
                }
                TextButton(
                    onClick = {
                        createMode = !createMode
                        status = null
                        emailFocusRequester.requestFocus()
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(switchModeFocusRequester)
                        .focusProperties {
                            up = submitFocusRequester
                        }
                        .focusRing(RoundedCornerShape(8.dp)),
                ) {
                    Text(if (createMode) "Giriş ekranına dön" else "Yeni hesap oluştur")
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ProfileChoice(profile: LocalProfileSummary, onClick: () -> Unit, avatarSize: Int, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(avatarSize.dp).border(3.dp, Color.White.copy(alpha = .28f), CircleShape), shape = CircleShape, color = Color(0xFF111827), shadowElevation = 10.dp) {
            Box(contentAlignment = Alignment.Center) {
                ProfileAvatar(profile.avatar, size = avatarSize - 20)
                if (!profile.pinHash.isNullOrBlank()) Surface(modifier = Modifier.align(Alignment.BottomEnd).size(if (compact) 22.dp else 30.dp), shape = CircleShape, color = Color(0xFF241525), border = BorderStroke(1.dp, Color.White.copy(alpha = .55f))) { Box(contentAlignment = Alignment.Center) { Text("•", color = Color.White) } }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
        Text(profile.name, style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        if (profile.isAdmin) Text("Admin", style = MaterialTheme.typography.labelMedium, color = Color(0xFF37D5E7))
        if (profile.isChild) Text("Çocuk", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .62f))
    }
}
