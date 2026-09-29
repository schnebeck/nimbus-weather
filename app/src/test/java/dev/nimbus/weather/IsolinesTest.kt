/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/IsolinesTest.kt
 * Tests for the isolines of the temperature field.
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

import dev.nimbus.weather.ui.radar.Isolines
import dev.nimbus.weather.ui.radar.WeatherGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolinesTest {
    /** 3 x 3 grid, temperature rising west → east: 0 / 5 / 10 °C per column. */
    private fun grid(): WeatherGrid {
        val t = FloatArray(9) { i -> (i % 3) * 5f }
        return WeatherGrid(50.0, 8.0, 1.0, 3, 3, longArrayOf(0L), arrayOf(t), arrayOf(FloatArray(9)), arrayOf(FloatArray(9)))
    }

    @Test
    fun `isolines of a west-east gradient are north-south lines at the right longitude`() {
        val segs = Isolines.compute(grid(), grid().temp[0], band = 2.0, refine = 4)
        assertEquals(setOf(2.0, 4.0, 6.0, 8.0), segs.map { it.level }.toSet())
        // 4 °C lies at 80 % of the first cell: lon 8.8
        segs.filter { it.level == 4.0 }.forEach {
            assertEquals(8.8, it.lon1, 1e-9)
            assertEquals(8.8, it.lon2, 1e-9)
        }
        // lines span the full latitude range 50..52
        val lats = segs.filter { it.level == 6.0 }.flatMap { listOf(it.lat1, it.lat2) }
        assertEquals(50.0, lats.min(), 1e-9)
        assertEquals(52.0, lats.max(), 1e-9)
    }

    @Test
    fun `thin-out zoom and zoom steps are consistent`() {
        // switching to a finer grid happens before labels would need thinning
        // a finer grid is only used from a zoom at which its points are already ≥ 90 dp apart
        for (step in WeatherGrid.STEPS.drop(1)) {
            val firstZoom = generateSequence(0.0) { it + 0.01 }.first { WeatherGrid.stepForZoom(it) == step }
            assertTrue("step $step used from $firstZoom", firstZoom >= dev.nimbus.weather.ui.radar.WeatherOverlays.thinZoom(step))
        }
        assertEquals(1.0, WeatherGrid.stepForZoom(6.6), 0.0)
        assertEquals(0.25, WeatherGrid.stepForZoom(8.6), 0.0)
    }

    @Test
    fun `every band holds exactly one label value`() {
        for (unit in dev.nimbus.weather.data.model.TemperatureUnit.entries) {
            var t = -30.0
            while (t < 45.0) {
                val display = dev.nimbus.weather.util.Units.temperature(t, unit)
                val label = dev.nimbus.weather.util.Units.temp(t, unit).removeSuffix("°").toInt()
                assertEquals("t=$t $unit", label, dev.nimbus.weather.ui.radar.WeatherOverlays.band(display))
                t += 0.05
            }
        }
    }

    @Test
    fun `isolines lie on the rounding borders`() {
        val segs = Isolines.compute(grid(), grid().temp[0], band = 1.0, offset = 0.5)
        assertEquals((0 until 10).map { it + 0.5 }.toSet(), segs.map { it.level }.toSet())
    }
}
