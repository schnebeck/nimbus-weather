/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/CommunitySource.kt
 * Readings of nearby citizen sensors from Sensor.Community.
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

import dev.nimbus.weather.data.model.CommunityObservation
import dev.nimbus.weather.data.model.Representative
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * Sensor.Community (formerly luftdaten.info): open citizen-science sensor network.
 * The "area" filter returns the readings of the last five minutes around a point.
 * Private sensors vary in quality, so values are aggregated with a robust median and
 * outliers are dropped.
 */
class CommunitySource(private val http: OkHttpClient, private val baseUrl: String = "https://data.sensor.community") {

    /** The sensors around the place at [elevationM] metres (null: unknown) – only those at its height. */
    suspend fun nearby(lat: Double, lon: Double, elevationM: Double? = null): CommunityObservation? {
        for (radius in listOf(4.0, 12.0)) {
            val url = String.format(Locale.US, "%s/airrohr/v1/filter/area=%.4f,%.4f,%.0f", baseUrl, lat, lon, radius)
            val result = aggregate(http.getJson(url), radius, elevationM)
            if (result != null && result.sensorCount >= 3) return result
        }
        return null
    }

    companion object {
        fun aggregate(root: JsonElement, radiusKm: Double, elevationM: Double? = null): CommunityObservation? {
            val entries = root.arr()?.mapNotNull { it as? JsonObject } ?: return null
            // Latest reading per sensor location, outdoor only.
            val byLocation = LinkedHashMap<String, JsonObject>()
            entries.forEach { e ->
                val loc = e.o("location") ?: return@forEach
                if (loc.s("indoor") == "1" || loc["indoor"].dbl() == 1.0) return@forEach
                // a sensor up the mountain or down in the valley measures another place
                if (!Representative.height(loc["altitude"].dbl() ?: loc.s("altitude")?.toDoubleOrNull(), elevationM)) return@forEach
                val key = (loc.l("id") ?: return@forEach).toString() + "/" + (e.o("sensor")?.l("id") ?: 0)
                val prev = byLocation[key]
                if (prev == null || (e.s("timestamp") ?: "") > (prev.s("timestamp") ?: "")) byLocation[key] = e
            }
            val temps = ArrayList<Double>(); val hums = ArrayList<Double>(); val press = ArrayList<Double>()
            val p10 = ArrayList<Double>(); val p25 = ArrayList<Double>()
            byLocation.values.forEach { e ->
                e.a("sensordatavalues")?.forEach { v ->
                    val o = v as? JsonObject ?: return@forEach
                    val value = o["value"].dbl() ?: o.s("value")?.toDoubleOrNull() ?: return@forEach
                    when (o.s("value_type")) {
                        "temperature" -> if (value in -40.0..50.0) temps += value
                        "humidity" -> if (value in 1.0..100.0) hums += value
                        "pressure_at_sealevel" -> (value / 100.0).let { if (it in 940.0..1070.0) press += it }
                        "P1" -> if (value in 0.0..999.0) p10 += value
                        "P2" -> if (value in 0.0..999.0) p25 += value
                    }
                }
            }
            val t = robust(temps)
            val count = maxOf(t.size, hums.size, p10.size)
            if (count == 0) return null
            return CommunityObservation(
                sensorCount = count,
                radiusKm = radiusKm,
                temperature = median(t),
                humidity = median(robust(hums)),
                pressure = median(robust(press)),
                pm10 = median(robust(p10)),
                pm25 = median(robust(p25)),
                temperatureSpread = if (t.size >= 3) t.sorted().let { it[(it.size * 3) / 4] - it[it.size / 4] } else null,
            )
        }

        fun median(v: List<Double>): Double? {
            if (v.isEmpty()) return null
            val s = v.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }

        /** Drops values further than 3 scaled MADs from the median. */
        fun robust(v: List<Double>): List<Double> {
            if (v.size < 4) return v
            val m = median(v)!!
            val mad = median(v.map { kotlin.math.abs(it - m) })!!.coerceAtLeast(0.3)
            return v.filter { kotlin.math.abs(it - m) <= 3 * 1.4826 * mad }
        }
    }
}
