/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/OpenMeteoSource.kt
 * Forecasts, air quality and place search from Open-Meteo.
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

import dev.nimbus.weather.data.model.AirQuality
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.WeatherCodes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

/** One model forecast from Open-Meteo, already mapped to domain types. */
data class ModelForecast(
    val timezone: String,
    val utcOffsetSeconds: Int,
    val current: CurrentWeather?,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val minutely: List<MinutelyPoint>,
) {
    /** Fills gaps (null fields, missing hours/days) of this forecast with values from [other]. */
    fun mergedWith(other: ModelForecast?): ModelForecast {
        if (other == null) return this
        val otherHours = other.hourly.associateBy { it.time }
        val hours = hourly.map { h ->
            val o = otherHours[h.time] ?: return@map h
            h.copy(
                apparentTemperature = h.apparentTemperature ?: o.apparentTemperature,
                precipitation = h.precipitation ?: o.precipitation,
                precipitationProbability = h.precipitationProbability ?: o.precipitationProbability,
                uvIndex = h.uvIndex ?: o.uvIndex,
                windSpeed = h.windSpeed ?: o.windSpeed,
                windGust = h.windGust ?: o.windGust,
                windDirection = h.windDirection ?: o.windDirection,
                humidity = h.humidity ?: o.humidity,
                visibility = h.visibility ?: o.visibility,
                pressure = h.pressure ?: o.pressure,
                cloudCover = h.cloudCover ?: o.cloudCover,
                sunshine = h.sunshine ?: o.sunshine,
            )
        }
        val lastHour = hours.lastOrNull()?.time ?: Long.MIN_VALUE
        val mergedHours = hours + other.hourly.filter { it.time > lastHour }

        val otherDays = other.daily.associateBy { it.date }
        val days = daily.map { d ->
            val o = otherDays[d.date] ?: return@map d
            d.copy(
                sunrise = d.sunrise ?: o.sunrise,
                sunset = d.sunset ?: o.sunset,
                precipitationSum = d.precipitationSum ?: o.precipitationSum,
                precipitationProbability = d.precipitationProbability ?: o.precipitationProbability,
                uvIndexMax = d.uvIndexMax ?: o.uvIndexMax,
                windSpeedMax = d.windSpeedMax ?: o.windSpeedMax,
                windDirection = d.windDirection ?: o.windDirection,
            )
        }
        val lastDay = days.lastOrNull()?.date ?: Long.MIN_VALUE
        val mergedDays = days + other.daily.filter { it.date > lastDay }

        val cur = current?.let { c ->
            val o = other.current ?: return@let c
            c.copy(
                apparentTemperature = c.apparentTemperature ?: o.apparentTemperature,
                humidity = c.humidity ?: o.humidity,
                dewPoint = c.dewPoint ?: o.dewPoint,
                pressure = c.pressure ?: o.pressure,
                windSpeed = c.windSpeed ?: o.windSpeed,
                windGust = c.windGust ?: o.windGust,
                windDirection = c.windDirection ?: o.windDirection,
                cloudCover = c.cloudCover ?: o.cloudCover,
                visibility = c.visibility ?: o.visibility,
                uvIndex = c.uvIndex ?: o.uvIndex,
            )
        } ?: other.current

        return copy(
            current = cur,
            hourly = mergedHours,
            daily = mergedDays,
            minutely = minutely.ifEmpty { other.minutely },
        )
    }
}

class OpenMeteoSource(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com",
    private val airQualityUrl: String = "https://air-quality-api.open-meteo.com",
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com",
) {

    suspend fun forecast(lat: Double, lon: Double, model: String): ModelForecast {
        val url = "$baseUrl/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", fmt(lat))
            .addQueryParameter("longitude", fmt(lon))
            .addQueryParameter("models", model)
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_days", "10")
            // 24 past hours: the day meteogram of "today" starts at midnight
            .addQueryParameter("past_hours", "24")
            .addQueryParameter("forecast_hours", "240")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("current", CURRENT)
            .addQueryParameter("hourly", HOURLY)
            .addQueryParameter("daily", DAILY)
            .addQueryParameter("minutely_15", "precipitation")
            .addQueryParameter("past_minutely_15", "1")
            .addQueryParameter("forecast_minutely_15", "12")
            .build()
        return parseForecast(http.getJson(url.toString()))
    }

    suspend fun airQuality(lat: Double, lon: Double): AirQuality {
        val url = "$airQualityUrl/v1/air-quality".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", fmt(lat))
            .addQueryParameter("longitude", fmt(lon))
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("current", "european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,$POLLEN")
            .build()
        return parseAirQuality(http.getJson(url.toString()))
    }

    suspend fun modelComparison(lat: Double, lon: Double, models: List<Pair<String, String>>): List<ModelSeries> {
        val url = "$baseUrl/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", fmt(lat))
            .addQueryParameter("longitude", fmt(lon))
            .addQueryParameter("models", models.joinToString(",") { it.first })
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_days", "4")
            .addQueryParameter("hourly", "temperature_2m,precipitation")
            .build()
        return parseModelComparison(http.getJson(url.toString()), models)
    }

    suspend fun searchPlaces(query: String, language: String): List<Place> {
        val url = "$geocodingUrl/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "15")
            .addQueryParameter("language", language)
            .addQueryParameter("format", "json")
            .build()
        return parseGeocoding(http.getJson(url.toString()))
    }

    companion object {
        const val CURRENT = "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,is_day," +
            "precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,visibility,uv_index"
        const val HOURLY = "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability," +
            "precipitation,weather_code,is_day,uv_index,visibility,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,pressure_msl,cloud_cover,sunshine_duration"
        const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum," +
            "precipitation_probability_max,uv_index_max,wind_speed_10m_max,wind_direction_10m_dominant"
        const val POLLEN = "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,ragweed_pollen,olive_pollen"

        fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)

        fun parseForecast(root: JsonElement): ModelForecast {
            val o = root.obj() ?: error("Invalid Open-Meteo response")
            if (o["error"].bool() == true) throw IllegalStateException(o.s("reason") ?: "Open-Meteo error")
            val c = o.o("current")
            val current = c?.let { cur ->
                val temp = cur.d("temperature_2m") ?: return@let null
                CurrentWeather(
                    time = (cur.l("time") ?: 0L) * 1000,
                    temperature = temp,
                    apparentTemperature = cur.d("apparent_temperature"),
                    condition = WeatherCodes.fromWmo(cur.d("weather_code")?.toInt()),
                    isDay = (cur.d("is_day") ?: 1.0) > 0.5,
                    humidity = cur.d("relative_humidity_2m"),
                    dewPoint = cur.d("dew_point_2m"),
                    pressure = cur.d("pressure_msl"),
                    windSpeed = cur.d("wind_speed_10m"),
                    windGust = cur.d("wind_gusts_10m"),
                    windDirection = cur.d("wind_direction_10m"),
                    cloudCover = cur.d("cloud_cover"),
                    visibility = cur.d("visibility"),
                    uvIndex = cur.d("uv_index"),
                    precipitation = cur.d("precipitation"),
                )
            }
            val hourly = o.o("hourly")?.let { h ->
                val t = h.longs("time")
                val temp = h.doubles("temperature_2m")
                val app = h.doubles("apparent_temperature")
                val rh = h.doubles("relative_humidity_2m")
                val pp = h.doubles("precipitation_probability")
                val pr = h.doubles("precipitation")
                val wc = h.doubles("weather_code")
                val day = h.doubles("is_day")
                val uv = h.doubles("uv_index")
                val vis = h.doubles("visibility")
                val ws = h.doubles("wind_speed_10m")
                val wd = h.doubles("wind_direction_10m")
                val wg = h.doubles("wind_gusts_10m")
                val ps = h.doubles("pressure_msl")
                val cc = h.doubles("cloud_cover")
                val sun = h.doubles("sunshine_duration")
                t.indices.mapNotNull { i ->
                    val time = t[i] ?: return@mapNotNull null
                    val tt = temp.at(i) ?: return@mapNotNull null
                    HourlyPoint(
                        time = time * 1000,
                        temperature = tt,
                        apparentTemperature = app.at(i),
                        condition = WeatherCodes.fromWmo(wc.at(i)?.toInt()),
                        isDay = (day.at(i) ?: 1.0) > 0.5,
                        precipitation = pr.at(i),
                        precipitationProbability = pp.at(i),
                        uvIndex = uv.at(i),
                        windSpeed = ws.at(i),
                        windGust = wg.at(i),
                        windDirection = wd.at(i),
                        humidity = rh.at(i),
                        visibility = vis.at(i),
                        pressure = ps.at(i),
                        cloudCover = cc.at(i),
                        sunshine = sun.at(i)?.div(60.0),
                    )
                }
            } ?: emptyList()
            val daily = o.o("daily")?.let { d ->
                val t = d.longs("time")
                val wc = d.doubles("weather_code")
                val tmax = d.doubles("temperature_2m_max")
                val tmin = d.doubles("temperature_2m_min")
                val sr = d.longs("sunrise")
                val ss = d.longs("sunset")
                val ps = d.doubles("precipitation_sum")
                val pp = d.doubles("precipitation_probability_max")
                val uv = d.doubles("uv_index_max")
                val ws = d.doubles("wind_speed_10m_max")
                val wd = d.doubles("wind_direction_10m_dominant")
                t.indices.mapNotNull { i ->
                    val time = t[i] ?: return@mapNotNull null
                    val hi = tmax.at(i) ?: return@mapNotNull null
                    val lo = tmin.at(i) ?: return@mapNotNull null
                    DailyPoint(
                        date = time * 1000,
                        condition = WeatherCodes.fromWmo(wc.at(i)?.toInt()),
                        tempMax = hi,
                        tempMin = lo,
                        sunrise = sr.at(i)?.times(1000),
                        sunset = ss.at(i)?.times(1000),
                        precipitationSum = ps.at(i),
                        precipitationProbability = pp.at(i),
                        uvIndexMax = uv.at(i),
                        windSpeedMax = ws.at(i),
                        windDirection = wd.at(i),
                    )
                }
            } ?: emptyList()
            val minutely = o.o("minutely_15")?.let { m ->
                val t = m.longs("time")
                val p = m.doubles("precipitation")
                t.indices.mapNotNull { i ->
                    val time = t[i] ?: return@mapNotNull null
                    MinutelyPoint(time * 1000, p.at(i) ?: return@mapNotNull null)
                }
            } ?: emptyList()
            return ModelForecast(
                timezone = o.s("timezone") ?: "UTC",
                utcOffsetSeconds = o.d("utc_offset_seconds")?.toInt() ?: 0,
                current = current,
                hourly = hourly,
                daily = daily,
                minutely = minutely,
            )
        }

        fun parseAirQuality(root: JsonElement): AirQuality {
            val c = root.obj()?.o("current") ?: error("No air quality data")
            val pollen = POLLEN.split(',').mapNotNull { k ->
                c.d(k)?.let { k.removeSuffix("_pollen") to it }
            }.toMap()
            return AirQuality(
                europeanAqi = c.d("european_aqi"),
                pm25 = c.d("pm2_5"),
                pm10 = c.d("pm10"),
                ozone = c.d("ozone"),
                nitrogenDioxide = c.d("nitrogen_dioxide"),
                pollen = pollen,
            )
        }

        fun parseModelComparison(root: JsonElement, models: List<Pair<String, String>>): List<ModelSeries> {
            val h = root.obj()?.o("hourly") ?: return emptyList()
            val times = h.longs("time").map { (it ?: 0L) * 1000 }
            return models.mapNotNull { (id, label) ->
                // With a single model Open-Meteo omits the suffix.
                val temp = (h.a("temperature_2m_$id") ?: h.a("temperature_2m"))?.map { it.dbl() } ?: return@mapNotNull null
                val prec = (h.a("precipitation_$id") ?: h.a("precipitation"))?.map { it.dbl() } ?: emptyList()
                if (temp.all { it == null }) return@mapNotNull null
                ModelSeries(id, label, times, temp, prec)
            }
        }

        fun parseGeocoding(root: JsonElement): List<Place> {
            val results = root.obj()?.a("results") ?: return emptyList()
            return results.mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                Place(
                    id = "geo:" + (r.l("id") ?: "${r.d("latitude")},${r.d("longitude")}"),
                    name = r.s("name") ?: return@mapNotNull null,
                    region = r.s("admin1"),
                    country = r.s("country"),
                    countryCode = r.s("country_code"),
                    latitude = r.d("latitude") ?: return@mapNotNull null,
                    longitude = r.d("longitude") ?: return@mapNotNull null,
                )
            }
        }
    }
}
