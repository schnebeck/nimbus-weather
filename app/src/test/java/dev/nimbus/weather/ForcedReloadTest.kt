/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ForcedReloadTest.kt
 * A forced reload asks everything anew: no answer from the HTTP cache, none from the sources' own
 * stores (data and lists alike); where a new answer fails a stored one stands in – shown, but
 * yellow.
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
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.FreshData
import dev.nimbus.weather.data.remote.GaugeSource
import dev.nimbus.weather.data.remote.LhpSource
import dev.nimbus.weather.data.remote.NlwknSource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.StandIns
import dev.nimbus.weather.data.remote.getJson
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForcedReloadTest {
    private val server = MockWebServer()
    private val dir = java.nio.file.Files.createTempDirectory("forced").toFile()
    private val asked = java.util.Collections.synchronizedList(mutableListOf<String>())
    /** The station list of Lower Saxony fails (the source has to fall back on its stored one). */
    @Volatile private var stationsFail = false

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                asked += path
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                return when {
                    // an answer a cache may keep for ten minutes
                    path == "/cached" -> MockResponse.Builder().code(200).body("""{"n":${asked.size}}""").addHeader("Cache-Control", "max-age=600").build()
                    path == "/v1/forecast" && request.url.queryParameter("models") == "icon_seamless" -> ok("openmeteo_icon.json")
                    path == "/v1/forecast" -> ok("openmeteo_best.json")
                    path == "/v1/air-quality" -> ok("openmeteo_aq.json")
                    path == "/current_weather" -> ok("brightsky_current.json")
                    path == "/alerts" -> MockResponse.Builder().code(200).body("""{"alerts":[]}""").build()
                    path.startsWith("/airrohr") -> ok("sensor_community.json")
                    path == "/pollen.json" -> ok("dwd_pollen.json")
                    path == "/wms" -> ok("dwd_pollen_region.json")
                    path == "/lhp/data/stations" -> ok("lhp_stations.json")
                    path.contains("stammdaten/stationen/All") -> if (stationsFail) MockResponse.Builder().code(500).build() else ok("nlwkn_stations.json")
                    path.startsWith("/nlwkn") -> ok("nlwkn_heinde.json")
                    path == "/stations.json" -> MockResponse.Builder().code(200).body("[]").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.close(); dir.deleteRecursively() }

    private fun base() = server.url("/").toString().trimEnd('/')

    /** The HTTP cache answered the forecast of ten minutes ago: asked anew it goes to the server. */
    @Test fun theHttpCacheAsksTheServerAgain() = runBlocking {
        val http = OkHttpClient.Builder().cache(okhttp3.Cache(java.io.File(dir, "http"), 1L shl 20)).build()
        val url = "${base()}/cached"
        http.getJson(url); http.getJson(url)
        assertEquals("the cache's answer", 1, asked.count { it == "/cached" })
        withContext(FreshData) { http.getJson(url) }
        assertEquals("asked anew – still from the cache", 2, asked.count { it == "/cached" })
    }

    /** The sources' own stores: the gauges of the states (kept 10 minutes) and a station list (kept a week). */
    @Test fun theSourcesAskAgain() = runBlocking {
        val http = OkHttpClient()
        val lhp = LhpSource(http, "${base()}/lhp")
        val nlwkn = NlwknSource(http, dir, "${base()}/nlwkn", "k")
        repeat(2) { lhp.candidates(52.15, 9.96, 30.0); nlwkn.candidates(52.15, 9.96, 30.0) }
        assertEquals(1, asked.count { it == "/lhp/data/stations" })
        assertEquals(1, asked.count { it.contains("stammdaten") })
        withContext(FreshData) { lhp.candidates(52.15, 9.96, 30.0); nlwkn.candidates(52.15, 9.96, 30.0) }
        assertEquals("the flood portal's gauges from its store", 2, asked.count { it == "/lhp/data/stations" })
        assertEquals("the station list from its file", 2, asked.count { it.contains("stammdaten") })
        // the new list fails: the stored one stands in – and says so
        stationsFail = true
        val notes = StandIns()
        val list = withContext(FreshData + notes) { nlwkn.candidates(52.15, 9.96, 30.0) }
        assertTrue(list.isNotEmpty())
        assertTrue("stood in without a word", notes.used)
    }

    /**
     * Through the repository: asked anew, the station list fails – the gauges are shown with the
     * stored list, but their card is yellow (and the part tried again), not green.
     */
    @Test fun aStoredValueStandingInIsYellow() = runBlocking {
        val http = OkHttpClient()
        val b = base()
        val nlwkn = NlwknSource(http, dir, "$b/nlwkn", "k")
        val gauges = GaugeSource(http, dir, b, nlwkn = nlwkn, nrw = null, hessen = null, sachsen = null, lhp = null)
        val repo = WeatherRepository(
            OpenMeteoSource(http, b, b, b), BrightSkySource(http, b), CommunitySource(http, b),
            PollenSource(http, "$b/pollen.json", "$b/wms", b), gauges = gauges,
        )
        val station = NlwknSource.parseStations(Fixtures.json("nlwkn_stations.json")).first()
        val place = Place("p", station.name, latitude = station.lat, longitude = station.lon)
        val first = repo.load(place, Settings(), german = true)
        assertTrue("no gauges to stand in for", first.gauges.isNotEmpty())
        assertFalse(DataPart.GAUGES in first.stale)

        stationsFail = true
        val again = repo.load(place, Settings(), german = true, previous = first, fresh = true)
        assertTrue(again.gauges.isNotEmpty())
        assertTrue("stored station list shown as current: ${again.stale}", DataPart.GAUGES in again.stale)
    }
}
