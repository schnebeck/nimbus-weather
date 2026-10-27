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

    /**
     * The satellite's sunshine over the place itself in its newest hour ([minutes]), while that
     * hour stands for now: ended at most [SATELLITE_VALID_MS] before [now]. [sunHigh]: the sun stood
     * high enough in it that no sunshine means cloud, not a sun too low to count.
     */
    data class SatelliteNow(val minutes: Double, val hourEnd: Long, val sunHigh: Boolean)

    /** [SatelliteNow] from the satellite's hours at [lat]/[lon] ([dev.nimbus.weather.data.remote.SatelliteSunshine]); null: none fresh enough. */
    fun satelliteNow(minutes: Map<Long, Double>, lat: Double, lon: Double, now: Long): SatelliteNow? {
        val (end, m) = minutes.entries.filter { it.key <= now && now - it.key <= SATELLITE_VALID_MS }.maxByOrNull { it.key } ?: return null
        return SatelliteNow(m, end, dev.nimbus.weather.data.remote.SunHour(end, lat, lon).elevation >= SATELLITE_DARK_ELEVATION)
    }

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
        // the sky measured overcast: no sun now, whatever the model reckons (its fog stays). The
        // ceilometer misses thin high cloud – a sky it measures clear may still be veiled, so only
        // the overcast one is taken.
        val overcast = observed == null && WeatherCodes.isSky(model.condition) && model.condition != Condition.FOG &&
            (obs.cloudCover ?: 0.0) >= OVERCAST_PERCENT
        val measuredSky = if (overcast) Condition.CLOUDY else observed
        // the sunshine measured (by day: at night there is none to measure) decides the sky – the
        // model's sunshine is not asked then
        val sun = obs.sunshine?.takeIf { model.isDay }
        val base = measuredSky ?: model.condition
        val sky = when {
            sun != null && (measuredSky == null || WeatherCodes.withSunshine(measuredSky, sun) != measuredSky) -> SkyBasis.MEASURED_SUNSHINE
            observed != null -> SkyBasis.OBSERVED
            overcast -> SkyBasis.MEASURED_CLOUD
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
                    SkyBasis.MEASURED_CLOUD -> at(NowValue.SKY, Reading.CLOUD_COVER, obs.cloudCover)
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

    /**
     * How long the satellite's newest hour stands for now after its end: the next one comes 20 to
     * 25 minutes after its own end – till then the hour before stands, or the sky would fall back
     * to the station for those minutes of every hour.
     */
    const val SATELLITE_VALID_MS = 90 * 60_000L

    /** The satellite's pixel (km): a station nearer than this measures the place itself. */
    const val SATELLITE_PIXEL_KM = 2.5

    /** The sun's mean height (degrees) from which no sunshine seen means cloud: below it a clear sky may count none. */
    const val SATELLITE_DARK_ELEVATION = 10.0

    /** Cloud cover (%) from which a station's sky is overcast: 7 eighths. */
    const val OVERCAST_PERCENT = 87.5

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
        if (near && (current.sky == SkyBasis.OBSERVED || current.sky == SkyBasis.MEASURED_SUNSHINE || current.sky == SkyBasis.MEASURED_CLOUD)) return current
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
     * What is measured over the place itself goes before a station's: the sunshine the satellite
     * saw over the place ([satellite]) decides the sky – by the hours' bounds: from 45 minutes
     * sunny, from 15 partly cloudy, (almost) none under a high sun clouded – before the station's
     * sunshine, cloud cover or fog and before the model. A station within the satellite's pixel
     * measures the place itself and stays. Precipitation and thunder stay as they are (the radar's
     * over the place, [withRadar]).
     */
    fun withSatellite(current: CurrentWeather, satellite: SatelliteNow?): CurrentWeather {
        val sat = satellite ?: return current
        if (!current.isDay || !WeatherCodes.isSky(current.condition)) return current
        // the station that measured the sky (its value may come from another than the main one)
        val skyKm = current.measured.firstOrNull { it.value == NowValue.SKY }?.distanceKm ?: Double.MAX_VALUE
        if (skyKm < SATELLITE_PIXEL_KM && current.sky in setOf(SkyBasis.OBSERVED, SkyBasis.MEASURED_SUNSHINE, SkyBasis.MEASURED_CLOUD)) return current
        val c = when {
            sat.minutes >= WeatherCodes.SUNNY_MINUTES -> Condition.CLEAR
            sat.minutes >= WeatherCodes.PARTLY_SUNNY_MINUTES -> Condition.PARTLY_CLOUDY
            // no sun seen: clouded where the sun stood high – fog stays fog; a low sun tells nothing
            sat.sunHigh && current.condition != Condition.FOG -> Condition.CLOUDY
            else -> return current
        }
        return current.copy(condition = c, sky = SkyBasis.SATELLITE, measured = current.measured.filter { it.value != NowValue.SKY })
    }

    /**
     * Nothing measured of the sky: now as the hour running now ([hourly]) where MOSMIX was weighed
     * into it ([SkyBlend]) – the sky the model has for this very moment would undo what MOSMIX
     * knows better. Precipitation and thunder now stay the model's, as in the hours.
     */
    fun withSkyOfHour(current: CurrentWeather, hourly: List<HourlyPoint>, now: Long): CurrentWeather {
        if (current.sky != SkyBasis.MODEL || !WeatherCodes.isSky(current.condition)) return current
        val c = runningHour(hourly, now)?.condition?.takeIf(WeatherCodes::isSky) ?: return current
        return current.copy(condition = c, sky = SkyBasis.MOSMIX)
    }

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
