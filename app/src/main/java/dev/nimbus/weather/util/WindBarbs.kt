/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/WindBarbs.kt
 * Wind barbs: the wind's strength in strokes at the end of a shaft pointing where it comes from.
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

package dev.nimbus.weather.util

import kotlin.math.roundToInt

/**
 * The barbs of the weather maps (WMO): the speed in knots rounded to 5 – a pennant for 50, a full
 * stroke for 10, a half one for 5; calm (under 2.5 kn) a circle. The shaft points to where the
 * wind comes from, the strokes at its far end on the side to the right of it (looking along it
 * from the station) – where the low lies in the northern hemisphere.
 */
object WindBarbs {
    data class Parts(val knots: Int, val pennants: Int, val fulls: Int, val half: Boolean) {
        val calm: Boolean get() = knots == 0
    }

    /** A stroke or a pennant's edge, from ([x1], [y1]) to ([x2], [y2]), in [shape]'s frame. */
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /**
     * The barb in a frame where the station is at (0, 0) and the shaft one long up to (0, −1):
     * the strokes going right (+x). Pennants as triangles of three corners each.
     */
    data class Shape(val calm: Boolean, val shaft: Line?, val strokes: List<Line>, val pennants: List<List<Pair<Float, Float>>>)

    /** The length of a full stroke, of the shaft's. */
    const val STROKE = 0.42f
    /** From one stroke to the next along the shaft. */
    private const val STEP = 0.15f
    /** How far a stroke's outer end leans to the shaft's end. */
    private const val LEAN = 0.14f

    fun parts(kmh: Double): Parts {
        val knots = ((kmh / 1.852) / 5).roundToInt() * 5
        return Parts(knots, knots / 50, knots % 50 / 10, knots % 10 == 5)
    }

    fun shape(kmh: Double): Shape {
        val p = parts(kmh)
        if (p.calm) return Shape(true, null, emptyList(), emptyList())
        val strokes = mutableListOf<Line>()
        val pennants = mutableListOf<List<Pair<Float, Float>>>()
        var y = -1f
        repeat(p.pennants) {
            pennants += listOf(0f to y, STROKE to y - LEAN / 2, 0f to y + STEP)
            y += STEP + STEP / 3
        }
        repeat(p.fulls) {
            strokes += Line(0f, y, STROKE, y - LEAN)
            y += STEP
        }
        if (p.half) {
            // alone it stands back from the end – an end without a stroke tells it from a full one
            if (p.pennants == 0 && p.fulls == 0) y += STEP
            strokes += Line(0f, y, STROKE / 2, y - LEAN / 2)
        }
        return Shape(false, Line(0f, 0f, 0f, -1f), strokes, pennants)
    }
}
