/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RvStepsTest.kt
 * "RainViewer eine zeitlich gröberes Raster … könnte man dessen Daten durch virtuelle Verdopplung
 * interpolierfähig machen?": the step between two RainViewer frames is computed, not a copy.
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

import dev.nimbus.weather.ui.radar.FieldGeo
import dev.nimbus.weather.ui.radar.FrameBuilder
import dev.nimbus.weather.ui.radar.RadarField
import dev.nimbus.weather.ui.radar.RadarMotion
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarTimeline
import dev.nimbus.weather.ui.radar.RvBetween
import dev.nimbus.weather.ui.radar.RvPlan
import dev.nimbus.weather.ui.radar.RvSteps
import dev.nimbus.weather.ui.radar.ViewFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RvStepsTest {
    private val m = 60_000L
    private val t0 = 1_791_219_000_000L / (10 * m) * (10 * m)      // on a RainViewer time (:00, :10 …)

    /** Steps every 5 minutes, RainViewer every 10 (matched as the time line does: nearest, ≤ 5 min). */
    private fun timeline(): RadarTimeline {
        val rv = (0..2).map { t0 + it * 10 * m to "/v2/radar/p$it" }
        val frames = (0..7).map { k ->
            val t = t0 + k * 5 * m
            val match = rv.minBy { kotlin.math.abs(it.first - t) }.takeIf { kotlin.math.abs(it.first - t) <= 5 * m }
            RadarFrame(t, k == 7, if (k == 7) t0 + 30 * m else null, match?.second, match?.first)
        }
        return RadarTimeline(frames, 6, "")
    }

    @Test fun ownBetweenNearNone() {
        val tl = timeline()
        assertEquals(RvPlan.Own("/v2/radar/p0"), RvSteps.plan(tl, 0))
        // :05 – the frame of :00 it used to copy: now half the way from :00 to :10
        assertEquals(RvPlan.Between("/v2/radar/p0", "/v2/radar/p1", 0.5f, 10 * m), RvSteps.plan(tl, 1))
        assertEquals(RvPlan.Own("/v2/radar/p1"), RvSteps.plan(tl, 2))
        // the newest past steps after the newest frame: the nearest (nothing later to move to), then none
        assertEquals(RvPlan.Near("/v2/radar/p2"), RvSteps.plan(tl, 5))
        assertEquals(RvPlan.None, RvSteps.plan(tl, 6))
        // the future: RainViewer has none
        assertEquals(RvPlan.None, RvSteps.plan(tl, 7))
    }

    /** A shower 10 pixels on from one frame to the next: half way in the step between – not where it was. */
    @Test fun theStepBetweenIsMovedHalfWay() {
        val w = 64; val h = 32
        fun blob(cx: Int): ViewFrame {
            val dbz = ByteArray(w * h); val wet = ByteArray(w * h)
            for (y in 12..19) for (x in cx - 3..cx + 3) { dbz[y * w + x] = 70; wet[y * w + x] = 255.toByte() }
            return ViewFrame(dbz, wet, null)
        }
        val a = blob(20); val b = blob(30)
        val flow = RadarMotion.motion(a, b, w, h, 16f)
        val mid = RvBetween.between(a, b, flow, 0.5f, w, h)
        var sum = 0.0; var weight = 0.0
        for (y in 0 until h) for (x in 0 until w) { val c = (mid.wet[y * w + x].toInt() and 0xFF).toDouble(); sum += c * x; weight += c }
        assertEquals("centre of the rain between", 25.0, sum / weight, 1.0)
        // and solid, not two faded halves (a blend in place had two at 50 %)
        assertTrue((mid.wet[16 * w + 25].toInt() and 0xFF) > 200)
    }

    /** The composites where they have values, the computed RainViewer picture where not. */
    @Test fun mergedWithTheComposites() {
        val n = 4
        val composite = ViewFrame(byteArrayOf(60, 60, 0, 0), byteArrayOf(-1, -1, 0, 0), null)
        val rv = ViewFrame(byteArrayOf(0, 0, 40, 40), byteArrayOf(0, 0, -1, -1), null)
        val merged = RvBetween.merge(composite, rv, booleanArrayOf(false, false, true, true))
        assertEquals(listOf(60, 60, 40, 40), merged.dbz.map { it.toInt() })
        assertEquals(n, merged.wet.count { it.toInt() == -1 })
    }

    /** Zoomed out: a field pixel spanning screen pixels gets smoothing; at the screen's size none. */
    @Test fun smoothingForFieldPixelsLargerThanTheScreens() {
        assertEquals(0, FrameBuilder.screenSmooth(1000.0, 1000.0))
        assertEquals(0, FrameBuilder.screenSmooth(1200.0, 1000.0))
        assertEquals(2, FrameBuilder.screenSmooth(2900.0, 1000.0))
        assertEquals(3, FrameBuilder.screenSmooth(9000.0, 1000.0))
        assertEquals(0, FrameBuilder.screenSmooth(2900.0, 0.0))
    }

    /** The smoothing asked for is applied: one wet spot spreads to its neighbours, unsmoothed it stays a block. */
    @Test fun extractSmoothsAsAsked() {
        // 9 × 9 field pixels of 50 km near the equator, in RainViewer's tile x 4, y 3 at zoom 3
        val g = FieldGeo(0.0, 0.0, 9 * 50_000.0, 9 * 50_000.0, 9, 9)
        val tile = ByteArray(512 * 512)
        // the tile pixels under the field's middle pixel: rain of 40 dBZ
        for (py in 0 until 512) for (px in 0 until 512) {
            val mx = (4 + (px + 0.5) / 512) * (2 * 20037508.342789244 / 8) - 20037508.342789244
            val my = 20037508.342789244 - (3 + (py + 0.5) / 512) * (2 * 20037508.342789244 / 8)
            if (mx in 200_000.0..250_000.0 && my in 200_000.0..250_000.0) tile[py * 512 + px] = 40
        }
        val rv = dev.nimbus.weather.ui.radar.RvMosaic(3, mapOf(dev.nimbus.weather.ui.radar.RvMosaic.key(4, 3) to tile))
        fun wetPixels(f: ViewFrame) = f.wet.count { (it.toInt() and 0xFF) > 0 }
        val hard = RadarField.extract(g, emptyList(), rv)
        val soft = RadarField.extract(g, emptyList(), rv, minSmooth = 1)
        assertEquals(1, wetPixels(hard))
        assertTrue("smoothed over ${wetPixels(soft)} pixels", wetPixels(soft) >= 9)
    }
}
