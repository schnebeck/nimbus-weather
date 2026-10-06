/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/BathingSource.kt
 * Official EU bathing waters (EEA) with the latest values of the states that publish them openly.
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

import dev.nimbus.weather.util.Geo
import dev.nimbus.weather.data.model.BathingCategory
import dev.nimbus.weather.data.model.BathingQuality
import dev.nimbus.weather.data.model.BathingSite
import dev.nimbus.weather.data.model.BathingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bathing water card (see docs/BATHING.md):
 * - all official EU bathing waters in the radius and the favourites, from the EEA map service
 *   (Europe-wide; location, kind of water, EU classification, profile link);
 * - the latest sample where the state publishes it openly: Berlin (LAGeSo, CC BY) and
 *   Schleswig-Holstein (CC BY 4.0; a 4 MB file – once a day and on Wi-Fi only);
 * - the sea temperature at coasts from the Open-Meteo marine model (CC BY 4.0).
 */
class BathingSource(
    private val http: OkHttpClient,
    private val cacheDir: File? = null,
    /** Whether large downloads are fine now (Wi-Fi). */
    private val unmetered: () -> Boolean = { true },
) {
    private val mutex = Mutex()
    private var berlin: Pair<Long, List<StateSample>>? = null
    private var sh: Pair<Long, Map<String, StateSample>>? = null

    suspend fun nearby(lat: Double, lon: Double, radiusKm: Int, favorites: Set<String>, now: Long = System.currentTimeMillis()): List<BathingSite> = coroutineScope {
        val nearJob = async { runCatching { eeaNearby(lat, lon, radiusKm.toDouble(), now) }.getOrDefault(emptyList()) }
        val favJob = async { if (favorites.isEmpty()) emptyList() else runCatching { eeaByIds(favorites, lat, lon, now) }.getOrDefault(emptyList()) }
        val sites = (nearJob.await() + favJob.await()).distinctBy { it.id }.sortedBy { it.distanceKm }
        if (sites.isEmpty()) return@coroutineScope sites
        val shown = sites.filter { it.id in favorites } + sites.filter { it.id !in favorites }.take(MAX_ENRICHED)
        val beJob = async {
            if (shown.none { it.id.startsWith("DEBE") }) emptyList()
            else withTimeoutOrNull(STATE_TIMEOUT_MS) { runCatching { berlinSamples(now) }.getOrNull() }.orEmpty()
        }
        val shJob = async {
            if (shown.none { it.id.startsWith("DESH") }) emptyMap()
            else withTimeoutOrNull(SH_TIMEOUT_MS) { runCatching { shSamples(now) }.getOrNull() }.orEmpty()
        }
        val be = beJob.await()
        val shMap = shJob.await()
        var enriched = sites.map { s ->
            when {
                s.id.startsWith("DEBE") -> be.minByOrNull { Geo.distanceKm(s.lat, s.lon, it.lat, it.lon) }
                    ?.takeIf { Geo.distanceKm(s.lat, s.lon, it.lat, it.lon) <= MATCH_KM }?.let { s.with(it) } ?: s
                s.id.startsWith("DESH") -> shMap[s.id]?.let { s.with(it) } ?: s
                else -> s
            }
        }
        // Sea temperature where no recent sample exists (coasts and estuaries)
        val shownIds = shown.map { it.id }.toSet()
        val sea = enriched.filter { s ->
            s.id in shownIds && (s.category == BathingCategory.COAST || s.category == BathingCategory.TRANSITIONAL) &&
                (s.waterTempTime == null || now - s.waterTempTime > SAMPLE_FRESH_MS)
        }
        if (sea.isNotEmpty()) {
            val sst = withTimeoutOrNull(STATE_TIMEOUT_MS) { runCatching { seaTemperatures(sea) }.getOrNull() }.orEmpty()
            enriched = enriched.map { s -> sst[s.id]?.let { (t, time) -> s.copy(waterTemp = t, waterTempTime = time, waterTempFromModel = true) } ?: s }
        }
        enriched
    }

    // --- EEA -------------------------------------------------------------------------------

    private suspend fun eeaNearby(lat: Double, lon: Double, radiusKm: Double, now: Long): List<BathingSite> {
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(lat)))
        val url = "$EEA_LAYER/query".toHttpUrl().newBuilder()
            .addQueryParameter("geometry", String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", lon - dLon, lat - dLat, lon + dLon, lat + dLat))
            .addQueryParameter("geometryType", "esriGeometryEnvelope")
            .addQueryParameter("inSR", "4326")
            .addQueryParameter("spatialRel", "esriSpatialRelIntersects")
            .addQueryParameter("outFields", FIELDS)
            .addQueryParameter("returnGeometry", "false")
            .addQueryParameter("f", "json")
            .build().toString()
        val key = String.format(Locale.ROOT, "eea2_%.2f_%.2f_%d", lat, lon, radiusKm.toInt())
        return parseEea(cachedJson(key, url, now), lat, lon).filter { it.distanceKm <= radiusKm }
    }

    private suspend fun eeaByIds(ids: Set<String>, lat: Double, lon: Double, now: Long): List<BathingSite> {
        val where = "countryCode <> 'EU' AND bathingWaterIdentifier IN (" + ids.sorted().joinToString(",") { "'" + it.replace("'", "") + "'" } + ")"
        val url = "$EEA_LAYER/query".toHttpUrl().newBuilder()
            .addQueryParameter("where", where)
            .addQueryParameter("outFields", FIELDS)
            .addQueryParameter("returnGeometry", "false")
            .addQueryParameter("f", "json")
            .build().toString()
        return parseEea(cachedJson("eea_fav2_" + ids.sorted().joinToString("_").hashCode(), url, now), lat, lon)
    }

    /** EEA data change once a year: kept on disk for a week. */
    private suspend fun cachedJson(key: String, url: String, now: Long): JsonElement {
        val file = cacheDir?.let { File(it, "$key.json") }
        // asked for anew (forced reload): the list again – the stored one only if that fails
        file?.takeIf { !freshData() && it.exists() && now - it.lastModified() < EEA_MAX_AGE_MS }?.let { f ->
            withContext(Dispatchers.IO) { runCatching { JsonCodec.parseToJsonElement(f.readText()) }.getOrNull() }?.let { return it }
        }
        val json = runCatching { http.getJson(url) }.getOrElse { e ->
            file?.takeIf { it.exists() }?.let { f -> withContext(Dispatchers.IO) { JsonCodec.parseToJsonElement(f.readText()) } }
                ?.also { standIn() } ?: throw e
        }
        if (json.obj()?.a("features") != null) file?.let { f -> withContext(Dispatchers.IO) { runCatching { f.parentFile?.mkdirs(); f.writeText(json.toString()) } } }
        return json
    }

    // --- Berlin ----------------------------------------------------------------------------

    private suspend fun berlinSamples(now: Long): List<StateSample> = mutex.withLock {
        berlin?.takeIf { !freshData() && now - it.first < STATE_MAX_AGE_MS }?.second?.let { return it }
        val text = http.getText(Request.Builder().url(BERLIN_URL).header("User-Agent", USER_AGENT).build())
        parseBerlin(text).also { berlin = now to it }
    }

    // --- Schleswig-Holstein ----------------------------------------------------------------

    private suspend fun shSamples(now: Long): Map<String, StateSample> = mutex.withLock {
        // asked for anew: the samples again – on Wi-Fi only (a large file), on mobile data the last ones
        val renew = freshData() && unmetered()
        sh?.takeIf { !renew && now - it.first < SH_MAX_AGE_MS }?.second?.let { return it }
        val file = cacheDir?.let { File(it, "sh_proben.csv") }
        val fresh = file?.takeIf { !renew && it.exists() && now - it.lastModified() < SH_MAX_AGE_MS }
        val bytes: ByteArray? = when {
            fresh != null -> withContext(Dispatchers.IO) { fresh.readBytes() }
            unmetered() -> withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(SH_URL).header("User-Agent", USER_AGENT).apply { if (renew) cacheControl(AskAgain) }.build()).execute().use { r ->
                        if (r.isSuccessful) r.body.bytes() else null
                    }
                }.getOrNull()?.also { b -> file?.let { runCatching { it.parentFile?.mkdirs(); it.writeBytes(b) } } }
            }
            // On mobile data: yesterday's file is better than nothing
            else -> file?.takeIf { it.exists() }?.let { withContext(Dispatchers.IO) { it.readBytes() } }?.also { standIn() }
        }
        val map = bytes?.let { parseSh(String(it, Charsets.ISO_8859_1)) }.orEmpty()
        if (map.isNotEmpty()) sh = now to map
        map
    }

    // --- Sea temperature -------------------------------------------------------------------

    private suspend fun seaTemperatures(sites: List<BathingSite>): Map<String, Pair<Double, Long>> {
        val url = "https://marine-api.open-meteo.com/v1/marine".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", sites.joinToString(",") { OpenMeteoSource.fmt(it.lat) })
            .addQueryParameter("longitude", sites.joinToString(",") { OpenMeteoSource.fmt(it.lon) })
            .addQueryParameter("current", "sea_surface_temperature")
            .addQueryParameter("cell_selection", "sea")
            .addQueryParameter("timeformat", "unixtime")
            .build().toString()
        return parseSeaTemperatures(http.getJson(url), sites.map { it.id })
    }

    /** One sample of a state, before it is matched to an EEA site. */
    data class StateSample(
        val id: String? = null,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val time: Long?,
        val waterTemp: Double?,
        val visibilityM: Double?,
        val status: BathingStatus? = null,
        val algae: Boolean = false,
        val notice: String? = null,
        val provider: String,
    )

    companion object {
        private const val EEA_LAYER = "https://water.discomap.eea.europa.eu/arcgis/rest/services/BathingWater/BathingWater_Dyna_WM/MapServer/0"
        private const val FIELDS = "bathingWaterIdentifier,bathingWaterName,bwWaterCategory,latitude,longitude,qualityStatus,bwProfileLink,countryCode"
        const val BERLIN_URL = "https://data.lageso.de/baden/0_letzte/letzte.csv"
        const val SH_URL = "https://efi2.schleswig-holstein.de/bg/opendata/v_proben_odata.csv"
        /** Current values are looked up for the favourites and this many nearest sites. */
        const val MAX_ENRICHED = 12
        private const val MATCH_KM = 0.5
        private const val STATE_TIMEOUT_MS = 10_000L
        private const val SH_TIMEOUT_MS = 25_000L
        private const val EEA_MAX_AGE_MS = 7 * 24 * 3_600_000L
        private const val STATE_MAX_AGE_MS = 3_600_000L
        private const val SH_MAX_AGE_MS = 24 * 3_600_000L
        /** A sample older than this does not stand for the water temperature any more. */
        const val SAMPLE_FRESH_MS = 10 * 24 * 3_600_000L
        private val BERLIN_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun parseEea(root: JsonElement, lat: Double, lon: Double): List<BathingSite> =
            root.obj()?.a("features").orEmpty().mapNotNull { e ->
                val a = (e as? JsonObject)?.o("attributes") ?: return@mapNotNull null
                // The service lists some sites twice; the copy with country "EU" has wrong
                // coordinates (Kiel, Kiellinie at 46.3° N 14.5° E in Slovenia)
                if (a.s("countryCode") == "EU") return@mapNotNull null
                val la = a.d("latitude") ?: return@mapNotNull null
                val lo = a.d("longitude") ?: return@mapNotNull null
                BathingSite(
                    id = a.s("bathingWaterIdentifier") ?: return@mapNotNull null,
                    name = a.s("bathingWaterName")?.trim() ?: return@mapNotNull null,
                    category = when (a.s("bwWaterCategory")) {
                        "River" -> BathingCategory.RIVER
                        "Coastal" -> BathingCategory.COAST
                        "Transitional" -> BathingCategory.TRANSITIONAL
                        else -> BathingCategory.LAKE
                    },
                    lat = la, lon = lo,
                    distanceKm = Geo.distanceKm(lat, lon, la, lo),
                    quality = when (a.s("qualityStatus")) {
                        "Excellent" -> BathingQuality.EXCELLENT
                        "Good" -> BathingQuality.GOOD
                        "Sufficient" -> BathingQuality.SUFFICIENT
                        "Poor" -> BathingQuality.POOR
                        null -> null
                        else -> BathingQuality.NOT_CLASSIFIED
                    },
                    profileLink = a.s("bwProfileLink")?.takeIf { it.startsWith("http") },
                )
            }

        private fun number(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()

        private fun date(s: String?): Long? = s?.trim()?.let {
            runCatching { LocalDate.parse(it, DATE).atTime(12, 0).atZone(BERLIN_ZONE).toInstant().toEpochMilli() }.getOrNull()
        }

        /** Simple CSV split: ";" separated, fields may be quoted with doubled quotes inside. */
        private fun splitCsv(line: String, sep: Char): List<String> {
            val out = ArrayList<String>()
            val cur = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == sep && !quoted -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(c)
                }
                i++
            }
            out += cur.toString()
            return out
        }

        /** Joins remarks into sentences: "A (b)" + "C." -> "A (b). C." */
        fun sentences(parts: List<String>): String? = parts.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.last() in ".!?") it else "$it." }.takeIf { it.isNotBlank() }

        /** LAGeSo "letzte.csv": the latest sample of every Berlin bathing site. */
        fun parseBerlin(text: String): List<StateSample> {
            val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
            if (lines.isEmpty()) return emptyList()
            val head = splitCsv(lines.first(), ';')
            fun idx(name: String) = head.indexOf(name)
            val iLat = idx("Latitude"); val iLon = idx("Longitude"); val iDat = idx("Dat"); val iSicht = idx("Sicht")
            val iFarbe = idx("Farbe"); val iTemp = idx("Temp"); val iAlgen = idx("Algen"); val iBem = idx("Bemerkung"); val iHint = idx("Weitere_Hinweise")
            if (iLat < 0 || iLon < 0) return emptyList()
            return lines.drop(1).mapNotNull { line ->
                val f = splitCsv(line, ';')
                fun at(i: Int) = f.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
                val la = number(at(iLat)) ?: return@mapNotNull null
                val lo = number(at(iLon)) ?: return@mapNotNull null
                val remarks = listOfNotNull(at(iBem), at(iHint)).filter { !it.equals("keine", true) && !it.equals("nicht zutreffend", true) }
                val algae = at(iAlgen)?.let { !it.equals("keine", true) && it != "0" } == true ||
                    remarks.any { it.contains("Blaualgen", true) && !it.contains("möglich", true) }
                StateSample(
                    lat = la, lon = lo,
                    time = date(at(iDat)),
                    waterTemp = number(at(iTemp)),
                    visibilityM = number(at(iSicht)),
                    status = when {
                        at(iFarbe)?.startsWith("rot", true) == true -> BathingStatus.CLOSED
                        at(iFarbe)?.startsWith("gelb", true) == true -> BathingStatus.WARNING
                        at(iFarbe)?.startsWith("gruen", true) == true -> BathingStatus.OK
                        else -> null
                    },
                    algae = algae,
                    notice = sentences(remarks.distinct()),
                    provider = "LAGeSo Berlin",
                )
            }
        }

        /**
         * Schleswig-Holstein "v_proben_odata.csv" ("|" separated, no header): BADEGEWAESSERID,
         * MESSSTELLENNAME, MESSSTELLENID, UEBERWACHUNGSARTID, UEBERWACHUNGSARTTEXT,
         * GEWAESSERKATEGORIE, KUESTENGEWAESSER, PROBEID, DATUMMESSUNG, PROBENART, ECOLI,
         * INTEST_ENTEROKOKKEN, WASSERTEMP, LUFTTEMP, SICHTTIEFE, BEMERKUNG – the latest per site.
         */
        fun parseSh(text: String): Map<String, StateSample> {
            val latest = HashMap<String, StateSample>()
            text.lineSequence().forEach { line ->
                val f = line.split('|')
                if (f.size < 15) return@forEach
                val id = f[0].trim().takeIf { it.startsWith("DESH") } ?: return@forEach
                val time = date(f[8]) ?: return@forEach
                val prev = latest[id]
                if (prev != null && (prev.time ?: 0) >= time) return@forEach
                val remark = f.getOrNull(15)?.trim()?.takeIf { it.isNotEmpty() }
                latest[id] = StateSample(
                    id = id, time = time,
                    waterTemp = number(f[12]),
                    visibilityM = number(f[14]),
                    algae = remark?.let { it.contains("Blaualgen", true) || it.contains("Cyanobakterien", true) } == true,
                    notice = remark,
                    provider = "MJG Schleswig-Holstein",
                )
            }
            return latest
        }

        /** Open-Meteo marine answers a list (several places) or one object, in request order. */
        fun parseSeaTemperatures(root: JsonElement, ids: List<String>): Map<String, Pair<Double, Long>> {
            val list = (root as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOfNotNull(root.obj())
            return list.mapIndexedNotNull { i, o ->
                val c = o.o("current") ?: return@mapIndexedNotNull null
                val t = c.d("sea_surface_temperature") ?: return@mapIndexedNotNull null
                val time = c.d("time")?.toLong()?.times(1000) ?: return@mapIndexedNotNull null
                ids.getOrNull(i)?.let { it to (t to time) }
            }.toMap()
        }

        private fun BathingSite.with(s: StateSample) = copy(
            waterTemp = s.waterTemp ?: waterTemp,
            waterTempTime = if (s.waterTemp != null) s.time else waterTempTime,
            sampleTime = s.time, visibilityM = s.visibilityM, status = s.status,
            algae = s.algae, notice = s.notice, provider = s.provider,
        )
    }
}
