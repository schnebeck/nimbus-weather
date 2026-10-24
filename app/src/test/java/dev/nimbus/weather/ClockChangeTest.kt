/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ClockChangeTest.kt
 * The days the clocks change: 25 hours in October, 23 in March – every hour of the day, none of the next.
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
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.DayOverview
import dev.nimbus.weather.ui.main.HourAxis
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.PrecipToday
import dev.nimbus.weather.ui.main.symbolBlocks
import dev.nimbus.weather.util.Days
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ClockChangeTest {
    private val h = 3_600_000L
    private val berlin = ZoneId.of("Europe/Berlin")
    private val tf = TimeFormat("Europe/Berlin", true, java.util.Locale.GERMANY)
    private fun midnight(date: String) = LocalDate.parse(date).atStartOfDay(berlin).toInstant().toEpochMilli()

    /** The day in October the clocks go back */
    private val back = midnight("2026-10-25")
    /** … and the one in March they go forward. */
    private val forward = midnight("2026-03-29")

    @Test fun aDayEndsAtTheNextMidnight() {
        assertEquals(25 * h, Days.end(back, berlin) - back)
        assertEquals(23 * h, Days.end(forward, berlin) - forward)
        assertEquals(24 * h, Days.end(midnight("2026-10-24"), berlin) - midnight("2026-10-24"))
        assertEquals(back, Days.start(back + 24 * h + 30 * 60_000L, berlin))
        // noon: 13 hours in when the clocks went back, 11 when they went forward
        assertEquals(back + 13 * h, Days.at(back, 12, berlin))
        assertEquals(forward + 11 * h, Days.at(forward, 12, berlin))
    }

    /** The day chart's labels name the clock's hours: 00, 03 … 21, 24 – the 03 four hours in when the clocks went back. */
    @Test fun theLabelsAreTheClocks() {
        for ((start, hours) in listOf(back to 25, forward to 23)) {
            val end = Days.end(start, berlin)
            val labels = HourAxis(start, HourAxis.dayAxisEnd(end), 0f, 1000f).labelHours(zone = berlin)
            assertEquals(listOf("00", "03", "06", "09", "12", "15", "18", "21", "00"), labels.map { tf.hour(it) })
            assertEquals(end, labels.last())
            assertEquals("$hours hours", hours * h, end - start)
        }
        assertEquals(back + 4 * h, HourAxis(back, back + 26 * h, 0f, 1f).labelHours(zone = berlin)[1])
        assertEquals(forward + 2 * h, HourAxis(forward, forward + 24 * h, 0f, 1f).labelHours(zone = berlin)[1])
    }

    /** The symbols stand in the labels' columns: a block from each labelled hour to the next. */
    @Test fun theSymbolsStandUnderTheLabels() {
        val pts = (0..26).map { k -> MeteoPoint(back + k * h, 10.0, if (k == 4) Condition.THUNDERSTORM else Condition.CLOUDY, true, 0.0) }
        val blocks = symbolBlocks(pts, back, HourAxis.dayAxisEnd(Days.end(back, berlin)), berlin)
        assertEquals(listOf("00", "03", "06", "09", "12", "15", "18", "21", "00"), blocks.map { (centre, _) -> tf.hour(centre) })
    }

    private fun weather(start: Long, rain: (Long) -> Double): WeatherData {
        val hourly = (1..30).map { k -> HourlyPoint(start + k * h, 10.0, condition = Condition.CLOUDY, isDay = false, precipitation = rain(start + k * h)) }
        val daily = listOf(DailyPoint(start, Condition.CLOUDY, 12.0, 8.0), DailyPoint(Days.end(start, berlin), Condition.CLOUDY, 12.0, 8.0))
        val cur = CurrentWeather(start, 10.0, 9.0, Condition.CLOUDY, false, 80.0, 7.0, 1012.0, 10.0, 20.0, 240.0, 90.0, 20000.0, 0.0, 0.0)
        return WeatherData(Place("p", "Groß Düngen", latitude = 52.07, longitude = 9.98), "Europe/Berlin", 3600, cur, hourly, daily, emptyList(), sources = emptyList(), fetchedAt = start)
    }

    /**
     * The 25-hour day: its last hour (23–24, stamped at the next midnight) is the day's – in the
     * precipitation until midnight and in the day's sum; the 23-hour day: the next day's first
     * hour is not.
     */
    @Test fun everyHourOfTheDayAndNoneOfTheNext() {
        // October: rain only from 23:00 to midnight
        val lastHour = weather(back) { t -> if (t == Days.end(back, berlin)) 1.0 else 0.0 }
        val late = Days.at(back, 22, berlin) + 30 * 60_000L
        assertEquals(1.0, PrecipToday.of(lastHour, late, false, null, tf)!!.next, 1e-9)
        assertEquals(1.0, DayOverview.of(lastHour.daily[0], lastHour.hourly, berlin).precipitation!!, 1e-9)
        // March: rain only from midnight to 01:00 of the next day
        val nextDay = weather(forward) { t -> if (t == Days.end(forward, berlin) + h) 5.0 else 0.1 }
        val lateMarch = Days.at(forward, 22, berlin) + 30 * 60_000L
        assertEquals(0.2, PrecipToday.of(nextDay, lateMarch, false, null, tf)!!.next, 1e-9)
        assertEquals(23 * 0.1, DayOverview.of(nextDay.daily[0], nextDay.hourly, berlin).precipitation!!, 1e-9)
    }
}
