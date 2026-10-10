/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/BarLookTest.kt
 * Measured blocks and expected frames as drawn: sunshine and precipitation, in the combined and
 * the separate chart, for today, the look-back and a day to come – checked pixel by pixel.
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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.Meteogram
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Sunshine and precipitation tell measured from expected in every form of the cards, with frame
 * colours that stand out from the measured block in the look-back too. Hours 1–12 are measured, 13–24 expected;
 * each hour's column must show a block exactly where measured and a frame exactly where expected
 * (the look-back: the forecast's frame in front of the block as well).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class BarLookTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val tf = TimeFormat("UTC", true)

    private enum class Form { TODAY, LOOK_BACK, DAY_TO_COME }

    /**
     * 00:00 … 24:00 at a steady 12° (its curve in green, no colour of a bar); no wind, no chance –
     * nothing white that could pass for the grey sunshine block. Measured 0.8 mm and 45 min, the
     * forecast 0.4 mm and 25 min, so a frame in front of its block is seen inside it.
     */
    private fun points(form: Form) = (0..24).map { k ->
        val measured = form != Form.DAY_TO_COME && k <= 12
        MeteoPoint(
            day + k * h, 12.0, Condition.RAIN, true,
            precipitation = if (measured) 0.8 else 0.4, sunshine = if (measured) 45.0 else 25.0,
            precipMeasured = measured, sunMeasured = measured,
            forecastPrecipitation = if (form == Form.LOOK_BACK && measured) 0.4 else null,
            forecastSunshine = if (form == Form.LOOK_BACK && measured) 25.0 else null,
        )
    }

    @Composable
    private fun Card(content: @Composable () -> Unit) = CompositionLocalProvider(
        LocalSettings provides Settings(), LocalTimeFormat provides tf,
    ) {
        Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { content() }
    }

    // ---- the four colours -----------------------------------------------------------------

    private fun Bitmap.rgb(x: Int, y: Int): Triple<Int, Int, Int> = getPixel(x, y).let { Triple((it shr 16) and 255, (it shr 8) and 255, it and 255) }
    private fun rainBlock(r: Int, g: Int, b: Int) = b > 170 && b - r > 60 && g in 140..190
    private fun rainFrame(r: Int, g: Int, b: Int) = abs(r - 107) <= 8 && abs(g - 122) <= 8 && abs(b - 255) <= 8
    private fun sunBlock(r: Int, g: Int, b: Int) = abs(r - 195) < 12 && abs(g - 201) < 12 && abs(b - 210) < 12
    private fun sunFrame(r: Int, g: Int, b: Int) = abs(r - 255) <= 8 && abs(g - 181) <= 8 && abs(b - 71) <= 8

    /** What one hour's column shows. */
    private data class Seen(val rainBlock: Boolean, val rainFrame: Boolean, val sunBlock: Boolean, val sunFrame: Boolean)

    /**
     * The hours' columns of the chart, left to right: runs of columns with a bar colour of
     * precipitation (each hour has a block or a frame) between the chart's top rows (labels,
     * symbols) and its wind row.
     */
    private fun Bitmap.hours(separate: Boolean): List<Seen> {
        val px = 3   // xxhdpi
        val rows = (12 + 44) * px until (12 + 14 + 30 + 120 + (if (separate) 106 else 0) + 26) * px
        fun col(x: Int, test: (Int, Int, Int) -> Boolean) = rows.any { y -> rgb(x, y).let { (r, g, b) -> test(r, g, b) } }
        val marked = (0 until width).filter { x -> col(x, ::rainBlock) || col(x, ::rainFrame) }
        val runs = marked.fold(mutableListOf<IntRange>()) { acc, x ->
            val last = acc.lastOrNull()
            if (last != null && x - last.last <= 2) acc[acc.lastIndex] = last.first..x else acc += x..x
            acc
        }
        return runs.map { run ->
            // the column's inner part: the frame's edges on both sides, the block in between
            Seen(
                run.any { col(it, ::rainBlock) }, run.any { col(it, ::rainFrame) },
                run.any { col(it, ::sunBlock) }, run.any { col(it, ::sunFrame) },
            )
        }
    }

    private fun check(form: Form, separate: Boolean) {
        compose.setContent { Card { Meteogram(points(form), day, day + 24 * h, emptyList(), day + 12 * h + 20 * 60_000L, separatePrecip = separate) } }
        compose.waitForIdle()
        val name = "${form.name.lowercase()}_${if (separate) "separate" else "combined"}"
        val seen = compose.onRoot().captureToImage().asAndroidBitmap().hours(separate)
        assertEquals("$name: hour columns", 24, seen.size)
        seen.forEachIndexed { i, s ->
            val measured = form != Form.DAY_TO_COME && i + 1 <= 12
            val framed = !measured || form == Form.LOOK_BACK
            assertEquals("$name ${i + 1}:00 precipitation block", measured, s.rainBlock)
            assertEquals("$name ${i + 1}:00 precipitation frame", framed, s.rainFrame)
            assertEquals("$name ${i + 1}:00 sunshine block", measured, s.sunBlock)
            assertEquals("$name ${i + 1}:00 sunshine frame", framed, s.sunFrame)
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/bars_$name.png")
    }

    @Test fun todayCombined() = check(Form.TODAY, separate = false)
    @Test fun todaySeparate() = check(Form.TODAY, separate = true)
    @Test fun lookBackCombined() = check(Form.LOOK_BACK, separate = false)
    @Test fun lookBackSeparate() = check(Form.LOOK_BACK, separate = true)
    @Test fun dayToComeCombined() = check(Form.DAY_TO_COME, separate = false)
    @Test fun dayToComeSeparate() = check(Form.DAY_TO_COME, separate = true)

    /**
     * The two frames apart from each other and from the blocks they stand in front of – the blocks
     * as they are seen, on the card (the precipitation's is see-through). The strong blue of the
     * precipitation's frame is near its block in colour – its line is wider instead (2 dp).
     */
    @Test fun framesStandOut() {
        val rain = dev.nimbus.weather.ui.main.HourBars.Rain
        val sun = dev.nimbus.weather.ui.main.HourBars.Sun
        val card = Color(0xFF2B3A4E)
        fun seen(c: Color) = c.compositeOver(card)
        fun dist(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        assertTrue("rain frame vs sun frame", dist(rain.frame, sun.frame) > 0.6f)
        assertTrue("rain frame vs its block", dist(rain.frame, seen(rain.fill)) > 0.35f)
        assertEquals(Color(0xFF6B7AFF), rain.frame)
        // a frame near its block in colour stands out by its line
        listOf(rain, sun).filter { dist(it.frame, seen(it.fill)) < 0.6f }.forEach { assertTrue(it.frameDp >= 2f) }
        assertTrue("sun frame vs its block", dist(sun.frame, seen(sun.fill)) > 0.6f)
    }
}
