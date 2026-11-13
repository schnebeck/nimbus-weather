/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetColorChoice.kt
 * The widget's background in its setup: the system's colour, a few at hand, or one picked freely.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Opus 5.5 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.R

/** Colours at hand: dark and light greys, and some calm ones that suit a home screen. */
internal val WIDGET_COLORS = listOf(
    0xFF000000, 0xFF2B2B30, 0xFF8A8A92, 0xFFFFFFFF, 0xFF1E2A44, 0xFF2F64B5,
    0xFF00796B, 0xFF2E7D32, 0xFFF9A825, 0xFFC62828, 0xFF6A1B9A,
).map { it.toInt() }

/** The colour's tag in tests and its name for screen readers: "#RRGGBB". */
internal fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

/** The background: its opacity, its colour – the system's (null), one at hand or one picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetBackgroundChoice(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val chosen = config.background
    Column {
        Text(
            stringResource(R.string.widget_setup_opacity, (config.opacity * 100).toInt()),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        )
        Slider(config.opacity, { onChange(config.copy(opacity = it)) }, valueRange = 0f..1f, steps = 9)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // the system's colour: light by day, dark by night
            Swatch(
                Brush.linearGradient(0.5f to Color(0xFFF4F3F8), 0.5f to Color(0xFF1D1D22)),
                chosen == null, stringResource(R.string.widget_color_system), "widget-color-system",
            ) { onChange(config.copy(background = null)) }
            WIDGET_COLORS.forEach { c ->
                Swatch(Brush.linearGradient(listOf(Color(c), Color(c))), chosen == c, hex(c), "widget-color-${hex(c)}") {
                    onChange(config.copy(background = c))
                }
            }
            // a colour of one's own: the rainbow – or, once picked, that colour inside it
            val own = chosen?.takeIf { it !in WIDGET_COLORS }
            Swatch(
                Brush.sweepGradient(RAINBOW), own != null, stringResource(R.string.widget_color_custom), "widget-color-custom",
                inner = own?.let { Color(it) },
            ) { picking = true }
        }
        Text(
            stringResource(R.string.widget_color_hint), Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (picking) ColorPickerDialog(chosen ?: WIDGET_COLORS[5], onDismiss = { picking = false }) {
        picking = false
        onChange(config.copy(background = it))
    }
}

/** The hues round the circle, red to red. */
internal val RAINBOW = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }

@Composable
private fun Swatch(fill: Brush, selected: Boolean, name: String, tag: String, inner: Color? = null, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = name; this.selected = selected }.testTag(tag)
            .padding(if (selected) 5.dp else 2.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (inner != null) Box(Modifier.size(20.dp).clip(CircleShape).background(inner))
    }
}
