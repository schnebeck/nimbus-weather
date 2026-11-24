/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WnFileTest.kt
 * A step of the DWD's composite out of its file: the numbers, the grid, the codes, the reach.
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

import dev.nimbus.weather.ui.radar.DwdCoverage
import dev.nimbus.weather.ui.radar.DwdProjection
import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.DwdResampler
import dev.nimbus.weather.ui.radar.Hdf5
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.WnStep
import dev.nimbus.weather.ui.radar.codeAt
import dev.nimbus.weather.ui.radar.grid16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The analysis of 24 Nov 2026, 19:20 UTC, as the open data server gave it. */
internal fun wnFile(): ByteArray = WnFileTest::class.java.classLoader!!.getResourceAsStream("fixtures/dwd_wn_20261124_1920_000.hd5")!!.readBytes()

class WnFileTest {
    private val file = wnFile()

    @Test fun theFileIsReadAsItsGroupsTell() {
        val h5 = Hdf5(file)
        assertEquals(setOf("dataset1", "how", "what", "where"), h5.children(h5.root).keys)
        val scaling = h5.doubles(h5.path("dataset1/data1/what"))
        assertEquals(0.0029298, scaling.getValue("gain"), 1e-6)
        assertEquals(-64.00293, scaling.getValue("offset"), 1e-5)
        assertEquals(65535.0, scaling.getValue("nodata"), 0.0)
        assertEquals(0.0, scaling.getValue("undetect"), 0.0)
        val grid = h5.grid16(h5.path("dataset1/data1/data"))
        assertEquals(1200, grid.rows)
        assertEquals(1100, grid.cols)
        var none = 0
        var empty = 0
        for (i in 0 until grid.rows * grid.cols) when (grid.at(i)) {
            65535 -> none++
            0 -> empty++
        }
        assertEquals(514490, none)
        assertEquals(800723, empty)
    }

    @Test fun aFileCutShortOrOfAnotherMakeIsNoStep() {
        assertNotNull(WnStep.read(file))
        assertNull(WnStep.read(file.copyOf(file.size / 2)))
        assertNull(WnStep.read(file.copyOf(2000)))
        assertNull(WnStep.read(ByteArray(4000)))
        assertNull(WnStep.read("not such a file".toByteArray()))
    }

    /** The corners the file names, and a place whose cell another program computed. */
    @Test fun theGridLiesWhereTheFileSaysItsCornersAre() {
        val (left, top) = DwdProjection.cell(55.862087108249824, 1.463301510256666)
        assertEquals(0.0, left, 0.01)
        assertEquals(0.0, top, 0.01)
        val (right, bottom) = DwdProjection.cell(45.68460578137082, 16.580869348598274)
        assertEquals(1100.0, right, 0.01)
        assertEquals(1200.0, bottom, 0.01)
        val (col, row) = DwdProjection.cell(52.37, 9.73)
        assertEquals(524.549, col, 0.005)
        assertEquals(441.229, row, 0.005)
    }

    /** Between its nodes the grid is weighed, not computed: the same cell as computed, but for a few at a cell's edge. */
    @Test fun betweenItsNodesTheGridIsLaidAlmostExactly() {
        val cells = DwdResampler(DwdRadar.LON0, DwdRadar.LAT1, RadarComposite.STEP, DwdRadar.W, DwdRadar.H)
        var asked = 0
        var other = 0
        val sources = cells.sources()
        for (cell in sources.indices step 5) {
            val source = sources[cell]
            val lat = DwdRadar.LAT1 - (cell / DwdRadar.W + 0.5) * RadarComposite.STEP
            val lon = DwdRadar.LON0 + (cell % DwdRadar.W + 0.5) * RadarComposite.STEP
            val (col, row) = DwdProjection.cell(lat, lon)
            val inside = col >= 0 && row >= 0 && col < DwdProjection.COLS && row < DwdProjection.ROWS
            val exact = if (inside) row.toInt() * DwdProjection.COLS + col.toInt() else -1
            asked++
            if (exact == source) continue
            other++
            // the neighbour at most, and only next to the edge between the two
            val edge = minOf(abs(col - Math.rint(col)), abs(row - Math.rint(row)))
            assertTrue("cell $cell: $source for $exact, $edge from an edge", edge < 0.02)
        }
        assertEquals(DwdRadar.W * DwdRadar.H / 5, asked)
        assertTrue("$other of $asked", other < asked / 100)
    }

    @Test fun theStepOnTheRadarGrid() {
        val codes = WnStep.read(file)!!.codes()
        assertEquals(DwdRadar.W * DwdRadar.H, codes.size)
        val wet = codes.count { it.toInt() != 0 }
        assertTrue("$wet cells with an echo", wet in 2250..2340)
        assertEquals(61, codes.maxOf { it.toInt() })
        assertTrue(codes.all { it.toInt() == 0 || it.toInt() in 8..95 })
        // a strong cell over the Baltic coast, two of the app's cells wide
        assertEquals(59, DwdRadar.codeAt(codes, 54.205, 12.08))
        // dry over Hannover; beyond the composite's grid nothing either
        assertEquals(0, DwdRadar.codeAt(codes, 52.37, 9.73))
        assertEquals(0, DwdRadar.codeAt(codes, 46.0, 2.0))
    }

    @Test fun whereTheRadarsReach() {
        val reach = WnStep.read(file)!!.reach()
        assertEquals(DwdCoverage.W * DwdCoverage.H, reach.size)
        val covered = reach.count { it }
        assertTrue("$covered cells", covered in 149_500..151_000)
        fun at(lat: Double, lon: Double) = reach[((DwdCoverage.LAT1 - lat) / 0.025).toInt() * DwdCoverage.W + ((lon - DwdCoverage.LON0) / 0.025).toInt()]
        assertTrue(at(52.37, 9.73) && at(48.14, 11.58) && at(53.37, 7.2))
        assertFalse(at(48.85, 2.35))
        assertFalse(at(56.2, 1.5))
    }
}
