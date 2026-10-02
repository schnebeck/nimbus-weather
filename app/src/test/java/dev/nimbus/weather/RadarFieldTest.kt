/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarFieldTest.kt
 * The radar picture of the view: cut from the grid, motion found, frames in between moved.
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

import dev.nimbus.weather.ui.radar.DwdGrid
import dev.nimbus.weather.ui.radar.FieldGeo
import dev.nimbus.weather.ui.radar.RadarField
import dev.nimbus.weather.ui.radar.ViewFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RadarFieldTest {
    private val w = 200
    private val h = 150

    /** A round rain cell of [dbz] with radius [r] around ([cx], [cy]). */
    private fun blob(cx: Float, cy: Float, r: Float = 9f, dbz: Int = 30): ViewFrame {
        val d = ByteArray(w * h); val wet = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (hypot(x - cx, y - cy) <= r) {
            d[y * w + x] = (dbz * 2).toByte(); wet[y * w + x] = 255.toByte()
        }
        return ViewFrame(d, wet, null)
    }

    /** Centre and size of what is drawn. */
    private fun drawn(out: IntArray): Triple<Float, Float, Int> {
        var sx = 0.0; var sy = 0.0; var n = 0
        for (i in out.indices) if (out[i] ushr 24 > 128) { sx += i % w; sy += i / w; n++ }
        return Triple((sx / n).toFloat(), (sy / n).toFloat(), n)
    }

    @Test fun motionOfAMovingCellIsFound() {
        val a = blob(80f, 70f); val b = blob(92f, 75f)
        val flow = RadarField.motion(a, b, w, h, maxShift = 20f)
        val v = FloatArray(2).also { flow.at(80f, 70f, it) }
        assertEquals(12f, v[0], 2f)
        assertEquals(5f, v[1], 2f)
    }

    @Test fun halfwayTheCellIsHalfwayNotDoubled() {
        val a = blob(80f, 70f); val b = blob(92f, 75f)
        val flow = RadarField.motion(a, b, w, h, maxShift = 20f)
        val out = IntArray(w * h)
        RadarField.render(a, b, null, 0f, w, h, null, out)
        val (x0, y0, n0) = drawn(out)
        RadarField.render(a, b, flow, 0.5f, w, h, null, out)
        val (xm, ym, nm) = drawn(out)
        assertEquals(80f, x0, 0.5f); assertEquals(70f, y0, 0.5f)
        assertEquals(86f, xm, 1.5f); assertEquals(72.5f, ym, 1.5f)
        // one cell of about the same size – a plain cross-fade would show two half-strong cells
        assertTrue("area $nm vs $n0", nm in (n0 * 0.75).toInt()..(n0 * 1.25).toInt())
    }

    /** [a] and [b] laid over each other (the stronger pixel wins). */
    private fun both(a: ViewFrame, b: ViewFrame) = ViewFrame(
        ByteArray(w * h) { maxOf(a.dbz[it].toInt() and 0xFF, b.dbz[it].toInt() and 0xFF).toByte() },
        ByteArray(w * h) { maxOf(a.wet[it].toInt() and 0xFF, b.wet[it].toInt() and 0xFF).toByte() }, null,
    )

    /**
     * A cell that dissolves while another forms nearby: it fades where it is (or drifts with the
     * rain around it) – it does not slide over to the new one (the "unrealistic shifts" when the
     * clouds dissolve). The rain around moves 2 px east.
     */
    @Test fun aDissolvingCellDoesNotJumpToANewOne() {
        val a = both(blob(60f, 75f, 8f), blob(150f, 40f, 14f))
        val b = both(blob(75f, 78f, 8f), blob(152f, 40f, 14f))           // 60 gone, 75 new; the big one moved by 2
        val flow = RadarField.motion(a, b, w, h, maxShift = 20f)
        val v = FloatArray(2).also { flow.at(60f, 75f, it) }
        assertTrue("dissolving cell moved by (${v[0]}, ${v[1]})", hypot(v[0], v[1]) < 5f)
        // the cell that moves is still found
        flow.at(150f, 40f, v)
        assertEquals(2f, v[0], 1.5f); assertEquals(0f, v[1], 1.5f)
    }

    @Test fun stillRainStaysStill() {
        val a = blob(100f, 75f)
        val flow = RadarField.motion(a, a, w, h, maxShift = 20f)
        val v = FloatArray(2).also { flow.at(100f, 75f, it) }
        assertEquals(0f, v[0], 0.5f); assertEquals(0f, v[1], 0.5f)
    }

    @Test fun fieldCutFromTheGridAtThePlace() {
        // One wet grid cell at Garbsen (52.42 N, 9.60 E), the view around it
        val grid = ByteArray(DwdGrid.W * DwdGrid.H)
        val r = ((DwdGrid.LAT1 - 52.42) / DwdGrid.STEP).toInt(); val c = ((9.60 - DwdGrid.LON0) / DwdGrid.STEP).toInt()
        for (dy in -2..2) for (dx in -3..3) grid[(r + dy) * DwdGrid.W + c + dx] = 35
        val geo = FieldGeo.forView(52.2, 52.6, 9.3, 9.9, maxSide = 300, margin = 0.0)
        val f = RadarField.extract(geo, grid, null, null)
        val my = FieldGeo.mercY(52.42); val mx = FieldGeo.R * Math.toRadians(9.60)
        val px = ((mx - geo.minX) / geo.pxM).toInt(); val py = ((geo.maxY - my) / geo.pxM).toInt()
        val i = py * geo.w + px
        assertTrue((f.wet[i].toInt() and 0xFF) > 200)
        assertEquals(35f, (f.dbz[i].toInt() and 0xFF) / 2f, 1f)
        // dry far away
        assertEquals(0, f.wet[5 * geo.w + 5].toInt())
    }

    @Test fun zoomedInTheCellsAreSmoothedNotBlocks() {
        assertEquals(0, RadarField.smoothRadius(1.2))
        assertTrue(RadarField.smoothRadius(0.1) >= 4)
    }
}
