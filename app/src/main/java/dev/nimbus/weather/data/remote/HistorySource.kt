/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/HistorySource.kt
 * The past days: DWD station measurements next to the model forecast.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherCodes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One past hour: DWD station measurement (if available) next to the model value. */
data class HistoryHour(
    val time: Long,
    val measured: Measured?,
    val model: Modelled?,
) {
    data class Measured(
        val temperature: Double?, val precipitation: Double?, val windSpeed: Double?, val windGust: Double?,
        val windDirection: Double?, val sunshineMinutes: Double?, val cloudCover: Double?, val condition: Condition?,
        /** Air pressure reduced to sea level (hPa). */
        val pressure: Double? = null,
    )

    data class Modelled(
        val temperature: Double?, val precipitation: Double?, val windSpeed: Double?, val windGust: Double?,
        val sunshineMinutes: Double?, val condition: Condition, val isDay: Boolean,
        /** Chance of precipitation (%) the model gave for the hour. */
        val chance: Double? = null,
        val windDirection: Double? = null,
    )
}

data class HistoryDay(val date: LocalDate, val hours: List<HistoryHour>)

data class History(
    val days: List<HistoryDay>,          // oldest first: day before yesterday, yesterday, today so far
    val stationName: String?,
    val stationDistanceKm: Double?,
    val modelId: String,
    val zone: ZoneId,
    val fetchedAt: Long,
    /** Station temperature every 10 minutes (SYNOP; about the last 1½ days), by time. */
    val fineMeasured: Map<Long, Double> = emptyMap(),
    /** The model's temperature every 15 minutes, by time. */
    val fineModel: Map<Long, Double> = emptyMap(),
    /**
     * Every hour in time order, across the days and into tomorrow's first hour: the day charts
     * show 00–24 plus the 24 column (the value of 00–01 the next day), and a day's 23–24 value
     * carries tomorrow's date (its time stamp is 00:00).
     */
    val allHours: List<HistoryHour> = days.flatMap { it.hours },
) {
    /** The hours of the day chart starting at [dayStart]: its 00:00 value through the 24 column. */
    fun chartHours(dayStart: Long): List<HistoryHour> = allHours.filter { it.time in dayStart..dayStart + 25 * 3_600_000L }
}

/** A 10-minute station report (SYNOP). Sums refer to the period before [time]. */
data class SynopReport(
    val time: Long, val temperature: Double?, val precipitation10: Double?, val precipitation60: Double?,
    val windSpeed: Double?, val windGust: Double?, val windDirection: Double?, val sunshine60: Double?,
    val pressure: Double?, val cloudCover: Double?, val condition: String?, val icon: String?,
)

/**
 * Past 48 h for a place, loaded on demand (nothing is stored permanently):
 * - DWD station observations via Bright Sky (Germany only). Bright Sky fills hours that are not
 *   yet observed with MOSMIX forecasts – those are dropped, only real measurements are kept.
 * - The selected model's hourly values for the same hours via Open-Meteo `past_days`.
 */
class HistorySource(
    private val http: OkHttpClient,
    private val openMeteoUrl: String = "https://api.open-meteo.com",
    private val brightSkyUrl: String = "https://api.brightsky.dev",
) {
    suspend fun load(lat: Double, lon: Double, model: String, inGermany: Boolean, now: Long = System.currentTimeMillis()): History =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { coroutineScope {
        val modelJob = async { http.getJson(modelUrl(lat, lon, model)) }
        val obsJob = async {
            if (!inGermany) null else runCatching {
                // Zone is not known before the model answer; Germany is always Europe/Berlin.
                val zone = ZoneId.of("Europe/Berlin")
                val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val url = "$brightSkyUrl/weather".toHttpUrl().newBuilder()
                    .addQueryParameter("lat", OpenMeteoSource.fmt(lat))
                    .addQueryParameter("lon", OpenMeteoSource.fmt(lon))
                    .addQueryParameter("date", today.minusDays(2).toString())
                    .addQueryParameter("last_date", today.plusDays(1).toString())
                    .addQueryParameter("tz", "Europe/Berlin")
                    .build()
                http.getJson(url.toString())
            }.getOrNull()
        }
        val modelRoot = modelJob.await()
        val obsRoot = obsJob.await()
        // 10-minute reports of the station that reports now (the hourly values lag 1–2 hours behind)
        val synop = obsRoot?.let { root -> synopStation(root) }?.let { station ->
            runCatching {
                val from = Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Berlin")).toLocalDate().minusDays(1)
                    .atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant()
                val url = "$brightSkyUrl/synop".toHttpUrl().newBuilder()
                    .addQueryParameter("dwd_station_id", station)
                    .addQueryParameter("date", from.toString())
                    .addQueryParameter("last_date", Instant.ofEpochMilli(now + 3_600_000L).toString())
                    .build()
                parseSynop(http.getJson(url.toString()))
            }.getOrNull()
        }.orEmpty()
        combine(modelRoot, obsRoot, model, now, synop)
    } }

    private fun modelUrl(lat: Double, lon: Double, model: String) = "$openMeteoUrl/v1/forecast".toHttpUrl().newBuilder()
        .addQueryParameter("latitude", OpenMeteoSource.fmt(lat))
        .addQueryParameter("longitude", OpenMeteoSource.fmt(lon))
        .addQueryParameter("models", model)
        .addQueryParameter("past_days", "2")
        .addQueryParameter("forecast_days", "2")
        .addQueryParameter("timezone", "auto")
        .addQueryParameter("timeformat", "unixtime")
        .addQueryParameter("wind_speed_unit", "kmh")
        .addQueryParameter("hourly", "temperature_2m,precipitation,precipitation_probability,weather_code,is_day,wind_speed_10m,wind_gusts_10m,wind_direction_10m,cloud_cover,sunshine_duration")
        .addQueryParameter("minutely_15", "temperature_2m")
        .build().toString()

    companion object {
        data class Observations(val byTime: Map<Long, HistoryHour.Measured>, val station: String?, val distanceKm: Double?)

        fun parseModel(root: JsonElement): Pair<ZoneId, Map<Long, HistoryHour.Modelled>> {
            val o = root.obj() ?: error("Invalid Open-Meteo response")
            val zone = o.s("timezone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
            val h = o.o("hourly") ?: return zone to emptyMap()
            val t = h.longs("time")
            val temp = h.doubles("temperature_2m"); val pr = h.doubles("precipitation"); val wc = h.doubles("weather_code")
            val day = h.doubles("is_day"); val ws = h.doubles("wind_speed_10m"); val wg = h.doubles("wind_gusts_10m")
            val sun = h.doubles("sunshine_duration"); val pp = h.doubles("precipitation_probability")
            val wd = h.doubles("wind_direction_10m")
            val map = t.indices.mapNotNull { i ->
                val time = (t[i] ?: return@mapNotNull null) * 1000
                time to HistoryHour.Modelled(
                    temperature = temp.at(i), precipitation = pr.at(i), windSpeed = ws.at(i), windGust = wg.at(i),
                    sunshineMinutes = sun.at(i)?.div(60.0),
                    condition = WeatherCodes.withSunshine(WeatherCodes.fromWmo(wc.at(i)?.toInt(), pr.at(i)), sun.at(i)?.div(60.0)),
                    isDay = (day.at(i) ?: 1.0) > 0.5, chance = pp.at(i), windDirection = wd.at(i),
                )
            }.toMap()
            return zone to map
        }

        /** The model's 15-minute temperatures (empty if not delivered). */
        fun parseModelFine(root: JsonElement): Map<Long, Double> {
            val m = root.obj()?.o("minutely_15") ?: return emptyMap()
            val t = m.longs("time"); val temp = m.doubles("temperature_2m")
            return t.indices.mapNotNull { i -> val time = t[i] ?: return@mapNotNull null; temp.at(i)?.let { time * 1000 to it } }.toMap()
        }

        /** DWD id of the station reporting now (the "current" source of the hourly answer). */
        fun synopStation(root: JsonElement): String? = root.obj()?.a("sources")?.mapNotNull { it as? JsonObject }
            ?.filter { it.s("observation_type") == "current" }?.minByOrNull { it.d("distance") ?: Double.MAX_VALUE }?.s("dwd_station_id")

        fun parseSynop(root: JsonElement): List<SynopReport> = root.obj()?.a("weather")?.mapNotNull { e ->
            val w = e as? JsonObject ?: return@mapNotNull null
            val time = runCatching { java.time.OffsetDateTime.parse(w.s("timestamp")).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            SynopReport(
                time, w.d("temperature"), w.d("precipitation_10"), w.d("precipitation_60"),
                w.d("wind_speed_10") ?: w.d("wind_speed_30"), w.d("wind_gust_speed_10") ?: w.d("wind_gust_speed_30"),
                w.d("wind_direction_10") ?: w.d("wind_direction_30"), w.d("sunshine_60"),
                w.d("pressure_msl"), w.d("cloud_cover"), w.s("condition"), w.s("icon"),
            )
        }?.sortedBy { it.time }.orEmpty()

        /**
         * An hour (ending at [end]) from the 10-minute reports, when the hourly values have none yet:
         * temperature, wind and pressure at the full hour; precipitation as the hour's sum (the
         * 60-minute value, else six 10-minute values – an incomplete hour stays without).
         */
        fun hourFromSynop(reports: Map<Long, SynopReport>, end: Long): HistoryHour.Measured? {
            val at = reports[end] ?: return null
            val tens = (0 until 6).map { reports[end - it * 600_000L]?.precipitation10 }
            val precip = at.precipitation60 ?: if (tens.all { it != null }) tens.sumOf { it!! } else null
            return HistoryHour.Measured(
                temperature = at.temperature, precipitation = precip, windSpeed = at.windSpeed, windGust = at.windGust,
                windDirection = at.windDirection, sunshineMinutes = at.sunshine60, cloudCover = at.cloudCover,
                condition = condition(at.condition, at.icon, precip)?.let { WeatherCodes.withSunshine(it, at.sunshine60) }, pressure = at.pressure,
            )
        }

        /** Hourly observations; entries from forecast sources (MOSMIX) are dropped. */
        fun parseObservations(root: JsonElement): Observations {
            val o = root.obj() ?: return Observations(emptyMap(), null, null)
            val sources = o.a("sources")?.mapNotNull { it as? JsonObject }.orEmpty()
            val forecastIds = sources.filter { it.s("observation_type") == "forecast" }.mapNotNull { it.l("id") }.toSet()
            val main = sources.filter { it.l("id") !in forecastIds }.minByOrNull { it.d("distance") ?: Double.MAX_VALUE }
            val map = o.a("weather")?.mapNotNull { e ->
                val w = e as? JsonObject ?: return@mapNotNull null
                if (w.l("source_id") in forecastIds) return@mapNotNull null
                val time = runCatching { java.time.OffsetDateTime.parse(w.s("timestamp")).toInstant().toEpochMilli() }.getOrNull()
                    ?: return@mapNotNull null
                // Like Open-Meteo, sums (precipitation, sunshine) refer to the hour before the timestamp.
                time to HistoryHour.Measured(
                    temperature = w.d("temperature"), precipitation = w.d("precipitation"), windSpeed = w.d("wind_speed"),
                    windGust = w.d("wind_gust_speed"), windDirection = w.d("wind_direction"), sunshineMinutes = w.d("sunshine"),
                    cloudCover = w.d("cloud_cover"),
                    // Bright Sky's icon follows the cloud cover only: the measured sunshine corrects it
                    condition = condition(w.s("condition"), w.s("icon"), w.d("precipitation"))?.let { WeatherCodes.withSunshine(it, w.d("sunshine")) },
                    pressure = w.d("pressure_msl"),
                )
            }?.toMap().orEmpty()
            return Observations(map, main?.s("station_name"), main?.d("distance")?.div(1000.0))
        }

        fun condition(condition: String?, icon: String?, precipitation: Double?): Condition? = when (condition) {
            "fog" -> Condition.FOG
            "rain" -> if ((precipitation ?: 0.0) >= WeatherCodes.HEAVY_RAIN_MM_H) Condition.HEAVY_RAIN else if ((precipitation ?: 0.0) < 0.3) Condition.DRIZZLE else Condition.RAIN
            "sleet" -> Condition.SLEET
            "snow" -> Condition.SNOW
            "hail" -> Condition.SHOWERS
            "thunderstorm" -> Condition.THUNDERSTORM
            else -> when (icon) {
                "clear-day", "clear-night" -> Condition.CLEAR
                "partly-cloudy-day", "partly-cloudy-night" -> Condition.PARTLY_CLOUDY
                "cloudy", "wind" -> Condition.CLOUDY
                "fog" -> Condition.FOG
                else -> null
            }
        }

        fun combine(modelRoot: JsonElement, obsRoot: JsonElement?, model: String, now: Long, synop: List<SynopReport> = emptyList()): History {
            val (zone, modelled) = parseModel(modelRoot)
            val hourly = obsRoot?.let { parseObservations(it) } ?: Observations(emptyMap(), null, null)
            // Hours the hourly values do not have yet, from the 10-minute reports
            val reports = synop.associateBy { it.time }
            val filled = reports.keys.filter { it % 3_600_000L == 0L && it !in hourly.byTime && it <= now }
                .mapNotNull { t -> hourFromSynop(reports, t)?.let { t to it } }
            val obs = hourly.copy(byTime = hourly.byTime + filled)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            // Past hours, and for today the forecast up to midnight (drawn dashed, not counted in the summary)
            val times = (modelled.keys + obs.byTime.keys).filter { t ->
                t <= now || (t in modelled && Instant.ofEpochMilli(t - 1).atZone(zone).toLocalDate() == today)
            }.distinct().sorted()
            val days = (2 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val hours = times.filter { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date }
                    .map { t -> HistoryHour(t, obs.byTime[t], modelled[t]) }
                HistoryDay(date, hours)
            }
            // For the charts: today's forecast on into tomorrow's first hour (the 24 column)
            val chartTimes = (modelled.keys + obs.byTime.keys).filter { t ->
                t <= now || (t in modelled && Instant.ofEpochMilli(t - 3_600_001L).atZone(zone).toLocalDate() <= today)
            }.distinct().sorted()
            return History(
                days, obs.station, obs.distanceKm, model, zone, now,
                allHours = chartTimes.map { t -> HistoryHour(t, obs.byTime[t], modelled[t]) },
                fineMeasured = synop.mapNotNull { r -> r.temperature?.let { r.time to it } }.toMap(),
                fineModel = parseModelFine(modelRoot),
            )
        }
    }
}

/** Summary numbers of a past day; measured values win, the model fills gaps. */
data class DaySummary(
    val tempMax: Double?, val tempMin: Double?, val precipitation: Double?, val sunshineHours: Double?,
    val maxGust: Double?, val maxGustAt: Long?, val meanWind: Double?, val condition: Condition,
    val modelTempMax: Double?, val modelTempMin: Double?, val modelPrecipitation: Double?,
    /** Mean absolute difference model − measurement of the hourly temperature. */
    val tempError: Double?,
    val measured: Boolean,
) {
    companion object {
        /** Summary of the hours up to [now] (today's forecast for the rest of the day is left out). */
        fun of(day: HistoryDay, now: Long = Long.MAX_VALUE): DaySummary {
            val h = day.hours.filter { it.time <= now }
            val mt = h.mapNotNull { it.measured?.temperature }
            val measured = mt.size >= h.size / 2 && mt.isNotEmpty()
            fun pick(m: (HistoryHour) -> Double?, mod: (HistoryHour) -> Double?) = h.mapNotNull { m(it) ?: mod(it) }
            val temps = pick({ it.measured?.temperature }, { it.model?.temperature })
            val gusts = h.mapNotNull { hh -> (hh.measured?.windGust ?: hh.model?.windGust)?.let { hh.time to it } }
            val errors = h.mapNotNull { hh ->
                val a = hh.measured?.temperature; val b = hh.model?.temperature
                if (a != null && b != null) kotlin.math.abs(a - b) else null
            }
            val precip = pick({ it.measured?.precipitation }, { it.model?.precipitation })
            val sun = pick({ it.measured?.sunshineMinutes }, { it.model?.sunshineMinutes })
            return DaySummary(
                tempMax = temps.maxOrNull(), tempMin = temps.minOrNull(),
                precipitation = precip.takeIf { it.isNotEmpty() }?.sum(),
                sunshineHours = sun.takeIf { it.isNotEmpty() }?.sum()?.div(60.0),
                maxGust = gusts.maxByOrNull { it.second }?.second, maxGustAt = gusts.maxByOrNull { it.second }?.first,
                meanWind = pick({ it.measured?.windSpeed }, { it.model?.windSpeed }).takeIf { it.isNotEmpty() }?.average(),
                condition = dominant(h),
                modelTempMax = h.mapNotNull { it.model?.temperature }.maxOrNull(),
                modelTempMin = h.mapNotNull { it.model?.temperature }.minOrNull(),
                modelPrecipitation = h.mapNotNull { it.model?.precipitation }.takeIf { it.isNotEmpty() }?.sum(),
                tempError = errors.takeIf { it.size >= 3 }?.average(),
                measured = measured,
            )
        }

        /** Most characteristic daytime weather: precipitation beats clouds if it lasted ≥ 2 h. */
        fun dominant(hours: List<HistoryHour>): Condition {
            val conds = hours.filter { it.model?.isDay != false }.mapNotNull { it.measured?.condition ?: it.model?.condition }
                .ifEmpty { hours.mapNotNull { it.measured?.condition ?: it.model?.condition } }
            if (conds.isEmpty()) return Condition.CLOUDY
            val precip = conds.filter { it.isPrecipitation }
            if (precip.size >= 2) return precip.groupingBy { it }.eachCount().maxBy { it.value }.key
            return conds.groupingBy { it }.eachCount().maxBy { it.value }.key
        }
    }
}
