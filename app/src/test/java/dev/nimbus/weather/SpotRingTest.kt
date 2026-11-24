/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SpotRingTest.kt
 * The radar's hours over a place kept on the device: a second look asks only for what is not kept.
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

import dev.nimbus.weather.data.remote.FreshData
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.Provenance
import dev.nimbus.weather.data.remote.SpotRing
import dev.nimbus.weather.data.remote.SpotSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class SpotRingTest {
    private val hour = 3_600_000L
    private val step = 5 * 60_000L
    private val start = Instant.parse("2026-11-24T10:20:00Z").toEpochMilli()
    private val dir = Files.createTempDirectory("lookback").toFile()
    private val server = MockWebServer()
    /** Every request to the radar's service: its layer and the times asked for. */
    private val asked = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var delayMs = 0L

    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    /** The service's answer: a value for every time asked for - 0.4 mm an hour, 20 dBZ a step. */
    private fun answer(layer: String, times: String): String {
        val all = if ('/' in times) times.split('/').map(::at).let { (a, b) -> (a..b step step).toList() } else times.split(',').map(::at)
        val band = if ("wn-analysis" in layer) "\"DBZH\":20.0" else "\"GRAY_INDEX\":0.4"
        return all.joinToString(",", "{\"features\":[", "]}") { "{\"properties\":{\"TIME\":\"${Instant.ofEpochMilli(it)}\",$band}}" }
    }

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(body: String) = MockResponse.Builder().code(200).body(body)
                return when (url.encodedPath) {
                    "/v1/forecast" -> ok(Fixtures.text("openmeteo_history.json")).build()
                    "/weather" -> ok(Fixtures.text("brightsky_history.json")).build()
                    "/wms" -> {
                        val layer = url.queryParameter("layers")!!
                        val times = url.queryParameter("time")!!
                        asked += layer to times
                        ok(answer(layer, times)).headersDelay(delayMs, TimeUnit.MILLISECONDS).build()
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun source(waitMs: Long = 5_000): HistorySource {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return HistorySource(http, base, base, SpotSource(http, wms = "$base/wms", satellite = base), spotWaitMs = waitMs, ring = SpotRing(dir))
    }

    private fun layers() = asked.map { it.first.substringAfter("dwd:") }.groupingBy { it }.eachCount()

    @Test fun theHoursNotKnownAndTheirStretches() {
        val from = at("2026-11-24T04:20:00Z")
        val to = at("2026-11-24T10:20:00Z")
        val hours = (5..10).map { at("2026-11-24T%02d:00:00Z".format(it)) }
        assertEquals(hours, SpotRing.missing(emptySet(), from, to))
        assertEquals(hours.drop(4), SpotRing.missing(hours.take(4).toSet(), from, to))
        // two blocks of the day: the hours to 06:00, and those after to the end asked for
        assertEquals(
            listOf(at("2026-11-24T04:05:00Z")..at("2026-11-24T06:00:00Z"), at("2026-11-24T06:05:00Z")..to),
            SpotRing.stretches(emptySet(), from, to),
        )
        // the first block known, of the second its first two hours: from the first step of the third
        assertEquals(listOf(at("2026-11-24T08:05:00Z")..to), SpotRing.stretches(hours.take(4).toSet(), from, to))
        assertEquals(emptyList<LongRange>(), SpotRing.stretches(hours.toSet(), from, to))
    }

    @Test fun aSecondLookAsksOnlyForTheHoursSince() = runBlocking {
        val first = source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start)
        // three days: the adjusted sums at once, the last hours' steps, the echoes block by block
        assertEquals(mapOf("RADOLAN-RW" to 1, "RADOLAN-RY" to 1, "Radar_wn-analysis_1x1km_ger" to 13), layers())
        assertEquals(72, asked.first { "RW" in it.first }.second.split(',').size)
        assertTrue(first.allHours.any { it.measured?.precipitationFrom == Provenance.RADAR })
        // ten minutes on, the same hour: every full hour is kept
        asked.clear()
        val second = source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start + 10 * 60_000L)
        assertEquals(emptyMap<String, Int>(), layers())
        assertEquals(
            first.allHours.mapNotNull { h -> h.measured?.precipitation?.let { h.time to it } },
            second.allHours.mapNotNull { h -> h.measured?.precipitation?.let { h.time to it } },
        )
        // two hours on: the two new hours alone, in one stretch of steps
        asked.clear()
        source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start + 2 * hour)
        assertEquals(mapOf("RADOLAN-RW" to 1, "RADOLAN-RY" to 1, "Radar_wn-analysis_1x1km_ger" to 1), layers())
        assertEquals("2026-11-24T11:00:00Z,2026-11-24T12:00:00Z", asked.first { "RW" in it.first }.second)
        assertEquals("2026-11-24T10:05:00Z/2026-11-24T12:00:00Z", asked.first { "analysis" in it.first }.second)
    }

    /** Another place has its own hours. */
    @Test fun eachPlaceItsOwnHours() = runBlocking {
        source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start)
        asked.clear()
        source().load(53.5511, 9.9937, "icon_seamless", inGermany = true, now = start)
        assertEquals(15, asked.size)
    }

    /** An answer later than the look-back waits is not lost: it is there for the next look. */
    @Test fun aLateAnswerIsKeptForTheNextLook() = runBlocking {
        delayMs = 300
        val started = System.nanoTime()
        val first = source(waitMs = 100).load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start)
        val waited = (System.nanoTime() - started) / 1_000_000
        assertTrue("waited $waited ms", waited < 290)
        assertTrue(first.allHours.none { it.measured?.precipitationFrom == Provenance.RADAR })
        // the thirteen stretches of steps go on arriving, two at a time - today's first
        delay(3_000)
        val stretches = asked.filter { "analysis" in it.first }.map { it.second }
        assertEquals(13, stretches.size)
        assertTrue(stretches.take(2).any { it.endsWith("/2026-11-24T10:20:00Z") })
        delayMs = 0
        asked.clear()
        val second = source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start + 60_000L)
        assertEquals(emptyMap<String, Int>(), layers())
        assertTrue(second.allHours.any { it.measured?.precipitationFrom == Provenance.RADAR })
    }

    /** Asked for anew, every hour is asked for again. */
    @Test fun aForcedLoadAsksForEveryHour() = runBlocking {
        source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start)
        asked.clear()
        withContext(FreshData) { source().load(52.3759, 9.732, "icon_seamless", inGermany = true, now = start + 60_000L) }
        assertEquals(15, asked.size)
    }
}
