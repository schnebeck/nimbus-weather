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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
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
import dev.nimbus.weather.ui.main.CurvePoint
import dev.nimbus.weather.ui.main.DayOverview
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
            precipMeasured = true, sunMeasured = true, forecastSunshine = 30.0,
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

    /**
     * Horizontal runs of bar colour in the row [y]: (first, last) x. A light blue block or the
     * violet frame (#9B4DFF, close: the curve's smoothed edges come near other colours).
     */
    private fun Bitmap.barRuns(y: Int): List<IntRange> {
        val out = ArrayList<IntRange>(); var s = -1
        for (x in 0 until width) {
            val (r, g, b) = rgb(x, y)
            val bar = (b > 150 && b - r > 40 && g > 110) || (abs(r - 155) <= 8 && abs(g - 77) <= 8 && abs(b - 255) <= 8)
            if (bar && s < 0) s = x
            if (!bar && s >= 0) { out += s until x; s = -1 }
        }
        // no wider than an hour's column: not a flat stretch of the temperature curve
        return out.filter { it.last - it.first in 4 until width / 25 }
    }

    /**
     * The row of the bars' feet: bars stand on the axis, so the lowest row of the plot with bar
     * colour – below the time labels and weather symbols (card padding 12 dp, 14 + 30 dp), above
     * the sunshine row (the plot 120 dp, [separate]: the precipitation chart 30 + 76 dp more); of
     * the few rows there the widest, past the rounded corners. (The row with the most bar pixels was
     * the curve's or the symbols' as soon as the bars were frames.)
     */
    private fun Bitmap.barRow(separate: Boolean = false): Int {
        val foot = ((12 + 14 + 30) * 3 until (12 + 14 + 30 + 120 + if (separate) 106 else 0) * 3).reversed().first { y -> barRuns(y).isNotEmpty() }
        return (foot - 8..foot).maxBy { y -> barRuns(y).sumOf { it.last - it.first } }
    }

    private fun assertCursorCentredOnABar(img: Bitmap, what: String, pressedX: Float, separate: Boolean = false) {
        val cx = img.cursorX()
        // the cursor goes to the bar under the finger
        assertTrue("$what: pressed at x=$pressedX, cursor at $cx", abs(cx - pressedX) <= img.width / 25f)
        val row = img.barRow(separate)
        // the cursor line itself splits its bar: join runs separated by the line
        val runs = img.barRuns(row).fold(mutableListOf<IntRange>()) { acc, r ->
            val last = acc.lastOrNull()
            if (last != null && r.first - last.last <= 8 && cx in last.last..r.first) acc[acc.lastIndex] = last.first..r.last else acc += r
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
        // violet frame pixels (the forecast of precipitation) – in every hour that has one
        val violet = (0 until img.width).count { x ->
            (0 until img.height).any { y -> val (r, g, b) = img.rgb(x, y); abs(r - 155) <= 8 && abs(g - 77) <= 8 && abs(b - 255) <= 8 }
        }
        assertTrue("forecast frames not visible ($violet columns)", violet > 100)
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_lookback.png")
    }

    /**
     * The "now" line meets the temperature curve at the temperature of now: line and curve points
     * stand at their time on the labels' clock. 10° until 12:00, 20° from 13:00: at the line
     * (12:00) the curve is on the 10° level. [fine]: 15-minute points, 10° until 11:30, 20° from
     * 11:45 – at 12:00 on 20°.
     */
    private fun nowLineMeetsTheCurve(fine: Boolean) {
        val q = 15 * 60_000L
        val step = (0..25).map { k ->
            MeteoPoint(day + k * h, if (k <= 12) 10.0 else 20.0, Condition.CLOUDY, k in 7..19, 0.0, windSpeed = 10.0, windDirection = 200.0)
        }
        val curve = if (!fine) null else (0..100).map { i -> CurvePoint(day + i * q, if (i <= 46) 10.0 else 20.0, q) }
        compose.setContent { Card { Meteogram(step, day, day + 24 * h, emptyList(), day + 12 * h, showNow = true, curve = curve) } }
        compose.waitForIdle()
        val img = bitmap()
        fun vivid(x: Int, y: Int) = img.rgb(x, y).let { (r, g, b) -> maxOf(r, g, b) - minOf(r, g, b) > 70 }
        val plot = 0 until img.height * 60 / 100
        // the curve's height in a column (mean of its pixels)
        fun curveY(x: Int): Double? = plot.filter { vivid(x, it) }.takeIf { it.isNotEmpty() }?.average()
        val nowX = img.nowX()
        val low = curveY(img.width / 5)!!; val high = curveY(img.width * 17 / 20)!!
        val atNow = (nowX - 3..nowX + 3).mapNotNull { curveY(it) }.average()
        val want = if (fine) high else low
        assertTrue("fine=$fine: curve at now ($atNow) should be on ${if (fine) 20 else 10}° ($want; 10° $low, 20° $high)", abs(atNow - want) < abs(high - low) * 0.15)
    }

    @Test fun nowLineMeetsTheHourlyCurveAtNow() = nowLineMeetsTheCurve(fine = false)

    @Test fun nowLineMeetsTheFineCurveAtNow() = nowLineMeetsTheCurve(fine = true)

    /** The "now" line: the column of the plot with the most light grey pixels. */
    private fun Bitmap.nowX(): Int {
        val plot = 0 until height * 60 / 100
        return (width / 10 until width * 9 / 10).maxBy { x ->
            plot.count { y -> rgb(x, y).let { (r, g, b) -> r > 170 && g > 170 && b > 170 && maxOf(r, g, b) - minOf(r, g, b) < 30 } }
        }
    }

    /**
     * "Die Stundenzeitanzeige (oben, 00.00 - 24.00) und der Slider stehen auf der vollen Stunden …
     * Also sollte die aktuelle Zeitanzeige auch zur Uhrzeit oben und zum Slider passen": the bar of
     * 12–13 stands under the 12 (11:30–12:30 on the clock), the slider of that hour on its middle –
     * at 12:00 the "now" line stands there too (it stood an hour early, on the bar of 11–12), at
     * 12:30 half way to the next bar.
     */
    @Test fun nowLineStandsOnTheClock() {
        var shown by mutableStateOf(false)
        var now by mutableLongStateOf(day + 12 * h)
        // rain in the hour 12–13 only: its bar (time stamp 13:00) is the only one
        val points = forecastDay().mapIndexed { k, p -> p.copy(precipitation = if (k == 13) 1.0 else 0.0) }
        compose.setContent { Card { Meteogram(points, day, day + 24 * h, emptyList(), now, showNow = shown) } }
        compose.waitForIdle()
        // (narrower than a column: not the legend's swatch)
        val runs = bitmap().let { img -> img.barRuns(img.barRow()) }
        val bar = runs.singleOrNull() ?: throw AssertionError("bars $runs")
        val centre = (bar.first + bar.last) / 2f
        val column = (bar.last - bar.first + 1) / dev.nimbus.weather.ui.main.HourAxis.BAR_SHARE
        shown = true
        compose.waitForIdle()
        val at12 = bitmap().nowX()
        assertTrue("12:00 at x=$at12, the bar of 12–13 at $centre", abs(at12 - centre) <= 3f)
        now = day + 12 * h + 30 * 60_000L
        compose.waitForIdle()
        val at1230 = bitmap().nowX()
        assertTrue("12:30 at x=$at1230, half a column right of the bar of 12–13 at $centre", abs(at1230 - (centre + column / 2)) <= 3f)
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
        assertCursorCentredOnABar(bitmap(), "separate precipitation", x, separate = true)
        // the bars stand in the chart below the temperature: under the chance line's top
        assertTrue("bars below the temperature chart", bitmap().barRow(separate = true) > chance.first())
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_separate.png")
    }

    /**
     * „Wenn ich in der 10-Tage-vorhersage nicht heute sondern morgen (oder einen anderen zukünfigen
     * Tag) auswähle, dann wird mir eine Stundenvorhersage 11-12.00 Uhr angezeigt, aber heine
     * Tagesvorhersage?!“ – a day to come shows its whole day until the cursor is set, the hour's
     * values while it stands, the day again once it has faded; the card keeps its height.
     */
    @Test fun aDayToComeShowsTheWholeDayUntilTheCursor() {
        val overview = DayOverview(Condition.RAIN, high = 18.0, low = 6.0, precipitation = 9.4, chance = 60.0, wind = 12.0, windDirection = 250.0, gust = 30.0, sunMinutes = 260.0, uv = 3.0)
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day - 30 * h, dayOverview = overview) } }
        compose.waitForIdle()
        val whole = ctx().getString(R.string.readout_whole_day)
        val noon = tf.time(day + 11 * h) + "–" + tf.time(day + 12 * h)
        compose.onNodeWithText(whole).assertExists()
        compose.onNodeWithText(ctx().getString(R.string.readout_high)).assertExists()
        compose.onNodeWithText(noon).assertDoesNotExist()
        val height = bitmap().height
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_whole_day.png")
        pressAt(0.37f, 0.35f)
        compose.onNodeWithText(whole).assertDoesNotExist()
        compose.onNodeWithText(ctx().getString(R.string.readout_feels)).assertExists()
        assertEquals("the card's height with the cursor", height, bitmap().height)
        // the cursor fades out: the whole day again
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithText(whole).assertExists()
        assertEquals("the card's height after the cursor", height, bitmap().height)
    }

    /** Today and the look-back: the hour as before (no day overview given). */
    @Test fun withoutADayOverviewTheHour() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day - 30 * h) } }
        compose.waitForIdle()
        compose.onNodeWithText(ctx().getString(R.string.readout_whole_day)).assertDoesNotExist()
        compose.onNodeWithText(tf.time(day + 11 * h) + "–" + tf.time(day + 12 * h)).assertExists()
    }

    @Test fun combinedChartHasNoChanceLine() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, emptyList(), day + 30 * h) } }
        compose.waitForIdle()
        assertTrue("a chance line in the combined chart", bitmap().whiteRows().isEmpty())
    }

    /**
     * A card slid under the header is cut off at the top only: the charts reach into the card's
     * side padding, their axis labels were cut off at the sides while pinned.
     */
    @Test fun pinnedCardKeepsWhatReachesIntoItsPadding() {
        compose.setContent {
            CompositionLocalProvider(dev.nimbus.weather.ui.components.LocalPinLine provides { 150f }) {
                Box(Modifier.width(300.dp).background(Color.Black)) {
                    dev.nimbus.weather.ui.components.GlassCard(title = "Pinned") {
                        // like the charts: 10 dp into the 14 dp padding on the left
                        Box(Modifier.offset(x = (-10).dp).size(40.dp, 400.dp).background(Color.Red))
                    }
                }
            }
        }
        compose.waitForIdle()
        val img = bitmap()
        val px = img.width / 300f                                         // px per dp
        val y = (img.height * 0.8f).toInt()                                // well below the pin line
        val (r, g, b) = img.rgb((8 * px).toInt(), y)                     // 8 dp: inside the card, in the padding
        assertTrue("bleed cut off while pinned: ($r, $g, $b)", r > 150 && g < 80 && b < 80)
    }

    /** Three days of weather for the 10-day card: rain in the morning, warm afternoons. */
    private fun cardData(): dev.nimbus.weather.data.model.WeatherData {
        val hourly = (0..72).map { k ->
            dev.nimbus.weather.data.model.HourlyPoint(
                day + k * h, 12.0 + 6 * sin((k % 24 - 8) / 24.0 * 2 * Math.PI), condition = if (k % 24 < 9) Condition.RAIN else Condition.PARTLY_CLOUDY,
                isDay = k % 24 in 7..18, precipitation = if (k % 24 < 9) 0.6 else 0.0, precipitationProbability = 60.0,
                windSpeed = 12.0, windDirection = 250.0, sunshine = if (k % 24 in 10..16) 40.0 else 0.0,
            )
        }
        val daily = (0..2).map { dev.nimbus.weather.data.model.DailyPoint(day + it * 24 * h, Condition.RAIN, 18.0, 8.0, precipitationSum = 5.4, precipitationProbability = 60.0) }
        val cur = dev.nimbus.weather.data.model.CurrentWeather(day + 10 * h, 15.0, 14.0, Condition.CLOUDY, true, 60.0, 8.0, 1020.0, 10.0, 20.0, 240.0, 80.0, 30000.0, 2.0, 0.0)
        return dev.nimbus.weather.data.model.WeatherData(
            dev.nimbus.weather.data.model.Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), "UTC", 0, cur, hourly, daily,
            sources = emptyList(), fetchedAt = day + 10 * h,
        )
    }

    /**
     * Opening a day of the 10-day card: the chart unfolds below its row – nothing of it is drawn
     * over the rows above, at no moment of the animation (it grew from the bottom, over them).
     */
    @Test fun dayChartUnfoldsBelowItsRow() {
        compose.setContent { Card { dev.nimbus.weather.ui.main.DailyCard(cardData(), day + 10 * h) } }
        compose.waitForIdle()
        // today is open: close it, then open tomorrow
        val today = ctx().getString(R.string.today)
        // the label's node is the whole clickable day (row and open chart): click its row
        compose.onNodeWithText(today).performTouchInput { click(Offset(width / 2f, 25.dp.toPx())) }
        compose.waitForIdle()
        val before = bitmap()
        val tomorrow = TimeFormat("UTC", true).weekdayShort(day + 24 * h)
        val row = compose.onNodeWithText(tomorrow).fetchSemanticsNode().boundsInRoot
        // the rows above the clicked one (its own ripple may change)
        val rowTop = row.top.toInt()
        // opening, then closing again: every frame of both animations
        fun toggleAndWatch(what: String) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(tomorrow).performTouchInput { click(Offset(width / 2f, 25.dp.toPx())) }
            for (k in 1..12) {
                compose.mainClock.advanceTimeBy(32)
                val img = bitmap()
                val changed = (0 until rowTop - 2).count { y ->
                    (0 until img.width step 3).any { x -> img.getPixel(x, y) != before.getPixel(x, y) }
                }
                assertEquals("$what, frame ${k * 32} ms: rows above the day changed (chart drawn over them)", 0, changed)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        toggleAndWatch("opening")
        // the open chart keeps its axis labels in the card's padding (the clip does not cut them)
        compose.onRoot().captureRoboImage("src/test/screenshots/daily_open.png")
        toggleAndWatch("closing")
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
    @Test fun lookBackReadoutSteady() = assertSteadyWhileSliding(restlessLookBack(), false, ctx().getString(R.string.readout_expected))
    @Test fun lookBackReadoutSteadySeparate() = assertSteadyWhileSliding(restlessLookBack(), true, ctx().getString(R.string.readout_expected))

    private fun ctx(): android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()

    /** Hannover, the test day (UTC): night until about 05:20, from about 17:00, and in the 24 column. */
    private val hannoverNights = dev.nimbus.weather.ui.main.nights(day, day + 25 * h, 52.3759, 9.7320)

    @Test fun nightShadingCoversThe00And24Columns() {
        compose.setContent { Card { Meteogram(forecastDay(), day, day + 24 * h, hannoverNights, day + 30 * h) } }
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
