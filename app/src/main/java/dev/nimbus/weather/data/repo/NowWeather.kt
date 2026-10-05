/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/NowWeather.kt
 * The weather now: the model's, with what a station measured for the place – each value noted
 * with its station – and the sky brightened by the sunshine as in the hours.
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

package dev.nimbus.weather.data.repo

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MeasuredValue
import dev.nimbus.weather.data.model.NowValue
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.model.StationNetwork
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.remote.Reading
import dev.nimbus.weather.data.remote.StationObservation

/**
 * Now the measurement decides, the forecast the hours to come – both turned into a condition by
 * the same rules: the station's sunshine brightens the sky now as the model's brightens its hours.
 */
object NowWeather {
    /**
     * The measurement standing for the place among the networks' nearest stations ([obs]): not
     * older than two hours, at the place's height and near the model ([StationObservation.forPlace]);
     * a network measuring every 10 minutes before the airports (METAR: every half hour, whole
     * degrees) – among them the nearest.
     */
    fun pickObservation(obs: List<StationObservation>, elevationM: Double?, modelTemperature: Double, now: Long): StationObservation? =
        obs.filter { now - it.time < 2 * 3600_000L }
            .mapNotNull { it.forPlace(elevationM, modelTemperature) }
            .minWithOrNull(compareBy({ it.network == StationNetwork.METAR }, { it.distanceKm }))

    /** Measured values from a nearby station beat modelled ones ([obs] already [StationObservation.forPlace]). */
    fun mergeObservation(model: CurrentWeather, obs: StationObservation): CurrentWeather {
        val t = obs.temperature ?: model.temperature
        val delta = t - model.temperature
        val observed = when {
            obs.condition != null && !(obs.condition == Condition.RAIN && model.condition == Condition.THUNDERSTORM) -> obs.condition
            obs.observedDry && model.condition.isPrecipitation && (obs.precipitation60 ?: 0.0) == 0.0 ->
                WeatherCodes.derive(obs.cloudCover ?: model.cloudCover, 0.0, t)
            else -> null
        }
        // the sunshine measured (by day: at night there is none to measure) decides the sky – the
        // model's sunshine is not asked then
        val sun = obs.sunshine?.takeIf { model.isDay }
        val base = observed ?: model.condition
        val sky = when {
            sun != null && (observed == null || WeatherCodes.withSunshine(observed, sun) != observed) -> SkyBasis.MEASURED_SUNSHINE
            observed != null -> SkyBasis.OBSERVED
            else -> SkyBasis.MODEL
        }
        fun at(v: NowValue, r: Reading, value: Any?) = value?.let { obs.siteOf(r).let { s -> MeasuredValue(v, s.name, s.distanceKm) } }
        return model.copy(
            temperature = t,
            apparentTemperature = model.apparentTemperature?.plus(delta),
            condition = sun?.let { WeatherCodes.withSunshine(base, it) } ?: base,
            humidity = obs.humidity ?: model.humidity,
            dewPoint = obs.dewPoint ?: model.dewPoint,
            pressure = obs.pressure ?: model.pressure,
            windSpeed = obs.windSpeed ?: model.windSpeed,
            windGust = obs.windGust ?: model.windGust,
            windDirection = obs.windDirection ?: model.windDirection,
            visibility = obs.visibility ?: model.visibility,
            visibilityMeasured = obs.visibility != null,
            stationName = obs.stationName,
            stationDistanceKm = obs.distanceKm,
            stationNetwork = obs.network,
            measured = listOfNotNull(
                at(NowValue.TEMPERATURE, Reading.TEMPERATURE, obs.temperature),
                at(NowValue.HUMIDITY, Reading.HUMIDITY, obs.humidity),
                at(NowValue.DEW_POINT, Reading.DEW_POINT, obs.dewPoint),
                at(NowValue.PRESSURE, Reading.PRESSURE, obs.pressure),
                at(NowValue.WIND, Reading.WIND_SPEED, obs.windSpeed),
                at(NowValue.GUSTS, Reading.WIND_GUST, obs.windGust),
                at(NowValue.VISIBILITY, Reading.VISIBILITY, obs.visibility),
                when (sky) {
                    SkyBasis.MEASURED_SUNSHINE -> at(NowValue.SKY, Reading.SUNSHINE, sun)
                    SkyBasis.OBSERVED -> at(NowValue.SKY, Reading.CONDITION, observed)
                    else -> null
                },
            ),
            sky = sky,
        )
    }

    /** The hour running at [now]: its values cover the hour before their time stamp. */
    fun runningHour(hourly: List<HourlyPoint>, now: Long): HourlyPoint? =
        hourly.firstOrNull { it.time > now }?.takeIf { it.time - now <= 3_600_000L }

    /**
     * Without a measured sunshine: the sky now brightened by the model's sunshine in the hour
     * running now ([hourly]) – as that hour is drawn. Thin high cloud counts as cloud cover: an
     * hour of full sun under it read "overcast" now, "sunny" in the hour.
     */
    fun withModelSunshine(current: CurrentWeather, hourly: List<HourlyPoint>, now: Long): CurrentWeather {
        if (current.sky != SkyBasis.MODEL) return current
        val sun = runningHour(hourly, now)?.sunshine ?: return current
        val c = WeatherCodes.withSunshine(current.condition, sun)
        return if (c == current.condition) current else current.copy(condition = c, sky = SkyBasis.MODEL_SUNSHINE)
    }
}
