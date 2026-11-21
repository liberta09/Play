package com.kaan.play.tv

import com.kaan.play.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import com.kaan.play.data.sync.LocalProfileSummary
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kaan.play.data.sync.SupabaseCloudSync
import com.kaan.play.data.sync.CloudRegistrationResult
import com.kaan.play.ui.settings.ProfileAvatar
import com.kaan.play.ui.settings.profileAvatarOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sayfa içi alt sayfalar; Geri her zaman profil listesine döner. */
private sealed interface ProfilePage {
    data object Main : ProfilePage
    data object Choose : ProfilePage
    /** PIN'li profil düzenlenmeden önce PIN sorulur; yoksa kumandayı alan herkes PIN'i kaldırabilirdi. */
    data class Unlock(val profile: LocalProfileSummary) : ProfilePage
    /** null: yeni profil. */
    data class Edit(val profile: LocalProfileSummary?) : ProfilePage
}

/**
 * Ayarlar → Profiller ve hesap (TV'ye özgü; Play'in Profil Yönetimi, profil oluşturma/düzenleme, "Son profili hatırla"
 * ve Bulut hesabı panelinin karşılığı). Veri Play'in yerel profil deposundan gelir; Play'in telefon ekranları gömülmez.
 */
@Composable
internal fun TvProfileSettings() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PlayApp).container }
    val repo = container.localProfile
    val scope = rememberCoroutineScope()
    val profiles by repo.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by repo.activeProfile.collectAsStateWithLifecycle(initialValue = null)
    val rememberLast by container.settings.rememberLastProfile.collectAsStateWithLifecycle(initialValue = true)
    var page by remember { mutableStateOf<ProfilePage>(ProfilePage.Main) }
    BackHandler(enabled = page != ProfilePage.Main) { page = ProfilePage.Main }

    when (val p = page) {
        ProfilePage.Choose -> {
            // Açılıştaki "Kim izliyor?" ekranının aynısı: PIN'li profil kendi PIN'iyle açılır. Gizli profiller burada
            // görünür; açılış seçicisinde görünmediklerinden onlara buradan geçilir.
            TvProfileChooser(profiles, active?.id, onChosen = { page = ProfilePage.Main })
            return
        }
        is ProfilePage.Unlock -> {
            PinPad(
                avatar = p.profile.avatar,
                title = stringResource(R.string.tv_profile_enter_pin, p.profile.name),
                onComplete = { pin ->
                    // PIN özeti (PBKDF2) ağır: ana iş parçacığı takılmasın.
                    val ok = withContext(Dispatchers.Default) { repo.verifyPin(p.profile, pin) }
                    if (ok) page = ProfilePage.Edit(p.profile)
                    ok
                },
            )
            return
        }
        is ProfilePage.Edit -> {
            ProfileEditor(target = p.profile, isActive = p.profile != null && p.profile.id == active?.id, onDone = { page = ProfilePage.Main })
            return
        }
        ProfilePage.Main -> Unit
    }

    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    SettingsPageFrame(stringResource(R.string.tv_settings_profile_title)) {
        item(key = "active") {
            Column {
                SectionTitle(stringResource(R.string.tv_profile_active))
                Spacer(Modifier.height(8.dp))
                ListItem(
                    selected = false,
                    onClick = { page = ProfilePage.Choose },
                    leadingContent = { active?.let { ProfileAvatar(avatar = it.avatar, size = 40) } },
                    headlineContent = { Text(active?.name ?: stringResource(R.string.tv_profile_none)) },
                    supportingContent = { Text(stringResource(R.string.tv_profile_switch_hint), color = TextDim) },
                    trailingContent = { Text("›", color = TextDim, fontSize = TvText.Row) },
                    modifier = Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused },
                )
            }
        }
        // Play'deki ayarın aynısı; TV açılışı (TvApp) bu değere göre "Kim izliyor?" sorar ya da sormaz.
        item(key = "remember-last") {
            ListItem(
                selected = false,
                onClick = { scope.launch { container.settings.setRememberLastProfile(!rememberLast) } },
                headlineContent = { Text(stringResource(R.string.tv_profile_remember_last)) },
                supportingContent = {
                    Text(
                        if (rememberLast) stringResource(R.string.tv_profile_remember_last_on) else stringResource(R.string.tv_profile_remember_last_off),
                        color = TextDim,
                    )
                },
                trailingContent = { Text(if (rememberLast) stringResource(R.string.tv_on) else stringResource(R.string.tv_off), color = if (rememberLast) Accent else TextDim) },
            )
        }
        item(key = "profiles-title") {
            Column {
                Spacer(Modifier.height(10.dp))
                SectionTitle(stringResource(R.string.tv_profile_profiles))
            }
        }
        items(profiles, key = { it.id }) { prof ->
            ListItem(
                selected = false,
                onClick = { page = if (prof.hasPin) ProfilePage.Unlock(prof) else ProfilePage.Edit(prof) },
                leadingContent = { ProfileAvatar(avatar = prof.avatar, size = 40) },
                headlineContent = { Text(prof.name) },
                supportingContent = { Text(profileTags(prof, prof.id == active?.id), color = TextDim) },
                trailingContent = { Text(stringResource(R.string.tv_profile_edit_arrow), color = TextDim) },
            )
        }
        item(key = "new") {
            ListItem(
                selected = false,
                onClick = { page = ProfilePage.Edit(null) },
                headlineContent = { Text(stringResource(R.string.tv_profile_new_item)) },
                supportingContent = { Text(stringResource(R.string.tv_profile_new_summary), color = TextDim) },
            )
        }
        item(key = "cloud") { CloudAccountSection(container.cloudSync) }
    }
    // Alt sayfadan dönünce odak boşta kalmasın.
    InitialFocus(first) { firstFocused }
}

@Composable
private fun profileTags(p: LocalProfileSummary, active: Boolean): String = listOfNotNull(
    stringResource(R.string.tv_profile_tag_active).takeIf { active },
    stringResource(R.string.tv_profile_child).takeIf { p.isChild },
    stringResource(R.string.tv_profile_pin_protected).takeIf { p.hasPin },
    stringResource(R.string.tv_profile_tag_hidden).takeIf { p.isHidden },
).joinToString(" · ").ifEmpty { stringResource(R.string.tv_profile_tag_standard) }

private enum class EditStep { Form, Pin, Delete }

/**
 * Profil oluşturma/düzenleme: ad (telefon klavyesi ya da TV klavyesi), avatar, çocuk profili, PIN, gizli. Kaydet'e kadar
 * hiçbir şey yazılmaz. PIN kuralı Play'in updateProfile'ı ile aynı: null değişmez, "" kaldırılır, 4 hane yeni PIN.
 */
@Composable
private fun ProfileEditor(target: LocalProfileSummary?, isActive: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as PlayApp).container.localProfile }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(target?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(target?.avatar ?: profileAvatarOptions.first()) }
    var child by remember { mutableStateOf(target?.isChild ?: false) }
    var hidden by remember { mutableStateOf(target?.isHidden ?: false) }
    var pin by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(EditStep.Form) }
    var busy by remember { mutableStateOf(false) }
    // PIN ekranından dönünce odak PIN satırına, ilk açılışta ad kutusuna.
    var backFromPin by remember { mutableStateOf(false) }
    BackHandler(enabled = step != EditStep.Form) { step = EditStep.Form }

    val hasPin = pin?.isNotEmpty() ?: (target?.hasPin == true)
    when (step) {
        EditStep.Pin -> {
            PinPad(
                avatar = avatar,
                title = stringResource(R.string.tv_profile_new_pin),
                onComplete = { entered -> pin = entered; backFromPin = true; step = EditStep.Form; true },
                onRemove = if (hasPin) ({ pin = ""; backFromPin = true; step = EditStep.Form }) else null,
            )
            return
        }
        EditStep.Delete -> {
            if (target != null) {
                DeleteConfirm(target, onCancel = { step = EditStep.Form }, onConfirm = {
                    if (!busy) {
                        busy = true
                        scope.launch { repo.deleteProfile(target.id); onDone() }
                    }
                })
            }
            return
        }
        EditStep.Form -> Unit
    }

    val save: () -> Unit = {
        if (name.isNotBlank() && !busy) {
            busy = true
            scope.launch {
                val id = if (target == null) {
                    repo.createProfile(name.trim(), avatar, child, pin?.takeIf { it.isNotEmpty() }).id
                } else {
                    repo.updateProfile(target.id, name.trim(), avatar, child, pin)
                    target.id
                }
                if (hidden != (target?.isHidden ?: false)) repo.setHidden(id, hidden)
                onDone()
            }
        }
    }
    val nameField = remember { FocusRequester() }
    val pinRow = remember { FocusRequester() }
    var nameFocused by remember { mutableStateOf(false) }
    var pinFocused by remember { mutableStateOf(false) }

    SettingsPageFrame(if (target == null) stringResource(R.string.tv_profile_new_title) else stringResource(R.string.tv_profile_edit_title)) {
        item(key = "name") {
            Column {
                SectionTitle(stringResource(R.string.tv_profile_name))
                Spacer(Modifier.height(8.dp))
                TvTextField(
                    value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.tv_profile_name_placeholder), onDone = {},
                    modifier = Modifier.width(480.dp).focusRequester(nameField).onFocusChanged { nameFocused = it.isFocused },
                )
            }
        }
        item(key = "avatar") {
            Column {
                SectionTitle(stringResource(R.string.tv_profile_avatar))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    items(profileAvatarOptions) { av -> AvatarChoice(av, selected = av == avatar) { avatar = av } }
                }
            }
        }
        item(key = "child") {
            ListItem(
                selected = false,
                onClick = { child = !child },
                headlineContent = { Text(stringResource(R.string.tv_profile_child_profile)) },
                supportingContent = { Text(stringResource(R.string.tv_profile_child_summary), color = TextDim) },
                trailingContent = { Text(if (child) stringResource(R.string.tv_on) else stringResource(R.string.tv_off), color = if (child) Accent else TextDim) },
            )
        }
        item(key = "pin") {
            ListItem(
                selected = false,
                onClick = { step = EditStep.Pin },
                headlineContent = { Text(stringResource(R.string.tv_profile_pin_code)) },
                supportingContent = {
                    val text = when {
                        pin == "" -> stringResource(R.string.tv_profile_pin_will_remove)
                        pin != null -> stringResource(R.string.tv_profile_pin_will_save)
                        target?.hasPin == true -> stringResource(R.string.tv_profile_pin_set)
                        else -> stringResource(R.string.tv_profile_pin_unset)
                    }
                    Text(text, color = TextDim)
                },
                trailingContent = { Text(if (hasPin) stringResource(R.string.tv_on) else stringResource(R.string.tv_off), color = if (hasPin) Accent else TextDim) },
                modifier = Modifier.focusRequester(pinRow).onFocusChanged { pinFocused = it.isFocused },
            )
        }
        item(key = "hidden") {
            ListItem(
                selected = false,
                onClick = { hidden = !hidden },
                headlineContent = { Text(stringResource(R.string.tv_profile_hidden_profile)) },
                supportingContent = { Text(stringResource(R.string.tv_profile_hidden_summary), color = TextDim) },
                trailingContent = { Text(if (hidden) stringResource(R.string.tv_on) else stringResource(R.string.tv_off), color = if (hidden) Accent else TextDim) },
            )
        }
        item(key = "actions") {
            Column {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Button(onClick = save, enabled = name.isNotBlank() && !busy) { Text(if (target == null) stringResource(R.string.tv_profile_create) else stringResource(R.string.tv_save)) }
                    Button(onClick = onDone) { Text(stringResource(R.string.tv_profile_cancel)) }
                    // Etkin profil silinmez: silinince sıradaki (belki PIN'li) profil PIN sorulmadan etkin olurdu.
                    if (target != null && !isActive) Button(onClick = { step = EditStep.Delete }) { Text(stringResource(R.string.tv_profile_delete), color = ErrorText) }
                }
                if (target != null && isActive) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tv_profile_delete_active_note), color = TextDim, fontSize = TvText.Meta)
                }
            }
        }
    }
    if (backFromPin) InitialFocus(pinRow) { pinFocused } else InitialFocus(nameField) { nameFocused }
}

/** Yuvarlak avatar seçeneği: seçili olan vurgulu, odaktaki beyaz halkalı. */
@Composable
private fun AvatarChoice(avatar: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CardDefaults.shape(CircleShape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, Accent), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, if (selected) Accent else TextMain), shape = CircleShape),
        ),
        colors = CardDefaults.colors(containerColor = Surface1),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProfileAvatar(avatar = avatar, size = 56) }
    }
}

/** Silme ayrı bir onay sayfasında; odak "Vazgeç"te başlar ki yanlışlıkla OK'a basmak profili silmesin. */
@Composable
private fun DeleteConfirm(profile: LocalProfileSummary, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    var cancelFocused by remember { mutableStateOf(false) }
    SettingsPageFrame(stringResource(R.string.tv_profile_delete)) {
        item(key = "who") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileAvatar(avatar = profile.avatar, size = 56)
                Text(profile.name, color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.Bold)
            }
        }
        item(key = "warn") {
            Text(
                stringResource(R.string.tv_profile_delete_warning, profile.name),
                color = TextDim, fontSize = TvText.Body,
            )
        }
        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Button(onClick = onCancel, modifier = Modifier.focusRequester(cancel).onFocusChanged { cancelFocused = it.isFocused }) {
                    Text(stringResource(R.string.tv_profile_cancel))
                }
                Button(onClick = onConfirm) { Text(stringResource(R.string.tv_profile_delete_confirm), color = ErrorText) }
            }
        }
    }
    InitialFocus(cancel) { cancelFocused }
}

/**
 * Bulut hesabı: Play'in kendi bulut eşitlemesi (telefondaki Profil > Bulut hesabı paneliyle aynı hesap ve aynı işlemler).
 * Giriş e-posta + parolayla bu TV'de yapılır (ekran klavyesi ya da telefon kumandasının yazı kutusu); oturum cihazlar arası
 * aktarılmaz. Sunucu adresi/anahtarı derlemede verilmemişse ([SupabaseCloudSync.isConfigured]) panel bilgi notu gösterir.
 */
@Composable
private fun CloudAccountSection(cloud: SupabaseCloudSync) {
    val scope = rememberCoroutineScope()
    val account by cloud.account.collectAsStateWithLifecycle()
    val pending by cloud.pendingSources.collectAsStateWithLifecycle()
    val installing by cloud.installingSources.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failedInstall by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    val syncOk = stringResource(com.kaan.play.R.string.cloud_sync_success)
    val syncError = stringResource(com.kaan.play.R.string.cloud_sync_error_generic)
    val signedOut = stringResource(com.kaan.play.R.string.cloud_sync_signed_out)
    val loginOk = stringResource(com.kaan.play.R.string.cloud_auth_login_success)
    val loginFailed = stringResource(com.kaan.play.R.string.cloud_auth_login_failed)
    val regSignedIn = stringResource(com.kaan.play.R.string.cloud_auth_registered_signed_in)
    val regConfirm = stringResource(com.kaan.play.R.string.cloud_auth_registered_email_confirm)
    val regFailed = stringResource(com.kaan.play.R.string.cloud_auth_register_failed)

    Column {
        Spacer(Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.tv_profile_cloud_title))
        Spacer(Modifier.height(6.dp))
        val current = account
        if (!cloud.isConfigured()) {
            Text(stringResource(R.string.tv_profile_cloud_unavailable), color = TextDim, fontSize = TvText.Body)
        } else if (current != null) {
            Text(current.email, color = Accent, fontSize = TvText.Row)
            Text(stringResource(com.kaan.play.R.string.cloud_sync_scope), color = TextDim, fontSize = TvText.Body)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            message = if (cloud.synchronize().isSuccess) syncOk else syncError
                        } finally { busy = false }
                    }
                }) {
                    Text(stringResource(if (busy) com.kaan.play.R.string.cloud_sync_syncing else com.kaan.play.R.string.cloud_sync_action_now))
                }
                Button(enabled = !busy, onClick = { scope.launch { cloud.signOut(); message = signedOut } }) {
                    Text(stringResource(com.kaan.play.R.string.cloud_sync_sign_out))
                }
            }
            if (pending.isNotEmpty()) {
                // Buluttan gelen kaynaklar kendiliğinden kurulmaz (Play'deki onay kuralı).
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.tv_profile_cloud_pending_title), color = TextMain, fontSize = TvText.Body)
                pending.forEach { Text("• " + it.name.ifBlank { it.sourceUrl }, color = TextMain, fontSize = TvText.Body) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(enabled = !installing, onClick = { scope.launch { failedInstall = cloud.confirmSources(pending) } }) {
                        Text(if (installing) stringResource(R.string.tv_profile_cloud_installing) else stringResource(R.string.tv_profile_cloud_install_all, pending.size))
                    }
                    Button(enabled = !installing, onClick = { cloud.dismissPendingSources() }) { Text(stringResource(R.string.tv_profile_cloud_not_now)) }
                }
            }
            if (failedInstall > 0) Text(pluralStringResource(R.plurals.tv_profile_cloud_install_failed, failedInstall, failedInstall), color = Accent, fontSize = TvText.Body)
        } else {
            Text(stringResource(if (register) com.kaan.play.R.string.cloud_auth_title_register else com.kaan.play.R.string.cloud_auth_title_login), color = TextMain, fontSize = TvText.Body)
            Text(stringResource(com.kaan.play.R.string.cloud_auth_subtitle), color = TextDim, fontSize = TvText.Meta)
            Spacer(Modifier.height(10.dp))
            val submit: () -> Unit = submit@{
                if (busy || email.isBlank() || password.length < 6) return@submit
                scope.launch {
                    busy = true
                    try {
                        message = if (register) {
                            val r = cloud.createAccount(email.trim(), password)
                            when (r.getOrNull()) {
                                CloudRegistrationResult.SIGNED_IN -> regSignedIn
                                CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED -> regConfirm
                                null -> regFailed
                            }
                        } else {
                            if (cloud.signIn(email.trim(), password).isSuccess) loginOk else loginFailed
                        }
                        if (cloud.account.value != null) password = ""
                    } finally { busy = false }
                }
            }
            TvTextField(
                value = email, onValueChange = { email = it; message = null },
                placeholder = stringResource(com.kaan.play.R.string.cloud_auth_email_label),
                keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, onDone = {},
                modifier = Modifier.width(520.dp),
            )
            Spacer(Modifier.height(8.dp))
            TvTextField(
                value = password, onValueChange = { password = it; message = null },
                placeholder = stringResource(com.kaan.play.R.string.cloud_auth_password_label),
                keyboardType = KeyboardType.Password, onDone = submit,
                modifier = Modifier.width(520.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = submit) {
                    Text(stringResource(if (register) com.kaan.play.R.string.cloud_auth_submit_register else com.kaan.play.R.string.cloud_auth_submit_login))
                }
                Button(enabled = !busy, onClick = { register = !register; message = null }) {
                    Text(stringResource(if (register) com.kaan.play.R.string.cloud_auth_switch_to_login else com.kaan.play.R.string.cloud_auth_switch_to_register))
                }
            }
        }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = TextDim, fontSize = TvText.Body) }
    }
}
