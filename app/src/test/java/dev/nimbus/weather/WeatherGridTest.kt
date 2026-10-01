/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WeatherGridTest.kt
 * Tests for the weather grid of the radar overlays.
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

import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.radar.RadarPalette
import dev.nimbus.weather.ui.radar.TileGeo
import dev.nimbus.weather.ui.radar.WeatherGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherGridTest {
    // 2 x 2 grid (rows south → north, columns west → east), two hours.
    private val json = JsonCodec.parseToJsonElement(
        """[
          {"hourly":{"time":[1000,4600],"temperature_2m":[0.0,10.0],"wind_speed_10m":[5,6],"wind_direction_10m":[270,270]}},
          {"hourly":{"time":[1000,4600],"temperature_2m":[2.0,12.0],"wind_speed_10m":[5,6],"wind_direction_10m":[270,270]}},
          {"hourly":{"time":[1000,4600],"temperature_2m":[4.0,14.0],"wind_speed_10m":[5,6],"wind_direction_10m":[270,270]}},
          {"hourly":{"time":[1000,4600],"temperature_2m":[6.0,16.0],"wind_speed_10m":[5,6],"wind_direction_10m":[270,270]}}
        ]""",
    )
    private val grid = WeatherGrid.parse(json, lat0 = 50.0, lon0 = 8.0, rows = 2, cols = 2, step = 1.0)

    @Test
    fun `parses times and fields in request order`() {
        assertEquals(listOf(1_000_000L, 4_600_000L), grid.times.toList())
        assertEquals(6f, grid.temp[0][3], 0f)
        assertEquals(16f, grid.temp[1][3], 0f)
    }

    @Test
    fun `bilinear sampling and nearest hour`() {
        assertEquals(3f, grid.temperatureAt(50.5, 8.5, 1_000_000L)!!, 1e-4f)   // centre of the cell
        assertEquals(13f, grid.temperatureAt(50.5, 8.5, 4_000_000L)!!, 1e-4f)  // nearer the 2nd hour
        assertEquals(2f, grid.temperatureAt(50.0, 9.0, 0L)!!, 1e-4f)           // exact grid point
        assertNull(grid.temperatureAt(52.0, 8.5, 0L))                          // outside
    }

    @Test
    fun `edge values continue slightly outside and large tiles overlap`() {
        assertEquals(2f, grid.sampleNear(grid.temp[0], 49.7, 9.5)!!, 1e-4f)      // clamped to (50, 9)
        assertNull(grid.sampleNear(grid.temp[0], 47.0, 9.0))
        val bigTile = TileGeo.fromXyz(4, 8, 5)                                       // ~22°x14° around central Europe
        assertTrue(grid.overlaps(bigTile.south, bigTile.north, bigTile.west, bigTile.east))
        assertTrue(!grid.contains(bigTile.centerLat, bigTile.centerLon))
    }

    @Test
    fun `snow legend only where it gets cold enough in view and time`() {
        // South-west point: 0 °C in hour 1, 10 °C in hour 2; north-east point: 6 / 16 °C.
        assertEquals(0f, grid.minTemperature(49.9, 51.1, 7.9, 9.1, 0L, 5_000_000L)!!, 0f)
        assertEquals(10f, grid.minTemperature(49.9, 51.1, 7.9, 9.1, 4_000_000L, 9_000_000L)!!, 0f)   // 2nd hour only
        assertEquals(6f, grid.minTemperature(52.0, 53.0, 10.0, 11.0, 0L, 1_000_000L)!!, 0f)        // view beyond the north-east point: it counts (one spacing margin), the others not
        assertNull(grid.minTemperature(60.0, 61.0, 20.0, 21.0, 0L, 5_000_000L))     // far away
        assertTrue(RadarPalette.showSnowLegend(3f))
        assertTrue(!RadarPalette.showSnowLegend(3.1f))
        assertTrue(RadarPalette.showSnowLegend(null))                                 // no grid: keep it
    }

    @Test
    fun `grid origin snaps to the spacing`() {
        val (a, b) = WeatherGrid.origin(52.52, 13.40)
        val (c, d) = WeatherGrid.origin(52.60, 13.35)
        assertEquals(a, c, 1e-9)
        assertEquals(b, d, 1e-9)
        assertTrue(52.52 in a..(a + 2 * WeatherGrid.HALF_ROWS * WeatherGrid.STEP))
    }

    @Test
    fun `tile geometry matches web mercator`() {
        val world = TileGeo.fromXyz(0, 0, 0)
        assertEquals(0.0, world.centerLat, 1e-9)
        assertEquals(0.0, world.centerLon, 1e-9)
        assertEquals(85.0511, world.latAt(0.0), 1e-3)
        val t = TileGeo.fromBbox("1252344.271424327,6261721.357121639,1878516.4071364924,6887893.492833804")!!
        assertEquals(11.25, t.lonAt(0.0), 1e-6)
        assertEquals(16.875, t.lonAt(1.0), 1e-6)
    }

    @Test
    fun `snow fraction from temperature and blended colours`() {
        assertEquals(1f, RadarPalette.snowFraction(-3f), 0f)
        assertEquals(1f, RadarPalette.snowFraction(0f), 0f)
        assertEquals(0f, RadarPalette.snowFraction(1f), 0f)
        assertEquals(0f, RadarPalette.snowFraction(5f), 0f)
        assertEquals(0.5f, RadarPalette.snowFraction(0.5f), 1e-4f)
        val rain = RadarPalette.colorFor(40, 0f)
        val snow = RadarPalette.colorFor(40, 1f)
        val sleet = RadarPalette.colorFor(40, 0.5f)
        // weak rain is light green, weak snow turquoise; strong rain magenta, strong snow pink
        fun r(c: Int) = (c shr 16) and 0xFF
        fun g(c: Int) = (c shr 8) and 0xFF
        fun bl(c: Int) = c and 0xFF
        val weakRain = RadarPalette.colorFor(14, 0f)
        val weakSnow = RadarPalette.colorFor(14, 1f)
        assertTrue(g(weakRain) > r(weakRain) && g(weakRain) > bl(weakRain))
        assertTrue(bl(weakSnow) > r(weakSnow))
        assertNotEquals(rain, snow)
        assertTrue(sleet != rain && sleet != snow)
        // mid range: rain yellowish (red and green high, blue low), snow near white
        val midRain = RadarPalette.colorFor(33, 0f)
        val midSnow = RadarPalette.colorFor(33, 1f)
        assertTrue(r(midRain) > 200 && g(midRain) > 200 && bl(midRain) < 100)
        assertTrue(r(midSnow) > 230 && g(midSnow) > 230 && bl(midSnow) > 230)
    }

    @Test
    fun `colours are opaque except the weakest echoes`() {
        for (d in 12..56) {
            assertEquals(255, RadarPalette.colorFor(d, 0f) ushr 24)
            assertEquals(255, RadarPalette.colorFor(d, 1f) ushr 24)
        }
        assertTrue((RadarPalette.colorFor(9, 0f) ushr 24) < 255)
    }
}
