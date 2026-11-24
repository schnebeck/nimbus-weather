/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CoverageMaskTest.kt
 * The radars' reach renewed by its age: a radar out of service is no longer counted as covering.
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
import dev.nimbus.weather.ui.radar.CoverageMask
import dev.nimbus.weather.ui.radar.RadarComposites
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class CoverageMaskTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val start = System.currentTimeMillis()
    private var now = start
    private val hour = 3_600_000L

    private val T = 0x00FFFFFF
    private val G = 0x807E7E7E.toInt()

    /** 7 × 5: a grey ring around one radar's area. */
    private val ring = intArrayOf(
        T, T, T, T, T, T, T,
        T, G, G, G, G, G, T,
        T, G, T, T, T, G, T,
        T, G, G, G, G, G, T,
        T, T, T, T, T, T, T,
    )
    /** The same with that radar out of service: grey where it measured. */
    private val out = IntArray(ring.size) { i -> if (i in setOf(16, 17, 18)) G else ring[i] }

    @Before fun setUp() {
        server.start()
        RadarComposites.dir = Files.createTempDirectory("mask").toFile()
    }

    @After fun tearDown() {
        server.shutdown()
        RadarComposites.dir?.deleteRecursively()
        RadarComposites.dir = null
    }

    private fun png(px: IntArray) = MockResponse().setHeader("Content-Type", "image/png").setBody(
        Buffer().write(
            ByteArrayOutputStream().also { o ->
                Bitmap.createBitmap(px, 7, 5, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, o)
            }.toByteArray(),
        ),
    )

    private fun mask() = CoverageMask(
        "test_coverage.png", 0.0, 0.0, 7.0, 5.0, 7, 5,
        url = { server.url("/mask").toString() }, classify = ::enclosed, maxAgeMs = hour, clock = { now },
    )

    /** Covered: not grey, with grey to its left and to its right in its row. */
    private fun enclosed(argb: IntArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        fun grey(k: Int) = (argb[k] ushr 24) in 1..199
        val row = i / w * w
        !grey(i) && (row until i).any(::grey) && (i + 1 until row + w).any(::grey)
    }

    /** The middle of the radar's area. */
    private fun CoverageMask.middle() = covers(2.5, 3.5)

    @Test fun aRadarOutOfServiceIsNoLongerCoveredAfterAnHour() = runBlocking {
        val m = mask()
        server.enqueue(png(ring))
        m.ensure(http)
        assertTrue(m.middle())
        // within the hour: the same mask, no request
        now = start + hour / 2
        m.ensure(http)
        assertEquals(1, server.requestCount)
        // an hour on: the composite has a hole where the radar measured
        now = start + hour + 1
        server.enqueue(png(out))
        m.ensure(http)
        assertEquals(2, server.requestCount)
        assertFalse(m.middle())
    }

    @Test fun theMaskInUseStaysWhenTheServiceFails() = runBlocking {
        val m = mask()
        server.enqueue(png(ring))
        m.ensure(http)
        now = start + 2 * hour
        server.enqueue(MockResponse().setResponseCode(503))
        m.ensure(http)
        assertTrue(m.ready)
        assertTrue(m.middle())
    }

    @Test fun anOlderPictureIsBetterThanNone() = runBlocking {
        server.enqueue(png(ring))
        mask().ensure(http)
        // the app started anew, offline, the next day
        now = start + 24 * hour
        server.enqueue(MockResponse().setResponseCode(503))
        val m = mask()
        m.ensure(http)
        assertTrue(m.ready)
        assertTrue(m.middle())
    }
}
