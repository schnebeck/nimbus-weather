package dev.nimbus.weather.data.remote

import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherAlert
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.OffsetDateTime

/** A DWD station observation delivered by Bright Sky. */
data class StationObservation(
    val time: Long,
    val stationName: String,
    val distanceKm: Double,
    val temperature: Double?,
    val humidity: Double?,
    val dewPoint: Double?,
    val pressure: Double?,
    val windSpeed: Double?,
    val windGust: Double?,
    val windDirection: Double?,
    val visibility: Double?,
    val cloudCover: Double?,
    val precipitation60: Double?,
    /** Observed weather, null if the station reports no present weather. */
    val condition: Condition?,
    val observedDry: Boolean,
)

/**
 * Bright Sky (https://brightsky.dev) — a free JSON API for DWD open data:
 * SYNOP station observations and official DWD weather warnings (Germany only).
 */
class BrightSkySource(private val http: OkHttpClient, private val baseUrl: String = "https://api.brightsky.dev") {

    suspend fun currentObservation(lat: Double, lon: Double): StationObservation? {
        val url = "$baseUrl/current_weather".toHttpUrl().newBuilder()
            .addQueryParameter("lat", OpenMeteoSource.fmt(lat))
            .addQueryParameter("lon", OpenMeteoSource.fmt(lon))
            .addQueryParameter("max_dist", "30000")
            .build()
        return try {
            parseCurrent(http.getJson(url.toString()))
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e   // outside Germany: no station nearby
        }
    }

    suspend fun alerts(lat: Double, lon: Double, german: Boolean): List<WeatherAlert> {
        val url = "$baseUrl/alerts".toHttpUrl().newBuilder()
            .addQueryParameter("lat", OpenMeteoSource.fmt(lat))
            .addQueryParameter("lon", OpenMeteoSource.fmt(lon))
            .build()
        return try {
            parseAlerts(http.getJson(url.toString()), german)
        } catch (e: HttpException) {
            if (e.code == 404 || e.code == 400) emptyList() else throw e
        }
    }

    companion object {
        fun parseTime(s: String?): Long? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

        fun parseCurrent(root: JsonElement): StationObservation? {
            val o = root.obj() ?: return null
            val w = o.o("weather") ?: return null
            val sourceId = w.l("source_id")
            val src = o.a("sources")?.mapNotNull { it as? JsonObject }?.let { list ->
                list.firstOrNull { it.l("id") == sourceId } ?: list.firstOrNull()
            } ?: return null
            val condStr = w.s("condition")
            val condition = when (condStr) {
                "fog" -> Condition.FOG
                "rain" -> if ((w.d("precipitation_60") ?: 0.0) >= 4.0) Condition.HEAVY_RAIN else Condition.RAIN
                "sleet" -> Condition.SLEET
                "snow" -> Condition.SNOW
                "hail" -> Condition.SHOWERS
                "thunderstorm" -> Condition.THUNDERSTORM
                else -> null
            }
            return StationObservation(
                time = parseTime(w.s("timestamp")) ?: 0L,
                stationName = src.s("station_name") ?: "DWD",
                distanceKm = (src.d("distance") ?: 0.0) / 1000.0,
                temperature = w.d("temperature"),
                humidity = w.d("relative_humidity"),
                dewPoint = w.d("dew_point"),
                pressure = w.d("pressure_msl"),
                windSpeed = w.d("wind_speed_10") ?: w.d("wind_speed_30"),
                windGust = w.d("wind_gust_speed_10") ?: w.d("wind_gust_speed_60"),
                windDirection = w.d("wind_direction_10") ?: w.d("wind_direction_30"),
                visibility = w.d("visibility"),
                cloudCover = w.d("cloud_cover"),
                precipitation60 = w.d("precipitation_60"),
                condition = condition,
                observedDry = condStr == "dry",
            )
        }

        fun parseAlerts(root: JsonElement, german: Boolean): List<WeatherAlert> {
            val list = root.obj()?.a("alerts") ?: return emptyList()
            return list.mapNotNull { e ->
                val a = e as? JsonObject ?: return@mapNotNull null
                if (a.s("status") == "test") return@mapNotNull null
                fun loc(key: String) = if (german) a.s("${key}_de") ?: a.s("${key}_en") else a.s("${key}_en") ?: a.s("${key}_de")
                WeatherAlert(
                    id = a.s("alert_id") ?: a.l("id")?.toString() ?: return@mapNotNull null,
                    headline = loc("headline") ?: loc("event") ?: "",
                    event = loc("event") ?: "",
                    description = loc("description") ?: "",
                    instruction = loc("instruction"),
                    severity = when (a.s("severity")) {
                        "extreme" -> AlertSeverity.EXTREME
                        "severe" -> AlertSeverity.SEVERE
                        "moderate" -> AlertSeverity.MODERATE
                        else -> AlertSeverity.MINOR
                    },
                    onset = parseTime(a.s("onset")),
                    expires = parseTime(a.s("expires")),
                    source = "DWD",
                )
            }.sortedByDescending { it.severity.ordinal }
        }
    }
}
