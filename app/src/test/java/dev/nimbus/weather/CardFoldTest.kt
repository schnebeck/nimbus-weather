/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CardFoldTest.kt
 * Folding the weather page's cards: to the title and one line, kept per card; today in the
 * 10-day forecast opened or closed as the user left it; the pollen card without pollen in the air
 * folded to its summary.
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.DailyCard
import dev.nimbus.weather.ui.main.Foldable
import dev.nimbus.weather.ui.main.GaugeCard
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalSettingsUpdater
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.PollenForecastCard
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class CardFoldTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_791_676_800_000L                     // 11 Oct 2026 00:00 UTC
    private val now = day + 15 * h
    private val tf = TimeFormat("UTC", true)
    private var settings by mutableStateOf(Settings())

    /** [content] on the page's glass, with the settings of this test (changed by the cards as by the app). */
    private fun page(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(
            LocalSettings provides settings, LocalTimeFormat provides tf,
            LocalSettingsUpdater provides { f: (Settings) -> Settings -> settings = f(settings) },
        ) {
            Column(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp).testTag("page")) { content() }
        }
    }

    private val gauge = GaugeInfo("u1", "HERRENHAUSEN", "LEINE", 4.3, tidal = false, level = 123.0, levelTime = day + 14 * h)

    /** The arrow in the title folds the card to its title and one line; a tap unfolds it; the settings keep it. */
    @Test fun foldsToItsTitleAndOneLine() {
        page { Foldable(WeatherCard.GAUGES) { GaugeCard(listOf(gauge), now) } }
        compose.onNodeWithText("Pegel Herrenhausen", substring = true).assertExists()
        compose.onNodeWithContentDescription("Einklappen").performClick()
        compose.waitForIdle()
        assertEquals(setOf(WeatherCard.GAUGES), settings.foldedCards)
        compose.onNodeWithText("Leine · Herrenhausen: 123${NBSP}cm").assertExists()
        compose.onNodeWithText("Pegel Herrenhausen", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("page").captureRoboImage("src/test/screenshots/card_folded.png")
        // a tap on the folded card opens it again
        compose.onNodeWithText("Leine · Herrenhausen: 123${NBSP}cm").performClick()
        compose.waitForIdle()
        assertTrue(settings.foldedCards.isEmpty())
        compose.onNodeWithText("Pegel Herrenhausen", substring = true).assertExists()
    }

    /** Folded in the settings: the card opens folded. */
    @Test fun opensAsItWasLeft() {
        settings = Settings(foldedCards = setOf(WeatherCard.GAUGES))
        page { Foldable(WeatherCard.GAUGES) { GaugeCard(listOf(gauge), now) } }
        compose.onNodeWithText("Leine · Herrenhausen: 123${NBSP}cm").assertExists()
        compose.onNodeWithContentDescription("Aufklappen").assertExists()
    }

    /** A card not on the page's list (no fold given) has no arrow. */
    @Test fun onlyThePagesCardsFold() {
        page { GaugeCard(listOf(gauge), now) }
        compose.onNodeWithContentDescription("Einklappen").assertDoesNotExist()
    }

    private fun pollen(levels: Map<PollenType, Float>) = PollenForecast(
        PollenSourceKind.DWD, "Weser- und Ems-Niederungen",
        (0..2).map { PollenDay(day + it * 24 * h, PollenType.entries.filter { t -> t != PollenType.OLIVE }.associateWith { t -> levels[t] ?: 0f }) },
    )

    /** No pollen in the air: the summary alone – the rows, all empty, once the card is opened. */
    @Test fun withoutPollenTheSummaryAlone() {
        page { PollenForecastCard(pollen(emptyMap()), now) }
        compose.onNodeWithText("Keine Belastung").assertExists()
        compose.onNodeWithText("Hasel").assertDoesNotExist()
        compose.onNodeWithTag("page").captureRoboImage("src/test/screenshots/pollen_quiet.png")
        compose.onNodeWithText("Alle Arten und Verlauf anzeigen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hasel").assertExists()
        compose.onNodeWithText("Beifuß").assertExists()
    }

    /** Pollen flying: its rows as before. */
    @Test fun withPollenItsRows() {
        page { PollenForecastCard(pollen(mapOf(PollenType.HAZEL to 1.5f)), now) }
        compose.onNodeWithText("Hasel").assertExists()
        compose.onNodeWithText("Beifuß").assertDoesNotExist()
    }

    private fun forecast(): WeatherData {
        val hourly = (0..48).map { k -> HourlyPoint(day + k * h, 10.0 + k % 24 / 3.0, condition = Condition.CLOUDY, isDay = k % 24 in 8..18) }
        val daily = (0..1).map { DailyPoint(day + it * 24 * h, Condition.CLOUDY, 18.0, 10.0) }
        val cur = CurrentWeather(now, 15.0, 14.0, Condition.CLOUDY, true, 80.0, 8.0, 1012.0, 3.0, 20.0, 230.0, 90.0, 20000.0, 0.0, 0.0)
        return WeatherData(Place("h", "Hildesheim", latitude = 52.15, longitude = 9.95), "UTC", 0, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    /** A tap on today's row – on its line, not on the chart below it (which takes taps for its cursor). */
    private fun tapToday() {
        compose.onNodeWithText("Heute").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, top + 60f)) }
        compose.waitForIdle()
    }

    /** The 10-day forecast folds to today alone – today's chart still opens; kept like the other cards. */
    @Test fun theTenDaysFoldToToday() {
        val data = forecast()
        page { Foldable(WeatherCard.DAILY) { DailyCard(data, now) } }
        compose.onNodeWithText("Mo", substring = false).assertExists()
        compose.onNodeWithContentDescription("Einklappen").performClick()
        compose.waitForIdle()
        assertEquals(setOf(WeatherCard.DAILY), settings.foldedCards)
        compose.onNodeWithText("Mo").assertDoesNotExist()
        compose.onNodeWithText("Lange auf das Diagramm drücken zeigt die Werte jeder Stunde").assertExists()
        tapToday()
        compose.onNodeWithText("Lange auf das Diagramm drücken zeigt die Werte jeder Stunde").assertDoesNotExist()
        compose.onNodeWithTag("page").captureRoboImage("src/test/screenshots/daily_folded.png")
        compose.onNodeWithContentDescription("Aufklappen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mo").assertExists()
    }

    /** Today opens unless closed by hand – then it opens closed, until opened again. */
    @Test fun todayAsTheUserLeftIt() {
        val data = forecast()
        var opened by mutableIntStateOf(0)
        page { key(opened) { DailyCard(data, now) } }
        val hint = "Lange auf das Diagramm drücken zeigt die Werte jeder Stunde"
        compose.onNodeWithText(hint).assertExists()
        tapToday()
        compose.waitForIdle()
        assertTrue(settings.todayClosed)
        // the app opened again: today closed
        opened++
        compose.waitForIdle()
        compose.onNodeWithText(hint).assertDoesNotExist()
        tapToday()
        compose.waitForIdle()
        assertFalse(settings.todayClosed)
        opened++
        compose.waitForIdle()
        compose.onNodeWithText(hint).assertExists()
    }
}
