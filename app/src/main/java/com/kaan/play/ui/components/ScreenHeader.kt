package com.kaan.play.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberInitialFocus

/** Alt ekranların ortak modern başlığı: dairesel geri oku + başlık + isteğe bağlı alt başlık ve sağ eylem düğmesi. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initialFocus: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val focus = rememberInitialFocus(enabled = initialFocus)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
                .size(40.dp)
                .then(if (initialFocus) Modifier.focusRequester(focus) else Modifier)
                .focusRing(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PlayIcons.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

