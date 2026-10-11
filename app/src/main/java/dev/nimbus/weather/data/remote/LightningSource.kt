/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/LightningSource.kt
 * Lightning near a place: the nearest flash of the last 15 minutes, from the DWD's satellite flashes.
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant
import kotlin.math.cos

/**
 * Lightning near a place: the nearest flash [distanceKm] away, [minutesAgo]; [flashes] within the
 * radius – [more]: at least that many (the newest groups only were asked for).
 */
data class LightningNearby(val distanceKm: Double, val minutesAgo: Int, val flashes: Int, val more: Boolean = false)

/**
 * The flashes the Lightning Imager of Meteosat Third Generation saw around a place, as the DWD
 * serves them (WFS): each flash in groups, each group an outline – its middle stands for where it
 * struck. Asked for a box around the place only: a few hundred bytes without lightning. Kept about
 * four days: the look-back's days too ([hourly]).
 */
class LightningSource(private val http: OkHttpClient, private val wfs: String = WFS) {
    /** The lightning of the last [WINDOW_MS] within [RADIUS_KM] of [lat]/[lon]; null: none (or no answer). */
    suspend fun nearby(lat: Double, lon: Double, now: Long): LightningNearby? = runCatching {
        // the newest first: under a storm the count cuts off the older ones
        val url = query(lat, lon, RADIUS_KM, now - WINDOW_MS, "FLASH_ID,GROUP_END_TIME,GEOM", "application/json", MAX_GROUPS)
            .addQueryParameter("sortBy", "GROUP_END_TIME DESC").build().toString()
        parse(http.getJson(url), lat, lon, now)
    }.getOrNull()

    /**
     * The flashes of each hour (by the hour's end) within about [NEAR_KM] of [lat]/[lon] since
     * [from]; null: no answer. Times only, as CSV – the outlines are not needed, and a day of a
     * storm overhead stays some tens of kilobytes.
     */
    suspend fun hourly(lat: Double, lon: Double, from: Long): Map<Long, Int>? = runCatching {
        val url = query(lat, lon, NEAR_KM, from, "FLASH_ID,GROUP_END_TIME", "csv", MAX_HOURLY_GROUPS).build()
        hours(http.getText(okhttp3.Request.Builder().url(url).build()))
    }.getOrNull()

    /** The groups since [from] in a box of [km] around the place (longitude, latitude: CRS:84 – EPSG:4326 would swap them). */
    private fun query(lat: Double, lon: Double, km: Double, from: Long, properties: String, format: String, count: Int): okhttp3.HttpUrl.Builder {
        val dLat = km / KM_PER_DEG
        val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val box = listOf(lon - dLon, lat - dLat, lon + dLon, lat + dLat).joinToString(",") { OpenMeteoSource.fmt(it) }
        return wfs.toHttpUrl().newBuilder()
            .addQueryParameter("service", "WFS").addQueryParameter("version", "2.0.0").addQueryParameter("request", "GetFeature")
            .addQueryParameter("typeNames", LAYER).addQueryParameter("outputFormat", format)
            .addQueryParameter("propertyName", properties).addQueryParameter("count", count.toString())
            .addQueryParameter("cql_filter", "GROUP_END_TIME AFTER ${Instant.ofEpochMilli(from)} AND BBOX(GEOM,$box,'CRS:84')")
    }

    companion object {
        const val WFS = "https://maps.dwd.de/geoserver/dwd/ows"
        const val LAYER = "dwd:Accumulated_Flash_Geometry"
        /** Lightning this near counts: a thunderstorm moves 30–50 km in an hour. */
        const val RADIUS_KM = 50.0
        /** Lightning this near counts as at the place: about as far as thunder is heard. */
        const val NEAR_KM = 15.0
        /** The flashes of the last 15 minutes – what is going on now. */
        const val WINDOW_MS = 15 * 60_000L
        /**
         * The newest groups only: under a storm overhead all of 15 minutes are thousands (1.4 MB),
         * the newest 400 some 30 KB – and they tell what is going on now.
         */
        const val MAX_GROUPS = 400
        /** Days of a storm overhead stay below this; past it the busiest hours lose some flashes. */
        private const val MAX_HOURLY_GROUPS = 20000
        private const val HOUR_MS = 3_600_000L
        private const val KM_PER_DEG = 111.2

        fun parse(root: JsonElement, lat: Double, lon: Double, now: Long): LightningNearby? {
            val features = root.obj()?.a("features") ?: return null
            var nearest: Pair<Double, Long>? = null
            val flashes = HashSet<Long>()
            for (f in features) {
                val o = f.obj() ?: continue
                val p = o.o("properties") ?: continue
                val time = p.s("GROUP_END_TIME")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: continue
                val (cLat, cLon) = middle(o.o("geometry")?.get("coordinates")) ?: continue
                val km = Geo.distanceKm(lat, lon, cLat, cLon)
                if (km > RADIUS_KM) continue
                p.l("FLASH_ID")?.let(flashes::add)
                if (nearest == null || km < nearest.first) nearest = km to time
            }
            val (km, time) = nearest ?: return null
            return LightningNearby(km, ((now - time) / 60_000L).toInt().coerceAtLeast(0), flashes.size.coerceAtLeast(1), more = features.size >= MAX_GROUPS)
        }

        /**
         * Flashes per hour of the CSV answer: distinct flashes of the groups ending in the hour (UTC
         * times). Null if it is no such answer (an error the server sends as text): unknown, not none.
         */
        fun hours(csv: String): Map<Long, Int>? {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val head = lines.firstOrNull()?.split(',') ?: return null
            val id = head.indexOf("FLASH_ID"); val at = head.indexOf("GROUP_END_TIME")
            if (id < 0 || at < 0) return null
            val flashes = HashMap<Long, HashSet<String>>()
            for (line in lines.drop(1)) {
                val cells = line.split(',')
                val time = cells.getOrNull(at)?.let { t -> runCatching { Instant.parse(if (t.endsWith("Z")) t else t + "Z").toEpochMilli() }.getOrNull() } ?: continue
                val end = Math.floorDiv(time + HOUR_MS - 1, HOUR_MS) * HOUR_MS
                flashes.getOrPut(end) { HashSet() }.add(cells.getOrNull(id) ?: continue)
            }
            return flashes.mapValues { it.value.size }
        }

        /** The mean of all positions of a (multi) line or polygon: latitude to longitude. */
        private fun middle(coordinates: JsonElement?): Pair<Double, Double>? {
            var sumLat = 0.0; var sumLon = 0.0; var n = 0
            fun walk(e: JsonElement?) {
                val a = e as? JsonArray ?: return
                val x = a.getOrNull(0).dbl(); val y = a.getOrNull(1).dbl()
                if (x != null && y != null && a.size <= 3) { sumLon += x; sumLat += y; n++ } else a.forEach(::walk)
            }
            walk(coordinates)
            return if (n == 0) null else sumLat / n to sumLon / n
        }
    }
}
