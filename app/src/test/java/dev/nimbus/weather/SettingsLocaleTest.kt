/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SettingsLocaleTest.kt
 * Tests for the default units by country and the temperature format.
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

import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WindUnit
import dev.nimbus.weather.util.Units
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsLocaleTest {
    @Test fun germanyUsesMetricUnits() {
        val s = Settings.forLocale(Locale.GERMANY)
        assertEquals(TemperatureUnit.CELSIUS, s.temperatureUnit)
        assertEquals(WindUnit.KMH, s.windUnit)
        assertEquals(PrecipitationUnit.MM, s.precipitationUnit)
    }

    @Test fun usUsesFahrenheitMphInch() {
        val s = Settings.forLocale(Locale.US)
        assertEquals(TemperatureUnit.FAHRENHEIT, s.temperatureUnit)
        assertEquals(WindUnit.MPH, s.windUnit)
        assertEquals(PrecipitationUnit.INCH, s.precipitationUnit)
    }

    @Test fun ukUsesCelsiusAndMph() {
        val s = Settings.forLocale(Locale.UK)
        assertEquals(TemperatureUnit.CELSIUS, s.temperatureUnit)
        assertEquals(WindUnit.MPH, s.windUnit)
    }

    @Test fun temperatureWithUnit() {
        assertEquals("21 °C", Units.tempFull(20.6, TemperatureUnit.CELSIUS))
        assertEquals("70 °F", Units.tempFull(21.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("–", Units.tempFull(null, TemperatureUnit.CELSIUS))
    }
}
