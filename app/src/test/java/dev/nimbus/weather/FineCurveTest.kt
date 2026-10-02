/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/FineCurveTest.kt
 * Day-chart curves from 10- and 15-minute values, station reports filling the latest hours, and
 * the order of measured and forecast precipitation bars.
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

import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.SynopReport
import dev.nimbus.weather.ui.main.Curve
import dev.nimbus.weather.ui.main.CurvePoint
import dev.nimbus.weather.ui.main.HourAxis
import dev.nimbus.weather.ui.main.PrecipStyle
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.ui.main.dayCurve
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FineCurveTest {
    private val h = HourAxis.HOUR
    private val q = 15 * 60_000L
    private val ten = 10 * 60_000L
    private val start = 1_790_800_000_000L / h * h
    private val axis = HourAxis(start, start + 24 * h, 0f, 2400f)      // 100 px per hour

    @Test fun everyPointInTheMiddleOfItsInterval() {
        // hourly value of 06:00 at 05:30, the 15-minute one of 06:00 at 05:52:30, the 10-minute at 05:55
        assertEquals(550f, axis.point(start + 6 * h), 0.01f)
        assertEquals(587.5f, axis.point(start + 6 * h, q), 0.01f)
        assertEquals(591.67f, axis.point(start + 6 * h, ten), 0.01f)
        // an hourly point is in its bar's column, under the cursor
        assertEquals(axis.cursor(start + 6 * h), axis.point(start + 6 * h), 0.01f)
    }

    @Test fun gapsSplitTheCurveFineStepsDoNot() {
        val pts = (0..12).map { CurvePoint(start + it * ten, 10.0, ten) } +           // 00:00–02:00 every 10 min
            (6..8).map { CurvePoint(start + it * h, 12.0) }                             // 06–08 hourly
        val seg = Curve.segments(pts)
        assertEquals(2, seg.size)
        assertEquals(13, seg[0].size)
        // the newest report half an hour after the one before: bridged, not a lone dot
        val late = (0..6).map { CurvePoint(start + it * ten, 10.0, ten) } + CurvePoint(start + 90 * 60_000L, 11.0, ten)
        assertEquals(1, Curve.segments(late).size)
    }

    @Test fun valueAtTheCursorLiesOnTheCurve() {
        val pts = listOf(CurvePoint(start + h, 10.0), CurvePoint(start + 2 * h, 14.0))
        assertEquals(10.0, Curve.at(pts, start + h / 2)!!, 1e-9)                       // on the point
        assertEquals(12.0, Curve.at(pts, start + h)!!, 1e-9)                           // halfway
        assertNull(Curve.at(pts, start + 5 * h))
    }

    @Test fun fineWhereThereIsFineCoarseElsewhere() {
        val fine = (0..8).map { CurvePoint(start + it * q, 10.0, q) }                  // 00:00–02:00
        val coarse = (0..5).map { CurvePoint(start + it * h, 9.0) }
        val m = Curve.merge(fine, coarse)
        assertEquals(9 + 3, m.size)                                                     // 03, 04, 05 hourly
        assertTrue(m.zipWithNext().all { (a, b) -> a.at <= b.at })
    }

    @Test fun todayReadingsRunIntoTheForecastWithoutAGap() {
        // readings every 10 minutes up to 11:30, the forecast every 15 minutes all day
        val fine = (0..69).associate { start + it * ten to 15.0 }
        val measured = TodayMeasured(emptyMap(), emptyMap(), "X", emptyMap(), fine)
        val minutely = (0..96).map { MinutelyPoint(start + it * q, 0.0, 18.0) }
        val hours = (0..24).map { HourlyPoint(start + it * h, 18.0, null, Condition.CLOUDY, true, 0.0, 0.0) }
        val c = dayCurve(hours, minutely, start, start + 24 * h, measured)
        assertEquals(1, Curve.segments(c).size)
        assertEquals(15.0, Curve.at(c, start + 11 * h)!!, 1e-9)                        // measured
        assertEquals(18.0, Curve.at(c, start + 14 * h)!!, 1e-9)                        // forecast
    }

    @Test fun stationReportsFillTheLatestHour() {
        val t = start + 11 * h
        val reports = (0..6).map { k ->
            SynopReport(t - k * ten, 15.0 + k, 0.1, null, 9.0, 14.0, 280.0, null, 1032.0, 50.0, "rain", null)
        }.associateBy { it.time }
        val m = HistorySource.hourFromSynop(reports, t)!!
        assertEquals(15.0, m.temperature!!, 1e-9)
        assertEquals(0.6, m.precipitation!!, 1e-9)                                     // six 10-minute sums
        // an hour with a report missing has no sum
        assertNull(HistorySource.hourFromSynop(reports - (t - 3 * ten), t)!!.precipitation)
    }

    @Test fun synopParsedAndStationFound() {
        val synop = Json.parseToJsonElement(
            """{"weather":[{"timestamp":"2026-10-02T10:10:00+00:00","temperature":16.8,"precipitation_10":0.0,"wind_speed_10":9.4,"pressure_msl":1032.7}]}""",
        )
        val r = HistorySource.parseSynop(synop).single()
        assertEquals(java.time.Instant.parse("2026-10-02T10:10:00Z").toEpochMilli(), r.time)
        assertEquals(16.8, r.temperature!!, 1e-9)
        val hourly = Json.parseToJsonElement(
            """{"sources":[{"id":1,"observation_type":"historical","dwd_station_id":"02011","distance":5000},
               {"id":2,"observation_type":"current","dwd_station_id":"02014","distance":7700},
               {"id":3,"observation_type":"forecast","dwd_station_id":"02011","distance":5200}],"weather":[]}""",
        )
        assertEquals("02014", HistorySource.synopStation(hourly))
    }

    @Test fun forecastBarInFrontOnlyWhenSmaller() {
        assertTrue(PrecipStyle.forecastInFront(0.5, 1.2))
        assertFalse(PrecipStyle.forecastInFront(1.2, 0.5))
        assertFalse(PrecipStyle.forecastInFront(1.0, 1.0))
        assertFalse(PrecipStyle.forecastInFront(0.5, null))
        // 40 % transparent
        assertEquals(0.6f, PrecipStyle.ForecastOverlay.alpha, 0.01f)
    }
}
