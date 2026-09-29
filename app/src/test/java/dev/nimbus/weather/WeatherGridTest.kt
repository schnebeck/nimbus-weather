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
        // weak rain is neutral white/grey, weak snow is pale pink
        val weakRain = RadarPalette.colorFor(10, 0f)
        val weakSnow = RadarPalette.colorFor(10, 1f)
        assertTrue(kotlin.math.abs(((weakRain shr 16) and 0xFF) - (weakRain and 0xFF)) < 8)
        assertTrue(((weakSnow shr 16) and 0xFF) > ((weakSnow shr 8) and 0xFF))
        assertNotEquals(rain, snow)
        assertTrue(sleet != rain && sleet != snow)
        // rain is blue-dominated, snow has a strong red share (pink/violet)
        assertTrue((rain and 0xFF) > ((rain shr 16) and 0xFF))
        assertTrue(((snow shr 16) and 0xFF) > ((snow shr 8) and 0xFF))
    }
}
