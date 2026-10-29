package com.kaan.play.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.library.ContinueSortMode
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing

@Composable
fun ContinueSortControl(mode: ContinueSortMode, onSelect: (ContinueSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.focusRing(MaterialTheme.shapes.small),
        ) {
            Text(stringResource(R.string.home_continue_sort_action))
            Icon(PlayIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ContinueSortMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = { onSelect(option); expanded = false },
                    trailingIcon = if (option == mode) {
                        { Icon(PlayIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

private fun ContinueSortMode.labelRes(): Int = when (this) {
    ContinueSortMode.RecentFirst -> R.string.home_continue_sort_recent
    ContinueSortMode.OldestFirst -> R.string.home_continue_sort_oldest
    ContinueSortMode.MoviesFirst -> R.string.home_continue_sort_movies
    ContinueSortMode.SeriesFirst -> R.string.home_continue_sort_series
    ContinueSortMode.TitleAscending -> R.string.home_continue_sort_title
}
