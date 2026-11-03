/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TempAxisTest.kt
 * The temperature axis: its marks counted from the freezing point, that one stronger.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.Meteogram
import dev.nimbus.weather.ui.main.PrecipAxis
import dev.nimbus.weather.ui.main.TempAxis
import dev.nimbus.weather.ui.main.tempAxis
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TempAxisTest {
    @get:Rule val compose = createComposeRule()

    /** The marks counted from 0 °C in both directions: 0 is always one of them, where it lies in the chart. */
    @Test fun theMarksFromZeroBothWays() {
        val a = TempAxis.of(-3.0, 7.0, TemperatureUnit.CELSIUS)
        assertEquals(listOf(-5.0, 0.0, 5.0, 10.0), a.ticks)
        assertTrue(a.isAnchor(0.0))
        val cold = TempAxis.of(-14.0, -6.0, TemperatureUnit.CELSIUS)
        assertTrue(cold.ticks.all { it % cold.step == 0.0 })
        assertTrue("0 above the chart: no frost mark", cold.ticks.none(cold::isAnchor))
        val warm = TempAxis.of(16.0, 27.0, TemperatureUnit.CELSIUS)
        assertEquals(listOf(15.0, 20.0, 25.0, 30.0), warm.ticks)
        // in Fahrenheit from 32 °F
        val f = TempAxis.of(25.0, 40.0, TemperatureUnit.FAHRENHEIT)
        assertTrue(f.ticks.toString(), 32.0 in f.ticks && f.isAnchor(32.0))
        assertTrue(f.ticks.all { (it - 32.0) % f.step == 0.0 })
    }

    /** Two to four steps; a flat day spans at least 4 degrees, as before. */
    @Test fun fewMarksAFlatDayFlat() {
        val flat = TempAxis.of(10.0, 10.4, TemperatureUnit.CELSIUS)
        assertEquals(listOf(9.0, 10.0, 11.0, 12.0, 13.0), flat.ticks)
        for ((lo, hi) in listOf(-30.0 to 35.0, 2.0 to 3.0, -0.5 to 0.5, 4.0 to 19.0)) {
            val a = TempAxis.of(lo, hi, TemperatureUnit.CELSIUS)
            assertTrue("$lo..$hi: ${a.ticks}", a.intervals in 2..4 && a.lo <= lo - 1 && a.hi >= hi + 1)
        }
    }

    /**
     * Every axis, whatever the range: marks in whole, round steps (1, 2, 5, 10, 20, 50°), evenly
     * apart and counted from the freezing point – never "−4, 8, 19" (the range's ends and middle);
     * each number is the value of its line, all values inside with a degree of room.
     */
    @Test fun everyAxisRoundAndEven() {
        val rnd = java.util.Random(7)
        repeat(2000) {
            val unit = if (it % 3 == 0) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            val lo = rnd.nextDouble() * 80 - 40
            val hi = lo + rnd.nextDouble() * 45
            val a = TempAxis.of(lo, hi, unit)
            val f = TempAxis.freezing(unit)
            val where = "$unit $lo..$hi: ${a.ticks}"
            assertTrue(where, a.step in listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0))
            assertTrue(where, a.intervals in 2..4)
            assertTrue(where, a.ticks.zipWithNext().all { (x, y) -> kotlin.math.abs(y - x - a.step) < 1e-9 })
            assertTrue(where, a.ticks.all { t -> t == Math.rint(t) && kotlin.math.abs(Math.IEEEremainder(t - f, a.step)) < 1e-9 })
            assertTrue(where, a.lo <= lo - 1 + 1e-9 && a.hi >= hi + 1 - 1e-9)
            // the freezing line exactly where it lies in the chart, nowhere else
            assertEquals(where, f in a.lo..a.hi, a.ticks.any(a::isAnchor))
        }
    }

    /** A summer's day: the chart starts where its values do, no 0 and no frost line in it. */
    @Test fun aSummerDayWithoutTheFrostLine() {
        val a = TempAxis.of(16.3, 31.8, TemperatureUnit.CELSIUS)
        assertTrue(a.ticks.toString(), a.lo >= 10.0 && a.ticks.none(a::isAnchor))
        assertEquals(listOf(15.0, 20.0, 25.0, 30.0, 35.0), a.ticks)
    }

    /** The spread's chart: runs from −3.6 to 18.4° on −10, 0, 10, 20 – not on −4, 8, 19. */
    @Test fun theSpreadsAxisOnRoundDegrees() {
        val n = 48
        val sp = dev.nimbus.weather.data.model.Spread(
            fetchedAt = 0, latitude = 0.0, longitude = 0.0, members = 51, times = (0 until n).map { it * 3_600_000L },
            lowest = List(n) { -3.6 }, low = List(n) { 0.0 }, median = List(n) { 5.0 }, high = List(n) { 10.0 }, highest = List(n) { 18.4 },
            days = emptyList(),
        )
        val a = sp.tempAxis(0, TemperatureUnit.CELSIUS)!!
        assertEquals(listOf(-10.0, 0.0, 10.0, 20.0), a.ticks)
    }

    /** The model comparison's chart alike: 3.2 to 11.7° on 0, 5, 10, 15. */
    @Test fun theModelComparisonsAxisOnRoundDegrees() {
        val times = (0 until 72).map { it * 3_600_000L }
        val models = listOf(
            dev.nimbus.weather.data.model.ModelSeries("a", "A", times, times.indices.map { 3.2 + it % 5 }, emptyList()),
            dev.nimbus.weather.data.model.ModelSeries("b", "B", times, times.indices.map { 11.7 - it % 3 }, emptyList()),
        )
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0), dev.nimbus.weather.ui.main.modelsTempAxis(models, 0, 72, TemperatureUnit.CELSIUS)!!.ticks)
    }

    /** The air pressure's chart alike: round hectopascals, never the range's ends and middle. */
    @Test fun thePressureAxisOnRoundValues() {
        assertEquals(listOf(1005.0, 1010.0, 1015.0, 1020.0), dev.nimbus.weather.ui.main.pressureAxis(1008.4, 1016.9).ticks)
        val calm = dev.nimbus.weather.ui.main.pressureAxis(1021.2, 1021.9)
        assertTrue(calm.ticks.toString(), calm.hi - calm.lo >= 6 && calm.ticks.all { it == Math.rint(it) && it % calm.step == 0.0 })
        val rnd = java.util.Random(11)
        repeat(500) {
            val lo = 960 + rnd.nextDouble() * 80
            val a = dev.nimbus.weather.ui.main.pressureAxis(lo, lo + rnd.nextDouble() * 30)
            assertTrue(a.ticks.toString(), a.intervals in 2..4 && a.ticks.all { t -> t % a.step == 0.0 })
        }
    }

    /** The precipitation beside it on the same lines: round amounts on every one. */
    @Test fun thePrecipitationOnTheSameLines() {
        assertEquals(0.5, PrecipAxis.step(1.0, 3, false), 1e-9)
        assertEquals(1.0, PrecipAxis.step(3.2, 4, false), 1e-9)
        assertEquals(2.0, PrecipAxis.step(4.5, 3, false), 1e-9)
        assertEquals(0.02, PrecipAxis.step(0.04, 2, true), 1e-9)
        // a little rain beside four steps: a quarter millimetre each, the scale stays at 1 mm
        assertEquals(0.25, PrecipAxis.step(1.0, 4, false), 1e-9)
        assertEquals(listOf("0", "0.25", "0.5", "0.75", "1.0"), (0..4).map { PrecipAxis.label(it, 0.25, false) })
    }

    /** The day chart around frost: the 0 line stronger and blue, the marks from it both ways. */
    @Test fun theDayChartAroundFrost() {
        val day = 1_790_812_800_000L
        val h = 3_600_000L
        val pts = (0..25).map { k ->
            MeteoPoint(day + k * h, 1.5 + 4 * sin((k - 8) / 24.0 * 2 * Math.PI), Condition.CLOUDY, k in 7..17, if (k in 12..15) 0.6 else 0.0,
                precipitationChance = 40.0, windSpeed = 10.0, windDirection = 250.0, sunshine = 0.0)
        }
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("UTC", true)) {
                Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { Meteogram(pts, day, day + 24 * h, emptyList(), day + 10 * h) }
            }
        }
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        // the frost line: a row bluish across the plot in dashes – about two thirds of it (the curve crosses a row in a few pixels only)
        fun icy(c: Int) = android.graphics.Color.blue(c) - android.graphics.Color.red(c) > 60 && android.graphics.Color.blue(c) > 170
        val xs = (img.width / 5 until img.width * 4 / 5)
        val blue = (0 until img.height).any { y -> xs.count { x -> icy(img.getPixel(x, y)) } in (xs.count() * 0.45).toInt()..(xs.count() * 0.8).toInt() }
        assertTrue("no frost line", blue)
        compose.onRoot().captureRoboImage("src/test/screenshots/frost_line.png")
    }
}
