package com.kaan.play.ui.components

import androidx.compose.foundation.BorderStroke
import com.kaan.play.ui.tv.focusRing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import java.util.Locale

private val BadgeShape = RoundedCornerShape(8.dp)

/** Play'in kendi puan rozeti: turkuaz çerçeveli "TMDB 8.0". Ondalık ayracı dilden bağımsız nokta. */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Text(
        text = stringResource(R.string.rating_tmdb, String.format(Locale.US, "%.1f", rating)),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, BadgeShape)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), BadgeShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Yaş sınırı çipi ("13+", "PG-13"): nötr çerçeve, vurgu rengi yok. */
@Composable
fun AgeChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, BadgeShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)), BadgeShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Süzgeç hapı (kaynak listesindeki eklenti filtresi). `selectable` olduğu için Play'in odak halkasını alır ve
 * kumandayla odaklanır; seçili hap turkuaz tonlu.
 */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            .focusRing(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
