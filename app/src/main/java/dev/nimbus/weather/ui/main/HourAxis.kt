/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HourAxis.kt
 * Where the hourly bars, the long-press cursor, the curves and the "now" line of a day chart go.
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

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * The time axis of an hourly chart from [start] to [end], drawn from [left] to [right] (pixels or
 * dp – the same unit throughout). Its labels are a clock: the "12" stands at 12:00 ([clock]).
 * - Moments – the temperatures, "now" – stand at their time on that clock: the 13:00 temperature
 *   under the 13, 12:09 just right of the 12.
 * - An hour's sums (precipitation, sunshine, the chance) cover the hour before their time stamp
 *   (as the models and stations deliver them); the bar of 12–13 stands under the 12, from 11:30
 *   to 12:30 on the clock – and so does the cursor of that hour, on the full hour.
 * Bars, curves, cursor and "now" take their positions only from here; the charts' drawing and
 * touch handling must not compute their own (they drifted apart before, more than once).
 */
class HourAxis(private val start: Long, private val end: Long, val left: Float, val right: Float) {
    private val span = (end - start).toFloat()

    /** Position of the moment [t]. */
    fun x(t: Long): Float = left + (right - left) * ((t - start) / span)

    /** Width of one hour. */
    val hourWidth: Float get() = (right - left) / (span / HOUR)

    /** Middle of the hour before [t] – the centre of its bar and the place of its cursor. */
    fun centre(t: Long): Float = x(t - HOUR / 2)

    /** Left edge and width of the bar of the hour before [t], [share] of the hour wide. */
    fun barLeft(t: Long, share: Float = BAR_SHARE): Float = centre(t) - hourWidth * share / 2
    fun barWidth(share: Float = BAR_SHARE): Float = hourWidth * share

    /** The cursor of the hour before [t]. */
    fun cursor(t: Long): Float = centre(t)

    /**
     * A point of a line of the hour's values (the chance of precipitation of the hour before [t]):
     * in its bar's column, under the cursor.
     */
    fun point(t: Long, interval: Long = HOUR): Float = x(t - interval / 2)

    /**
     * The moment [t] on the clock of the labels: a curve's point (the temperature at [t]), the
     * "now" line. The 00:00 value stands under the 00, the one of 24:00 under the 24; the hour
     * before 00:00 lies left of the plot – the curve comes in from the edge.
     */
    fun clock(t: Long): Float = x(t + HOUR / 2)

    /**
     * The time label of the full hour [hourStart] ("03" at 03:00): on the clock – right over the
     * bar and the cursor of the hour starting there, so the label names what the cursor shows.
     */
    fun label(hourStart: Long): Float = clock(hourStart)

    /**
     * Starts of the labelled hours: every [every] hours from the axis start (00, 03 … 21; with the
     * day's axis running into the next day's first hour, 24 as well – see [dayAxisEnd]).
     */
    fun labelHours(every: Int = 3): List<Long> =
        generateSequence(start) { it + every * HOUR }.takeWhile { it < end }.toList()

    /** The hour ([times] index) whose bar is nearest to [pos]; only hours within the axis count. */
    fun indexAt(pos: Float, times: List<Long>, last: Long = end): Int {
        val inside = times.indices.filter { times[it] > start && times[it] <= minOf(end, last) }
        if (inside.isEmpty()) return 0
        return inside.minBy { abs(centre(times[it]) - pos) }
    }

    companion object {
        const val HOUR = 3_600_000L

        /**
         * A day chart's axis ends an hour after midnight: the 24 gets a column of its own like the
         * 00 – label in the middle, its bar where there is one, the curves running up to its
         * middle. The day's totals stay 00–24.
         */
        fun dayAxisEnd(dayEnd: Long) = dayEnd + HOUR
        const val BAR_SHARE = 0.72f
    }
}

/**
 * A value of a curve at the moment [time] (drawn there, see [HourAxis.clock]); [interval]: the step
 * of its series (an hour, 15 minutes, a station's 10 minutes) – how far apart its points may lie.
 */
data class CurvePoint(val time: Long, val value: Double, val interval: Long = HourAxis.HOUR)

/** Curves of a day chart from points of any resolution – the finer the data, the more points. */
object Curve {
    /** Gaps up to this long are bridged in any resolution (the newest station report often comes before the ones in between). */
    private const val BRIDGE_MS = 40 * 60_000L

    /**
     * Points in order, split where data is missing: a gap longer than twice the coarser of the two
     * intervals (and than [BRIDGE_MS]) ends a segment – a station report missing for an hour is
     * not bridged by a line.
     */
    fun segments(points: List<CurvePoint>): List<List<CurvePoint>> {
        val out = ArrayList<MutableList<CurvePoint>>()
        points.sortedBy { it.time }.forEach { p ->
            val last = out.lastOrNull()?.lastOrNull()
            if (last == null || p.time - last.time > maxOf(2 * maxOf(p.interval, last.interval), BRIDGE_MS)) out += mutableListOf(p) else out.last() += p
        }
        return out
    }

    /** The curve's value at moment [t] (linear between its points), null outside a segment. */
    fun at(points: List<CurvePoint>, t: Long): Double? {
        for (seg in segments(points)) {
            if (seg.size == 1) { if (abs(seg[0].time - t) <= seg[0].interval / 2) return seg[0].value; continue }
            if (t < seg.first().time || t > seg.last().time) continue
            val i = seg.indexOfLast { it.time <= t }.coerceIn(0, seg.size - 2)
            val a = seg[i]; val b = seg[i + 1]
            val f = if (b.time == a.time) 0.0 else (t - a.time).toDouble() / (b.time - a.time)
            return a.value + (b.value - a.value) * f
        }
        return null
    }

    /** Readings this fine (the station's 10-minute reports) are drawn as a mean over [SMOOTH_MS]. */
    const val SMOOTH_UP_TO_MS = 10 * 60_000L
    const val SMOOTH_MS = 30 * 60_000L

    /**
     * The curve as drawn: the station's 10-minute reports are moments – every cloud shadow and
     * warm gust shows as a jump of a degree or more; drawn is the mean of the reports within
     * ±[SMOOTH_MS]/2 (three reports), the course without the flicker. Coarser points stay.
     */
    fun smoothed(points: List<CurvePoint>): List<CurvePoint> {
        val s = points.sortedBy { it.time }
        return s.mapIndexed { i, p ->
            if (p.interval > SMOOTH_UP_TO_MS) return@mapIndexed p
            var sum = 0.0; var n = 0
            var j = i
            while (j >= 0 && p.time - s[j].time <= SMOOTH_MS / 2) { if (s[j].interval <= SMOOTH_UP_TO_MS) { sum += s[j].value; n++ }; j-- }
            j = i + 1
            while (j < s.size && s[j].time - p.time <= SMOOTH_MS / 2) { if (s[j].interval <= SMOOTH_UP_TO_MS) { sum += s[j].value; n++ }; j++ }
            p.copy(value = sum / n)
        }
    }

    /** How long the forecast takes to close the gap to the last reading. */
    const val JOIN_MS = 3 * 3_600_000L

    /**
     * [readings] and the [forecast] after them, the forecast starting at the last reading: the
     * station and the model differ (the station is up to 20 km away, the model a grid value) –
     * without this the curve jumped at "now". The difference fades out over [JOIN_MS]; it is taken
     * from the drawn (smoothed) readings, not from a single report.
     */
    fun joined(readings: List<CurvePoint>, forecast: List<CurvePoint>): List<CurvePoint> {
        if (readings.isEmpty()) return forecast
        val last = readings.maxBy { it.time }
        val after = forecast.filter { it.time > last.time }
        val anchor = smoothed(readings).maxBy { it.time }
        val model = at(forecast, anchor.time) ?: return readings + after
        val d = anchor.value - model
        return readings + after.map { p ->
            val k = 1.0 - (p.time - anchor.time).toDouble() / JOIN_MS
            if (k <= 0.0) p else p.copy(value = p.value + d * k)
        }
    }

    /**
     * Fine points where there are some, the coarse ones elsewhere: [coarse] points falling inside
     * the span of [fine] are dropped (e.g. 15-minute model values for two days, hourly after that).
     */
    fun merge(fine: List<CurvePoint>, coarse: List<CurvePoint>): List<CurvePoint> {
        if (fine.isEmpty()) return coarse
        val lo = fine.minOf { it.time }; val hi = fine.maxOf { it.time }
        return (fine + coarse.filter { it.time < lo || it.time > hi }).sortedBy { it.time }
    }
}

/** Precipitation bars of the day charts – the colours of the course of the day everywhere. */
object PrecipStyle {
    /** An amount on its own (forecast days) and a measured one: the meteogram's light blue. */
    val Bar = Color(0xB38FD3FF)
    /** Measured amount (DWD station). */
    val Measured = Bar
    /** Forecast next to measurements (today, look-back): a muted blue, opaque – no blending. */
    val Forecast = Color(0xFF6F89A0)

    /**
     * Look-back: the forecast as an unfilled frame of the bar's size, a thin line drawn in front –
     * the measured bar stays one colour, and the frame shows the forecast's height above, on or
     * inside it. Warm amber: it stands out from the light blue-grey bar as from the dark glass.
     */
    val ForecastFrame = Color(0xFFFFB547)
    const val FRAME_DP = 1f

    /** Draws the forecast frame of a bar ([left], [top] … [bottom], [width] wide). */
    fun androidx.compose.ui.graphics.drawscope.DrawScope.forecastFrame(left: Float, top: Float, width: Float, bottom: Float) {
        val w = FRAME_DP * density
        if (bottom - top < w) {
            // (almost) nothing forecast: a thin line on the axis
            drawLine(ForecastFrame, androidx.compose.ui.geometry.Offset(left, bottom - w / 2), androidx.compose.ui.geometry.Offset(left + width, bottom - w / 2), w)
            return
        }
        drawRoundRect(
            ForecastFrame, androidx.compose.ui.geometry.Offset(left + w / 2, top + w / 2),
            androidx.compose.ui.geometry.Size(width - w, bottom - top - w), androidx.compose.ui.geometry.CornerRadius(2f * density),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
        )
    }
}
