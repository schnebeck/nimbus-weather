/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/GridWindowTest.kt
 * A composite's cells of an area: found, cut from the whole step, asked for exactly.
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

import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.GridWindow
import dev.nimbus.weather.ui.radar.KnmiRadar
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.bbox
import dev.nimbus.weather.ui.radar.codeAt
import dev.nimbus.weather.ui.radar.cut
import dev.nimbus.weather.ui.radar.whole
import dev.nimbus.weather.ui.radar.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.zip.GZIPInputStream

class GridWindowTest {
    /** Around Hannover, 2 cells more each side; beyond the grid: none. */
    @Test fun theWindowOfAnArea() {
        val w = DwdRadar.window(8.0, 11.5, 51.7, 53.0)!!
        assertEquals(GridWindow(658, 327, 355, 135), w)
        assertNull(DwdRadar.window(-20.0, -10.0, 30.0, 40.0))
        // at the grid's edge it ends there
        assertEquals(0, DwdRadar.window(1.0, 2.0, 55.0, 57.0)!!.row0)
        assertEquals(DwdRadar.whole, DwdRadar.window(0.0, 30.0, 40.0, 60.0))
    }

    /** The cells cut out are the whole step's at the same places. */
    @Test fun cutFromTheWhole() {
        val codes = ByteArray(DwdRadar.W * DwdRadar.H) { (it % 97).toByte() }
        val w = DwdRadar.window(8.0, 11.5, 51.7, 53.0)!!
        val part = DwdRadar.cut(codes, w)
        assertEquals(w.size, part.size)
        for ((lat, lon) in listOf(52.375 to 9.735, 51.705 to 8.005, 52.995 to 11.495)) {
            val r = ((DwdRadar.LAT1 - lat) / RadarComposite.STEP).toInt() - w.row0
            val c = ((lon - DwdRadar.LON0) / RadarComposite.STEP).toInt() - w.col0
            assertEquals("at $lat, $lon", DwdRadar.codeAt(codes, lat, lon), part[r * w.w + c].toInt() and 0xFF)
        }
    }

    /**
     * The request asks for exactly the window's cells: its edges on the grid's, one pixel per cell
     * (the DWD and the KNMI answered so on 5 October 2026: the same cells as the whole step's).
     */
    @Test fun theRequestAsksForTheWindow() {
        val w = GridWindow(660, 60, 430, 240)
        assertEquals("8.00,53.30,12.30,55.70", DwdRadar.bbox(w))
        val t = Instant.parse("2026-10-05T12:50:00Z").toEpochMilli()
        val dwd = DwdRadar.url(t, w)
        assertTrue(dwd, "bbox=8.00,53.30,12.30,55.70&width=430&height=240" in dwd)
        assertTrue(dwd, "bbox=1.40,45.60,18.80,56.30&width=1740&height=1070" in DwdRadar.url(t))
        val knmi = KnmiRadar.url(t, KnmiRadar.window(5.0, 8.5, 51.0, 53.2, margin = 0)!!)
        assertTrue(knmi, "bbox=5.00,51.00,8.51,53.21&width=351&height=221" in knmi)
    }

    /** The KNMI's answer for a window read at the window's size – a whole grid is not one. */
    @Test fun theKnmiWindowRead() {
        val text = javaClass.classLoader!!.getResourceAsStream("fixtures/knmi_reflectivity.asc.gz")!!
            .let { GZIPInputStream(it).bufferedReader().readText() }
        val whole = KnmiRadar.parse(text)!!
        assertNull(KnmiRadar.parse(text, 350, 220))
        val small = "ncols 3\nnrows 2\nxllcorner 5.0\nyllcorner 51.0\ncellsize 0.01\nNODATA_value 95.5\n-32 20.5 95.5\n8 -10 41\n"
        val codes = KnmiRadar.parse(small, 3, 2)!!.map { it.toInt() and 0xFF }
        assertEquals(listOf(0, 21, RadarComposite.NO_DATA, 8, 0, 41), codes)
        assertEquals(KnmiRadar.W * KnmiRadar.H, whole.size)
    }
}
