/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/LookBackPoints.kt
 * The hours of a look-back day as meteogram points: measurement against forecast.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay

/**
 * The hours of [day] (starting at [start]) from 00:00 through the 24 column: the station's
 * readings where there are some, the forecast beside them. With precipitation readings the bars
 * show what fell, the forecast as frames in front; an hour without a reading only its forecast.
 * Sunshine likewise, hour by hour.
 */
fun History.lookBackPoints(day: HistoryDay, start: Long): List<MeteoPoint> {
    val measuredRain = day.hours.any { it.measured?.precipitation != null }
    return chartHours(start).mapNotNull { h ->
        val m = h.measured
        val f = h.model
        // No reading (yet): the forecast only, dashed – never the forecast drawn as measured
        if (m?.temperature == null) {
            val ft = f?.temperature ?: return@mapNotNull null
            return@mapNotNull MeteoPoint(
                time = h.time, temperature = ft, condition = f.condition, isDay = f.isDay,
                precipitation = if (measuredRain) null else f.precipitation,
                forecastPrecipitation = if (measuredRain) f.precipitation else null, windSpeed = f.windSpeed, windDirection = f.windDirection, windGust = f.windGust,
                forecastTemperature = ft, sunshine = f.sunshineMinutes, forecastOnly = true,
                compare = HourCompare(
                    null, ft, null, f.precipitation, f.chance, null, null, f.windSpeed, null, f.windGust, null, f.sunshineMinutes,
                ),
            )
        }
        val sunMeasured = m.sunshineMinutes != null
        MeteoPoint(
            time = h.time,
            temperature = m.temperature,
            condition = m.condition ?: f?.condition ?: Condition.CLOUDY,
            isDay = f?.isDay ?: true,
            precipitation = if (measuredRain) m.precipitation else f?.precipitation,
            windSpeed = m.windSpeed ?: f?.windSpeed,
            windDirection = m.windDirection ?: f?.windDirection,
            windGust = m.windGust ?: f?.windGust,
            forecastTemperature = f?.temperature,
            forecastPrecipitation = if (measuredRain) f?.precipitation else null,
            sunshine = m.sunshineMinutes ?: f?.sunshineMinutes,
            // the readout table shows both apart, an empty cell where one is missing
            compare = HourCompare(
                m.temperature, f?.temperature, m.precipitation, f?.precipitation, f?.chance,
                m.windSpeed, m.windDirection, f?.windSpeed, m.windGust, f?.windGust, m.sunshineMinutes, f?.sunshineMinutes,
            ),
            precipMeasured = measuredRain && m.precipitation != null,
            sunMeasured = sunMeasured,
            forecastSunshine = if (sunMeasured) f?.sunshineMinutes else null,
        )
    }
}
