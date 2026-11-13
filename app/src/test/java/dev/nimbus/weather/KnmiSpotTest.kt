/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/KnmiSpotTest.kt
 * The KNMI's radar over the place where the DWD's has no data: measured, the hours back, two hours ahead.
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

import dev.nimbus.weather.data.model.RadarSource
import dev.nimbus.weather.data.remote.KnmiSpot
import dev.nimbus.weather.data.remote.SpotSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Recorded on 13 Nov 2026, 17:00–18:00 UTC: a shower over Utrecht, Norden dry, Hamburg beyond the
 * KNMI's radars ("no data", 95.5); the nowcast of 18:00 over Utrecht.
 */
class KnmiSpotTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun theReflectivityAndItsNoData() {
        val utrecht = KnmiSpot.reflectivity(Fixtures.json("knmi_reflectivity_utrecht.json"))
        assertEquals(30.5, utrecht.getValue(at("2026-11-13T17:55:00Z"))!!, 1e-9)
        assertEquals(-32.0, utrecht.getValue(at("2026-11-13T17:00:00Z"))!!, 1e-9)
        assertTrue(KnmiSpot.reflectivity(Fixtures.json("knmi_reflectivity_hamburg.json")).values.all { it == null })
    }

    /** The nowcast's rates (mm/h) as mm in each 5 minutes; its first step, the analysis, left out. */
    @Test fun theNowcastsSteps() {
        val steps = KnmiSpot.nowcastSteps(Fixtures.json("knmi_nowcast_utrecht.json"))
        assertEquals(24, steps.size)
        assertFalse(at("2026-11-13T18:00:00Z") in steps)
        assertEquals(at("2026-11-13T20:00:00Z"), steps.keys.max())
    }

    /** The look-back's hours: the sum of the twelve steps, the strongest as a rate; a gap where the DWD's are missing. */
    @Test fun theHoursAndTheGap() {
        val h = 3_600_000L
        val end = at("2026-11-13T18:00:00Z")
        val steps = (0 until 12).associate { k -> end - k * 5 * 60_000L to if (k == 0) 0.25 else 0.0 }
        val (sums, echoes) = KnmiSpot.hours(steps)
        assertEquals(0.25, sums.getValue(end), 1e-9)
        assertEquals(3.0, echoes.getValue(end), 1e-9)
        // the DWD's hours up to the last full hour: no gap; from 10:00 on missing: from 10:00
        val from = end - 72 * h
        assertNull(KnmiSpot.gapFrom((1..72).map { from + it * h }.toSet(), from, end + 20 * 60_000L))
        assertEquals(end - 8 * h, KnmiSpot.gapFrom((1..64).map { from + it * h }.toSet(), from, end + 20 * 60_000L))
        assertEquals(from, KnmiSpot.gapFrom(emptySet(), from, end))
    }

    /** The KNMI's services answering from fixtures: the radar by the place's longitude, the nowcast for one run. */
    private fun server(nowcastIssue: String? = "2026-11-13T18:00:00Z", asked: MutableList<String> = mutableListOf()) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                asked += url.toString()
                val bbox = url.queryParameter("BBOX").orEmpty()
                val name = if (url.encodedPath.startsWith("/nowcast")) {
                    if (url.queryParameter("DIM_REFERENCE_TIME") != nowcastIssue) return MockResponse.Builder().code(422).build()
                    "knmi_nowcast_utrecht.json"
                } else when {
                    bbox.contains(",5.04") -> "knmi_reflectivity_utrecht.json"
                    bbox.contains(",7.19") -> "knmi_reflectivity_norden.json"
                    else -> "knmi_reflectivity_hamburg.json"
                }
                return MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
            }
        }
        start()
    }

    private fun knmi(s: MockWebServer) = KnmiSpot(OkHttpClient(), radar = s.url("/radar").toString(), nowcast = s.url("/nowcast").toString(), againMs = 0)

    @Test fun theRadarNowWithItsNowcast() = runBlocking {
        val s = server()
        try {
            val r = knmi(s).radarNow(52.015, 5.045, at("2026-11-13T18:05:00Z"))!!
            assertEquals(RadarSource.KNMI, r.source)
            assertEquals(at("2026-11-13T18:00:00Z"), r.time)
            // 30.5 dBZ at 17:55 a rate of about 2.9 mm/h – the quarter hour 17:50–18:05 wet
            assertTrue("${r.mmPerHour}", r.mmPerHour > 1.0)
            assertEquals(at("2026-11-13T18:00:00Z"), r.issue)
            assertTrue(r.steps.keys.any { it > r.time })
        } finally { s.close() }
    }

    /** The newest run not there yet: the one before it. */
    @Test fun theRunBeforeWhenTheNewestIsNotThere() = runBlocking {
        val s = server(nowcastIssue = "2026-11-13T18:00:00Z")
        try {
            // at 18:09 the newest that must be there is 18:05 – it is not, 18:00 is
            val r = knmi(s).nowcast(52.015, 5.045, at("2026-11-13T18:09:00Z"))
            assertEquals(at("2026-11-13T18:00:00Z"), r?.first)
        } finally { s.close() }
    }

    /** Dry where it measures; where it does not, nothing – its nowcast says "dry" there too and is not asked. */
    @Test fun dryWhereItMeasuresNothingBeyond() = runBlocking {
        val asked = mutableListOf<String>()
        val s = server(asked = asked)
        try {
            val dry = knmi(s).radarNow(53.6, 7.2, at("2026-11-13T18:05:00Z"))!!
            assertEquals(0.0, dry.mmPerHour, 1e-9)
            asked.clear()
            assertNull(knmi(s).radarNow(53.55, 10.0, at("2026-11-13T18:05:00Z")))
            assertTrue("asked: $asked", asked.none { "/nowcast" in it })
        } finally { s.close() }
    }

    /** The DWD's radar with "no data" over the place: the KNMI's in its place, said so; with data, the KNMI is not asked. */
    @Test fun theKnmiWhereTheDwdHasNoData() = runBlocking {
        val asked = mutableListOf<String>()
        val k = server(asked = asked)
        val dwd = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val layer = request.url.queryParameter("layers").orEmpty()
                    val name = if (layer.endsWith("RY")) "radar_ry_no_data_norden.json" else "radar_nowcast_no_data_norden.json"
                    return MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                }
            }
            start()
        }
        try {
            val spot = SpotSource(OkHttpClient(), wms = dwd.url("/wms").toString(), knmi = knmi(k))
            val here = spot.radarHere(53.6, 7.2, at("2026-11-13T18:05:00Z"))
            assertTrue(here.dwdNoData)
            assertEquals(RadarSource.KNMI, here.radar?.source)
            assertTrue(asked.isNotEmpty())
        } finally { k.close(); dwd.close() }
    }
}
