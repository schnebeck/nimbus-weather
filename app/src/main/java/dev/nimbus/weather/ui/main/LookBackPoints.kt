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
 * The hours of [day] (starting at [start]) from 00:00 through the 24 column, each quantity on its
 * own: what was measured (station; precipitation and sunshine over the place by radar and
 * satellite) as a block with the forecast's frame in front – what was not, the forecast's frame
 * alone. An hour without a temperature reading (yet) is drawn as the forecast, dashed.
 */
fun History.lookBackPoints(day: HistoryDay, start: Long): List<MeteoPoint> = chartHours(start).mapNotNull { h ->
    val m = h.measured
    val f = h.model
    val temp = m?.temperature ?: f?.temperature ?: return@mapNotNull null
    val rain = m?.precipitation
    val sun = m?.sunshineMinutes
    MeteoPoint(
        time = h.time,
        temperature = temp,
        condition = m?.condition ?: f?.condition ?: Condition.CLOUDY,
        isDay = f?.isDay ?: true,
        precipitation = rain ?: f?.precipitation,
        windSpeed = m?.windSpeed ?: f?.windSpeed,
        windDirection = m?.windDirection ?: f?.windDirection,
        windGust = m?.windGust ?: f?.windGust,
        forecastTemperature = f?.temperature,
        forecastPrecipitation = f?.precipitation.takeIf { rain != null },
        sunshine = sun ?: f?.sunshineMinutes,
        forecastOnly = m?.temperature == null,
        // the readout table shows both apart, an empty cell where one is missing
        compare = HourCompare(
            m?.temperature, f?.temperature, rain, f?.precipitation, f?.chance,
            m?.windSpeed, m?.windDirection, f?.windSpeed, m?.windGust, f?.windGust, sun, f?.sunshineMinutes,
        ),
        precipMeasured = rain != null,
        sunMeasured = sun != null,
        forecastSunshine = f?.sunshineMinutes.takeIf { sun != null },
    )
}
