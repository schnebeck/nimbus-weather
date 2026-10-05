/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/KnmiRadarTest.kt
 * The KNMI radar composite: its raw values on the cells of the DWD grid, drawn where the DWD's
 * radars do not reach. Recorded answer of 1 October 2026, 12:00 UTC (rain over the Low Countries).
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
import dev.nimbus.weather.ui.radar.KnmiRadar
import dev.nimbus.weather.ui.radar.RadarField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class KnmiRadarTest {
    private val codes by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/knmi_reflectivity.asc.gz")!!
        requireNotNull(KnmiRadar.parse(GZIPInputStream(stream).bufferedReader().readText())) { "not the KNMI grid" }
    }

    /** "KNMI-Radar integrieren": the grid read – rain, dry, beyond the radars. */
    @Test fun theCompositeRead() {
        assertEquals(KnmiRadar.W * KnmiRadar.H, codes.size)
        assertEquals(41, KnmiRadar.at(codes, 50.315, 6.725))                 // the strongest echo
        assertEquals(33, KnmiRadar.at(codes, 50.165, 1.985))
        assertEquals(KnmiRadar.NO_DATA, KnmiRadar.at(codes, 52.425, 10.165)) // east of the radars' reach
        assertEquals(KnmiRadar.NO_DATA, KnmiRadar.at(codes, 40.0, 5.0))      // outside the grid
        assertTrue("no dry cell", (0 until codes.size).any { codes[it].toInt() == 0 })
    }

    /** "passt da unsere bisherige visuelle Dateninterpolation zu?": its cells are the DWD grid's. */
    @Test fun itsCellsAreTheDwdGrids() {
        assertEquals(DwdGrid.LON0, KnmiRadar.LON0, 1e-9)
        assertEquals(DwdGrid.STEP, KnmiRadar.STEP, 1e-9)
        for (lat in listOf(49.125, 51.505, 53.005, 55.905)) {           // cell centres
            val dwdRow = floor((DwdGrid.LAT1 - lat) / DwdGrid.STEP).toInt()
            val knmiRow = floor((KnmiRadar.LAT1 - lat) / KnmiRadar.STEP).toInt()
            assertEquals("row at $lat", 33, dwdRow - knmiRow)
        }
    }

    /** The URL asks for exactly these cells (width and height given: 0.01° each) at a time. */
    @Test fun theRequest() {
        val url = KnmiRadar.url(java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli())
        assertTrue(url, "width=944&height=707" in url && "time=2026-10-01T12:00:00Z" in url && "bbox=1.4,48.90,10.84,55.97" in url)
    }

    private fun geo(lat: Double, lon: Double, halfDeg: Double = 0.05): FieldGeo {
        fun x(l: Double) = FieldGeo.R * Math.toRadians(l)
        fun y(l: Double) = FieldGeo.R * ln(tan(Math.PI / 4 + Math.toRadians(l) / 2))
        return FieldGeo(x(lon - halfDeg), y(lat - halfDeg), x(lon + halfDeg), y(lat + halfDeg), 16, 16)
    }

    /** Where the DWD's radars do not reach, the picture shows the KNMI's rain – beyond both, none. */
    @Test fun thePictureTakesTheDutchRain() {
        val here = geo(50.315, 6.725)
        val withKnmi = RadarField.extract(here, dwd = null, rv = null, inDwd = null, knmi = codes)
        val without = RadarField.extract(here, dwd = null, rv = null, inDwd = null, knmi = null)
        assertTrue("no rain from the KNMI grid", withKnmi.wet.any { it.toInt() and 0xFF > 0 })
        assertTrue(without.wet.all { it.toInt() == 0 })
        // beyond the radars' reach: nothing (RainViewer would fill in)
        val beyond = RadarField.extract(geo(52.425, 10.165), dwd = null, rv = null, inDwd = null, knmi = codes)
        assertTrue(beyond.wet.all { it.toInt() == 0 })
    }
}
