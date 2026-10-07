/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DayOverview.kt
 * A whole day in figures: what the chart's readout shows for a day to come while no cursor is set.
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
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint

/**
 * The day's figures: highest and lowest temperature, precipitation in all and its highest chance,
 * the strongest wind and gust, the sunshine in all and the highest UV index. The totals are the
 * hours' (as the chart's legend has them); the daily forecast gives what the hours do not.
 */
data class DayOverview(
    val condition: Condition,
    val high: Double, val low: Double,
    val precipitation: Double?, val chance: Double?,
    val wind: Double?, val windDirection: Double?, val gust: Double?,
    val sunMinutes: Double?, val uv: Double?,
) {
    companion object {
        private const val DAY_MS = 24 * 3_600_000L

        /** [hours]: any hours – those of the day (stamped after its midnight, up to the next) count. */
        fun of(day: DailyPoint, hours: List<HourlyPoint>): DayOverview {
            val own = hours.filter { it.time > day.date && it.time <= day.date + DAY_MS }
            return DayOverview(
                condition = day.condition,
                high = day.tempMax, low = day.tempMin,
                precipitation = own.mapNotNull { it.precipitation }.takeIf { it.isNotEmpty() }?.sum() ?: day.precipitationSum,
                chance = day.precipitationProbability ?: own.mapNotNull { it.precipitationProbability }.maxOrNull(),
                wind = day.windSpeedMax ?: own.mapNotNull { it.windSpeed }.maxOrNull(),
                windDirection = day.windDirection,
                gust = own.mapNotNull { it.windGust }.maxOrNull(),
                sunMinutes = own.mapNotNull { it.sunshine }.takeIf { it.isNotEmpty() }?.sum(),
                uv = day.uvIndexMax ?: own.mapNotNull { it.uvIndex }.maxOrNull(),
            )
        }
    }
}
