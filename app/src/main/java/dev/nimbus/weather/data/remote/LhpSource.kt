/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/LhpSource.kt
 * Flood situation at all gauges and regional flood alerts of the German states (LHP-PublicAPI).
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

import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.WeatherAlert
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * Länderübergreifendes Hochwasserportal (LHP), public API, CC BY 4.0: for about 1,600 flood
 * gauges of all states the current classification (no flood … very large flood) and a link to
 * the gauge page of the state; plus the active regional flood alerts. No measured values – those
 * come from PEGELONLINE and the state sources where they are open. Both lists are small
 * (~55 kB / ~1 kB compressed) and kept for [MAX_AGE_MS], as the LHP asks for.
 */
class LhpSource(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.hochwasserzentralen.de/public/v1",
) {
    private val mutex = Mutex()
    private var stations: Pair<Long, JsonElement>? = null
    private var alerts: Pair<Long, JsonElement>? = null

    suspend fun candidates(lat: Double, lon: Double, radiusKm: Double, now: Long = System.currentTimeMillis()): List<GaugeInfo> {
        val root = mutex.withLock {
            stations?.takeIf { now - it.first < MAX_AGE_MS }?.second
                ?: http.getJson("$baseUrl/data/stations?format=json&lang=de").also { stations = now to it }
        }
        return parseStations(root, lat, lon).filter { it.distanceKm <= radiusKm }
    }

    suspend fun alerts(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): List<WeatherAlert> {
        val root = mutex.withLock {
            alerts?.takeIf { now - it.first < MAX_AGE_MS }?.second
                ?: http.getJson("$baseUrl/data/alerts?format=json&lang=de").also { alerts = now to it }
        }
        return parseAlerts(root, lat, lon)
    }

    companion object {
        const val MAX_AGE_MS = 10 * 60_000L
        /** River sections are lines: a place counts as affected within this distance. */
        private const val LINE_DISTANCE_KM = 5.0

        private fun features(root: JsonElement): List<JsonObject> {
            val o = root.obj() ?: return emptyList()
            return (o.a("features") ?: o.a("data")).orEmpty().mapNotNull { it as? JsonObject }
        }

        fun parseStations(root: JsonElement, lat: Double, lon: Double): List<GaugeInfo> =
            features(root).mapNotNull { f ->
                val c = f.o("geometry")?.a("coordinates") ?: return@mapNotNull null
                val lo = c.getOrNull(0).dbl() ?: return@mapNotNull null
                val la = c.getOrNull(1).dbl() ?: return@mapNotNull null
                val p = f.o("properties") ?: return@mapNotNull null
                GaugeInfo(
                    uuid = "LHP:" + (f.s("id") ?: return@mapNotNull null),
                    name = p.s("name")?.trim() ?: return@mapNotNull null,
                    water = p.s("water")?.trim() ?: "",
                    distanceKm = GaugeGeo.distanceKm(lat, lon, la, lo),
                    tidal = false,
                    provider = GaugeProvider.LHP,
                    lhpClass = p.d("lhpClass")?.toInt(),
                    lhpClassName = p.s("stateClassName")?.let { decodeEntities(it).trim() },
                    link = p.s("stationLink"),
                )
            }

        /** Active alerts whose area contains the place (regions) or passes near it (river sections). */
        fun parseAlerts(root: JsonElement, lat: Double, lon: Double): List<WeatherAlert> =
            features(root).mapNotNull { f ->
                val geo = f.o("geometry") ?: return@mapNotNull null
                if (!GaugeGeo.affects(geo, lat, lon, LINE_DISTANCE_KM)) return@mapNotNull null
                val p = f.o("properties") ?: f
                val cls = (p.d("lhpClass") ?: p.s("lhpClass")?.toDoubleOrNull())?.toInt() ?: 0
                val area = p.s("areaDesc")?.trim().orEmpty()
                WeatherAlert(
                    id = "LHP:" + (f.s("id") ?: area),
                    headline = p.s("alertHeadline")?.trim() ?: p.s("lhpClassName") ?: "Hochwasserwarnung",
                    event = p.s("lhpClassName")?.trim() ?: "Hochwasser",
                    description = listOfNotNull(area.takeIf { it.isNotEmpty() }, p.s("alertLink")).joinToString("\n"),
                    severity = when {
                        cls >= 6 -> AlertSeverity.EXTREME
                        cls >= 4 -> AlertSeverity.SEVERE
                        cls >= 2 -> AlertSeverity.MODERATE
                        else -> AlertSeverity.MINOR
                    },
                    onset = null, expires = null,
                    source = "LHP",
                )
            }

        /** The LHP escapes some characters as HTML entities ("&#60; 2-jährliches Hochwasser"). */
        private val ENTITY = Regex("&#(\\d+);")
        fun decodeEntities(s: String): String = ENTITY.replace(s) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}

/** Small geometry helpers for gauges and alert areas (lat/lon, good enough within a country). */
object GaugeGeo {
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        kotlin.math.hypot((lat2 - lat1) * 111.2, (lon2 - lon1) * 111.2 * kotlin.math.cos(Math.toRadians(lat1)))

    private fun ring(a: JsonArray): List<Pair<Double, Double>> = a.mapNotNull { p ->
        val c = p as? JsonArray ?: return@mapNotNull null
        (c.getOrNull(0).dbl() ?: return@mapNotNull null) to (c.getOrNull(1).dbl() ?: return@mapNotNull null)
    }

    /** Ray casting; points are (lon, lat). */
    fun inRing(points: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val (xi, yi) = points[i]; val (xj, yj) = points[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun inPolygon(rings: JsonArray, lat: Double, lon: Double): Boolean {
        val all = rings.mapNotNull { (it as? JsonArray)?.let { r -> ring(r) } }
        if (all.isEmpty() || !inRing(all[0], lat, lon)) return false
        return all.drop(1).none { inRing(it, lat, lon) }   // holes
    }

    private fun nearLine(points: List<Pair<Double, Double>>, lat: Double, lon: Double, km: Double) =
        points.zipWithNext().any { (a, b) ->
            // distance to the segment in a local metric plane
            val kx = 111.2 * kotlin.math.cos(Math.toRadians(lat)); val ky = 111.2
            val ax = (a.first - lon) * kx; val ay = (a.second - lat) * ky
            val bx = (b.first - lon) * kx; val by = (b.second - lat) * ky
            val dx = bx - ax; val dy = by - ay
            val t = if (dx == 0.0 && dy == 0.0) 0.0 else (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            kotlin.math.hypot(ax + t * dx, ay + t * dy) <= km
        } || points.size == 1 && distanceKm(lat, lon, points[0].second, points[0].first) <= km

    fun affects(geometry: JsonObject, lat: Double, lon: Double, lineKm: Double): Boolean {
        val c = geometry.a("coordinates") ?: return false
        return when (geometry.s("type")) {
            "Polygon" -> inPolygon(c, lat, lon)
            "MultiPolygon" -> c.any { (it as? JsonArray)?.let { p -> inPolygon(p, lat, lon) } == true }
            "LineString" -> nearLine(ring(c), lat, lon, lineKm)
            "MultiLineString" -> c.any { (it as? JsonArray)?.let { l -> nearLine(ring(l), lat, lon, lineKm) } == true }
            "Point" -> ring(JsonArray(listOf(c))).firstOrNull()?.let { distanceKm(lat, lon, it.second, it.first) <= lineKm } == true
            else -> false
        }
    }
}
