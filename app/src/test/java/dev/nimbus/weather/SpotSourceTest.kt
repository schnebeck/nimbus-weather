/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SpotSourceTest.kt
 * Measured over the place itself: the DWD radar's precipitation and the satellite's sunshine, from
 * recorded answers.
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

import dev.nimbus.weather.data.remote.SpotSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * „Eigentlich müsste man zwischen den Stationen triangulieren …“ – „setze gleich alles für eine
 * 1.36.0 um und führe gleich die quellenprüfung mit durch“: precipitation by the DWD's radar over
 * the place (RADOLAN RW, adjusted to the gauges; RY for the newest hour), sunshine by the satellite
 * (the DWD's from EUMETSAT MTG, via Open-Meteo). Recorded on 6 Oct 2026: rain over the Bay of
 * Kiel, Bad Salzdetfurth in the sun.
 */
class SpotSourceTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun theRadarsHoursAtThePlace() {
        val rw = SpotSource.series(Fixtures.json("radolan_rw_kiel.json"))
        val ry = SpotSource.series(Fixtures.json("radolan_ry_kiel.json"))
        // RW every 10 minutes, a moving hourly sum: 15:10 had 1.4 mm in its hour
        assertEquals(1.4, rw.getValue(at("2026-10-06T15:10:00Z")), 1e-6)
        val hours = SpotSource.hourly(rw, ry)
        // the full hours from RW (the hour before each)
        assertEquals(0.1, hours.getValue(at("2026-10-06T15:00:00Z")), 1e-6)
        assertEquals(0.0, hours.getValue(at("2026-10-06T16:00:00Z")), 1e-6)
        // 18:00: RW's adjusted 0.1 mm, not RY's 0.42
        assertEquals(0.1, hours.getValue(at("2026-10-06T18:00:00Z")), 1e-6)
        // 19:00, not in RW yet: RY's twelve 5-minute steps
        assertEquals(0.03, hours.getValue(at("2026-10-06T19:00:00Z")), 1e-6)
        // 20:00 is not over: no value
        assertTrue(at("2026-10-06T20:00:00Z") !in hours)
        // only full hours
        assertTrue(hours.keys.all { it % 3_600_000L == 0L })
    }

    /** Beyond the radars: no feature at all – or "no data" (−1) at its edge: nothing measured. */
    @Test fun beyondTheRadarsNothing() {
        assertTrue(SpotSource.series(Fixtures.json("radolan_rw_outside.json")).isEmpty())
        val all = SpotSource.series(Fixtures.json("radolan_rw_kiel.json"))
        val edge = Fixtures.json("radolan_rw_kiel.json").toString().replace("\"GRAY_INDEX\":0,", "\"GRAY_INDEX\":-1,")
        val parsed = SpotSource.series(dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement(edge))
        // the dry steps turned "no data": left out, the wet ones stay
        assertEquals(all.filterValues { it > 0.0 }, parsed)
    }

    @Test fun theSatellitesSunshine() {
        val sun = SpotSource.sunshine(Fixtures.json("satellite_sun_salzdetfurth.json"), 52.06, 10.0)
        // 11:00–12:00 UTC (13–14 h) clouds over the place, 12–13 UTC 14 minutes
        assertEquals(0.0, sun.getValue(at("2026-10-06T11:00:00Z")), 1e-9)
        assertEquals(868.94 / 60, sun.getValue(at("2026-10-06T12:00:00Z")), 1e-6)
        assertTrue(sun.values.all { it in 0.0..60.0 })
    }

    /**
     * „wie schafft es das Wetter mit einer prognose von 60min Sonnenschein 1,2mm Niederschlag zu
     * generieren?“ – „generell finde ich die lokalere Prognose aufgrund der Sattelitendaten zu
     * ermitteln die bessere Methode … schön wäre es, wenn du die Anpassung mit sagen wir 100 Alt-Daten
     * über verschiedene Orte zu zurückligenden Zeiten/Jahreszeiten prüfen würdest und daran sogar dein
     * Modell optimieren könntest“: Norden, 10 Oct 2026, showers – Open-Meteo's sunshine gave the
     * full hour 12–13 h (and 337 minutes the day), the stations 12 km away 11 (and 135).
     */
    @Test fun theSatellitesSunshineByItsDirectIrradiance() {
        val sun = SpotSource.sunshine(Fixtures.json("satellite_sun_norden_showers.json"), 53.5964, 7.2061)
        // the radiation left out: Open-Meteo's own sunshine
        val raw = SpotSource.sunshine(Fixtures.json("satellite_sun_norden_showers.json").toString()
            .replace("direct_normal_irradiance", "unused").let { dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement(it) }, 53.5964, 7.2061)
        val noon = at("2026-10-10T11:00:00Z")          // 12–13 h CEST
        assertEquals(60.0, raw.getValue(noon), 0.1)
        assertTrue("12–13 h: ${sun.getValue(noon)} min", sun.getValue(noon) in 15.0..30.0)
        val station = 135.0
        assertTrue("the day: ${sun.values.sum()} min", abs(sun.values.sum() - station) < abs(raw.values.sum() - station) / 2)
        assertTrue(sun.values.all { it in 0.0..60.0 })
    }

    /** One request each: RW over the time asked for, RY over the last hours – a time series at the place. */
    @Test fun oneRequestPerProduct() = runBlocking {
        val asked = java.util.Collections.synchronizedList(mutableListOf<okhttp3.HttpUrl>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.url
                val layer = request.url.queryParameter("layers").orEmpty()
                val name = if (layer.endsWith("RW")) "radolan_rw_kiel.json" else "radolan_ry_kiel.json"
                return MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
            }
        }
        server.start()
        try {
            val spot = SpotSource(OkHttpClient(), wms = server.url("/wms").toString())
            val hours = spot.radarPrecipitation(54.338, 10.538, at("2026-10-06T14:00:00Z"), at("2026-10-06T19:25:00Z"))
            assertEquals(0.03, hours.getValue(at("2026-10-06T19:00:00Z")), 1e-6)
            assertEquals(2, asked.size)
            val rw = asked.first { it.queryParameter("layers") == "dwd:RADOLAN-RW" }
            assertEquals("2026-10-06T14:00:00Z/2026-10-06T19:25:00Z", rw.queryParameter("time"))
            assertEquals("GetFeatureInfo", rw.queryParameter("request"))
            val ry = asked.first { it.queryParameter("layers") == "dwd:RADOLAN-RY" }
            assertEquals("2026-10-06T16:25:00Z/2026-10-06T19:25:00Z", ry.queryParameter("time"))
        } finally { server.close() }
    }
}
