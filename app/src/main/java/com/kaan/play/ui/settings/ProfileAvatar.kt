package com.kaan.play.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AvatarStyle(
    val color: Color,
    val symbol: String,
)

val profileAvatarOptions = listOf("aurora", "ocean", "berry", "sunset", "forest", "moon")

private val avatarStyles = mapOf(
    "aurora" to AvatarStyle(Color(0xFF4DD0B5), "✦"),
    "ocean" to AvatarStyle(Color(0xFF4F8FEA), "≈"),
    "berry" to AvatarStyle(Color(0xFFB76CE2), "◆"),
    "sunset" to AvatarStyle(Color(0xFFFF8A65), "☀"),
    "forest" to AvatarStyle(Color(0xFF66BB6A), "▲"),
    "moon" to AvatarStyle(Color(0xFF90A4AE), "●"),
)

@Composable
fun ProfileAvatar(
    avatar: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Int = 48,
) {
    val style = avatarStyles[avatar] ?: avatarStyles["aurora"]!!
    val borderWidth = if (selected) 2.dp else 0.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(style.color.copy(alpha = 0.2f))
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = style.symbol,
            color = style.color,
            fontSize = (size * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
