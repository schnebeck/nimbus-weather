/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SunnySkyTest.kt
 * An hour of sunshine is not "cloudy": the sunshine of the hour corrects a sky that is cloudy only
 * by its cloud cover (thin high cirrus) – measured (Bright Sky) and forecast (Open-Meteo) alike.
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
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SunnySkyTest {
    @Test fun sunshineMakesTheSkySunnierNeverDarker() {
        assertEquals(Condition.CLEAR, WeatherCodes.withSunshine(Condition.CLOUDY, 60.0))
        assertEquals(Condition.CLEAR, WeatherCodes.withSunshine(Condition.PARTLY_CLOUDY, 45.0))
        assertEquals(Condition.PARTLY_CLOUDY, WeatherCodes.withSunshine(Condition.CLOUDY, 30.0))
        assertEquals(Condition.CLOUDY, WeatherCodes.withSunshine(Condition.CLOUDY, 10.0))
        // never darker: a clear sky without sun (dawn, haze) stays clear
        assertEquals(Condition.CLEAR, WeatherCodes.withSunshine(Condition.CLEAR, 0.0))
        assertEquals(Condition.MOSTLY_CLEAR, WeatherCodes.withSunshine(Condition.MOSTLY_CLEAR, 20.0))
        // no sunshine known: as it is
        assertEquals(Condition.CLOUDY, WeatherCodes.withSunshine(Condition.CLOUDY, null))
    }

    @Test fun precipitationAndThunderStay() {
        for (c in listOf(Condition.RAIN, Condition.SHOWERS, Condition.DRIZZLE, Condition.THUNDERSTORM, Condition.SNOW)) {
            assertEquals(c, WeatherCodes.withSunshine(c, 60.0))
        }
    }

    /** Fog gives way to the sun: 48 minutes of sunshine are no foggy hour. */
    @Test fun fogGivesWayToTheSun() {
        assertEquals(Condition.CLEAR, WeatherCodes.withSunshine(Condition.FOG, 48.0))
        assertEquals(Condition.PARTLY_CLOUDY, WeatherCodes.withSunshine(Condition.FOG, 20.0))
        assertEquals(Condition.FOG, WeatherCodes.withSunshine(Condition.FOG, 10.0))
        assertEquals(Condition.FOG, WeatherCodes.withSunshine(Condition.FOG, null))
    }

    /** The hour of the history's marker: the station reports fog, and 48 minutes of sun. */
    @Test fun aFoggyStationHourWithSunIsSunny() {
        val json = Json.parseToJsonElement(
            """{"weather":[
                {"timestamp":"2026-10-04T11:00:00+02:00","source_id":1,"temperature":12.0,"cloud_cover":90,"sunshine":48.0,"precipitation":0.0,"condition":"fog","icon":"fog"}
               ],"sources":[{"id":1,"observation_type":"historical","station_name":"Hannover","distance":3500}]}""",
        )
        // the station's hour as it ends up in the look-back: brightened by its sunshine (HistorySource.overSpot)
        assertEquals(Condition.CLEAR, HistorySource.overSpot(HistorySource.parseObservations(json).byTime, emptyMap(), emptyMap(), Long.MAX_VALUE).values.single().condition)
    }

    /** The hour of the screenshot: Hannover-Kirchrode, 30 Sept. 13:00 – 87 % cloud, 60 min sun, icon "cloudy". */
    @Test fun brightSkyCloudyHourWithFullSunIsSunny() {
        val json = Json.parseToJsonElement(
            """{"weather":[
                {"timestamp":"2026-09-30T13:00:00+02:00","source_id":1,"temperature":23.0,"cloud_cover":87,"sunshine":60.0,"precipitation":0.0,"condition":"dry","icon":"cloudy"},
                {"timestamp":"2026-10-01T13:00:00+02:00","source_id":1,"temperature":18.0,"cloud_cover":100,"sunshine":0.0,"precipitation":0.0,"condition":"dry","icon":"cloudy"}
               ],"sources":[{"id":1,"observation_type":"historical","station_name":"Hannover-Kirchrode","distance":3500}]}""",
        )
        val obs = HistorySource.overSpot(HistorySource.parseObservations(json).byTime, emptyMap(), emptyMap(), Long.MAX_VALUE).toSortedMap().values.toList()
        assertEquals(Condition.CLEAR, obs[0].condition)
        assertEquals(Condition.CLOUDY, obs[1].condition)
    }

    @Test fun forecastCloudyHourWithFullSunIsSunny() {
        // WMO 3 (overcast) with 3600 s of sunshine, and one without
        val json = Json.parseToJsonElement(
            """{"timezone":"Europe/Berlin","hourly":{"time":[1790931600,1790935200],"temperature_2m":[22.0,21.0],
                "weather_code":[3,3],"sunshine_duration":[3600.0,0.0],"precipitation":[0.0,0.0],"is_day":[1,1]}}""",
        )
        val hours = OpenMeteoSource.parseForecast(json).hourly
        assertEquals(Condition.CLEAR, hours[0].condition)
        assertEquals(Condition.CLOUDY, hours[1].condition)
        val (_, model) = HistorySource.parseModel(json)
        assertEquals(Condition.CLEAR, model[1_790_931_600_000L]!!.condition)
        assertEquals(Condition.CLOUDY, model[1_790_935_200_000L]!!.condition)
    }
}
