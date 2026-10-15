/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/RadarAhead.kt
 * The next hours by the radar over the place: its measured steps and its nowcast take the model's
 * place in the 15-minute steps and in the hours they cover.
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

package dev.nimbus.weather.data.repo

import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryHour
import kotlin.math.round

/**
 * The radar over the place for the next two hours: what it measured of the running hour and what
 * its nowcast brings after – before the model. A stretch counts only where the radar has all of
 * its 5-minute steps; elsewhere the model's values stay.
 */
object RadarAhead {
    private const val STEP_MS = 5 * 60_000L
    private const val QUARTER_MS = 15 * 60_000L
    private const val HOUR_MS = 3_600_000L

    /** mm in the stretch of [lengthMs] ending at [end] – null unless every step of it is there. */
    private fun sum(steps: Map<Long, Double>, end: Long, lengthMs: Long): Double? {
        val n = (lengthMs / STEP_MS).toInt()
        val values = (0 until n).map { steps[end - it * STEP_MS] ?: return null }
        return round(values.sum() * 100) / 100
    }

    /** The 15-minute steps from [now] on (each its preceding quarter of an hour) by the radar where it covers them. */
    fun minutely(points: List<MinutelyPoint>, radar: RadarNow?, now: Long): List<MinutelyPoint> {
        val r = NowWeather.radarValid(radar, now) ?: return points
        return points.map { p ->
            if (p.time + QUARTER_MS <= now) p else sum(r.steps, p.time, QUARTER_MS)?.let { p.copy(precipitation = it) } ?: p
        }
    }

    /**
     * The hours from the running one on by the radar where it covers them: the amount, the symbol
     * matched with it ([WeatherCodes.forMeasured] – rain by its amount, none without; thunder
     * stays), no chance – the radar does not give one.
     */
    fun hours(hours: List<HourlyPoint>, radar: RadarNow?, now: Long): List<HourlyPoint> {
        val r = NowWeather.radarValid(radar, now) ?: return hours
        return hours.map { h ->
            if (h.time <= now) return@map h
            val mm = sum(r.steps, h.time, HOUR_MS) ?: return@map h
            val c = WeatherCodes.forMeasured(h.condition, mm, h.cloudCover, h.temperature, byRadar = true) ?: h.condition
            h.copy(precipitation = mm, condition = WeatherCodes.withSunshine(c, h.sunshine), precipitationProbability = null, radar = true)
        }
    }

    /**
     * The look-back's hours to come – today's from the running one on – by the radar as [hours]
     * gives the weather page's: the model's amount and symbol where the radar covers the hour,
     * no chance. The hours over keep what was measured and expected.
     */
    fun history(history: History, radar: RadarNow?, now: Long): History {
        val r = NowWeather.radarValid(radar, now) ?: return history
        fun ahead(h: HistoryHour): HistoryHour {
            val m = h.model ?: return h
            if (h.time <= now) return h
            val mm = sum(r.steps, h.time, HOUR_MS) ?: return h
            val c = WeatherCodes.forMeasured(m.condition, mm, null, m.temperature, byRadar = true) ?: m.condition
            return h.copy(model = m.copy(precipitation = mm, condition = WeatherCodes.withSunshine(c, m.sunshineMinutes), chance = null))
        }
        return history.copy(
            days = history.days.map { d -> d.copy(hours = d.hours.map(::ahead)) },
            allHours = history.allHours.map(::ahead),
        )
    }
}
