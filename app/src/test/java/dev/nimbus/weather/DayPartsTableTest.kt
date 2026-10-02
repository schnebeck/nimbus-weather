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

    @Test @Config(qualifiers = "de") fun germanWordsStayWhole() = checkAll("de")

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
