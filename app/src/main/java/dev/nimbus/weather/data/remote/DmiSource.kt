/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/DmiSource.kt
 * Measurements of the Danish Meteorological Institute (every 10 minutes, no key).
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
import java.time.OffsetDateTime
import java.util.Locale

/** The DMI synoptic station nearest to a place in Denmark, its latest values. */
class DmiSource(private val http: OkHttpClient, private val baseUrl: String = "https://opendataapi.dmi.dk") {

    suspend fun nearest(lat: Double, lon: Double): StationObservation? {
        // the stations around the place only (the whole list is 600 kB)
        val box = String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", lon - 0.55, lat - 0.27, lon + 0.55, lat + 0.27)
        val list = parseStations(http.getJson("$baseUrl/v2/metObs/collections/station/items?status=Active&bbox=$box"))
        val (st, km) = NetworkStations.nearest(list, lat, lon) ?: return null
        return parse(http.getJson("$baseUrl/v2/metObs/collections/observation/items?stationId=${st.id}&period=latest-hour&limit=500"), st, km)
    }

    companion object {
        /** The synoptic stations measuring temperature, each once (the list holds one per period of a station's history). */
        fun parseStations(root: JsonElement): List<NetworkStation> =
            root.obj()?.a("features")?.mapNotNull { e ->
                val f = e as? JsonObject ?: return@mapNotNull null
                val p = f.o("properties") ?: return@mapNotNull null
                val measures = p.a("parameterId")?.any { (it as? JsonPrimitive)?.content == "temp_dry" } == true
                if (p.s("type") != "Synop" || !measures) return@mapNotNull null
                val c = f.o("geometry")?.a("coordinates") ?: return@mapNotNull null
                val station = NetworkStation(
                    p.s("stationId") ?: return@mapNotNull null, p.s("name") ?: "",
                    c.getOrNull(1).dbl() ?: return@mapNotNull null, c.getOrNull(0).dbl() ?: return@mapNotNull null, p.d("stationHeight"),
                )
                station to (p.s("validFrom") ?: "")
            }.orEmpty().groupBy { it.first.id }.map { (_, periods) -> periods.maxBy { it.second }.first }

        /** The latest value of each parameter; wind in m/s → km/h. */
        fun parse(root: JsonElement, st: NetworkStation, km: Double): StationObservation? {
            val latest = HashMap<String, Pair<String, Double>>()
            root.obj()?.a("features")?.forEach { e ->
                val p = (e as? JsonObject)?.o("properties") ?: return@forEach
                val key = p.s("parameterId") ?: return@forEach
                val at = p.s("observed") ?: return@forEach
                val value = p.d("value") ?: return@forEach
                if ((latest[key]?.first ?: "") < at) latest[key] = at to value
            }
            val (at, t) = latest["temp_dry"] ?: return null
            fun v(key: String) = latest[key]?.second
            return StationObservation(
                time = runCatching { OffsetDateTime.parse(at).toInstant().toEpochMilli() }.getOrNull() ?: return null,
                stationName = st.name, distanceKm = km,
                temperature = t, humidity = v("humidity"), dewPoint = v("temp_dew"), pressure = v("pressure_at_sea"),
                windSpeed = v("wind_speed")?.times(3.6), windGust = v("wind_max")?.times(3.6), windDirection = v("wind_dir"),
                visibility = v("visibility"), cloudCover = v("cloud_cover"), precipitation60 = v("precip_past1h"),
                condition = null, observedDry = false,
                heightM = st.heightM, stationId = st.id, network = StationNetwork.DMI,
            )
        }
    }
}
