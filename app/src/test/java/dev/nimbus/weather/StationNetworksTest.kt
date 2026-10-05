/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StationNetworksTest.kt
 * Measured, not modelled, beyond Germany: GeoSphere Austria, MeteoSwiss, DMI every 10 minutes,
 * airports (METAR) where none of them measures. Recorded answers of 5 October 2026.
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
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.StationNetwork
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.DmiSource
import dev.nimbus.weather.data.remote.GeoSphereSource
import dev.nimbus.weather.data.remote.MetarSource
import dev.nimbus.weather.data.remote.MeteoSwissSource
import dev.nimbus.weather.data.remote.NetworkStations
import dev.nimbus.weather.data.remote.StationNetworks
import dev.nimbus.weather.data.remote.StationObservation
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationNetworksTest {
    private val now = java.time.Instant.parse("2026-10-05T10:40:00Z").toEpochMilli()

    /** The airport nearest to Hannover: its report in the app's units – a CAVOK sky clear, "6+" miles no visibility. */
    @Test fun metarHannover() {
        val o = MetarSource.parse(Fixtures.json("metar_hannover.json"), 52.3759, 9.732)!!
        assertEquals("Hannover Arpt", o.stationName)
        assertEquals(StationNetwork.METAR, o.network)
        assertEquals(53.0, o.heightM!!, 0.1)
        assertEquals(20.0, o.temperature!!, 0.01)
        assertEquals(12.0, o.dewPoint!!, 0.01)
        assertEquals(60.0, o.humidity!!, 1.5)                     // from temperature and dew point
        assertEquals(6 * 1.852, o.windSpeed!!, 0.01)              // knots → km/h
        assertNull(o.visibility)                                  // "6+": 10 km and more
        assertEquals(0.0, o.cloudCover!!, 0.01)                    // CAVOK
        assertTrue(o.observedDry)
    }

    @Test fun metarWeather() {
        assertEquals(Condition.RAIN, MetarSource.condition("-RA"))
        assertEquals(Condition.HEAVY_RAIN, MetarSource.condition("+RA"))
        assertEquals(Condition.SHOWERS, MetarSource.condition("SHRA"))
        assertEquals(Condition.THUNDERSTORM, MetarSource.condition("TSRA"))
        assertEquals(Condition.SNOW, MetarSource.condition("-SN"))
        assertEquals(Condition.SLEET, MetarSource.condition("RASN"))
        assertEquals(Condition.FREEZING_RAIN, MetarSource.condition("FZRA"))
        assertEquals(Condition.FOG, MetarSource.condition("FG"))
        assertNull(MetarSource.condition("BCFG"))          // fog patches: not a foggy place
        assertNull(MetarSource.condition("BR"))            // mist
        assertNull(MetarSource.condition(null))
    }

    /** Vienna: the nearest TAWES station is "Wien-Innere Stadt", its 10 minutes in km/h. */
    @Test fun geosphereVienna() {
        val stations = GeoSphereSource.parseStations(Fixtures.json("geosphere_metadata.json"), now)
        assertTrue(stations.size > 250)
        val (st, km) = NetworkStations.nearest(stations, 48.2082, 16.3738)!!
        assertEquals("11034", st.id)
        assertEquals("Wien-Innere Stadt", st.name)
        assertEquals(1.2, km, 0.2)
        val o = GeoSphereSource.parse(Fixtures.json("geosphere_wien_innere_stadt.json"), st, km)!!
        assertEquals(19.8, o.temperature!!, 0.01)
        assertEquals(0.8 * 3.6, o.windSpeed!!, 0.01)
        assertEquals(1025.3, o.pressure!!, 0.01)
        assertEquals(StationNetwork.GEOSPHERE, o.network)
        assertEquals(177.0, o.heightM!!, 0.1)
    }

    /** Zürich: Fluntern (Uetliberg is nearer to nothing – and its row is empty), umlauts read right. */
    @Test fun meteoswissZurich() {
        // the file is ISO 8859-1, as MeteoSwiss serves it
        val csv = String(javaClass.classLoader!!.getResourceAsStream("fixtures/meteoswiss_stations.csv")!!.readBytes(), Charsets.ISO_8859_1)
        val stations = MeteoSwissSource.parseStations(csv)
        assertTrue("stations: ${stations.size}", stations.size > 100)
        val rows = MeteoSwissSource.parseValues(Fixtures.text("meteoswiss_vqha80.csv"))
        val (st, km) = NetworkStations.nearest(stations.filter { rows[it.id]?.get("tre200s0")?.toDoubleOrNull() != null }, 47.3769, 8.5417)!!
        assertEquals("SMA", st.id)
        assertEquals("Zürich / Fluntern", st.name)
        val o = MeteoSwissSource.observation(rows.getValue(st.id), st, km)!!
        assertEquals(19.0, o.temperature!!, 0.01)
        assertEquals(2.9, o.windSpeed!!, 0.01)                     // already km/h
        assertEquals(604.0, o.heightM!!, 0.1)
        assertEquals(StationNetwork.METEOSWISS, o.network)
    }

    /** Copenhagen airport: each station once (the list holds one per period), the latest values. */
    @Test fun dmiCopenhagen() {
        val stations = DmiSource.parseStations(Fixtures.json("dmi_stations_kopenhagen.json"))
        assertEquals(stations.size, stations.map { it.id }.toSet().size)
        val airport = stations.single { it.id == "06180" }
        val o = DmiSource.parse(Fixtures.json("dmi_obs_06180.json"), airport, 1.0)!!
        assertEquals(17.2, o.temperature!!, 0.01)
        assertEquals(7.2 * 3.6, o.windSpeed!!, 0.01)
        assertEquals(45000.0, o.visibility!!, 0.1)
        assertEquals(StationNetwork.DMI, o.network)
    }

    private fun obs(network: StationNetwork, km: Double, t: Double = 20.0, ageMs: Long = 600_000L) = StationObservation(
        now - ageMs, network.label, km, t, null, null, null, null, null, null, null, null, null, null, false, heightM = 60.0, network = network,
    )

    /** A network every 10 minutes before the airport – even a nearer one; the airport where nothing else fits. */
    @Test fun theNationalNetworkBeforeTheAirport() {
        val pick = { list: List<StationObservation> -> WeatherRepository.pickObservation(list, 60.0, 20.0, now) }
        assertEquals(StationNetwork.GEOSPHERE, pick(listOf(obs(StationNetwork.METAR, 3.0), obs(StationNetwork.GEOSPHERE, 12.0)))!!.network)
        // the national one too old: the airport
        assertEquals(StationNetwork.METAR, pick(listOf(obs(StationNetwork.METAR, 3.0), obs(StationNetwork.GEOSPHERE, 12.0, ageMs = 3 * 3_600_000L)))!!.network)
        // far off the model: none
        assertNull(pick(listOf(obs(StationNetwork.METAR, 3.0, t = 35.0))))
    }

    /** The whole way: Vienna's page measured at Wien-Innere Stadt, named with its network. */
    @Test fun viennaMeasuredByGeoSphere() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
                return when {
                    // a forecast recorded elsewhere, here for Vienna: its height and a temperature near the measured one
                    url.encodedPath == "/v1/forecast" -> ok(
                        Fixtures.text("openmeteo_best.json").replace("\"elevation\":524.0", "\"elevation\":170.0").replace("\"temperature_2m\":16.0", "\"temperature_2m\":19.0"),
                    )
                    url.encodedPath.endsWith("/metadata") -> ok(Fixtures.text("geosphere_metadata.json"))
                    url.encodedPath.startsWith("/v1/station/current") -> ok(Fixtures.text("geosphere_wien_innere_stadt.json"))
                    url.encodedPath == "/api/data/metar" -> ok("[]")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        try {
            val http = OkHttpClient()
            val b = server.url("/").toString().trimEnd('/')
            val repo = WeatherRepository(
                OpenMeteoSource(http, b, b, b), BrightSkySource(http, b), CommunitySource(http, b), PollenSource(http, "$b/pollen.json", "$b/wms", b),
                clock = { now }, stations = StationNetworks(GeoSphereSource(http, b, clock = { now }), MeteoSwissSource(http, b), DmiSource(http, b), MetarSource(http, b)),
            )
            val data = repo.load(Place("w", "Wien", latitude = 48.2082, longitude = 16.3738), Settings(), german = true)
            assertEquals("Wien-Innere Stadt", data.current.stationName)
            assertEquals(StationNetwork.GEOSPHERE, data.current.stationNetwork)
            assertEquals(19.8, data.current.temperature, 0.01)
            assertTrue(data.sources.any { it.kind == SourceKind.STATION && it.network == StationNetwork.GEOSPHERE })
        } finally {
            server.close()
        }
    }
}
