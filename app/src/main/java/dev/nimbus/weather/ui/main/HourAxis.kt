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
     * A point of a curve for the value at [t] (temperature, chance …): in the middle of its hour
     * as well, so curve points, bars and cursor are one column per hour. The 00:00 value lies half
     * an hour left of the plot – the curve comes in from the edge.
     */
    fun point(t: Long): Float = centre(t)

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
