/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HourAxis.kt
 * Where the hourly bars and the long-press cursor of a day chart go – one geometry for both.
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
 * dp – the same unit throughout). An hourly value covers the hour before its time stamp (as the
 * models and stations deliver it), so its bar stands on that hour – and the cursor of the hour
 * stands in the middle of the bar, and so do the curve points of the hour. Bars, curve points and
 * cursor take their positions only from here; the charts' drawing and touch handling must not
 * compute their own (they drifted apart before, more than once).
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
     * A point of a curve for the value at [t] standing for the [interval] before it: in the middle
     * of that interval – an hourly value in the middle of its hour (its bar's column, under the
     * cursor), a 15-minute value in the middle of its quarter hour, a 10-minute one in the middle
     * of its 10 minutes. The 00:00 value lies left of the plot – the curve comes in from the edge.
     */
    fun point(t: Long, interval: Long = HOUR): Float = x(t - interval / 2)

    /** The hour ([times] index) whose bar is nearest to [pos]; only hours within the axis count. */
    fun indexAt(pos: Float, times: List<Long>): Int {
        val inside = times.indices.filter { times[it] > start && times[it] <= end }
        if (inside.isEmpty()) return 0
        return inside.minBy { abs(centre(times[it]) - pos) }
    }

    companion object {
        const val HOUR = 3_600_000L
        const val BAR_SHARE = 0.72f
    }
}

/** A value of a curve at [time], standing for the [interval] before it (see [HourAxis.point]). */
data class CurvePoint(val time: Long, val value: Double, val interval: Long = HourAxis.HOUR) {
    /** The moment the point is drawn at: the middle of its interval. */
    val at: Long get() = time - interval / 2
}

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
        points.sortedBy { it.at }.forEach { p ->
            val last = out.lastOrNull()?.lastOrNull()
            if (last == null || p.at - last.at > maxOf(2 * maxOf(p.interval, last.interval), BRIDGE_MS)) out += mutableListOf(p) else out.last() += p
        }
        return out
    }

    /** The curve's value at moment [t] (linear between its points), null outside a segment. */
    fun at(points: List<CurvePoint>, t: Long): Double? {
        for (seg in segments(points)) {
            if (seg.size == 1) { if (abs(seg[0].at - t) <= seg[0].interval / 2) return seg[0].value; continue }
            if (t < seg.first().at || t > seg.last().at) continue
            val i = seg.indexOfLast { it.at <= t }.coerceIn(0, seg.size - 2)
            val a = seg[i]; val b = seg[i + 1]
            val f = if (b.at == a.at) 0.0 else (t - a.at).toDouble() / (b.at - a.at)
            return a.value + (b.value - a.value) * f
        }
        return null
    }

    /**
     * Fine points where there are some, the coarse ones elsewhere: [coarse] points falling inside
     * the span of [fine] are dropped (e.g. 15-minute model values for two days, hourly after that).
     */
    fun merge(fine: List<CurvePoint>, coarse: List<CurvePoint>): List<CurvePoint> {
        if (fine.isEmpty()) return coarse
        val lo = fine.minOf { it.time }; val hi = fine.maxOf { it.time }
        return (fine + coarse.filter { it.time < lo || it.time > hi }).sortedBy { it.at }
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
     * Look-back: the forecast bar is drawn in front of the measured one when it is smaller (it
     * would be hidden behind it), behind it when it is as large or larger.
     */
    fun forecastInFront(forecast: Double, measured: Double?): Boolean = measured != null && forecast < measured
}
