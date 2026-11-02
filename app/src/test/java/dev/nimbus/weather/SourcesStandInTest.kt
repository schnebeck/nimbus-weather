/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SourcesStandInTest.kt
 * A source of the page that fails: its last answer stands in while it stands for now, its part out
 * of date (the cards yellow) and asked again – the page does not jump with each load.
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

import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.SpotSource
import dev.nimbus.weather.data.repo.StandIn
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.main.cardStatus
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SourcesStandInTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val server = MockWebServer()
    /** The sources failing now (500 from their server). */
    private val failing = mutableSetOf<String>()
    // the Bright Sky fixture: Berlin-Tempelhof, 5.8 km, at 20:30 UTC
    private val fixtureNow = Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()
    private val berlin = Place("b", "Berlin", latitude = 52.52, longitude = 13.40)
    /** The forecast fixtures' first hour. */
    private val start = 1_790_625_600L * 1000

    /** A warning in force until two hours after the first load, another until a quarter of an hour after it. */
    private val alertsBody = """{"alerts":[
        {"id":1,"alert_id":"long","status":"actual","severity":"moderate","event_de":"WINDBÖEN","headline_de":"Amtliche WARNUNG vor WINDBÖEN",
         "description_de":"Böen","onset":"2026-09-28T18:00:00Z","expires":"2026-09-28T22:45:00Z"},
        {"id":2,"alert_id":"short","status":"actual","severity":"minor","event_de":"FROST","headline_de":"Amtliche WARNUNG vor FROST",
         "description_de":"Frost","onset":"2026-09-28T18:00:00Z","expires":"2026-09-28T21:00:00Z"}
    ]}"""

    /** MOSMIX at Tempelhof: overcast for three days. */
    private val mosmixBody = """{"weather":[""" + (0 until 72).joinToString(",") { k ->
        """{"timestamp":"${Instant.ofEpochMilli(start + k * hour)}","source_id":7,"sunshine":0,"cloud_cover":100,"condition":"dry"}"""
    } + """],"sources":[{"id":7,"observation_type":"forecast","station_name":"BERLIN-TEMPELHOF","distance":4000,"height":48.0}]}"""

    /** The satellite's hours of the day: sunshine in each. */
    private val satelliteBody = run {
        val hours = (0 until 24).map { start / 1000 + it * 3600L }
        """{"hourly":{"time":[${hours.joinToString(",")}],"sunshine_duration":[${hours.joinToString(",") { "1800" }}],""" +
            """"direct_normal_irradiance":[${hours.joinToString(",") { "300" }}],"shortwave_radiation":[${hours.joinToString(",") { "250" }}],""" +
            """"diffuse_radiation":[${hours.joinToString(",") { "80" }}]}}"""
    }

    /** ICON-D2's own chance: 77 % in each of its hours. */
    private val d2Body = run {
        val hours = (0 until 72).map { start / 1000 + it * 3600L }
        """{"hourly":{"time":[${hours.joinToString(",")}],"precipitation_probability":[${hours.joinToString(",") { "77" }}]}}"""
    }

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
                fun forecast(name: String) = ok(Fixtures.text(name).replace("\"elevation\":524.0", "\"elevation\":34.0"))
                val fail = MockResponse.Builder().code(500).build()
                val models = url.queryParameter("models")
                return when {
                    url.encodedPath == "/v1/forecast" && models == "icon_d2" -> if ("d2" in failing) fail else ok(d2Body)
                    url.encodedPath == "/v1/forecast" && models == "icon_seamless" -> forecast("openmeteo_icon.json")
                    url.encodedPath == "/v1/forecast" -> forecast("openmeteo_best.json")
                    url.encodedPath == "/v1/archive" -> if ("satellite" in failing) fail else ok(satelliteBody)
                    url.encodedPath == "/current_weather" -> if ("station" in failing) fail else ok(Fixtures.text("brightsky_current.json"))
                    url.encodedPath == "/weather" && url.queryParameter("lat") != null -> if ("mosmix" in failing) fail else ok(mosmixBody)
                    url.encodedPath == "/alerts" -> if ("alerts" in failing) fail else ok(alertsBody)
                    // the radar over the place: outside its area – nothing to stand in for
                    url.encodedPath == "/radar" -> ok("""{"type":"FeatureCollection","features":[]}""")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    /**
     * A load at [at]; [previous] as the store gives it back (written and read again – the last
     * answers must come through the store).
     */
    private fun load(previous: WeatherData? = null, at: Long = fixtureNow, place: Place = berlin, model: ForecastModel = ForecastModel.BEST_MATCH): WeatherData = runBlocking {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        val stored = previous?.let { JsonCodec.decodeFromString(WeatherData.serializer(), JsonCodec.encodeToString(WeatherData.serializer(), it)) }
        WeatherRepository(
            OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
            clock = { at }, spot = SpotSource(http, wms = "$base/radar", satellite = base),
        ).load(place, Settings(model = model), previous = stored, refresh = setOf(DataPart.FORECAST))
    }

    /** Every source answering: all of the page's parts current, the answers kept for the next load. */
    @Test fun allAnsweredAllCurrent() {
        val d = load()
        assertTrue(d.stale.toString(), d.stale.isEmpty())
        for (p in listOf(DataPart.STATION, DataPart.SATELLITE, DataPart.MOSMIX, DataPart.ALERTS)) assertEquals(p.name, fixtureNow, d.fetchedAt(p))
        assertEquals("Berlin-Tempelhof", d.last.stations.single().stationName)
        assertEquals("BERLIN-TEMPELHOF", d.last.mosmix!!.site.name)
        assertEquals(24, d.last.satellite.size)
        assertEquals(2, d.alerts.size)
    }

    /**
     * The station fails: its last readings stand in – the values now, the line "measured at"
     * unchanged; the tiles yellow, the part asked again, the ten days' card still green.
     */
    @Test fun theStationsLastReadingsStandIn() {
        val before = load()
        failing += "station"
        val after = load(before, fixtureNow + 10 * min)
        assertEquals(before.current.temperature, after.current.temperature, 0.0)
        assertEquals("Berlin-Tempelhof", after.current.stationName)
        assertTrue(after.sources.any { it.kind == SourceKind.STATION })
        assertEquals(setOf(DataPart.STATION), after.stale)
        assertEquals(fixtureNow, after.fetchedAt(DataPart.STATION))
        assertEquals(CardStatus.STALE, cardStatus("tiles", after.stale))
        assertEquals(CardStatus.STALE, cardStatus("tile-wind", after.stale))
        assertEquals(CardStatus.FRESH, cardStatus("daily", after.stale))
        // back again: current
        failing.clear()
        assertTrue(load(after, fixtureNow + 12 * min).stale.isEmpty())
    }

    /** Half an hour on the readings no longer tell of now: the model's values, still yellow. */
    @Test fun oldReadingsStandInNoLonger() {
        val before = load()
        failing += "station"
        val later = load(before, fixtureNow + StandIn.STATION_MS + min)
        assertNull(later.current.stationName)
        assertTrue(DataPart.STATION in later.stale)
    }

    /** MOSMIX fails: the sky of the hours stays weighed with its last run; the hourly and the ten days' cards yellow. */
    @Test fun mosmixsLastRunStandsIn() {
        val before = load()
        failing += "mosmix"
        val after = load(before, fixtureNow + 10 * min)
        assertEquals(before.hourly.map { it.time to it.cloudCover }, after.hourly.map { it.time to it.cloudCover })
        assertTrue(after.sources.any { it.kind == SourceKind.MOSMIX })
        assertEquals(setOf(DataPart.MOSMIX), after.stale)
        assertEquals(CardStatus.STALE, cardStatus("hourly", after.stale))
        assertEquals(CardStatus.STALE, cardStatus("daily", after.stale))
        assertEquals(CardStatus.FRESH, cardStatus("precip", after.stale))
    }

    /** The satellite fails: its last hours stand in for the sky now; the part yellow. */
    @Test fun theSatellitesLastHoursStandIn() {
        val before = load()
        failing += "satellite"
        val after = load(before, fixtureNow + 10 * min)
        assertEquals(before.last.satellite, after.last.satellite)
        assertEquals(before.last.satelliteAt, after.last.satelliteAt)
        assertEquals(before.current.condition, after.current.condition)
        assertEquals(before.current.sky, after.current.sky)
        assertEquals(setOf(DataPart.SATELLITE), after.stale)
    }

    /**
     * The warnings fail: those of the last answer still in force stay – a warning does not vanish
     * for a load; one that has run out goes. The warnings' card yellow.
     */
    @Test fun theWarningsInForceStay() {
        val before = load()
        failing += "alerts"
        val after = load(before, fixtureNow + 30 * min)
        assertEquals(listOf("long"), after.alerts.map { it.id })
        assertEquals(setOf(DataPart.ALERTS), after.stale)
        assertEquals(CardStatus.STALE, cardStatus("alerts", after.stale))
    }

    /** DWD ICON's own chance fails: its last one stands in – the chances do not jump; the forecast yellow, asked again. */
    @Test fun theD2ChanceStandsIn() {
        val before = load(model = ForecastModel.DWD_ICON)
        val hour = before.hourly.first { it.time > fixtureNow }
        assertEquals(77.0, hour.precipitationProbability!!, 0.0)
        failing += "d2"
        val after = load(before, fixtureNow + 10 * min, model = ForecastModel.DWD_ICON)
        assertEquals(77.0, after.hourly.first { it.time == hour.time }.precipitationProbability!!, 0.0)
        assertEquals(setOf(DataPart.FORECAST), after.stale)
    }

    /** All failing at once on a first load: nothing to stand in – the model's values, every part yellow. */
    @Test fun nothingToStandInOnAFirstLoad() {
        failing += listOf("station", "mosmix", "satellite", "alerts")
        val d = load()
        assertNull(d.current.stationName)
        assertTrue(d.alerts.isEmpty())
        assertEquals(setOf(DataPart.STATION, DataPart.SATELLITE, DataPart.MOSMIX, DataPart.ALERTS), d.stale)
        assertFalse(d.sources.any { it.kind == SourceKind.MOSMIX })
    }

    /** "My location" a few hundred metres on: the same spot – the last answers stand in. */
    @Test fun myLocationAFewMetresOn() {
        val here = berlin.copy(id = "current", isCurrentLocation = true)
        val before = load(place = here)
        failing += "station"
        val moved = load(before, fixtureNow + 10 * min, place = here.copy(latitude = here.latitude + 0.003))
        assertEquals("Berlin-Tempelhof", moved.current.stationName)
    }
}
