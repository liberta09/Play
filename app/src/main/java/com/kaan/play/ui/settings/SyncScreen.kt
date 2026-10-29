package com.kaan.play.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.data.sync.CloudRegistrationResult
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.DismissImeOnBack
import com.kaan.play.ui.tv.dismissImeOnDoneOrBack
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val account by container.cloudSync.account.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cloudStatus by remember { mutableStateOf<String?>(null) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var cloudBusy by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    val createAccountFocusRequester = remember { FocusRequester() }
    DismissImeOnBack(emailFocused || passwordFocused)

    LaunchedEffect(Unit) {
        container.startCloudAutoSync()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Senkronizasyon", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bulut hesabı", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Telefon ve TV internet üzerinden eşitlenir; aynı Wi‑Fi ağı veya eşleştirme kodu gerekmez. İzleme geçmişi, favoriler, profil adı, avatarı, çocuk ayarı ve PIN koruması bu hesaba bağlanır. PIN’in kendisi gönderilmez; yalnızca tuzlu PBKDF2 özeti eşitlenir.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (account != null) {
                        Text("Giriş yapıldı", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(account!!.email, style = MaterialTheme.typography.bodyLarge)
                        Text("Aynı hesapla diğer cihazlarda giriş yapın. Geçmiş ve favoriler otomatik olarak eşitlenir.")
                        TextButton(
                            onClick = {
                                scope.launch {
                                    cloudBusy = true
                                    cloudStatus = "Son değişiklikler kaydedilip hesaptan çıkılıyor…"
                                    try {
                                        val synced = container.cloudSync.signOut()
                                        cloudStatus = if (synced) {
                                            "Çıkış yapıldı. Bulut kopyanız duruyor; bu cihazın yerel profil verisi hesaplar karışmasın diye temizlendi."
                                        } else {
                                            "Çıkış yapıldı ancak internet eşitlemesi başarısızdı. Son cihaz değişiklikleri buluta ulaşmamış olabilir; bu cihazdaki kopya hesap güvenliği için temizlendi."
                                        }
                                    } catch (e: Exception) {
                                        cloudStatus = "Yerel çıkış tamamlanamadı: ${e.message.orEmpty()}"
                                    } finally {
                                        cloudBusy = false
                                    }
                                }
                            },
                            enabled = !cloudBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Çıkış yap / hesabı değiştir") }
                    } else {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(emailFocusRequester)
                                .focusProperties {
                                    down = passwordFocusRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        passwordFocusRequester.requestFocus()
                                        true
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
                            onValueChange = { password = it },
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
                                            Key.DirectionUp -> {
                                                emailFocusRequester.requestFocus()
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                submitFocusRequester.requestFocus()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .onFocusChanged { passwordFocused = it.isFocused }
                                .dismissImeOnDoneOrBack(),
                            label = { Text("Şifre (en az 8 karakter)") },
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
                                if (!trimmedEmail.contains('@') || !trimmedEmail.contains('.')) {
                                    cloudStatus = "Lütfen geçerli bir e-posta adresi girin."
                                    emailFocusRequester.requestFocus()
                                    return@Button
                                }
                                if (password.isEmpty()) {
                                    cloudStatus = "Lütfen şifrenizi girin."
                                    passwordFocusRequester.requestFocus()
                                    return@Button
                                }
                                scope.launch {
                                    cloudBusy = true
                                    cloudStatus = "Giriş yapılıyor…"
                                    try {
                                        container.cloudSync.signIn(trimmedEmail, password)
                                        password = ""
                                        val count = container.cloudSync.synchronize()
                                        cloudStatus = "Giriş başarılı. $count profil otomatik eşitleniyor."
                                    } catch (e: Exception) {
                                        cloudStatus = e.message ?: "Giriş başarısız. Bilgileri kontrol edip tekrar deneyin."
                                    } finally {
                                        cloudBusy = false
                                    }
                                }
                            },
                            enabled = !cloudBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(submitFocusRequester)
                                .focusProperties {
                                    up = passwordFocusRequester
                                    down = createAccountFocusRequester
                                },
                        ) { Text(if (cloudBusy) "Bağlanıyor…" else "Giriş yap") }
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                val trimmedEmail = email.trim()
                                if (!trimmedEmail.contains('@') || !trimmedEmail.contains('.')) {
                                    cloudStatus = "Lütfen geçerli bir e-posta adresi girin."
                                    emailFocusRequester.requestFocus()
                                    return@TextButton
                                }
                                if (password.length < 8) {
                                    cloudStatus = "Şifre en az 8 karakter olmalıdır."
                                    passwordFocusRequester.requestFocus()
                                    return@TextButton
                                }
                                scope.launch {
                                    cloudBusy = true
                                    cloudStatus = "Hesap oluşturuluyor…"
                                    try {
                                        when (container.cloudSync.createAccount(trimmedEmail, password)) {
                                            CloudRegistrationResult.SIGNED_IN -> {
                                                password = ""
                                                val count = container.cloudSync.synchronize()
                                                cloudStatus = "Hesap oluşturuldu ve giriş yapıldı. $count profil otomatik eşitleniyor."
                                            }
                                            CloudRegistrationResult.EMAIL_CONFIRMATION_REQUIRED -> {
                                                password = ""
                                                cloudStatus = "Doğrulama bağlantısını e-posta kutunuzdan açın; sonra bu ekranda giriş yapın."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        cloudStatus = e.message ?: "Hesap oluşturulamadı. Bilgileri kontrol edip tekrar deneyin."
                                    } finally {
                                        cloudBusy = false
                                    }
                                }
                            },
                            enabled = !cloudBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(createAccountFocusRequester)
                                .focusProperties {
                                    up = submitFocusRequester
                                },
                        ) { Text("Yeni hesap oluştur") }
                        Text("Oturum bilgileri cihazda şifreli saklanır. Şifre Supabase'e HTTPS üzerinden gönderilir ve cihazda kaydedilmez.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (cloudBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    cloudStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }

        }
    }
}
