/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DayPartsTest.kt
 * The look-back day in parts instead of one condition: a rainy night and a sunny afternoon are
 * not "drizzle" – and the table grows in the course of the day.
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
import dev.nimbus.weather.data.remote.DayPart
import dev.nimbus.weather.data.remote.DayParts
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayPartsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 2)
    private val h = 3_600_000L
    private val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Hannover-Kirchrode, 2 Oct. 2026 (Bright Sky): the hour ending at 01:00 … 24:00 –
     * precipitation (mm), the station's condition, sunshine (min).
     */
    private val precip = listOf(0.8, 0.2, 0.3, 1.1, 1.0, 0.8, 0.6, 0.1, 0.4, 0.0, 0.1, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    private val sun = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 9.0, 41.0, 44.0, 12.0, 14.0, 41.0, 41.0, 41.0, 10.0, 0.0, 0.0, 0.0, 0.0)

    private fun day(): HistoryDay = HistoryDay(date, (1..24).map { k ->
        val p = precip[k - 1]
        val cond = when {
            k <= 12 -> if (p >= 0.3) Condition.RAIN else Condition.DRIZZLE      // the station: "rain" until 12:00
            else -> Condition.CLOUDY                                             // "cloudy" by the cloud cover
        }
        HistoryHour(
            midnight + k * h,
            HistoryHour.Measured(14.0, p, 10.0, 20.0, 250.0, sun[k - 1], 90.0, cond),
            HistoryHour.Modelled(14.0, p, 10.0, 20.0, sun[k - 1], cond, isDay = k in 8..19),
        )
    })

    @Test fun aRainyNightAndASunnyAfternoonAreNotDrizzle() {
        val parts = DayParts.of(day(), zone, Long.MAX_VALUE).associate { it.part to it.condition }
        assertEquals(DayPart.entries.toSet(), parts.keys)
        assertEquals(Condition.RAIN, parts[DayPart.EARLY])                 // 4.2 mm in 6 hours
        assertEquals(Condition.RAIN, parts[DayPart.MORNING])               // 1.1 mm
        assertEquals(Condition.DRIZZLE, parts[DayPart.FORENOON])           // 0.2 mm in 3 hours
        // dry, the sun out half the time although the cloud cover says "cloudy"
        assertTrue(parts[DayPart.AFTERNOON] in setOf(Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY))
        assertEquals(Condition.CLOUDY, parts[DayPart.NIGHT])
    }

    @Test fun theTableGrowsInTheCourseOfTheDay() {
        // 13:30: the afternoon has one hour over
        val at1330 = DayParts.of(day(), zone, midnight + 13 * h + 30 * 60_000L).map { it.part }
        assertEquals(listOf(DayPart.EARLY, DayPart.MORNING, DayPart.FORENOON, DayPart.AFTERNOON), at1330)
        // 00:30: nothing over yet
        assertTrue(DayParts.of(day(), zone, midnight + 30 * 60_000L).isEmpty())
    }

    /** The sky of each part in the light of its time of day – no moon at noon (it showed the light of now). */
    @Test fun eachPartInTheLightOfItsTime() {
        val base = dev.nimbus.weather.ui.background.SkyScene(Condition.CLEAR, daylight = 0f, twilight = 0f, wind = 0.1f)
        fun at(hour: Double) = dev.nimbus.weather.ui.background.SkyScene.atTime(base, midnight + (hour * h).toLong(), 52.3759, 9.7320)
        val afternoon = at(15.0); val early = at(3.0); val morning = at(7.5)
        assertEquals(1f, afternoon.daylight, 0.01f)
        assertTrue(!afternoon.isNight)
        assertEquals(0f, early.daylight, 0.01f)
        assertTrue(early.isNight)
        // sunrise in Hannover on 2 Oct. is about 07:25: the morning part is in twilight
        assertTrue("morning twilight ${morning.twilight}", morning.twilight > 0.3f)
    }

    @Test fun nightPartsAreNight() {
        val parts = DayParts.of(day(), zone, Long.MAX_VALUE).associate { it.part to it.isDay }
        assertEquals(false, parts[DayPart.EARLY])
        assertEquals(true, parts[DayPart.AFTERNOON])
        assertEquals(false, parts[DayPart.NIGHT])
    }
}
