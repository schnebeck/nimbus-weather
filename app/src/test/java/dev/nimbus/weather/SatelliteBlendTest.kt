/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SatelliteBlendTest.kt
 * The satellite while the radar plays: the picture before and the one after shown together, the
 * later one over the earlier as the time goes on; the next ones loaded ahead.
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
import dev.nimbus.weather.ui.radar.SatelliteLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class SatelliteBlendTest {
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = t("2026-10-11T20:00:00Z")

    /** Playing: both pictures around the time are shown blended, and the next ones are asked for before playback comes to them. */
    @Test fun bothPicturesAndTheNextOnes() {
        val jpeg = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }.toByteArray()
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.url.queryParameter("time")!!
                return MockResponse.Builder().code(200).setHeader("Content-Type", "image/jpeg").body(okio.Buffer().write(jpeg)).build()
            }
        }
        server.start()
        val layer = SatelliteLayer(CoroutineScope(Dispatchers.Unconfined), OkHttpClient(), server.url("/wms").toString())
        layer.setView(52.0, 54.5, 5.5, 9.5)
        val time = t("2026-10-11T14:35:00Z")
        val until = System.currentTimeMillis() + 5000
        while (layer.shown?.after == null && System.currentTimeMillis() < until) {
            layer.at(time, playing = true, stepMs = SatelliteLayer.STEP_MS, now = now)
            Thread.sleep(50)
        }
        assertEquals(SatelliteLayer.Blend(t("2026-10-11T14:30:00Z"), t("2026-10-11T14:40:00Z"), 0.5f), layer.shown)
        // and the next pictures, up to 40 minutes on
        assertTrue(asked.toString(), listOf("14:30", "14:40", "14:50", "15:00", "15:10").all { hm -> asked.any { "T$hm:00" in it } })
        // standing again: the picture alone
        layer.at(time, playing = false, stepMs = SatelliteLayer.STEP_MS, now = now)
        assertEquals(SatelliteLayer.Blend(t("2026-10-11T14:30:00Z"), null, 0f), layer.shown)
        server.close()
    }
}
