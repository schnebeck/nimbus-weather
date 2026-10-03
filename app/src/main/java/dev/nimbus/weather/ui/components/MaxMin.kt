/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/MaxMin.kt
 * Large temperature with unit and the max/min display of a weather station.
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

package dev.nimbus.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Units

/**
 * Max/min temperature stacked like on a weather station display: labels in one column,
 * right-aligned values with unit in the other.
 */
@Composable
fun MaxMinStack(
    maxC: Double?, minC: Double?, unit: TemperatureUnit,
    modifier: Modifier = Modifier, fontSize: TextUnit = 18.sp, shadow: Shadow? = null,
    /** On the open sky (header) the labels are white as well. */
    labelColor: Color = NimbusColors.Secondary,
) {
    val lineH = with(LocalDensity.current) { (fontSize * 1.3f).toDp() }
    val labelStyle = TextStyle(fontSize = fontSize * 0.62f, color = labelColor, fontWeight = FontWeight.Medium, shadow = shadow)
    val valueStyle = TextStyle(fontSize = fontSize, color = Color.White, fontWeight = FontWeight.Medium, shadow = shadow)
    val rows = listOf(stringResource(R.string.max_label) to maxC, stringResource(R.string.min_label) to minC)
    Row(modifier) {
        Column {
            rows.forEach { (label, _) ->
                Box(Modifier.height(lineH), contentAlignment = Alignment.CenterStart) { Text(label.uppercase(), style = labelStyle) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            rows.forEach { (_, v) ->
                Box(Modifier.height(lineH), contentAlignment = Alignment.CenterEnd) { Text(Units.tempFull(v, unit), style = valueStyle) }
            }
        }
    }
}

/** Large temperature with the unit set smaller and raised, e.g. "20 °C". */
@Composable
fun BigTemperature(celsius: Double?, unit: TemperatureUnit, fontSize: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Thin, shadow: Shadow? = null) {
    val number = Units.tempNumber(celsius, unit)
    val size = if (number.length >= 3) fontSize * 0.82f else fontSize
    Row(modifier) {
        Text(number, style = TextStyle(fontSize = size, fontWeight = weight, color = Color.White, lineHeight = size * 1.04f, shadow = shadow))
        Text(
            Units.tempUnit(unit),
            style = TextStyle(fontSize = size * 0.36f, fontWeight = FontWeight.Light, color = Color.White, shadow = shadow),
            modifier = Modifier.padding(start = 2.dp, top = with(LocalDensity.current) { (size * 0.16f).toDp() }),
        )
    }
}

/**
 * Shrinks the content as a whole when it is wider than the space it gets (a narrow phone, a large
 * system font) – the header's symbol, temperature and max/min stay one line and complete instead
 * of being cut off at the edge. Content that fits is left as it is.
 */
fun Modifier.shrinkToFit(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
        val s = if (constraints.hasBoundedWidth && p.width > constraints.maxWidth) constraints.maxWidth.toFloat() / p.width else 1f
        val w = kotlin.math.ceil(p.width * s).toInt(); val h = kotlin.math.ceil(p.height * s).toInt()
        layout(w, h) {
            // scaled about its centre, placed so that the centre is the middle of the space taken
            p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { scaleX = s; scaleY = s }
        }
    },
)

/**
 * [text] on one line – or [short] (an abbreviation) when the full form does not fit the space
 * (a narrow tile, a large system font): never broken inside a word, never cut off at the edge.
 */
@Composable
fun FitText(text: String, short: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val fits = !constraints.hasBoundedWidth ||
            measurer.measure(text, style, softWrap = false, density = density).size.width <= constraints.maxWidth
        Text(if (fits) text else short, style = style, maxLines = 1, softWrap = false)
    }
}

/** Running text in German: hyphenated where a long word does not fit the line ("Luftfeuch-tigkeit"), not broken anywhere. */
val Hyphenated = TextStyle(
    hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
)

/**
 * Text as wide as its longest line. Wrapped, a [Text] takes all the width it is offered – the
 * pill around the station line stood wide around a short second line.
 */
@Composable
fun TightText(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    val merged = androidx.compose.material3.LocalTextStyle.current.merge(style)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    androidx.compose.ui.layout.Layout({ Text(text, style = merged) }, modifier) { measurables, c ->
        val r = measurer.measure(text, merged, constraints = androidx.compose.ui.unit.Constraints(maxWidth = c.maxWidth), density = density)
        val widest = (0 until r.lineCount).maxOfOrNull { kotlin.math.ceil(r.getLineRight(it) - r.getLineLeft(it)).toInt() } ?: 0
        val p = measurables.first().measure(c.copy(minWidth = 0, maxWidth = (widest + 1).coerceAtMost(c.maxWidth)))
        layout(p.width, p.height) { p.place(0, 0) }
    }
}
