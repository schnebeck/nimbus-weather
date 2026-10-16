/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TodayCurvesTest.kt
 * Today's chart in the 10-day forecast: the dashed curve named the forecast so far, the info text
 * saying what both curves are, and the setting drawing the readings against the forecast of the
 * whole day as the look-back's course of the day does.
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

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.main.DailyCard
import dev.nimbus.weather.ui.main.DayCourseCard
import dev.nimbus.weather.ui.main.HourAxis
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.TodayMeasured
import dev.nimbus.weather.ui.main.dayCurve
import dev.nimbus.weather.ui.main.dayExpected
import dev.nimbus.weather.ui.main.dayReadingsCurve
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.sin

/** A day with the station's readings up to 15:00, a kelvin above the model; now 15:40. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class TodayCurvesTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_791_676_800_000L                     // 11 Oct 2026 00:00 UTC
    private val now = day + 15 * h + 40 * 60_000L
    private val lastReading = day + 15 * h
    private val tf = TimeFormat("UTC", true)

    private fun temp(end: Int) = 10.0 + 6 * sin((end - 9) / 24.0 * 2 * Math.PI)
    private fun rain(end: Int) = when (end) { in 6..9 -> 0.8; 18 -> 0.4; else -> 0.0 }

    private fun forecast(): WeatherData {
        val hourly = (0..48).map { k ->
            val e = k % 24
            HourlyPoint(
                day + k * h, temp(e), apparentTemperature = temp(e) - 2, condition = if (rain(e) > 0) Condition.RAIN else Condition.CLOUDY,
                isDay = e in 8..18, precipitation = rain(e), precipitationProbability = if (rain(e) > 0) 60.0 else 10.0,
                windSpeed = 8.0, windGust = 20.0, windDirection = 230.0, humidity = 85.0, sunshine = if (e in 11..14) 30.0 else 0.0,
            )
        }
        val daily = (0..1).map { DailyPoint(day + it * 24 * h, Condition.RAIN, 16.0, 5.0, precipitationSum = 3.6, precipitationProbability = 60.0) }
        val cur = CurrentWeather(now, temp(15) + 1, temp(15) - 1, Condition.CLOUDY, true, 85.0, 8.0, 1012.0, 3.0, 20.0, 230.0, 90.0, 20000.0, 0.0, 0.0)
        return WeatherData(Place("h", "Hildesheim", latitude = 52.15, longitude = 9.95), "UTC", 0, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    private fun history(): History {
        val hours = (0..25).map { k ->
            val e = k % 24
            val t = day + k * h
            val measured = if (t <= lastReading) HistoryHour.Measured(
                temp(e) + 1.0, rain(e), 8.0, 20.0, 230.0, if (e in 11..14) 40.0 else 0.0, 85.0, if (rain(e) > 0) Condition.RAIN else Condition.CLOUDY,
            ) else null
            HistoryHour(t, measured, HistoryHour.Modelled(temp(e), rain(e), 8.0, 20.0, if (e in 11..14) 30.0 else 0.0, Condition.CLOUDY, e in 8..18, chance = null, windDirection = 230.0))
        }
        val today = HistoryDay(LocalDate.of(2026, 10, 11), hours.filter { it.time > day && it.time <= day + 24 * h })
        return History(listOf(today), "Hildesheim", 3.1, "icon_seamless", ZoneOffset.UTC, now, allHours = hours)
    }

    /** Today's day chart of the 10-day forecast and the look-back's course of today, with [settings]. */
    private fun both(settings: Settings) {
        val data = forecast()
        val history = history()
        val measured = TodayMeasured.of(history, LocalDate.of(2026, 10, 11))
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides settings, LocalTimeFormat provides tf) {
                Column(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) {
                    Column(Modifier.testTag("forecast")) { DailyCard(data, now, measured) }
                    Spacer(Modifier.height(16.dp))
                    DayCourseCard(history.days[0], DaySummary.of(history.days[0], now), settings, tf, history, data.place, data.hourly, data.current.condition, now)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun ctx() = RuntimeEnvironment.getApplication()

    /**
     * Left of the now line the coloured curve is what was measured, the dashed one what had been
     * forecast for those hours: named the forecast so far – the look-back's dashed curve, the
     * forecast of the whole day, keeps its name.
     */
    @Test fun theDashedCurveIsTheForecastSoFar() {
        both(Settings())
        compose.onNodeWithTag("forecast").captureRoboImage("src/test/screenshots/today_curves_so_far.png")
        compose.onAllNodesWithText("Prognose bisher").assertCountEquals(1)
        compose.onAllNodesWithText("Temp. Prognose").assertCountEquals(1)
    }

    /** The setting on: today's chart as the look-back's – the readings against the whole day's forecast. */
    @Test fun measuredAgainstTheForecast() {
        both(Settings(todayMeasuredVsForecast = true))
        compose.onNodeWithTag("forecast").captureRoboImage("src/test/screenshots/today_curves_apart.png")
        compose.onAllNodesWithText("Prognose bisher").assertCountEquals(0)
        compose.onAllNodesWithText("Temp. Prognose").assertCountEquals(2)
    }

    /** The coloured curve: the readings up to the last one (setting on), or going on as the forecast to 24 h (off); the dashed one to 24 h in both. */
    @Test fun theReadingsEndAtTheLastOne() {
        val data = forecast()
        val measured = TodayMeasured.of(history(), LocalDate.of(2026, 10, 11))
        val end = HourAxis.dayAxisEnd(day + 24 * h)
        val readings = dayReadingsCurve(data.hourly, data.minutely, day, end, measured)
        assertEquals(lastReading, readings.maxOf { it.time })
        assertEquals(temp(15) + 1.0, readings.last().value, 1e-9)
        assertEquals(end, dayCurve(data.hourly, data.minutely, day, end, measured).maxOf { it.time })
        val expected = dayExpected(data.hourly, data.minutely, day, end, measured)!!
        assertEquals(end, expected.maxOf { it.time })
        // up to the last reading the model's own: a kelvin below the readings
        assertEquals(temp(12), expected.first { it.time == day + 12 * h }.value, 1e-9)
        // without readings: the day's curve
        assertEquals(dayCurve(data.hourly, data.minutely, day, end, null), dayReadingsCurve(data.hourly, data.minutely, day, end, null))
    }

    /** The info text says what both curves of today are and names the setting – in German and English. */
    @Test fun theInfoTextNamesTodaysCurves() {
        for (locale in listOf(Locale.GERMAN, Locale.ENGLISH)) {
            val res = ctx().createConfigurationContext(Configuration(ctx().resources.configuration).apply { setLocale(locale) }).resources
            val body = res.getString(R.string.term_daily_body)
            assertTrue("$locale: $body", res.getString(R.string.legend_temp_forecast_so_far) in body)
            assertTrue("$locale: $body", res.getString(R.string.settings_today_apart) in body)
        }
    }

    /** The setting: off by default, switched in the cards' group of the settings, and kept. */
    @Test fun theSettingIsKept() {
        assertFalse(Settings().todayMeasuredVsForecast)
        assertFalse(JsonCodec.decodeFromString(Settings.serializer(), "{}").todayMeasuredVsForecast)
        val on = Settings(todayMeasuredVsForecast = true)
        assertTrue(JsonCodec.decodeFromString(Settings.serializer(), JsonCodec.encodeToString(Settings.serializer(), on)).todayMeasuredVsForecast)
        var settings by mutableStateOf(Settings())
        compose.setContent { dev.nimbus.weather.ui.settings.SettingsScreen(settings, { settings = it(settings) }, {}, {}) }
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(hasText("Heute: Messung gegen Prognose"))
        compose.onNodeWithText("Heute: Messung gegen Prognose").performClick()
        compose.waitForIdle()
        assertTrue(settings.todayMeasuredVsForecast)
    }
}
