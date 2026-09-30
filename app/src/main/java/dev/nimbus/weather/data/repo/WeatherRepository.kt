/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/WeatherRepository.kt
 * Combines model forecast, station data, alerts and pollen into one result.
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
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.Source
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.model.ComparisonModels
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.ModelForecast
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.StationObservation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class WeatherRepository(
    private val openMeteo: OpenMeteoSource,
    private val brightSky: BrightSkySource,
    private val community: CommunitySource,
    private val pollen: PollenSource,
    private val gauges: dev.nimbus.weather.data.remote.GaugeSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun load(place: Place, settings: Settings, german: Boolean): WeatherData = coroutineScope {
        val lat = place.latitude
        val lon = place.longitude
        val primaryModel = settings.model.openMeteoId
        val inDwdArea = isInDwdArea(lat, lon)

        val primaryJob = async {
            runCatching { openMeteo.forecast(lat, lon, primaryModel) }
        }
        val fillJob = async {
            if (primaryModel == ForecastModel.BEST_MATCH.openMeteoId) null
            else runCatching { openMeteo.forecast(lat, lon, ForecastModel.BEST_MATCH.openMeteoId) }.getOrNull()
        }
        val obsJob = async {
            if (settings.useStationObservations && inDwdArea) runCatching { brightSky.currentObservation(lat, lon) }.getOrNull() else null
        }
        val alertsJob = async {
            if (inDwdArea) runCatching { brightSky.alerts(lat, lon, german) }.getOrDefault(emptyList()) else emptyList()
        }
        val aqJob = async { runCatching { openMeteo.airQuality(lat, lon) }.getOrNull() }
        val communityJob = async { runCatching { community.nearby(lat, lon) }.getOrNull() }
        val pollenJob = async { runCatching { pollen.forecast(lat, lon, inDwdArea) }.getOrNull() }
        // Water levels: federal waterways only (Germany). Optional – never fails the forecast.
        val gaugeJob = async {
            if (gauges == null || !inDwdArea) null
            else kotlinx.coroutines.withTimeoutOrNull(GAUGE_TIMEOUT_MS) {
                runCatching { gauges.nearest(lat, lon) }
                    .onFailure { if (it !is kotlinx.coroutines.CancellationException) android.util.Log.w("Nimbus", "gauge unavailable: $it") }
                    .getOrNull()
            }
        }

        val primaryResult = primaryJob.await()
        val fill = fillJob.await()
        val primary = primaryResult.getOrNull()
        val sources = mutableListOf<Source>()
        val forecast: ModelForecast = when {
            primary != null -> {
                sources += Source(
                    when (settings.model) {
                        ForecastModel.DWD_ICON -> SourceKind.MODEL_DWD_ICON
                        ForecastModel.ECMWF -> SourceKind.MODEL_ECMWF
                        ForecastModel.METEO_FRANCE -> SourceKind.MODEL_METEO_FRANCE
                        ForecastModel.BEST_MATCH -> SourceKind.MODEL_BEST_MATCH
                    },
                )
                if (fill != null) sources += Source(SourceKind.GAP_FILL)
                primary.mergedWith(fill)
            }
            fill != null -> { sources += Source(SourceKind.MODEL_BEST_MATCH); fill }
            else -> throw primaryResult.exceptionOrNull() ?: IllegalStateException("No forecast available")
        }

        val obs = obsJob.await()
        var current = forecast.current ?: forecast.hourly.minByOrNull { kotlin.math.abs(it.time - clock()) }?.let { h ->
            CurrentWeather(h.time, h.temperature, h.apparentTemperature, h.condition, h.isDay, h.humidity, null, h.pressure,
                h.windSpeed, h.windGust, h.windDirection, h.cloudCover, h.visibility, h.uvIndex, h.precipitation)
        } ?: throw IllegalStateException("No current weather")
        if (obs != null && clock() - obs.time < 2 * 3600_000L) {
            current = mergeObservation(current, obs)
            sources += Source(SourceKind.DWD_STATION, obs.stationName)
        }
        val alerts = alertsJob.await()
        if (inDwdArea) sources += Source(SourceKind.DWD_WARNINGS)
        val aq = aqJob.await()
        if (aq != null) sources += Source(SourceKind.CAMS)
        val pollenForecast = pollenJob.await()
        if (pollenForecast?.source == dev.nimbus.weather.data.model.PollenSourceKind.DWD) sources += Source(SourceKind.DWD_POLLEN)
        val gauge = gaugeJob.await()
        if (gauge != null) sources += Source(SourceKind.PEGELONLINE, gauge.name)
        val communityObs = communityJob.await()
        if (communityObs != null) sources += Source(SourceKind.COMMUNITY)

        WeatherData(
            place = place,
            timezone = forecast.timezone,
            utcOffsetSeconds = forecast.utcOffsetSeconds,
            current = current,
            hourly = forecast.hourly,
            daily = forecast.daily,
            minutely = forecast.minutely,
            alerts = alerts,
            airQuality = aq,
            community = communityObs,
            pollen = pollenForecast,
            gauge = gauge,
            sources = sources,
            fetchedAt = clock(),
        )
    }

    suspend fun modelComparison(place: Place): List<ModelSeries> =
        openMeteo.modelComparison(place.latitude, place.longitude, ComparisonModels)

    suspend fun search(query: String, language: String): List<Place> = openMeteo.searchPlaces(query, language)

    companion object {
        /** The first tide fit downloads ~3 MB; later loads take a fraction of a second. */
        private const val GAUGE_TIMEOUT_MS = 30_000L

        /** Rough bounding box of the DWD station network / warning area. */
        fun isInDwdArea(lat: Double, lon: Double) = lat in 47.2..55.1 && lon in 5.8..15.1

        /** Measured values from a nearby DWD station beat modelled ones. */
        fun mergeObservation(model: CurrentWeather, obs: StationObservation): CurrentWeather {
            val t = obs.temperature ?: model.temperature
            val delta = t - model.temperature
            val condition = when {
                obs.condition != null && !(obs.condition == Condition.RAIN && model.condition == Condition.THUNDERSTORM) -> obs.condition
                obs.observedDry && model.condition.isPrecipitation && (obs.precipitation60 ?: 0.0) == 0.0 ->
                    WeatherCodes.derive(obs.cloudCover ?: model.cloudCover, 0.0, t)
                else -> model.condition
            }
            return model.copy(
                temperature = t,
                apparentTemperature = model.apparentTemperature?.plus(delta),
                condition = condition,
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
            )
        }
    }
}
