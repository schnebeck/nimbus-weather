/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/SkyAxis.kt
 * The time axis under the sun's and the moon's course: the hours below it, above it the
 * directions the body passes and when.
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

package dev.nimbus.weather.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.SkyCourse
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat

/** The eight directions' names (N, NE … NW) in the app's language. */
@Composable
internal fun directionNames(): List<String> = Texts.compass.map { stringResource(it) }

/** The 16-point name of [degrees] from north (where the sun or the moon rises and sets). */
@Composable
internal fun directionName(degrees: Double): String = stringResource(Texts.compass16[SkyCourse.index16(degrees)])

/** The sun's and the moon's chart alike: one above the other, their axes line up. */
internal val SkyChartHeight = 141.dp

internal val SkyAxisStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)

/** The axis' height under the plot: the directions' row, the line, the hours. */
internal fun DrawScope.skyAxisHeight(measurer: TextMeasurer): Float =
    2 * measurer.measure("00", SkyAxisStyle).size.height + 10.dp.toPx()

/**
 * The axis along the bottom of a chart from [start] to [end], [x] placing a time: the hours
 * every 6 h below the line and, above it, the directions in [crossings] – each where the body
 * stands in it, in [up] while above the horizon and faint while below. Where two would touch, the
 * one between the main directions gives way ([SkyCourse.spread]).
 */
internal fun DrawScope.drawSkyAxis(
    start: Long, end: Long, x: (Long) -> Float, tf: TimeFormat, measurer: TextMeasurer,
    crossings: List<SkyCourse.Crossing>, names: List<String>, up: Color,
) {
    val labelH = measurer.measure("00", SkyAxisStyle).size.height
    val line = size.height - labelH - 4.dp.toPx()
    drawLine(Color(0x40FFFFFF), Offset(x(start), line), Offset(x(end), line), 1.dp.toPx())
    var mark = start
    while (mark <= end) {
        val xm = x(mark)
        drawLine(Color(0x40FFFFFF), Offset(xm, line), Offset(xm, line + 3.dp.toPx()), 1.dp.toPx())
        val lt = measurer.measure(if (mark == end) tf.hourEnd(mark) else tf.hour(mark), SkyAxisStyle)
        // inside the chart: "12 AM" is wider than the "24" the margins are made for (it would be cut off)
        drawText(lt, topLeft = Offset(axisLabelLeft(xm, lt.size.width, size.width), line + 4.dp.toPx()))
        mark += 6 * 3_600_000L
    }
    val labels = crossings.associateWith { measurer.measure(names[it.direction], SkyAxisStyle.copy(color = if (it.altitude >= 0) up else NimbusColors.Tertiary)) }
    val kept = SkyCourse.spread(crossings, { it.main }, { x(it.time) }, { labels.getValue(it).size.width.toFloat() }, 6.dp.toPx())
    kept.forEach { c ->
        val xc = x(c.time)
        val lt = labels.getValue(c)
        val tick = if (c.altitude >= 0) up.copy(alpha = 0.8f) else Color(0x59FFFFFF)
        drawLine(tick, Offset(xc, line - 3.dp.toPx()), Offset(xc, line), 1.dp.toPx())
        drawText(lt, topLeft = Offset(axisLabelLeft(xc, lt.size.width, size.width), line - 4.dp.toPx() - lt.size.height))
    }
}
