/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/Insights.kt
 * Pure helper logic behind the card texts, free of Android types for unit tests.
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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.WeatherData

/** Pure helper logic behind the card texts – kept free of Android types for unit tests. */
object Insights {
    const val RAIN_THRESHOLD_MM_15 = 0.05

    sealed interface Nowcast {
        data object Dry : Nowcast
        data class StartsIn(val minutes: Int) : Nowcast
        data class StopsIn(val minutes: Int) : Nowcast
        data object Continues : Nowcast
    }

    /** 15-minute points covering the next three hours, starting with the current interval. */
    fun nowcastPoints(minutely: List<MinutelyPoint>, now: Long): List<MinutelyPoint> {
        val start = minutely.indexOfLast { it.time <= now }.coerceAtLeast(0)
        return minutely.drop(start).filter { it.time <= now + 3 * 3600_000L }
    }

    /**
     * @param rainingNow precipitation is currently observed (e.g. by the DWD station) – the
     * first interval then counts as wet even if the model has it dry.
     */
    fun nowcast(points: List<MinutelyPoint>, now: Long, rainingNow: Boolean = false): Nowcast {
        if (points.isEmpty()) return if (rainingNow) Nowcast.Continues else Nowcast.Dry
        val wet = points.mapIndexed { i, p -> p.precipitation >= RAIN_THRESHOLD_MM_15 || (i == 0 && rainingNow) }
        return if (wet.first()) {
            val stop = wet.indexOfFirst { !it }
            if (stop == -1) Nowcast.Continues else Nowcast.StopsIn(minutesUntil(points[stop].time, now))
        } else {
            val start = wet.indexOfFirst { it }
            if (start == -1) Nowcast.Dry else Nowcast.StartsIn(minutesUntil(points[start].time, now))
        }
    }

    private fun minutesUntil(t: Long, now: Long) = (((t - now) / 60_000L).toInt()).coerceAtLeast(5).let { (it + 4) / 5 * 5 }

    /** Hourly points for the next [hours] hours, the first one being the current hour. */
    fun upcomingHours(data: WeatherData, now: Long, hours: Int = 25): List<HourlyPoint> {
        val idx = data.hourly.indexOfLast { it.time <= now }.coerceAtLeast(0)
        return data.hourly.drop(idx).take(hours)
    }

    /** Groups conditions into what a person would describe as "the same weather". */
    fun group(c: Condition): Int = when (c) {
        Condition.CLEAR, Condition.MOSTLY_CLEAR -> 0
        Condition.PARTLY_CLOUDY -> 1
        Condition.CLOUDY -> 2
        Condition.FOG -> 3
        Condition.DRIZZLE, Condition.RAIN, Condition.HEAVY_RAIN, Condition.SHOWERS, Condition.FREEZING_RAIN -> 4
        Condition.SLEET, Condition.SNOW, Condition.HEAVY_SNOW -> 5
        Condition.THUNDERSTORM -> 6
    }

    data class HourlyChange(val condition: Condition, val isDay: Boolean, val time: Long?)

    /** The next change of weather within 12 hours, or the continuing condition (time == null). */
    fun nextChange(hours: List<HourlyPoint>, current: Condition): HourlyChange {
        val g = group(current)
        val next = hours.drop(1).take(12).firstOrNull { group(it.condition) != g }
        return if (next != null) HourlyChange(next.condition, next.isDay, next.time)
        else HourlyChange(current, hours.firstOrNull()?.isDay ?: true, null)
    }

    /** Chance of precipitation as shown in the forecasts, rounded to 10 %; null only without data. */
    fun chanceLabel(probability: Double?): Int? {
        val p = probability ?: return null
        return (Math.round(p / 10.0) * 10).toInt().coerceIn(0, 100)
    }

    /** Below this the value is shown dimmed: technically possible, practically dry. */
    const val CHANCE_RELEVANT = 10

    fun maxGust(hours: List<HourlyPoint>): Double? = hours.take(12).mapNotNull { it.windGust }.maxOrNull()

    enum class Trend { RISING, FALLING, STEADY }

    fun pressureTrend(hours: List<HourlyPoint>): Trend {
        val now = hours.firstOrNull()?.pressure ?: return Trend.STEADY
        val later = hours.getOrNull(3)?.pressure ?: return Trend.STEADY
        return when {
            later - now > 1.0 -> Trend.RISING
            now - later > 1.0 -> Trend.FALLING
            else -> Trend.STEADY
        }
    }

    /** Last hour today with UV >= 3, or null if UV stays low. */
    fun uvProtectUntil(hours: List<HourlyPoint>, isSameDay: (Long) -> Boolean): Long? =
        hours.filter { isSameDay(it.time) && (it.uvIndex ?: 0.0) >= 3.0 }.maxOfOrNull { it.time }

    fun next24hPrecipitation(hours: List<HourlyPoint>): Double = hours.drop(1).take(24).sumOf { it.precipitation ?: 0.0 }

    private val tempStops = listOf(
        -15.0 to Color(0xFF7B6CFF), -5.0 to Color(0xFF3F8CFF), 3.0 to Color(0xFF4FC3F7), 10.0 to Color(0xFF8BD66B),
        17.0 to Color(0xFFF7D548), 24.0 to Color(0xFFFFA23A), 31.0 to Color(0xFFFF5B36), 38.0 to Color(0xFFD9304F),
    )

    fun temperatureColor(celsius: Double): Color {
        if (celsius <= tempStops.first().first) return tempStops.first().second
        if (celsius >= tempStops.last().first) return tempStops.last().second
        val i = tempStops.indexOfFirst { it.first > celsius }
        val (t0, c0) = tempStops[i - 1]
        val (t1, c1) = tempStops[i]
        return lerp(c0, c1, ((celsius - t0) / (t1 - t0)).toFloat())
    }
}
