/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/OpenMeteoParserTest.kt
 * Tests for parsing Open-Meteo forecasts and merging models.
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

import dev.nimbus.weather.data.model.ComparisonModels
import dev.nimbus.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoParserTest {
    private val icon = OpenMeteoSource.parseForecast(Fixtures.json("openmeteo_icon.json"))
    private val best = OpenMeteoSource.parseForecast(Fixtures.json("openmeteo_best.json"))

    @Test
    fun `parses current weather`() {
        val c = assertNotNull(icon.current).let { icon.current!! }
        assertTrue(c.temperature in -40.0..50.0)
        assertTrue(c.time > 1_700_000_000_000L)
        assertEquals("Europe/Berlin", icon.timezone)
    }

    @Test
    fun `drops hours without temperature (ICON ends after ~8 days)`() {
        assertTrue(icon.hourly.size in 150..239)
        assertTrue(icon.hourly.zipWithNext().all { (a, b) -> b.time - a.time == 3_600_000L })
        assertEquals(241, best.hourly.size)   // 1 past hour + 240 forecast hours
    }

    @Test
    fun `ICON has no UV index, best match fills it`() {
        assertTrue(icon.hourly.all { it.uvIndex == null })
        val merged = icon.mergedWith(best)
        assertTrue(merged.hourly.any { it.uvIndex != null })
        assertNotNull(merged.current?.uvIndex)
        assertEquals(10, merged.daily.size)
        assertTrue(merged.hourly.size >= best.hourly.size)
        // Primary values are kept where present.
        val t = icon.hourly.first().time
        assertEquals(icon.hourly.first().temperature, merged.hourly.first { it.time == t }.temperature, 0.0)
    }

    @Test
    fun `daily sunrise before sunset`() {
        best.daily.forEach { d ->
            val rise = d.sunrise!!
            val set = d.sunset!!
            assertTrue(rise < set)
            assertTrue(d.tempMin <= d.tempMax)
        }
    }

    @Test
    fun `minutely nowcast is 15 minute spaced`() {
        assertTrue(icon.minutely.isNotEmpty())
        assertTrue(icon.minutely.zipWithNext().all { (a, b) -> b.time - a.time == 900_000L })
    }

    @Test
    fun `parses air quality and pollen`() {
        val aq = OpenMeteoSource.parseAirQuality(Fixtures.json("openmeteo_aq.json"))
        assertNotNull(aq.europeanAqi)
        assertTrue(aq.pollen.keys.containsAll(listOf("birch", "grass")))
    }

    @Test
    fun `parses geocoding results`() {
        val places = OpenMeteoSource.parseGeocoding(Fixtures.json("geocoding.json"))
        assertTrue(places.isNotEmpty())
        assertEquals("Berlin", places.first().name)
        assertTrue(places.first().id.startsWith("geo:"))
    }

    @Test
    fun `parses suffixed multi model series`() {
        val models = ComparisonModels.filter { it.first in setOf("icon_d2", "ecmwf_ifs025") }
        val series = OpenMeteoSource.parseModelComparison(Fixtures.json("openmeteo_models.json"), models)
        assertEquals(2, series.size)
        assertEquals(series[0].times.size, series[0].temperature.size)
        // Unknown model is skipped instead of crashing
        val none = OpenMeteoSource.parseModelComparison(Fixtures.json("openmeteo_models.json"), listOf("nope" to "Nope"))
        assertTrue(none.isEmpty())
    }

    @Test
    fun `error responses throw`() {
        val err = dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement("""{"error":true,"reason":"bad"}""")
        val ex = runCatching { OpenMeteoSource.parseForecast(err) }.exceptionOrNull()
        assertEquals("bad", ex?.message)
        assertNull(runCatching { OpenMeteoSource.parseForecast(err) }.getOrNull())
    }
}
