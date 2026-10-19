/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarScaleTest.kt
 * The radar's colours: the calm blue scale as the default – rain sky blue to magenta, snow grey to
 * white, apart for red-green colour blindness –, the weakest steps easier to see, the legend from
 * the weakest step with words and rain rates; the separate precipitation chart as the default.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.RadarColors
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.radar.LegendSteps
import dev.nimbus.weather.ui.radar.RadarPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class RadarScaleTest {
    @get:Rule val compose = createComposeRule()
    private val before = RadarPalette.scheme

    @After fun restore() { RadarPalette.scheme = before }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun a(c: Int) = c ushr 24
    private fun luma(c: Int) = 0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)

    /** New installations (and settings never changed): the blue scale and precipitation as a chart of its own. */
    @Test fun theDefaults() {
        assertEquals(RadarColors.BLUE, Settings().radarColors)
        assertTrue(Settings().separatePrecipitation)
        val stored = JsonCodec.decodeFromString(Settings.serializer(), "{}")
        assertEquals(RadarColors.BLUE, stored.radarColors)
        assertTrue(stored.separatePrecipitation)
        // a choice made keeps
        val contrast = JsonCodec.encodeToString(Settings.serializer(), Settings(radarColors = RadarColors.CONTRAST, separatePrecipitation = false))
        assertEquals(RadarColors.CONTRAST, JsonCodec.decodeFromString(Settings.serializer(), contrast).radarColors)
    }

    /** Rain sky blue → blue → violet → magenta, the heavy end brighter than the middle; snow grey → white. */
    @Test fun blueToMagentaAndGreyToWhite() {
        RadarPalette.scheme = RadarColors.BLUE
        val weak = RadarPalette.colorFor(14, 0f)
        assertTrue("weak rain sky blue", b(weak) > 200 && b(weak) > r(weak) + 60)
        val mid = RadarPalette.colorFor(30, 0f)
        assertTrue("moderate rain blue", b(mid) > 150 && r(mid) < 80)
        val violet = RadarPalette.colorFor(44, 0f)
        assertTrue("heavy rain violet", r(violet) > 130 && b(violet) > 200 && g(violet) < 100)
        val top = RadarPalette.colorFor(56, 0f)
        assertTrue("severe magenta", r(top) > 230 && b(top) > 200 && g(top) < r(top) - 100)
        assertTrue("the heavy end brighter than the middle", luma(top) > luma(mid) + 30)
        // snow without a hue, whiter the more of it
        var last = 0.0
        for (d in 12..56 step 4) {
            val c = RadarPalette.colorFor(d, 1f)
            assertTrue("snow at $d grey", abs(r(c) - b(c)) < 25 && abs(g(c) - b(c)) < 25)
            assertTrue("snow at $d whiter", luma(c) >= last); last = luma(c)
        }
        assertEquals(0xFFFFFFFF.toInt(), RadarPalette.colorFor(56, 1f))
        // no colour in both
        for (d in 12..56) assertTrue(RadarPalette.colorFor(d, 0f) != RadarPalette.colorFor(d, 1f))
    }

    /** Drizzle (about 0.1 mm/h) is easier to see: the two weakest steps more than half opaque, from 12 dBZ all of it – in both scales. */
    @Test fun theWeakestStepsShowMore() {
        for (s in RadarColors.entries) {
            RadarPalette.scheme = s
            for (snow in listOf(0f, 1f)) {
                assertTrue("$s 8 dBZ", a(RadarPalette.colorFor(8, snow)) in 0x80 until 0xFF)
                assertTrue("$s 10 dBZ", a(RadarPalette.colorFor(10, snow)) in 0xB0 until 0xFF)
                assertEquals("$s 12 dBZ", 0xFF, a(RadarPalette.colorFor(12, snow)))
            }
        }
    }

    /** sRGB → linear → the deuteranope's view (Machado 2009, severity 1) → sRGB. */
    private fun deutan(c: Int): DoubleArray {
        fun lin(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        val (lr, lg, lb) = Triple(lin(r(c)), lin(g(c)), lin(b(c)))
        val m = arrayOf(doubleArrayOf(0.367322, 0.860646, -0.227968), doubleArrayOf(0.280085, 0.672501, 0.047413), doubleArrayOf(-0.011820, 0.042940, 0.968881))
        return DoubleArray(3) { i -> (m[i][0] * lr + m[i][1] * lg + m[i][2] * lb).coerceIn(0.0, 1.0) }
    }

    /** The CIE76 distance of two linear colours (D65). */
    private fun deltaE(x: DoubleArray, y: DoubleArray): Double {
        fun lab(v: DoubleArray): DoubleArray {
            val xx = (0.4124 * v[0] + 0.3576 * v[1] + 0.1805 * v[2]) / 0.95047
            val yy = 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]
            val zz = (0.0193 * v[0] + 0.1192 * v[1] + 0.9505 * v[2]) / 1.08883
            fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
            return doubleArrayOf(116 * f(yy) - 16, 500 * (f(xx) - f(yy)), 200 * (f(yy) - f(zz)))
        }
        val p = lab(x); val q = lab(y)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    /** With red-green colour blindness the steps of the legend (drizzle … severe) stay apart in the blue scale. */
    @Test fun apartForRedGreenColourBlindness() {
        RadarPalette.scheme = RadarColors.BLUE
        val steps = LegendSteps.map { (dbz, _) -> deutan(RadarPalette.colorFor(maxOf(dbz, 12), 0f)) }
        val gaps = steps.zipWithNext { x, y -> deltaE(x, y) }
        assertTrue("neighbouring steps seen apart: $gaps", gaps.all { it > 20 })
        // and rain is not taken for snow there either
        for (d in listOf(14, 23, 34, 44, 50)) {
            assertTrue("rain and snow at $d", deltaE(deutan(RadarPalette.colorFor(d, 0f)), deutan(RadarPalette.colorFor(d, 1f))) > 15)
        }
    }

    /** The steps' rain rates (Marshall–Palmer, Z = 200 R^1.6) at the reflectivities they stand at. */
    @Test fun theStepsRates() {
        for ((dbz, s) in LegendSteps) {
            val mm = s.second
            val z = 10 * log10(200 * mm.pow(1.6))
            assertEquals("$mm mm/h", dbz.toDouble(), z, 1.5)
        }
        assertEquals(RadarPalette.LEGEND_MIN_DBZ, LegendSteps.first().first)
        assertEquals(RadarPalette.colorFor(8, 0f), RadarPalette.legendRain.first())
    }

    /** The legend: rain and snow bars from the weakest step, words and rates under them. */
    @Test fun theLegend() {
        RadarPalette.scheme = RadarColors.BLUE
        compose.setContent {
            Column(Modifier.width(411.dp).background(Color(0xFF05080D)).padding(16.dp)) {
                dev.nimbus.weather.ui.radar.Legend(showTemp = false, showSnow = true, unit = TemperatureUnit.CELSIUS)
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/radar_legend.png")
    }
}
