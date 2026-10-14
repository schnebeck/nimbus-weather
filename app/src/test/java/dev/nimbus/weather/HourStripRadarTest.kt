/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HourStripRadarTest.kt
 * The hourly strip with hours the radar tells: their amount in the chance's place, every symbol in line.
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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.HourlyCard
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.util.TimeFormat
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
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class HourStripRadarTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L

    /** The model's hours with 60 % each; the running hour and the next by the radar: dry, then 1.4 mm. */
    private fun data(): WeatherData {
        val hourly = (0..30).map { k ->
            val t = day + k * h
            val radar = t in day + 11 * h..day + 12 * h
            HourlyPoint(
                t, 14.0, condition = if (radar && t == day + 12 * h) Condition.RAIN else if (radar) Condition.CLOUDY else Condition.SHOWERS,
                isDay = true, precipitation = if (radar) (if (t == day + 12 * h) 1.4 else 0.0) else 0.6,
                precipitationProbability = if (radar) null else 60.0, radar = radar,
            )
        }
        val daily = listOf(DailyPoint(day, Condition.RAIN, 18.0, 8.0, precipitationSum = 5.4, precipitationProbability = 60.0))
        val cur = CurrentWeather(now, 14.0, 13.0, Condition.CLOUDY, true, 60.0, 8.0, 1020.0, 10.0, 20.0, 240.0, 80.0, 30000.0, 2.0, 0.0)
        return WeatherData(Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), "UTC", 0, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    @Test fun theRadarsAmountStandsInTheChancesLine() {
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("UTC", true)) {
                Box(Modifier.width(411.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { HourlyCard(data(), now) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/hourly_radar.png")
        // the running hour ("Jetzt", 10–11 h) dry by the radar, 11–12 h 1.4 mm – no chance shown for them
        val amounts = compose.onAllNodesWithText("mm", substring = true).fetchSemanticsNodes()
        assertTrue("radar amounts: ${amounts.size}", amounts.size >= 2)
        val chances = compose.onAllNodesWithText("%", substring = true).fetchSemanticsNodes()
        assertTrue(chances.isNotEmpty())
        // one line for all: the amounts stand where the chances stand – the symbols above them in line
        val tops = (amounts + chances).map { it.boundsInRoot.top }.distinct()
        assertEquals("tops: $tops", 1, tops.size)
        val heights = (amounts + chances).map { it.boundsInRoot.height }.distinct()
        assertEquals("heights: $heights", 1, heights.size)
    }
}
