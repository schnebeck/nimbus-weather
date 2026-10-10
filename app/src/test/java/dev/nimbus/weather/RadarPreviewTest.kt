/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarPreviewTest.kt
 * The preview's radar picture is drawn as the radar loop draws it (one way for both, any place):
 * an image comparison of a rain area over Hannover.
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
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.RadarField
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarLayer
import dev.nimbus.weather.ui.radar.RadarPicture
import dev.nimbus.weather.ui.radar.RadarPreview
import dev.nimbus.weather.ui.radar.RadarTimeline
import dev.nimbus.weather.ui.radar.cut
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class RadarPreviewTest {
    /** No network: whatever is not handed over, cannot be had. */
    private val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
    private val frame = RadarFrame(1_790_900_100_000L, false, null, null)
    private val timeline = RadarTimeline(listOf(frame), 0, "")

    /** A shower over Hannover: 45 dBZ in its core, weaker outwards, 25 cells across. */
    private val shower = ByteArray(DwdRadar.W * DwdRadar.H).also { g ->
        val r0 = ((DwdRadar.LAT1 - 52.3759) / RadarComposite.STEP).toInt(); val c0 = ((9.7320 - DwdRadar.LON0) / RadarComposite.STEP).toInt()
        for (dr in -12..12) for (dc in -12..12) {
            val d = hypot(dr.toDouble(), dc.toDouble())
            if (d <= 12) g[(r0 + dr) * DwdRadar.W + c0 + dc] = (45 - d * 2).toInt().toByte()
        }
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    /** The small preview from the grid: the very picture of the radar loop, the preview's size. */
    @Test fun thePreviewIsTheLoopsPicture() = runBlocking {
        val g = RadarPreview.geo(52.3759, 9.732, 380, 220)
        val still = RadarPicture.still(offline, timeline, frame, g) { c, w -> if (c == DwdRadar) DwdRadar.cut(shower, w) else null }!!
        assertEquals(760 to 440, still.width to still.height)
        val px = pixels(still)
        assertTrue("no rain in the middle", px[220 * 760 + 380] ushr 24 > 0)
        assertEquals("rain in the corner", 0, px[5 * 760 + 5] ushr 24)
        // drawn as the loop draws a frame
        val loop = RadarField.extract(g, listOf(RadarLayer(DwdRadar, shower, RadarPicture.coverage(g)[DwdRadar])), null)
        val out = IntArray(g.w * g.h).also { RadarField.render(loop, null, null, 0f, g.w, g.h, null, it) }
        // (a bitmap keeps its colours multiplied by the opacity: nearly transparent pixels come back
        // with rounded colours – the opacity is the same, the colour wherever it is seen)
        for (i in px.indices) {
            assertEquals("opacity at $i", out[i] ushr 24, px[i] ushr 24)
            // the rounding of a colour multiplied by the opacity a: up to 255 / a
            val a = out[i] ushr 24
            if (a >= 32) for (sh in listOf(16, 8, 0)) {
                assertEquals("colour at $i", ((out[i] shr sh) and 0xFF).toDouble(), ((px[i] shr sh) and 0xFF).toDouble(), 255.0 / a + 1)
            }
        }
        still.captureRoboImage("src/test/screenshots/radar_preview.png")
    }

    /** Nothing to be had (no composite step, no RainViewer frame): no picture – the stored one stays. */
    @Test fun nothingToBeHadNoPicture() = runBlocking {
        assertNull(RadarPicture.still(offline, timeline, frame, RadarPreview.geo(52.3759, 9.732, 380, 220)) { _, _ -> null })
    }
}
