/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HourBars.kt
 * The hourly bars of the day charts (precipitation, sunshine): measured filled, expected framed.
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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource

/** One quantity's bars: [fill] for a measured hour, an unfilled frame in [frame] for an expected one. */
class BarLook(val fill: Color, val frame: Color)

/**
 * Measured hours are filled blocks, expected ones (the forecast) unfilled frames of the bar's size –
 * in every chart and every form of the cards. Where both are known (look-back) the frame stands in
 * front of the block and shows the forecast above, on or inside what came. The frames are opaque
 * and of a colour of their own, so they stand out from their block as from the dark glass.
 */
object HourBars {
    /**
     * Precipitation: light blue, the forecast framed in violet – dark and saturated enough to stand
     * out in front of the light blue block as on the dark glass.
     */
    val Rain = BarLook(fill = Color(0xB38FD3FF), frame = Color(0xFF9B4DFF))
    /** Sunshine: light grey (the temperature curve has the warm colours), the forecast framed in orange. */
    val Sun = BarLook(fill = Color(0xFFC3C9D2), frame = Color(0xFFFFB547))
    const val FRAME_DP = 1.5f
}

/** What an hour's bar shows of a quantity: the [measured] amount (block) and the [expected] one (frame). */
data class HourBar(val measured: Double?, val expected: Double?)

/** The hour's precipitation: a reading is a block; the forecast – of an hour to come or beside the reading – a frame. */
fun MeteoPoint.rainBar() = HourBar(
    precipitation.takeIf { precipMeasured }, forecastPrecipitation ?: precipitation.takeIf { !precipMeasured },
)

/** The hour's sunshine, as [rainBar]. */
fun MeteoPoint.sunBar() = HourBar(
    sunshine.takeIf { sunMeasured }, forecastSunshine ?: sunshine.takeIf { !sunMeasured },
)

/** The day's totals of the blocks and of the frames; null where there is none of them. */
fun List<MeteoPoint>.barTotals(bar: (MeteoPoint) -> HourBar) = HourBar(
    mapNotNull { bar(it).measured }.takeIf { it.isNotEmpty() }?.sum(),
    mapNotNull { bar(it).expected }.takeIf { it.isNotEmpty() }?.sum(),
)

/**
 * Draws an hour's [bar] standing on [bottom], [width] wide from [left]; [top] gives the y of an
 * amount. Amounts up to [least] are not drawn (a frame of almost nothing is a line on the axis).
 */
fun DrawScope.hourBar(look: BarLook, bar: HourBar, left: Float, width: Float, bottom: Float, corner: Float, least: Double = 0.0, top: (Double) -> Float) {
    bar.measured?.takeIf { it > least }?.let { m ->
        drawRoundRect(look.fill, Offset(left, top(m)), Size(width, bottom - top(m)), CornerRadius(corner))
    }
    bar.expected?.takeIf { it > least }?.let { e -> frame(look.frame, left, top(e), width, bottom, corner) }
}

/** An unfilled frame of a bar ([left], [top] … [bottom], [width] wide), the line inside its edges. */
private fun DrawScope.frame(color: Color, left: Float, top: Float, width: Float, bottom: Float, corner: Float) {
    val w = HourBars.FRAME_DP * density
    if (bottom - top < w) {
        // (almost) nothing expected: a thin line on the axis
        drawLine(color, Offset(left, bottom - w / 2), Offset(left + width, bottom - w / 2), w)
        return
    }
    drawRoundRect(color, Offset(left + w / 2, top + w / 2), Size(width - w, bottom - top - w), CornerRadius(corner), style = Stroke(w))
}

/**
 * The legend of one quantity's bars with the day's totals: block and frame named apart where
 * both are drawn; frames alone (a day to come) under the plain name. [notMeasured]: the hours
 * over have no reading of it at all – said so (their bars are not drawn), with the frames to come.
 */
@Composable
fun BarLegendItems(
    look: BarLook, totals: HourBar, @StringRes plain: Int, @StringRes measured: Int, @StringRes expected: Int,
    @StringRes notMeasured: Int? = null, amount: @Composable (Double) -> String,
) {
    when {
        totals.measured != null -> {
            LegendItem(look.fill, stringResource(measured, amount(totals.measured)))
            if (totals.expected != null) LegendItem(look.frame, stringResource(expected, amount(totals.expected)), frame = true)
        }
        notMeasured != null -> {
            // no block in the chart: a faint one in the legend
            LegendItem(look.fill.copy(alpha = 0.25f), stringResource(notMeasured))
            if (totals.expected != null) LegendItem(look.frame, stringResource(expected, amount(totals.expected)), frame = true)
        }
        else -> LegendItem(look.frame, stringResource(plain, amount(totals.expected ?: 0.0)), frame = true)
    }
}
