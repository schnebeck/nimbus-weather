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
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MeasuredValue
import dev.nimbus.weather.data.model.NowValue
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.model.StationNetwork
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.remote.HistorySource
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

    /**
     * How long the radar's newest step stands for now: an answer coming late keeps the last one,
     * the sky does not change its source back and forth with every load.
     */
    const val RADAR_VALID_MS = 30 * 60_000L

    /** What the radar says of now: its newest quarter hour of measured steps. */
    private const val NOW_QUARTER_MS = 15 * 60_000L

    /** [radar] while it still stands for [now]; null: too old or none. */
    fun radarValid(radar: RadarNow?, now: Long): RadarNow? = radar?.takeIf { now - it.time in 0..RADAR_VALID_MS }

    /**
     * What falls now by the radar over the place ([radar], while valid): it beats a station
     * farther off than the radar's pixel, and the model – precipitation without an echo shows the
     * sky, rain is told by its rate, an echo where nothing was seen falls as
     * [WeatherCodes.derive] gives it ([WeatherCodes.forMeasured]). A station within the pixel
     * that saw the sky measures the place itself and stays.
     */
    fun withRadar(current: CurrentWeather, radar: RadarNow?, now: Long): CurrentWeather {
        val r = radarValid(radar, now) ?: return current
        val near = (current.stationDistanceKm ?: Double.MAX_VALUE) < HistorySource.RADAR_PIXEL_KM
        if (near && (current.sky == SkyBasis.OBSERVED || current.sky == SkyBasis.MEASURED_SUNSHINE)) return current
        // a shower of a few minutes in the newest quarter hour falls now, however little its average
        val falling = (RadarAhead.peak(r.steps, r.time, NOW_QUARTER_MS) ?: 0.0) >= dev.nimbus.weather.ui.main.Insights.WET_RATE_MM_H
        val c = WeatherCodes.forMeasured(current.condition, r.mmPerHour, current.cloudCover, current.temperature, byRadar = true, falling) ?: current.condition
        if (c == current.condition) return current
        return current.copy(condition = c, sky = SkyBasis.RADAR, measured = current.measured.filter { it.value != NowValue.SKY })
    }

    /**
     * Today's high and low hold the temperature now: the model's day may top out below what is
     * measured, the day reached at least that. The day running at [now] only; the others as forecast.
     */
    fun dailyWithNow(daily: List<DailyPoint>, current: CurrentWeather, now: Long): List<DailyPoint> {
        val i = daily.indexOfLast { it.date <= now }
        if (i < 0) return daily
        val d = daily[i]
        val end = daily.getOrNull(i + 1)?.date ?: (d.date + 25 * 3_600_000L)
        val t = current.temperature
        if (now >= end || t in d.tempMin..d.tempMax) return daily
        return daily.toMutableList().also { it[i] = d.copy(tempMax = maxOf(d.tempMax, t), tempMin = minOf(d.tempMin, t)) }
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
