/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/GaugeSource.kt
 * Water levels of the federal waterways from PEGELONLINE, with tide predictions for tide gauges.
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

import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.LevelSample
import dev.nimbus.weather.util.Tides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.time.OffsetDateTime
import kotlin.math.cos
import kotlin.math.hypot

/**
 * PEGELONLINE (Wasserstraßen- und Schifffahrtsverwaltung des Bundes), open data under
 * "Datenlizenz Deutschland – Zero – 2.0". Covers the federal waterways: rivers, the coast and
 * the islands. Canals are skipped – their level is regulated and says nothing about the weather.
 *
 * Tide gauges (with mean high/low tide values) get a prediction: four weeks of minute data are
 * fitted once ([Tides.fit], ~3 MB download) and the model is kept for [TIDE_REFIT_MS].
 */
class GaugeSource(
    private val http: OkHttpClient,
    private val cacheDir: File? = null,
    private val baseUrl: String = "https://www.pegelonline.wsv.de/webservices/rest-api/v2",
) {
    suspend fun nearest(lat: Double, lon: Double, radiusKm: Int = RADIUS_KM, now: Long = System.currentTimeMillis()): GaugeInfo? {
        val url = "$baseUrl/stations.json".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", OpenMeteoSource.fmt(lat))
            .addQueryParameter("longitude", OpenMeteoSource.fmt(lon))
            .addQueryParameter("radius", radiusKm.toString())
            .addQueryParameter("includeTimeseries", "true")
            .addQueryParameter("includeCurrentMeasurement", "true")
            .addQueryParameter("includeCharacteristicValues", "true")
            .build().toString()
        val root = http.getJson(url)
        val station = pickStation(root, lat, lon) ?: return null
        val tidal = "MThw" in station.marks && "MTnw" in station.marks
        // Tide gauges: 36 hours are plenty for the curve. Others a week – three days for stations
        // that measure every minute (the Baltic coast), whose week would be ~750 kB.
        val minuteData = equidistance(root, station.uuid)?.let { it <= 1 } ?: tidal
        val period = when { tidal -> "PT36H"; minuteData -> "P3D"; else -> "P7D" }
        val history = runCatching { measurements(station.uuid, period) }.getOrDefault(emptyList())
        var info = station.copy(history = binned(history))
        if (tidal) {
            val model = runCatching { tideModel(station.uuid, now) }.getOrNull()
            if (model != null) {
                info = info.copy(
                    prediction = Tides.curve(model, now - 3 * 3_600_000L, now + 48 * 3_600_000L).map { LevelSample(it.first, it.second) },
                    extremes = Tides.extremes(model, now - 7 * 3_600_000L, now + 48 * 3_600_000L),
                    predictionRms = model.rms,
                )
            }
        }
        return info
    }

    private suspend fun measurements(uuid: String, period: String): List<LevelSample> {
        val url = "$baseUrl/stations/$uuid/W/measurements.json".toHttpUrl().newBuilder()
            .addQueryParameter("start", period).build().toString()
        return parseMeasurements(http.getJson(url))
    }

    /** Tide model from the disk cache, refitted when older than [TIDE_REFIT_MS]. */
    private suspend fun tideModel(uuid: String, now: Long): Tides.Model? {
        val file = cacheDir?.let { File(it, "tide_$uuid.json") }
        val cached = file?.takeIf { it.exists() }?.let { f ->
            withContext(Dispatchers.IO) { runCatching { JsonCodec.decodeFromString(Tides.Model.serializer(), f.readText()) }.getOrNull() }
        }
        if (cached != null && now - cached.fittedAt < TIDE_REFIT_MS) return cached
        val t0 = System.currentTimeMillis()
        val data = runCatching { measurements(uuid, "P31D") }
            .onFailure { android.util.Log.w("Nimbus", "tide data unavailable: $it") }.getOrNull() ?: return cached
        val t1 = System.currentTimeMillis()
        val model = withContext(Dispatchers.Default) {
            Tides.fit(data.map { it.time }.toLongArray(), data.map { it.value }.toDoubleArray(), now)
        } ?: return cached
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("Nimbus", "tide fit $uuid: ${data.size} values, download+parse ${t1 - t0} ms, fit ${System.currentTimeMillis() - t1} ms, rms ${model.rms}")
        file?.let { f -> withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(JsonCodec.encodeToString(Tides.Model.serializer(), model)) } }
        return model
    }

    companion object {
        const val RADIUS_KM = 10
        const val TIDE_REFIT_MS = 14 * 24 * 3_600_000L

        /** Nearest station with a water level series, canals excluded. */
        fun pickStation(root: JsonElement, lat: Double, lon: Double): GaugeInfo? {
            val stations = (root as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            return stations.mapNotNull { s ->
                val water = s.o("water")?.s("longname") ?: return@mapNotNull null
                if (water.contains("KANAL", ignoreCase = true)) return@mapNotNull null
                val series = s.a("timeseries")?.mapNotNull { it as? JsonObject }.orEmpty()
                val w = series.firstOrNull { it.s("shortname") == "W" } ?: return@mapNotNull null
                val sLat = s.d("latitude") ?: return@mapNotNull null
                val sLon = s.d("longitude") ?: return@mapNotNull null
                val dist = hypot((sLat - lat) * 111.2, (sLon - lon) * 111.2 * cos(Math.toRadians(lat)))
                val current = w.o("currentMeasurement")
                val q = series.firstOrNull { it.s("shortname") == "Q" }?.o("currentMeasurement")
                val qTime = q?.s("timestamp")?.let { parseTime(it) }
                GaugeInfo(
                    uuid = s.s("uuid") ?: return@mapNotNull null,
                    name = s.s("longname") ?: s.s("shortname") ?: "",
                    water = water,
                    distanceKm = dist,
                    tidal = false,
                    gaugeZero = w.o("gaugeZero")?.d("value"),
                    level = current?.d("value"),
                    levelTime = current?.s("timestamp")?.let { parseTime(it) },
                    state = current?.s("stateMnwMhw")?.takeIf { it != "unknown" },
                    marks = w.a("characteristicValues")?.mapNotNull { c ->
                        val o = c as? JsonObject ?: return@mapNotNull null
                        val k = o.s("shortname") ?: return@mapNotNull null
                        val v = o.d("value") ?: return@mapNotNull null
                        k to v
                    }?.toMap().orEmpty(),
                    // Discharge only if it is current (some stations publish it days late).
                    discharge = q?.d("value")?.takeIf { qTime != null && System.currentTimeMillis() - qTime < 24 * 3_600_000L },
                )
            }.minByOrNull { it.distanceKm }?.let { it.copy(tidal = "MThw" in it.marks && "MTnw" in it.marks) }
        }

        /** Measuring interval of the station's water level series in minutes. */
        fun equidistance(root: JsonElement, uuid: String): Int? =
            (root as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("uuid") == uuid }
                ?.a("timeseries")?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("shortname") == "W" }
                ?.d("equidistance")?.toInt()

        fun parseMeasurements(root: JsonElement): List<LevelSample> =
            (root as? JsonArray)?.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val t = o.s("timestamp")?.let { parseTime(it) } ?: return@mapNotNull null
                val v = o.d("value") ?: return@mapNotNull null
                LevelSample(t, v)
            }.orEmpty()

        /** 10-minute means (minute data of tide gauges would be needlessly large to keep). */
        fun binned(samples: List<LevelSample>, binMs: Long = 10 * 60_000L): List<LevelSample> =
            samples.groupBy { it.time / binMs }.map { (b, list) -> LevelSample(b * binMs + binMs / 2, list.sumOf { it.value } / list.size) }
                .sortedBy { it.time }

        private fun parseTime(s: String): Long? = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }
}
