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
import dev.nimbus.weather.data.model.DataPart
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
    private val bathing: dev.nimbus.weather.data.remote.BathingSource? = null,
    /** The extras get this long in all (from the start of the load); later ones keep their last values. */
    private val extrasDeadlineMs: Long = 60_000L,
) {

    /**
     * The weather of [place] in two steps: [onCore] gets the forecast with station and warnings as
     * soon as they are there – the page shows at once –, the result adds the extras (air quality,
     * pollen, citizen sensors, gauges, bathing waters, flood alerts of the states), the slow
     * sources that kept a new place blank for up to half a minute. Until they arrive, the core
     * carries the extras of [previous] (the same place's last data), so a refresh does not empty
     * the cards. Cards switched off load nothing (citizen sensors, gauges, bathing waters).
     */
    // Off the main thread: besides the requests, the parsing and selection (e.g. ~1,600 LHP gauges)
    // take noticeable CPU time – on the main thread they froze the UI.
    suspend fun load(
        place: Place, settings: Settings, german: Boolean,
        previous: WeatherData? = null,
        /** False (the hourly background refresh): the core only – the extras keep [previous]'s, marked older. */
        extras: Boolean = true,
        onCore: (WeatherData) -> Unit = {},
    ): WeatherData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { coroutineScope {
        val lat = place.latitude
        val lon = place.longitude
        val primaryModel = settings.model.openMeteoId
        val inDwdArea = isInDwdArea(lat, lon)

        val started = System.nanoTime()
        val primaryJob = async {
            runCatching { openMeteo.forecast(lat, lon, primaryModel) }
        }
        // DWD ICON: chance of precipitation that matches the ICON-D2 amounts (see iconD2Chance)
        val d2ChanceJob = async {
            if (settings.model != ForecastModel.DWD_ICON) emptyMap()
            else runCatching { openMeteo.iconD2Chance(lat, lon) }.getOrDefault(emptyMap())
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
        // The extras: each an answer (its value may be "nothing here") – or null when it failed or
        // took too long; then the place's last value stays (a refresh does not empty a card)
        val aqJob = async { if (!extras) null else fetch(EXTRA_TIMEOUT_MS, "air quality") { openMeteo.airQuality(lat, lon) } }
        val communityJob = async {
            if (!extras) null
            else if (!settings.shows(dev.nimbus.weather.data.model.WeatherCard.COMMUNITY)) Fetched(null)
            else fetch(COMMUNITY_TIMEOUT_MS, "citizen sensors") { community.nearby(lat, lon) }
        }
        val pollenJob = async { if (!extras) null else fetch(EXTRA_TIMEOUT_MS, "pollen") { pollen.forecast(lat, lon, inDwdArea) } }
        // Water levels and state flood alerts (Germany). Optional – never fail the forecast.
        val gaugeJob = async {
            if (!extras) null
            else if (gauges == null || !inDwdArea || !settings.shows(dev.nimbus.weather.data.model.WeatherCard.GAUGES)) Fetched(emptyList())
            else fetch(GAUGE_TIMEOUT_MS, "gauges") { gauges.nearby(lat, lon) }
        }
        // Bathing waters (EEA, Europe-wide) – only when the card is shown; optional like the gauges
        val bathingJob = async {
            if (!extras) null
            else if (bathing == null || !settings.shows(dev.nimbus.weather.data.model.WeatherCard.BATHING)) Fetched(emptyList())
            else fetch(BATHING_TIMEOUT_MS, "bathing waters") { bathing.nearby(lat, lon, settings.bathingRadiusKm, settings.bathingFavorites) }
        }
        val floodJob = async {
            if (!extras) null
            else if (gauges?.lhp == null || !inDwdArea) Fetched(emptyList())
            else fetch(10_000L, "flood alerts") { gauges.lhp.alerts(lat, lon) }
        }

        val d2Chance = d2ChanceJob.await()
        val primaryResult = primaryJob.await().map { dev.nimbus.weather.data.remote.OpenMeteoSource.withChance(it, d2Chance) }
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
        // The core: the page can show
        val extraKinds = setOf(SourceKind.CAMS, SourceKind.DWD_POLLEN, SourceKind.BATHING, SourceKind.GAUGES, SourceKind.LHP_ALERTS, SourceKind.COMMUNITY)
        val keep = previous?.takeIf { it.place.latitude == lat && it.place.longitude == lon }
        fun data(
            aq: dev.nimbus.weather.data.model.AirQuality?, community: dev.nimbus.weather.data.model.CommunityObservation?,
            pollen: dev.nimbus.weather.data.model.PollenForecast?, gauges: List<dev.nimbus.weather.data.model.GaugeInfo>,
            bathing: List<dev.nimbus.weather.data.model.BathingSite>, flood: List<dev.nimbus.weather.data.model.WeatherAlert>,
            extraSources: List<Source>, stale: Set<DataPart>,
        ) = WeatherData(
            place = place,
            timezone = forecast.timezone,
            utcOffsetSeconds = forecast.utcOffsetSeconds,
            current = current,
            hourly = forecast.hourly,
            daily = forecast.daily,
            minutely = forecast.minutely,
            alerts = alerts + flood,
            airQuality = aq,
            community = community,
            pollen = pollen,
            gauges = gauges,
            bathing = bathing,
            sources = sources + extraSources,
            fetchedAt = clock(),
            stale = stale,
        )
        onCore(
            data(
                keep?.airQuality, keep?.community, keep?.pollen, keep?.gauges.orEmpty(), keep?.bathing.orEmpty(),
                keep?.alerts.orEmpty().filter { it.source == LHP }, keep?.sources.orEmpty().filter { it.kind in extraKinds },
                DataPart.entries.toSet() - DataPart.FORECAST,
            ),
        )

        // The extras – a failed one, or one still out when the time is up, keeps the place's last value
        val stale = mutableSetOf<DataPart>()
        suspend fun <T> got(job: kotlinx.coroutines.Deferred<Fetched<T>?>, part: DataPart, last: T): T {
            val left = extrasDeadlineMs - (System.nanoTime() - started) / 1_000_000
            val r = if (left <= 0 && !job.isCompleted) null else kotlinx.coroutines.withTimeoutOrNull(left.coerceAtLeast(1)) { job.await() }
            if (r == null) { job.cancel(); stale += part; return last }
            return r.value
        }
        val extra = mutableListOf<Source>()
        val aq = got(aqJob, DataPart.AIR_QUALITY, keep?.airQuality)
        if (aq != null) extra += Source(SourceKind.CAMS)
        val pollenForecast = got(pollenJob, DataPart.POLLEN, keep?.pollen)
        if (pollenForecast?.source == dev.nimbus.weather.data.model.PollenSourceKind.DWD) extra += Source(SourceKind.DWD_POLLEN)
        val gaugeList = got(gaugeJob, DataPart.GAUGES, keep?.gauges.orEmpty())
        val bathingList = got(bathingJob, DataPart.BATHING, keep?.bathing.orEmpty())
        if (bathingList.isNotEmpty()) extra += Source(SourceKind.BATHING, bathingList.mapNotNull { it.provider }.distinct().joinToString(","))
        if (gaugeList.isNotEmpty()) extra += Source(SourceKind.GAUGES, gaugeList.map { it.provider }.distinct().joinToString(",") { it.name })
        val floodAlerts = got(floodJob, DataPart.FLOOD, keep?.alerts.orEmpty().filter { a -> a.source == LHP })
        if (floodAlerts.isNotEmpty()) extra += Source(SourceKind.LHP_ALERTS)
        val communityObs = got(communityJob, DataPart.COMMUNITY, keep?.community)
        if (communityObs != null) extra += Source(SourceKind.COMMUNITY)
        // a part without a last value has nothing old to show: not stale, simply not there
        data(aq, communityObs, pollenForecast, gaugeList, bathingList, floodAlerts, extra, stale.filterTo(mutableSetOf()) { keep != null })
    } }

    suspend fun modelComparison(place: Place): List<ModelSeries> =
        openMeteo.modelComparison(place.latitude, place.longitude, ComparisonModels)

    suspend fun search(query: String, language: String): List<Place> = openMeteo.searchPlaces(query, language)

    /** An extra source's answer; [value] may be "nothing here" (null, empty). */
    private class Fetched<out T>(val value: T)

    /** [f]'s answer, or null when it failed or took longer than [timeoutMs] (logged as [what]). */
    private suspend fun <T> fetch(timeoutMs: Long, what: String, f: suspend () -> T): Fetched<T>? =
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            runCatching { Fetched(f()) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; android.util.Log.w("Nimbus", "$what unavailable: $it") }
                .getOrNull()
        }.also { if (it == null) android.util.Log.w("Nimbus", "$what: no answer, the last one stays") }

    companion object {
        /** The first tide fit downloads ~3 MB; later loads take a fraction of a second. */
        private const val GAUGE_TIMEOUT_MS = 40_000L
        /** Air quality and pollen: small requests, but the page must not wait forever for them. */
        private const val EXTRA_TIMEOUT_MS = 20_000L
        /** sensor.community is often slow; two areas are asked one after the other. */
        private const val COMMUNITY_TIMEOUT_MS = 15_000L
        /** [WeatherAlert.source] of the states' flood alerts. */
        private const val LHP = "LHP"
        private const val BATHING_TIMEOUT_MS = 30_000L

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
