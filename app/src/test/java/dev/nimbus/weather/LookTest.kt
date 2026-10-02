/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LookTest.kt
 * The day charts as they look: rendered from fixed data, compared with reference images and
 * checked pixel by pixel – the cursor in the middle of a bar, the forecast frames to be seen.
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.main.HourCompare
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
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain application: the charts need none of the app's services (MapLibre has no JVM build)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class LookTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val tf = TimeFormat("UTC", true)

    /** A day 00:00 … 01:00 the next day (the 24 column), rain every hour, warm in the afternoon. */
    private fun forecastDay() = (0..25).map { k ->
        MeteoPoint(
            day + k * h, 12.0 + 6 * sin((k - 8) / 24.0 * 2 * Math.PI), Condition.RAIN, k in 7..19,
            0.2 + (k % 5) * 0.25, precipitationChance = 60.0, windSpeed = 12.0, windDirection = 250.0, sunshine = 20.0,
        )
    }

    /** The same day in the look-back: measured amounts, the forecast beside them. */
    private fun lookBackDay() = forecastDay().map { p ->
        p.copy(
            precipitation = p.precipitation!! * if ((p.time / h) % 2 == 0L) 1.6 else 0.5,
            forecastPrecipitation = p.precipitation, forecastTemperature = p.temperature + 1.0,
        )
    }

    @Composable
    private fun Card(content: @Composable () -> Unit) = CompositionLocalProvider(
        LocalSettings provides Settings(), LocalTimeFormat provides tf,
    ) {
        Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { content() }
    }

    /**
     * Long press at [fx] of the width, [fy] of the height, once the chart is drawn; the cursor
     * stays (no 10 s fade-out in between). Returns the x of the press (px).
     */
    private fun pressAt(fx: Float, fy: Float): Float {
        compose.waitForIdle()
        // Robolectric draws only when an image is taken – and the charts learn their plot edges
        // (for touch) while drawing, as on the phone before any finger can reach them
        bitmap()
        var x = 0f
        compose.onRoot().performTouchInput { x = width * fx; longClick(Offset(x, height * fy)) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        return x
    }

    private fun bitmap(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    // ---- pixel checks ---------------------------------------------------------------------

    private fun Bitmap.rgb(x: Int, y: Int): Triple<Int, Int, Int> = getPixel(x, y).let { Triple((it shr 16) and 255, (it shr 8) and 255, it and 255) }

    /** The cursor: the column with the longest run of near-white pixels. */
    private fun Bitmap.cursorX(): Int = (0 until width).maxBy { x ->
        var best = 0; var run = 0
        for (y in 0 until height) {
            val (r, g, b) = rgb(x, y)
            if (r > 215 && g > 215 && b > 215) { run++; best = maxOf(best, run) } else run = 0
        }
        best
    }

    /** Horizontal runs of bar colour (light blue) in the row [y]: (first, last) x. */
    private fun Bitmap.barRuns(y: Int): List<IntRange> {
        val out = ArrayList<IntRange>(); var s = -1
        for (x in 0 until width) {
            val (r, g, b) = rgb(x, y)
            val bar = b > 150 && b - r > 40 && g > 110
            if (bar && s < 0) s = x
            if (!bar && s >= 0) { out += s until x; s = -1 }
        }
        return out.filter { it.last - it.first >= 4 }
    }

    /** The row with the most bar pixels (just above the axis). */
    private fun Bitmap.barRow(): Int = (0 until height).maxBy { y -> barRuns(y).sumOf { it.last - it.first } }

    private fun assertCursorCentredOnABar(img: Bitmap, what: String, pressedX: Float) {
        val cx = img.cursorX()
        // the cursor goes to the bar under the finger
        assertTrue("$what: pressed at x=$pressedX, cursor at $cx", abs(cx - pressedX) <= img.width / 25f)
        val row = img.barRow()
        // the cursor line itself splits its bar: join runs separated by the line
        val runs = img.barRuns(row).fold(mutableListOf<IntRange>()) { acc, r ->
            val last = acc.lastOrNull()
            if (last != null && r.first - last.last <= 6 && cx in last.last..r.first) acc[acc.lastIndex] = last.first..r.last else acc += r
            acc
        }
        val bar = runs.firstOrNull { cx in it }
        assertTrue("$what: cursor at x=$cx not on a bar (bars: $runs, row $row)", bar != null)
        val centre = (bar!!.first + bar.last) / 2f
        assertTrue("$what: cursor x=$cx, bar centre $centre", abs(cx - centre) <= 2f)
    }

    // ---- the charts -------------------------------------------------------------------------

    @Test fun meteogramCursorStandsInTheMiddleOfItsBar() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day + 15 * h) } }
        val x = pressAt(0.37f, 0.35f)
        assertCursorCentredOnABar(bitmap(), "meteogram", x)
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_cursor.png")
    }

    @Test fun lookBackForecastFramesAreSeen() {
        compose.setContent { Card { Meteogram(lookBackDay(), day, day + 24 * h, emptyList(), day + 30 * h) } }
        compose.waitForIdle()
        val img = bitmap()
        // amber frame pixels (the forecast) – in every hour that has one
        val amber = (0 until img.width).count { x ->
            (0 until img.height).any { y -> val (r, g, b) = img.rgb(x, y); r > 220 && g in 150..200 && b < 110 }
        }
        assertTrue("forecast frames not visible ($amber columns)", amber > 100)
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_lookback.png")
    }

    /** Rows where at least [share] of the width is near-white: the chance line (60 % all day). */
    private fun Bitmap.whiteRows(share: Float = 0.5f): List<Int> = (0 until height).filter { y ->
        (0 until width).count { x -> val (r, g, b) = rgb(x, y); r > 215 && g > 215 && b > 215 } > width * share
    }

    @Test fun separatePrecipitationChart() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day + 15 * h, separatePrecip = true) } }
        compose.waitForIdle()
        val plain = bitmap()
        // the chance as a white line across the precipitation chart, below the temperature
        val chance = plain.whiteRows()
        assertTrue("chance line not found", chance.isNotEmpty())
        val x = pressAt(0.37f, 0.45f)
        assertCursorCentredOnABar(bitmap(), "separate precipitation", x)
        // the bars stand in the chart below the temperature: under the chance line's top
        assertTrue("bars below the temperature chart", bitmap().barRow() > chance.first())
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_separate.png")
    }

    @Test fun combinedChartHasNoChanceLine() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day + 30 * h) } }
        compose.waitForIdle()
        assertTrue("a chance line in the combined chart", bitmap().whiteRows().isEmpty())
    }

    // ---- the readout while the finger slides --------------------------------------------------

    /** A day whose values change in width from hour to hour: names, signs, digits, missing gusts. */
    private fun restlessDay() = (0..25).map { k ->
        val c = Condition.entries[k % Condition.entries.size]
        MeteoPoint(
            day + k * h, -14.0 + k * 1.7, c, k in 7..19, if (k % 3 == 0) 0.0 else k * 0.53,
            precipitationChance = (k * 37 % 101).toDouble(), windSpeed = (k * 9 % 140).toDouble(), windDirection = k * 45.0,
            windGust = if (k % 4 == 0) null else (k * 11 % 160).toDouble(), humidity = (k * 7 % 101).toDouble(),
            apparentTemperature = -20.0 + k * 2.1, sunshine = (k * 7 % 61).toDouble(),
        )
    }

    private fun restlessLookBack() = restlessDay().map { p ->
        p.copy(compare = HourCompare(
            p.temperature, p.temperature + 3, p.precipitation, if (p.time / h % 2 == 0L) null else 4.4, p.precipitationChance,
            p.windSpeed, p.windDirection, 120.0, p.windGust, null, p.sunshine, 60.0,
        ))
    }

    /**
     * Slides the finger over every hour; the card's height, the hint and legend below the readout
     * and the readout's columns must not move (they "fluttered" when a value's width decided the layout).
     */
    private fun assertSteadyWhileSliding(points: List<MeteoPoint>, separate: Boolean, columnLabel: String) {
        // font sizes from normal to the largest system setting: somewhere two pairs per row stop fitting
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                Card { Meteogram(points, day, day + 24 * h, emptyList(), day + 30 * h, separatePrecip = separate) }
            }
        }
        for (scale in listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            compose.mainClock.autoAdvance = true
            fontScale = scale
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(11_000)                       // the cursor of the last round fades out
            slide(separate, columnLabel, scale)
        }
    }

    private fun slide(separate: Boolean, columnLabel: String, scale: Float) {
        bitmap()
        val hint = ctx().getString(R.string.meteogram_hint)
        fun probe(): Triple<Int, Float, Float> = Triple(
            compose.onRoot().fetchSemanticsNode().size.height,
            compose.onNodeWithText(hint).fetchSemanticsNode().positionInRoot.y,
            compose.onNodeWithText(columnLabel).fetchSemanticsNode().positionInRoot.x,
        )
        val root = compose.onRoot().fetchSemanticsNode().size
        val y = root.height * 0.3f
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { down(Offset(root.width * 0.1f, y)) }
        compose.mainClock.advanceTimeBy(800)                              // the long press
        val seen = (0..60).map { i ->
            compose.onRoot().performTouchInput { moveTo(Offset(root.width * (0.1f + 0.8f * i / 60), y)) }
            compose.mainClock.advanceTimeBy(32)
            probe()
        }
        compose.onRoot().performTouchInput { up() }
        val first = seen.first()
        seen.forEachIndexed { i, p -> assertEquals("font $scale, step $i (separate=$separate): height, hint y, column x", first, p) }
    }

    @Test fun forecastReadoutSteady() = assertSteadyWhileSliding(restlessDay(), false, ctx().getString(R.string.readout_feels))
    @Test fun forecastReadoutSteadySeparate() = assertSteadyWhileSliding(restlessDay(), true, ctx().getString(R.string.readout_feels))
    @Test fun lookBackReadoutSteady() = assertSteadyWhileSliding(restlessLookBack(), false, ctx().getString(R.string.forecast))
    @Test fun lookBackReadoutSteadySeparate() = assertSteadyWhileSliding(restlessLookBack(), true, ctx().getString(R.string.forecast))

    private fun ctx(): android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()

    /** Garbsen, the test day (UTC): night until about 05:20, from about 17:00, and in the 24 column. */
    private val garbsenNights = dev.nimbus.weather.ui.main.nights(day, day + 25 * h, 52.42, 9.60)

    @Test fun nightShadingCoversThe00And24Columns() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, garbsenNights, day + 30 * h) } }
        compose.waitForIdle()
        val img = bitmap()
        fun lum(x: Int, y: Int) = img.rgb(x, y).let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
        // the upper part of the plot (above the bars): median brightness of each column
        val rows = (img.height * 15 / 100 until img.height * 24 / 100).toList()
        val med = DoubleArray(img.width) { x -> rows.map { lum(x, it) }.sorted()[rows.size / 2] }
        val card = lum(1, 1)
        val inPlot = (0 until img.width).filter { abs(med[it] - card) > 2.5 }
        val l = inPlot.first(); val r = inPlot.last(); val w = r - l
        fun at(f: Double) = med[(l + w * f).toInt()]
        val n00 = at(0.01); val n24 = at(0.99); val noon = at(12.75 / 25)
        assertTrue("00 column ($n00) darker than noon ($noon)", n00 < noon - 8)
        assertTrue("24 column ($n24) as dark as the 00 column ($n00)", abs(n24 - n00) < 3 && n24 < noon - 8)
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_nights.png")
    }
}
