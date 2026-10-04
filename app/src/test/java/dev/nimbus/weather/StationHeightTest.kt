/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StationHeightTest.kt
 * A measurement stands for a place only if it was taken at the place's height: on the Zugspitze
 * (2962 m) the app showed 16 °C "measured at Zugspitze" – Bright Sky had filled in the temperature
 * of Garmisch (719 m), because the summit's 10-minute report had none. Recorded answers of
 * 4 October 2026.
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
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.Reading
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationHeightTest {
    private val summit = 2924.0                    // Open-Meteo's height of the place
    private val current get() = BrightSkySource.parseCurrent(Fixtures.json("brightsky_current_zugspitze.json"))!!
    private val hours get() = BrightSkySource.parseStationHours(Fixtures.json("brightsky_hours_zugspitze.json"))

    /** The answer as it came: the summit station, its temperature from the valley. */
    @Test fun theFilledInValuesNameTheirStation() {
        val obs = current
        assertEquals("Zugspitze", obs.stationName)
        assertEquals(16.1, obs.temperature!!, 0.01)
        val valley = obs.siteOf(Reading.TEMPERATURE)
        assertEquals("Garmisch-Partenkirch", valley.name)
        assertEquals(719.3, valley.heightM!!, 0.1)
        assertEquals(valley, obs.siteOf(Reading.DEW_POINT))
        assertEquals(2956.0, obs.siteOf(Reading.WIND_SPEED).heightM!!, 0.1)   // the summit's own
    }

    /** Without the summit's own hour: no temperature "measured at Zugspitze" – the model's stays. */
    @Test fun theSummitTakesNoValleyTemperature() {
        assertNull(current.forPlace(summit, modelTemperature = 2.7))
        // not even if the model were as warm: the height alone decides
        assertNull(current.forPlace(summit, modelTemperature = 16.0))
    }

    /** The summit's own hourly report has the temperature its 10-minute report left out. */
    @Test fun theStationsOwnHourFillsTheGap() {
        val filled = current.filledFrom(hours)
        assertEquals(2.4, filled.temperature!!, 0.01)              // 17:00 UTC, the latest before 17:30
        assertEquals(-1.6, filled.dewPoint!!, 0.01)
        assertEquals(75.0, filled.humidity!!, 0.01)
        assertEquals("Zugspitze", filled.siteOf(Reading.TEMPERATURE).name)
        val shown = filled.forPlace(summit, modelTemperature = 2.7)!!
        assertEquals("Zugspitze", shown.stationName)
        assertEquals(2.4, shown.temperature!!, 0.01)
        // the precipitation stays Garmisch's (the summit's hour has none of its own): not taken
        assertNull(shown.precipitation60)
    }

    /** The other way round: in Garmisch the valley station measures, the summit does not. */
    @Test fun theValleyTakesNoSummitValues() {
        val shown = current.forPlace(719.0, modelTemperature = 15.0)!!
        assertEquals(16.1, shown.temperature!!, 0.01)
        assertEquals("Garmisch-Partenkirch", shown.stationName)     // named after the temperature's station
        assertEquals(9.0, shown.distanceKm, 0.1)
        assertNull(shown.windSpeed)                                 // the summit's wind
        assertNull(shown.visibility)
        assertTrue(!shown.observedDry && shown.condition == null)
    }

    /** A temperature ten degrees off the model's is from somewhere else – its station gives nothing. */
    @Test fun farOffTheModelIsNotTaken() {
        val filled = current.filledFrom(hours)
        assertNull(filled.forPlace(summit, modelTemperature = 13.0))
        assertNotNull(filled.forPlace(summit, modelTemperature = 12.0))
    }

    /** The whole way: the Zugspitze page shows the summit's 2.4 °C, not the valley's 16.1 °C. */
    @Test fun theZugspitzePageShowsTheSummit() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
                return when {
                    // a forecast recorded in Munich, here for the summit: its height and its 2.7 °C
                    url.encodedPath == "/v1/forecast" -> ok(
                        Fixtures.text(if (url.queryParameter("models") == "icon_seamless") "openmeteo_icon.json" else "openmeteo_best.json")
                            .replace("\"elevation\":524.0", "\"elevation\":$summit")
                            .replace("\"temperature_2m\":16.0", "\"temperature_2m\":2.7"),
                    )
                    url.encodedPath == "/current_weather" -> ok(Fixtures.text("brightsky_current_zugspitze.json"))
                    url.encodedPath == "/weather" && url.queryParameter("dwd_station_id") == "05792" -> ok(Fixtures.text("brightsky_hours_zugspitze.json"))
                    url.encodedPath == "/alerts" -> ok("""{"alerts":[]}""")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        try {
            val http = OkHttpClient()
            val base = server.url("/").toString().trimEnd('/')
            val repo = WeatherRepository(
                OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
                PollenSource(http, "$base/pollen.json", "$base/wms", base),
                clock = { java.time.Instant.parse("2026-10-04T17:45:00Z").toEpochMilli() },
            )
            val data = repo.load(Place("z", "Zugspitze", latitude = 47.4211, longitude = 10.9853), Settings(model = ForecastModel.DWD_ICON), german = true)
            assertEquals(2.4, data.current.temperature, 0.01)
            assertEquals("Zugspitze", data.current.stationName)
            assertNotEquals(16.1, data.current.temperature, 0.5)
        } finally {
            server.close()
        }
    }

    /** The past days: the values of the valley stations Bright Sky filled in are left out. */
    @Test fun theHistoryTakesOnlyTheSummit() {
        val json = Fixtures.json("brightsky_history_zugspitze.json")
        val at = java.time.OffsetDateTime.parse("2026-10-03T10:00:00+02:00").toInstant().toEpochMilli()
        // the hourly record of the summit (historical source): its condition and precipitation from Griesen, 825 m
        val all = HistorySource.parseObservations(json).byTime.getValue(at)
        val summitOnly = HistorySource.parseObservations(json, summit).byTime.getValue(at)
        assertNotNull(all.precipitation)
        assertNull(summitOnly.precipitation)
        assertEquals(all.temperature, summitOnly.temperature)
        // the station named is the summit
        assertEquals("Zugspitze", HistorySource.parseObservations(json, summit).station)
        // the recent hours: precipitation from the Zugspitzplatt, 500 m lower – left out too
        val recent = java.time.OffsetDateTime.parse("2026-10-04T18:00:00+02:00").toInstant().toEpochMilli()
        assertNull(HistorySource.parseObservations(json, summit).byTime.getValue(recent).precipitation)
        assertEquals(Condition.FOG, HistorySource.parseObservations(json, summit).byTime.getValue(recent).condition)
    }

    /** Citizen sensors: only the ones at the place's height count. */
    @Test fun sensorsElsewhereInHeightAreLeftOut() {
        val json = Fixtures.json("sensor_community.json")      // Hannover, 35–60 m
        assertNotNull(CommunitySource.aggregate(json, 3.0, elevationM = 50.0))
        assertNull(CommunitySource.aggregate(json, 3.0, elevationM = 524.0))
    }
}
