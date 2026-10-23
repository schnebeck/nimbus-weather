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

import dev.nimbus.weather.data.model.RadarNow
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
 *   at once – one request each, the values at the place as a time series; the two hours to come
 *   from its nowcast (WN, reflectivity every 5 minutes);
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
        // RW at its full hours only: the whole span (it keeps about 3½ days) in one request – asked
        // for as a range, every 10-minute sum of it came and took seconds longer
        val rw = async { runCatching { http.getJson(pointUrl(RW, lat, lon, fullHours(from, to))) }.getOrNull()?.let(::series) }
        val ry = async { runCatching { http.getJson(pointUrl(RY, lat, lon, maxOf(from, to - RY_SPAN_MS), to)) }.getOrNull()?.let(::series) }
        hourly(rw.await().orEmpty(), ry.await().orEmpty())
    }

    /**
     * The radar's 5-minute steps over the place, hour by hour ([hourEchoes]): for each hour ending
     * [from] … [to] its strongest step (mm/h, 0: no echo); the steps the radar map shows (WN
     * analysis, about 3½ days back). Asked for in stretches of [ECHO_STRETCH_MS] at once, each
     * waiting at most [waitMs]: an hour missing (not all its steps come) is not known.
     */
    suspend fun radarEchoes(lat: Double, lon: Double, from: Long, to: Long, waitMs: Long): Map<Long, Double> = coroutineScope {
        val start = from / HOUR_MS * HOUR_MS
        (start until to step ECHO_STRETCH_MS).map { s ->
            async {
                kotlinx.coroutines.withTimeoutOrNull(waitMs) {
                    runCatching { analysis(http.getJson(pointUrl(WN_ANALYSIS, lat, lon, s + STEP_MS, minOf(s + ECHO_STRETCH_MS, to)))) }.getOrNull()
                }.orEmpty()
            }
        }.flatMap { it.await().entries }.associate { it.key to it.value }.let(::hourEchoes)
    }

    /** RY's measured 5-minute steps over the place (mm by their ends) from [from] to [to]; empty outside the radar's area or without an answer. */
    suspend fun radarSteps(lat: Double, lon: Double, from: Long, to: Long): Map<Long, Double> =
        runCatching { series(http.getJson(pointUrl(RY, lat, lon, from, to))) }.getOrDefault(emptyMap())

    /**
     * Precipitation over the place now: RY's newest 5-minute steps (up to [NOW_STEPS]) as a rate,
     * stamped with the newest one; null outside the radar's area.
     */
    suspend fun radarNow(lat: Double, lon: Double, now: Long): RadarNow? = coroutineScope {
        // the nowcast on its own: without it, what falls now still counts
        val wn = async { runCatching { nowcast(http.getJson(pointUrl(WN, lat, lon, now - NOW_STEPS * STEP_MS, now + AHEAD_MS))) }.getOrNull() }
        val ry = series(http.getJson(pointUrl(RY, lat, lon, now - NOW_SPAN_MS, now)))
        val r = now(ry) ?: return@coroutineScope null.also { wn.cancel() }
        val ahead = wn.await()
        r.copy(steps = ry + ahead?.second.orEmpty().filterKeys { it > r.time }, issue = ahead?.first)
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
    private fun pointUrl(layer: String, lat: Double, lon: Double, from: Long, to: Long): String = pointUrl(layer, lat, lon, "${iso(from)}/${iso(to)}")

    /** The values of [layer] at the place at [times] (GetFeatureInfo: a time range or a list). */
    private fun pointUrl(layer: String, lat: Double, lon: Double, times: String): String {
        // a box of a few metres around the place, its middle pixel asked for
        val d = 0.002
        return "$wms?service=WMS&version=1.3.0&request=GetFeatureInfo&layers=dwd:$layer&query_layers=dwd:$layer" +
            "&crs=EPSG:4326&bbox=${OpenMeteoSource.fmt(lat - d)},${OpenMeteoSource.fmt(lon - d)},${OpenMeteoSource.fmt(lat + d)},${OpenMeteoSource.fmt(lon + d)}" +
            "&width=5&height=5&i=2&j=2&info_format=application/json&feature_count=$MAX_FEATURES" +
            "&time=$times"
    }

    companion object {
        private const val RW = "RADOLAN-RW"
        private const val RY = "RADOLAN-RY"
        /** The DWD's sunshine from the satellite (SIS, Europe and Africa). */
        const val SATELLITE_MODEL = "dwd_sis_europe_africa_v4"
        private const val HOUR_MS = 3_600_000L
        private const val STEP_MS = 5 * 60_000L
        /** The radar map's analysis: reflectivity every 5 minutes, kept about 3½ days. */
        private const val WN_ANALYSIS = "Radar_wn-analysis_1x1km_ger"
        /** The analysis is asked for in stretches of 6 hours: a day at once took the service too long. */
        const val ECHO_STRETCH_MS = 6 * HOUR_MS
        /** RY is asked for the last hours only: those RW does not have yet. */
        private const val RY_SPAN_MS = 3 * HOUR_MS
        /** RW at the full hours of 3½ days, RY every 5 minutes for three hours – and room to spare. */
        private const val MAX_FEATURES = 400

        private fun iso(ms: Long) = Instant.ofEpochMilli(ms / 60_000L * 60_000L).toString()

        /** The full hours from [from] to [to] as a list of times. */
        fun fullHours(from: Long, to: Long): String =
            ((from + HOUR_MS - 1) / HOUR_MS * HOUR_MS..to step HOUR_MS).joinToString(",") { iso(it) }

        /** The analysis' answer: the time of each step → its rate (mm/h, 0: no echo); a step without a value left out. */
        fun analysis(root: JsonElement): Map<Long, Double> = root.obj()?.a("features").orEmpty().mapNotNull { e ->
            val p = (e as? JsonObject)?.o("properties") ?: return@mapNotNull null
            val t = runCatching { Instant.parse(p.s("TIME")).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val dbz = p.keys.firstOrNull { it != "TIME" && it != "REFERENCE_TIME" }?.let { p.d(it) } ?: return@mapNotNull null
            t to kotlin.math.round(rate(dbz) * 100) / 100
        }.toMap()

        /**
         * Each hour (by its end) with all twelve of its steps ([steps]: time → mm/h, each covering
         * the 5 minutes before it): its strongest step.
         */
        fun hourEchoes(steps: Map<Long, Double>): Map<Long, Double> =
            steps.keys.map { (it + HOUR_MS - 1) / HOUR_MS * HOUR_MS }.toSortedSet().mapNotNull { end ->
                val hour = (0 until 12).map { steps[end - it * STEP_MS] ?: return@mapNotNull null }
                end to hour.max()
            }.toMap()

        /** The DWD's nowcast: reflectivity every 5 minutes for two hours from each analysis. */
        private const val WN = "Radar_wn-product_1x1km_ger"
        /** What [radarNow] asks for: the last hour of RY (the running hour's steps so far) … */
        private const val NOW_SPAN_MS = 65 * 60_000L
        /** … and the nowcast as far as it reaches (two hours, and room to spare). */
        private const val AHEAD_MS = 2 * HOUR_MS + 15 * 60_000L
        /** Weaker echoes are no precipitation (as on the radar map). */
        const val WET_DBZ = 8.0
        /** The steps [radarNow] averages: a quarter of an hour. */
        const val NOW_STEPS = 3

        /** The rate (mm/h) of the newest [NOW_STEPS] 5-minute steps of [ry], stamped with the newest; null: none. */
        fun now(ry: Map<Long, Double>): RadarNow? {
            val last = ry.keys.maxOrNull() ?: return null
            val steps = (0 until NOW_STEPS).mapNotNull { ry[last - it * STEP_MS] }
            return RadarNow(last, kotlin.math.round(steps.average() * (HOUR_MS / STEP_MS) * 100) / 100)
        }

        /**
         * Rain from reflectivity (Marshall–Palmer, Z = 200 · R^1.6), in mm per hour; weaker than
         * [WET_DBZ]: none.
         */
        fun rate(dbz: Double): Double = if (dbz < WET_DBZ) 0.0 else Math.pow(Math.pow(10.0, dbz / 10) / 200, 1 / 1.6)

        /** The nowcast's answer: its analysis, and the mm of each 5 minutes by their ends; null: none. */
        fun nowcast(root: JsonElement): Pair<Long?, Map<Long, Double>>? {
            var issue: Long? = null
            val steps = root.obj()?.a("features").orEmpty().mapNotNull { e ->
                val p = (e as? JsonObject)?.o("properties") ?: return@mapNotNull null
                val t = runCatching { Instant.parse(p.s("TIME")).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
                p.s("REFERENCE_TIME")?.let { r -> runCatching { Instant.parse(r).toEpochMilli() }.getOrNull() }?.let { issue = it }
                val dbz = p.keys.firstOrNull { it != "TIME" && it != "REFERENCE_TIME" }?.let { p.d(it) } ?: return@mapNotNull null
                t to kotlin.math.round(rate(dbz) * STEP_MS / HOUR_MS * 1000) / 1000
            }.toMap()
            return if (steps.isEmpty()) null else issue to steps
        }

        /** A time series of a point answer: time → value; "no data" (negative, outside the radars) left out. */
        fun series(root: JsonElement): Map<Long, Double> = root.obj()?.a("features").orEmpty().mapNotNull { e ->
            val p = (e as? JsonObject)?.o("properties") ?: return@mapNotNull null
            val t = runCatching { Instant.parse(p.s("TIME")).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            // the band's value, named by the layer's file: GRAY_INDEX (RY), RW_HDF5 (RW)
            val v = p.keys.firstOrNull { it != "TIME" }?.let { p.d(it) }?.takeIf { it >= 0.0 } ?: return@mapNotNull null
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
