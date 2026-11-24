/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/BackgroundLoadTest.kt
 * A load without the app shown: it does not wait long for the radar over the place.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Fable 5.2 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Fable 5.2 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */


package dev.nimbus.weather

import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.SpotSource
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackgroundLoadTest {
    private val min = 60_000L
    private val fixtureNow = Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()
    private val server = MockWebServer()
    private val berlin = Place("b", "Berlin", latitude = 52.52, longitude = 13.40)
    /** How long the radar's service takes to answer. */
    private val radarMs = 1_500L

    private fun features(times: List<Long>, key: String, value: Double) = times.joinToString(",", "{\"features\":[", "]}") { t ->
        "{\"properties\":{\"$key\":$value,\"TIME\":\"${Instant.ofEpochMilli(t)}\",\"REFERENCE_TIME\":\"${Instant.ofEpochMilli(fixtureNow - 5 * min)}\"}}"
    }

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                val layer = url.queryParameter("layers").orEmpty()
                fun slow(body: String) = MockResponse.Builder().code(200).body(body).headersDelay(radarMs, TimeUnit.MILLISECONDS).build()
                return when {
                    url.encodedPath == "/radar" && layer.endsWith("RY") -> slow(features((0..12).map { fixtureNow - 5 * min - it * 5 * min }, "GRAY_INDEX", 0.2))
                    url.encodedPath == "/radar" -> slow(features((0..24).map { fixtureNow + it * 5 * min }, "WN_ANALYSIS", 30.0))
                    url.encodedPath == "/v1/forecast" -> MockResponse.Builder().code(200).body(Fixtures.text("openmeteo_best.json")).build()
                    url.encodedPath == "/current_weather" -> MockResponse.Builder().code(200).body(Fixtures.text("brightsky_current.json")).build()
                    url.encodedPath == "/alerts" -> MockResponse.Builder().code(200).body("{\"alerts\":[]}").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    /** The load and how long it took (ms). */
    private fun load(background: Boolean): Pair<WeatherData, Long> = runBlocking {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        val repository = WeatherRepository(
            OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
            clock = { fixtureNow }, spot = SpotSource(http, wms = "$base/radar"), backgroundWaitMs = 200,
        )
        val started = System.nanoTime()
        val data = repository.load(berlin, Settings(), refresh = setOf(DataPart.FORECAST), background = background)
        data to (System.nanoTime() - started) / 1_000_000
    }

    @Test fun withoutTheAppShownTheLoadDoesNotWaitForASlowRadar() {
        val (data, took) = load(background = true)
        assertTrue("the load took $took ms", took < radarMs - 200)
        assertNull(data.radarNow)
        assertTrue(data.hourly.isNotEmpty())
    }

    @Test fun withTheAppShownTheRadarIsWaitedFor() {
        val (data, took) = load(background = false)
        assertTrue("the load took $took ms", took >= radarMs)
        assertNotNull(data.radarNow)
    }
}
