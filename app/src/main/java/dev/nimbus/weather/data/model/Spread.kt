/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/Spread.kt
 * The spread of the forecast: how far the runs of an ensemble lie apart over the next days.
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

package dev.nimbus.weather.data.model

import kotlinx.serialization.Serializable

/**
 * An ensemble's forecast for a spot, fetched at [fetchedAt]: [members] runs of the same model from
 * slightly changed starting values. Kept small – the hourly temperature as the bands the card
 * draws, each day as the runs' highs and precipitation sums (sorted, for any share of them).
 */
@Serializable
data class Spread(
    val fetchedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val members: Int,
    /** Hourly, from the hour of the fetch on. */
    val times: List<Long>,
    val lowest: List<Double?>,
    /** 10 % of the runs lie below. */
    val low: List<Double?>,
    val median: List<Double?>,
    /** 10 % of the runs lie above. */
    val high: List<Double?>,
    val highest: List<Double?>,
    val days: List<SpreadDay>,
) {
    /** The hours from the one [now] is in. */
    fun from(now: Long): Int = times.indexOfFirst { it >= now - now.mod(HOUR_MS) }.let { if (it < 0) times.size else it }

    companion object {
        const val HOUR_MS = 3_600_000L
        /** The share of the runs inside the inner band: from 10 % to 90 %. */
        const val INNER = 0.8
        /** A day counts as wet in a run from this much precipitation on. */
        const val WET_MM = 1.0

        /** The value [q] (0..1) of [sorted] values, between two of them in proportion. */
        fun quantile(sorted: List<Double>, q: Double): Double? {
            if (sorted.isEmpty()) return null
            val pos = q.coerceIn(0.0, 1.0) * (sorted.size - 1)
            val i = pos.toInt()
            val f = pos - i
            return if (i + 1 >= sorted.size) sorted[i] else sorted[i] + (sorted[i + 1] - sorted[i]) * f
        }
    }
}

/**
 * One day of the place (from its local midnight [start]): each run's highest temperature and its
 * precipitation sum, sorted. Today from the hour of the fetch on.
 */
@Serializable
data class SpreadDay(val start: Long, val highs: List<Double>, val sums: List<Double>) {
    /** The share of the runs with at least [Spread.WET_MM] on this day (null: no sums). */
    fun wetShare(): Double? = if (sums.isEmpty()) null else sums.count { it >= Spread.WET_MM } / sums.size.toDouble()
}
