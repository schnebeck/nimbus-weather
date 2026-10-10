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

import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.RadarLayer
import dev.nimbus.weather.ui.radar.codeAt
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
        assertEquals(41, KnmiRadar.codeAt(codes, 50.315, 6.725))                 // the strongest echo
        assertEquals(33, KnmiRadar.codeAt(codes, 50.165, 1.985))
        assertEquals(RadarComposite.NO_DATA, KnmiRadar.codeAt(codes, 52.425, 10.165)) // east of the radars' reach
        assertEquals(RadarComposite.NO_DATA, KnmiRadar.codeAt(codes, 40.0, 5.0))      // outside the grid
        assertTrue("no dry cell", (0 until codes.size).any { codes[it].toInt() == 0 })
    }

    /** The KNMI's cells fit the smoothing made for the DWD's: they are the DWD grid's. */
    @Test fun itsCellsAreTheDwdGrids() {
        assertEquals(DwdRadar.lon0, KnmiRadar.lon0, 1e-9)
                for (lat in listOf(49.125, 51.505, 53.005, 55.905)) {           // cell centres
            val dwdRow = floor((DwdRadar.lat1 - lat) / RadarComposite.STEP).toInt()
            val knmiRow = floor((KnmiRadar.lat1 - lat) / RadarComposite.STEP).toInt()
            assertEquals("row at $lat", 33, dwdRow - knmiRow)
        }
    }

    /** The URL asks for exactly these cells (width and height given: 0.01° each) at a time. */
    @Test fun theRequest() {
        val url = KnmiRadar.url(java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli())
        assertTrue(url, "width=944&height=707" in url && "time=2026-10-01T12:00:00Z" in url && "bbox=1.40,48.90,10.84,55.97" in url)
    }

    private fun geo(lat: Double, lon: Double, halfDeg: Double = 0.05): FieldGeo {
        fun x(l: Double) = FieldGeo.R * Math.toRadians(l)
        fun y(l: Double) = FieldGeo.R * ln(tan(Math.PI / 4 + Math.toRadians(l) / 2))
        return FieldGeo(x(lon - halfDeg), y(lat - halfDeg), x(lon + halfDeg), y(lat + halfDeg), 16, 16)
    }

    /** Where the DWD's radars do not reach, the picture shows the KNMI's rain – beyond both, none. */
    @Test fun thePictureTakesTheDutchRain() {
        val here = geo(50.315, 6.725)
        val withKnmi = RadarField.extract(here, listOf(RadarLayer(KnmiRadar, codes, null)), rv = null)
        val without = RadarField.extract(here, emptyList(), rv = null)
        assertTrue("no rain from the KNMI grid", withKnmi.wet.any { it.toInt() and 0xFF > 0 })
        assertTrue(without.wet.all { it.toInt() == 0 })
        // beyond the radars' reach: nothing (RainViewer would fill in)
        val beyond = RadarField.extract(geo(52.425, 10.165), listOf(RadarLayer(KnmiRadar, codes, null)), rv = null)
        assertTrue(beyond.wet.all { it.toInt() == 0 })
    }

    /**
     * The processing does not depend on the DWD's data: it takes the composites in order – where the first covers a spot it shows it, dry or not; where it does
     * not cover it, the next one.
     */
    @Test fun theFirstCompositeCoveringASpotShowsIt() {
        val here = geo(50.315, 6.725)
        val n = here.w * here.h
        val dryDwd = ByteArray(DwdRadar.W * DwdRadar.H)
        val dwdCovers = RadarField.extract(here, listOf(RadarLayer(DwdRadar, dryDwd, BooleanArray(n) { true }), RadarLayer(KnmiRadar, codes, null)), null)
        assertTrue("the DWD's dry spot shown wet", dwdCovers.wet.all { it.toInt() == 0 })
        val dwdNot = RadarField.extract(here, listOf(RadarLayer(DwdRadar, dryDwd, BooleanArray(n) { false }), RadarLayer(KnmiRadar, codes, null)), null)
        assertTrue("the KNMI's rain not shown beyond the DWD's area", dwdNot.wet.any { it.toInt() and 0xFF > 0 })
    }

    /** The composites known to the processing, the DWD's first, then the KNMI's, then MET Norway's two grids; KNMI past steps only. */
    @Test fun theCompositesInOrder() {
        assertEquals(listOf("dwd", "knmi", "nordic4", "nordic1"), dev.nimbus.weather.ui.radar.RadarComposites.all.map { it.id })
        assertTrue(DwdRadar.hasNowcast && DwdRadar.exactCoverage)
        assertTrue(!KnmiRadar.hasNowcast && !KnmiRadar.exactCoverage)
    }
}
