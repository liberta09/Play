package com.kaan.play.ui.settings

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import com.kaan.play.ui.tv.focusRing
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.kaan.play.R
import com.kaan.play.data.settings.SubtitleBackground
import com.kaan.play.data.settings.SubtitleColor
import com.kaan.play.data.settings.SubtitleSize
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.ui.player.SubtitleStyling

private val PreviewHeight = 120.dp

/** Önizleme kutusu gerçek oynatıcıdan ~3 kat küçük (360dp -> 120dp); metin boyutu buna göre büyütülür. */
private const val PREVIEW_SCALE = 3f

/**
 * Canlı önizleme: gerçek Media3 SubtitleView, oynatıcıyla aynı biçimlendirme. Ayar değişince anında değişir.
 * Arka plan her zaman siyah (video gibi), böylece beyaz/sarı ve "arka plan yok" doğru görünür.
 */
@OptIn(UnstableApi::class)
@Composable
fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    val sample = stringResource(R.string.subtitle_preview)
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { view ->
                SubtitleStyling.apply(view, style, PREVIEW_SCALE)
                view.setCues(listOf(Cue.Builder().setText(sample).build()))
            },
            modifier = Modifier.fillMaxWidth().height(PreviewHeight),
        )
    }
}

/**
 * Altyazı görünümü denetimleri: boyut, kalın, italik, yazı rengi, arka plan. Hepsi dokunma ve D-pad ile
 * kullanılır (çip ve anahtarlar odaklanabilir). [onUpdate], DataStore'a tek yazmayla kaydedilen dönüşümü alır.
 */
@Composable
fun SubtitleStyleControls(style: SubtitleStyle, onUpdate: ((SubtitleStyle) -> SubtitleStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChipGroup(
            title = stringResource(R.string.subtitle_size),
            options = SubtitleSize.entries,
            selected = style.size,
            label = { it.labelRes() },
            onSelect = { size -> onUpdate { it.copy(size = size) } },
        )
        SwitchRow(stringResource(R.string.subtitle_bold), style.bold) { v -> onUpdate { it.copy(bold = v) } }
        SwitchRow(stringResource(R.string.subtitle_italic), style.italic) { v -> onUpdate { it.copy(italic = v) } }
        ChipGroup(
            title = stringResource(R.string.subtitle_color),
            options = SubtitleColor.entries,
            selected = style.color,
            label = { it.labelRes() },
            onSelect = { color -> onUpdate { it.copy(color = color) } },
        )
        ChipGroup(
            title = stringResource(R.string.subtitle_background),
            options = SubtitleBackground.entries,
            selected = style.background,
            label = { it.labelRes() },
            onSelect = { bg -> onUpdate { it.copy(background = bg) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    @StringRes label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    label = { Text(stringResource(label(option))) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private fun SubtitleSize.labelRes(): Int = when (this) {
    SubtitleSize.Small -> R.string.subtitle_size_small
    SubtitleSize.Normal -> R.string.subtitle_size_normal
    SubtitleSize.Large -> R.string.subtitle_size_large
    SubtitleSize.ExtraLarge -> R.string.subtitle_size_xlarge
}

private fun SubtitleColor.labelRes(): Int = when (this) {
    SubtitleColor.White -> R.string.subtitle_color_white
    SubtitleColor.Yellow -> R.string.subtitle_color_yellow
}

private fun SubtitleBackground.labelRes(): Int = when (this) {
    SubtitleBackground.None -> R.string.subtitle_background_none
    SubtitleBackground.Translucent -> R.string.subtitle_background_translucent
    SubtitleBackground.Black -> R.string.subtitle_background_black
}
