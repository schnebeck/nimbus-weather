/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/TodayMeasured.kt
 * Today's station readings (DWD via Bright Sky) for the day charts: measured next to forecast.
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

import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryHour
import java.time.LocalDate

/**
 * Hourly readings of the nearest DWD station for today, keyed by the hour's time stamp (sums
 * cover the hour before it, like the model values): precipitation in mm, pressure in hPa, and
 * all readings of the hour in [hours].
 */
data class TodayMeasured(
    val precipitation: Map<Long, Double>,
    val pressure: Map<Long, Double>,
    val station: String?,
    val hours: Map<Long, HistoryHour.Measured> = emptyMap(),
) {
    /**
     * The 0–24 h charts show what was measured for the hours already over, the forecast only for
     * the rest of the day (the comparison of the two is in the look-back): [p] with the station's
     * readings in place of the model values, unchanged for hours to come or without a reading.
     */
    fun apply(p: MeteoPoint, now: Long): MeteoPoint {
        if (p.time > now) return p
        // An hour over without a precipitation reading shows none (its forecast is not what fell)
        val m = hours[p.time] ?: return if (precipitation.isNotEmpty()) p.copy(precipitation = null) else p
        val t = m.temperature
        return p.copy(
            temperature = t ?: p.temperature,
            apparentTemperature = if (t != null) p.apparentTemperature?.plus(t - p.temperature) else p.apparentTemperature,
            condition = m.condition ?: p.condition,
            precipitation = m.precipitation ?: if (precipitation.isNotEmpty()) null else p.precipitation,
            // the chance stays as it was forecast
            windSpeed = m.windSpeed ?: p.windSpeed,
            windDirection = m.windDirection ?: p.windDirection,
            windGust = m.windGust ?: p.windGust,
            sunshine = m.sunshineMinutes ?: p.sunshine,
            measured = true,
        )
    }

    companion object {
        fun of(history: History?, today: LocalDate): TodayMeasured? {
            val day = history?.days?.firstOrNull { it.date == today } ?: return null
            val precip = day.hours.mapNotNull { h -> h.measured?.precipitation?.let { h.time to it } }.toMap()
            val pressure = day.hours.mapNotNull { h -> h.measured?.pressure?.let { h.time to it } }.toMap()
            val hours = day.hours.mapNotNull { h -> h.measured?.let { h.time to it } }.toMap()
            if (hours.isEmpty()) return null
            return TodayMeasured(precip, pressure, history.stationName, hours)
        }
    }
}
