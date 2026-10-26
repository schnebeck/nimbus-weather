/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/BrightSkyTest.kt
 * Tests for parsing Bright Sky station observations and alerts.
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

import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.StationObservation
import dev.nimbus.weather.data.repo.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightSkyTest {
    @Test
    fun `parses station observation`() {
        val obs = assertNotNull(BrightSkySource.parseCurrent(Fixtures.json("brightsky_current.json"))).let {
            BrightSkySource.parseCurrent(Fixtures.json("brightsky_current.json"))!!
        }
        assertEquals("Berlin-Tempelhof", obs.stationName)
        assertEquals(5.8, obs.distanceKm, 0.1)
        assertTrue(obs.observedDry)
        assertNotNull(obs.temperature)
    }

    @Test
    fun `parses alerts in both languages in the order of their periods`() {
        val json = JsonCodec.parseToJsonElement(
            """{"alerts":[
              {"id":1,"alert_id":"a","status":"actual","severity":"minor","event_en":"frost","event_de":"FROST",
               "headline_en":"Official WARNING of FROST","headline_de":"Amtliche WARNUNG vor FROST",
               "description_en":"cold","description_de":"kalt","onset":"2026-01-10T18:00:00+01:00","expires":"2026-01-11T10:00:00+01:00"},
              {"id":2,"alert_id":"b","status":"actual","severity":"severe","event_en":"gale","event_de":"STURMBÖEN",
               "headline_en":"Official WARNING of GALE","headline_de":"Amtliche UNWETTERWARNUNG vor STURMBÖEN",
               "description_en":"wind","description_de":"Wind","onset":null,"expires":null},
              {"id":3,"alert_id":"c","status":"test","severity":"extreme","headline_en":"test"}
            ]}""",
        )
        val alerts = BrightSkySource.parseAlerts(json)
        assertEquals(2, alerts.size)
        assertEquals(AlertSeverity.SEVERE, alerts[0].severity)
        assertEquals("Amtliche UNWETTERWARNUNG vor STURMBÖEN", alerts[0].inLanguage(german = true).headline)
        assertEquals("Official WARNING of FROST", alerts[1].inLanguage(german = false).headline)
        assertNotNull(alerts[1].onset)
    }

    /**
     * After a change of Nimbus's language the warnings speak the new one at once: both wordings are
     * kept, chosen when shown – also from the stored data, without a new fetch; a warning worded
     * only in German stays German.
     */
    @Test
    fun `the warnings follow a change of language without a new fetch`() {
        val json = JsonCodec.parseToJsonElement(
            """{"alerts":[
              {"id":1,"alert_id":"a","status":"actual","severity":"moderate","event_en":"strong wind gusts","event_de":"WINDBÖEN",
               "headline_en":"Official WARNING of WIND GUSTS","headline_de":"Amtliche WARNUNG vor WINDBÖEN",
               "description_en":"There is a risk of wind gusts.","description_de":"Es treten Windböen auf.",
               "instruction_de":"Achten Sie auf herabstürzende Äste.","onset":null,"expires":null},
              {"id":2,"alert_id":"b","status":"actual","severity":"minor","event_de":"FROST",
               "headline_de":"Amtliche WARNUNG vor FROST","description_de":"Es tritt Frost auf.","onset":null,"expires":null}
            ]}""",
        )
        val list = kotlinx.serialization.builtins.ListSerializer(WeatherAlert.serializer())
        val stored = JsonCodec.decodeFromString(list, JsonCodec.encodeToString(list, BrightSkySource.parseAlerts(json)))
        val gusts = stored.first { it.id == "a" }
        assertEquals("Amtliche WARNUNG vor WINDBÖEN", gusts.inLanguage(german = true).headline)
        assertEquals("Es treten Windböen auf.", gusts.inLanguage(german = true).description)
        val english = gusts.inLanguage(german = false)
        assertEquals("Official WARNING of WIND GUSTS", english.headline)
        assertEquals("There is a risk of wind gusts.", english.description)
        // no English instruction given: the German one rather than none
        assertEquals("Achten Sie auf herabstürzende Äste.", english.instruction)
        // only in German: German in both
        val frost = stored.first { it.id == "b" }
        assertEquals("Amtliche WARNUNG vor FROST", frost.inLanguage(german = false).headline)
        // stored before both were kept: as it was fetched
        val old = JsonCodec.decodeFromString(
            WeatherAlert.serializer(),
            """{"id":"x","headline":"Official WARNING of FROST","event":"frost","description":"cold","severity":"MINOR","onset":null,"expires":null,"source":"DWD"}""",
        )
        assertEquals("Official WARNING of FROST", old.inLanguage(german = true).headline)
    }

    /**
     * The warnings in the order of their periods, not of their levels: the gusts from this evening
     * before the storm from tomorrow, the frost running already before both.
     */
    @Test
    fun `alerts are in the order of their periods`() {
        val h = 3_600_000L
        fun alert(id: String, severity: AlertSeverity, onset: Long?, expires: Long?) =
            WeatherAlert(id, id, id, "", severity = severity, onset = onset, expires = expires, source = "DWD")
        val storm = alert("storm", AlertSeverity.SEVERE, 30 * h, 40 * h)
        val gusts = alert("gusts", AlertSeverity.MINOR, 18 * h, 22 * h)
        val gustsLonger = alert("gusts longer", AlertSeverity.MINOR, 18 * h, 26 * h)
        val thunder = alert("thunder", AlertSeverity.MODERATE, 18 * h, 22 * h)
        val frost = alert("frost", AlertSeverity.MINOR, 2 * h, 10 * h)
        val flood = alert("flood", AlertSeverity.MODERATE, null, null).copy(source = "LHP")
        val ordered = listOf(storm, gusts, gustsLonger, thunder, frost, flood).sortedWith(WeatherAlert.chronological)
        assertEquals(listOf("flood", "frost", "thunder", "gusts", "gusts longer", "storm"), ordered.map { it.id })
    }

    private val model = CurrentWeather(
        time = 0, temperature = 10.0, apparentTemperature = 8.0, condition = Condition.RAIN, isDay = true,
        humidity = 80.0, dewPoint = 7.0, pressure = 1010.0, windSpeed = 10.0, windGust = 20.0, windDirection = 200.0,
        cloudCover = 90.0, visibility = 10000.0, uvIndex = 1.0, precipitation = 0.5,
    )

    private fun obs(condition: Condition?, dry: Boolean, t: Double = 12.0) = StationObservation(
        time = 0, stationName = "Test", distanceKm = 3.0, temperature = t, humidity = 70.0, dewPoint = 6.0, pressure = 1012.0,
        windSpeed = 15.0, windGust = 30.0, windDirection = 250.0, visibility = 20000.0, cloudCover = 40.0,
        precipitation60 = 0.0, condition = condition, observedDry = dry,
    )

    @Test
    fun `measurements override model values`() {
        val m = dev.nimbus.weather.data.repo.NowWeather.mergeObservation(model, obs(Condition.SNOW, false))
        assertEquals(12.0, m.temperature, 0.0)
        assertEquals(10.0, m.apparentTemperature!!, 0.001)  // shifted by the same delta
        assertEquals(Condition.SNOW, m.condition)
        assertEquals("Test", m.stationName)
        assertEquals(1.0, m.uvIndex!!, 0.0)                 // not measured -> model value kept
    }

    @Test
    fun `dry station downgrades modelled rain`() {
        val m = dev.nimbus.weather.data.repo.NowWeather.mergeObservation(model, obs(null, dry = true))
        assertFalse(m.condition.isPrecipitation)
        assertEquals(Condition.PARTLY_CLOUDY, m.condition)
    }

    @Test
    fun `station rain does not downgrade modelled thunderstorm`() {
        val m = dev.nimbus.weather.data.repo.NowWeather.mergeObservation(model.copy(condition = Condition.THUNDERSTORM), obs(Condition.RAIN, false))
        assertEquals(Condition.THUNDERSTORM, m.condition)
    }

    @Test
    fun `DWD area covers Germany only`() {
        assertTrue(WeatherRepository.isInDwdArea(52.52, 13.40))   // Berlin
        assertTrue(WeatherRepository.isInDwdArea(47.42, 10.98))   // Zugspitze
        assertFalse(WeatherRepository.isInDwdArea(48.85, 2.35))   // Paris
        assertFalse(WeatherRepository.isInDwdArea(41.9, 12.5))    // Rome
    }
}
