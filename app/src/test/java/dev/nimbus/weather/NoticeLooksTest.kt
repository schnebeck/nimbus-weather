/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NoticeLooksTest.kt
 * The notices as the phone shows them – the source written out – and the warnings card showing
 * the level chosen, the weaker warnings in a line of their own.
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

import android.app.Notification
import android.app.NotificationManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.notify.NoticePoster
import dev.nimbus.weather.notify.NoticeStep
import dev.nimbus.weather.ui.main.AlertsCard
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "de-rDE")
class NoticeLooksTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val h = 3_600_000L
    private val day = 1_792_195_200_000L                // 2026-10-17 00:00 UTC
    private val now = day + 17 * h + 30 * 60_000L
    private val place = Place("p", "Groß Düngen", latitude = 52.095, longitude = 10.022)
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    private fun shown(): List<Notification> = shadowOf(nm).allNotifications
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    /** A warning: its headline, its time and the place – and the source written out. */
    @Test fun aWarningWithItsSource() {
        val a = WeatherAlert("a", "Amtliche WARNUNG vor STURMBÖEN", "Sturmböen", "", null, AlertSeverity.MODERATE, day + 12 * h, day + 28 * h, "DWD")
        NoticePoster.post(app, place, listOf(NoticeStep.Warning(a, loud = true)), now, ZoneId.of("UTC"))
        val n = shown().single()
        assertEquals("Amtliche WARNUNG vor STURMBÖEN", n.title())
        assertTrue(n.text(), n.text().contains("Groß Düngen") && n.text().contains("Quelle: Deutscher Wetterdienst"))
        assertEquals(NoticePoster.WARNINGS, n.channelId)
    }

    /** A gauge: water, gauge and level in the title; the level, its trend and the operator by its full name. */
    @Test fun aGaugeWithItsOperator() {
        val g = GaugeInfo("nlwkn-heinde", "HEINDE", "INNERSTE", 9.5, false, level = 241.0, alertStage = 1, alertLevels = mapOf(1 to 230.0), provider = GaugeProvider.NLWKN, tendency = 1)
        NoticePoster.post(app, place, listOf(NoticeStep.Gauge(g, 1, below = false, loud = true)), now, ZoneId.of("UTC"))
        val n = shown().single()
        assertEquals("Innerste in Heinde: Meldestufe 1 erreicht", n.title())
        assertTrue(n.text(), n.text().contains("241 cm, steigend") && n.text().contains("Niedersächsischer Landesbetrieb für Wasserwirtschaft, Küsten- und Naturschutz"))
    }

    /** A quiet update of a notice put away: it stays away. */
    @Test fun putAwayStaysAway() {
        val g = GaugeInfo("nlwkn-heinde", "HEINDE", "INNERSTE", 9.5, false, level = 200.0, alertStage = 0, alertLevels = mapOf(1 to 230.0), provider = GaugeProvider.NLWKN)
        NoticePoster.post(app, place, listOf(NoticeStep.Gauge(g, 1, below = true, loud = false)), now, ZoneId.of("UTC"))
        assertTrue(shown().isEmpty())
    }

    /** Rain ahead: the words of the precipitation card, the radar as source, gone when the rain is over. */
    @Test fun rainAhead() {
        val notice = Insights.PrecipNotice(Insights.PrecipKind.RAIN, Insights.Intensity.LIGHT, Insights.Nowcast.StartsIn(30), at = now + 30 * 60_000L)
        NoticePoster.post(app, place, listOf(NoticeStep.Rain(notice, now + 75 * 60_000L)), now, ZoneId.of("UTC"))
        val n = shown().single()
        assertEquals("Leichter Regen ab etwa 18:00", n.title())
        assertTrue(n.text(), n.text().contains("bis etwa 18:45") && n.text().contains("Regenradar des Deutschen Wetterdienstes"))
        assertEquals(NoticePoster.RAIN, n.channelId)
        assertEquals(75 * 60_000L, n.timeoutAfter)
    }

    /** The card: warnings from the level chosen; a weaker one as a line – a tap shows it too. */
    @Test fun theCardFromTheLevelChosen() {
        val strong = WeatherAlert("s", "Amtliche WARNUNG vor STURMBÖEN", "", "", null, AlertSeverity.MODERATE, now, now + h, "DWD")
        val weak = WeatherAlert("w", "Amtliche WARNUNG vor WINDBÖEN", "", "", null, AlertSeverity.MINOR, now, now + h, "DWD")
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(warningLevel = 2), LocalTimeFormat provides TimeFormat("UTC", true)) {
                AlertsCard(listOf(strong, weak))
            }
        }
        compose.onNodeWithText("Amtliche WARNUNG vor STURMBÖEN").assertExists()
        compose.onNodeWithText("Amtliche WARNUNG vor WINDBÖEN").assertDoesNotExist()
        compose.onNodeWithText("1 Warnung geringerer Stufe").assertExists().performClick()
        compose.onNodeWithText("Amtliche WARNUNG vor WINDBÖEN").assertExists()
    }

    /** Nimbus in English: the card and the notice word the DWD's warning in English, from the same data. */
    @Test @Config(qualifiers = "en-rGB") fun aWarningInNimbussLanguage() {
        val english = dev.nimbus.weather.data.model.AlertText("Official WARNING of GALE", "gale", "There is a risk of gale-force gusts.")
        val a = WeatherAlert("a", "Amtliche WARNUNG vor STURMBÖEN", "STURMBÖEN", "Es treten Sturmböen auf.", null, AlertSeverity.MODERATE, now, now + h, "DWD", english)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(warningLevel = 2), LocalTimeFormat provides TimeFormat("UTC", true)) {
                AlertsCard(listOf(a))
            }
        }
        compose.onNodeWithText("Official WARNING of GALE").assertExists().performClick()
        compose.onNodeWithText("There is a risk of gale-force gusts.").assertExists()
        compose.onNodeWithText("Amtliche WARNUNG vor STURMBÖEN").assertDoesNotExist()
        NoticePoster.post(app, place, listOf(NoticeStep.Warning(a, loud = true)), now, ZoneId.of("UTC"))
        assertEquals("Official WARNING of GALE", shown().single().title())
    }
}
