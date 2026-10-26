/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PastDayTableTest.kt
 * A day over in the look-back: its hour table only while the cursor stands on the chart – without
 * it the table stood on midday and read as the day's figures.
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.ui.main.DailyCard
import dev.nimbus.weather.ui.main.DayCourseCard
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class PastDayTableTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_791_676_800_000L                     // 11 Oct 2026 00:00 UTC
    private val tf = TimeFormat("UTC", true)

    private fun history(now: Long): History {
        val hours = (0..25).map { k ->
            val t = day + k * h
            HistoryHour(
                t, HistoryHour.Measured(10.0 + k % 7, 0.0, 5.0, 12.0, 200.0, 10.0, 60.0, Condition.CLOUDY),
                HistoryHour.Modelled(10.0 + k % 6, 0.0, 5.0, 12.0, 10.0, Condition.CLOUDY, k in 8..18, chance = 10.0),
            )
        }
        val day0 = HistoryDay(LocalDate.of(2026, 10, 11), hours.filter { it.time > day && it.time <= day + 24 * h })
        return History(listOf(day0), "Hannover", 10.5, "icon_seamless", ZoneOffset.UTC, now, allHours = hours)
    }

    private fun hourTitles() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }.filter { Regex("""\d\d:00–\d\d:00""").matches(it) }

    private fun show(now: Long) = show { now }

    /** The card at the time [now] gives – the page's clock goes on while it is shown. */
    private fun show(now: () -> Long) {
        val history = history(now())
        val place = Place("h", "Hannover", latitude = 52.37, longitude = 9.73)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides tf) {
                Column(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp).testTag("card")) {
                    DayCourseCard(history.days[0], DaySummary.of(history.days[0], now()), Settings(), tf, history, place, emptyList(), Condition.CLOUDY, now())
                }
            }
        }
        compose.waitForIdle()
    }

    /** Yesterday untouched: no hour table – held on the chart: the hour under the finger. */
    @Test fun aDayOverShowsAnHourOnlyWhileHeld() {
        show(day + 34 * h)
        assertTrue("untouched: ${hourTitles()}", hourTitles().isEmpty())
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(Offset(card.left + card.width * 0.5f, card.top + 250f)) }
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(1, hourTitles().size)
        compose.onRoot().performTouchInput { up() }
    }

    /** Today the table stays and stands on the hour running now – the line of now marks it. */
    @Test fun todayTheTableFollowsNow() {
        show(day + 14 * h + 20 * 60_000L)
        assertEquals(listOf("14:00–15:00"), hourTitles())
    }

    /**
     * Today the cursor set on the morning: the table shows that hour while the cursor is on – the
     * cursor fading out, it is back on the hour running now, the morning's values gone with it.
     */
    @Test fun todayTheTableGoesBackToNowWithTheCursor() {
        show(day + 14 * h + 20 * 60_000L)
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { longClick(Offset(card.left + card.width * 0.3f, card.top + 250f)) }
        compose.waitForIdle()
        val held = hourTitles()
        assertEquals(1, held.size)
        assertTrue("the cursor's hour, not now's: $held", held[0] != "14:00–15:00")
        compose.mainClock.advanceTimeBy(11_000)
        compose.waitForIdle()
        assertEquals(listOf("14:00–15:00"), hourTitles())
    }

    /** Today the page left open: the table goes on to the next hour with the clock. */
    @Test fun todayTheTableGoesOnWithTheClock() {
        var now by mutableLongStateOf(day + 14 * h + 50 * 60_000L)
        show { now }
        assertEquals(listOf("14:00–15:00"), hourTitles())
        now = day + 15 * h + 10 * 60_000L
        compose.waitForIdle()
        assertEquals(listOf("15:00–16:00"), hourTitles())
    }
}
