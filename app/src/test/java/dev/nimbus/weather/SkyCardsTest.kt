/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SkyCardsTest.kt
 * The sun's and the moon's card: where each rises and sets, the moon's course over the day,
 * the directions on the axis – for the place shown.
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.MoonCard
import dev.nimbus.weather.ui.main.SunCard
import dev.nimbus.weather.util.TimeFormat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w1240dp-h1200dp-mdpi", application = android.app.Application::class)
class SkyCardsTest {
    @get:Rule val compose = createComposeRule()

    private val hannover = Place("h", "Hannover", latitude = 52.3759, longitude = 9.7320)
    private val bergen = Place("b", "Bergen", latitude = 60.39, longitude = 5.32)

    private fun data(place: Place, now: Long) = WeatherData(
        place, "Europe/Berlin", 3600, CurrentWeather(now, 8.0, 6.0, Condition.CLEAR, false, 80.0, 6.0, 1020.0, 10.0, 15.0, 200.0, 0.0, 30000.0, 0.0, 0.0),
        emptyList(), emptyList(), sources = emptyList(), fetchedAt = now,
    )

    private fun texts() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }

    private fun cards(vararg shown: Pair<Place, String>) {
        val tf = TimeFormat("Europe/Berlin", true)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides tf) {
                Row(Modifier.background(Color(0xFF14233A)).padding(8.dp)) {
                    shown.forEach { (place, at) ->
                        val now = ZonedDateTime.parse(at).toInstant().toEpochMilli()
                        Column(Modifier.width(380.dp).padding(6.dp)) {
                            SunCard(data(place, now), now)
                            MoonCard(data(place, now), now)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Hannover on an autumn evening: the sun rose in the east-south-east and set in the
     * west-south-west; the full moon is up in the east, its rise and set with their directions.
     */
    @Test fun whereTheSunAndTheMoonStand() {
        cards(hannover to "2026-10-26T20:00+01:00")
        val t = texts()
        assertTrue(t.toString(), "OSO" in t && "WSW" in t)
        assertTrue(t.toString(), t.any { Regex("""\d\d:\d\d · [NOSW]{1,3}""").matches(it) })
        assertTrue(t.toString(), t.any { it.startsWith("Mond jetzt") && "über dem Horizont, Richtung O" in it })
        // its highest is tonight after midnight, not today: none named, rather than the day's edge
        assertTrue(t.toString(), t.none { "Höchststand" in it && it.startsWith("Mond") })
    }

    /** The same evening in Bergen, midwinter and midsummer in Hannover: the pictures for a look. */
    @Test fun theCoursesAtOtherPlacesAndSeasons() {
        cards(bergen to "2026-10-26T20:00+01:00", hannover to "2026-12-21T12:00+01:00", hannover to "2026-06-21T22:00+02:00")
        // midwinter: the moon culminates in the evening, at its highest in the south
        assertTrue(texts().toString(), texts().any { it.startsWith("Mond jetzt") && "Höchststand 63°" in it })
        compose.onRoot().captureRoboImage("src/test/screenshots/sky_cards_seasons.png")
    }

    @Test fun hannoverToday() {
        cards(hannover to "2026-10-26T20:00+01:00")
        compose.onRoot().captureRoboImage("src/test/screenshots/sky_cards_hannover.png")
    }
}
