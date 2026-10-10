/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NordicRadarTest.kt
 * MET Norway's Nordic composite, used at the zoom it suits: read from MET
 * Norway's answers of 5 October 2026, 19:50 UTC, in two grids by scale.
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

import android.graphics.BitmapFactory
import dev.nimbus.weather.ui.radar.FieldGeo
import dev.nimbus.weather.ui.radar.GridWindow
import dev.nimbus.weather.ui.radar.NordicFine
import dev.nimbus.weather.ui.radar.NordicOverview
import dev.nimbus.weather.ui.radar.NordicRadar
import dev.nimbus.weather.ui.radar.RadarComposites
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarStore
import dev.nimbus.weather.ui.radar.bbox
import dev.nimbus.weather.ui.radar.codeAt
import dev.nimbus.weather.ui.radar.whole
import dev.nimbus.weather.ui.radar.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class NordicRadarTest {
    private fun png(name: String): IntArray {
        val bytes = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.readBytes()
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPremultiplied = false })
        return IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    }
    private val legend by lazy { NordicRadar.legendTable(png("nordic_legend.png")) }

    /** The colour bar: black the highest, white the lowest – read back to dBZ, falling with the grey. */
    @Test fun theColourBarReadsBack() {
        assertEquals(80.0, legend[0].toDouble(), 0.5)
        assertEquals(0.0, legend[255].toDouble(), 0.5)
        assertTrue((1..255).all { legend[it] <= legend[it - 1] })
    }

    /** Around Bergen: showers read as dBZ (8–95), the rest dry – the transparent cells. */
    @Test fun aWindowAroundBergen() {
        val px = png("nordic_bergen.png")
        val codes = NordicRadar.codes(px, legend)
        val wet = codes.count { (it.toInt() and 0xFF) >= 8 }
        val opaque = px.count { (it ushr 24) >= 128 }
        assertTrue("wet $wet of ${codes.size}", wet in 500..opaque)
        assertTrue(codes.all { val v = it.toInt() and 0xFF; v == 0 || v in 8..95 })
        assertTrue((codes.maxOf { it.toInt() and 0xFF }) in 25..70)
        assertTrue(px.indices.filter { (px[it] ushr 24) == 0 }.all { codes[it].toInt() == 0 })
    }

    /** Where the radars reach: Bergen, Stockholm, Helsinki – not the Atlantic far out, not beyond the area. */
    @Test fun whereItsRadarsReach() {
        val mask = dev.nimbus.weather.ui.radar.NordicCoverage.compute(png("nordic_nodata.png"), 1220, 500)
        fun at(lat: Double, lon: Double) = mask[((73.1 - lat) / 0.04).toInt() * 1220 + ((lon + 8.0) / 0.04).toInt()]
        assertTrue(at(60.39, 5.32)); assertTrue(at(59.33, 18.07)); assertTrue(at(60.17, 24.94))
        assertFalse(at(66.0, -6.0))
        assertFalse(at(53.5, 40.0))
    }

    /** One daily file per day, the step by its time; a window's edges on the grid's cells. */
    @Test fun theRequest() {
        val t = Instant.parse("2026-10-05T19:50:00Z").toEpochMilli()
        val w = NordicFine.window(4.0, 7.0, 59.5, 61.5, margin = 0)!!
        val (west, south, east, north) = NordicFine.bbox(w).split(',').map { it.toDouble() }
        assertTrue("${NordicFine.bbox(w)}", west <= 4.0 && south <= 59.5 && east >= 7.0 && north >= 61.5)
        // its edges on the cells: whole steps of 0.01° from the grid's corner
        for (v in listOf(west - NordicRadar.LON0, NordicRadar.LAT1 - north)) assertEquals(Math.round(v * 100) / 100.0, v, 1e-6)
        val url = NordicRadar.mapUrl("equivalent_reflectivity_factor", t, NordicFine.bbox(w), w.w, w.h)
        assertTrue(url, "/2026/10/yrwms-nordic.mos.pcappi-0-dbz.noclass-clfilter-novpr-clcorr-block.nordiclcc-1000.20261005.nc?" in url)
        assertTrue(url, "time=2026-10-05T19:50:00Z" in url && "width=${w.w}&height=${w.h}" in url)
    }

    /**
     * "nur in einer entsprechenden Zoomstufe": zoomed out the overview (0.04°), closer the 1 km
     * cells in windows of at most a million cells (Scandinavia at 1 km: about 4000 × 2400 – refused).
     */
    @Test fun twoGridsByScale() {
        assertEquals(1220 to 500, NordicOverview.w to NordicOverview.h)
        assertEquals(4880 to 2000, NordicFine.w to NordicFine.h)
        assertTrue(NordicFine.windowed && !NordicOverview.windowed)
        // the view of the screenshot (Scandinavia, ~2 km field pixels) and one around Bergen
        val scandinavia = FieldGeo.forView(54.0, 71.0, 2.0, 31.0, maxSide = 1024)
        val bergen = FieldGeo.forView(59.9, 60.9, 4.3, 6.3, maxSide = 1024)
        assertTrue(NordicOverview.serves(scandinavia) && !NordicFine.serves(scandinavia))
        assertTrue(NordicFine.serves(bergen) && !NordicOverview.serves(bergen))
        // Bergen's window: snapped to blocks of 64 cells, not the whole grid
        val w = RadarStore.areaWindow(NordicFine, bergen)!!
        assertTrue("$w", w.col0 % 64 == 0 && w.row0 % 64 == 0 && w.size < 400_000)
        assertEquals(NordicOverview.whole, RadarStore.areaWindow(NordicOverview, scandinavia))
    }

    /** Stored per step (and window); kept four days like the DWD's analyses. */
    @Test fun storeNamesAndExpiry() {
        val f = RadarFrame(Instant.parse("2026-10-05T19:50:00Z").toEpochMilli(), false, null, null)
        val win = GridWindow(1216, 1152, 256, 128)
        val name = RadarStore.areaKey(NordicFine, f, win)
        assertEquals("nordic1_${f.time / 60_000}_w1216_1152_256x128", name)
        assertEquals(false, NordicFine.expired(name, f.time, f.time + 3_600_000L, null))
        assertEquals(true, NordicFine.expired(name, f.time, f.time + 5L * 24 * 3_600_000L, null))
        assertEquals(null, NordicOverview.expired(name, f.time, f.time, null))
    }

    /** A place in Norway follows the Nordic composite's steps (once its reach is known); one in Germany the DWD's. */
    @Test fun aPlaceFollowsItsComposite() {
        dev.nimbus.weather.ui.radar.NordicCoverage.setForTest(dev.nimbus.weather.ui.radar.NordicCoverage.compute(png("nordic_nodata.png"), 1220, 500))
        assertEquals(NordicOverview, RadarComposites.anchorFor(60.39, 5.32))
        assertEquals(dev.nimbus.weather.ui.radar.DwdRadar, RadarComposites.anchorFor(52.38, 9.73))
        assertEquals(NordicOverview.codeAt(ByteArray(NordicOverview.w * NordicOverview.h), 60.0, 5.0), 0)
    }

    /** The look-back's radar of a whole day: wherever a composite keeps its steps – Bergen now too, Madrid not (RainViewer: two hours). */
    @Test fun aDayOfRadarWhereACompositeCovers() {
        dev.nimbus.weather.ui.radar.NordicCoverage.setForTest(dev.nimbus.weather.ui.radar.NordicCoverage.compute(png("nordic_nodata.png"), 1220, 500))
        assertTrue(RadarComposites.covered(60.39, 5.32))
        assertTrue(RadarComposites.covered(52.38, 9.73))
        assertFalse(RadarComposites.covered(40.42, -3.70))
    }
}
