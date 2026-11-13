/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/ColorPickerDialog.kt
 * A colour picked freely: its hue on a bar, its saturation and brightness on a field.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.R

/** Picks a colour starting from [initial] (opaque ARGB); [onPick] gets the one confirmed. */
@Composable
internal fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, sat, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_color_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // saturation to the right, brightness upwards
                Canvas(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).testTag("color-field")
                        .track { p, size ->
                            sat = (p.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val at = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.White, 9.dp.toPx(), at, style = Stroke(2.5f.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.4f), 10.5f.dp.toPx(), at, style = Stroke(1.dp.toPx()))
                }
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).testTag("color-hue")
                        .track { p, size -> hue = (p.x / size.width).coerceIn(0f, 1f) * 359.9f },
                ) {
                    drawRect(Brush.horizontalGradient(RAINBOW))
                    val x = hue / 360f * size.width
                    drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x, size.height / 2), style = Stroke(2.5f.dp.toPx()))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color))
                    Text(hex(color.toArgb()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("color-hex"))
                }
            }
        },
        confirmButton = {
            TextButton({ onPick(color.toArgb() or WidgetInk.OPAQUE) }, Modifier.testTag("color-ok")) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Follows a finger from where it touches down until it lifts, the position within the view's [IntSize]. */
private fun Modifier.track(onAt: (Offset, IntSize) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onAt(down.position, size)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            onAt(change.position, size)
            change.consume()
        }
    }
}
