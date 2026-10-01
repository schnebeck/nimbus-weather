/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LogicTest.kt
 * Tests for the card logic in Insights and the unit formatting.
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
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.model.WindUnit
import dev.nimbus.weather.ui.background.Season
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.util.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    @Test
    fun `chance of precipitation labels`() {
        assertNull(Insights.chanceLabel(null))
        assertEquals(0, Insights.chanceLabel(0.0))
        assertEquals(0, Insights.chanceLabel(4.0))
        assertEquals(10, Insights.chanceLabel(5.0))
        assertEquals(40, Insights.chanceLabel(38.0))
        assertEquals(100, Insights.chanceLabel(100.0))
    }

    @Test
    fun `map labels use the app language`() {
        val style = dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement(
            """{"layers":[
              {"id":"place","layout":{"text-field":["coalesce",["get","name_en"],["get","name"]]}},
              {"id":"road","layout":{"text-field":["to-string",["get","ref"]]}},
              {"id":"water"}
            ]}""",
        ) as kotlinx.serialization.json.JsonObject
        val out = dev.nimbus.weather.ui.radar.MapStyle.localize(style, "de").toString()
        assertTrue(out.contains("name:de"))
        assertTrue(!out.contains("name_en"))
        assertTrue(out.contains("\"ref\""))
    }

    @Test
    fun `radar layers are embedded below the first label layer`() {
        val style = dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement(
            """{"sources":{"osm":{"type":"vector"}},"layers":[{"id":"bg","type":"background"},{"id":"label","type":"symbol"}]}""",
        ) as kotlinx.serialization.json.JsonObject
        val out = dev.nimbus.weather.ui.radar.MapStyle.withRasters(
            style, listOf(dev.nimbus.weather.ui.radar.MapStyle.Raster("dwd0", "https://x/{bbox-epsg-3857}", 10, 0.85f)),
        )
        val ids = (out["layers"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonObject)["id"].toString().trim('"') }
        assertEquals(listOf("bg", "dwd0", "label"), ids)
        assertTrue((out["sources"] as kotlinx.serialization.json.JsonObject).containsKey("osm"))
        assertTrue(out.toString().contains("\"tileSize\":512"))
    }

    @Test
    fun `wmo codes`() {
        assertEquals(Condition.CLEAR, WeatherCodes.fromWmo(0))
        assertEquals(Condition.FOG, WeatherCodes.fromWmo(45))
        assertEquals(Condition.HEAVY_RAIN, WeatherCodes.fromWmo(65))
        assertEquals(Condition.SNOW, WeatherCodes.fromWmo(71))
        assertEquals(Condition.THUNDERSTORM, WeatherCodes.fromWmo(99))
        assertEquals(Condition.CLOUDY, WeatherCodes.fromWmo(null))
    }

    @Test
    fun `derived conditions`() {
        assertEquals(Condition.SNOW, WeatherCodes.derive(100.0, 1.0, -2.0))
        assertEquals(Condition.SLEET, WeatherCodes.derive(100.0, 1.0, 1.0))
        assertEquals(Condition.DRIZZLE, WeatherCodes.derive(100.0, 0.2, 10.0))
        assertEquals(Condition.CLEAR, WeatherCodes.derive(5.0, 0.0, 10.0))
        assertEquals(Condition.CLOUDY, WeatherCodes.derive(95.0, 0.0, 10.0))
    }

    @Test
    fun `temperature formatting`() {
        assertEquals("0°", Units.temp(-0.4, TemperatureUnit.CELSIUS))
        assertEquals("-3°", Units.temp(-2.6, TemperatureUnit.CELSIUS))
        assertEquals("68°", Units.temp(20.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("–", Units.temp(null, TemperatureUnit.CELSIUS))
    }

    @Test
    fun `wind units and beaufort`() {
        assertEquals(0, Units.beaufort(0.5))
        assertEquals(4, Units.beaufort(25.0))
        assertEquals(8, Units.beaufort(70.0))
        assertEquals(12, Units.beaufort(130.0))
        assertEquals(10.0, Units.windValue(36.0, WindUnit.MS), 0.001)
    }

    @Test
    fun `compass sectors`() {
        assertEquals(0, Units.compassIndex(0.0))
        assertEquals(0, Units.compassIndex(350.0))
        assertEquals(2, Units.compassIndex(90.0))
        assertEquals(6, Units.compassIndex(270.0))
        assertEquals(7, Units.compassIndex(-45.0))
    }

    private val now = 1_800_000_000_000L
    private fun mins(vararg mm: Double) = mm.mapIndexed { i, p -> MinutelyPoint(now + i * 900_000L, p) }

    @Test
    fun `nowcast summaries`() {
        assertEquals(Insights.Nowcast.Dry, Insights.nowcast(mins(0.0, 0.0, 0.0), now))
        assertEquals(Insights.Nowcast.StartsIn(30), Insights.nowcast(mins(0.0, 0.0, 0.4, 0.8), now))
        assertEquals(Insights.Nowcast.StopsIn(45), Insights.nowcast(mins(0.5, 0.3, 0.1, 0.0), now))
        assertEquals(Insights.Nowcast.Continues, Insights.nowcast(mins(0.5, 0.5), now))
        // measured rain overrides a dry first model interval
        assertEquals(Insights.Nowcast.StopsIn(15), Insights.nowcast(mins(0.0, 0.0, 0.0), now, rainingNow = true))
        assertEquals(Insights.Nowcast.Continues, Insights.nowcast(mins(0.0, 0.4, 0.4), now, rainingNow = true))
    }

    @Test
    fun `chance shown as below 10 when the main run still delivers an amount`() {
        assertEquals("<10", Insights.chanceText(0.0, 0.5))      // Groß Düngen, Saturday 22:00: 0.5 mm at 0 %
        assertEquals("0", Insights.chanceText(0.0, 0.0))
        assertEquals("0", Insights.chanceText(3.0, 0.05))      // below the measurable amount
        assertEquals("20", Insights.chanceText(20.0, 0.5))
        assertEquals(null, Insights.chanceText(null, 0.5))
    }

    @Test
    fun `precipitation notice - kind, strength and two-hour horizon`() {
        val showers = listOf(hour(1, Condition.RAIN), hour(2, Condition.RAIN), hour(3, Condition.RAIN))
        // light rain (0.4 mm/15 min = 1.6 mm/h) starting in 30 min
        val light = Insights.precipNotice(mins(0.0, 0.0, 0.4, 0.4), showers, Condition.CLOUDY, now, false)!!
        assertEquals(Insights.PrecipKind.RAIN, light.kind)
        assertEquals(Insights.Intensity.LIGHT, light.intensity)
        assertEquals(Insights.Nowcast.StartsIn(30), light.state)
        // heavy (3 mm/15 min = 12 mm/h)
        assertEquals(Insights.Intensity.HEAVY, Insights.precipNotice(mins(0.0, 3.0, 1.0), showers, Condition.CLOUDY, now, false)!!.intensity)
        // thunderstorm hour at the start
        val storm = listOf(hour(1, Condition.THUNDERSTORM))
        assertEquals(Insights.PrecipKind.THUNDERSTORM, Insights.precipNotice(mins(0.0, 1.0), storm, Condition.CLOUDY, now, false)!!.kind)
        val hail = listOf(hour(1, Condition.THUNDERSTORM).copy(hail = true))
        assertTrue(dev.nimbus.weather.data.model.WeatherCodes.isHail(96) && dev.nimbus.weather.data.model.WeatherCodes.isHail(99) && !dev.nimbus.weather.data.model.WeatherCodes.isHail(95))
        assertEquals(Insights.PrecipKind.HAIL, Insights.precipNotice(mins(0.0, 1.0), hail, Condition.CLOUDY, now, false)!!.kind)
        // snow falling now, ends in 30 min
        val snow = Insights.precipNotice(mins(0.2, 0.2, 0.0), showers, Condition.SNOW, now, true)!!
        assertEquals(Insights.PrecipKind.SNOW, snow.kind)
        assertEquals(Insights.Nowcast.StopsIn(30), snow.state)
        assertTrue(snow.now)
        assertTrue(!light.now)
        // "until about 10:59" -> 11:00: the end is the start of the first dry interval, on 5 minutes
        val at1049 = now + 4 * 60_000L + 30_000L                     // points at :45, :00, :15; now = :49:30
        val ends = Insights.precipNotice(mins(0.3, 0.0, 0.0), showers, Condition.RAIN, at1049, true)!!
        assertEquals(now + 900_000L, ends.at)
        assertEquals(now + 1_800_000L, light.at)                       // starts at :30
        assertEquals(now + 2 * 3_600_000L, Insights.precipNotice(mins(0.5, 0.5, 0.5), showers, Condition.RAIN, now, true)!!.at)
        // rain only after 2 h 15 min: no notice
        val late = DoubleArray(9) { 0.0 } + doubleArrayOf(1.0, 1.0)
        assertEquals(null, Insights.precipNotice(mins(*late), showers, Condition.CLOUDY, now, false))
        // dry
        assertEquals(null, Insights.precipNotice(mins(0.0, 0.0, 0.0), showers, Condition.CLOUDY, now, false))
    }

    private fun hour(i: Int, c: Condition, gust: Double? = null, p: Double? = null) =
        HourlyPoint(now + i * 3_600_000L, 10.0, condition = c, isDay = true, windGust = gust, pressure = p)

    @Test
    fun `next condition change`() {
        val hours = listOf(hour(0, Condition.CLEAR), hour(1, Condition.MOSTLY_CLEAR), hour(2, Condition.RAIN))
        val ch = Insights.nextChange(hours, Condition.CLEAR)
        assertEquals(Condition.RAIN, ch.condition)
        assertEquals(now + 2 * 3_600_000L, ch.time)
        assertNull(Insights.nextChange(hours.take(2), Condition.CLEAR).time)
    }

    @Test
    fun `pressure trend and gusts`() {
        val rising = (0..4).map { hour(it, Condition.CLEAR, gust = 20.0 + it * 10, p = 1000.0 + it) }
        assertEquals(Insights.Trend.RISING, Insights.pressureTrend(rising))
        assertEquals(60.0, Insights.maxGust(rising)!!, 0.0)
    }

    @Test
    fun `temperature colour scale is monotonic in hue range and clamps`() {
        assertEquals(Insights.temperatureColor(-40.0), Insights.temperatureColor(-15.0))
        assertEquals(Insights.temperatureColor(50.0), Insights.temperatureColor(38.0))
    }

    @Test
    fun `seasons incl southern hemisphere`() {
        assertEquals(Season.AUTUMN, SkyScene.seasonOf(LocalDate.of(2026, 10, 15), false).first)
        assertEquals(Season.SPRING, SkyScene.seasonOf(LocalDate.of(2026, 10, 15), true).first)
        assertEquals(Season.WINTER, SkyScene.seasonOf(LocalDate.of(2026, 1, 15), false).first)
        val early = SkyScene.seasonOf(LocalDate.of(2026, 9, 5), false).second
        val late = SkyScene.seasonOf(LocalDate.of(2026, 11, 25), false).second
        assertTrue(early < 0.1f && late > 0.9f)
    }

    @Test
    fun `seasonal ambient only when dry`() {
        val autumn = SkyScene(Condition.PARTLY_CLOUDY, 1f, 0f, 0.5f, season = Season.AUTUMN)
        assertEquals(dev.nimbus.weather.ui.background.Ambient.LEAVES, autumn.ambient)
        assertEquals(dev.nimbus.weather.ui.background.Ambient.NONE, autumn.copy(condition = Condition.RAIN).ambient)
        val summerNight = SkyScene(Condition.CLEAR, 0f, 0f, 0f, season = Season.SUMMER, temperature = 18.0)
        assertEquals(dev.nimbus.weather.ui.background.Ambient.FIREFLIES, summerNight.ambient)
        val frost = SkyScene(Condition.CLEAR, 1f, 0f, 0f, season = Season.WINTER, temperature = -6.0)
        assertEquals(dev.nimbus.weather.ui.background.Ambient.ICE_CRYSTALS, frost.ambient)
        assertEquals(0f, frost.copy(condition = Condition.SNOW, pollen = 0.8f).visiblePollen)
    }
}
