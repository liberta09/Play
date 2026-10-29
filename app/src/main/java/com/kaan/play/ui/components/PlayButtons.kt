package com.kaan.play.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kaan.play.ui.tv.focusRing
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledIconButton as M3FilledIconButton
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.OutlinedIconButton as M3OutlinedIconButton
import androidx.compose.material3.TextButton as M3TextButton

/** Turkuaz dolgulu düğmede turkuaz halka görünmez: ters (ana yazı) renk. */
@Composable
private fun onFilled(): Color = MaterialTheme.colorScheme.onBackground

/*
 * Material düğmelerinin ince sarmalayıcıları (aynı adlar, aynı kullanım): tek fark, kumanda/klavye odağında Play'in
 * turkuaz halkasının çizilmesi. Material düğmeleri `LocalIndication` kullanmadığı için halka burada eklenir.
 * Ekranlar `androidx.compose.material3.Button` yerine bunları içe aktarır.
 */

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    M3Button(onClick = onClick, modifier = modifier.focusRing(CircleShape, onFilled(), scale = 1.10f), enabled = enabled, content = content)

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    M3OutlinedButton(onClick = onClick, modifier = modifier.focusRing(CircleShape, scale = 1.10f), enabled = enabled, content = content)

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    M3TextButton(onClick = onClick, modifier = modifier.focusRing(CircleShape, scale = 1.10f), enabled = enabled, content = content)

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) =
    M3IconButton(onClick = onClick, modifier = modifier.focusRing(CircleShape, scale = 1.10f), enabled = enabled, content = content)

@Composable
fun OutlinedIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) =
    M3OutlinedIconButton(onClick = onClick, modifier = modifier.focusRing(CircleShape, scale = 1.10f), enabled = enabled, content = content)

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    content: @Composable () -> Unit,
) = M3FilledIconButton(onClick = onClick, modifier = modifier.focusRing(CircleShape, onFilled(), scale = 1.10f), enabled = enabled, colors = colors, content = content)
