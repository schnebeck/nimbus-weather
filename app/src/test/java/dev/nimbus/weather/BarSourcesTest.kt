/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/BarSourcesTest.kt
 * Measured or expected, hour by hour: what today's readings, the look-back and a forecast day
 * hand the day charts' sunshine and precipitation bars.
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
import dev.nimbus.weather.ui.main.HourBar
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.ui.main.barTotals
import dev.nimbus.weather.ui.main.lookBackPoints
import dev.nimbus.weather.ui.main.rainBar
import dev.nimbus.weather.ui.main.sunBar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * „Wieso ist hier schon gefallener niederschlag im diagramm vermerkt??“ – „Sonne und niederschlag
 * müssen aber in allen Kachel-Darstellungsformen korrekt zwischen erwartet und gemessen
 * unterscheiden können“: a reading is a block, the forecast a frame – per quantity and per hour.
 */
class BarSourcesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 1)
    private val t0 = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val h = 3_600_000L
    private val now = t0 + 12 * h + 20 * 60_000L

    /** The forecast of the day: 0.3 mm and 40 min of sun every hour. */
    private fun forecast() = (0..24).map { k -> MeteoPoint(t0 + k * h, 14.0, Condition.RAIN, true, 0.3, sunshine = 40.0) }

    private fun reading(k: Int, rain: Double?, sun: Double?) =
        HistoryHour.Measured(15.0, rain, 5.0, 8.0, 200.0, sun, 90.0, Condition.RAIN)

    private fun modelled() = HistoryHour.Modelled(14.0, 0.3, 5.0, 8.0, 40.0, Condition.RAIN, true)

    /** Station readings 00:00 … 12:00 ([rain] and [sun] each hour), the model all day. */
    private fun history(rain: Double?, sun: Double?) = History(
        listOf(HistoryDay(today, (0..24).map { k -> HistoryHour(t0 + k * h, if (k <= 12) reading(k, rain, sun) else null, modelled()) })),
        "Hannover", 5.0, "icon_seamless", zone, 0L,
    )

    private fun today(history: History) = TodayMeasured.of(history, today)!!.let { m -> forecast().map { m.apply(it, now) } }

    @Test fun todayReadingsAreBlocksTheRestOfTheDayFrames() {
        val pts = today(history(rain = 0.8, sun = 10.0))
        pts.filter { it.time in t0 + h..now }.forEach {
            assertEquals(HourBar(0.8, null), it.rainBar())
            assertEquals(HourBar(10.0, null), it.sunBar())
        }
        pts.filter { it.time > now }.forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
        // the legend: measured so far, expected until midnight
        val day = pts.filter { it.time > t0 }
        assertEquals(12 * 0.8, day.barTotals { it.rainBar() }.measured!!, 1e-9)
        assertEquals(12 * 0.3, day.barTotals { it.rainBar() }.expected!!, 1e-9)
        assertEquals(12 * 40.0, day.barTotals { it.sunBar() }.expected!!, 1e-9)
    }

    /** A station without rain gauge and sunshine sensor: its hours keep the forecast – drawn as such, not as fallen. */
    @Test fun aStationWithoutGaugeOrSunshineLeavesTheForecastFramed() {
        today(history(rain = null, sun = null)).forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }

    /** Rain gauge but no sunshine sensor: precipitation measured, sunshine expected – each on its own. */
    @Test fun eachQuantityByItsOwnReadings() {
        val past = today(history(rain = 0.0, sun = null)).filter { it.time in t0 + h..now }
        past.forEach {
            assertEquals(HourBar(0.0, null), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }

    @Test fun lookBackReadingsWithTheForecastInFront() {
        val hist = history(rain = 0.8, sun = 10.0)
        val pts = hist.lookBackPoints(hist.days[0], t0)
        pts.filter { it.time <= t0 + 12 * h }.forEach {
            assertEquals(HourBar(0.8, 0.3), it.rainBar())
            assertEquals(HourBar(10.0, 40.0), it.sunBar())
        }
        pts.filter { it.time > t0 + 12 * h }.forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }

    @Test fun lookBackWithoutGaugeOrSunshineIsTheForecast() {
        val hist = history(rain = null, sun = null)
        hist.lookBackPoints(hist.days[0], t0).forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }

    @Test fun aDayToComeIsAllForecast() {
        forecast().forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }
}
