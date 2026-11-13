/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LookBackKnmiTest.kt
 * The look-back's radar hours from the KNMI where the DWD's radar has no data over the place.
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

import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.KnmiSpot
import dev.nimbus.weather.data.remote.Provenance
import dev.nimbus.weather.data.remote.SpotSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Hannover's look-back of 29 Sep 2026 with the DWD's radar out over the place ("no data" in RW, RY
 * and its analysis): the KNMI's radar measured a shower from 08:00 to 09:00 UTC – 30 dBZ, about
 * 2.7 mm in the hour – and nothing else.
 */
class LookBackKnmiTest {
    private val now = Instant.parse("2026-09-29T10:20:00Z").toEpochMilli()
    private val showerEnd = Instant.parse("2026-09-29T09:00:00Z").toEpochMilli()
    private val step = 5 * 60_000L

    /** The KNMI's reflectivity for the asked range: 30 dBZ in the steps of the shower's hour, dry else. */
    private fun knmi(range: String): String {
        val (a, b) = range.split("/").map { Instant.parse(it).toEpochMilli() }
        val steps = (a..b step step).joinToString(",") { t ->
            val wet = t in (showerEnd - 11 * step)..showerEnd
            "\"${Instant.ofEpochMilli(t)}\":\"${if (wet) "30.000000" else "-32.000000"}\""
        }
        return """[{"name":"Reflectivity","units":"dBZ","dims":"time","data":{$steps}}]"""
    }

    @Test fun theKnmisHoursWhereTheDwdsAreMissing() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
                return when (url.encodedPath) {
                    "/v1/forecast" -> ok(Fixtures.text("openmeteo_history.json"))
                    "/weather" -> ok(Fixtures.text("brightsky_history.json"))
                    // the DWD's radar: "no data" at every step of every layer
                    "/wms" -> ok(if (url.queryParameter("layers").orEmpty().contains("RADOLAN")) Fixtures.text("radar_ry_no_data_norden.json")
                        else Fixtures.text("radar_wn_no_data_norden.json"))
                    "/knmi-radar" -> ok(knmi(url.queryParameter("TIME")!!))
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        try {
            val http = OkHttpClient()
            val base = server.url("/").toString().trimEnd('/')
            val spot = SpotSource(http, wms = "$base/wms", satellite = base, knmi = KnmiSpot(http, "$base/knmi-radar", "$base/knmi-nowcast", againMs = 0))
            val history = HistorySource(http, base, base, spot, spotWaitMs = 10_000).load(52.3759, 9.732, "icon_seamless", inGermany = true, now = now)
            val shower = history.allHours.single { it.time == showerEnd }.measured!!
            assertEquals(Provenance.RADAR, shower.precipitationFrom)
            assertEquals(SpotSource.rate(30.0), shower.precipitation!!, 0.05)
            // the hour before it dry by the KNMI's radar
            val before = history.allHours.single { it.time == showerEnd - 3_600_000L }.measured!!
            assertEquals(Provenance.RADAR, before.precipitationFrom)
            assertEquals(0.0, before.precipitation!!, 1e-9)
            assertTrue(server.requestCount > 0)
        } finally { server.close() }
    }
}
