/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/MetarSource.kt
 * Airport weather reports (METAR) worldwide via NOAA's aviationweather.gov – every half hour,
 * temperatures in whole degrees; where no national network measures.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.StationNetwork
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.Locale

/** The airport nearest to a place with a temperature, its latest report. */
class MetarSource(private val http: OkHttpClient, private val baseUrl: String = "https://aviationweather.gov") {

    suspend fun nearest(lat: Double, lon: Double): StationObservation? {
        val dLon = BOX_DEG / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val box = String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", lat - BOX_DEG, lon - dLon, lat + BOX_DEG, lon + dLon)
        return parse(http.getJson("$baseUrl/api/data/metar?bbox=$box&format=json"), lat, lon)
    }

    companion object {
        /** Half the height of the box asked for around the place, in degrees (about 30 km). */
        private const val BOX_DEG = 0.27

        /** Weather of a METAR's present weather group ("-RA", "TSRA", "FG" …); none: nothing reported. */
        fun condition(wx: String?): Condition? {
            val w = wx?.uppercase() ?: return null
            return when {
                "TS" in w -> Condition.THUNDERSTORM
                "FZRA" in w || "FZDZ" in w -> Condition.FREEZING_RAIN
                "GR" in w || "GS" in w -> Condition.SHOWERS
                "PL" in w || ("RA" in w && "SN" in w) -> Condition.SLEET
                "+SN" in w -> Condition.HEAVY_SNOW
                "SN" in w || "SG" in w -> Condition.SNOW
                "SH" in w -> Condition.SHOWERS
                "+RA" in w -> Condition.HEAVY_RAIN
                "RA" in w -> Condition.RAIN
                "DZ" in w -> Condition.DRIZZLE
                "FG" in w && "BCFG" !in w && "MIFG" !in w && "PRFG" !in w -> Condition.FOG
                else -> null
            }
        }

        /** Cloud cover (%) – the most covered layer; no layer and CAVOK: clear. */
        private fun clouds(o: JsonObject): Double? {
            val covers = o.a("clouds")?.mapNotNull { (it as? JsonObject)?.s("cover") }.orEmpty()
            val raw = o.s("rawOb").orEmpty()
            if (covers.isEmpty()) return if (listOf("CAVOK", "SKC", "CLR", "NSC").any { it in raw }) 0.0 else null
            return covers.maxOf { c -> when (c) { "FEW" -> 20.0; "SCT" -> 45.0; "BKN" -> 75.0; "OVC", "OVX", "VV" -> 100.0; else -> 0.0 } }
        }

        /** The nearest airport with a temperature, within [NetworkStations.MAX_KM]; knots → km/h, statute miles → m. */
        fun parse(root: JsonElement, lat: Double, lon: Double): StationObservation? {
            val (o, km) = (root as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty().mapNotNull { r ->
                if (r.d("temp") == null) return@mapNotNull null
                r to GaugeGeo.distanceKm(lat, lon, r.d("lat") ?: return@mapNotNull null, r.d("lon") ?: return@mapNotNull null)
            }.filter { it.second <= NetworkStations.MAX_KM }.minByOrNull { it.second } ?: return null
            val t = o.d("temp")!!
            val td = o.d("dewp")
            val wx = o.s("wxString")
            return StationObservation(
                time = o.l("obsTime")?.times(1000) ?: o.s("reportTime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null,
                stationName = (o.s("name") ?: o.s("icaoId") ?: "METAR").substringBefore(',').trim(), distanceKm = km,
                temperature = t, humidity = td?.let { NetworkStations.humidity(t, it) }, dewPoint = td, pressure = o.d("altim"),
                // a variable direction ("VRB") has none
                windSpeed = o.d("wspd")?.times(1.852), windGust = o.d("wgst")?.times(1.852), windDirection = o.d("wdir"),
                // "6+" (10 km and more) says nothing beyond
                visibility = o.d("visib")?.times(1609.344),
                cloudCover = clouds(o), precipitation60 = null,
                condition = condition(wx), observedDry = wx == null,
                heightM = o.d("elev"), stationId = o.s("icaoId"), network = StationNetwork.METAR,
            )
        }
    }
}
