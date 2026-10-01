/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/WeatherCodes.kt
 * Maps WMO weather codes and measured values to the app's weather conditions.
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

/** Maps WMO weather interpretation codes (as used by Open-Meteo) to [Condition]. */
object WeatherCodes {
    /**
     * Heavy rain from this rate on (mm/h) – the DWD's class "stark", also used by the precipitation
     * notice. The model's own "heavy" codes (65, 82) start lower; with a known rate below it they
     * become plain rain, so the header never says "heavy rain" above a notice "moderate rain".
     */
    const val HEAVY_RAIN_MM_H = 10.0

    /** Heavy snow from this rate on (mm/h water equivalent), as in the precipitation notice. */
    const val HEAVY_SNOW_MM_H = 4.0

    /** [rateMmPerHour]: precipitation rate of the same interval, if known. */
    fun fromWmo(code: Int?, rateMmPerHour: Double?): Condition {
        val c = fromWmo(code)
        return if (c == Condition.HEAVY_RAIN && rateMmPerHour != null && rateMmPerHour < HEAVY_RAIN_MM_H) Condition.RAIN else c
    }

    fun fromWmo(code: Int?): Condition = when (code) {
        null -> Condition.CLOUDY
        0 -> Condition.CLEAR
        1 -> Condition.MOSTLY_CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        51, 53, 55 -> Condition.DRIZZLE
        56, 57, 66, 67 -> Condition.FREEZING_RAIN
        61, 63 -> Condition.RAIN
        65 -> Condition.HEAVY_RAIN
        71, 73, 77 -> Condition.SNOW
        75 -> Condition.HEAVY_SNOW
        80, 81 -> Condition.SHOWERS
        82 -> Condition.HEAVY_RAIN
        85 -> Condition.SNOW
        86 -> Condition.HEAVY_SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.CLOUDY
    }

    /** WMO 96/99: thunderstorm with hail (forecast for Central Europe). */
    fun isHail(code: Int?): Boolean = code == 96 || code == 99

    /**
     * Derives a condition from raw parameters, used for sources that deliver no
     * weather code (e.g. DWD station data).
     */
    fun derive(
        cloudCover: Double?,
        precipitationMmPerHour: Double?,
        temperature: Double?,
        thunder: Boolean = false,
        fog: Boolean = false,
    ): Condition {
        val p = precipitationMmPerHour ?: 0.0
        val cold = (temperature ?: 10.0) <= 0.5
        val sleety = (temperature ?: 10.0) in 0.5..2.0
        if (thunder) return Condition.THUNDERSTORM
        if (p >= 0.1) {
            return when {
                cold && p >= HEAVY_SNOW_MM_H -> Condition.HEAVY_SNOW
                cold -> Condition.SNOW
                sleety -> Condition.SLEET
                p >= HEAVY_RAIN_MM_H -> Condition.HEAVY_RAIN
                p < 0.3 -> Condition.DRIZZLE
                else -> Condition.RAIN
            }
        }
        if (fog) return Condition.FOG
        val c = cloudCover ?: 50.0
        return when {
            c < 12.5 -> Condition.CLEAR
            c < 37.5 -> Condition.MOSTLY_CLEAR
            c < 75.0 -> Condition.PARTLY_CLOUDY
            else -> Condition.CLOUDY
        }
    }
}
