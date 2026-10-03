/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DayPartsTableTest.kt
 * The look-back's day parts side by side: every weather, by day and by night, in German and
 * English, on a narrow and a normal phone, at normal and large font – no word broken inside
 * ("Überwiegen|d sonnig"), nothing cut off ("Nachmitt…").
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.remote.DayPart
import dev.nimbus.weather.data.remote.DayPartWeather
import dev.nimbus.weather.ui.main.DayPartsTable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DayPartsTableTest {
    @get:Rule val compose = createComposeRule()

    /** All weathers, by day and by night, six at a time (a full day). */
    private val rounds: List<List<DayPartWeather>> = Condition.entries.flatMap { c -> listOf(c to true, c to false) }
        .chunked(DayPart.entries.size)
        .map { chunk -> chunk.mapIndexed { i, (c, day) -> DayPartWeather(DayPart.entries[i], c, day, null) } }

    private fun checkAll(what: String) {
        var parts by mutableStateOf(rounds.first())
        var width by mutableFloatStateOf(360f)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                // the page's side margins: 16 dp each
                Box(Modifier.width(width.dp).padding(horizontal = 16.dp)) { DayPartsTable(parts, 0, TextStyle()) }
            }
        }
        val failures = ArrayList<String>()
        for (w in listOf(360f, 411f)) for (scale in listOf(1f, 1.3f)) for (round in rounds) {
            width = w; fontScale = scale; parts = round
            compose.waitForIdle()
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            for (node in nodes) {
                val results = ArrayList<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val layout = results.firstOrNull() ?: continue
                val text = layout.layoutInput.text.text
                // cut off: text beyond the last visible line, a line ellipsized, or a line wider than its column
                val maxWidth = layout.layoutInput.constraints.maxWidth
                val tooWide = (0 until layout.lineCount).any { layout.getLineRight(it) - layout.getLineLeft(it) > maxWidth + 0.5f }
                // the visible lines hold the whole text (maxLines did not cut it)
                val shown = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                if (shown < text.trimEnd().length || (0 until layout.lineCount).any { layout.isLineEllipsized(it) } || tooWide) {
                    failures += "$what ${w.toInt()} dp ×$scale: \"$text\" cut off"
                }
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    if (end in 1 until text.length && text[end - 1] != ' ') {
                        failures += "$what ${w.toInt()} dp ×$scale: \"$text\" broken inside a word (\"${text.substring(0, end)}|${text.substring(end)}\")"
                    }
                }
            }
        }
        assertTrue(failures.distinct().joinToString("\n"), failures.isEmpty())
    }

    /**
     * "Böen 3 km/h" in ever narrower space, as Android breaks lines: never "km/" | "h" (seen in
     * the wind tile on a narrow phone) – the unit strings carry a word joiner after the slash.
     */
    @Test @Config(qualifiers = "de") fun unitsDoNotBreak() {
        var width by mutableFloatStateOf(120f)
        var text by mutableStateOf("")
        compose.setContent { Box(Modifier.width(width.dp)) { androidx.compose.material3.Text(text) } }
        val failures = ArrayList<String>()
        for (unit in listOf(R.string.unit_kmh, R.string.unit_ms)) {
            val u = org.robolectric.RuntimeEnvironment.getApplication().getString(unit)
            for (w in 50..120 step 2) {
                width = w.toFloat(); text = "aus NO · Böen 13\u00A0$u"
                compose.waitForIdle()
                val node = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().first()
                val results = ArrayList<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val layout = results.first()
                val at = text.indexOf(u)
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    if (end in at + 1 until at + u.length) failures += "$w dp: \"${text.substring(0, end)}|${text.substring(end)}\""
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The radar's time slider reaches the screen's edges: dragging it there must not start the back gesture. */
    @Test fun radarSliderIsNotABackGesture() {
        lateinit var view: android.view.View
        val frames = (0..24).map { dev.nimbus.weather.ui.radar.RadarFrame(it * 300_000L, false, null, null) }
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            Box(Modifier.width(400.dp)) { dev.nimbus.weather.ui.radar.TimelineSlider(dev.nimbus.weather.ui.radar.RadarTimeline(frames, 24, ""), 12) {} }
        }
        compose.waitForIdle()
        val rects = view.systemGestureExclusionRects
        assertTrue("no gesture exclusion: $rects", rects.isNotEmpty() && rects.any { it.width() > 0 && it.height() > 0 })
    }

    /** A label in full where it fits, its short form where not – one line, nothing cut off. */
    @Test @Config(qualifiers = "de") fun fitTextAbbreviatesInsteadOfCutting() {
        var width by mutableFloatStateOf(300f)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                Box(Modifier.width(width.dp)) { dev.nimbus.weather.ui.components.FitText("Sonnenuntergang", "Untergang", style = TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp))) }
            }
        }
        fun shown(): String {
            compose.waitForIdle()
            return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
                .first().config[SemanticsProperties.Text].joinToString()
        }
        assertTrue(shown() == "Sonnenuntergang")
        width = 70f
        assertTrue(shown() == "Untergang")
        width = 300f; fontScale = 2f
        assertTrue(shown() == "Sonnenuntergang")
        width = 110f
        assertTrue("at 200 % in 110 dp: ${shown()}", shown() == "Untergang")
    }

    @Test @Config(qualifiers = "de") fun germanWordsStayWhole() = checkAll("de")

    /**
     * Every cell readable, not only the one the sky shows: the others were dimmed to 55 % and
     * vanished on a bright sky. On a light ground (a foggy day sky) the text of a cell not shown
     * keeps its white letters.
     */
    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi") fun cellsNotShownStayReadable() {
        val day = DayPart.entries.map { DayPartWeather(it, Condition.CLOUDY, true, null) }
        compose.setContent {
            Box(Modifier.width(411.dp).background(androidx.compose.ui.graphics.Color(0xFF8E99A6)).padding(16.dp)) { DayPartsTable(day, 0, TextStyle()) }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        // the right half of the upper row: cells 2 and 3, not shown – white letters there
        var white = 0
        for (y in 0 until img.height / 2) for (x in img.width / 2 until img.width) {
            val c = img.getPixel(x, y)
            if ((c shr 16 and 255) > 235 && (c shr 8 and 255) > 235 && (c and 255) > 235) white++
        }
        assertTrue("no white letters in the cells not shown ($white px)", white > 200)
    }

    /** The table as it looks: a phone, German, the day of the screenshot (yesterday, 2 Oct.). */
    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi") fun looksLikeThis() {
        val day = listOf(
            Condition.RAIN to false, Condition.RAIN to true, Condition.DRIZZLE to true,
            Condition.MOSTLY_CLEAR to true, Condition.CLOUDY to false, Condition.FOG to false,
        ).mapIndexed { i, (c, d) -> DayPartWeather(DayPart.entries[i], c, d, null) }
        compose.setContent {
            Box(Modifier.width(411.dp).background(androidx.compose.ui.graphics.Color(0xFF14213A)).padding(16.dp)) { DayPartsTable(day, 3, TextStyle()) }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day_parts.png")
    }

    @Test @Config(qualifiers = "en") fun englishWordsStayWhole() = checkAll("en")
}
