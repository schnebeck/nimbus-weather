/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TodayMeasuredTest.kt
 * Today's station readings for the day charts are taken from the look-back data.
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
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.TodayMeasured
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayMeasuredTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 1)
    private val t0 = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun hour(i: Int, rain: Double?, hpa: Double?) = HistoryHour(
        t0 + i * 3_600_000L,
        HistoryHour.Measured(15.0, rain, 5.0, 8.0, 200.0, 0.0, 90.0, Condition.RAIN, hpa),
        null,
    )

    private fun history(vararg days: HistoryDay) = History(days.toList(), "Hannover-Herrenhausen", 5.0, "icon_seamless", zone, 0L)

    @Test fun readingsOfTodayByHour() {
        val h = history(
            HistoryDay(today.minusDays(1), listOf(hour(-5, 9.9, 1000.0))),
            HistoryDay(today, listOf(hour(13, 1.2, 1021.0), hour(14, 0.4, 1021.5), hour(15, null, 1022.0))),
        )
        val m = TodayMeasured.of(h, today)!!
        assertEquals(mapOf(t0 + 13 * 3_600_000L to 1.2, t0 + 14 * 3_600_000L to 0.4), m.precipitation)
        assertEquals(3, m.pressure.size)
        assertEquals("Hannover-Herrenhausen", m.station)
    }

    @Test fun nothingWithoutTodayOrReadings() {
        assertNull(TodayMeasured.of(null, today))
        assertNull(TodayMeasured.of(history(HistoryDay(today.minusDays(1), listOf(hour(-5, 1.0, 1000.0)))), today))
        assertNull(TodayMeasured.of(history(HistoryDay(today, emptyList())), today))
    }

    @Test fun measuredReplacesTheForecastForHoursOver() {
        val m = TodayMeasured.of(history(HistoryDay(today, listOf(hour(13, 1.2, 1021.0)))), today)!!
        val t = t0 + 13 * 3_600_000L
        val forecast = MeteoPoint(t, 18.0, Condition.CLOUDY, true, 0.0, precipitationChance = 20.0, apparentTemperature = 17.0, sunshine = 30.0)
        val over = m.apply(forecast, now = t + 600_000L)
        assertEquals(15.0, over.temperature, 1e-9)
        assertEquals(14.0, over.apparentTemperature!!, 1e-9)       // shifted with the temperature
        assertEquals(1.2, over.precipitation!!, 1e-9)
        assertEquals(20.0, over.precipitationChance!!, 1e-9)            // the forecast chance stays
        assertEquals(Condition.RAIN, over.condition)
        assertEquals(0.0, over.sunshine!!, 1e-9)
        assertEquals(true, over.measured)
        // the hour still running and the hours to come keep the forecast
        assertEquals(forecast, m.apply(forecast, now = t - 1))
        assertEquals(forecast.copy(time = t + 3_600_000L), m.apply(forecast.copy(time = t + 3_600_000L), now = t + 600_000L))
    }

    @Test fun nightLastsUntilTheEndOfTheLastHour() {
        // Look-back: 21:00, 22:00, 23:00 at night – the last night runs to 24:00, not to 23:00
        val pts = (21..23).map { MeteoPoint(t0 + it * 3_600_000L, 10.0, Condition.CLEAR, false, null) }
        val nights = dev.nimbus.weather.ui.main.nightsFromFlags(pts)
        assertEquals(t0 + 24 * 3_600_000L - 1, nights.maxOf { it.last })
        assertEquals(t0 + 21 * 3_600_000L, nights.minOf { it.first })
    }

    @Test fun anHourOverWithoutAReadingShowsNoForecastAmount() {
        val m = TodayMeasured.of(history(HistoryDay(today, listOf(hour(13, 1.2, 1021.0)))), today)!!
        val t = t0 + 12 * 3_600_000L                 // no reading for 12:00
        val forecast = MeteoPoint(t, 18.0, Condition.CLOUDY, true, 0.8, precipitationChance = 40.0)
        val shown = m.apply(forecast, now = t + 3_600_000L)
        assertNull(shown.precipitation)
        assertEquals(40.0, shown.precipitationChance!!, 1e-9)
    }
}
