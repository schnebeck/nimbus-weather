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
import dev.nimbus.weather.data.model.modelFor
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class WeatherRepository(
    private val openMeteo: OpenMeteoSource,
    private val brightSky: BrightSkySource,
    private val community: CommunitySource,
    private val pollen: PollenSource,
    private val gauges: dev.nimbus.weather.data.remote.GaugeSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val bathing: dev.nimbus.weather.data.remote.BathingSource? = null,
    /** The extras get this long in all (from the start of the load); later ones keep their last values. */
    private val extrasDeadlineMs: Long = SOURCE_TIMEOUT_MS,
) {

    /**
     * The weather of [place] step by step: [onProgress] gets the forecast with station and warnings as
     * soon as they are there – the page shows at once –, then again with each extra as it arrives
     * (its card turns current); the result holds them all: the extras (air quality,
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
        /** False (the hourly background refresh): the core only – the extras keep [previous]'s with their own times. */
        extras: Boolean = true,
        /**
         * The extras to load anew – the ones past their shelf life ([Freshness.dueParts]); the
         * others keep [previous]'s values and times, nothing is asked for them.
         */
        refresh: Set<DataPart> = DataPart.entries.toSet(),
        /** Asked for anew (forced reload): nothing from a store – see [dev.nimbus.weather.data.remote.FreshData]. */
        fresh: Boolean = false,
        onProgress: (WeatherData) -> Unit = {},
    ): WeatherData = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.Default + (if (fresh) dev.nimbus.weather.data.remote.FreshData else kotlin.coroutines.EmptyCoroutineContext),
    ) { coroutineScope {
        val lat = place.latitude
        val lon = place.longitude
        // the place's own model, else the one of the settings
        val model = settings.modelFor(place)
        val primaryModel = model.openMeteoId
        val inDwdArea = isInDwdArea(lat, lon)

        val started = System.nanoTime()
        val primaryJob = async {
            runCatching { openMeteo.forecast(lat, lon, primaryModel) }
        }
        // DWD ICON: chance of precipitation that matches the ICON-D2 amounts (see iconD2Chance)
        val d2ChanceJob = async {
            if (model != ForecastModel.DWD_ICON) emptyMap()
            else runCatching { openMeteo.iconD2Chance(lat, lon) }.getOrDefault(emptyMap())
        }
        val fillJob = async {
            if (primaryModel == ForecastModel.BEST_MATCH.openMeteoId) null
            else runCatching { openMeteo.forecast(lat, lon, ForecastModel.BEST_MATCH.openMeteoId) }.getOrNull()
        }
        // which single model Open-Meteo's best match takes, hour by hour (for the sources: named
        // with its grid) – it fills the chosen model's gaps, or it is the chosen one
        val partsJob = async { runCatching { openMeteo.bestMatchModels(lat, lon) }.getOrDefault(emptyMap()) }
        val obsJob = async {
            if (settings.useStationObservations && inDwdArea) runCatching { brightSky.currentObservation(lat, lon) }.getOrNull() else null
        }
        val alertsJob = async {
            if (inDwdArea) runCatching { brightSky.alerts(lat, lon, german) }.getOrDefault(emptyList()) else emptyList()
        }
        // The extras: each an answer (its value may be "nothing here") – or null when it failed or
        // took too long; then the place's last value stays (a refresh does not empty a card).
        // Only the ones asked for: a part still current keeps its value and is not asked again.
        val wanted = if (extras) refresh else emptySet()
        val aqJob = async { if (DataPart.AIR_QUALITY !in wanted) null else fetch(SOURCE_TIMEOUT_MS, "air quality") { openMeteo.airQuality(lat, lon) } }
        val communityJob = async {
            if (DataPart.COMMUNITY !in wanted) null
            else if (!settings.shows(dev.nimbus.weather.data.model.WeatherCard.COMMUNITY)) Fetched(null)
            else {
                // the sensors at the place's height: the forecast knows it
                val elevation = primaryJob.await().getOrNull()?.elevation ?: fillJob.await()?.elevation
                fetch(SOURCE_TIMEOUT_MS, "citizen sensors") { community.nearby(lat, lon, elevation) }
            }
        }
        val pollenJob = async { if (DataPart.POLLEN !in wanted) null else fetch(SOURCE_TIMEOUT_MS, "pollen") { pollen.forecast(lat, lon, inDwdArea) } }
        // Water levels and state flood alerts (Germany). Optional – never fail the forecast.
        val gaugeJob = async {
            if (DataPart.GAUGES !in wanted) null
            else if (gauges == null || !inDwdArea || !settings.shows(dev.nimbus.weather.data.model.WeatherCard.GAUGES)) Fetched(emptyList())
            else fetch(SOURCE_TIMEOUT_MS, "gauges") { gauges.nearby(lat, lon) }
        }
        // Bathing waters (EEA, Europe-wide) – only when the card is shown; optional like the gauges
        val bathingJob = async {
            if (DataPart.BATHING !in wanted) null
            else if (bathing == null || !settings.shows(dev.nimbus.weather.data.model.WeatherCard.BATHING)) Fetched(emptyList())
            else fetch(SOURCE_TIMEOUT_MS, "bathing waters") { bathing.nearby(lat, lon, settings.bathingRadiusKm, settings.bathingFavorites) }
        }
        val floodJob = async {
            if (DataPart.FLOOD !in wanted) null
            else if (gauges?.lhp == null || !inDwdArea) Fetched(emptyList())
            else fetch(SOURCE_TIMEOUT_MS, "flood alerts") { gauges.lhp.alerts(lat, lon) }
        }

        val d2Chance = d2ChanceJob.await()
        val primaryResult = primaryJob.await().map { dev.nimbus.weather.data.remote.OpenMeteoSource.withChance(it, d2Chance) }
        val fill = fillJob.await()
        val primary = primaryResult.getOrNull()
        // the best match's single models: not worth holding the forecast back for long
        val parts = kotlinx.coroutines.withTimeoutOrNull(PARTS_WAIT_MS) { partsJob.await() } ?: emptyMap<Long, dev.nimbus.weather.data.model.ModelPart>().also { partsJob.cancel() }
        val nowHour = clock() / HOUR_MS * HOUR_MS
        // the single model at [t] – in the hours where the best match blends two, the next one
        fun partAt(t: Long) = parts.entries.firstOrNull { it.key >= t && it.key < t + DAY_MS }?.value
        val sources = mutableListOf<Source>()
        val forecast: ModelForecast = when {
            primary != null -> {
                val kind = when {
                    model.part != null -> SourceKind.MODEL_REGIONAL
                    model == ForecastModel.DWD_ICON -> SourceKind.MODEL_DWD_ICON
                    model == ForecastModel.ECMWF -> SourceKind.MODEL_ECMWF
                    model == ForecastModel.METEO_FRANCE -> SourceKind.MODEL_METEO_FRANCE
                    else -> SourceKind.MODEL_BEST_MATCH
                }
                sources += Source(kind, part = if (kind == SourceKind.MODEL_BEST_MATCH) partAt(nowHour) else model.part)
                if (fill != null) {
                    // from when on the best match gives the forecast: after the chosen model's last hour
                    val last = primary.hourly.lastOrNull()?.time
                    val since = last?.let { l -> fill.hourly.firstOrNull { it.time > l }?.time }
                    sources += Source(SourceKind.GAP_FILL, since = since, part = since?.let(::partAt))
                }
                primary.mergedWith(fill)
            }
            fill != null -> { sources += Source(SourceKind.MODEL_BEST_MATCH, part = partAt(nowHour)); fill }
            else -> throw primaryResult.exceptionOrNull() ?: IllegalStateException("No forecast available")
        }

        val obs = obsJob.await()
        var current = forecast.current ?: forecast.hourly.minByOrNull { kotlin.math.abs(it.time - clock()) }?.let { h ->
            CurrentWeather(h.time, h.temperature, h.apparentTemperature, h.condition, h.isDay, h.humidity, null, h.pressure,
                h.windSpeed, h.windGust, h.windDirection, h.cloudCover, h.visibility, h.uvIndex, h.precipitation)
        } ?: throw IllegalStateException("No current weather")
        // only the measured values that stand for the place (its height, near the model)
        val measured = obs?.takeIf { clock() - it.time < 2 * 3600_000L }?.forPlace(forecast.elevation, current.temperature)
        if (measured != null) {
            current = mergeObservation(current, measured)
            sources += Source(SourceKind.DWD_STATION, measured.stationName)
        }
        val alerts = alertsJob.await()
        if (inDwdArea) sources += Source(SourceKind.DWD_WARNINGS)
        // The core: the page can show
        val extraKinds = setOf(SourceKind.CAMS, SourceKind.DWD_POLLEN, SourceKind.BATHING, SourceKind.GAUGES, SourceKind.LHP_ALERTS, SourceKind.COMMUNITY)
        val keep = previous?.takeIf { it.place.latitude == lat && it.place.longitude == lon }
        val coreAt = clock()
        fun data(
            aq: dev.nimbus.weather.data.model.AirQuality?, community: dev.nimbus.weather.data.model.CommunityObservation?,
            pollen: dev.nimbus.weather.data.model.PollenForecast?, gauges: List<dev.nimbus.weather.data.model.GaugeInfo>,
            bathing: List<dev.nimbus.weather.data.model.BathingSite>, flood: List<dev.nimbus.weather.data.model.WeatherAlert>,
            extraSources: List<Source>, stale: Set<DataPart>, partsAt: Map<DataPart, Long>,
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
            fetchedAt = coreAt,
            stale = stale,
            partsAt = partsAt,
        )
        // when each part was fetched: the forecast now, an extra when its answer came – one not
        // asked for (still current) or without an answer keeps the time of its last value
        val arrivedAt = mutableMapOf<DataPart, Long>()
        fun partsAt(): Map<DataPart, Long> = buildMap {
            put(DataPart.FORECAST, coreAt)
            for (part in DataPart.entries - DataPart.FORECAST) (arrivedAt[part] ?: keep?.fetchedAt(part))?.let { put(part, it) }
        }
        val asked = wanted - DataPart.FORECAST
        onProgress(
            data(
                keep?.airQuality, keep?.community, keep?.pollen, keep?.gauges.orEmpty(), keep?.bathing.orEmpty(),
                keep?.alerts.orEmpty().filter { it.source == LHP }, keep?.sources.orEmpty().filter { it.kind in extraKinds },
                asked, partsAt(),
            ),
        )

        // The extras, each on its own: a card turns current as soon as its source has answered –
        // waiting for all of them, the cards stayed yellow until the slowest (gauges: up to 40 s).
        // A failed one, or one still out when the time is up, keeps the place's last value.
        var aq = keep?.airQuality
        var pollenForecast = keep?.pollen
        var gaugeList = keep?.gauges.orEmpty()
        var bathingList = keep?.bathing.orEmpty()
        var floodAlerts = keep?.alerts.orEmpty().filter { a -> a.source == LHP }
        var communityObs = keep?.community
        val pending = asked.toMutableSet()
        val failed = mutableSetOf<DataPart>()
        // answered with a stored value standing in: shown – so yellow even without an earlier value
        val stoodIn = mutableSetOf<DataPart>()
        val lock = kotlinx.coroutines.sync.Mutex()
        fun snapshot(): WeatherData {
            val extra = mutableListOf<Source>()
            if (aq != null) extra += Source(SourceKind.CAMS)
            if (pollenForecast?.source == dev.nimbus.weather.data.model.PollenSourceKind.DWD) extra += Source(SourceKind.DWD_POLLEN)
            if (bathingList.isNotEmpty()) extra += Source(SourceKind.BATHING, bathingList.mapNotNull { it.provider }.distinct().joinToString(","))
            if (gaugeList.isNotEmpty()) extra += Source(SourceKind.GAUGES, gaugeList.map { it.provider }.distinct().joinToString(",") { it.name })
            if (floodAlerts.isNotEmpty()) extra += Source(SourceKind.LHP_ALERTS)
            if (communityObs != null) extra += Source(SourceKind.COMMUNITY)
            // a part without a last value has nothing old to show: not stale, simply not there
            return data(aq, communityObs, pollenForecast, gaugeList, bathingList, floodAlerts, extra, (pending + failed).filterTo(mutableSetOf()) { keep != null } + stoodIn, partsAt())
        }
        coroutineScope {
            // each extra asked for on its own: its card turns current when its answer is there
            fun <T> arrive(job: kotlinx.coroutines.Deferred<Fetched<T>?>, part: DataPart, take: (T) -> Unit) {
                if (part !in asked) return
                launch {
                    val left = extrasDeadlineMs - (System.nanoTime() - started) / 1_000_000
                    val r = kotlinx.coroutines.withTimeoutOrNull(left.coerceAtLeast(1)) { job.await() }
                    lock.withLock {
                        // no answer: the last value stays; a stored value standing in: shown, but yellow
                        // like one without answer – and tried again
                        if (r == null) { job.cancel(); failed += part }
                        else { take(r.value); if (r.standIn) stoodIn += part else arrivedAt[part] = clock() }
                        pending -= part
                        if (dev.nimbus.weather.BuildConfig.DEBUG) {
                            android.util.Log.d("NimbusLoad", "$part ${if (r == null) "old value" else "new"} after ${(System.nanoTime() - started) / 1_000_000} ms")
                        }
                        if (extras) onProgress(snapshot())
                    }
                }
            }
            arrive(aqJob, DataPart.AIR_QUALITY) { aq = it }
            arrive(pollenJob, DataPart.POLLEN) { pollenForecast = it }
            arrive(gaugeJob, DataPart.GAUGES) { gaugeList = it }
            arrive(bathingJob, DataPart.BATHING) { bathingList = it }
            arrive(floodJob, DataPart.FLOOD) { floodAlerts = it }
            arrive(communityJob, DataPart.COMMUNITY) { communityObs = it }
        }
        snapshot()
    } }

    suspend fun modelComparison(place: Place): List<ModelSeries> =
        openMeteo.modelComparison(place.latitude, place.longitude, ComparisonModels)

    suspend fun search(query: String, language: String): List<Place> = openMeteo.searchPlaces(query, language)

    /**
     * An extra source's answer; [value] may be "nothing here" (null, empty). [standIn]: it holds a
     * stored value in place of a new one that failed – shown, but not as current.
     */
    private class Fetched<out T>(val value: T, val standIn: Boolean = false)

    /** [f]'s answer, or null when it failed or took longer than [timeoutMs] (logged as [what]). */
    private suspend fun <T> fetch(timeoutMs: Long, what: String, f: suspend () -> T): Fetched<T>? =
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            val notes = dev.nimbus.weather.data.remote.StandIns()
            runCatching { kotlinx.coroutines.withContext(notes) { f() }.let { Fetched(it, notes.used) } }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; android.util.Log.w("Nimbus", "$what unavailable: $it") }
                .getOrNull()
        }.also { if (it == null) android.util.Log.w("Nimbus", "$what: no answer, the last one stays") }

    companion object {
        /**
         * How long each extra source may take: two minutes, on any network. The page does not wait
         * for them (each card turns current when its answer is there); an answer cut off is lost
         * and fetched once more – so a longer wait saves data. A forced reload renews the stored
         * lists too (gauges, tide fit ~3 MB): on a slow network they took longer than 10–40 s.
         */
        const val SOURCE_TIMEOUT_MS = 120_000L
        /** [WeatherAlert.source] of the states' flood alerts. */
        private const val LHP = "LHP"
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24 * HOUR_MS
        /** How long the forecast waits for the best match's single models (they only name a source). */
        private const val PARTS_WAIT_MS = 3_000L

        /** Rough bounding box of the DWD station network / warning area. */
        fun isInDwdArea(lat: Double, lon: Double) = lat in 47.2..55.1 && lon in 5.8..15.1

        /** Measured values from a nearby DWD station beat modelled ones ([obs] already [StationObservation.forPlace]). */
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
