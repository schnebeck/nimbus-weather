/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/DaySummary.kt
 * Summary numbers of a past day: measured values win, the model fills gaps.
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

package dev.nimbus.weather.data.remote

import dev.nimbus.weather.data.model.Condition

/** Summary numbers of a past day; measured values win, the model fills gaps. */
data class DaySummary(
    val tempMax: Double?, val tempMin: Double?, val precipitation: Double?, val sunshineHours: Double?,
    val maxGust: Double?, val maxGustAt: Long?, val meanWind: Double?, val condition: Condition,
    val modelTempMax: Double?, val modelTempMin: Double?, val modelPrecipitation: Double?,
    /** Mean absolute difference model − measurement of the hourly temperature. */
    val tempError: Double?,
    val measured: Boolean,
) {
    companion object {
        /** Summary of the hours up to [now] (today's forecast for the rest of the day is left out). */
        fun of(day: HistoryDay, now: Long = Long.MAX_VALUE): DaySummary {
            val h = day.hours.filter { it.time <= now }
            val mt = h.mapNotNull { it.measured?.temperature }
            val measured = mt.size >= h.size / 2 && mt.isNotEmpty()
            fun pick(m: (HistoryHour) -> Double?, mod: (HistoryHour) -> Double?) = h.mapNotNull { m(it) ?: mod(it) }
            val temps = pick({ it.measured?.temperature }, { it.model?.temperature })
            val gusts = h.mapNotNull { hh -> (hh.measured?.windGust ?: hh.model?.windGust)?.let { hh.time to it } }
            val errors = h.mapNotNull { hh ->
                val a = hh.measured?.temperature; val b = hh.model?.temperature
                if (a != null && b != null) kotlin.math.abs(a - b) else null
            }
            val precip = pick({ it.measured?.precipitation }, { it.model?.precipitation })
            val sun = pick({ it.measured?.sunshineMinutes }, { it.model?.sunshineMinutes })
            return DaySummary(
                tempMax = temps.maxOrNull(), tempMin = temps.minOrNull(),
                precipitation = precip.takeIf { it.isNotEmpty() }?.sum(),
                sunshineHours = sun.takeIf { it.isNotEmpty() }?.sum()?.div(60.0),
                maxGust = gusts.maxByOrNull { it.second }?.second, maxGustAt = gusts.maxByOrNull { it.second }?.first,
                meanWind = pick({ it.measured?.windSpeed }, { it.model?.windSpeed }).takeIf { it.isNotEmpty() }?.average(),
                condition = dominant(h),
                modelTempMax = h.mapNotNull { it.model?.temperature }.maxOrNull(),
                modelTempMin = h.mapNotNull { it.model?.temperature }.minOrNull(),
                modelPrecipitation = h.mapNotNull { it.model?.precipitation }.takeIf { it.isNotEmpty() }?.sum(),
                tempError = errors.takeIf { it.size >= 3 }?.average(),
                measured = measured,
            )
        }

        /** Most characteristic daytime weather: precipitation beats clouds if it lasted ≥ 2 h. */
        fun dominant(hours: List<HistoryHour>): Condition {
            val conds = hours.filter { it.model?.isDay != false }.mapNotNull { it.measured?.condition ?: it.model?.condition }
                .ifEmpty { hours.mapNotNull { it.measured?.condition ?: it.model?.condition } }
            if (conds.isEmpty()) return Condition.CLOUDY
            val precip = conds.filter { it.isPrecipitation }
            if (precip.size >= 2) return precip.groupingBy { it }.eachCount().maxBy { it.value }.key
            return conds.groupingBy { it }.eachCount().maxBy { it.value }.key
        }
    }
}
