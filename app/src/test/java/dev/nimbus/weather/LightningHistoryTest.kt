/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LightningHistoryTest.kt
 * Lightning in the look-back: the hours with flashes near the place are thunderstorms – in symbols, rows and the day's summary.
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
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.LightningSource
import dev.nimbus.weather.ui.main.HourCompare
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.symbolBlocks
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Lightning near the place (within about 15 km, as far as thunder is heard) marks the hours of the
 * look-back and of today's chart as thunderstorms – measured by the satellite, whatever the station
 * or the model said. Recorded north of Syria on 11 Oct 2026.
 */
class LightningHistoryTest {
    private val h = 3_600_000L
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    /** The flashes of each hour, by the hour's end: distinct flashes of the groups ending in it. */
    @Test fun theHoursOfTheFlashes() {
        val hours = LightningSource.hours(Fixtures.text("lightning_hours_raqqa.csv"))!!
        assertEquals(mapOf(at("2026-10-11T00:00:00Z") to 45, at("2026-10-11T01:00:00Z") to 102, at("2026-10-11T04:00:00Z") to 4), hours)
        // no CSV (an error as text): not known – not none
        assertNull(LightningSource.hours("<ServiceExceptionReport/>"))
        assertEquals(emptyMap<Long, Int>(), LightningSource.hours("FID,ID,FLASH_ID,GROUP_END_TIME\n"))
    }

    /** The look-back's three days in one small request: times only, as CSV, a box of about 15 km. */
    @Test fun theRequestOfTheLookBack() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(200).body("FID,ID,FLASH_ID,GROUP_END_TIME\n").build())
        server.start()
        try {
            val hours = LightningSource(OkHttpClient(), server.url("/ows").toString()).hourly(52.38, 9.73, at("2026-10-08T00:00:00Z"))
            assertEquals(emptyMap<Long, Int>(), hours)
            val url = server.takeRequest().url
            assertEquals("csv", url.queryParameter("outputFormat"))
            assertEquals("FLASH_ID,GROUP_END_TIME", url.queryParameter("propertyName"))
            val filter = url.queryParameter("cql_filter")!!
            assertTrue(filter, filter.startsWith("GROUP_END_TIME AFTER 2026-10-08T00:00:00Z AND BBOX(GEOM,9.5") && filter.endsWith(",'CRS:84')"))
        } finally { server.close() }
    }

    /** An hour with flashes is a thunderstorm with its count; one without, when known, has none; unknown stays unknown. */
    @Test fun anHourWithLightningIsAThunderstorm() {
        val noon = at("2026-10-10T12:00:00Z"); val one = noon - h
        val station = mapOf(
            noon to HistoryHour.Measured(20.0, 0.0, 10.0, 20.0, 200.0, 30.0, 50.0, Condition.PARTLY_CLOUDY),
            one to HistoryHour.Measured(21.0, 0.0, 10.0, 20.0, 200.0, 60.0, 10.0, Condition.CLEAR),
        )
        val spot = HistorySource.overSpot(station, emptyMap(), emptyMap(), noon + h, lightning = mapOf(noon to 12))
        assertEquals(Condition.THUNDERSTORM, spot.getValue(noon).condition)
        assertEquals(12, spot.getValue(noon).lightning)
        assertEquals(0, spot.getValue(one).lightning)
        assertEquals(Condition.CLEAR, spot.getValue(one).condition)
        assertNull(HistorySource.overSpot(station, emptyMap(), emptyMap(), noon + h).getValue(noon).lightning)
        // lightning without any other reading (no station abroad): the hour is there, a thunderstorm
        val alone = HistorySource.overSpot(emptyMap(), emptyMap(), emptyMap(), noon + h, lightning = mapOf(noon to 3))
        assertEquals(Condition.THUNDERSTORM, alone.getValue(noon).condition)
    }

    /** The day's summary: the flashes in all and their hours – and a day of thunderstorms. */
    @Test fun theDaysSummary() {
        val day = LocalDate.of(2026, 10, 10); val t0 = at("2026-10-10T00:00:00Z")
        fun hour(k: Int, flashes: Int) = HistoryHour(t0 + k * h, HistoryHour.Measured(15.0, 0.0, 5.0, 8.0, 200.0, 0.0, 90.0, Condition.CLOUDY, lightning = flashes), null)
        val hours = (1..24).map { k -> hour(k, if (k == 17) 20 else if (k == 18) 3 else 0) }
        val sum = DaySummary.of(HistoryDay(day, hours))
        assertEquals(23, sum.lightning)
        assertEquals(t0 + 16 * h, sum.lightningFrom)
        assertEquals(t0 + 18 * h, sum.lightningTo)
        assertEquals(Condition.THUNDERSTORM, sum.condition)
        assertNull(DaySummary.of(HistoryDay(day, hours.map { it.copy(measured = it.measured!!.copy(lightning = null)) })).lightning)
    }

    /** A 3-hour block's symbol shows the storm of any of its hours, not only of its first. */
    @Test fun theSymbolOfABlockShowsItsStorm() {
        val t0 = at("2026-10-10T00:00:00Z")
        fun compare(flashes: Int?) = HourCompare(null, null, null, null, null, null, null, null, null, null, null, null, lightningM = flashes)
        val pts = (0..25).map { k ->
            val storm = k == 17
            MeteoPoint(t0 + k * h, 15.0, if (storm) Condition.THUNDERSTORM else Condition.CLOUDY, true, 0.0, compare = compare(if (storm) 7 else 0))
        }
        val blocks = symbolBlocks(pts, t0, t0 + 25 * h).associate { (centre, p) -> (centre - t0) / h to p.condition }
        // 15–18 h: its first hour (15–16) cloudy, but lightning in 16–17
        assertEquals(Condition.THUNDERSTORM, blocks[15])
        assertEquals(Condition.CLOUDY, blocks[12])
        assertEquals(Condition.CLOUDY, blocks[18])
    }
}
