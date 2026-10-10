/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SpotSource.kt
 * Measured at the place itself: precipitation from the DWD's radar, sunshine from the satellite –
 * instead of a station 20–40 km away.
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

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * What was measured over the place itself, hour by hour (each value covers the hour before its
 * time stamp, as everywhere in the app):
 * - precipitation from the DWD's radar (RADOLAN, Germany): RW, the hourly sum adjusted to the rain
 *   gauges (half an hour late, a day back); the hours it does not have yet from RY, 5-minute sums
 *   at once – one request each, the values at the place as a time series;
 * - sunshine from the satellite (the DWD's from EUMETSAT MTG, 2.5 km, about 20 minutes late, all of
 *   Europe), via Open-Meteo.
 */
class SpotSource(
    private val http: OkHttpClient,
    private val wms: String = "https://maps.dwd.de/geoserver/dwd/wms",
    private val satellite: String = "https://satellite-api.open-meteo.com",
) {
    /** Precipitation (mm) at the place, by the hour's end, for the hours ending [from] … [to]; empty outside the radar's area. */
    suspend fun radarPrecipitation(lat: Double, lon: Double, from: Long, to: Long): Map<Long, Double> = coroutineScope {
        // RW keeps a day: asked for more, the service took seconds longer
        val rw = async { runCatching { http.getJson(pointUrl(RW, lat, lon, maxOf(from, to - RW_SPAN_MS), to)) }.getOrNull()?.let(::series) }
        val ry = async { runCatching { http.getJson(pointUrl(RY, lat, lon, maxOf(from, to - RY_SPAN_MS), to)) }.getOrNull()?.let(::series) }
        hourly(rw.await().orEmpty(), ry.await().orEmpty())
    }

    /** The satellite's hours at the place, by the hour's end, for the last [pastDays] days and today ([SatelliteSunshine]). */
    suspend fun satelliteHours(lat: Double, lon: Double, pastDays: Int): Map<Long, SatelliteHour> = runCatching {
        val url = "$satellite/v1/archive".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", OpenMeteoSource.fmt(lat))
            .addQueryParameter("longitude", OpenMeteoSource.fmt(lon))
            .addQueryParameter("hourly", "sunshine_duration,direct_normal_irradiance,shortwave_radiation,diffuse_radiation")
            .addQueryParameter("models", SATELLITE_MODEL)
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("past_days", pastDays.toString())
            .addQueryParameter("forecast_days", "1")
            .build().toString()
        satelliteHours(http.getJson(url))
    }.getOrDefault(emptyMap())

    /** The values of [layer] at the place from [from] to [to] (GetFeatureInfo with a time range). */
    private fun pointUrl(layer: String, lat: Double, lon: Double, from: Long, to: Long): String {
        // a box of a few metres around the place, its middle pixel asked for
        val d = 0.002
        return "$wms?service=WMS&version=1.3.0&request=GetFeatureInfo&layers=dwd:$layer&query_layers=dwd:$layer" +
            "&crs=EPSG:4326&bbox=${OpenMeteoSource.fmt(lat - d)},${OpenMeteoSource.fmt(lon - d)},${OpenMeteoSource.fmt(lat + d)},${OpenMeteoSource.fmt(lon + d)}" +
            "&width=5&height=5&i=2&j=2&info_format=application/json&feature_count=$MAX_FEATURES" +
            "&time=${iso(from)}/${iso(to)}"
    }

    companion object {
        private const val RW = "RADOLAN-RW"
        private const val RY = "RADOLAN-RY"
        /** The DWD's sunshine from the satellite (SIS, Europe and Africa). */
        const val SATELLITE_MODEL = "dwd_sis_europe_africa_v4"
        private const val HOUR_MS = 3_600_000L
        private const val STEP_MS = 5 * 60_000L
        /** What RW keeps: a day (and an hour to spare). */
        private const val RW_SPAN_MS = 25 * HOUR_MS
        /** RY is asked for the last hours only: those RW does not have yet. */
        private const val RY_SPAN_MS = 3 * HOUR_MS
        /** RW every 10 minutes for a day, RY every 5 minutes for three hours – and room to spare. */
        private const val MAX_FEATURES = 400

        private fun iso(ms: Long) = Instant.ofEpochMilli(ms / 60_000L * 60_000L).toString()

        /** A time series of a point answer: time → value; "no data" (negative, outside the radars) left out. */
        fun series(root: JsonElement): Map<Long, Double> = root.obj()?.a("features").orEmpty().mapNotNull { e ->
            val p = (e as? JsonObject)?.o("properties") ?: return@mapNotNull null
            val t = runCatching { Instant.parse(p.s("TIME")).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val v = p.d("GRAY_INDEX")?.takeIf { it >= 0.0 } ?: return@mapNotNull null
            t to v
        }.toMap()

        /**
         * The hours: RW's at the full hours (its sum of the hour before); an hour it does not have
         * yet, the sum of RY's twelve 5-minute steps (each ending at its time) – if all are there.
         */
        fun hourly(rw: Map<Long, Double>, ry: Map<Long, Double>): Map<Long, Double> {
            val out = rw.filterKeys { it % HOUR_MS == 0L }.toMutableMap()
            val ends = ry.keys.map { (it + HOUR_MS - 1) / HOUR_MS * HOUR_MS }.toSortedSet()
            for (end in ends) {
                if (end in out) continue
                val steps = (0 until 12).map { ry[end - it * STEP_MS] }
                if (steps.all { it != null }) out[end] = (steps.sumOf { it!! } * 100).let { kotlin.math.round(it) / 100 }
            }
            return out
        }

        /** Open-Meteo's satellite answer: the hour's end → minutes of sunshine (null hours left out). */
        fun satelliteHours(root: JsonElement): Map<Long, SatelliteHour> {
            val h = root.obj()?.o("hourly") ?: return emptyMap()
            val t = h.longs("time"); val s = h.doubles("sunshine_duration"); val dni = h.doubles("direct_normal_irradiance")
            val ghi = h.doubles("shortwave_radiation"); val dif = h.doubles("diffuse_radiation")
            return t.indices.mapNotNull { i ->
                val time = (t[i] ?: return@mapNotNull null) * 1000
                time to SatelliteHour(s.at(i)?.div(60.0), dni.at(i), ghi.at(i), dif.at(i))
            }.toMap()
        }

        /** The satellite's hours at [lat]/[lon] in minutes of sunshine ([SatelliteSunshine]). */
        fun sunshine(root: JsonElement, lat: Double, lon: Double, lowCloud: (Long) -> Double? = { null }): Map<Long, Double> =
            SatelliteSunshine.minutes(satelliteHours(root), lat, lon, lowCloud)
    }
}
