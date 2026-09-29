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

    val isSnowy: Boolean get() = this == SNOW || this == HEAVY_SNOW || this == SLEET
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
    /** True if [visibility] was measured at the DWD station (else it is a model value). */
    val visibilityMeasured: Boolean = false,
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

@Serializable
data class MinutelyPoint(val time: Long, val precipitation: Double)

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
    val sources: List<Source>,
    val fetchedAt: Long,
)

/** A data source that contributed to a forecast; rendered localized in the UI. */
@Serializable
enum class SourceKind { MODEL_DWD_ICON, MODEL_ECMWF, MODEL_METEO_FRANCE, MODEL_BEST_MATCH, GAP_FILL, DWD_STATION, DWD_WARNINGS, CAMS, COMMUNITY, DWD_POLLEN }

@Serializable
data class Source(val kind: SourceKind, val detail: String? = null)

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
