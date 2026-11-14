package com.kaan.play.tv

import com.kaan.play.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.settings.ProfileAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Kim izliyor?": Play'in yerel profilleri (telefondan Wi-Fi ile gelir) arasından seçim; izleme geçmişi ve favoriler
 * seçilen profile göre tutulur. PIN'li profil kumandayla girilen 4 haneli PIN ister (Play'in doğrulaması).
 * Profil ekleme/düzenleme telefonda yapılır. Odak açılışta etkin profildedir.
 * [hideHidden]: "Gizli profil" işaretli profiller gösterilmez (hepsi gizliyse seçici boş kalmasın diye hepsi görünür).
 */
@Composable
internal fun TvProfileChooser(profiles: List<LocalProfileSummary>, activeId: String?, hideHidden: Boolean = false, onChosen: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as PlayApp).container.localProfile }
    val scope = rememberCoroutineScope()
    var pinFor by remember { mutableStateOf<LocalProfileSummary?>(null) }
    val choose: (LocalProfileSummary) -> Unit = { p -> scope.launch { repo.selectProfile(p.id); onChosen() } }

    val target = pinFor
    if (target != null) {
        BackHandler { pinFor = null }
        PinPad(
            avatar = target.avatar,
            title = stringResource(R.string.tv_profile_enter_pin, target.name),
            onComplete = { pin ->
                val ok = withContext(Dispatchers.Default) { repo.verifyPin(target, pin) }
                if (ok) { pinFor = null; choose(target) }
                ok
            },
            backHint = stringResource(R.string.tv_profile_back_to_profiles),
        )
        return
    }
    val shown = if (hideHidden) profiles.filterNot { it.isHidden }.ifEmpty { profiles } else profiles
    val initial = shown.firstOrNull { it.id == activeId } ?: shown.firstOrNull()
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.tv_profile_who_is_watching), color = TextMain, fontSize = TvText.Screen, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(36.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
            ) {
                items(shown, key = { it.id }) { p ->
                    ProfileTile(p, requestInitialFocus = p == initial) { if (p.hasPin) pinFor = p else choose(p) }
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(p: LocalProfileSummary, requestInitialFocus: Boolean, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // yuvarlak avatar; odakta vurgu halka ve hafif büyüme.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Card(
                onClick = onClick,
                modifier = Modifier.size(88.dp).focusRequester(requester).onFocusChanged { focused = it.isFocused },
                shape = CardDefaults.shape(CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.08f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Accent), shape = CircleShape)),
                colors = CardDefaults.colors(containerColor = Surface1),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProfileAvatar(avatar = p.avatar, size = 80) }
            }
            if (p.hasPin) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Bg), contentAlignment = Alignment.Center) {
                    Icon(PlayIcons.Lock, contentDescription = stringResource(R.string.tv_profile_pin_protected), tint = Accent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p.name, color = if (focused) TextMain else TextDim, fontSize = TvText.Body, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(120.dp),
        )
        if (p.isChild) Text(stringResource(R.string.tv_profile_child), color = TextDim, fontSize = TvText.Label)
    }
    if (requestInitialFocus) InitialFocus(requester) { focused }
}

/**
 * Kumandayla 4 haneli PIN: ekrandaki tuşlar ya da kumandanın rakam tuşları; haneler yalnız nokta olarak görünür.
 * Dördüncü hanede [onComplete] çağrılır; false dönerse "PIN hatalı" yazılır ve yeniden girilir.
 * [onRemove] doluysa sağ alttaki boşlukta "Kaldır" tuşu çıkar (mevcut PIN'i silmek için).
 * Açılıştaki seçici ve Ayarlar → Profiller aynı tuş takımını kullanır.
 */
@Composable
internal fun PinPad(
    avatar: String,
    title: String,
    onComplete: suspend (String) -> Boolean,
    onRemove: (() -> Unit)? = null,
    backHint: String = stringResource(R.string.tv_profile_back_cancel),
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }

    fun press(d: Char) {
        if (checking || pin.length >= PIN_LENGTH) return
        pin += d; error = false
        if (pin.length == PIN_LENGTH) {
            checking = true
            val entered = pin
            scope.launch {
                val ok = onComplete(entered)
                checking = false
                if (!ok) { error = true; pin = "" }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Bg).onKeyEvent { e ->
            val digit = DIGIT_KEYS[e.key]
            if (e.type == KeyEventType.KeyDown && digit != null) { press(digit); true } else false
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(avatar = avatar, size = 72)
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(PIN_LENGTH) { i ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < pin.length) Accent else TextDim.copy(alpha = 0.35f)))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (error) stringResource(R.string.tv_profile_pin_wrong) else " ", color = ErrorText, fontSize = TvText.Body)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { d ->
                            val m = if (d == '1') Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier
                            PadKey(d.toString(), m) { press(d) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadKey(stringResource(R.string.tv_profile_pad_delete)) { pin = pin.dropLast(1); error = false }
                    PadKey("0") { press('0') }
                    if (onRemove != null) PadKey(stringResource(R.string.tv_profile_pad_remove), onClick = onRemove) else Spacer(Modifier.size(KEY_W, KEY_H))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(backHint, color = TextDim, fontSize = TvText.Meta)
        }
    }
    InitialFocus(first) { firstFocused }
}

@Composable
private fun PadKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.size(KEY_W, KEY_H), contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (label.length == 1) 22.sp else TvText.Body)
        }
    }
}

private const val PIN_LENGTH = 4
private val KEY_W = 84.dp
private val KEY_H = 56.dp
private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
)
