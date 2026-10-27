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
    /** Bright Sky's MOSMIX at the place (null: no station near). */
    private var mosmixBody: String? = null

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                synchronized(requested) { requested += url.encodedPath + "?" + (url.queryParameter("models") ?: "") }
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                // the forecasts were recorded in Munich (524 m): here they stand for Berlin, 34 m –
                // the height the Berlin station and the sensors are measured against
                fun forecast(name: String) = MockResponse.Builder().code(200)
                    .body(Fixtures.text(name).replace("\"elevation\":524.0", "\"elevation\":34.0")).build()
                return when {
                    url.encodedPath == "/v1/forecast" && failForecast -> MockResponse.Builder().code(500).build()
                    url.encodedPath == "/v1/forecast" && url.queryParameter("models") == "icon_seamless" -> forecast("openmeteo_icon.json")
                    url.encodedPath == "/v1/forecast" -> forecast("openmeteo_best.json")
                    url.encodedPath == "/v1/air-quality" -> ok("openmeteo_aq.json")
                    url.encodedPath == "/current_weather" -> ok("brightsky_current.json")
                    url.encodedPath == "/weather" && url.queryParameter("lat") != null ->
                        mosmixBody?.let { MockResponse.Builder().code(200).body(it).build() } ?: MockResponse.Builder().code(404).build()
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

    /** The parts of [d] past their shelf life at [now] (what their records would say). */
    private fun expired(d: dev.nimbus.weather.data.model.WeatherData, now: Long) =
        dev.nimbus.weather.data.model.DataPart.entries.filterTo(mutableSetOf()) { now - d.fetchedAt(it) >= dev.nimbus.weather.data.repo.Freshness.lifeMs(it) }
    private val paris = Place("p", "Paris", latitude = 48.85, longitude = 2.35)

    // Fixture timestamp of the Bright Sky observation is 2026-09-28T20:30Z.
    private val fixtureNow = java.time.Instant.parse("2026-09-28T20:45:00Z").toEpochMilli()

    @Test
    fun `combines all sources for a German location`() = runTest {
        val data = repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON))
        assertEquals("Berlin-Tempelhof", data.current.stationName)
        assertEquals(10, data.daily.size)                      // ICON (8 days) + best match fill
        assertNotNull(data.airQuality)
        assertNotNull(data.community)
        assertEquals(dev.nimbus.weather.data.model.PollenSourceKind.DWD, data.pollen?.source)
        assertTrue(data.pollen!!.region!!.startsWith("Geest"))
        assertEquals(dev.nimbus.weather.data.model.SourceKind.MODEL_DWD_ICON, data.sources.first().kind)
        assertEquals("Berlin-Tempelhof", data.sources.first { it.kind == dev.nimbus.weather.data.model.SourceKind.STATION && it.network == dev.nimbus.weather.data.model.StationNetwork.DWD }.detail)
        assertTrue(requested.any { it.endsWith("icon_seamless") })
        assertTrue(requested.any { it.endsWith("best_match") })
    }

    /**
     * MOSMIX at Berlin-Tempelhof (4 km, 48 m) says overcast all along: the hours' sky weighed with
     * it – no sunny hour left, the sun's minutes MOSMIX's –, the source named.
     */
    @Test
    fun `the sky of the hours weighed with MOSMIX`() = runTest {
        val start = 1_790_625_600L                      // the forecast fixture's first hour
        val plain = repo(fixtureNow).load(berlin, Settings(useStationObservations = false))
        mosmixBody = """{"weather":[""" + (0 until 72).joinToString(",") { k ->
            val t = java.time.Instant.ofEpochSecond(start + k * 3600L)
            """{"timestamp":"$t","source_id":7,"sunshine":0,"cloud_cover":100,"condition":"dry"}"""
        } + """],"sources":[{"id":7,"observation_type":"forecast","station_name":"BERLIN-TEMPELHOF","distance":4000,"height":48.0}]}"""
        val data = repo(fixtureNow).load(berlin, Settings(useStationObservations = false))
        val src = data.sources.single { it.kind == dev.nimbus.weather.data.model.SourceKind.MOSMIX }
        assertEquals("BERLIN-TEMPELHOF", src.detail)
        assertEquals(4.0, src.distanceKm!!, 1e-9)
        val sky = setOf(dev.nimbus.weather.data.model.Condition.CLEAR, dev.nimbus.weather.data.model.Condition.MOSTLY_CLEAR, dev.nimbus.weather.data.model.Condition.PARTLY_CLOUDY)
        val weighed = data.hourly.filter { it.time < (start + 72 * 3600L) * 1000 }
        assertTrue(plain.hourly.any { it.condition in sky })
        assertTrue(weighed.none { it.condition in sky })
        assertTrue(weighed.all { (it.cloudCover ?: 100.0) >= 80.0 })
        assertTrue(requested.any { it.startsWith("/weather") })
    }

    @Test
    fun `no DWD station or warnings outside Germany`() = runTest {
        val data = repo(fixtureNow).load(paris, Settings())
        assertNull(data.current.stationName)
        assertTrue(requested.none { it.startsWith("/current_weather") || it.startsWith("/alerts") })
    }

    @Test
    fun `old observations are ignored`() = runTest {
        val data = repo(fixtureNow + 5 * 3600_000L).load(berlin, Settings())
        assertNull(data.current.stationName)
    }

    @Test
    fun `station usage can be disabled`() = runTest {
        val data = repo(fixtureNow).load(berlin, Settings(useStationObservations = false))
        assertNull(data.current.stationName)
    }

    @Test
    fun `the forecast shows before a slow extra source answers`() = runTest {
        // warm up (class loading, first connections) – then the slow source
        repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON))
        communityDelayMs = 2_500
        val t0 = System.nanoTime()
        var coreAfterMs = -1L
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        // the first step is the core; the extras' steps follow
        val data = repo(fixtureNow).load(berlin, Settings(model = ForecastModel.DWD_ICON)) {
            if (core == null) { coreAfterMs = (System.nanoTime() - t0) / 1_000_000; core = it }
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
        val first = repo(fixtureNow).load(berlin, Settings())
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        repo(fixtureNow).load(berlin, Settings(), previous = first) { core = it }
        assertEquals(first.community, core!!.community)
        assertEquals(first.airQuality, core!!.airQuality)
    }

    /**
     * Each extra turns current as soon as its source has answered: the cards waited for the
     * slowest (gauges up to 40 s) and then all turned green at once.
     */
    @Test
    fun `each extra is current as soon as it has arrived`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings())
        communityDelayMs = 2_000
        val updates = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Set<dev.nimbus.weather.data.model.DataPart>>>())
        val t0 = System.nanoTime()
        val data = repo(fixtureNow).load(berlin, Settings(), previous = first) {
            updates += (System.nanoTime() - t0) / 1_000_000 to it.stale
        }
        assertTrue(data.stale.isEmpty())
        // pollen and air quality current while the citizen sensors were still out
        val early = updates.firstOrNull { (_, stale) ->
            dev.nimbus.weather.data.model.DataPart.POLLEN !in stale && dev.nimbus.weather.data.model.DataPart.AIR_QUALITY !in stale &&
                dev.nimbus.weather.data.model.DataPart.COMMUNITY in stale
        }
        assertTrue("no step with pollen and air quality current before the sensors: $updates", early != null)
        assertTrue("that step came only after ${early!!.first} ms", early.first < 1_500)
    }

    @Test
    fun `an extra too late keeps its last value and is marked older`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings())
        assertTrue(first.stale.isEmpty())
        communityDelayMs = 3_000
        var core: dev.nimbus.weather.data.model.WeatherData? = null
        val t0 = System.nanoTime()
        val data = repo(fixtureNow, extrasDeadlineMs = 1_000).load(berlin, Settings(), previous = first) { if (core == null) core = it }
        // the time for the extras is up after 1 s – not waiting for the 3 s
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_500)
        assertEquals(first.community, data.community)
        assertEquals(setOf(dev.nimbus.weather.data.model.DataPart.COMMUNITY), data.stale)
        // while the extras were loading, all of them counted as older
        assertTrue(core!!.stale.containsAll(listOf(dev.nimbus.weather.data.model.DataPart.POLLEN, dev.nimbus.weather.data.model.DataPart.COMMUNITY)))
        assertTrue(dev.nimbus.weather.data.model.DataPart.FORECAST !in core!!.stale)
    }

    /** Only the forecast due: the extras are not asked for and keep their values with their own times. */
    @Test
    fun `only the forecast due asks the forecast only and keeps the extras`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings())
        synchronized(requested) { requested.clear() }
        val later = fixtureNow + 3_600_000L
        val bg = repo(later).load(berlin, Settings(), previous = first, refresh = setOf(dev.nimbus.weather.data.model.DataPart.FORECAST))
        // forecast, station and warnings – nothing else
        assertTrue(requested.toString(), requested.none { it.startsWith("/airrohr") || it.startsWith("/v1/air-quality") || it.startsWith("/pollen") || it.startsWith("/wms") })
        assertTrue(requested.any { it.startsWith("/v1/forecast") })
        // the extras are the last ones, with the time they were fetched: past their shelf life
        // they count as older (citizen sensors after 10 minutes), the pollen still not (3 hours)
        assertEquals(first.community, bg.community)
        assertEquals(first.pollen, bg.pollen)
        assertEquals(first.fetchedAt(dev.nimbus.weather.data.model.DataPart.COMMUNITY), bg.fetchedAt(dev.nimbus.weather.data.model.DataPart.COMMUNITY))
        val expired = expired(bg, later)
        assertTrue(expired.toString(), dev.nimbus.weather.data.model.DataPart.COMMUNITY in expired && dev.nimbus.weather.data.model.DataPart.POLLEN !in expired)
        assertTrue(dev.nimbus.weather.data.model.DataPart.FORECAST !in expired && dev.nimbus.weather.data.model.DataPart.FORECAST !in bg.stale)
    }

    /**
     * Each part has its own shelf life: 20 minutes on, the forecast and the citizen sensors are
     * loaded again – air quality (1 h) and pollen (3 h) are still current and not asked for.
     */
    @Test
    fun `only the parts past their shelf life are asked again`() = runTest {
        val first = repo(fixtureNow).load(berlin, Settings())
        synchronized(requested) { requested.clear() }
        val later = fixtureNow + 20 * 60_000L
        val due = expired(first, later)
        assertEquals(setOf(dev.nimbus.weather.data.model.DataPart.FORECAST, dev.nimbus.weather.data.model.DataPart.COMMUNITY,
            dev.nimbus.weather.data.model.DataPart.GAUGES, dev.nimbus.weather.data.model.DataPart.FLOOD), due)
        val data = repo(later).load(berlin, Settings(), previous = first, refresh = due)
        assertTrue(requested.toString(), requested.none { it.startsWith("/v1/air-quality") || it.startsWith("/pollen") || it.startsWith("/wms") })
        assertTrue(requested.toString(), requested.any { it.startsWith("/airrohr") } && requested.any { it.startsWith("/v1/forecast") })
        // the ones not asked for: their values and times kept, current – nothing yellow
        assertEquals(first.airQuality, data.airQuality)
        assertEquals(fixtureNow, data.fetchedAt(dev.nimbus.weather.data.model.DataPart.AIR_QUALITY))
        assertEquals(later, data.fetchedAt(dev.nimbus.weather.data.model.DataPart.COMMUNITY))
        assertTrue(data.stale.toString(), data.stale.isEmpty())
        assertTrue(expired(data, later).isEmpty())
    }

    /** Data stored without a time per part: the extras count as expired, the forecast has its time. */
    @Test
    fun `stored data without times per part`() = runTest {
        val old = repo(fixtureNow).load(berlin, Settings()).copy(partsAt = emptyMap())
        val due = expired(old, fixtureNow + 60_000L)
        assertEquals((dev.nimbus.weather.data.model.DataPart.entries - dev.nimbus.weather.data.model.DataPart.FORECAST).toSet(), due)
    }

    @Test
    fun `cards switched off load nothing`() = runTest {
        val off = Settings(hiddenCards = setOf(dev.nimbus.weather.data.model.WeatherCard.COMMUNITY))
        val data = repo(fixtureNow).load(berlin, off)
        assertNull(data.community)
        assertTrue(requested.none { it.startsWith("/airrohr") })
    }

    @Test(expected = Exception::class)
    fun `fails when no forecast is available`() = runTest {
        failForecast = true
        repo(fixtureNow).load(berlin, Settings())
    }
}
