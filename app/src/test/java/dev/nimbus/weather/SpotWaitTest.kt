/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SpotWaitTest.kt
 * The look-back does not wait for a slow radar or satellite: the station's values stand.
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
import java.util.concurrent.TimeUnit

/**
 * „alle drei Punkte als 1.37.0 umsetzen“ – the third: the DWD's point request for the radar took
 * from one second to fifty, and the look-back waited for it. Now it waits a set time; late, the
 * station's precipitation and sunshine stand.
 */
class SpotWaitTest {
    private val now = Instant.parse("2026-09-29T10:20:00Z").toEpochMilli()

    @Test fun aSlowRadarDoesNotHoldUpTheLookBack() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name))
                return when {
                    path == "/v1/forecast" -> ok("openmeteo_history.json").build()
                    path == "/weather" -> ok("brightsky_history.json").build()
                    // radar and satellite: an answer only after five seconds
                    path == "/wms" -> ok("radolan_rw_kiel.json").headersDelay(5_000, TimeUnit.MILLISECONDS).build()
                    path == "/v1/archive" -> ok("satellite_sun_salzdetfurth.json").headersDelay(5_000, TimeUnit.MILLISECONDS).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        try {
            val http = OkHttpClient()
            val base = server.url("/").toString().trimEnd('/')
            val spot = SpotSource(http, wms = "$base/wms", satellite = base)
            val started = System.nanoTime()
            val history = HistorySource(http, base, base, spot, spotWaitMs = 300).load(52.3759, 9.732, "icon_seamless", inGermany = true, now = now)
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("the look-back waited ${tookMs} ms", tookMs < 2_500)
            // the station's readings stand
            assertEquals("Hannover-Herrenhausen", history.stationName)
            val measured = history.allHours.mapNotNull { it.measured }
            assertTrue(measured.any { it.precipitation != null })
            assertTrue(measured.all { it.precipitationFrom == Provenance.STATION || it.precipitation == null })
        } finally { server.close() }
    }
}
