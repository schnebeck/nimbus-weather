/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/DayParts.kt
 * A look-back day in parts – early, morning, forenoon, afternoon, evening, night.
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
import dev.nimbus.weather.data.model.WeatherCodes
import java.time.Instant
import java.time.ZoneId

/** Parts of a day, from the hour [from] to the hour [to] (local time). */
enum class DayPart(val from: Int, val to: Int) {
    EARLY(0, 6), MORNING(6, 9), FORENOON(9, 12), AFTERNOON(12, 18), EVENING(18, 22), NIGHT(22, 24)
}

/** The weather of a [DayPart]: what it was like, by day or by night, and how much fell. */
data class DayPartWeather(val part: DayPart, val condition: Condition, val isDay: Boolean, val precipitation: Double?)

/**
 * A look-back day in parts – early, morning, forenoon, afternoon, evening, night – instead of one
 * condition for the whole day (a rainy night and a sunny afternoon made it "drizzle").
 */
object DayParts {
    /** An hour counts as wet from this amount (mm); a part as rainy when at least half its hours are. */
    private const val WET_MM = 0.1
    /** Less than this in all the wet hours of a part is nothing to speak of. */
    private const val SOME_MM = 0.2
    private val bySky = listOf(Condition.CLEAR, Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY, Condition.CLOUDY)

    /**
     * The parts of [day] that have begun by [now], each from its hours over by then (measured, or
     * the model where nothing was measured): the list grows in the course of the day. Each hour
     * covers the hour before its time stamp.
     */
    fun of(day: HistoryDay, zone: ZoneId, now: Long): List<DayPartWeather> {
        val done = day.hours.filter { it.time <= now && (it.measured != null || it.model != null) }
        return DayPart.entries.mapNotNull { part ->
            val hours = done.filter { h ->
                val startHour = Instant.ofEpochMilli(h.time - 3_600_000L).atZone(zone)
                startHour.toLocalDate() == day.date && startHour.hour in part.from until part.to
            }
            if (hours.isEmpty()) null else weather(part, hours)
        }
    }

    private fun weather(part: DayPart, hours: List<HistoryHour>): DayPartWeather {
        val conds = hours.mapNotNull { it.measured?.condition ?: it.model?.condition }
        val amounts = hours.map { it.measured?.precipitation ?: it.model?.precipitation }
        val isDay = hours.count { it.model?.isDay != false } * 2 >= hours.size
        val wet = hours.indices.filter { i -> amounts[i]?.let { it >= WET_MM } ?: (conds.getOrNull(i)?.isPrecipitation == true) }
        val sum = amounts.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
        val wetSum = wet.sumOf { amounts[it] ?: 0.0 }
        val c = when {
            Condition.THUNDERSTORM in conds -> Condition.THUNDERSTORM
            wet.size * 2 >= hours.size && (wetSum >= SOME_MM || wet.any { amounts[it] == null }) -> {
                val rate = wetSum / wet.size
                val kinds = conds.filter { it.isPrecipitation }
                fun most(vararg c: Condition) = kinds.count { it in c } * 2 > kinds.size
                when {
                    most(Condition.SNOW, Condition.HEAVY_SNOW) -> if (rate >= WeatherCodes.HEAVY_SNOW_MM_H) Condition.HEAVY_SNOW else Condition.SNOW
                    most(Condition.SLEET) -> Condition.SLEET
                    most(Condition.FREEZING_RAIN) -> Condition.FREEZING_RAIN
                    rate >= WeatherCodes.HEAVY_RAIN_MM_H -> Condition.HEAVY_RAIN
                    rate < SOME_MM -> Condition.DRIZZLE
                    else -> Condition.RAIN
                }
            }
            wet.isNotEmpty() && wetSum >= SOME_MM -> Condition.SHOWERS
            conds.count { it == Condition.FOG } * 2 > conds.size -> Condition.FOG
            else -> sky(hours, conds)
        }
        return DayPartWeather(part, c, isDay, sum)
    }

    /** Dry: by day from the sunshine (share of the daylight hours' minutes), at night from the clouds. */
    private fun sky(hours: List<HistoryHour>, conds: List<Condition>): Condition {
        val light = hours.filter { it.model?.isDay != false }
        val sun = light.mapNotNull { it.measured?.sunshineMinutes ?: it.model?.sunshineMinutes }
        if (light.size * 2 >= hours.size && sun.isNotEmpty()) {
            val share = sun.sum() / (60.0 * sun.size)
            return when {
                share >= 0.75 -> Condition.CLEAR
                share >= 0.5 -> Condition.MOSTLY_CLEAR
                share >= 0.2 -> Condition.PARTLY_CLOUDY
                else -> Condition.CLOUDY
            }
        }
        val ranks = conds.map { bySky.indexOf(it) }.filter { it >= 0 }
        if (ranks.isEmpty()) return Condition.CLOUDY
        return bySky[(ranks.average() + 0.5).toInt().coerceIn(0, bySky.lastIndex)]
    }
}
