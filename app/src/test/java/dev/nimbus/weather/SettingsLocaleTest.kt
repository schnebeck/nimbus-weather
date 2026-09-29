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
