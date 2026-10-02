/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RepositoryTest.kt
 * Tests for the repository with a mock web server.
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

import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private val server = MockWebServer()
    private var failForecast = false
    /** The citizen sensors answer this late (ms) – a slow extra source. */
    private var communityDelayMs = 0L
    private val requested = mutableListOf<String>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                synchronized(requested) { requested += url.encodedPath + "?" + (url.queryParameter("models") ?: "") }
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                return when {
                    url.encodedPath == "/v1/forecast" && failForecast -> MockResponse.Builder().code(500).build()
                    url.encodedPath == "/v1/forecast" && url.queryParameter("models") == "icon_seamless" -> ok("openmeteo_icon.json")
                    url.encodedPath == "/v1/forecast" -> ok("openmeteo_best.json")
                    url.encodedPath == "/v1/air-quality" -> ok("openmeteo_aq.json")
                    url.encodedPath == "/current_weather" -> ok("brightsky_current.json")
                    url.encodedPath == "/alerts" -> MockResponse.Builder().code(200).body("""{"alerts":[]}""").build()
                    url.encodedPath.startsWith("/airrohr") ->
                        MockResponse.Builder().code(200).body(Fixtures.text("sensor_community.json"))
                            .headersDelay(communityDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                    url.encodedPath == "/pollen.json" -> ok("dwd_pollen.json")
                    url.encodedPath == "/wms" -> ok("dwd_pollen_region.json")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun repo(clock: Long, extrasDeadlineMs: Long = 60_000L): WeatherRepository {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return WeatherRepository(
            OpenMeteoSource(http, base, base, base),
            BrightSkySource(http, base),
            CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
            clock = { clock },
            extrasDeadlineMs = extrasDeadlineMs,
        )
    }

    private val berlin = Place("b", "Berlin", latitude = 52.52, longitude = 13.40)
    private val paris = Place("p", "Paris", latitude = 48.85, longitude = 2.35)

    // Fixture timestamp of the Bright Sky observation is 2026-09-28T20:30Z.
    private val fixtureNow = java.time.Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()

    @Test
    fun `combines all sources for a German location`() = runTest {
        val data = repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON), german = true)
        assertEquals("Berlin-Tempelhof", data.current.stationName)
        assertEquals(10, data.daily.size)                      // ICON (8 days) + best match fill
        assertNotNull(data.airQuality)
        assertNotNull(data.community)
        assertEquals(dev.nimbus.weather.data.model.PollenSourceKind.DWD, data.pollen?.source)
        assertTrue(data.pollen!!.region!!.startsWith("Geest"))
        assertEquals(dev.nimbus.weather.data.model.SourceKind.MODEL_DWD_ICON, data.sources.first().kind)
        assertEquals("Berlin-Tempelhof", data.sources.first { it.kind == dev.nimbus.weather.data.model.SourceKind.DWD_STATION }.detail)
        assertTrue(requested.any { it.endsWith("icon_seamless") })
        assertTrue(requested.any { it.endsWith("best_match") })
    }

    @Test
    fun `no DWD station or warnings outside Germany`() = runTest {
        val data = repo(fixtureNow).load(paris, Settings(), german = false)
        assertNull(data.current.stationName)
        assertTrue(requested.none { it.startsWith("/current_weather") || it.startsWith("/alerts") })
    }

    @Test
    fun `old observations are ignored`() = runTest {
        val data = repo(fixtureNow + 5 * 3600_000L).load(berlin, Settings(), german = false)
        assertNull(data.current.stationName)
    }

    @Test
    fun `station usage can be disabled`() = runTest {
        val data = repo(fixtureNow).load(berlin, Settings(useStationObservations = false), german = false)
        assertNull(data.current.stationName)
    }

    @Test
    fun `the forecast shows before a slow extra source answers`() = runTest {
        // warm up (class loading, first connections) – then the slow source
        repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON), german = true)
        communityDelayMs = 2_500
        val t0 = System.nanoTime()
        var coreAfterMs = -1L
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        val data = repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON), german = true) {
            coreAfterMs = (System.nanoTime() - t0) / 1_000_000; core = it
        }
        val totalMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("core after $coreAfterMs ms, all after $totalMs ms", coreAfterMs in 0..1_500 && totalMs >= 2_500)
        // the core has the forecast, the extras come with the result
        assertEquals(10, core!!.daily.size)
        assertNull(core!!.community)
        assertNotNull(data.community)
    }

    @Test
    fun `a refresh keeps the extras until the new ones arrive`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings(), german = true)
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        repo(fixtureNow).load(berlin, Settings(), german = true, previous = first) { core = it }
        assertEquals(first.community, core!!.community)
        assertEquals(first.airQuality, core!!.airQuality)
    }

    @Test
    fun `an extra too late keeps its last value and is marked older`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings(), german = true)
        assertTrue(first.stale.isEmpty())
        communityDelayMs = 3_000
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        val t0 = System.nanoTime()
        val data = repo(fixtureNow, extrasDeadlineMs = 1_000).load(berlin, Settings(), german = true, previous = first) { core = it }
        // the time for the extras is up after 1 s – not waiting for the 3 s
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_500)
        assertEquals(first.community, data.community)
        assertEquals(setOf(dev.nimbus.weather.data.model.DataPart.COMMUNITY), data.stale)
        // while the extras were loading, all of them counted as older
        assertTrue(core!!.stale.containsAll(listOf(dev.nimbus.weather.data.model.DataPart.POLLEN, dev.nimbus.weather.data.model.DataPart.COMMUNITY)))
        assertTrue(dev.nimbus.weather.data.model.DataPart.FORECAST !in core!!.stale)
    }

    @Test
    fun `cards switched off load nothing`() = runTest {
        val off = Settings(hiddenCards = setOf(dev.nimbus.weather.data.model.WeatherCard.COMMUNITY))
        val data = repo(fixtureNow).load(berlin, off, german = true)
        assertNull(data.community)
        assertTrue(requested.none { it.startsWith("/airrohr") })
    }

    @Test(expected = Exception::class)
    fun `fails when no forecast is available`() = runTest {
        failForecast = true
        repo(fixtureNow).load(berlin, Settings(), german = false)
    }
}
