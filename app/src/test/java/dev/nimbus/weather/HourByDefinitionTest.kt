/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HourByDefinitionTest.kt
 * An hour is the hour from its label on – "17" is 17–18 – in the strip, the short forecast and the
 * precipitation card alike; what they say of the hours to come agrees with the charts.
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
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.ui.main.Curve
import dev.nimbus.weather.ui.main.CurvePoint
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.ui.main.Outlook
import dev.nimbus.weather.ui.main.PrecipToday
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.ui.main.dayCurve
import dev.nimbus.weather.ui.main.lookBackCurves
import dev.nimbus.weather.ui.main.stripHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Celle on 11 Oct 2026 at 15:48: rain until about 16:00, a dry hour, then 2.7 mm from 17 to 18.
 * The data points carry the hour before their time (17–18 is the point at 18:00).
 */
class HourByDefinitionTest {
    private val h = 3_600_000L
    private val day = 1_791_676_800_000L                       // 11 Oct 2026 00:00 UTC
    private val now = day + 15 * h + 48 * 60_000L

    /** The point at the end of the hour starting at [start] o'clock. */
    private fun p(start: Int, c: Condition, chance: Double, mm: Double, t: Double = 11.0) =
        HourlyPoint(day + (start + 1) * h, t, condition = c, isDay = start in 7..17, precipitationProbability = chance, precipitation = mm)

    /** From 14–15 on: 15–16 rain, 16–17 cloudy, 17–18 rain 2.7 mm, 18–19 rain, then cloudy and drier. */
    private val celle = listOf(
        p(14, Condition.RAIN, 85.0, 0.4), p(15, Condition.RAIN, 82.0, 0.2), p(16, Condition.CLOUDY, 80.0, 0.0),
        p(17, Condition.RAIN, 70.0, 2.7), p(18, Condition.RAIN, 60.0, 0.6),
    ) + (19..23).map { p(it, Condition.CLOUDY, 50.0 - (it - 19) * 10, 0.0) }

    /** The strip's "17" is the hour 17–18: its rain, its chance, the temperature at 17:00. */
    @Test fun theStripsHourIsTheHourFromItsLabel() {
        val strip = stripHours(celle)
        val at17 = strip.single { it.start == day + 17 * h }
        assertEquals(Condition.RAIN, at17.condition)
        assertEquals(70.0, at17.chance!!, 0.0)
        assertEquals(2.7, at17.amount!!, 0.0)
        assertEquals(celle.single { it.time == day + 17 * h }.temperature, at17.temperature, 0.0)
        assertEquals(Condition.CLOUDY, strip.single { it.start == day + 16 * h }.condition)
    }

    /** A dry hour between rain is no change of weather; the short forecast names hours by their start. */
    @Test fun aDryHourBetweenRainIsNoChange() {
        val running = celle.filter { it.time > now - h }
        val change = Insights.nextChange(listOf(HourlyPoint(now, 11.0, condition = Condition.RAIN, isDay = true)) + running, Condition.RAIN)
        assertEquals("the change: cloudy from 19:00 (the hour 19–20), not the dry hour 16–17", day + 20 * h, change.time)
    }

    /** "Ends around 16:02" while 2.7 mm come from 17:00: it stops for a while. */
    @Test fun rainStoppingAndComingAgainIsABreak() {
        val nowcast = listOf(0.3, 0.1, 0.0, 0.0, 0.0, 0.0).mapIndexed { i, mm -> MinutelyPoint(day + 15 * h + 45 * 60_000L + i * 15 * 60_000L, mm) }
        val o = Outlook.precipitation(Condition.RAIN, celle.filter { it.time > now }, nowcast, now)
        assertTrue(o.toString(), o is Outlook.Precip.Pause)
        o as Outlook.Precip.Pause
        assertEquals(day + 17 * h, o.again)
        assertEquals(Condition.RAIN, o.condition)
        // without rain to come it ends
        val dryAfter = celle.filter { it.time > now }.map { it.copy(condition = Condition.CLOUDY, precipitation = 0.0, precipitationProbability = 20.0) }
        assertTrue(Outlook.precipitation(Condition.RAIN, dryAfter, nowcast, now) is Outlook.Precip.Ongoing)
    }

    /** "Highest chance: 82 % around 16:00" with the hour running now: no high point – the chance falls. */
    @Test fun highestOnlyAtAHighPoint() {
        val rest = celle.filter { it.time > now }
        val c = PrecipToday.chance(rest)
        assertTrue(c.toString(), c is PrecipToday.Chance.Falls)
        assertEquals(PrecipToday.Chance.Falls(10, day + 23 * h), c)
        // a real high point, by its start
        val peak = (16..23).map { p(it, Condition.CLOUDY, if (it == 19) 70.0 else 20.0, 0.0) }
        assertEquals(PrecipToday.Chance.Highest(70, day + 19 * h), PrecipToday.chance(peak))
        // a real low point
        val low = (16..23).map { p(it, Condition.RAIN, if (it == 18) 20.0 else 80.0, 1.0) }
        assertEquals(PrecipToday.Chance.Lowest(20, day + 18 * h), PrecipToday.chance(low))
        // rising to the evening
        val rising = (16..23).map { p(it, Condition.CLOUDY, 10.0 + (it - 16) * 10, 0.0) }
        assertEquals(PrecipToday.Chance.Rises(80, day + 23 * h), PrecipToday.chance(rising))
        assertEquals(PrecipToday.Chance.None, PrecipToday.chance((16..23).map { p(it, Condition.CLOUDY, 0.0, 0.0) }))
    }

    /**
     * The day's forecast and the look-back expect the same for the same hour: the model as it is –
     * never drawn to the readings, the hours to come neither; only the day's coloured curve goes on
     * from the last reading.
     */
    @Test fun bothChartsExpectTheSame() {
        // readings up to 15:00, about 1 K below the model
        val readings = (0..15).associate { day + it * h to 10.0 }
        val model = (0..25).map { HourlyPoint(day + it * h, 11.0, condition = Condition.CLOUDY, isDay = true) }
        val history = History(
            emptyList(), "Celle", 1.0, "m", ZoneOffset.UTC, now, fineMeasured = readings,
            allHours = model.map { HistoryHour(it.time, null, HistoryHour.Modelled(it.temperature, 0.0, null, null, null, Condition.CLOUDY, true)) },
        )
        val (_, expected) = lookBackCurves(history, day)
        val today = TodayMeasured(emptyMap(), emptyMap(), "Celle", emptyMap(), readings)
        val dayExpected = dev.nimbus.weather.ui.main.dayExpected(model, emptyList(), day, day + 24 * h, today)!!
        for (t in listOf(12, 15, 16, 17, 18, 20)) {
            assertEquals("$t:00", 11.0, Curve.at(expected, day + t * h)!!, 1e-9)
            assertEquals("$t:00", 11.0, Curve.at(dayExpected, day + t * h)!!, 1e-9)
        }
        // the coloured curve of the day: from the last reading on, no step at it
        val coloured = dayCurve(model, emptyList(), day, day + 24 * h, today)
        assertTrue(Curve.at(coloured, day + 16 * h)!! < 11.0)
        assertEquals(dev.nimbus.weather.ui.main.dayLastReading(model, emptyList(), day, day + 24 * h, today), day + 15 * h)
        // another day: the model as it is
        val (_, yesterday) = lookBackCurves(history.copy(fetchedAt = now + 24 * h), day)
        assertEquals(model.map { CurvePoint(it.time, it.temperature) }.filter { it.time <= day + 25 * h }, yesterday)
    }
}
