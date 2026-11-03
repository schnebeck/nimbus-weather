/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/TempAxis.kt
 * The temperature axis of the charts: its marks counted from the freezing point.
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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.TemperatureUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * An axis from [lo] to [hi], a mark every [step]: all of them counted from [anchor] in both
 * directions (a temperature's from the freezing point, so that where frost begins is always one
 * of them – drawn stronger wherever it lies in the chart), every mark a round value at its line.
 */
data class ChartAxis(val lo: Double, val hi: Double, val step: Double, val anchor: Double) {
    /** The marks from the lowest up. */
    val ticks: List<Double> get() = (0..((hi - lo) / step).roundToLong().toInt()).map { lo + it * step }
    val intervals: Int get() = ticks.size - 1

    /** Whether [v] is the anchor (for a temperature: the freezing point). */
    fun isAnchor(v: Double): Boolean = kotlin.math.abs(v - anchor) < step / 1000

    companion object {
        /**
         * The axis for values from [min] to [max], [margin] of room above and below, at least
         * [minSpan] from the lowest mark to the highest, with [minIntervals] to [maxIntervals]
         * steps from [steps] (the smallest that keeps them few) between the marks.
         */
        fun around(
            min: Double, max: Double, anchor: Double, steps: List<Double>,
            margin: Double = 1.0, minSpan: Double = 0.0, minIntervals: Int = 2, maxIntervals: Int = 4,
        ): ChartAxis {
            val a = min - margin
            val b = maxOf(max + margin, a + minSpan)
            for (step in steps) {
                val lo = anchor + floor((a - anchor) / step) * step
                var hi = anchor + ceil((b - anchor) / step) * step
                val n = ((hi - lo) / step).roundToLong().toInt()
                if (n > maxIntervals) continue
                if (n < minIntervals) hi = lo + minIntervals * step
                return ChartAxis(lo, hi, step, anchor)
            }
            val step = steps.last()
            return ChartAxis(anchor + floor((a - anchor) / step) * step, anchor + ceil((b - anchor) / step) * step, step, anchor)
        }
    }
}

/** The temperature axis of the charts: its marks counted from the freezing point (0 °C, 32 °F). */
object TempAxis {
    /** The degrees between two marks. */
    val STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0)
    /** At least this many degrees from the lowest mark to the highest: a flat day stays flat. */
    const val MIN_SPAN = 4.0

    fun freezing(unit: TemperatureUnit): Double = if (unit == TemperatureUnit.FAHRENHEIT) 32.0 else 0.0

    /** The axis for temperatures from [min] to [max] (display unit), a degree of room above and below. */
    fun of(min: Double, max: Double, unit: TemperatureUnit): ChartAxis =
        ChartAxis.around(min, max, freezing(unit), STEPS, margin = 1.0, minSpan = MIN_SPAN)

    /**
     * The freezing line: stronger than the grid, in the blue of ice and dashed – the
     * temperature curve near 0° is blue too, and drawn solid.
     */
    val FrostLine = Color(0xD98FD3FF)
    /** Its number on the axis. */
    val FrostLabel = Color(0xFF8FD3FF)
    const val FROST_LINE_DP = 1.6f
    const val FROST_DASH_DP = 5f
    const val FROST_GAP_DP = 3f
}

/**
 * The precipitation axis beside a temperature axis of [parts] steps (their grid lines shared):
 * a step in whole, round amounts – mm 0.25, 0.5, 1, 2, 5 …, inches 0.01, 0.02, 0.05 … – so that
 * every line has a round number on either side.
 */
object PrecipAxis {
    private val MM = listOf(0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)
    private val INCH = listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0)

    /** The step for the highest amount [max] over [parts] steps. */
    /** [k] steps of [step] as written beside the line: one decimal, two where a quarter needs them. */
    fun label(k: Int, step: Double, inch: Boolean): String {
        val v = k * step
        return when {
            k == 0 -> "0"
            inch || kotlin.math.abs(v * 10 - Math.rint(v * 10)) > 1e-9 -> String.format(java.util.Locale.getDefault(), "%.2f", v)
            else -> dev.nimbus.weather.util.Units.oneDecimal(v)
        }
    }

    fun step(max: Double, parts: Int, inch: Boolean): Double = (if (inch) INCH else MM).firstOrNull { it * parts >= max - 1e-9 } ?: (if (inch) INCH else MM).last()
}

/** The freezing line from [from] to [to] ([TempAxis.FrostLine]). */
fun DrawScope.drawFrostLine(from: Offset, to: Offset) = drawLine(
    TempAxis.FrostLine, from, to, TempAxis.FROST_LINE_DP.dp.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(TempAxis.FROST_DASH_DP.dp.toPx(), TempAxis.FROST_GAP_DP.dp.toPx())),
)
