/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TodayChartTest.kt
 * Today's day chart: measured and expected in columns of their own, a quantity not measured said so.
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.Meteogram
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * „Können wir nicht Spaltentitel "gemessen" und "erwartet" haben?“ – the hour 16–17 measured at
 * 17:32: temperature from the station, the sunshine not (yet): the readout put "60 min" under
 * "· gemessen" while the chart had the forecast's frame. Now the sunshine's measured cell is
 * empty and its expected one holds the 60 minutes; a station without sunshine shows none for
 * the hours over and says so in the legend.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TodayChartTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 1)
    private val t0 = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val h = 3_600_000L
    private val now = t0 + 17 * h + 32 * 60_000L

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    /** The station until 17:00: temperature and wind, no sunshine, no rain gauge. */
    private fun points(): List<MeteoPoint> {
        val hours = (0..17).map { k ->
            HistoryHour(t0 + k * h, HistoryHour.Measured(20.0, null, 4.0, 8.0, 0.0, null, null, Condition.PARTLY_CLOUDY), null)
        }
        val m = TodayMeasured.of(History(listOf(HistoryDay(today, hours)), "Alfeld", 20.3, "icon_seamless", ZoneOffset.UTC, now), today)!!
        return (0..25).map { k ->
            MeteoPoint(t0 + k * h, 18.0, Condition.CLEAR, k in 7..18, 0.0, precipitationChance = 0.0, sunshine = if (k in 8..18) 60.0 else 0.0, humidity = 68.0)
        }.map { m.apply(it, now) }
    }

    @Test fun measuredAndExpectedInColumns() {
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("UTC", true)) {
                Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) {
                    Meteogram(points(), t0, t0 + 24 * h, emptyList(), now, showNow = true)
                }
            }
        }
        compose.waitForIdle()
        val texts = compose.onAllNodes(androidx.compose.ui.test.hasText("min", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        compose.onNodeWithText(str(R.string.readout_measured)).assertExists()
        compose.onNodeWithText(str(R.string.readout_expected)).assertExists()
        // the hour shown (17–18, running): nothing measured of the sunshine, 60 minutes expected
        org.junit.Assert.assertTrue("60 min expected in $texts", texts.any { it == "60" + dev.nimbus.weather.util.NBSP + "min" })
        // no hour-wide "measured" after the weather any more
        assertEquals(0, compose.onAllNodesWithText("· " + str(R.string.measured_word), substring = true).fetchSemanticsNodes().size)
        // the station has no sunshine and no gauge: said in the legend
        compose.onNodeWithText(str(R.string.legend_sun_not_measured)).assertExists()
        compose.onNodeWithText(str(R.string.legend_precip_not_measured)).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/meteogram_today.png")
    }
}
