/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CardSettingsFoldTest.kt
 * The settings of a hidden card fold to one line that brings the card back, and open with it.
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.ui.settings.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class CardSettingsFoldTest {
    @get:Rule val compose = createComposeRule()
    private var settings by mutableStateOf(Settings())

    private fun screen() {
        compose.setContent { SettingsScreen(settings, { settings = it(settings) }, {}, {}) }
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    /**
     * The pollen card hidden: its section is one line, no types to choose; switched on there, the
     * card is on the page again and the section opens with its types.
     */
    @Test fun aHiddenCardsSettingsFoldAndOpenWithIt() {
        settings = settings.withCard(WeatherCard.POLLEN, false)
        screen()
        scrollTo("POLLENFLUG")
        scrollTo("Kachel ausgeblendet")
        compose.onNodeWithText("Hasel").assertDoesNotExist()
        compose.onNodeWithText("Kachel ausgeblendet").performClick()
        compose.waitForIdle()
        assertTrue(settings.shows(WeatherCard.POLLEN))
        scrollTo("Hasel")
        compose.onNodeWithText("Hasel").assertExists()
        compose.onNodeWithText("Kachel ausgeblendet").assertDoesNotExist()
    }

    /** A card on the page: its settings as they are, no folded line. */
    @Test fun aShownCardsSettingsAsTheyAre() {
        screen()
        scrollTo("Hasel")
        compose.onNodeWithText("Kachel ausgeblendet").assertDoesNotExist()
    }

    /**
     * The cards' own switches only with their cards: today against the forecast with the 10 days,
     * a dry day's card with the precipitation; the separate chart stays (the look-back has it too).
     */
    @Test fun aCardsSwitchesOnlyWithIt() {
        settings = settings.withCard(WeatherCard.DAILY, false).withCard(WeatherCard.PRECIPITATION, false)
        screen()
        scrollTo("Niederschlag als eigenes Diagramm")
        compose.onNodeWithText("Heute: Messung gegen Prognose").assertDoesNotExist()
        compose.onNodeWithText("Niederschlag-Kachel an trockenen Tagen").assertDoesNotExist()
        settings = settings.withCard(WeatherCard.DAILY, true).withCard(WeatherCard.PRECIPITATION, true)
        compose.waitForIdle()
        compose.onNodeWithText("Heute: Messung gegen Prognose").assertExists()
        compose.onNodeWithText("Niederschlag-Kachel an trockenen Tagen").assertExists()
    }
}
