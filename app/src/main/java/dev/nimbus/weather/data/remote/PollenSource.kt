package dev.nimbus.weather.data.remote

import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pollen forecast:
 * - Germany: DWD "Pollenflug-Gefahrenindex" (8 types, levels 0–3 for today, tomorrow and the day
 *   after, 27 regions). The region of a place is looked up with a WMS GetFeatureInfo request on
 *   the DWD layer "Pollenfluggebiete".
 * - Everywhere in Europe: hourly CAMS concentrations (6 types, ~3 days) via Open-Meteo, used for
 *   the composition and course, and for the daily levels outside Germany.
 */
class PollenSource(
    private val http: OkHttpClient,
    private val dwdIndexUrl: String = "https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json",
    private val dwdWmsUrl: String = "https://maps.dwd.de/geoserver/dwd/wms",
    private val airQualityUrl: String = "https://air-quality-api.open-meteo.com",
) {
    private val mutex = Mutex()
    private var index: Pair<Long, JsonElement>? = null
    private val regions = HashMap<String, Pair<Int, String>?>()

    suspend fun forecast(lat: Double, lon: Double, inGermany: Boolean): PollenForecast? = coroutineScope {
        val camsJob = async { runCatching { cams(lat, lon) }.getOrNull() }
        val dwdJob = async { if (inGermany) runCatching { dwd(lat, lon) }.getOrNull() else null }
        val cams = camsJob.await()
        val dwd = dwdJob.await()
        val hourlyTimes = cams?.times ?: emptyList()
        val hourly = cams?.values ?: emptyMap()
        when {
            dwd != null -> PollenForecast(PollenSourceKind.DWD, dwd.first, dwd.second, hourlyTimes, hourly)
            cams != null -> PollenForecast(PollenSourceKind.CAMS, null, camsDays(hourlyTimes, hourly, cams.zone), hourlyTimes, hourly)
            else -> null
        }
    }

    private suspend fun dwd(lat: Double, lon: Double): Pair<String, List<PollenDay>>? {
        val (regionId, regionName) = region(lat, lon) ?: return null
        val json = mutex.withLock {
            val now = System.currentTimeMillis()
            index?.takeIf { now - it.first < 60 * 60_000L }?.second
                ?: http.getJson(dwdIndexUrl).also { index = now to it }
        }
        return parseDwd(json, regionId)?.let { regionName to it }
    }

    private suspend fun region(lat: Double, lon: Double): Pair<Int, String>? {
        val key = "%.2f,%.2f".format(Locale.US, lat, lon)
        mutex.withLock { if (regions.containsKey(key)) return regions[key] }
        val bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", lat - 0.01, lon - 0.01, lat + 0.01, lon + 0.01)
        val url = "$dwdWmsUrl?service=WMS&version=1.3.0&request=GetFeatureInfo&layers=dwd:Pollenfluggebiete" +
            "&query_layers=dwd:Pollenfluggebiete&crs=EPSG:4326&bbox=$bbox&width=3&height=3&i=1&j=1&info_format=application/json"
        val r = parseRegion(http.getJson(url))
        mutex.withLock { regions[key] = r }
        return r
    }

    data class Cams(val times: List<Long>, val values: Map<PollenType, List<Double?>>, val zone: ZoneId)

    private suspend fun cams(lat: Double, lon: Double): Cams? {
        val url = "$airQualityUrl/v1/air-quality?latitude=${OpenMeteoSource.fmt(lat)}&longitude=${OpenMeteoSource.fmt(lon)}" +
            "&hourly=${CAMS.values.joinToString(",")}&forecast_days=4&timezone=auto&timeformat=unixtime"
        return parseCams(http.getJson(url))
    }

    companion object {
        /** DWD JSON keys → types. */
        private val DWD = mapOf(
            "Hasel" to PollenType.HAZEL, "Erle" to PollenType.ALDER, "Esche" to PollenType.ASH, "Birke" to PollenType.BIRCH,
            "Graeser" to PollenType.GRASS, "Roggen" to PollenType.RYE, "Beifuss" to PollenType.MUGWORT, "Ambrosia" to PollenType.RAGWEED,
        )

        /** Open-Meteo CAMS variables. */
        val CAMS = linkedMapOf(
            PollenType.ALDER to "alder_pollen", PollenType.BIRCH to "birch_pollen", PollenType.GRASS to "grass_pollen",
            PollenType.MUGWORT to "mugwort_pollen", PollenType.OLIVE to "olive_pollen", PollenType.RAGWEED to "ragweed_pollen",
        )

        /** "0", "0-1", …, "3" → 0.0 … 3.0; "-1" or unknown → null (not issued). */
        fun level(s: String?): Float? = when (s?.trim()) {
            "0" -> 0f; "0-1" -> 0.5f; "1" -> 1f; "1-2" -> 1.5f; "2" -> 2f; "2-3" -> 2.5f; "3" -> 3f
            else -> null
        }

        fun parseRegion(root: JsonElement): Pair<Int, String>? {
            val f = root.obj()?.a("features")?.firstOrNull() as? JsonObject ?: return null
            val p = f.o("properties") ?: return null
            val gf = p.l("GF")?.toInt() ?: return null
            return gf to (p.s("GEN") ?: "")
        }

        /**
         * The DWD file is issued daily around 11:00; "today" is the issue date. Days are keyed
         * by date so a file from yesterday still yields correct values for today and tomorrow.
         */
        fun parseDwd(root: JsonElement, regionId: Int, zone: ZoneId = ZoneId.of("Europe/Berlin")): List<PollenDay>? {
            val o = root.obj() ?: return null
            val issued = o.s("last_update")?.let {
                runCatching { LocalDate.parse(it.take(10), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            } ?: return null
            val entry = o.a("content")?.mapNotNull { it as? JsonObject }?.firstOrNull {
                it.l("partregion_id")?.toInt() == regionId || (it.l("partregion_id")?.toInt() == -1 && it.l("region_id")?.toInt() == regionId)
            } ?: return null
            val pollen = entry.o("Pollen") ?: return null
            return listOf("today", "tomorrow", "dayafter_to").mapIndexedNotNull { i, key ->
                val levels = DWD.mapNotNull { (name, type) -> level(pollen.o(name)?.s(key))?.let { type to it } }.toMap()
                if (levels.isEmpty()) null
                else PollenDay(issued.plusDays(i.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(), levels)
            }
        }

        fun parseCams(root: JsonElement): Cams? {
            val o = root.obj() ?: return null
            val h = o.o("hourly") ?: return null
            val zone = o.s("timezone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
            val times = h.longs("time").map { (it ?: 0L) * 1000 }
            val values = CAMS.mapValues { (_, v) -> h.doubles(v) }.filterValues { list -> list.any { it != null } }
            // Trim trailing hours without any data (CAMS covers ~3 of the 4 requested days).
            val last = times.indices.lastOrNull { i -> values.values.any { it.getOrNull(i) != null } } ?: return null
            return Cams(times.take(last + 1), values.mapValues { it.value.take(last + 1) }, zone)
        }

        /** Daily maximum concentration per type, mapped to the DWD 0–3 scale. */
        fun camsDays(times: List<Long>, hourly: Map<PollenType, List<Double?>>, zone: ZoneId): List<PollenDay> {
            val byDay = times.indices.groupBy { java.time.Instant.ofEpochMilli(times[it]).atZone(zone).toLocalDate() }
            return byDay.entries.sortedBy { it.key }.filter { it.value.size >= 12 }.map { (date, idx) ->
                val levels = hourly.mapValues { (type, v) -> levelFromConcentration(type, idx.mapNotNull { v.getOrNull(it) }.maxOrNull() ?: 0.0) }
                PollenDay(date.atStartOfDay(zone).toInstant().toEpochMilli(), levels)
            }.take(3)
        }

        /**
         * Approximate DWD-like level from a concentration in grains/m³. Thresholds follow common
         * allergy scales: trees become relevant at higher counts than grasses or weeds.
         */
        fun levelFromConcentration(type: PollenType, c: Double): Float {
            val (moderate, high) = when (type) {
                PollenType.GRASS, PollenType.RYE -> 10.0 to 50.0
                PollenType.MUGWORT, PollenType.RAGWEED -> 5.0 to 15.0
                else -> 10.0 to 100.0
            }
            return when {
                c < 1 -> 0f
                c < moderate / 3 -> 0.5f
                c < moderate -> 1f
                c < kotlin.math.sqrt(moderate * high) -> 1.5f
                c < high -> 2f
                c < 2 * high -> 2.5f
                else -> 3f
            }
        }
    }
}
