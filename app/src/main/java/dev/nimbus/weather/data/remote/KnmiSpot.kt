/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/KnmiSpot.kt
 * The KNMI's radar over the place, where the DWD's has no data there: measured and two hours ahead.
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
import dev.nimbus.weather.data.model.RadarSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * The KNMI's composite (the Netherlands and its neighbours, the north-west of Germany too) at the
 * place: its reflectivity every 5 minutes, 1 km, read as the DWD's ([SpotSource.rate]), and its
 * nowcast (pySTEPS, 1 km, 5-minute steps for two hours, mm/h). Both answer a point request
 * without a key; the nowcast's service allows 1 request a second and 1000 an hour for all who
 * ask without one – it is asked only where the DWD's radar has no data over the place.
 */
class KnmiSpot(
    private val http: OkHttpClient,
    private val radar: String = "https://geoservices.knmi.nl/adagucserver",
    private val nowcast: String = "https://anonymous.api.dataplatform.knmi.nl/wms/adaguc-server",
    /** The pause before asking the nowcast's service again (it allows one request a second). */
    private val againMs: Long = 1_100L,
) {
    /**
     * The measured steps at the place from [from] to [to]: mm in each 5 minutes by their ends; null
     * where the KNMI's radars do not reach (every step "no data") or no answer came. Asked for in
     * stretches of [STRETCH_MS] – the service refuses three days at once.
     */
    suspend fun steps(lat: Double, lon: Double, from: Long, to: Long): Map<Long, Double>? = coroutineScope {
        val start = from / STEP_MS * STEP_MS
        val parts = (start until to step STRETCH_MS).map { s ->
            async { answer { reflectivity(http.getJson(radarUrl(lat, lon, s, minOf(s + STRETCH_MS, to)))) } }
        }.awaitAll()
        if (parts.all { it == null }) return@coroutineScope null
        val dbz = parts.filterNotNull().fold(emptyMap<Long, Double?>()) { a, b -> a + b }
        if (dbz.values.all { it == null }) null
        else dbz.mapNotNull { (t, v) -> v?.let { t to kotlin.math.round(SpotSource.rate(it) / STEPS_PER_HOUR * 1000) / 1000 } }.toMap()
    }

    /** The newest nowcast at [now]: its analysis and mm in each 5 minutes by their ends; null: none. */
    suspend fun nowcast(lat: Double, lon: Double, now: Long): Pair<Long, Map<Long, Double>>? {
        // a nowcast is there about 3 minutes after its analysis: the newest that must be there, else the one before
        val newest = (now - READY_MS) / STEP_MS * STEP_MS
        for (issue in listOf(newest, newest - STEP_MS)) {
            if (issue != newest) delay(againMs)
            val steps = answer { nowcastSteps(http.getJson(nowcastUrl(lat, lon, issue))) }
            if (!steps.isNullOrEmpty()) return issue to steps
        }
        return null
    }

    /**
     * Precipitation over the place now, as [SpotSource.radarNow] tells it from the DWD's: the newest
     * quarter hour of measured steps as a rate, the nowcast after them; null where the KNMI's radars
     * do not reach – its nowcast says "dry" there too, so it is asked only where they measure.
     */
    suspend fun radarNow(lat: Double, lon: Double, now: Long): RadarNow? {
        val measured = steps(lat, lon, now - NOW_SPAN_MS, now) ?: return null
        val r = SpotSource.now(measured) ?: return null
        val ahead = nowcast(lat, lon, now)
        return r.copy(
            steps = measured + ahead?.second.orEmpty().filterKeys { it > r.time },
            issue = ahead?.first,
            source = RadarSource.KNMI,
        )
    }

    /** [ask]'s answer, null when it failed – a cancellation still ends the caller. */
    private suspend fun <T> answer(ask: suspend () -> T): T? =
        runCatching { ask() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    private fun box(lat: Double, lon: Double): String {
        val d = 0.002
        return "${OpenMeteoSource.fmt(lat - d)},${OpenMeteoSource.fmt(lon - d)},${OpenMeteoSource.fmt(lat + d)},${OpenMeteoSource.fmt(lon + d)}"
    }

    private fun point(layer: String, lat: Double, lon: Double) =
        "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=$layer&QUERY_LAYERS=$layer&CRS=EPSG:4326" +
            "&BBOX=${box(lat, lon)}&WIDTH=5&HEIGHT=5&I=2&J=2&INFO_FORMAT=application/json"

    private fun radarUrl(lat: Double, lon: Double, from: Long, to: Long) =
        "$radar?dataset=RADAR&${point("Reflectivity", lat, lon)}&TIME=${iso(from)}/${iso(to)}"

    private fun nowcastUrl(lat: Double, lon: Double, issue: Long) =
        "$nowcast?DATASET=radar_forecast_2.0a&${point("precipitation_nowcast", lat, lon)}" +
            "&TIME=${iso(issue)}/${iso(issue + AHEAD_MS)}&DIM_REFERENCE_TIME=${iso(issue)}"

    companion object {
        private const val STEP_MS = 5 * 60_000L
        private const val STEPS_PER_HOUR = 12.0
        /** The reflectivity is asked for in stretches of 6 hours. */
        const val STRETCH_MS = 6 * 3_600_000L
        /** The KNMI's "no data" in its reflectivity (dry is -32 dBZ). */
        const val NO_DATA_DBZ = 95.5
        /** How long after its analysis a nowcast is there. */
        private const val READY_MS = 4 * 60_000L
        /** The nowcast's reach: two hours. */
        private const val AHEAD_MS = 2 * 3_600_000L
        /** The measured steps [radarNow] asks for: the last hour. */
        private const val NOW_SPAN_MS = 65 * 60_000L

        private const val HOUR_MS = 3_600_000L

        private fun iso(ms: Long) = Instant.ofEpochMilli(ms / 60_000L * 60_000L).toString()

        /**
         * The look-back's hours from [steps] (mm in each 5 minutes by their ends): each complete
         * hour's sum and its strongest step as a rate ([SpotSource.hourEchoes]), by the hour's end.
         */
        fun hours(steps: Map<Long, Double>): Pair<Map<Long, Double>, Map<Long, Double>> {
            val echoes = SpotSource.hourEchoes(steps.mapValues { it.value * STEPS_PER_HOUR })
            val sums = echoes.keys.associateWith { end ->
                kotlin.math.round((0 until 12).sumOf { steps[end - it * STEP_MS] ?: 0.0 } * 100) / 100
            }
            return sums to echoes
        }

        /**
         * Where the look-back lacks the DWD's radar: from the newest hour of [known] after [from] (or
         * [from] itself) – null while it is at most an hour behind the last full hour before [now].
         */
        fun gapFrom(known: Set<Long>, from: Long, now: Long): Long? {
            val lastFull = now / HOUR_MS * HOUR_MS
            val newest = known.filter { it > from }.maxOrNull() ?: return from
            return if (lastFull - newest >= 2 * HOUR_MS) newest else null
        }

        /** The point answer's values by time (the first layer's; under a reference time, its run's). */
        private fun values(root: JsonElement): Map<Long, Double?> {
            val data = (root as? kotlinx.serialization.json.JsonArray)?.firstOrNull().obj()?.o("data") ?: return emptyMap()
            // the nowcast's values lie under its reference time
            val byTime = data.values.firstOrNull()?.let { it as? JsonObject }?.takeIf { data.size == 1 } ?: data
            return byTime.mapNotNull { (k, v) ->
                runCatching { Instant.parse(k).toEpochMilli() }.getOrNull()?.let { it to v.dbl() }
            }.toMap()
        }

        /** The reflectivity's answer: dBZ by time, null where "no data". */
        fun reflectivity(root: JsonElement): Map<Long, Double?> =
            values(root).mapValues { (_, v) -> v?.takeIf { it < NO_DATA_DBZ } }

        /**
         * The nowcast's answer: mm in each 5 minutes by their ends (its values are rates, mm/h); the
         * first step is the analysis itself and is left out.
         */
        fun nowcastSteps(root: JsonElement): Map<Long, Double> {
            val v = values(root).filterValues { it != null && it >= 0 }
            val first = v.keys.minOrNull() ?: return emptyMap()
            return v.filterKeys { it > first }.mapValues { (_, r) -> kotlin.math.round(r!! / STEPS_PER_HOUR * 1000) / 1000 }
        }
    }
}
