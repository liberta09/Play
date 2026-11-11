package com.kaan.play.ui.party

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.party.PartyConnectionState
import com.kaan.play.data.party.PartyRole
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

@Composable
fun WatchPartyScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMedia: ((tmdbId: Int, mediaType: String, season: Int?, episode: Int?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = rememberFocusMemory()
    val partyManager = container.watchPartyManager
    val activeProfile by container.localProfile.activeProfile.collectAsStateWithLifecycle(initialValue = null)

    val session by partyManager.session.collectAsStateWithLifecycle()
    val connectionState by partyManager.realtimeClient.connectionState.collectAsStateWithLifecycle()

    var inputCode by remember { mutableStateOf("") }
    var joinLoading by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }

    val linkCopiedMsg = stringResource(R.string.watch_party_link_copied)
    val syncErrorMsg = stringResource(R.string.watch_party_out_of_sync)

    val backFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            // Üst Çubuk
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .focusRing()
                        .focusMemory(focus, "wp_back"),
                ) {
                    Icon(
                        imageVector = PlayIcons.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.watch_party_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.watch_party_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val currentSession = session
                if (currentSession != null) {
                    // 1) Aktif Oda Kartı
                    item {
                        ActiveRoomCard(
                            session = currentSession,
                            connectionState = connectionState,
                            onCopyLink = {
                                val link = "play://party?code=${currentSession.room.code}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Watch Party Link", link))
                                Toast.makeText(context, linkCopiedMsg, Toast.LENGTH_SHORT).show()
                            },
                            onLeave = {
                                scope.launch {
                                    partyManager.leaveParty()
                                }
                            },
                            onOpenPlayer = {
                                onOpenMedia?.invoke(
                                    currentSession.room.tmdbId,
                                    currentSession.room.mediaType,
                                    currentSession.room.season,
                                    currentSession.room.episode,
                                )
                            },
                            onShareInvite = {
                                val inviteText = buildString {
                                    append(context.getString(R.string.watch_party_share_message, currentSession.room.code))
                                    val title = currentSession.room.title
                                    if (!title.isNullOrBlank()) {
                                        append("\n")
                                        append(context.getString(R.string.watch_party_share_title, title))
                                    }
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, inviteText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                        )
                    }
                } else {
                    // 2) Odaya Katıl Kartı
                    item {
                        JoinRoomCard(
                            code = inputCode,
                            onCodeChange = { raw ->
                                val clean = raw.uppercase().filter { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" }.take(6)
                                inputCode = clean
                                joinError = null
                            },
                            isLoading = joinLoading,
                            errorMessage = joinError,
                            onJoin = {
                                if (inputCode.length == 6) {
                                    joinLoading = true
                                    joinError = null
                                    scope.launch {
                                        val result = partyManager.joinParty(inputCode, displayName = activeProfile?.name)
                                        joinLoading = false
                                        result.onSuccess { room ->
                                            onOpenMedia?.invoke(room.tmdbId, room.mediaType, room.season, room.episode)
                                        }.onFailure { err ->
                                            joinError = err.toWatchPartyErrorMessage(context)
                                        }
                                    }
                                }
                            },
                        )
                    }

                    // 3) Bilgilendirme Kartı
                    item {
                        HowItWorksCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRoomCard(
    session: com.kaan.play.data.party.PartySession,
    connectionState: PartyConnectionState,
    onCopyLink: () -> Unit,
    onLeave: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShareInvite: () -> Unit = {},
) {
    val room = session.room
    val roleStr = if (session.role == PartyRole.HOST) {
        stringResource(R.string.watch_party_role_host)
    } else {
        stringResource(R.string.watch_party_role_guest)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.watch_party_badge, session.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Rol rozeti
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = roleStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 6 Haneli Oda Kodu (Büyük, Monospace)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.watch_party_join_code_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = room.code,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = stringResource(R.string.watch_party_room_code_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (session.members.isNotEmpty()) {
            Text(
                text = session.members.joinToString(", ") { it.displayName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        if (session.role == PartyRole.HOST) {
            Button(
                onClick = onShareInvite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRing(),
            ) {
                Text(stringResource(R.string.watch_party_share_invite))
            }
        }

        // Eylemler
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCopyLink,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRing(),
            ) {
                Icon(PlayIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.watch_party_copy_link))
            }

            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRing(),
            ) {
                Text(stringResource(R.string.watch_party_leave_button))
            }
        }
    }
}

@Composable
private fun JoinRoomCard(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.watch_party_join_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ABC234") },
            label = { Text(stringResource(R.string.watch_party_join_code_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onJoin() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onJoin,
            enabled = code.length == 6 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRing(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.watch_party_join_button),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.watch_party_how_it_works),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.watch_party_how_it_works_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}
