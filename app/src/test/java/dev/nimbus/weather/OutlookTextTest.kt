/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/OutlookTextTest.kt
 * "ab etwa 22:00 Nieselregen … Kein Niederschlag in Sicht": the short forecast never calls dry
 * what an hour's symbol shows as precipitation.
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

import androidx.compose.ui.test.junit4.createComposeRule
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.main.Outlook
import dev.nimbus.weather.ui.main.outlookText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "de")
class OutlookTextTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = 1_791_219_600_000L        // 5 Oct. 2026, 20:20 in Norden
    private val drizzleAt = (now / h + 2) * h    // 22:00

    /** Norden that evening: cloudy, at 22:00 the symbol drizzle – a trace, a chance below 10 %. */
    private fun norden(): WeatherData {
        val hourly = (0..14).map { k ->
            val t = (now / h + k) * h
            val drizzle = t == drizzleAt
            HourlyPoint(t, 17.0, condition = if (drizzle) Condition.DRIZZLE else Condition.CLOUDY, isDay = false,
                precipitationProbability = if (drizzle) 4.0 else 0.0, precipitation = if (drizzle) 0.1 else 0.0)
        }
        val cur = CurrentWeather(now, 17.0, 16.0, Condition.CLOUDY, false, 80.0, 13.0, 1020.0, 20.0, 40.0, 260.0, 100.0, null, null, 0.0)
        return WeatherData(Place("n", "Norden", latitude = 53.596, longitude = 7.206), "Europe/Berlin", 7200, cur, hourly, emptyList(), sources = emptyList(), fetchedAt = now)
    }

    @Test fun drizzleShownIsNotDry() {
        val p = Outlook.from(norden(), now, isAfternoon = false).precipitation
        assertEquals(Outlook.Precip.Unlikely(Condition.DRIZZLE, drizzleAt, "<10"), p)
    }

    @Test fun theTextSaysHowLikelyTheDrizzleIs() {
        var text = ""
        compose.setContent { text = outlookText(norden(), now) }
        compose.waitForIdle()
        assertTrue(text, "Nieselregen" in text)
        assertFalse(text, "Kein Niederschlag in Sicht" in text)
        assertTrue(text, "Die Wahrscheinlichkeit dafür ist gering (<10 %)." in text)
    }

    /** No hour shows any: dry as before. */
    @Test fun noHourShowingItIsDry() {
        val dry = norden().let { d -> d.copy(hourly = d.hourly.map { it.copy(condition = Condition.CLOUDY, precipitation = 0.0, precipitationProbability = 0.0) }) }
        assertEquals(Outlook.Precip.Dry, Outlook.from(dry, now, isAfternoon = false).precipitation)
    }
}
