/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ForecastSunshineTest.kt
 * The learnt model of the forecast's sunshine in the app: found, the same answers as the tool, read with the forecast.
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
import dev.nimbus.weather.data.remote.ForecastSunshine
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The forecast's sunshine read from the whole hour of the model: an hour with a shower and its
 * mean direct beam well short of a clear sky's is not sunny from start to end, though Open-Meteo's
 * own sunshine says so.
 */
class ForecastSunshineTest {
    private val trees = requireNotNull(ForecastSunshine.model)

    /** Found from a class of another package too – the release build moves the app's classes. */
    @Test fun theModelIsFoundFromAnywhere() {
        assertNotNull(GridSpanTest::class.java.getResourceAsStream(ForecastSunshine.MODEL))
    }

    /** The model's sunshine, direct beam against a clear sky, sun height, cloud, precipitation – as the tool evaluated them. */
    @Test fun theSameAnswersAsTheTool() {
        assertEquals(38.870220, trees.minutes(doubleArrayOf(60.0, 0.52, 27.0, 22.0, 0.3)), 1e-4)
        assertEquals(58.820062, trees.minutes(doubleArrayOf(60.0, 0.9, 40.0, 10.0, 0.0)), 1e-4)
        assertEquals(2.767933, trees.minutes(doubleArrayOf(0.0, 0.05, 15.0, 100.0, 1.2)), 1e-4)
        assertEquals(30.171347, trees.minutes(doubleArrayOf(60.0, 0.4, 27.0, Double.NaN, Double.NaN)), 1e-4)
        assertEquals(29.586991, trees.minutes(doubleArrayOf(30.0, 0.6, 35.0, 50.0, 0.1)), 1e-4)
    }

    /**
     * Norden, 11 Oct 2026, 13–14 h (best match): 0.3 mm, cloud 1 % → 22 %, mean direct beam
     * 270 W/m² – Open-Meteo's sunshine the full hour. Read with the forecast: about half an hour,
     * and the symbol showers.
     */
    @Test fun anHourOfShowersReadWithTheForecast() {
        val start = Instant.parse("2026-10-11T11:00:00Z").epochSecond
        val root = JsonCodec.parseToJsonElement(
            """{"latitude":53.6,"longitude":7.2,"timezone":"Europe/Berlin","utc_offset_seconds":7200,
               "hourly":{"time":[$start,${start + 3600}],"temperature_2m":[12,12],"precipitation":[0.0,0.3],
               "weather_code":[3,61],"is_day":[1,1],"cloud_cover":[1,22],"sunshine_duration":[3600,3600],
               "direct_normal_irradiance":[600,270.4]}}""",
        )
        val hour = OpenMeteoSource.parseForecast(root).hourly.single { it.time == (start + 3600) * 1000 }
        assertTrue("sunshine ${hour.sunshine}", hour.sunshine!! in 20.0..36.0)
        assertEquals(Condition.SHOWERS, hour.condition)
    }

    /** Without the direct beam (an older answer, another source): the model's own sunshine. */
    @Test fun withoutTheDirectBeamTheModelsOwn() {
        val end = Instant.parse("2026-10-11T12:00:00Z").toEpochMilli()
        assertEquals(60.0, ForecastSunshine.minutes(60.0, null, end, 53.6, 7.2, 1.0, 22.0, 0.3)!!, 0.0)
    }
}
