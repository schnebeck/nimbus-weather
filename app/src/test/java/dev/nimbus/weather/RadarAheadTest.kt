/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarAheadTest.kt
 * The next two hours by the radar over the place: its nowcast read, and put in the model's place in the
 * 15-minute steps and the hours it covers.
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
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.SpotSource
import dev.nimbus.weather.data.repo.RadarAhead
import dev.nimbus.weather.data.repo.WeatherRepository
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

/**
 * The DWD's nowcast (WN: reflectivity every 5 minutes, two hours from each analysis) at the place
 * takes the model's place for the hours it covers – with the radar's measured steps of the hour
 * already running. Where a stretch lacks a step, the model stays.
 */
class RadarAheadTest {
    private val min = 60_000L
    private val hour = 60 * min
    /** 14:20 UTC: the running hour ends at 15:00. */
    private val t0 = Instant.parse("2026-10-14T14:00:00Z").toEpochMilli()
    private val now = t0 + 20 * min

    /** Light rain about 1 mm/h is 23 dBZ; 8 dBZ is the weakest echo counted, below it nothing. */
    @Test fun rainFromReflectivity() {
        assertEquals(1.0, SpotSource.rate(23.0), 0.01)
        assertEquals(0.0, SpotSource.rate(7.9), 0.0)
        assertTrue(SpotSource.rate(8.0) > 0.0)
        assertEquals(0.0, SpotSource.rate(-64.0), 0.0)
        // heavy: 46 dBZ about 27 mm/h
        assertEquals(27.3, SpotSource.rate(46.0), 0.3)
    }

    /** The answer as the DWD gives it (recorded at Groß Düngen, 14 Oct 2026, dry), and with rain. */
    @Test fun theNowcastIsRead() {
        fun feature(time: String, dbz: Double) =
            """{"type":"Feature","geometry":null,"properties":{"WN_ANALYSIS":$dbz,"TIME":"$time","REFERENCE_TIME":"2026-10-14T05:20:00Z"}}"""
        val json = """{"type":"FeatureCollection","features":[${feature("2026-10-14T05:25:00Z", -64.0029296875)},${feature("2026-10-14T05:30:00Z", 23.0)}]}"""
        val (issue, steps) = SpotSource.nowcast(JsonCodec.parseToJsonElement(json))!!
        assertEquals(Instant.parse("2026-10-14T05:20:00Z").toEpochMilli(), issue)
        assertEquals(0.0, steps.getValue(Instant.parse("2026-10-14T05:25:00Z").toEpochMilli()), 0.0)
        // 1 mm/h for 5 minutes
        assertEquals(1.0 / 12, steps.getValue(Instant.parse("2026-10-14T05:30:00Z").toEpochMilli()), 0.001)
        assertNull(SpotSource.nowcast(JsonCodec.parseToJsonElement("""{"type":"FeatureCollection","features":[]}""")))
    }

    /**
     * Steps from 14:05 to 16:15: dry until 15:00 (measured to 14:15, the nowcast after), 1 mm/h
     * from 15:05 to 16:00, nothing known after 16:15.
     */
    private fun radar(): RadarNow {
        val steps = (1..27).associate { k -> val t = t0 + k * 5 * min; t to (if (t > t0 + hour && t <= t0 + 2 * hour) 1.0 / 12 else 0.0) }
        return RadarNow(t0 + 15 * min, 0.0, steps, issue = t0 + 15 * min)
    }

    private fun hourOf(end: Long, c: Condition, mm: Double, chance: Double) =
        HourlyPoint(end, 12.0, condition = c, isDay = true, precipitation = mm, precipitationProbability = chance, cloudCover = 95.0, sunshine = 0.0)

    /** The model's hours: rain until 15:00, dry 15–16, rain 16–17 – the radar the other way round until 16:00. */
    private val model = listOf(
        hourOf(t0, Condition.RAIN, 1.2, 80.0),
        hourOf(t0 + hour, Condition.RAIN, 2.0, 90.0),
        hourOf(t0 + 2 * hour, Condition.CLOUDY, 0.0, 10.0),
        hourOf(t0 + 3 * hour, Condition.RAIN, 1.5, 70.0),
    )

    @Test fun theHoursByTheRadar() {
        val hours = RadarAhead.hours(model, radar(), now).associateBy { it.time }
        // the hour over: as it was
        assertEquals(model[0], hours.getValue(t0))
        // the running hour (14–15 h): the radar's measured steps and its nowcast – dry, no rain symbol
        val running = hours.getValue(t0 + hour)
        assertEquals(0.0, running.precipitation!!, 0.0)
        assertEquals(Condition.CLOUDY, running.condition)
        assertNull(running.precipitationProbability)
        assertTrue(running.radar)
        // 15–16 h: 1 mm by the nowcast where the model had it dry
        val next = hours.getValue(t0 + 2 * hour)
        assertEquals(1.0, next.precipitation!!, 0.01)
        assertEquals(Condition.RAIN, next.condition)
        assertTrue(next.radar)
        // 16–17 h: the nowcast ends at 16:15 – the model's hour stays, with its chance
        assertEquals(model[3], hours.getValue(t0 + 3 * hour))
        assertFalse(hours.getValue(t0 + 3 * hour).radar)
    }

    /** The 15-minute steps likewise: the radar's quarter hours where it has all three steps. */
    @Test fun theQuartersByTheRadar() {
        val points = (0..12).map { k -> MinutelyPoint(t0 + k * 15 * min, 0.4) }
        val q = RadarAhead.minutely(points, radar(), now).associateBy { it.time }
        // over before now: the model's as they were
        assertEquals(0.4, q.getValue(t0).precipitation, 0.0)
        // 14:30 (14:15–14:30): dry by the radar
        assertEquals(0.0, q.getValue(t0 + 30 * min).precipitation, 0.0)
        // 15:15: a quarter of 1 mm/h
        assertEquals(0.25, q.getValue(t0 + 75 * min).precipitation, 0.01)
        // 16:30: beyond the nowcast
        assertEquals(0.4, q.getValue(t0 + 150 * min).precipitation, 0.0)
    }

    /** A radar answer older than half an hour tells nothing of the hours to come. */
    @Test fun anOldNowcastTellsNothing() {
        assertEquals(model, RadarAhead.hours(model, radar(), t0 + 46 * min))
    }

    // ---- the load

    private val server = MockWebServer()
    private var nowcastFails = false
    // the Bright Sky fixture: Berlin-Tempelhof, 5.8 km, dry at 20:30 UTC
    private val fixtureNow = Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()
    private val berlin = Place("b", "Berlin", latitude = 52.52, longitude = 13.40)

    private fun features(times: List<Long>, key: String, value: Double) = times.joinToString(",", "{\"type\":\"FeatureCollection\",\"features\":[", "]}") { t ->
        """{"type":"Feature","properties":{"$key":$value,"TIME":"${Instant.ofEpochMilli(t)}","REFERENCE_TIME":"${Instant.ofEpochMilli(fixtureNow - 5 * min)}"}}"""
    }

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                val layer = url.queryParameter("layers").orEmpty()
                return when {
                    // RY: dry for the last hour; WN: 46 dBZ for two hours
                    url.encodedPath == "/radar" && layer.endsWith("RY") ->
                        MockResponse.Builder().code(200).body(features((0..12).map { fixtureNow - 5 * min - it * 5 * min }, "GRAY_INDEX", 0.0)).build()
                    url.encodedPath == "/radar" && nowcastFails -> MockResponse.Builder().code(500).build()
                    url.encodedPath == "/radar" ->
                        MockResponse.Builder().code(200).body(features((0..24).map { fixtureNow + it * 5 * min }, "WN_ANALYSIS", 46.0)).build()
                    url.encodedPath == "/v1/forecast" -> MockResponse.Builder().code(200)
                        .body(Fixtures.text("openmeteo_best.json").replace("\"elevation\":524.0", "\"elevation\":34.0")).build()
                    url.encodedPath == "/current_weather" -> MockResponse.Builder().code(200).body(Fixtures.text("brightsky_current.json")).build()
                    url.encodedPath == "/alerts" -> MockResponse.Builder().code(200).body("""{"alerts":[]}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun load(): WeatherData = runBlocking {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        WeatherRepository(
            OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
            clock = { fixtureNow }, spot = SpotSource(http, wms = "$base/radar"),
        ).load(berlin, Settings(), german = true, refresh = setOf(DataPart.FORECAST))
    }

    /** Heavy rain in the nowcast: the next hour by it, the source named. */
    @Test fun theLoadTakesTheNowcast() {
        val data = load()
        val next = data.hourly.first { it.time > fixtureNow + 15 * min }
        assertTrue(next.radar)
        assertEquals(Condition.HEAVY_RAIN, next.condition)
        assertTrue(data.sources.any { it.kind == SourceKind.RADAR_NOWCAST })
        // dry now by the radar's measured steps
        assertEquals(0.0, data.radarNow!!.mmPerHour, 0.0)
    }

    /** Without the nowcast, what falls now still counts – the hours stay the model's. */
    @Test fun withoutTheNowcastTheNowStill() {
        nowcastFails = true
        val data = load()
        assertTrue(data.radarNow != null)
        assertTrue(data.hourly.none { it.radar && it.time > fixtureNow + hour })
        assertTrue(data.sources.none { it.kind == SourceKind.RADAR_NOWCAST })
        assertEquals(0.0, data.radarNow!!.mmPerHour, 0.0)
        assertTrue(data.radarNow!!.steps.keys.all { it <= data.radarNow!!.time })
    }
}
