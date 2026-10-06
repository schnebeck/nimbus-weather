/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SachsenSource.kt
 * Current water levels of the Saxon state gauges from the LfULG map service.
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
import dev.nimbus.weather.data.model.AlertKind
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Freistaat Sachsen, LfULG: the layer "aktuelle_Wasserstaende" of the official map service
 * "Pegelnetz" (listed in the open data catalogue) holds the current level, discharge, tendency
 * and the four alert levels (Alarmstufen) of about 190 gauges – one query per place. There is
 * no history in the service, so the card shows the current state only.
 */
class SachsenSource(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://luis.sachsen.de/arcgis/rest/services/wasser/pegelnetz/FeatureServer/1",
) {
    fun inArea(lat: Double, lon: Double) = lat in 50.1..51.75 && lon in 11.8..15.1

    suspend fun candidates(lat: Double, lon: Double, radiusKm: Double): List<GaugeInfo> {
        if (!inArea(lat, lon)) return emptyList()
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(lat)))
        val url = "$baseUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("geometry", "${lon - dLon},${lat - dLat},${lon + dLon},${lat + dLat}")
            .addQueryParameter("geometryType", "esriGeometryEnvelope")
            .addQueryParameter("inSR", "4326").addQueryParameter("outSR", "4326")
            .addQueryParameter("outFields", "*").addQueryParameter("returnGeometry", "true")
            .addQueryParameter("f", "json")
            .build().toString()
        return parse(http.getJson(url), lat, lon).filter { it.distanceKm <= radiusKm }
    }

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        /** "15" -> 15.0, "0,367" -> 0.367, "k.A." -> null */
        private fun number(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()

        fun parse(root: JsonElement, lat: Double, lon: Double): List<GaugeInfo> =
            root.obj()?.a("features").orEmpty().mapNotNull { e ->
                val f = e as? JsonObject ?: return@mapNotNull null
                val a = f.o("attributes") ?: return@mapNotNull null
                val g = f.o("geometry") ?: return@mapNotNull null
                val x = g.d("x") ?: return@mapNotNull null
                val y = g.d("y") ?: return@mapNotNull null
                val level = number(a.s("PEG_WASSERSTAND"))
                val time = a.s("PEG_WERTZEITSTEMPEL_CHAR")?.let { t ->
                    runCatching {
                        val offset = ZoneOffset.of(a.s("ZEITZONE") ?: "+01:00")
                        LocalDateTime.parse(t, TIME).toInstant(offset).toEpochMilli()
                    }.getOrNull()
                }
                val levels = (1..4).mapNotNull { k -> a.d("PEG_AS$k")?.let { k to it } }.toMap()
                val tendency = when (a.s("TENDENZ")?.lowercase()) {
                    null -> null
                    else -> a.s("TENDENZ")!!.lowercase().let { t ->
                        when { "steig" in t -> 1; "fall" in t -> -1; "gleich" in t -> 0; else -> null }
                    }
                }
                GaugeInfo(
                    uuid = "SN:" + (a.d("PEG_MSTNR")?.toLong() ?: return@mapNotNull null),
                    name = a.s("PEG_NAME")?.trim() ?: return@mapNotNull null,
                    water = a.s("WLV_GEWAESSER")?.trim() ?: "",
                    distanceKm = Geo.distanceKm(lat, lon, y, x),
                    tidal = false,
                    level = level,
                    levelTime = time,
                    discharge = number(a.s("PEG_DURCHFLUSS")),
                    provider = GaugeProvider.LFULG_SACHSEN,
                    alertKind = AlertKind.ALARMSTUFE,
                    alertLevels = levels,
                    alertStage = if (level != null && levels.isNotEmpty()) levels.filterValues { level >= it }.keys.maxOrNull() ?: 0 else null,
                    stateText = a.s("PEG_STATUS")?.trim()?.takeIf { it.isNotEmpty() },
                    tendency = tendency,
                    link = a.s("PEG_INTERNETSEITE_URL")?.takeIf { it.startsWith("http") },
                )
            }
    }
}
