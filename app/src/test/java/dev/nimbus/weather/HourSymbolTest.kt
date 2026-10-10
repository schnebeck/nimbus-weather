/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HourSymbolTest.kt
 * An hour's symbol agrees with its amount and sunshine: no rain cloud without rain, showers with sun.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An hour's symbol agrees with its amount and its sunshine: no rain cloud over an hour without
 * precipitation, showers for rain with sun in the same hour.
 */
class HourSymbolTest {
    private val wet = setOf(
        Condition.DRIZZLE, Condition.RAIN, Condition.HEAVY_RAIN, Condition.SHOWERS, Condition.FREEZING_RAIN,
        Condition.SLEET, Condition.SNOW, Condition.HEAVY_SNOW,
    )

    /** The model's code says rain, the amount is 0.0: the sky by the cloud cover, brightened by the sun. */
    @Test fun noRainCloudWithoutRain() {
        assertEquals(Condition.CLOUDY, WeatherCodes.forHour(61, 0.0, 100.0, 0.0))
        assertEquals(Condition.CLOUDY, WeatherCodes.forHour(80, 0.0, 96.0, null))
        assertEquals(Condition.PARTLY_CLOUDY, WeatherCodes.forHour(80, 0.0, 63.0, 39.8))
        assertEquals(Condition.CLEAR, WeatherCodes.forHour(61, 0.0, 90.0, 50.0))
        assertEquals(Condition.CLOUDY, WeatherCodes.forHour(71, 0.0, null, null))
        // thunder stays; an amount not known: the code as it is
        assertEquals(Condition.THUNDERSTORM, WeatherCodes.forHour(95, 0.0, 100.0, 0.0))
        assertEquals(Condition.RAIN, WeatherCodes.forHour(61, null, 100.0, 0.0))
    }

    /** Rain with sun in the same hour (15 minutes and more) is showers; heavy rain and snow stay. */
    @Test fun rainWithSunIsShowers() {
        assertEquals(Condition.SHOWERS, WeatherCodes.forHour(61, 0.4, 80.0, 20.0))
        assertEquals(Condition.SHOWERS, WeatherCodes.forHour(53, 0.1, 80.0, 15.0))
        assertEquals(Condition.RAIN, WeatherCodes.forHour(61, 0.4, 80.0, 10.0))
        assertEquals(Condition.HEAVY_RAIN, WeatherCodes.forHour(65, 12.0, 80.0, 20.0))
        assertEquals(Condition.SNOW, WeatherCodes.forHour(71, 0.3, 80.0, 30.0))
    }

    /** Hannover, 10 Oct 2026 (ICON): five hours with a rain code and 0.0 mm – none shows rain now. */
    @Test fun theForecastOfHannover() {
        val root = Fixtures.json("openmeteo_hannover_rain_traces.json")
        val hours = OpenMeteoSource.parseForecast(root).hourly
        val dry = hours.filter { it.precipitation == 0.0 }
        assertTrue(dry.isNotEmpty())
        assertTrue(dry.none { it.condition in wet })
        // one of them had 40 minutes of sun under 63 % cloud
        assertTrue(dry.any { it.condition == Condition.PARTLY_CLOUDY && (it.sunshine ?: 0.0) > 39.0 })
    }

    /** Norden (best match): rain hours with sun come as showers. */
    @Test fun theForecastOfNorden() {
        val hours = OpenMeteoSource.parseForecast(Fixtures.json("openmeteo_norden_best_match.json")).hourly
        val sunnyRain = hours.filter { (it.precipitation ?: 0.0) >= 0.05 && (it.sunshine ?: 0.0) >= 15.0 }
        assertTrue(sunnyRain.isNotEmpty())
        assertTrue(sunnyRain.none { it.condition == Condition.RAIN || it.condition == Condition.DRIZZLE })
    }
}
