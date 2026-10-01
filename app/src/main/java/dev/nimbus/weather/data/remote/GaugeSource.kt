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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import dev.nimbus.weather.data.model.GaugeProvider
import kotlin.math.abs
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.time.OffsetDateTime

/**
 * Water levels around a place from all open sources – see docs/GAUGES.md for the state of the
 * support per federal state.
 *
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
    /** State gauges of Lower Saxony (rivers that are not federal waterways). */
    private val nlwkn: NlwknSource? = NlwknSource(http, cacheDir),
    private val nrw: WiskiSource? = WiskiSource(http, WiskiSource.NRW, cacheDir),
    private val hessen: WiskiSource? = WiskiSource(http, WiskiSource.HESSEN, cacheDir),
    private val sachsen: SachsenSource? = SachsenSource(http),
    /** Classification of all state gauges (no values) and the state flood alerts. */
    val lhp: LhpSource? = LhpSource(http),
) {
    /**
     * Gauges around a place, one per water body (Hann. Münden: Weser, Fulda and Werra), at most
     * [MAX_GAUGES], a tide gauge first. All sources are asked in parallel; each may fail on its own.
     * Details (course of the week, tide prediction) are loaded only for the chosen gauges.
     */
    suspend fun nearby(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): List<GaugeInfo> = coroutineScope {
        val r = FALLBACK_RADIUS_KM.toDouble()   // candidates up to the fallback; select() narrows to RADIUS_KM
        suspend fun <T> safe(name: String, block: suspend () -> List<T>): List<T> =
            runCatching { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { block() }.orEmpty() }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; android.util.Log.w("Nimbus", "gauges $name: $it") }
                .getOrDefault(emptyList())
        val jobs = listOf(
            async { safe("pegelonline") { federalCandidates(lat, lon) } },
            async { safe("nlwkn") { nlwkn?.candidates(lat, lon, r, now).orEmpty() } },
            async { safe("nrw") { nrw?.candidates(lat, lon, r, now).orEmpty() } },
            async { safe("hessen") { hessen?.candidates(lat, lon, r, now).orEmpty() } },
            async { safe("sachsen") { sachsen?.candidates(lat, lon, r).orEmpty() } },
            async { safe("lhp") { lhp?.candidates(lat, lon, r, now).orEmpty() } },
        )
        val chosen = select(jobs.flatMap { it.await() })
        chosen.map { g -> async { withTimeoutOrNull(DETAIL_TIMEOUT_MS) { runCatching { details(g, now) }.getOrNull() } ?: g.takeIf { it.hasValues || it.lhpClass != null } } }
            .mapNotNull { it.await() }
    }

    /** Values, history and (tide gauges) prediction of a chosen gauge; null drops it. */
    private suspend fun details(g: GaugeInfo, now: Long): GaugeInfo? = when (g.provider) {
        GaugeProvider.PEGELONLINE -> federalDetails(g, now)
        GaugeProvider.NLWKN -> nlwkn?.details(g)?.let { mergeLhp(it, g) } ?: g.takeIf { it.lhpClass != null }?.asLhpOnly()
        GaugeProvider.LANUK_NRW -> nrw?.details(g)?.let { mergeLhp(it, g) } ?: g.takeIf { it.lhpClass != null }?.asLhpOnly()
        GaugeProvider.HLNUG_HESSEN -> hessen?.details(g)?.let { mergeLhp(it, g) } ?: g.takeIf { it.lhpClass != null }?.asLhpOnly()
        GaugeProvider.LFULG_SACHSEN, GaugeProvider.LHP -> g
    }

    private suspend fun federalCandidates(lat: Double, lon: Double): List<GaugeInfo> {
        val url = "$baseUrl/stations.json".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", OpenMeteoSource.fmt(lat))
            .addQueryParameter("longitude", OpenMeteoSource.fmt(lon))
            .addQueryParameter("radius", TIDE_RADIUS_KM.toString())
            .addQueryParameter("includeTimeseries", "true")
            .addQueryParameter("includeCurrentMeasurement", "true")
            .addQueryParameter("includeCharacteristicValues", "true")
            .build().toString()
        return parseStations(http.getJson(url), lat, lon)
    }

    private suspend fun federalDetails(station: GaugeInfo, now: Long): GaugeInfo {
        // Tide gauges: 36 hours are plenty for the curve. Others a week – three days for stations
        // that measure every minute (the Baltic coast), whose week would be ~750 kB.
        val period = when { station.tidal -> "PT36H"; station.minuteData -> "P3D"; else -> "P7D" }
        val history = runCatching { measurements(station.uuid, period) }.getOrDefault(emptyList())
        var info = station.copy(history = binned(history))
        if (station.tidal) {
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

    /**
     * Fits run in their own scope: a slow first download (~3 MB) must not be cancelled by the time
     * limit of the weather load – the model is stored and used from the next refresh on.
     */
    private val fitScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val fits = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Tides.Model?>>()

    /** Tide model from the disk cache, refitted when older than [TIDE_REFIT_MS]. */
    private suspend fun tideModel(uuid: String, now: Long): Tides.Model? {
        val file = cacheDir?.let { File(it, "tide_$uuid.json") }
        val cached = file?.takeIf { it.exists() }?.let { f ->
            withContext(Dispatchers.IO) { runCatching { JsonCodec.decodeFromString(Tides.Model.serializer(), f.readText()) }.getOrNull() }
        }
        if (cached != null && now - cached.fittedAt < TIDE_REFIT_MS) return cached
        val job = fits.getOrPut(uuid) {
            fitScope.async { runCatching { fitNew(uuid, now, file) }.getOrNull().also { fits.remove(uuid) } }
        }
        return withTimeoutOrNull(TIDE_WAIT_MS) { job.await() } ?: cached
    }

    private suspend fun fitNew(uuid: String, now: Long, file: File?): Tides.Model? {
        val t0 = System.currentTimeMillis()
        val data = runCatching { measurements(uuid, "P31D") }
            .onFailure { android.util.Log.w("Nimbus", "tide data unavailable: $it") }.getOrNull() ?: return null
        val t1 = System.currentTimeMillis()
        val model = withContext(Dispatchers.Default) {
            Tides.fit(data.map { it.time }.toLongArray(), data.map { it.value }.toDoubleArray(), now)
        } ?: return null
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("Nimbus", "tide fit $uuid: ${data.size} values, download+parse ${t1 - t0} ms, fit ${System.currentTimeMillis() - t1} ms, rms ${model.rms}")
        file?.let { f -> withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(JsonCodec.encodeToString(Tides.Model.serializer(), model)) } }
        return model
    }

    companion object {
        const val RADIUS_KM = 10
        /**
         * Without any gauge within [RADIUS_KM], the nearest one up to this distance stands in – e.g. in
         * the east of Hannover, ~11 km from the Leine gauge Herrenhausen.
         */
        const val FALLBACK_RADIUS_KM = 20
        const val MAX_GAUGES = 4
        private const val SOURCE_TIMEOUT_MS = 12_000L
        /** The first tide fit downloads ~3 MB. */
        private const val DETAIL_TIMEOUT_MS = 20_000L
        /** How long a load waits for a new tide fit before showing the gauge without prediction. */
        private const val TIDE_WAIT_MS = 15_000L
        const val TIDE_REFIT_MS = 14 * 24 * 3_600_000L

        /**
         * The tide changes only slowly along the coast: a tide gauge counts up to this distance
         * (e.g. the town of Norden, 11.6 km from Norderney), other gauges only within [RADIUS_KM].
         */
        const val TIDE_RADIUS_KM = 25

        /** PEGELONLINE stations with a water level series, canals excluded. */
        fun parseStations(root: JsonElement, lat: Double, lon: Double): List<GaugeInfo> {
            val stations = (root as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            return stations.mapNotNull { s ->
                val water = s.o("water")?.s("longname") ?: return@mapNotNull null
                if (water.contains("KANAL", ignoreCase = true)) return@mapNotNull null
                val series = s.a("timeseries")?.mapNotNull { it as? JsonObject }.orEmpty()
                val w = series.firstOrNull { it.s("shortname") == "W" } ?: return@mapNotNull null
                val sLat = s.d("latitude") ?: return@mapNotNull null
                val sLon = s.d("longitude") ?: return@mapNotNull null
                val current = w.o("currentMeasurement")
                val q = series.firstOrNull { it.s("shortname") == "Q" }?.o("currentMeasurement")
                val qTime = q?.s("timestamp")?.let { parseTime(it) }
                val marks = w.a("characteristicValues")?.mapNotNull { c ->
                    val o = c as? JsonObject ?: return@mapNotNull null
                    val k = o.s("shortname") ?: return@mapNotNull null
                    val v = o.d("value") ?: return@mapNotNull null
                    k to v
                }?.toMap().orEmpty()
                GaugeInfo(
                    uuid = s.s("uuid") ?: return@mapNotNull null,
                    name = s.s("longname") ?: s.s("shortname") ?: "",
                    water = water,
                    distanceKm = GaugeGeo.distanceKm(lat, lon, sLat, sLon),
                    tidal = "MThw" in marks && "MTnw" in marks,
                    gaugeZero = w.o("gaugeZero")?.d("value"),
                    level = current?.d("value"),
                    levelTime = current?.s("timestamp")?.let { parseTime(it) },
                    state = current?.s("stateMnwMhw")?.takeIf { it != "unknown" },
                    marks = marks,
                    // Discharge only if it is current (some stations publish it days late).
                    discharge = q?.d("value")?.takeIf { qTime != null && System.currentTimeMillis() - qTime < 24 * 3_600_000L },
                    minuteData = (w.d("equidistance")?.toInt() ?: 15) <= 1,
                )
            }
        }

        /** Nearest gauge in [radiusKm] (tide gauges up to [TIDE_RADIUS_KM]) – kept for single-gauge callers. */
        fun pickStation(root: JsonElement, lat: Double, lon: Double, radiusKm: Double = RADIUS_KM.toDouble()): GaugeInfo? =
            select(parseStations(root, lat, lon), radiusKm).firstOrNull()

        /** "Weser", "WESER", "Weser (Tideweser)" -> "weser" */
        fun waterKey(w: String): String = w.lowercase().replace(Regex("\\(.*?\\)"), "").replace("ß", "ss").trim()

        private val PRIORITY = listOf(GaugeProvider.PEGELONLINE, GaugeProvider.NLWKN, GaugeProvider.LANUK_NRW, GaugeProvider.HLNUG_HESSEN, GaugeProvider.LFULG_SACHSEN, GaugeProvider.LHP)

        /** Same gauge from two sources: same water and closer than this. */
        private const val SAME_GAUGE_KM = 1.5

        /**
         * Chooses the gauges to show from all candidates:
         * 1. duplicates (a federal gauge also listed by a state or the LHP) are merged, the source
         *    with values wins and takes over the LHP classification and link;
         * 2. only gauges within [radiusKm] count – if there is no tide gauge among them, the nearest
         *    one up to [TIDE_RADIUS_KM]; with none at all, the nearest gauge up to [fallbackKm];
         * 3. per water body the nearest one (a gauge with values is preferred over a classification
         *    only one, if it is less than 3 km farther away);
         * 4. a tide gauge first, then by distance, at most [max].
         */
        fun select(
            all: List<GaugeInfo>, radiusKm: Double = RADIUS_KM.toDouble(), max: Int = MAX_GAUGES,
            fallbackKm: Double = FALLBACK_RADIUS_KM.toDouble(),
        ): List<GaugeInfo> {
            val sorted = all.sortedBy { PRIORITY.indexOf(it.provider) }
            val merged = ArrayList<GaugeInfo>()
            for (g in sorted) {
                val i = merged.indexOfFirst { m ->
                    (waterKey(m.water) == waterKey(g.water) || m.name.equals(g.name, ignoreCase = true)) &&
                        abs(m.distanceKm - g.distanceKm) < SAME_GAUGE_KM
                }
                if (i < 0) { merged += g; continue }
                val m = merged[i]
                merged[i] = m.copy(
                    lhpClass = m.lhpClass ?: g.lhpClass, lhpClassName = m.lhpClassName ?: g.lhpClassName, link = m.link ?: g.link,
                )
            }
            // Farther tide gauges only stand in when there is none within the radius (Norden -> Norderney).
            val near = merged.filter { it.distanceKm <= radiusKm }
            val farTide = if (near.any { it.tidal }) null
            else merged.filter { it.tidal && it.distanceKm <= TIDE_RADIUS_KM }.minByOrNull { it.distanceKm }
            val inRange = (near + listOfNotNull(farTide)).ifEmpty {
                listOfNotNull(merged.filter { it.distanceKm <= fallbackKm }.minByOrNull { it.distanceKm + if (it.provider == GaugeProvider.LHP) 3.0 else 0.0 })
            }
            val perWater = inRange.groupBy { waterKey(it.water).ifEmpty { it.uuid } }.values.map { group ->
                group.minBy { it.distanceKm + if (it.provider == GaugeProvider.LHP) 3.0 else 0.0 }
            }
            return perWater.sortedWith(compareBy({ !it.tidal }, { it.distanceKm })).take(max)
        }

        private fun mergeLhp(detailed: GaugeInfo, candidate: GaugeInfo) = detailed.copy(
            distanceKm = candidate.distanceKm,
            lhpClass = candidate.lhpClass, lhpClassName = candidate.lhpClassName, link = detailed.link ?: candidate.link,
        )

        /** A state gauge whose values could not be loaded: keep it with the LHP classification. */
        private fun GaugeInfo.asLhpOnly() = copy(provider = GaugeProvider.LHP)

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
