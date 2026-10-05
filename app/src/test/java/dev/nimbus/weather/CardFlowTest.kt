/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CardFlowTest.kt
 * "Im Landscape-mode fließen die Kacheln nicht so richtig in die Lücken": in columns the cards
 * fill whichever column is shorter – no column empty beside a long card while cards follow.
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.main.LocalContentWidth
import dev.nimbus.weather.ui.main.WeatherPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w914dp-h411dp-xxhdpi", application = android.app.Application::class)
class CardFlowTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = System.currentTimeMillis() / h * h + 20 * 60_000L
    /** The cards over all columns: the sky above, the alerts, the offline note, the sources. */
    private val wide = setOf("header-space", "alerts", "offline", "sources")

    private fun data(): WeatherData {
        val start = now / h * h - 2 * h
        val hourly = (0..72).map { k -> HourlyPoint(start + k * h, 14.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0, precipitationProbability = 10.0) }
        val daily = (-1..9).map { DailyPoint(start + it * 24 * h, Condition.CLOUDY, 20.0, 9.0, precipitationSum = 0.0) }
        val cur = CurrentWeather(now, 11.0, 13.0, Condition.CLOUDY, true, 97.0, 10.0, 1032.0, 3.0, 6.0, 40.0, 100.0, 12000.0, 0.0, 0.0)
        return WeatherData(Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), "Europe/Berlin", 7200, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    /** Every card of the page with its place (a page taller than the window: all of them composed). */
    private fun cards(widthDp: Int): List<Pair<String, Rect>> {
        compose.setContent {
            CompositionLocalProvider(LocalContentWidth provides widthDp.dp) {
                Box(Modifier.requiredSize(widthDp.dp, 8000.dp)) {
                    WeatherPage(
                        Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), PlaceState(data()),
                        Settings(hiddenCards = setOf(WeatherCard.RADAR, WeatherCard.MODELS)), null, false, {}, {}, {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val tagged = SemanticsMatcher("a card") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("card-") == true }
        return compose.onAllNodes(tagged, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix("card-") to it.boundsInRoot }
            .filter { it.second.height > 0f }
    }

    /**
     * A phone sideways, today's day open in the 10-day card (long): each column runs without a gap
     * from the first card to its last, and the shorter one ends less than a card above the other –
     * the next card would have gone into it.
     */
    @Test fun inColumnsTheCardsFillTheShorterOne() {
        val all = cards(914)
        val px = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.density
        // a card over the columns closes them like a line: only the sky, the alerts, the offline
        // note and the sources may (told by their width, not their name)
        val laneWidth = all.minOf { it.second.width }
        val spanning = all.filter { it.second.width > laneWidth * 1.5f }
        assertTrue("over all columns: ${spanning.map { it.first }}", spanning.all { it.first in wide })
        val narrow = all - spanning.toSet()
        val lanes = narrow.groupBy { (it.second.left / 10).toInt() }.values.map { lane -> lane.sortedBy { it.second.top } }
        assertEquals("columns: ${lanes.map { l -> l.map { it.first } }}", 2, lanes.size)
        // both begin at the same height
        assertEquals("first cards: ${lanes.map { it.first().first }}", lanes[0].first().second.top / px, lanes[1].first().second.top / px, 2f)
        val daily = narrow.single { it.first == "daily" }.second
        assertTrue("today's day not open (${daily.height / px} dp)", daily.height / px > 350)
        for (lane in lanes) lane.zipWithNext().forEach { (a, b) ->
            val gap = (b.second.top - a.second.bottom) / px
            assertTrue("gap of $gap dp between ${a.first} and ${b.first} in ${lanes.map { l -> l.map { it.first } }}", gap <= 15f)
        }
        val (short, long) = lanes.sortedBy { it.last().second.bottom }
        val behind = (long.last().second.bottom - short.last().second.bottom) / px
        assertTrue("the shorter column ends $behind dp above the other – more than its last card (${long.last().first})", behind <= long.last().second.height / px + 15f)
        assertTrue("the small tiles not in the columns: ${narrow.map { it.first }}", narrow.count { it.first.startsWith("tile-") } >= 4)
    }

    /** One column (phone upright): the small tiles two side by side in one card, as before. */
    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi") fun inOneColumnTheTilesTogether() {
        val keys = cards(411).map { it.first }
        assertTrue(keys.toString(), "tiles" in keys && keys.none { it.startsWith("tile-") })
    }
}
