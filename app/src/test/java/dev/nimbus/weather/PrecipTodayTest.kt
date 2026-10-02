/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PrecipTodayTest.kt
 * When a day counts as dry (the precipitation card shrinks to one line or is hidden) and which
 * hour is the next precipitation it names.
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

package dev.nimbus.weather

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.PrecipToday
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecipTodayTest {
    private val h = 3_600_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L // 10:20
    private val tf = TimeFormat("UTC", true)

    /** Three days, dry with 5 % – [wet] hours (index from [day]) get [chance] and [mm]. */
    private fun data(
        wet: IntRange = IntRange.EMPTY, chance: Double = 60.0, mm: Double = 0.6,
        daySum: Double = 0.0, condition: Condition = Condition.CLEAR, minutelyMm: Double = 0.0,
    ): WeatherData {
        val hourly = (1..72).map { k ->
            val w = k in wet
            HourlyPoint(
                day + k * h, 15.0, condition = if (w) Condition.RAIN else Condition.CLEAR, isDay = k % 24 in 7..18,
                precipitation = if (w) mm else 0.0, precipitationProbability = if (w) chance else 5.0,
            )
        }
        val daily = (0..2).map { DailyPoint(day + it * 24 * h, Condition.CLEAR, 18.0, 8.0, precipitationSum = if (it == 0) daySum else 0.0) }
        val minutely = (0..12).map { MinutelyPoint(now - 15 * 60_000L + it * 15 * 60_000L, minutelyMm, 15.0) }
        val cur = CurrentWeather(now, 15.0, 14.0, condition, true, 60.0, 8.0, 1020.0, 10.0, 20.0, 240.0, 10.0, 30000.0, 3.0, 0.0)
        return WeatherData(Place("p", "Garbsen", latitude = 52.42, longitude = 9.60), "UTC", 0, cur, hourly, daily, minutely, sources = emptyList(), fetchedAt = now)
    }

    private fun of(d: WeatherData, raining: Boolean = false) = PrecipToday.of(d, now, raining, null, tf)!!

    @Test fun aDryDayNamesTheNextPrecipitation() {
        // rain tomorrow 14:00–17:00 (the hour stamped 15:00 covers 14–15)
        val p = of(data(wet = 39..42))
        assertTrue(p.dry)
        assertEquals(day + 39 * h, p.nextWet!!.time)
    }

    @Test fun nothingInTheForecastNoNextPrecipitation() {
        val p = of(data())
        assertTrue(p.dry)
        assertNull(p.nextWet)
    }

    @Test fun aChanceLaterTodayIsNotDry() {
        // 30 % at 18:00, no amount: not "dry today"
        assertFalse(of(data(wet = 18..18, chance = 30.0, mm = 0.0)).dry)
        // a mere 15 % stays dry
        assertTrue(of(data(wet = 18..18, chance = 15.0, mm = 0.0)).dry)
    }

    @Test fun rainThisMorningIsNotDry() {
        assertFalse(of(data(daySum = 1.2)).dry)
    }

    @Test fun rainNowOrInTheNowcastIsNotDry() {
        assertFalse(of(data(), raining = true).dry)
        assertFalse(of(data(minutelyMm = 0.5, condition = Condition.RAIN)).dry)
    }

    @Test fun theDryDaySettingShowsByDefaultAndTheCardLeads() {
        assertTrue(Settings().showDryPrecipitation)
        assertEquals(WeatherCard.PRECIPITATION, Settings().orderedCards().first())
    }
}
