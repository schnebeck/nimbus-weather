/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PageJumpTest.kt
 * The widget's parts as bookmarks of the page: the hours, today opened, the pollen, the head.
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.main.LocalSettingsUpdater
import dev.nimbus.weather.ui.main.PageJump
import dev.nimbus.weather.ui.main.PageSection
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
@Config(sdk = [35], qualifiers = "de-w411dp-h900dp-xxhdpi", application = android.app.Application::class)
class PageJumpTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = System.currentTimeMillis() / h * h + 20 * 60_000L
    private var settings by mutableStateOf(Settings().withCard(WeatherCard.RADAR, false).withCard(WeatherCard.MODELS, false))
    private var jump by mutableStateOf<PageJump?>(null)
    private var jumped: PageJump? = null

    private fun data(): WeatherData {
        val start = now / h * h - 2 * h
        val hourly = (0..72).map { k -> HourlyPoint(start + k * h, 14.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0, precipitationProbability = 10.0) }
        val daily = (-1..9).map { DailyPoint(start + it * 24 * h, Condition.CLOUDY, 20.0, 9.0, precipitationSum = 0.0) }
        val cur = CurrentWeather(now, 11.0, 13.0, Condition.CLOUDY, true, 97.0, 10.0, 1032.0, 3.0, 6.0, 40.0, 100.0, 12000.0, 0.0, 0.0)
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate()
            .atStartOfDay(java.time.ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val pollen = PollenForecast(
            PollenSourceKind.DWD, "Niedersachsen",
            (0..2).map { PollenDay(today + it * 24 * h, mapOf(PollenType.BIRCH to 2f, PollenType.GRASS to 1f)) },
        )
        return WeatherData(Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), "Europe/Berlin", 7200, cur, hourly, daily, pollen = pollen, sources = emptyList(), fetchedAt = now)
    }

    private fun page() {
        val d = data()
        compose.setContent {
            CompositionLocalProvider(LocalSettingsUpdater provides { f: (Settings) -> Settings -> settings = f(settings) }) {
                Box(Modifier.requiredSize(411.dp, 900.dp)) {
                    WeatherPage(d.place, PlaceState(d), settings, null, true, {}, {}, {}, jump = jump, onJumped = { jumped = it })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun go(section: PageSection) {
        val j = PageJump("p", section)
        jump = j
        compose.waitForIdle()
        assertEquals(j, jumped)
    }

    private fun topOf(text: String): Dp = compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot().top

    /** Right below the page's header (collapsed, 134 dp), not under it and not further down. */
    private fun belowTheHeader(top: Dp) = assertTrue("$top", top in 120.dp..240.dp)

    /**
     * The pollen: their card opened though folded – folded it stays in the settings – and on the
     * screen below the header (near the page's end it cannot go higher).
     */
    @Test fun thePollenOpenedBelowTheHeader() {
        settings = settings.copy(foldedCards = settings.foldedCards + WeatherCard.POLLEN)
        page()
        go(PageSection.POLLEN)
        val title = topOf("POLLENFLUG")
        assertTrue("$title", title in 120.dp..700.dp)
        val arrows = compose.onAllNodesWithContentDescription("Einklappen").fetchSemanticsNodes()
        assertTrue("opened", arrows.any { n -> kotlin.math.abs(with(compose.density) { n.boundsInRoot.top.toDp() }.value - title.value) < 30f })
        assertTrue(WeatherCard.POLLEN in settings.foldedCards)
    }

    /** The precipitation: the days with today opened, though closed in the settings. */
    @Test fun todayOpenedAmongTheDays() {
        settings = settings.copy(todayClosed = true)
        page()
        compose.onNodeWithTag("day-chart", useUnmergedTree = true).assertDoesNotExist()
        go(PageSection.TODAY)
        belowTheHeader(topOf("TAGE-VORHERSAGE"))
        compose.onNodeWithTag("day-chart", useUnmergedTree = true).assertExists()
    }

    /** The temperature: the hours ahead right below the header. */
    @Test fun theTemperatureLeadsToTheHours() {
        page()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("POLLENFLUG"))
        go(PageSection.HOURLY)
        belowTheHeader(topOf("in den nächsten Stunden"))
    }

    /** A card switched off – or a tap beside the parts – leads to the page's head. */
    @Test fun aCardSwitchedOffLeadsToTheHead() {
        settings = settings.withCard(WeatherCard.POLLEN, false)
        page()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("TAGE-VORHERSAGE", substring = true))
        go(PageSection.POLLEN)
        // the header open: the first cards below its 288 dp
        assertTrue(topOf("in den nächsten Stunden") > 280.dp)
    }
}
