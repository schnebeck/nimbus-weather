/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/NlwknSource.kt
 * Water levels of the Lower Saxony state gauges (NLWKN Pegelonline) with their flood alert levels.
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
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.LevelSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.io.File

/**
 * NLWKN Pegelonline: the gauges of Lower Saxony on rivers that are not federal waterways (e.g. the
 * Innerste at Heinde), with the state's flood alert levels (Meldestufen 1–3). Free web service;
 * the source www.pegelonline.nlwkn.niedersachsen.de must be named. The key is the public one from
 * the NLWKN user manual ("Pegelonline Webservice – Benutzerhandbuch").
 *
 * The station list (~850 kB) is kept on disk for a week; a place then costs one request (~90 kB)
 * with the station data and the last seven days.
 */
class NlwknSource(
    private val http: OkHttpClient,
    private val cacheDir: File? = null,
    private val baseUrl: String = "https://bis.azure-api.net/PegelonlinePublic/REST",
    private val key: String = "9dc05f4e3b4a43a9988d747825b39f43",
) {
    @Serializable
    data class Station(val id: Int, val name: String, val water: String, val lat: Double, val lon: Double, val tidal: Boolean)

    /** Stations within [radiusKm], without values yet (see [details]). */
    suspend fun candidates(lat: Double, lon: Double, radiusKm: Double, now: Long = System.currentTimeMillis()): List<GaugeInfo> {
        if (!inArea(lat, lon)) return emptyList()
        return stations(now)
            // Tide gauges come from PEGELONLINE (with the prediction); canals say nothing about the weather.
            .filter { !it.tidal && !it.water.contains("kanal", ignoreCase = true) }
            .map { it to Geo.distanceKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusKm }
            .map { (st, d) -> GaugeInfo(uuid = "nlwkn-${st.id}", name = st.name, water = st.water, distanceKm = d, tidal = false, provider = GaugeProvider.NLWKN) }
    }

    /** Station data, the last seven days and the alert levels (one request, ~90 kB). */
    suspend fun details(candidate: GaugeInfo): GaugeInfo? {
        val id = candidate.uuid.removePrefix("nlwkn-")
        val root = http.getJson("$baseUrl/station/$id/datenspuren/parameter/1/tage/-7?key=$key")
        return parseSeries(root, candidate.distanceKm)
    }

    private suspend fun stations(now: Long): List<Station> {
        val file = cacheDir?.let { File(it, "nlwkn_stations.json") }
        val serializer = ListSerializer(Station.serializer())
        // asked for anew (forced reload): the list again – the stored one only if that fails
        file?.takeIf { !freshData() && it.exists() && now - it.lastModified() < STATIONS_MAX_AGE_MS }?.let { f ->
            withContext(Dispatchers.IO) { runCatching { JsonCodec.decodeFromString(serializer, f.readText()) }.getOrNull() }?.let { return it }
        }
        val list = runCatching { parseStations(http.getJson("$baseUrl/stammdaten/stationen/All?key=$key")) }.getOrElse { e ->
            // Offline: an older list is still fine, stations rarely move.
            file?.takeIf { it.exists() }?.let { f -> withContext(Dispatchers.IO) { JsonCodec.decodeFromString(serializer, f.readText()) } }
                ?.also { standIn() } ?: throw e
        }
        file?.let { f -> withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(JsonCodec.encodeToString(serializer, list)) } }
        return list
    }

    companion object {
        private const val STATIONS_MAX_AGE_MS = 7 * 24 * 3_600_000L

        /** Lower Saxony and Bremen, generously. */
        fun inArea(lat: Double, lon: Double) = lat in 51.2..54.0 && lon in 6.5..11.7


        /**
         * The service names its coordinates inconsistently ("Latitude" holds the longitude), so
         * the two WGS84 values are told apart by their range: Lower Saxony lies at 51–54° N, 6–12° E.
         */
        private fun coordinates(o: JsonObject): Pair<Double, Double>? {
            val a = o.d("WGS84Hochwert") ?: o.s("Latitude")?.toDoubleOrNull() ?: return null
            val b = o.d("WGS84Rechtswert") ?: o.s("Longitude")?.toDoubleOrNull() ?: return null
            return if (a > b) a to b else b to a
        }

        fun parseStations(root: JsonElement): List<Station> =
            (root.obj()?.a("getStammdatenResult") ?: return emptyList()).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.d("STA_ID")?.toInt() ?: return@mapNotNull null
                val (lat, lon) = coordinates(o) ?: return@mapNotNull null
                val hasLevel = o.a("Parameter")?.any { p ->
                    (p as? JsonObject)?.a("Datenspuren")?.any { (it as? JsonObject)?.let { d -> d.d("PAT_ID") == 1.0 && d["IstWasserstand"].bool() == true } == true } == true
                } == true
                if (!hasLevel) return@mapNotNull null
                Station(id, o.s("Name")?.trim() ?: return@mapNotNull null, o.s("GewaesserName")?.trim() ?: "", lat, lon, o["IstTide"].bool() == true)
            }

        /** "/Date(1790778600000)/" -> 1790778600000 */
        private val DATE = Regex("""/Date\((-?\d+)""")
        fun parseDate(s: String?): Long? = s?.let { DATE.find(it)?.groupValues?.get(1)?.toLongOrNull() }

        /** Station data and the water level series of the "datenspuren" answer. */
        fun parseSeries(root: JsonElement, distanceKm: Double): GaugeInfo? {
            val o = root.obj()?.o("getPegelDatenspurenResult") ?: return null
            val ds = o.a("Parameter")?.mapNotNull { it as? JsonObject }?.flatMap { p -> p.a("Datenspuren")?.mapNotNull { it as? JsonObject }.orEmpty() }
                ?.firstOrNull { it.d("PAT_ID") == 1.0 } ?: return null
            val history = ds.a("Pegelstaende")?.mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                val t = parseDate(p.s("DatumUTC")) ?: return@mapNotNull null
                val v = p.d("Wert")?.takeIf { it > -800 } ?: return@mapNotNull null   // -888 = no value
                LevelSample(t, v)
            }?.sortedBy { it.time }.orEmpty()
            val current = ds.o("AktuellerPegelstand")
            val marks = listOfNotNull(
                (o.d("StatistikMittelNiedrigwasser") ?: ds.d("StatistikNiedrigwasser"))?.let { "MNW" to it },
                (o.d("StatistikMittelwasser") ?: ds.d("StatistikMittelwasser"))?.let { "MW" to it },
                (o.d("StatistikMittelHochwasser") ?: ds.d("StatistikHochWasser"))?.let { "MHW" to it },
            ).filter { it.second > -800 }.toMap()
            return GaugeInfo(
                uuid = "nlwkn-" + (o.d("STA_ID")?.toInt() ?: 0),
                name = o.s("Name")?.trim() ?: return null,
                water = o.s("GewaesserName")?.trim() ?: "",
                distanceKm = distanceKm,
                tidal = false,
                gaugeZero = o.d("Hoehe"),
                level = (current?.d("Wert") ?: ds.d("AktuellerMesswert"))?.takeIf { it > -800 },
                levelTime = parseDate(current?.s("DatumUTC")),
                marks = marks,
                history = history,
                provider = GaugeProvider.NLWKN,
                alertLevels = ds.a("Meldestufen")?.mapNotNull { e ->
                    val m = e as? JsonObject ?: return@mapNotNull null
                    val stage = m.d("Stufe")?.toInt() ?: return@mapNotNull null
                    val v = m.d("Wert") ?: return@mapNotNull null
                    stage to v
                }?.toMap().orEmpty(),
                alertStage = ds.d("AktuelleMeldeStufe")?.toInt()?.takeIf { it >= 0 },
                alertKind = dev.nimbus.weather.data.model.AlertKind.MELDESTUFE,
                tendency = when (ds.s("Trend")?.lowercase()) { "steigend" -> 1; "fallend" -> -1; "gleichbleibend" -> 0; else -> null },
            )
        }
    }
}
