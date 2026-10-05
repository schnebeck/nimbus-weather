/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/BrightSkySource.kt
 * DWD station observations and weather alerts via the Bright Sky API.
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

package dev.nimbus.weather.data.remote

import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Representative
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.data.model.WeatherCodes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.OffsetDateTime

/** A DWD station: where a measured value was taken. */
data class StationSite(val name: String, val distanceKm: Double, val heightM: Double?)

/** The values of a [StationObservation]; each may come from another station than the main one. */
enum class Reading { TEMPERATURE, HUMIDITY, DEW_POINT, PRESSURE, WIND_SPEED, WIND_GUST, WIND_DIRECTION, VISIBILITY, CLOUD_COVER, PRECIPITATION, CONDITION, SUNSHINE }

/**
 * A station observation: from the DWD via Bright Sky, or from another [network] (see
 * [StationNetworks]). The main station is [stationName]; values it does not measure Bright Sky
 * fills in from other stations nearby – [fallbacks] names their station, which may be hundreds of
 * metres lower or higher (see [forPlace]).
 */
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
    /** Height of the main station in metres. */
    val heightM: Double? = null,
    /** DWD id of the main station. */
    val stationId: String? = null,
    /** The network it was measured in. */
    val network: dev.nimbus.weather.data.model.StationNetwork = dev.nimbus.weather.data.model.StationNetwork.DWD,
    /** The values taken from another station than the main one, and that station. */
    val fallbacks: Map<Reading, StationSite> = emptyMap(),
    /** Minutes of sunshine per hour: measured over the last half hour (doubled), else the last hour. */
    val sunshine: Double? = null,
) {
    val site: StationSite get() = StationSite(stationName, distanceKm, heightM)
    fun siteOf(r: Reading): StationSite = fallbacks[r] ?: site

    /**
     * The values Bright Sky took from other stations, taken instead from the main station's own
     * hourly reports in [hours] where it has them (the latest, at most [maxAgeMs] before [time]):
     * a station that leaves a value out of its 10-minute report usually has it in the hourly one.
     */
    fun filledFrom(hours: List<StationHour>, maxAgeMs: Long = 2 * 3600_000L): StationObservation {
        var o = this
        fallbacks.keys.forEach { r ->
            val h = hours.filter { it.time in time - maxAgeMs..time && r in it.values }.maxByOrNull { it.time } ?: return@forEach
            val v = h.values.getValue(r)
            o = when (r) {
                Reading.TEMPERATURE -> o.copy(temperature = v)
                Reading.HUMIDITY -> o.copy(humidity = v)
                Reading.DEW_POINT -> o.copy(dewPoint = v)
                Reading.PRESSURE -> o.copy(pressure = v)
                Reading.WIND_SPEED -> o.copy(windSpeed = v)
                Reading.WIND_GUST -> o.copy(windGust = v)
                Reading.WIND_DIRECTION -> o.copy(windDirection = v)
                Reading.VISIBILITY -> o.copy(visibility = v)
                Reading.CLOUD_COVER -> o.copy(cloudCover = v)
                Reading.PRECIPITATION -> o.copy(precipitation60 = v)
                // the weather and the sunshine of an hour ago are not those of now
                Reading.CONDITION, Reading.SUNSHINE -> return@forEach
            }.let { it.copy(fallbacks = it.fallbacks - r) }
        }
        return o
    }

    /**
     * Only the values that stand for the place at [elevationM] metres (unknown: the main station's
     * height) whose model temperature is [modelTemperature]: each value's own station must be at the
     * place's height ([Representative.height]); a station whose temperature is far off the model's
     * gives nothing ([Representative.temperature]). The sea-level pressure holds at any height.
     * The station named is the one whose temperature is kept, and only then – null if no
     * temperature is left.
     */
    fun forPlace(elevationM: Double?, modelTemperature: Double?): StationObservation? {
        val ref = elevationM ?: heightM
        val sites = Reading.entries.associateWith { siteOf(it) }
        val distrusted = temperature?.takeIf { !Representative.temperature(it, modelTemperature) }?.let { sites.getValue(Reading.TEMPERATURE) }
        fun fits(r: Reading): Boolean {
            val s = sites.getValue(r)
            return s != distrusted && (r == Reading.PRESSURE || Representative.height(s.heightM, ref))
        }
        val t = temperature?.takeIf { fits(Reading.TEMPERATURE) } ?: return null
        val shown = sites.getValue(Reading.TEMPERATURE)
        return copy(
            stationName = shown.name, distanceKm = shown.distanceKm, heightM = shown.heightM,
            temperature = t,
            humidity = humidity?.takeIf { fits(Reading.HUMIDITY) },
            dewPoint = dewPoint?.takeIf { fits(Reading.DEW_POINT) },
            pressure = pressure?.takeIf { fits(Reading.PRESSURE) },
            windSpeed = windSpeed?.takeIf { fits(Reading.WIND_SPEED) },
            windGust = windGust?.takeIf { fits(Reading.WIND_GUST) },
            windDirection = windDirection?.takeIf { fits(Reading.WIND_DIRECTION) },
            // "measured at <station>" – only where the station shown measured it
            visibility = visibility?.takeIf { fits(Reading.VISIBILITY) && sites.getValue(Reading.VISIBILITY) == shown },
            cloudCover = cloudCover?.takeIf { fits(Reading.CLOUD_COVER) },
            precipitation60 = precipitation60?.takeIf { fits(Reading.PRECIPITATION) },
            condition = condition?.takeIf { fits(Reading.CONDITION) },
            sunshine = sunshine?.takeIf { fits(Reading.SUNSHINE) },
            observedDry = observedDry && fits(Reading.CONDITION) && fits(Reading.PRECIPITATION),
            fallbacks = Reading.entries.filter { sites.getValue(it) != shown }.associateWith { sites.getValue(it) },
        )
    }
}

/** A station's own hourly report: the values it measured itself (none filled in from elsewhere). */
data class StationHour(val time: Long, val values: Map<Reading, Double>)

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
        val obs = try {
            parseCurrent(http.getJson(url.toString()))
        } catch (e: HttpException) {
            if (e.code == 404) return null else throw e   // outside Germany: no station nearby
        } ?: return null
        // values from other stations: first the main station's own hourly reports
        if (obs.fallbacks.keys.all { it == Reading.CONDITION } || obs.stationId == null) return obs
        val hoursUrl = "$baseUrl/weather".toHttpUrl().newBuilder()
            .addQueryParameter("dwd_station_id", obs.stationId)
            .addQueryParameter("date", java.time.Instant.ofEpochMilli(obs.time - 3 * 3600_000L).toString())
            .addQueryParameter("last_date", java.time.Instant.ofEpochMilli(obs.time).toString())
            .build()
        val hours = runCatching { parseStationHours(http.getJson(hoursUrl.toString())) }.getOrNull() ?: return obs
        return obs.filledFrom(hours)
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
            val list = o.a("sources")?.mapNotNull { it as? JsonObject }.orEmpty()
            val src = list.firstOrNull { it.l("id") == sourceId } ?: list.firstOrNull() ?: return null
            fun site(s: JsonObject) = StationSite(s.s("station_name") ?: "DWD", (s.d("distance") ?: 0.0) / 1000.0, s.d("height"))
            val main = site(src)
            // Bright Sky's gap filling: the value of a key comes from another station
            val fallbackIds = w.o("fallback_source_ids")
            val fallbacks = mutableMapOf<Reading, StationSite>()
            // (a station missing from the list has no known height: it is not taken, see forPlace)
            fun note(r: Reading, key: String) = fallbackIds?.l(key)?.let { id ->
                fallbacks[r] = list.firstOrNull { it.l("id") == id }?.let(::site) ?: StationSite("DWD", 0.0, null)
            }
            /** The first of [keys] with a value, its station noted under [r]. */
            fun value(r: Reading, vararg keys: String): Double? {
                val key = keys.firstOrNull { w.d(it) != null } ?: return null
                note(r, key)
                return w.d(key)
            }
            val condStr = w.s("condition")
            note(Reading.CONDITION, "condition")
            val precipitation60 = value(Reading.PRECIPITATION, "precipitation_60")
            val sunshine = w.d("sunshine_30")?.let { note(Reading.SUNSHINE, "sunshine_30"); it * 2 } ?: value(Reading.SUNSHINE, "sunshine_60")
            val condition = when (condStr) {
                "fog" -> Condition.FOG
                "rain" -> if ((precipitation60 ?: 0.0) >= WeatherCodes.HEAVY_RAIN_MM_H) Condition.HEAVY_RAIN else Condition.RAIN
                "sleet" -> Condition.SLEET
                "snow" -> Condition.SNOW
                "hail" -> Condition.SHOWERS
                "thunderstorm" -> Condition.THUNDERSTORM
                else -> null
            }
            return StationObservation(
                time = parseTime(w.s("timestamp")) ?: 0L,
                stationName = main.name,
                distanceKm = main.distanceKm,
                temperature = value(Reading.TEMPERATURE, "temperature"),
                humidity = value(Reading.HUMIDITY, "relative_humidity"),
                dewPoint = value(Reading.DEW_POINT, "dew_point"),
                pressure = value(Reading.PRESSURE, "pressure_msl"),
                windSpeed = value(Reading.WIND_SPEED, "wind_speed_10", "wind_speed_30"),
                windGust = value(Reading.WIND_GUST, "wind_gust_speed_10", "wind_gust_speed_60"),
                windDirection = value(Reading.WIND_DIRECTION, "wind_direction_10", "wind_direction_30"),
                visibility = value(Reading.VISIBILITY, "visibility"),
                cloudCover = value(Reading.CLOUD_COVER, "cloud_cover"),
                precipitation60 = precipitation60,
                condition = condition,
                observedDry = condStr == "dry",
                heightM = main.heightM,
                stationId = src.s("dwd_station_id"),
                fallbacks = fallbacks,
                sunshine = sunshine,
            )
        }

        /**
         * A station's hourly reports (`/weather?dwd_station_id=`): per hour the values the station
         * measured – not the ones Bright Sky filled in from other stations, not forecasts (MOSMIX).
         */
        fun parseStationHours(root: JsonElement): List<StationHour> {
            val o = root.obj() ?: return emptyList()
            val forecastIds = o.a("sources")?.mapNotNull { it as? JsonObject }.orEmpty()
                .filter { it.s("observation_type") == "forecast" }.mapNotNull { it.l("id") }.toSet()
            val keys = mapOf(
                Reading.TEMPERATURE to "temperature", Reading.HUMIDITY to "relative_humidity", Reading.DEW_POINT to "dew_point",
                Reading.PRESSURE to "pressure_msl", Reading.WIND_SPEED to "wind_speed", Reading.WIND_GUST to "wind_gust_speed",
                Reading.WIND_DIRECTION to "wind_direction", Reading.VISIBILITY to "visibility", Reading.CLOUD_COVER to "cloud_cover",
                Reading.PRECIPITATION to "precipitation",
            )
            return o.a("weather")?.mapNotNull { e ->
                val w = e as? JsonObject ?: return@mapNotNull null
                if (w.l("source_id") in forecastIds) return@mapNotNull null
                val time = parseTime(w.s("timestamp")) ?: return@mapNotNull null
                val filled = w.o("fallback_source_ids")?.keys.orEmpty()
                StationHour(time, keys.mapNotNull { (r, k) -> if (k in filled) null else w.d(k)?.let { r to it } }.toMap())
            }.orEmpty()
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
