/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/MeteoSwissSource.kt
 * Measurements of MeteoSwiss (SwissMetNet, every 10 minutes, no key).
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

import dev.nimbus.weather.data.model.StationNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The SwissMetNet weather station nearest to a place in Switzerland that measures now: the latest
 * 10 minutes of all stations come as one file.
 */
class MeteoSwissSource(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://data.geo.admin.ch",
    clock: () -> Long = System::currentTimeMillis,
) {
    private val stations = KeptList<List<NetworkStation>>(STATION_LIST_MS, clock)

    suspend fun nearest(lat: Double, lon: Double): StationObservation? {
        val list = stations.get { parseStations(latin1("$baseUrl/ch.meteoschweiz.messnetz-automatisch/ch.meteoschweiz.messnetz-automatisch_de.csv")) }
        val rows = parseValues(latin1("$baseUrl/ch.meteoschweiz.messwerte-aktuell/VQHA80.csv"))
        // the nearest one measuring now (a row may be empty: "-" throughout)
        val (st, km) = NetworkStations.nearest(list.filter { rows[it.id]?.get(TEMPERATURE)?.toDoubleOrNull() != null }, lat, lon) ?: return null
        return observation(rows.getValue(st.id), st, km)
    }

    /** MeteoSwiss' files are ISO 8859-1 (read as UTF-8 their umlauts broke). */
    private suspend fun latin1(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply { if (freshData()) cacheControl(AskAgain) }.build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).await().use { resp ->
                if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code} for ${req.url.host}")
                String(resp.body.bytes(), Charsets.ISO_8859_1)
            }
        }
    }

    companion object {
        private const val STATION_LIST_MS = 7 * 24 * 3_600_000L
        private const val TEMPERATURE = "tre200s0"

        private fun rows(text: String): List<List<String>> =
            text.lineSequence().filter { it.isNotBlank() }.map { line -> line.split(';').map { it.trim().removeSurrounding("\"") } }.toList()

        /** The weather stations of the automatic network (not the ones for precipitation only). */
        fun parseStations(csv: String): List<NetworkStation> {
            val all = rows(csv)
            val head = all.firstOrNull() ?: return emptyList()
            fun col(name: String) = head.indexOfFirst { it.startsWith(name) }
            val id = col("Abk."); val name = col("Station"); val type = col("Stationstyp")
            val height = col("Stationsh"); val lat = col("Breitengrad"); val lon = col("Längengrad")
            return all.drop(1).mapNotNull { r ->
                if (r.getOrNull(type) != "Wetterstation") return@mapNotNull null
                NetworkStation(
                    r.getOrNull(id) ?: return@mapNotNull null, r.getOrNull(name) ?: "",
                    r.getOrNull(lat)?.toDoubleOrNull() ?: return@mapNotNull null, r.getOrNull(lon)?.toDoubleOrNull() ?: return@mapNotNull null,
                    r.getOrNull(height)?.toDoubleOrNull(),
                )
            }
        }

        /** The latest 10 minutes of every station: station id → column → value ("-": none). */
        fun parseValues(csv: String): Map<String, Map<String, String>> {
            val all = rows(csv)
            val head = all.firstOrNull() ?: return emptyMap()
            return all.drop(1).filter { it.size == head.size }.associate { r -> r[0] to head.zip(r).toMap() }
        }

        /** One station's row; its wind already in km/h (fu3), the pressure reduced to sea level (QFF). */
        fun observation(row: Map<String, String>, st: NetworkStation, km: Double): StationObservation? {
            fun v(key: String) = row[key]?.toDoubleOrNull()
            val time = row["Date"]?.let {
                runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmm")).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            } ?: return null
            return StationObservation(
                time = time, stationName = st.name, distanceKm = km,
                temperature = v(TEMPERATURE) ?: return null, humidity = v("ure200s0"), dewPoint = v("tde200s0"), pressure = v("pp0qffs0"),
                windSpeed = v("fu3010z0"), windGust = v("fu3010z1"), windDirection = v("dkl010z0"),
                visibility = null, cloudCover = null, precipitation60 = null, condition = null, observedDry = false,
                heightM = st.heightM, stationId = st.id, network = StationNetwork.METEOSWISS,
            )
        }
    }
}
