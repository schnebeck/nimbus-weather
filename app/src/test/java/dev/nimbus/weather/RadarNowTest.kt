/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarNowTest.kt
 * The sky now by the radar over the place: it beats a distant station and the model, comes after the page
 * without holding it, and a late answer keeps the last one.
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
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.MeasuredValue
import dev.nimbus.weather.data.model.NowValue
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.SpotSource
import dev.nimbus.weather.data.repo.NowWeather
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * "Radar > distant station" always holds – but the page neither waits for the radar nor changes
 * the source of its sky with every load because an answer came late.
 */
class RadarNowTest {
    private val min = 60_000L

    // ---- the rule

    private fun now(condition: Condition, sky: SkyBasis, km: Double?) = CurrentWeather(
        0L, 12.0, 11.0, condition, true, 80.0, 8.0, 1015.0, 10.0, 20.0, 200.0, 90.0, 20_000.0, 1.0, 0.0,
        stationName = km?.let { "Alfeld" }, stationDistanceKm = km,
        measured = listOf(MeasuredValue(NowValue.TEMPERATURE, "Alfeld", km ?: 0.0), MeasuredValue(NowValue.SKY, "Alfeld", km ?: 0.0)),
        sky = sky,
    )
    private val t = 1_000L * min

    /** RY's newest three 5-minute steps as a rate, stamped with the newest. */
    @Test fun theRadarsRateNow() {
        val ry = SpotSource.series(Fixtures.json("radolan_ry_kiel.json")).filterKeys { it <= Instant.parse("2026-10-06T17:55:00Z").toEpochMilli() }
        val r = SpotSource.now(ry)!!
        assertEquals(Instant.parse("2026-10-06T17:55:00Z").toEpochMilli(), r.time)
        // (0.01 + 0.29 + 0.09) / 3 per 5 minutes
        assertEquals(1.56, r.mmPerHour, 1e-9)
        assertNull(SpotSource.now(emptyMap()))
    }

    /** A station 17 km away sees drizzle, the radar nothing over the place: the place is dry. */
    @Test fun theRadarBeatsADistantStation() {
        val c = NowWeather.withRadar(now(Condition.DRIZZLE, SkyBasis.OBSERVED, 17.4), RadarNow(t - 10 * min, 0.0), t)
        assertEquals(Condition.CLOUDY, c.condition)
        assertEquals(SkyBasis.RADAR, c.sky)
        // the station no longer stands for the sky; its temperature does
        assertTrue(c.measured.none { it.value == NowValue.SKY })
        assertTrue(c.measured.any { it.value == NowValue.TEMPERATURE })
    }

    /** And the model: dry by the model, 3 mm/h by the radar – rain. */
    @Test fun theRadarBeatsTheModel() {
        val c = NowWeather.withRadar(now(Condition.PARTLY_CLOUDY, SkyBasis.MODEL, null), RadarNow(t - 5 * min, 3.0), t)
        assertEquals(Condition.RAIN, c.condition)
        assertEquals(SkyBasis.RADAR, c.sky)
    }

    /** A station within the radar's pixel measures the place itself. */
    @Test fun aStationWithinThePixelStays() {
        val near = now(Condition.DRIZZLE, SkyBasis.OBSERVED, 0.6)
        assertEquals(near, NowWeather.withRadar(near, RadarNow(t - 5 * min, 0.0), t))
    }

    /** Thunder stays; the radar agreeing changes nothing (nor the named source). */
    @Test fun thunderAndAgreementStay() {
        val storm = now(Condition.THUNDERSTORM, SkyBasis.OBSERVED, 17.4)
        assertEquals(storm, NowWeather.withRadar(storm, RadarNow(t, 0.0), t))
        val dry = now(Condition.CLOUDY, SkyBasis.OBSERVED, 17.4)
        assertEquals(dry, NowWeather.withRadar(dry, RadarNow(t, 0.0), t))
    }

    /** The radar's step stands for half an hour – older, the station's sky is back. */
    @Test fun theRadarStandsForHalfAnHour() {
        val station = now(Condition.DRIZZLE, SkyBasis.OBSERVED, 17.4)
        assertEquals(SkyBasis.RADAR, NowWeather.withRadar(station, RadarNow(t - 29 * min, 0.0), t).sky)
        assertEquals(station, NowWeather.withRadar(station, RadarNow(t - 31 * min, 0.0), t))
    }

    // ---- the load

    private val server = MockWebServer()
    /** How the radar answers: its delay, or not at all (500). */
    private var radarDelayMs = 0L
    private var radarFails = false
    // the Bright Sky fixture: Berlin-Tempelhof, 5.8 km, dry under 88 % cloud at 20:30 UTC
    private val fixtureNow = Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()
    private val berlin = Place("b", "Berlin", latitude = 52.52, longitude = 13.40)

    /** RY at the place: 0.25 mm in each of the last three 5-minute steps – 3 mm/h. */
    private fun ry(): String {
        val steps = (1..3).joinToString(",") { k ->
            val time = Instant.ofEpochMilli(fixtureNow - k * 5 * min)
            """{"type":"Feature","properties":{"GRAY_INDEX":0.25,"TIME":"$time"}}"""
        }
        return """{"type":"FeatureCollection","features":[$steps]}"""
    }

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                return when {
                    url.encodedPath == "/radar" && radarFails -> MockResponse.Builder().code(500).build()
                    url.encodedPath == "/radar" -> MockResponse.Builder().code(200).body(ry())
                        .headersDelay(radarDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                    url.encodedPath == "/v1/forecast" -> MockResponse.Builder().code(200)
                        .body(Fixtures.text("openmeteo_best.json").replace("\"elevation\":524.0", "\"elevation\":34.0")).build()
                    url.encodedPath == "/current_weather" -> ok("brightsky_current.json")
                    url.encodedPath == "/alerts" -> MockResponse.Builder().code(200).body("""{"alerts":[]}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun repo(): WeatherRepository {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return WeatherRepository(
            OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
            clock = { fixtureNow }, spot = SpotSource(http, wms = "$base/radar"),
        )
    }

    /** Loads with only the forecast asked for; every page shown, in order. */
    private fun load(previous: WeatherData? = null): List<WeatherData> = runBlocking {
        val pages = mutableListOf<WeatherData>()
        repo().load(berlin, Settings(), german = true, previous = previous, refresh = setOf(dev.nimbus.weather.data.model.DataPart.FORECAST)) {
            synchronized(pages) { pages += it }
        }
        pages
    }

    /** Rain by the radar over Berlin while Tempelhof, 5.8 km away, reports it dry. */
    @Test fun theRadarDecidesNow() {
        val last = load().last()
        assertEquals(Condition.RAIN, last.current.condition)
        assertEquals(SkyBasis.RADAR, last.current.sky)
        assertEquals(3.0, last.radarNow!!.mmPerHour, 1e-9)
        assertTrue(last.sources.any { it.kind == SourceKind.RADAR_NOW })
    }

    /** A slow radar does not hold the page: it shows with the sky without it, the radar's follows. */
    @Test fun thePageDoesNotWaitForTheRadar() {
        radarDelayMs = 3_000L
        val started = System.nanoTime()
        val pages = load()
        val first = pages.first()
        assertTrue(first.current.sky != SkyBasis.RADAR)
        assertNull(first.radarNow)
        assertEquals(SkyBasis.RADAR, pages.last().current.sky)
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 3_000L)
    }

    /** No answer this time: the last one, still standing for now, keeps the sky – from the first page on. */
    @Test fun aLateAnswerKeepsTheLastOne() {
        val before = load().last()
        radarFails = true
        val pages = load(previous = before)
        pages.forEach { assertEquals(SkyBasis.RADAR, it.current.sky) }
        assertEquals(before.radarNow, pages.last().radarNow)
    }

    /** The last answer older than half an hour stands for nothing: the sky without it. */
    @Test fun anOldAnswerStandsForNothing() {
        val before = load().last().let { it.copy(radarNow = it.radarNow!!.copy(time = fixtureNow - 31 * min)) }
        radarFails = true
        val last = load(previous = before).last()
        assertTrue(last.current.sky != SkyBasis.RADAR)
        assertNull(last.radarNow)
    }
}
