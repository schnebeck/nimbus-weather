/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/GeoSphereSource.kt
 * Measurements of GeoSphere Austria (TAWES, every 10 minutes, no key).
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The TAWES station nearest to a place in Austria, its latest 10 minutes. */
class GeoSphereSource(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://dataset.api.hub.geosphere.at",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stations = KeptList<List<NetworkStation>>(STATION_LIST_MS, clock)

    suspend fun nearest(lat: Double, lon: Double): StationObservation? {
        val list = stations.get { parseStations(http.getJson("$baseUrl/v1/station/current/tawes-v1-10min/metadata"), clock()) }
        val (st, km) = NetworkStations.nearest(list, lat, lon) ?: return null
        return parse(http.getJson("$baseUrl/v1/station/current/tawes-v1-10min?parameters=TL,TP,RF,FF,FFX,DD,PRED,RR&station_ids=${st.id}"), st, km)
    }

    companion object {
        private const val STATION_LIST_MS = 7 * 24 * 3_600_000L

        /** The stations still running (a closed one's validity has ended). */
        fun parseStations(root: JsonElement, now: Long): List<NetworkStation> =
            root.obj()?.a("stations")?.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val until = o.s("valid_to")?.let { runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
                if (until != null && until < now) return@mapNotNull null
                NetworkStation(
                    o.s("id") ?: return@mapNotNull null, NetworkStations.niceName(o.s("name") ?: ""),
                    o.d("lat") ?: return@mapNotNull null, o.d("lon") ?: return@mapNotNull null, o.d("altitude"),
                )
            }.orEmpty()

        /** The latest 10 minutes of [st]; wind in m/s → km/h, pressure reduced to sea level. */
        fun parse(root: JsonElement, st: NetworkStation, km: Double): StationObservation? {
            val o = root.obj() ?: return null
            val times = o.a("timestamps") ?: return null
            val i = times.lastIndex
            val time = (times.getOrNull(i) as? JsonPrimitive)?.content
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: return null
            val p = (o.a("features")?.firstOrNull() as? JsonObject)?.o("properties")?.o("parameters") ?: return null
            fun v(key: String) = p.o(key)?.a("data")?.getOrNull(i).dbl()
            return StationObservation(
                time = time, stationName = st.name, distanceKm = km,
                temperature = v("TL") ?: return null, humidity = v("RF"), dewPoint = v("TP"), pressure = v("PRED"),
                windSpeed = v("FF")?.times(3.6), windGust = v("FFX")?.times(3.6), windDirection = v("DD"),
                visibility = null, cloudCover = null, precipitation60 = null, condition = null, observedDry = false,
                heightM = st.heightM, stationId = st.id, network = StationNetwork.GEOSPHERE,
            )
        }
    }
}
