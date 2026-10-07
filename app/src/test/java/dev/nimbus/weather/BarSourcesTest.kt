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
import org.junit.Assert.assertTrue
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

    /**
     * „wenn eine Station gar keinen Parameter meldet, sollte die Grafik den dann einfach auch gar
     * nicht visualisieren“: without rain gauge and sunshine sensor the hours over show no bar
     * (their forecast is in the readout's second column) – the hours to come the forecast's frames.
     */
    @Test fun theHoursOverWithoutAReadingShowNothing() {
        val pts = today(history(rain = null, sun = null))
        pts.filter { it.time <= now }.forEach {
            assertEquals(HourBar(null, null), it.rainBar())
            assertEquals(HourBar(null, null), it.sunBar())
            assertEquals(0.3, it.compare!!.precipF!!, 1e-9)
            assertEquals(40.0, it.compare!!.sunF!!, 1e-9)
        }
        pts.filter { it.time > now }.forEach {
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertEquals(HourBar(null, 40.0), it.sunBar())
        }
    }

    /**
     * „da stimmt was nicht mit den Sonnenstunden in der Tagesvorhersage?! Da fehlt ein Feld!“ – the
     * satellite's newest hour comes some 20 minutes late: an hour over after the last reading shows
     * the forecast's frame until its reading is in; an hour missing between readings stays empty.
     */
    @Test fun anHourStillOnItsWayShowsTheForecast() {
        val hist = History(
            listOf(HistoryDay(today, (0..24).map { k ->
                val sun = if (k == 6 || k == 12) null else 10.0   // 06: missing, 12: not in yet
                HistoryHour(t0 + k * h, if (k <= 12) reading(k, 0.8, sun) else null, modelled())
            })),
            "Hannover", 5.0, "icon_seamless", zone, 0L,
        )
        val pts = today(hist)
        val at = { k: Int -> pts.first { it.time == t0 + k * h } }
        assertEquals(HourBar(null, 40.0), at(12).sunBar())
        assertEquals(HourBar(null, null), at(6).sunBar())
        assertEquals(HourBar(10.0, null), at(11).sunBar())
        // the rain gauge is up to date: its hours are blocks
        assertEquals(HourBar(0.8, null), at(12).rainBar())
        // the legend: the hour on its way counts as expected
        assertEquals(40.0, pts.filter { it.time > t0 && it.time <= now }.barTotals { it.sunBar() }.expected!!, 1e-9)
    }

    /** Rain gauge but no sunshine sensor: precipitation measured, sunshine expected – each on its own. */
    @Test fun eachQuantityByItsOwnReadings() {
        val past = today(history(rain = 0.0, sun = null)).filter { it.time in t0 + h..now }
        past.forEach {
            assertEquals(HourBar(0.0, null), it.rainBar())
            assertEquals(HourBar(null, null), it.sunBar())
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

    /**
     * Over the place itself: the radar's precipitation and the satellite's sunshine take the
     * station's place – each hour's weather brightened by the sunshine measured there.
     */
    @Test fun theRadarAndTheSatelliteMeasureOverThePlace() {
        val station = (0..12).associate { k -> t0 + k * h to reading(k, rain = 0.8, sun = 55.0).copy(condition = Condition.CLOUDY) }
        val noon = t0 + 12 * h
        val spot = dev.nimbus.weather.data.remote.HistorySource.overSpot(station, radar = mapOf(noon to 1.5), sun = mapOf(noon to 5.0), now = now)
        val m = spot.getValue(noon)
        assertEquals(1.5, m.precipitation!!, 1e-9)
        assertEquals(dev.nimbus.weather.data.remote.Provenance.RADAR, m.precipitationFrom)
        assertEquals(5.0, m.sunshineMinutes!!, 1e-9)
        assertEquals(dev.nimbus.weather.data.remote.Provenance.SATELLITE, m.sunshineFrom)
        // five minutes of sun leave the clouds: the station's 55 minutes (40 km away) made it sunny
        assertEquals(Condition.CLOUDY, m.condition)
        assertEquals(Condition.CLEAR, spot.getValue(noon - h).condition)
        assertEquals(dev.nimbus.weather.data.remote.Provenance.STATION, spot.getValue(noon - h).precipitationFrom)
    }

    /** No station (beyond Germany): the satellite's sunshine is measured all the same – the temperature is the forecast's. */
    @Test fun theSatelliteWithoutAStation() {
        val sun = (1..12).associate { k -> t0 + k * h to 30.0 }
        val measured = dev.nimbus.weather.data.remote.HistorySource.overSpot(emptyMap(), emptyMap(), sun, now)
        val hist = History(
            listOf(HistoryDay(today, (0..24).map { k -> HistoryHour(t0 + k * h, measured[t0 + k * h], modelled()) })),
            null, null, "icon_seamless", zone, now,
        )
        val pts = hist.lookBackPoints(hist.days[0], t0)
        pts.filter { it.time in t0 + h..t0 + 12 * h }.forEach {
            assertEquals(HourBar(30.0, 40.0), it.sunBar())
            assertEquals(HourBar(null, 0.3), it.rainBar())
            assertTrue(it.forecastOnly)
        }
        // today's chart: sunshine measured in the hours over, and nothing else measured
        val m = TodayMeasured.of(hist, today)!!
        forecast().map { m.apply(it, now) }.filter { it.time in t0 + h..now }.forEach {
            assertEquals(HourBar(30.0, null), it.sunBar())
            assertEquals(HourBar(null, null), it.rainBar())
        }
    }
}
