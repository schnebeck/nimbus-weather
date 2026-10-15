/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SummarySunTest.kt
 * The look-back's summary: the sunshine measured, and beneath it the forecast's for the same hours.
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.SummaryCard
import dev.nimbus.weather.util.TimeFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w360dp-h800dp-xxhdpi", application = android.app.Application::class)
class SummarySunTest {
    @get:Rule val compose = createComposeRule()

    /** A day: 30 minutes of sun measured each hour from 7 to 17 h (5 h 30 min), the forecast 45 (8 h 15 min). */
    @Test fun theForecastsSunshineBesideTheMeasured() {
        val t0 = 1_790_812_800_000L
        val hours = (0 until 24).map { i ->
            val day = i in 7..17
            HistoryHour(
                t0 + (i + 1) * 3_600_000L,
                HistoryHour.Measured(12.0, 0.0, 5.0, 10.0, 200.0, if (day) 30.0 else 0.0, 50.0, Condition.PARTLY_CLOUDY),
                HistoryHour.Modelled(12.5, 0.0, 6.0, 12.0, if (day) 45.0 else 0.0, Condition.MOSTLY_CLEAR, day),
            )
        }
        val day = HistoryDay(LocalDate.of(2026, 10, 1), hours)
        val history = History(listOf(day), "Hildesheim", 9.6, "icon_seamless", ZoneOffset.UTC, t0 + 25 * 3_600_000L)
        val sum = DaySummary.of(day)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("UTC", true)) {
                Box(Modifier.width(360.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { SummaryCard(sum, history, Settings(), TimeFormat("UTC", true)) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/summary_sun.png")
        compose.onNodeWithText("5:30\u00A0Std.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Vorhersage 8:15\u00A0Std.", substring = true).assertIsDisplayed()
    }
}
