/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LightningTest.kt
 * Lightning live: on the radar the flashes of the last 15 minutes, on the page the nearest one.
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

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.LightningNearby
import dev.nimbus.weather.data.remote.LightningSource
import dev.nimbus.weather.ui.main.LightningNote
import dev.nimbus.weather.ui.radar.LightningLayer
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.Instant

/**
 * Lightning from the DWD's satellite flashes (Meteosat Third Generation's Lightning Imager): on
 * the radar where it struck in the 15 minutes up to the radar's time, the newest brightest; on the
 * page how near the nearest flash is. Recorded on 11 Oct 2026 at 00:20 UTC, under a storm in the
 * north of Syria.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LightningTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-11T00:20:54Z").toEpochMilli()

    /** Under the storm: the nearest flash's middle under 5 km away, just now – the newest 400 groups. */
    @Test fun theNearestFlashUnderAStorm() {
        val n = LightningSource.parse(Fixtures.json("lightning_near_raqqa.json"), 36.2, 39.0, now)!!
        assertEquals(4.73, n.distanceKm, 0.05)
        assertEquals(0, n.minutesAgo(now))
        assertEquals(218, n.flashes)
        assertTrue("the answer ends at the count: at least that many", n.more)
    }

    /** Nothing in 50 km: no note. */
    @Test fun noLightningNoNote() {
        assertNull(LightningSource.parse(Fixtures.json("lightning_none_hannover.json"), 52.3759, 9.732, now))
        // the same flashes, seen from 200 km north
        assertNull(LightningSource.parse(Fixtures.json("lightning_near_raqqa.json"), 38.0, 39.0, now))
    }

    /** One small request: the box around the place in longitude, latitude, the last 15 minutes, the newest first. */
    @Test fun theRequestAroundThePlace() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(200).body(Fixtures.text("lightning_none_hannover.json")).build())
        server.start()
        try {
            assertNull(LightningSource(OkHttpClient(), server.url("/ows").toString()).nearby(52.3759, 9.732, now))
            val url = server.takeRequest().url
            val filter = url.queryParameter("cql_filter")!!
            assertTrue(filter, filter.startsWith("GROUP_END_TIME AFTER 2026-10-11T00:05:54Z AND BBOX(GEOM,8.99"))
            assertTrue(filter, filter.endsWith(",'CRS:84')"))
            assertEquals("GROUP_END_TIME DESC", url.queryParameter("sortBy"))
            assertEquals(LightningSource.MAX_GROUPS.toString(), url.queryParameter("count"))
        } finally { server.close() }
    }

    /** "My location" keeps its id when it moves: the lightning is asked for anew at the new position. */
    @Test fun aMovedPlaceIsAskedAnew() {
        val server = MockWebServer()
        repeat(2) { server.enqueue(MockResponse.Builder().code(200).body(Fixtures.text("lightning_none_hannover.json")).build()) }
        server.start()
        try {
            val source = LightningSource(OkHttpClient(), server.url("/ows").toString())
            var place by androidx.compose.runtime.mutableStateOf(dev.nimbus.weather.data.model.Place("here", "Here", latitude = 52.38, longitude = 9.73))
            compose.setContent { dev.nimbus.weather.ui.main.rememberLightningNearby(place, source) }
            val first = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.url.queryParameter("cql_filter")!!
            compose.runOnIdle { place = place.copy(latitude = 36.2, longitude = 39.0) }
            compose.waitForIdle()
            val second = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("no request after the move", second != null)
            assertTrue(first, "BBOX(GEOM,8." in first)
            assertTrue(second!!.url.queryParameter("cql_filter")!!, "BBOX(GEOM,38." in second.url.queryParameter("cql_filter")!!)
        } finally { server.close() }
    }

    private val norden = Instant.parse("2026-10-11T13:30:00Z").toEpochMilli()      // the quarter of the fixture

    /** A server answering the quarter of the fixture (any other: none), each answer [delayMs] late. */
    private fun server(delayMs: Long = 0): MockWebServer = MockWebServer().apply {
        val csv = Fixtures.text("lightning_groups_norden.csv")
        val empty = csv.lineSequence().first() + "\n"
        dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                if (delayMs > 0) Thread.sleep(delayMs)
                val ours = "2026-10-11T13:30:00Z/" in (request.url.queryParameter("cql_filter") ?: "")
                return MockResponse.Builder().code(200).body(if (ours) csv else empty).build()
            }
        }
        start()
    }

    private fun layer(server: MockWebServer) =
        LightningLayer(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), OkHttpClient(), server.url("/ows").toString())
            .apply { setView(52.0, 54.5, 5.5, 9.5) }

    /** The radar's time up to now (the flashes a minute or two late); none ahead of now; the request of a quarter hour. */
    @Test fun theStepOfTheRadarsTime() {
        val t = Instant.parse("2026-10-11T00:20:00Z").toEpochMilli()
        assertEquals(t, LightningLayer.timeFor(t, t + 60_000L))
        assertEquals(null, LightningLayer.timeFor(t + 30 * 60_000L, t))
        val l = LightningLayer(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), OkHttpClient())
        val g = dev.nimbus.weather.ui.radar.FieldGeo.forView(52.0, 54.5, 5.5, 9.5, maxSide = 256, minPxKm = 0.0)
        val url = java.net.URLDecoder.decode(l.url(g, norden), "UTF-8")
        assertTrue(url, "outputFormat=csv" in url && "srsName=CRS:84" in url && "sortBy=GROUP_END_TIME DESC" in url)
        assertTrue(url, "GROUP_END_TIME DURING 2026-10-11T13:30:00Z/2026-10-11T13:45:00Z" in url && "'CRS:84'" in url)
    }

    /**
     * Each flash a point where its first group lit, at that minute – 20 flashes of 67 groups that
     * quarter around Norden; the newest the largest and brightest, nothing left after 15 minutes.
     */
    @Test fun theNewestBrightest() {
        val flashes = LightningLayer.flashes(Fixtures.text("lightning_groups_norden.csv"))!!
        assertEquals(20, flashes.size)
        assertTrue(flashes.all { it.lat in 52.0..54.5 && it.lon in 5.5..9.5 && it.time in norden..norden + 15 * 60_000L })
        assertNull(LightningLayer.flashes("<ServiceExceptionReport/>"))
        assertEquals(LightningLayer.RADIUS_DP to 1f, LightningLayer.look(0))
        assertEquals(0f to 0f, LightningLayer.look(LightningLayer.FADE_MS))
        val half = LightningLayer.look(LightningLayer.FADE_MS / 2)
        assertTrue("$half", half.first in 1f..LightningLayer.RADIUS_DP - 1 && half.second in 0.3f..0.7f)
    }

    /** At a time only the flashes of the 15 minutes before it: none struck later, none older. */
    @Test fun anotherTimeTakesTheFlashesAway() {
        val server = server()
        val layer = layer(server)
        val t = norden + 15 * 60_000L
        layer.at(t, now = t + 3_600_000L)
        val until = System.currentTimeMillis() + 5000
        while (!layer.ready(t) && System.currentTimeMillis() < until) Thread.sleep(20)
        val at = layer.flashesAt(t)
        val all = LightningLayer.flashes(Fixtures.text("lightning_groups_norden.csv"))!!
        // those of the last 15 minutes (one struck at 13:30 is 15 minutes old at 13:45: gone)
        assertEquals(all.count { t - it.time in 0 until LightningLayer.FADE_MS }, at.size)
        assertTrue("${at.size}", at.size in 10 until all.size)
        // half an hour later: all of them gone; before them: none yet
        assertTrue(layer.flashesAt(t + 30 * 60_000L).isEmpty())
        assertTrue(layer.flashesAt(norden - 60_000L).isEmpty())
        // a time ahead of now (the radar's forecast): none
        layer.at(null)
        assertNull(layer.shownTime)
        server.close()
    }

    /**
     * Playing (a quarter hour every 250 ms, each answer 400 ms late): the quarters come before
     * playback reaches them – loaded ahead; asked only when reached, not one in time.
     */
    @Test fun theFlashesKeepUpWithPlayback() {
        val server = server(delayMs = 400)
        val quarter = 15 * 60_000L
        fun play(base: Long, ahead: Boolean): Int {
            val layer = layer(server)
            var inTime = 0
            for (k in 0 until 24) {
                val t = base + k * quarter
                layer.at(t, now = base + 2 * 24 * 3_600_000L)
                if (ahead) layer.ahead((1..4).map { t + it * quarter }, now = base + 2 * 24 * 3_600_000L)
                Thread.sleep(250)
                if (k >= 4 && layer.ready(t)) inTime++
            }
            return inTime
        }
        val kept = play(norden - 24 * 3_600_000L, ahead = true)
        assertTrue("ready in time: $kept of 20", kept >= 16)
        assertEquals("asked only when reached: none in time", 0, play(norden - 48 * 3_600_000L, ahead = false))
        server.close()
    }

    /** The minutes go on with the clock between the askings; past the window the lightning is over. */
    @Test fun theMinutesFollowTheClock() {
        val ctx = RuntimeEnvironment.getApplication()
        val n = LightningNearby(12.4, now, 27)
        var clock by androidx.compose.runtime.mutableLongStateOf(now)
        compose.setContent { LightningNote(n, clock) {} }
        fun line(min: Int) = ctx.getString(R.string.lightning_nearest_km, 12) + " · " +
            (if (min < 1) ctx.getString(R.string.lightning_just_now) else ctx.getString(R.string.lightning_minutes_ago, min)) + " · " +
            ctx.resources.getQuantityString(R.plurals.lightning_flashes, 27, 27)
        compose.onNodeWithText(line(0)).assertExists()
        clock = now + 14 * 60_000L + 10_000L
        compose.onNodeWithText(line(14)).assertExists()
        assertTrue(n.current(now + 14 * 60_000L))
        assertTrue(!n.current(now + LightningSource.WINDOW_MS))
    }

    /** The note: how near, how long ago, how many – tapped, the radar's lightning. */
    @Test fun theNote() {
        val ctx = RuntimeEnvironment.getApplication()
        var opened = false
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                LightningNote(LightningNearby(12.4, now - 3 * 60_000L, 27), now) { opened = true }
                LightningNote(LightningNearby(4.7, now, 218, more = true), now) {}
            }
        }
        val near = ctx.getString(R.string.lightning_nearest_km, 12) + " · " + ctx.getString(R.string.lightning_minutes_ago, 3) + " · " +
            ctx.resources.getQuantityString(R.plurals.lightning_flashes, 27, 27)
        compose.onNodeWithText(near).assertExists()
        val overhead = ctx.getString(R.string.lightning_overhead) + " · " + ctx.getString(R.string.lightning_just_now) + " · " +
            ctx.resources.getQuantityString(R.plurals.lightning_flashes_more, 218, 218)
        compose.onNodeWithText(overhead).assertExists()
        compose.onNodeWithText(near).performClick()
        assertTrue(opened)
    }
}
