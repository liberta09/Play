package com.kaan.play.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.focusRing
import androidx.compose.foundation.shape.CircleShape

@Composable
fun ProfileSetupScreen(
    onCreate: (name: String, avatar: String, isChild: Boolean, pin: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf(profileAvatarOptions.first()) }
    var isChild by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.profile_setup_title),
            onBack = onCancel,
        )

        Text(
            text = stringResource(R.string.profile_avatar_dialog_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(profileAvatarOptions) { av ->
                ProfileAvatar(
                    avatar = av,
                    selected = av == selectedAvatar,
                    size = 56,
                    modifier = Modifier.focusRing(CircleShape).clickable { selectedAvatar = av },
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.profile_edit_name_label)) },
            placeholder = { Text(stringResource(R.string.profile_setup_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().dpadLeavesTextField(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_setup_child_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.profile_setup_child_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isChild,
                onCheckedChange = { isChild = it },
                modifier = Modifier.focusRing(CircleShape),
            )
        }

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text(stringResource(R.string.profile_setup_pin_label)) },
            placeholder = { Text(stringResource(R.string.profile_setup_pin_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().dpadLeavesTextField(),
            supportingText = {
                Text(stringResource(R.string.profile_setup_pin_help))
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), selectedAvatar, isChild, pin.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.profile_setup_create_action))
            }
        }
    }
}
