/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HistoryTest.kt
 * Tests for the look back: station and model hours, day summaries.
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryTest {
    // Fixture recorded 2026-09-29 around 12:20 CEST
    private val now = Instant.parse("2026-09-29T10:20:00Z").toEpochMilli()

    @Test
    fun `forecast entries are dropped from observations`() {
        val obs = HistorySource.parseObservations(Fixtures.json("brightsky_history.json"))
        assertEquals("Hannover-Herrenhausen", obs.station)
        assertEquals(5.0, obs.distanceKm!!, 0.1)
        // The last entry (12:00) came from the MOSMIX forecast source and must be gone.
        val noon = Instant.parse("2026-09-29T10:00:00Z").toEpochMilli()
        assertNull(obs.byTime[noon])
        assertTrue(obs.byTime.size in 55..61)
    }

    @Test
    fun `days are split in the local time zone, future hours cut`() {
        val h = HistorySource.combine(Fixtures.json("openmeteo_history.json"), Fixtures.json("brightsky_history.json"), "icon_seamless", now)
        assertEquals(listOf(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)), h.days.map { it.date })
        assertEquals(24, h.days[0].hours.size)
        assertEquals(24, h.days[1].hours.size)
        assertTrue(h.days[2].hours.all { it.time <= now })
        assertTrue(h.days[2].hours.size in 11..13)
        // measured and modelled values are both present for past hours
        val sample = h.days[1].hours[12]
        assertNotNull(sample.measured?.temperature)
        assertNotNull(sample.model?.temperature)
    }

    @Test
    fun `summary prefers measurements and computes the forecast error`() {
        val t0 = 1_800_000_000_000L
        fun hour(i: Int, measured: Double?, model: Double, rain: Double = 0.0, gust: Double = 10.0) = HistoryHour(
            t0 + i * 3_600_000L,
            measured?.let { HistoryHour.Measured(it, rain, 5.0, gust, 200.0, 30.0, 50.0, Condition.CLOUDY) },
            HistoryHour.Modelled(model, 0.0, 6.0, 12.0, 20.0, Condition.PARTLY_CLOUDY, true),
        )
        val day = HistoryDay(LocalDate.of(2027, 1, 15), (0 until 24).map { hour(it, 10.0 + it % 5, 11.0 + it % 5, if (it == 5) 1.5 else 0.0, if (it == 8) 45.0 else 10.0) })
        val s = DaySummary.of(day)
        assertTrue(s.measured)
        assertEquals(14.0, s.tempMax!!, 1e-9)
        assertEquals(15.0, s.modelTempMax!!, 1e-9)
        assertEquals(1.0, s.tempError!!, 1e-9)
        assertEquals(1.5, s.precipitation!!, 1e-9)
        assertEquals(0.0, s.modelPrecipitation!!, 1e-9)
        assertEquals(45.0, s.maxGust!!, 1e-9)
        assertEquals(12.0, s.sunshineHours!!, 1e-9)          // 24 x 30 min
        val modelOnly = DaySummary.of(HistoryDay(day.date, (0 until 24).map { hour(it, null, 8.0) }))
        assertTrue(!modelOnly.measured)
        assertNull(modelOnly.tempError)
    }

    @Test
    fun `dominant condition favours lasting precipitation`() {
        val t0 = 1_800_000_000_000L
        val hours = (0 until 10).map { i ->
            HistoryHour(t0 + i * 3_600_000L, HistoryHour.Measured(10.0, 0.0, 1.0, 2.0, 0.0, 0.0, 50.0, if (i < 3) Condition.RAIN else Condition.CLOUDY), null)
        }
        assertEquals(Condition.RAIN, DaySummary.dominant(hours))
        assertEquals(Condition.CLOUDY, DaySummary.dominant(hours.drop(2)))
    }

    @Test
    fun `bright sky conditions`() {
        assertEquals(Condition.CLEAR, HistorySource.condition("dry", "clear-night", 0.0))
        assertEquals(Condition.DRIZZLE, HistorySource.condition("rain", "rain", 0.1))
        assertEquals(Condition.HEAVY_RAIN, HistorySource.condition("rain", "rain", 5.0))
        assertNull(HistorySource.condition("dry", null, null))
    }
}
