/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/MeteoPoint.kt
 * One hour of a day chart, measured against forecast, and the nights of a day.
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
import dev.nimbus.weather.data.model.HourlyPoint

/**
 * One hour of a meteogram. For the look back [temperature] etc. are measurements and
 * [forecastTemperature] / [forecastPrecipitation] what the model had predicted.
 */
data class MeteoPoint(
    val time: Long,
    val temperature: Double,
    val condition: Condition,
    val isDay: Boolean,
    val precipitation: Double?,
    val precipitationChance: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGust: Double? = null,
    val humidity: Double? = null,
    val apparentTemperature: Double? = null,
    val forecastTemperature: Double? = null,
    val forecastPrecipitation: Double? = null,
    /** Minutes of sunshine in the hour before [time]. */
    val sunshine: Double? = null,
    /** Look-back of today: an hour still to come – only the forecast, drawn dashed and paler. */
    val forecastOnly: Boolean = false,
    /** Today in the forecast: an hour already over, with the station's readings in place of the forecast. */
    val measured: Boolean = false,
    /** Look-back: what was measured and what was forecast, kept apart for the readout table. */
    val compare: HourCompare? = null,
    /** [precipitation] is a reading (a block in the chart); else the forecast (a frame). */
    val precipMeasured: Boolean = false,
    /** [sunshine] is a reading; else the forecast. */
    val sunMeasured: Boolean = false,
    /** Look-back: the forecast sunshine beside the measured (a frame in front of its block). */
    val forecastSunshine: Double? = null,
)

/** One hour of the look-back: measured (M) and forecast (F) values; null where there is none. */
data class HourCompare(
    val tempM: Double?, val tempF: Double?,
    val precipM: Double?, val precipF: Double?, val chanceF: Double?,
    val windM: Double?, val windDirM: Double?, val windF: Double?,
    val gustM: Double?, val gustF: Double?,
    val sunM: Double?, val sunF: Double?,
    /** Today's chart: the forecast's feels-like temperature and humidity (rows of their own). */
    val feelsF: Double? = null, val humidityF: Double? = null,
)

/**
 * The hour running at [now] (its values cover the hour before its time stamp) with the weather now
 * [condition] – the header's: now the measurement decides, the hours to come the forecast.
 */
fun MeteoPoint.asNow(now: Long, condition: Condition?): MeteoPoint =
    if (condition != null && time > now && time - now <= 3_600_000L) copy(condition = condition) else this

fun HourlyPoint.toMeteo() = MeteoPoint(
    time, temperature, condition, isDay, precipitation, precipitationProbability,
    windSpeed, windDirection, windGust, humidity, apparentTemperature, sunshine = sunshine,
)

/**
 * Night between [start] and [end] at the place: wherever the sun is below the horizon (its upper
 * limb with refraction, −0.833° – sunrise and sunset as on the sun card), found every 5 minutes and
 * interpolated to the minute. One source for every day chart, across the whole axis including the
 * 24 column; also right on polar days and nights. (The hourly day/night flags of the models put
 * sunrise and sunset on full hours; the daily sunrise/sunset ended at midnight.)
 */
fun nights(start: Long, end: Long, lat: Double, lon: Double): List<LongRange> {
    if (end <= start) return emptyList()
    val step = 5 * 60_000L
    fun alt(t: Long) = dev.nimbus.weather.util.Moon.sunAltitude(t, lat, lon) + 0.833
    val out = ArrayList<LongRange>()
    var t = start
    var a = alt(t)
    var nightFrom: Long? = if (a < 0) start else null
    while (t < end) {
        val n = minOf(t + step, end)
        val b = alt(n)
        if ((a < 0) != (b < 0)) {
            val edge = t + ((n - t) * (a / (a - b))).toLong()
            if (b < 0) nightFrom = edge else { out += nightFrom!! until edge; nightFrom = null }
        }
        t = n; a = b
    }
    nightFrom?.let { out += it until end }
    return out
}
