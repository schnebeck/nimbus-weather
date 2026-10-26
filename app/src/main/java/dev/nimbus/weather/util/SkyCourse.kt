/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/SkyCourse.kt
 * The course of the sun or the moon over a day: altitude and direction, and when it stands in
 * each of the eight directions.
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

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

object SkyCourse {
    /** Where a body stands at [time]: [altitude] above the horizon and [azimuth] from north, clockwise (both in degrees). */
    data class Point(val time: Long, val altitude: Double, val azimuth: Double)

    /** The body stands in [direction] (0 = N, 1 = NE … 7 = NW) at [time], at [altitude]. */
    data class Crossing(val time: Long, val direction: Int, val altitude: Double) {
        /** N, E, S, W – kept before the directions between them where the labels have no room. */
        val main get() = direction % 2 == 0
    }

    private const val STEP = 5 * 60_000L

    /** The sun's course from [from] to [to] in 5-minute steps (its centre, without refraction). */
    fun sun(from: Long, to: Long, lat: Double, lon: Double): List<Point> = times(from, to).map {
        Point(it, Moon.sunAltitude(it, lat, lon), Moon.sunAzimuth(it, lat, lon))
    }

    /** The moon's course from [from] to [to] in 5-minute steps (with refraction, as its rise and set). */
    fun moon(from: Long, to: Long, lat: Double, lon: Double): List<Point> = times(from, to).map {
        Point(it, Math.toDegrees(Moon.altitude(it, lat, lon)), Moon.azimuth(it, lat, lon))
    }

    private fun times(from: Long, to: Long) = generateSequence(from) { it + STEP }.takeWhile { it <= to }.toList()

    /**
     * The moon's highest altitude at a place: the sun's at midsummer plus the 5° its orbit is
     * tilted against the sun's path – the top of a scale fixed for the place.
     */
    fun moonMaxAltitude(latitude: Double): Double = minOf(90.0, 90.0 - abs(latitude) + 23.44 + 5.15)

    /**
     * When the body passes each of the eight directions, in time order. The direction is followed
     * across north (359° → 1°), either way round – the sun runs from east to west, but where it
     * passes near the zenith its direction turns fast.
     */
    fun crossings(course: List<Point>): List<Crossing> {
        val out = mutableListOf<Crossing>()
        for (i in 1 until course.size) {
            val a = course[i - 1]; val b = course[i]
            val turn = ((b.azimuth - a.azimuth + 540.0) % 360.0) - 180.0
            if (turn == 0.0) continue
            val lo = minOf(a.azimuth, a.azimuth + turn); val hi = maxOf(a.azimuth, a.azimuth + turn)
            // each direction once: a sample lying exactly on it counts for the step it ends
            var m = ceil(lo / 45.0).toInt()
            while (m <= floor(hi / 45.0).toInt()) {
                val deg = m * 45.0
                val f = (deg - a.azimuth) / turn
                if (f > 0.0 && f <= 1.0 || (i == 1 && f == 0.0)) {
                    out += Crossing(
                        a.time + ((b.time - a.time) * f).toLong(), ((m % 8) + 8) % 8,
                        a.altitude + (b.altitude - a.altitude) * f,
                    )
                }
                m++
            }
        }
        return out
    }

    /**
     * The labels that have room on the axis: the main directions first, then the ones between,
     * each where it keeps [gap] to the labels already placed; [x] gives a crossing's place and
     * [width] its label's width.
     */
    fun <T> spread(items: List<T>, main: (T) -> Boolean, x: (T) -> Float, width: (T) -> Float, gap: Float): List<T> {
        val kept = mutableListOf<T>()
        for (it in items.sortedBy { if (main(it)) 0 else 1 }) {
            if (kept.all { k -> abs(x(k) - x(it)) >= (width(k) + width(it)) / 2 + gap }) kept += it
        }
        return kept.sortedBy(x)
    }

    /** The 16-point compass name's index (0 = N, 1 = NNE … 15 = NNW) of [degrees] from north. */
    fun index16(degrees: Double): Int = ((((degrees % 360) + 360) % 360 + 11.25) / 22.5).toInt() % 16
}
