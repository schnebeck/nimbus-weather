/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SkyNowTest.kt
 * Station and forecast never disagree on the sky now: the sky now by the same rule as the hours, the station's sunshine first – and where each value comes from. Norden,
 * 5 October 2026, 16:30 (MET Nordic: overcast under a veil, an hour of sun).
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

import androidx.compose.ui.test.junit4.createComposeRule
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.NowValue
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.StationObservation
import dev.nimbus.weather.data.repo.NowWeather
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.asNow
import dev.nimbus.weather.ui.main.nowSourcesText
import dev.nimbus.weather.ui.main.toMeteo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "de")
class SkyNowTest {
    @get:Rule val compose = createComposeRule()

    private val model = OpenMeteoSource.parseForecast(Fixtures.json("openmeteo_norden_metno_nordic_veil.json"))
    private val now = model.current!!.time
    private val station: StationObservation =
        requireNotNull(BrightSkySource.parseCurrent(Fixtures.json("brightsky_current_norden.json"))?.forPlace(14.0, model.current!!.temperature))

    /** The model now: overcast (its code 3, 98 % cloud) – under a veil the sun shone the whole hour. */
    @Test fun theRecording() {
        assertEquals(Condition.CLOUDY, model.current!!.condition)
        assertEquals(60.0, station.sunshine!!, 0.01)
        assertEquals("Norderney", station.stationName)
    }

    /** "Now" is the measurement, the sky too: the station's full sunshine – sunny, as the hour. */
    @Test fun theStationsSunshineDecidesTheSky() {
        val c = NowWeather.mergeObservation(model.current!!, station)
        assertEquals(Condition.CLEAR, c.condition)
        assertEquals(SkyBasis.MEASURED_SUNSHINE, c.sky)
        assertEquals("Norderney", c.measured.single { it.value == NowValue.SKY }.station)
    }

    /** Little sun measured: the sky stays as it was – the model's sunshine is not asked then. */
    @Test fun littleSunMeasuredKeepsTheSky() {
        val c = NowWeather.mergeObservation(model.current!!, station.copy(sunshine = 10.0))
        assertEquals(Condition.CLOUDY, c.condition)
        assertEquals(Condition.CLOUDY, NowWeather.withModelSunshine(c, model.hourly, now).condition)
    }

    /** Without a measurement, the sunshine of the model's hour running now: sunny, as that hour. */
    @Test fun withoutAMeasurementTheModelsSunshine() {
        val running = NowWeather.runningHour(model.hourly, now)!!
        assertEquals(57.4, running.sunshine!!, 0.1)
        val c = NowWeather.withModelSunshine(model.current!!, model.hourly, now)
        assertEquals(Condition.CLEAR, c.condition)
        assertEquals(SkyBasis.MODEL_SUNSHINE, c.sky)
        // the header and the hour running now follow the same rule
        assertEquals(running.condition, c.condition)
    }

    /** At night there is no sunshine to measure: the sky stays the model's. */
    @Test fun atNightNoSunshine() {
        val night = model.current!!.copy(isDay = false)
        val c = NowWeather.mergeObservation(night, station.copy(sunshine = 0.0))
        assertEquals(Condition.CLOUDY, c.condition)
        assertEquals(SkyBasis.MODEL, c.sky)
    }

    /**
     * "Now" in the chart and the header always agree: the hour running now (16:00–17:00, its values
     * at 17:00) shows the header's sky, the others the forecast's.
     */
    @Test fun theHourRunningNowShowsTheHeader() {
        val points: List<MeteoPoint> = model.hourly.map { it.toMeteo().copy(condition = Condition.CLOUDY) }.map { it.asNow(now, Condition.RAIN) }
        val running = points.single { it.condition == Condition.RAIN }
        assertEquals(now + 30 * 60_000L, running.time)
        assertTrue(points.filter { it !== running }.all { it.condition == Condition.CLOUDY })
    }

    /** The "measured at …" note tells in the info view, value by value, where each comes from. */
    @Test fun eachValueWithItsSource() {
        val c = NowWeather.mergeObservation(model.current!!, station.copy(humidity = null))
        var text = ""
        var stored = "?"
        compose.setContent { text = nowSourcesText(c); stored = nowSourcesText(c.copy(measured = emptyList())) }
        compose.waitForIdle()
        val lines = text.lines()
        assertTrue(text, "• Temperatur: gemessen, Norderney (13,4 km)" in lines || "• Temperatur: gemessen, Norderney (13,4 km)" in lines)
        assertTrue(text, "• Luftfeuchte: Vorhersagemodell" in lines)
        assertTrue(text, lines.any { it.startsWith("• Himmel: nach dem gemessenen Sonnenschein, Norderney") })
        // weather stored before the values were noted: nothing to say
        assertEquals("", stored)
    }
}
