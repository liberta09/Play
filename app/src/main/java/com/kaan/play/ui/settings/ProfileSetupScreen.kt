package com.kaan.play.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.DismissImeOnBack
import com.kaan.play.ui.tv.dismissImeOnDoneOrBack
import com.kaan.play.ui.tv.rememberFocusMemory

@Composable
fun ProfileSetupScreen(
    onCreate: (name: String, avatar: String, child: Boolean, pin: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("space_astro") }
    var child by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var nameFocused by remember { mutableStateOf(false) }
    var pinFocused by remember { mutableStateOf(false) }
    val valid = name.trim().isNotEmpty() && (pin.isEmpty() || pin.length >= 4)
    val focus = rememberFocusMemory(forceOnTouch = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DismissImeOnBack(nameFocused || pinFocused)
    val background = Brush.verticalGradient(listOf(Color(0xFF3A1027), Color(0xFF190B14), Color(0xFF050505)))

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("PLAY", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF37D5E7))
            Text("Profil oluştur", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("İzleme geçmişin, favorilerin ve tercihlerin bu profile özel tutulur.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .76f))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF101F32))) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Seçili Avatar Önizleme (Netflix tarzı)
                    ProfileAvatar(avatar = avatar, size = 88, selected = true)
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.fillMaxWidth().focusMemory(focus, "profile-name", isDefault = true).focusRing(RoundedCornerShape(12.dp)).dpadLeavesTextField().onFocusChanged { nameFocused = it.isFocused }.dismissImeOnDoneOrBack(), label = { Text("Profil adı") }, placeholder = { Text("Örn. Kaan") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                    
                    Text("Avatarını seç", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.Start))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(profileAvatarOptions, key = { _, item -> item }) { index, value ->
                            ProfileAvatar(
                                avatar = value,
                                selected = avatar == value,
                                size = 64,
                                modifier = Modifier
                                    .clickable { avatar = value }
                                    .focusMemory(focus, "avatar-$index")
                                    .focusRing(RoundedCornerShape(50))
                                    .clip(RoundedCornerShape(50)),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.Start)) {
                        Checkbox(checked = child, onCheckedChange = { child = it }, modifier = Modifier.focusMemory(focus, "child-toggle").focusRing(RoundedCornerShape(8.dp)))
                        Text("Çocuk profili", color = Color.White)
                    }
                    OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, modifier = Modifier.fillMaxWidth().focusMemory(focus, "pin").focusRing(RoundedCornerShape(12.dp)).dpadLeavesTextField().onFocusChanged { pinFocused = it.isFocused }.dismissImeOnDoneOrBack(), label = { Text("PIN (isteğe bağlı)") }, supportingText = { Text("En az 4 rakam; cihazlara PIN değil hash eşitlenir") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus(force = true) }))
                }
            }
            Button(onClick = { keyboard?.hide(); focusManager.clearFocus(force = true); onCreate(name.trim(), avatar, child, pin.takeIf { it.isNotEmpty() }) }, enabled = valid, modifier = Modifier.fillMaxWidth().focusMemory(focus, "create-profile").focusRing(RoundedCornerShape(12.dp))) { Text("Profili oluştur") }
            TextButton(onClick = { keyboard?.hide(); focusManager.clearFocus(force = true); onCancel() }, modifier = Modifier.focusMemory(focus, "cancel").focusRing(RoundedCornerShape(12.dp))) { Text("Vazgeç", color = Color.White.copy(alpha = .82f)) }
        }
    }
}
