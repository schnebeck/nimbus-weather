/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/WiskiSource.kt
 * State gauges published with KISTERS WISKI web (Nordrhein-Westfalen, Hessen).
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

import dev.nimbus.weather.data.model.AlertKind
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.LevelSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.io.File
import java.time.OffsetDateTime

/**
 * The flood portals of Nordrhein-Westfalen (LANUK) and Hessen (HLNUG) run KISTERS WISKI web and
 * publish the same JSON files: `stations.json` (all stations) and per station
 * `<param>/week.json` (7 days, 15-minute values; Hessen adds its forecast `vhs`) and
 * `<param>/alarmlevel.json` (alert levels and mean values).
 *
 * NRW: open data under "Datenlizenz Deutschland – Zero – 2.0" (dataset "Hydrologische Rohdaten
 * (Hochwasserportal NRW)", Open.NRW). Hessen: the HLNUG allows use with "HLNUG" as the source.
 */
class WiskiSource(
    private val http: OkHttpClient,
    private val config: Config,
    private val cacheDir: File? = null,
) {
    data class Config(
        val provider: GaugeProvider,
        val baseUrl: String,
        /** Station parameter of the water level: "S" in NRW, "W" in Hessen. */
        val levelParam: String,
        val alertKind: AlertKind,
        /** Rough bounding box of the state (lat/lon), to skip the request elsewhere. */
        val latRange: ClosedFloatingPointRange<Double>,
        val lonRange: ClosedFloatingPointRange<Double>,
    )

    @Serializable
    data class Station(val no: String, val site: String, val name: String, val water: String, val lat: Double, val lon: Double, val gaugeZero: Double?)

    fun inArea(lat: Double, lon: Double) = lat in config.latRange && lon in config.lonRange

    /** Stations within [radiusKm], without values yet (see [details]). */
    suspend fun candidates(lat: Double, lon: Double, radiusKm: Double, now: Long): List<GaugeInfo> {
        if (!inArea(lat, lon)) return emptyList()
        return stations(now).map { it to GaugeGeo.distanceKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusKm }
            .map { (s, d) ->
                GaugeInfo(
                    uuid = key(s), name = s.name, water = s.water, distanceKm = d, tidal = false, gaugeZero = s.gaugeZero,
                    provider = config.provider, alertKind = config.alertKind,
                )
            }
    }

    /** Week of values, forecast and alert levels; null if the station has no water level series. */
    suspend fun details(candidate: GaugeInfo): GaugeInfo? = coroutineScope {
        val base = "${config.baseUrl}/internet/stations/${candidate.uuid.substringAfter(':')}/${config.levelParam}"
        val alarmJob = async { runCatching { http.getJson("$base/alarmlevel.json") }.getOrNull() }
        val week = runCatching { http.getJson("$base/week.json") }.getOrNull() ?: return@coroutineScope null
        val series = parseWeek(week)
        val alarm = alarmJob.await()?.let { parseAlarm(it) } ?: Alarm(emptyMap(), emptyMap())
        val last = series.measured.lastOrNull() ?: return@coroutineScope null
        candidate.copy(
            level = last.value, levelTime = last.time, history = GaugeSource.binned(series.measured), forecast = series.forecast,
            alertLevels = alarm.levels, marks = candidate.marks + alarm.marks,
            alertStage = alarm.levels.takeIf { it.isNotEmpty() }?.let { lv -> lv.filterValues { last.value >= it }.keys.maxOrNull() ?: 0 },
        )
    }

    private fun key(s: Station) = "${config.provider.name}:${s.site}/${s.no}"

    private suspend fun stations(now: Long): List<Station> {
        val file = cacheDir?.let { File(it, "wiski_${config.provider.name.lowercase()}.json") }
        val serializer = ListSerializer(Station.serializer())
        // asked for anew (forced reload): the list again – the stored one only if that fails
        file?.takeIf { !freshData() && it.exists() && now - it.lastModified() < STATIONS_MAX_AGE_MS }?.let { f ->
            withContext(Dispatchers.IO) { runCatching { JsonCodec.decodeFromString(serializer, f.readText()) }.getOrNull() }?.let { return it }
        }
        val list = runCatching { parseStations(http.getJson("${config.baseUrl}/internet/stations/stations.json")) }.getOrElse { e ->
            file?.takeIf { it.exists() }?.let { f -> withContext(Dispatchers.IO) { JsonCodec.decodeFromString(serializer, f.readText()) } }
                ?.also { standIn() } ?: throw e
        }
        file?.let { f -> withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(JsonCodec.encodeToString(serializer, list)) } }
        return list
    }

    data class Series(val measured: List<LevelSample>, val forecast: List<LevelSample>)
    data class Alarm(val levels: Map<Int, Double>, val marks: Map<String, Double>)

    companion object {
        private const val STATIONS_MAX_AGE_MS = 7 * 24 * 3_600_000L

        /** Surface water stations (not rain, groundwater or temperature only). */
        fun parseStations(root: JsonElement): List<Station> =
            (root as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if (o.s("object_type")?.contains("Oberflächengewässer") != true) return@mapNotNull null
                val lat = o.s("station_latitude")?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = o.s("station_longitude")?.toDoubleOrNull() ?: return@mapNotNull null
                Station(
                    no = o.s("station_no") ?: return@mapNotNull null,
                    site = o.s("site_no") ?: "0",
                    name = (o.s("station_name") ?: return@mapNotNull null).removeSuffix("_NRW").replace('_', ' ').trim(),
                    water = (o.s("WTO_OBJECT")?.takeIf { it.isNotBlank() } ?: o.s("catchment_name") ?: "").trim(),
                    lat = lat, lon = lon,
                    gaugeZero = o.s("GAUGE_DATUM")?.replace(',', '.')?.toDoubleOrNull(),
                )
            }

        private fun points(o: JsonObject): List<LevelSample> = o.a("data").orEmpty().mapNotNull { row ->
            val r = row as? JsonArray ?: return@mapNotNull null
            val t = r.getOrNull(0)?.str()?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
            val v = r.getOrNull(1)?.dbl() ?: return@mapNotNull null
            LevelSample(t, v)
        }

        /**
         * The measured series is the one that is neither a forecast ("vhs…") nor a scenario
         * ("abs…", "nor…"); Hessen's forecast "vhs" continues it into the next hours.
         */
        fun parseWeek(root: JsonElement): Series {
            val all = (root as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val measured = all.firstOrNull { o -> val n = o.s("ts_name").orEmpty().lowercase(); !n.startsWith("vhs") && !n.startsWith("abs") && !n.startsWith("nor") }
            val forecast = all.firstOrNull { it.s("ts_name").orEmpty().lowercase().startsWith("vhs") }
            val m = measured?.let { points(it) }.orEmpty().sortedBy { it.time }
            val last = m.lastOrNull()?.time ?: Long.MIN_VALUE
            return Series(m, forecast?.let { points(it) }.orEmpty().filter { it.time > last }.sortedBy { it.time })
        }

        private val STAGE = Regex("""(?:Meldestufe|Informationswert|Alarmstufe)\D*(\d)""")

        /** "Meldestufe1", "W.Informationswert_2", "Alarmstufe 3" -> alert level; "W.MW" etc. -> mean values. */
        fun parseAlarm(root: JsonElement): Alarm {
            val levels = HashMap<Int, Double>()
            val marks = HashMap<String, Double>()
            (root as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { o ->
                val name = o.s("ts_name") ?: return@forEach
                val value = points(o).lastOrNull()?.value ?: return@forEach
                val stage = STAGE.find(name)?.groupValues?.get(1)?.toInt()
                if (stage != null) levels[stage] = value
                else when (name.substringAfterLast('.')) {
                    "MNW", "MW", "MHW", "HHW" -> marks[name.substringAfterLast('.')] = value
                }
            }
            return Alarm(levels, marks)
        }

        val NRW = Config(
            GaugeProvider.LANUK_NRW, "https://www.hochwasserportal.nrw/data", "S", AlertKind.INFORMATIONSWERT,
            50.3..52.6, 5.8..9.5,
        )
        val HESSEN = Config(
            GaugeProvider.HLNUG_HESSEN, "https://www.hlnug.de/static/pegel/wiskiweb3/data", "W", AlertKind.MELDESTUFE,
            49.35..51.7, 7.7..10.3,
        )
    }
}
