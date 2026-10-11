/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SameHourBothViewsTest.kt
 * Today's hour in the 10-day forecast and in the look-back's day course: what both show of it is
 * the same – the weather, the temperature, what fell and its chance, wind, gusts, sun.
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * Norden on 11 Oct 2026 at 18:59 as on the phone: MET Nordic's forecast (no chance of its own –
 * the day's forecast takes one from another model), the station's readings up to 18:00 about a
 * kelvin above the model, drizzle forecast for 18–19 while the station says cloudy now – the hour
 * running now shows the weather now in both, as the header does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h2400dp-xxhdpi", application = android.app.Application::class)
class SameHourBothViewsTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val day = 1_791_676_800_000L                     // 11 Oct 2026 00:00 UTC
    private val now = day + 18 * h + 59 * 60_000L
    private val tf = TimeFormat("UTC", true)

    /** The model's hour ending at [end] o'clock. */
    private fun temp(end: Int) = 10.0 + 6 * sin((end - 9) / 24.0 * 2 * Math.PI)     // steep: an hour apart, a degree or more
    private fun cond(end: Int) = when (end) { in 1..12 -> Condition.RAIN; 19 -> Condition.DRIZZLE; else -> Condition.CLOUDY }
    private fun rain(end: Int) = when (end) { in 1..12 -> 1.2; 19 -> 0.3; 20 -> 0.2; else -> 0.0 }
    private fun sun(end: Int) = if (end in 13..17) 20.0 else 0.0

    private fun forecast(): WeatherData {
        val hourly = (0..48).map { k ->
            val e = k % 24
            HourlyPoint(
                day + k * h, temp(e), apparentTemperature = temp(e) - 2, condition = cond(e), isDay = e in 8..18,
                precipitation = rain(e), precipitationProbability = if (rain(e) > 0) 40.0 else 10.0, windSpeed = 3.0 + e % 5,
                windGust = 15.0 + e % 7, windDirection = 200.0, humidity = 90.0, sunshine = sun(e),
            )
        }
        val daily = (0..1).map { DailyPoint(day + it * 24 * h, Condition.RAIN, 14.0, 9.0, precipitationSum = 25.0, precipitationProbability = 100.0) }
        val cur = CurrentWeather(now, 13.0, 12.0, Condition.CLOUDY, false, 92.0, 11.0, 1012.0, 3.0, 15.0, 200.0, 90.0, 20000.0, 0.0, 0.0)
        return WeatherData(Place("n", "Norden", latitude = 53.6, longitude = 7.21), "UTC", 0, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    private fun history(): History {
        val hours = (0..25).map { k ->
            val e = k % 24
            val t = day + k * h
            val measured = if (t <= day + 18 * h) HistoryHour.Measured(
                temp(e) + 1.0, rain(e) * 0.8, 3.0 + e % 5, 15.0 + e % 7, 200.0, sun(e) + 5, 90.0, if (rain(e) > 0) Condition.RAIN else Condition.CLOUDY,
            ) else null
            HistoryHour(t, measured, HistoryHour.Modelled(temp(e), rain(e), 3.0 + e % 5, 15.0 + e % 7, sun(e), cond(e), e in 8..18, chance = null, windDirection = 200.0))
        }
        val today = HistoryDay(LocalDate.of(2026, 10, 11), hours.filter { it.time > day && it.time <= day + 24 * h })
        return History(listOf(today), "Norden-Leybuchtpolder", 11.6, "metno_nordic", ZoneOffset.UTC, now, allHours = hours)
    }

    /** The readout's rows of the card tagged [tag]: label to the values beside it, and its title line. */
    private fun readout(tag: String): Map<String, List<String>> {
        val card = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text }.let { it to n.boundsInRoot } }
            .filter { (_, b) -> card.contains(b.center) }
        fun row(of: Rect) = texts.filter { (_, b) -> kotlin.math.abs(b.center.y - of.center.y) < 6f && b.left >= of.right - 1 }.sortedBy { it.second.left }.map { it.first }
        // the title ("18:00–19:00" and the weather beside it), the table below it (the legend's words further down)
        val title = texts.firstOrNull { Regex("""\d\d:00–\d\d:00""").matches(it.first) } ?: return emptyMap()
        val labels = listOf("Temperatur", "Niederschlag", "Wahrsch.", "Wind", "Böen", "Sonne")
        val rows = labels.mapNotNull { l -> texts.filter { it.first == l && it.second.top > title.second.bottom }.minByOrNull { it.second.top }?.let { (_, b) -> l to row(b) } }.toMap()
        return rows + (title.first to row(title.second))
    }

    private fun both(at: (String) -> Unit = {}): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        at("forecast"); at("lookback")
        compose.waitForIdle()
        return readout("forecast") to readout("lookback")
    }

    /** Every value both show of the hour – where both have it – is the same. */
    private fun assertSame(what: String, f: Map<String, List<String>>, l: Map<String, List<String>>) {
        val common = f.keys intersect l.keys
        assertTrue("$what: too little in common: forecast $f, look-back $l", common.size >= 6)
        for (k in common) assertEquals("$what, $k (forecast $f, look-back $l)", f.getValue(k), l.getValue(k))
    }

    @Test fun bothViewsShowTheSameHour() {
        val data = forecast()
        val history = history()
        val measured = TodayMeasured.of(history, LocalDate.of(2026, 10, 11))
        val summary = DaySummary.of(history.days[0], now)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides tf) {
                Column(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) {
                    Column(Modifier.testTag("forecast")) { DailyCard(data, now, measured) }
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.testTag("lookback")) {
                        DayCourseCard(history.days[0], summary, Settings(), tf, history, data.place, data.hourly, data.current.condition, now)
                    }
                }
            }
        }
        compose.waitForIdle()
        // untouched: both show the hour running now
        val (f, l) = both()
        assertEquals("18:00–19:00", f.keys.firstOrNull { ":00–" in it })
        assertSame("untouched", f, l)
        // a past hour, the hour running now and one to come: held on the chart at the same place
        for (hour in listOf(12, 18, 21)) {
            /** Holds the chart of [tag] where its readout shows the hour from [hour] on (the finger moved until it does). */
            fun hold(tag: String) {
                val card = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                val title = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                    .first { card.contains(it.boundsInRoot.center) && ":00–" in it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }.boundsInRoot
                val y = title.top - 300f
                val perHour = card.width * 0.85f / 24
                var x = card.left + card.width * 0.08f + perHour * (hour + 0.5f)
                compose.onRoot().performTouchInput { down(Offset(x, y)) }
                compose.mainClock.advanceTimeBy(900)
                repeat(6) {
                    val shown = readout(tag).keys.firstOrNull { ":00–" in it }?.substringBefore(':')?.toIntOrNull() ?: return@repeat
                    if (shown == hour) return
                    x += (hour - shown) * perHour
                    compose.onRoot().performTouchInput { moveTo(Offset(x, y)) }
                    compose.mainClock.advanceTimeBy(100)
                }
            }
            hold("forecast")
            val fh = readout("forecast")
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
            hold("lookback")
            val lh = readout("lookback")
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
            assertEquals("held at $hour", "%02d:00–%02d:00".format(hour, hour + 1), fh.keys.firstOrNull { ":00–" in it })
            assertSame("the hour $hour–${hour + 1}", fh, lh)
            // the hour running now: the weather now, as the header has it
            if (hour == 18) assertEquals(listOf("Bewölkt"), fh.getValue("18:00–19:00"))
        }
    }
}
