/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/Weather.kt
 * Domain model: places, current weather, hourly and daily values, alerts, pollen.
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

package dev.nimbus.weather.data.model

import kotlinx.serialization.Serializable

/** Simplified weather condition used for icons, texts and the animated background. */
@Serializable
enum class Condition {
    CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG,
    DRIZZLE, RAIN, HEAVY_RAIN, FREEZING_RAIN, SLEET,
    SNOW, HEAVY_SNOW, SHOWERS, THUNDERSTORM;

    val isPrecipitation: Boolean
        get() = this in setOf(DRIZZLE, RAIN, HEAVY_RAIN, FREEZING_RAIN, SLEET, SNOW, HEAVY_SNOW, SHOWERS, THUNDERSTORM)
}

@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
    /** The forecast model chosen for this place; null: the one of the settings (always for "my location"). */
    val model: ForecastModel? = null,
) {
    val subtitle: String get() = listOfNotNull(region, country).distinct().joinToString(", ")
}

@Serializable
data class CurrentWeather(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double?,
    val condition: Condition,
    val isDay: Boolean,
    val humidity: Double?,
    val dewPoint: Double?,
    val pressure: Double?,
    val windSpeed: Double?,          // km/h
    val windGust: Double?,           // km/h
    val windDirection: Double?,      // degrees, direction wind blows from
    val cloudCover: Double?,         // %
    val visibility: Double?,         // m
    val uvIndex: Double?,
    val precipitation: Double?,      // mm in the last interval
    /** Name of the observation station if the values were measured, not modelled. */
    val stationName: String? = null,
    val stationDistanceKm: Double? = null,
    /** The network of [stationName] (null in data stored before: the DWD's). */
    val stationNetwork: StationNetwork? = null,
    /** True if [visibility] was measured at the DWD station (else it is a model value). */
    val visibilityMeasured: Boolean = false,
    /** The values measured at a station, each with its station; the others are the model's. */
    val measured: List<MeasuredValue> = emptyList(),
    /** Where [condition] comes from. */
    val sky: SkyBasis = SkyBasis.MODEL,
)

@Serializable
data class HourlyPoint(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double? = null,
    val condition: Condition,
    val isDay: Boolean,
    val precipitation: Double? = null,
    val precipitationProbability: Double? = null,
    val uvIndex: Double? = null,
    val windSpeed: Double? = null,
    val windGust: Double? = null,
    val windDirection: Double? = null,
    val humidity: Double? = null,
    val visibility: Double? = null,
    val pressure: Double? = null,
    val cloudCover: Double? = null,
    /** Minutes of sunshine in the hour before [time]. */
    val sunshine: Double? = null,
    /** Thunderstorm with hail (WMO 96/99) – [condition] is THUNDERSTORM then. */
    val hail: Boolean = false,
)

@Serializable
data class DailyPoint(
    val date: Long,              // local midnight, epoch millis
    val condition: Condition,
    val tempMax: Double,
    val tempMin: Double,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val precipitationSum: Double? = null,
    val precipitationProbability: Double? = null,
    val uvIndexMax: Double? = null,
    val windSpeedMax: Double? = null,
    val windDirection: Double? = null,
)

/** A 15-minute step: precipitation of the quarter hour before [time], temperature at [time]. */
@Serializable
data class MinutelyPoint(val time: Long, val precipitation: Double, val temperature: Double? = null)

@Serializable
enum class AlertSeverity { MINOR, MODERATE, SEVERE, EXTREME }

@Serializable
data class WeatherAlert(
    val id: String,
    val headline: String,
    val event: String,
    val description: String,
    val instruction: String? = null,
    val severity: AlertSeverity,
    val onset: Long?,
    val expires: Long?,
    val source: String,
)

@Serializable
data class AirQuality(
    val europeanAqi: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val nitrogenDioxide: Double?,
    val pollen: Map<String, Double> = emptyMap(),
)

@Serializable
data class WeatherData(
    val place: Place,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val current: CurrentWeather,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val minutely: List<MinutelyPoint> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    val airQuality: AirQuality? = null,
    val community: CommunityObservation? = null,
    val pollen: PollenForecast? = null,
    /** Nearby water level gauges, one per water body (tide gauge first), only in Germany. */
    val gauges: List<GaugeInfo> = emptyList(),
    /** Official EU bathing waters in the chosen radius plus favourites (nearest first). */
    val bathing: List<BathingSite> = emptyList(),
    val sources: List<Source>,
    val fetchedAt: Long,
    /** Parts still showing older values: being refreshed, or their source failed (cards: yellow dot). */
    val stale: Set<DataPart> = emptySet(),
    /**
     * When each part was fetched – each has its own shelf life ([dev.nimbus.weather.data.repo.Freshness.lifeMs]).
     * The forecast missing here (data stored before) counts as fetched at [fetchedAt]; an extra
     * missing here is of unknown age: expired (stored by an earlier version, whose background
     * refresh kept the extras of an earlier time under a newer [fetchedAt]).
     */
    val partsAt: Map<DataPart, Long> = emptyMap(),
) {
    /** When [part] was fetched (0: unknown). */
    fun fetchedAt(part: DataPart): Long = partsAt[part] ?: if (part == DataPart.FORECAST) fetchedAt else 0L
}

/** The parts of [WeatherData] that come from sources of their own (and can be older than the rest). */
@Serializable
enum class DataPart { FORECAST, AIR_QUALITY, POLLEN, COMMUNITY, GAUGES, BATHING, FLOOD }

@Serializable
enum class BathingCategory { LAKE, RIVER, COAST, TRANSITIONAL }

/** EU classification of the last four seasons (bathing water directive). */
@Serializable
enum class BathingQuality { EXCELLENT, GOOD, SUFFICIENT, POOR, NOT_CLASSIFIED }

/** Current assessment by the state, where it publishes one (Berlin: traffic light). */
@Serializable
enum class BathingStatus { OK, WARNING, CLOSED }

/** An official EU bathing water (EEA) with the latest values the state publishes openly. */
@Serializable
data class BathingSite(
    /** EU identifier, e.g. "DEBE_PR_0019". */
    val id: String,
    val name: String,
    val category: BathingCategory,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double,
    val quality: BathingQuality? = null,
    /** Bathing water profile of the state (PDF or web page). */
    val profileLink: String? = null,
    /** Water temperature (°C): a sample of the health office, or the sea model at coasts. */
    val waterTemp: Double? = null,
    val waterTempTime: Long? = null,
    val waterTempFromModel: Boolean = false,
    /** Date of the last sample and what it found. */
    val sampleTime: Long? = null,
    val visibilityM: Double? = null,
    val status: BathingStatus? = null,
    /** Blue-green algae (cyanobacteria) reported at the last check. */
    val algae: Boolean = false,
    /** Notice of the health office (algae, cercariae, closures …), as published. */
    val notice: String? = null,
    /** Who provided the current values (e.g. "LAGeSo Berlin"). */
    val provider: String? = null,
)

/** A data source that contributed to a forecast; rendered localized in the UI. */
@Serializable
enum class SourceKind { MODEL_DWD_ICON, MODEL_ECMWF, MODEL_METEO_FRANCE, MODEL_REGIONAL, MODEL_BEST_MATCH, GAP_FILL, DWD_STATION, STATION, DWD_WARNINGS, CAMS, COMMUNITY, DWD_POLLEN, PEGELONLINE, NLWKN, GAUGES, LHP_ALERTS, BATHING }

@Serializable
data class Source(
    val kind: SourceKind,
    val detail: String? = null,
    /** From when on it gives the forecast (the gap fill after the chosen model's last hour). */
    val since: Long? = null,
    /** The single model behind it (Open-Meteo's best match: which one, with its grid). */
    val part: ModelPart? = null,
    /** The measuring network of a [SourceKind.STATION]. */
    val network: StationNetwork? = null,
)

/** A network of weather stations whose measurements replace model values: its name and who delivers them. */
@Serializable
enum class StationNetwork(val label: String, val provider: String) {
    DWD("DWD", "Bright Sky"),
    GEOSPHERE("GeoSphere", "GeoSphere Austria"),
    METEOSWISS("MeteoSwiss", "MeteoSwiss"),
    DMI("DMI", "DMI Open Data"),
    /** Airports worldwide (METAR, every half hour; temperatures in whole degrees). */
    METAR("METAR", "NOAA aviationweather.gov"),
}

/** Pollen types covered by the DWD index and/or CAMS; [key] is used for texts and colours. */
@Serializable
enum class PollenType(val key: String) {
    HAZEL("hazel"), ALDER("alder"), ASH("ash"), BIRCH("birch"), GRASS("grass"),
    RYE("rye"), MUGWORT("mugwort"), RAGWEED("ragweed"), OLIVE("olive"),
}

/** One day of the forecast: level 0..3 in half steps (DWD scale), null if not issued. */
@Serializable
data class PollenDay(val date: Long, val levels: Map<PollenType, Float>)

@Serializable
enum class PollenSourceKind { DWD, CAMS }

@Serializable
data class PollenForecast(
    val source: PollenSourceKind,
    /** DWD forecast region, e.g. "Geest, Schleswig-Holstein und Hamburg". */
    val region: String? = null,
    val days: List<PollenDay>,
    /** Hourly CAMS concentrations (grains/m³) for composition and course. */
    val hourlyTimes: List<Long> = emptyList(),
    val hourly: Map<PollenType, List<Double?>> = emptyMap(),
)

/** Aggregated readings of nearby citizen-science sensors (Sensor.Community). */
@Serializable
data class CommunityObservation(
    val sensorCount: Int,
    val radiusKm: Double,
    val temperature: Double?,
    val humidity: Double?,
    val pressure: Double?,     // hPa at sea level
    val pm10: Double?,
    val pm25: Double?,
    val temperatureSpread: Double? = null,
)

/** One forecast model's hourly series for the model comparison chart. */
@Serializable
data class ModelSeries(
    val modelId: String,
    val label: String,
    val times: List<Long>,
    val temperature: List<Double?>,
    val precipitation: List<Double?>,
)

/** One water level reading, [value] in cm above gauge zero. */
@Serializable
data class LevelSample(val time: Long, val value: Double)

/**
 * The nearest gauge of the federal waterways (PEGELONLINE): current level, reference values and
 * the recent course; for tide gauges also the predicted high and low waters.
 */
@Serializable
data class GaugeInfo(
    val uuid: String,
    val name: String,
    val water: String,
    val distanceKm: Double,
    val tidal: Boolean,
    /** Gauge zero in metres above sea level (NHN), if known. */
    val gaugeZero: Double? = null,
    val level: Double? = null,
    val levelTime: Long? = null,
    /** PEGELONLINE classification relative to mean low / mean high water: low, normal, high. */
    val state: String? = null,
    /** Characteristic values in cm, e.g. MNW, MW, MHW, HSW, M_I, MThw, MTnw. */
    val marks: Map<String, Double> = emptyMap(),
    /** Discharge in m³/s, if measured recently. */
    val discharge: Double? = null,
    /** Measured course, 10-minute means (rivers: 7 days, coast: 1.5 days). */
    val history: List<LevelSample> = emptyList(),
    /** Tide gauges: predicted course for the next 2 days. */
    val prediction: List<LevelSample> = emptyList(),
    val extremes: List<dev.nimbus.weather.util.Tides.Extreme> = emptyList(),
    /** RMS of the tide fit in cm – how much wind and noise the model does not explain. */
    val predictionRms: Double? = null,
    /** Who operates the gauge / delivers the data. */
    val provider: GaugeProvider = GaugeProvider.PEGELONLINE,
    /** Flood alert levels in cm (Meldestufe / Informationswert / Alarmstufe 1–4), where published. */
    val alertLevels: Map<Int, Double> = emptyMap(),
    /** Current alert level, 0 = none. */
    val alertStage: Int? = null,
    /** How the state names its alert levels. */
    val alertKind: AlertKind = AlertKind.MELDESTUFE,
    /** Forecast of the state flood centre (Hessen), dashed in the chart. */
    val forecast: List<LevelSample> = emptyList(),
    /** Classification of the Länderübergreifendes Hochwasserportal: -1 no data, 0 none … 4 very large flood. */
    val lhpClass: Int? = null,
    /** The state's own wording for the classification, e.g. "Kein Hochwasser", "Meldestufe 1". */
    val lhpClassName: String? = null,
    /** State's own status text where it provides one (Sachsen: "Niedrigwasser", "Kein Hochwasser"). */
    val stateText: String? = null,
    /** Web page of the gauge at the operator (for gauges without measured values in the app). */
    val link: String? = null,
    /** Tendency given by the operator: 1 rising, 0 steady, -1 falling. */
    val tendency: Int? = null,
    /** PEGELONLINE: the station measures every minute (only a shorter history is loaded). */
    val minuteData: Boolean = false,
) {
    /** True when the app has a measured value; LHP-only gauges carry just a classification. */
    val hasValues: Boolean get() = level != null
}

/**
 * Who delivers the data of a gauge. PEGELONLINE: federal waterways; the states for their own
 * rivers; LHP: classification only (all states, no measured values).
 */
@Serializable
enum class GaugeProvider { PEGELONLINE, NLWKN, LANUK_NRW, LFULG_SACHSEN, HLNUG_HESSEN, LHP }

@Serializable
enum class AlertKind { MELDESTUFE, INFORMATIONSWERT, ALARMSTUFE }
