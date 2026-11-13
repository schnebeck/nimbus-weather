/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarOutCardTest.kt
 * The precipitation card says when the DWD's radar is out over the place – and what stands in.
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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.RadarSource
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.PrecipitationCard
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class RadarOutCardTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L

    /** Norden, showers about: the DWD's radar out – with the KNMI's in its place ([knmi]) or none. */
    private fun data(out: Boolean, knmi: Boolean): WeatherData {
        val hourly = (0..30).map { k -> HourlyPoint(day + k * h, 9.0, condition = Condition.SHOWERS, isDay = true, precipitation = 0.4, precipitationProbability = 60.0) }
        val daily = listOf(DailyPoint(day, Condition.SHOWERS, 11.0, 6.0, precipitationSum = 4.0, precipitationProbability = 70.0))
        val cur = CurrentWeather(now, 9.0, 7.0, Condition.SHOWERS, true, 80.0, 6.0, 1008.0, 25.0, 40.0, 300.0, 80.0, 20000.0, 1.0, 0.4)
        val radar = if (knmi) RadarNow(now - 5 * 60_000L, 1.2, source = RadarSource.KNMI) else null
        return WeatherData(
            Place("n", "Norden", latitude = 53.6, longitude = 7.2, countryCode = "DE"), "UTC", 0, cur, hourly, daily,
            sources = emptyList(), fetchedAt = now, radarNow = radar, dwdRadarOut = out,
        )
    }

    private fun show(d: WeatherData) = compose.setContent {
        CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("UTC", true)) {
            Box(Modifier.width(411.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { PrecipitationCard(d, now) }
        }
    }

    @Test fun theKnmiStandsIn() {
        show(data(out = true, knmi = true))
        compose.onNodeWithTag("radar-out").assertTextContains("KNMI", substring = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/precip_radar_out_knmi.png")
    }

    @Test fun nothingStandsIn() {
        show(data(out = true, knmi = false))
        compose.onNodeWithTag("radar-out").assertTextContains("Station und Modell", substring = true)
    }

    @Test fun inServiceNothingIsSaid() {
        show(data(out = false, knmi = false))
        assertEquals(0, compose.onAllNodesWithTag("radar-out").fetchSemanticsNodes().size)
    }
}
