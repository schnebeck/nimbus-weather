package dev.nimbus.weather

import dev.nimbus.weather.util.Moon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SunTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun start(d: String) = LocalDate.parse(d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun hannoverSunriseSunsetEndOfSeptember() {
        // Hannover 2026-09-29: sunrise ≈ 07:17, sunset ≈ 19:05 CEST (timeanddate.com)
        val (rise, set) = Moon.sunTimes(start("2026-09-29"), 52.37, 9.73)
        assertNotNull(rise); assertNotNull(set)
        val r = java.time.Instant.ofEpochMilli(rise!!).atZone(zone).toLocalTime()
        val s = java.time.Instant.ofEpochMilli(set!!).atZone(zone).toLocalTime()
        assertEquals(7 * 60 + 17.0, r.hour * 60.0 + r.minute, 5.0)
        assertEquals(19 * 60 + 5.0, s.hour * 60.0 + s.minute, 5.0)
    }

    @Test fun noonAltitudeAtEquinoxIsNinetyMinusLatitude() {
        // Around the September equinox the sun culminates at about 90° − latitude.
        val day = start("2026-09-23")
        val max = (0..288).maxOf { Moon.sunAltitude(day + it * 300_000L, 52.37, 9.73) }
        assertEquals(90 - 52.37, max, 1.0)
    }

    @Test fun polarNightHasNoSunrise() {
        val (rise, set) = Moon.sunTimes(LocalDate.parse("2026-12-21").atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), 78.2, 15.6)
        assertEquals(null, rise); assertEquals(null, set)
    }
}
