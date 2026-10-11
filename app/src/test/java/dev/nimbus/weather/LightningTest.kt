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

    /** The radar's time: the newest step ending before it (the flashes a minute or two late); none ahead of now. */
    @Test fun theStepOfTheRadarsTime() {
        val t = Instant.parse("2026-10-11T00:20:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-11T00:15:00Z").toEpochMilli(), LightningLayer.stepFor(t, t + 60_000L))
        assertEquals(Instant.parse("2026-10-11T00:20:00Z").toEpochMilli(), LightningLayer.stepFor(t, t + 5 * 60_000L))
        assertEquals(Instant.parse("2026-10-10T22:05:00Z").toEpochMilli(), LightningLayer.stepFor(Instant.parse("2026-10-10T22:08:00Z").toEpochMilli(), t))
        // the radar's forecast: no lightning is forecast
        assertNull(LightningLayer.stepFor(t + 30 * 60_000L, t))
        val url = LightningLayer.url(dev.nimbus.weather.ui.radar.FieldGeo.forView(50.0, 54.0, 6.0, 12.0), t)
        assertTrue(url, "time=2026-10-11T00:15:01.000Z/2026-10-11T00:20:00.000Z" in url && "crs=EPSG:3857" in url && "transparent=true" in url)
    }

    /**
     * The flashes stand for their own time only: a new time takes them away at once, its own come
     * when loaded; a time loaded before (the loop played again) comes at once, and a time passed
     * by while playing goes on loading for the next round.
     */
    @Test fun anotherTimeTakesTheFlashesAway() {
        val png = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        val slow = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                asked += request.url.queryParameter("time")!!
                if (slow.get()) Thread.sleep(600)
                return MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").body(okio.Buffer().write(png)).build()
            }
        }
        server.start()
        val layer = LightningLayer(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), OkHttpClient(), server.url("/wms").toString())
        layer.setView(50.0, 54.0, 6.0, 12.0)
        fun until(step: Long?) {
            val end = System.currentTimeMillis() + 5000
            while (layer.shownStep != step && System.currentTimeMillis() < end) Thread.sleep(20)
            assertEquals(step, layer.shownStep)
        }
        val a = Instant.parse("2026-10-11T00:15:00Z").toEpochMilli()
        val b = a - 15 * 60_000L
        val c = b - 15 * 60_000L
        layer.show(a)
        until(a)
        slow.set(true)
        layer.show(b)
        assertNull("the flashes of the time before stay", layer.shownStep)
        until(b)
        val n = asked.size
        layer.show(a)
        assertEquals(a, layer.shownStep)
        // passed by: its steps load on
        layer.show(c)
        layer.show(a)
        val end = System.currentTimeMillis() + 5000
        while (asked.size < n + 3 && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(1000)
        layer.show(c)
        until(c)
        assertEquals(n + 3, asked.size)
        // a time ahead (the radar's forecast): none
        layer.show(null)
        assertNull(layer.shownStep)
        server.close()
    }

    /**
     * Playing (a day of the look-back: a quarter hour every 250 ms here, each picture 400 ms on its
     * way): the flashes keep up – one picture per quarter hour, the next ones loaded ahead. As
     * before (three pictures per step, nothing ahead) not one came in time.
     */
    @Test fun theFlashesKeepUpWithPlayback() {
        val png = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                Thread.sleep(400)
                return MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").body(okio.Buffer().write(png)).build()
            }
        }
        server.start()
        val quarter = 15 * 60_000L
        fun play(base: Long, playing: Boolean, ahead: Int): Int {
            val layer = LightningLayer(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), OkHttpClient(), server.url("/wms").toString())
            layer.setView(52.0, 54.0, 6.0, 9.0)
            var inTime = 0
            for (k in 0 until 24) {
                val step = base + k * quarter
                layer.show(step, playing, (1..ahead).map { step + it * quarter })
                Thread.sleep(250)
                if (k >= 4 && layer.shownStep == step) inTime++
            }
            return inTime
        }
        val a = Instant.parse("2026-10-11T08:00:00Z").toEpochMilli()
        val kept = play(a, playing = true, ahead = 6)
        assertTrue("shown in time: $kept of 20", kept >= 16)
        val before = play(a + 24 * 3_600_000L, playing = false, ahead = 0)
        assertEquals("as before: none in time", 0, before)
        server.close()
    }

    /** The three steps in one bright colour, the newest strongest, the oldest faintest. */
    @Test fun theNewestBrightest() {
        fun step(x: Int) = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply { setPixel(x, 0, 0xFF00FF00.toInt()) }
        val out = LightningLayer.compose(listOf(step(0), step(1), step(2)))
        val alpha = (0..2).map { out.getPixel(it, 0) ushr 24 }
        assertTrue("$alpha", alpha[0] > alpha[1] && alpha[1] > alpha[2] && alpha[2] > 0)
        assertEquals(0xFFFFF176.toInt() and 0xFFFFFF, out.getPixel(0, 0) and 0xFFFFFF)
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
