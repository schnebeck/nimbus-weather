/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarPaletteTest.kt
 * Tests for the radar colour scale.
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

import dev.nimbus.weather.ui.radar.RadarPalette
import dev.nimbus.weather.ui.radar.RadarPalette.Source
import dev.nimbus.weather.ui.radar.RadarSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPaletteTest {
    private fun alpha(c: Int) = c ushr 24

    @Test
    fun `DWD legend colours map to the unified palette`() {
        val yellow = RadarPalette.mapPixel(0xFFFFFF00.toInt(), Source.DWD)     // 32.5..37 dBZ
        assertEquals(RadarPalette.colorFor(35, false), yellow)
        assertTrue(alpha(yellow) > 200)
    }

    @Test
    fun `DWD no-data grey and transparent white are removed`() {
        assertEquals(0, RadarPalette.mapPixel(0x807D7D7D.toInt(), Source.DWD))
        assertEquals(0, RadarPalette.mapPixel(0x00FFFFFF, Source.DWD))
        // magenta radar coverage outline is not part of the legend
        assertEquals(0, alpha(RadarPalette.mapPixel(0xFFFF00FF.toInt(), Source.DWD)))
    }

    @Test
    fun `RainViewer universal blue decodes to the same scale`() {
        // #00a3e0ff is 20 dBZ in the rain block
        assertEquals(RadarPalette.colorFor(20, false), RadarPalette.mapPixel(0xFF00A3E0.toInt(), Source.RAINVIEWER))
        // #ffee00ff is 35 dBZ
        assertEquals(RadarPalette.colorFor(35, false), RadarPalette.mapPixel(0xFFFFEE00.toInt(), Source.RAINVIEWER))
        // snow block #7fbfffff is 20 dBZ snow
        assertEquals(RadarPalette.colorFor(20, true), RadarPalette.mapPixel(0xFF7FBFFF.toInt(), Source.RAINVIEWER))
        // very weak echoes (< 8 dBZ) are hidden to reduce clutter
        assertEquals(0, RadarPalette.mapPixel(0x14636159, Source.RAINVIEWER))
    }

    @Test
    fun `palette is monotonic in alpha for weak echoes`() {
        assertTrue(alpha(RadarPalette.colorFor(10, false)) < alpha(RadarPalette.colorFor(30, false)))
        assertNotEquals(RadarPalette.colorFor(40, false), RadarPalette.colorFor(40, true))
    }

    @Test
    fun `wms time format`() {
        assertEquals("2026-09-28T20:40:00.000Z", RadarSources.isoTime(java.time.Instant.parse("2026-09-28T20:40:00Z").toEpochMilli()))
    }

    // ---- smoothing of the radar cells

    private val dark = 0xFF009934.toInt()      // DWD 17 dBZ
    private val yellow = 0xFFFFFF00.toInt()    // DWD 35 dBZ

    /** [w]×4 image whose columns come from [col]. */
    private fun image(w: Int, col: (Int) -> Int) = IntArray(w * 4) { col(it % w) }

    @Test
    fun `fractional reflectivity blends neighbouring colours`() {
        val a = RadarPalette.colorFor(20, 0f)
        val b = RadarPalette.colorFor(21, 0f)
        val m = RadarPalette.colorFor(20.5f, 0f)
        for (sh in listOf(0, 8, 16)) {
            val ca = (a shr sh) and 0xFF; val cb = (b shr sh) and 0xFF; val cm = (m shr sh) and 0xFF
            assertTrue(cm in minOf(ca, cb)..maxOf(ca, cb))
        }
    }
}
