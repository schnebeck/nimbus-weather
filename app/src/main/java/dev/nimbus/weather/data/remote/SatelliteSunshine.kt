/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SatelliteSunshine.kt
 * The hour's sunshine from the satellite's radiation and the model's low cloud, learnt from DWD stations.
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

import dev.nimbus.weather.util.Moon
import kotlinx.serialization.json.JsonElement
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** An hour of the satellite at a place: Open-Meteo's sunshine (minutes) and the radiation (W/m², hour means). */
data class SatelliteHour(val sunshine: Double?, val direct: Double?, val global: Double?, val diffuse: Double?)

/**
 * The sunshine of an hour from what the satellite says of it – Open-Meteo's own sunshine, the
 * direct and the global irradiance against a clear sky's, the diffuse share – and the model's low
 * cloud, with the sun's height. Open-Meteo's sunshine counts an hour of passing showers as sunny
 * once the hour's mean direct irradiance is high; the weather of the hour as a whole tells how
 * long the sun shone. Gradient-boosted trees learnt from DWD stations measuring sunshine read it
 * ([MODEL], written and checked by tools/sunshine_calibration.py, see docs/STATIONS.md); they know
 * neither month nor place, so they hold for any of them.
 */
object SatelliteSunshine {
    /** Absolute: the release build renames this class and its package, a relative path would not find the file. */
    const val MODEL = "/dev/nimbus/weather/data/remote/sunshine_model.json"

    /** Minutes of sunshine in the hour ending [hourEnd]; Open-Meteo's where the radiation is missing, the sun down or the model unreadable. */
    fun minutes(hour: SatelliteHour, hourEnd: Long, lat: Double, lon: Double, lowCloud: Double?): Double? =
        model?.let { m -> features(hour, hourEnd, lat, lon, lowCloud)?.let(m::minutes) } ?: hour.sunshine

    /** The hours of [hours] in minutes of sunshine; [lowCloud]: the model's low cloud (%) of the hour ending at a time. */
    fun minutes(hours: Map<Long, SatelliteHour>, lat: Double, lon: Double, lowCloud: (Long) -> Double? = { null }): Map<Long, Double> =
        hours.mapNotNull { (t, h) -> minutes(h, t, lat, lon, lowCloud(t))?.let { t to it } }.toMap()

    /** In the order of the model's features (see the tool); null: no estimate. */
    internal fun features(h: SatelliteHour, hourEnd: Long, lat: Double, lon: Double, lowCloud: Double?): DoubleArray? {
        val sun = h.sunshine ?: return null
        val direct = h.direct ?: return null
        val global = h.global ?: return null
        val diffuse = h.diffuse ?: return null
        // the sun's height in six steps through the hour (radians)
        val heights = DoubleArray(6) { k -> Moon.sunAltitude(hourEnd - (5 + 10 * k) * 60_000L, lat, lon) * PI / 180 }
        if (heights.max() <= 0) return null
        val clearDirect = heights.sumOf { if (it <= 0.01) 0.0 else 1367 * 0.7.pow((1 / sin(it)).pow(0.678)) } / 6
        val clearGlobal = heights.sumOf { if (it <= 0.01) 0.0 else 1098 * sin(it) * exp(-0.057 / sin(it)) } / 6
        return doubleArrayOf(
            sun,
            if (clearDirect > 0) direct / clearDirect else 0.0,
            if (clearGlobal > 0) global / clearGlobal else 0.0,
            if (global > 0) diffuse / global else 1.0,
            heights.average() * 180 / PI,
            lowCloud ?: Double.NaN,
        )
    }

    /** Null if unreadable: the sunshine is then Open-Meteo's – the look-back must not fail for it. */
    internal val model: Trees? by lazy {
        runCatching {
            Trees.parse(JsonCodec.parseToJsonElement(
                requireNotNull(SatelliteSunshine::class.java.getResourceAsStream(MODEL)) { "$MODEL missing" }.bufferedReader().use { it.readText() },
            ))
        }.onFailure { android.util.Log.w("Nimbus", "sunshine model unreadable: ${it.message}") }.getOrNull()
    }

    /** The learnt trees: each a list of nodes; a leaf has no left child (−1). */
    internal class Trees(
        private val baseline: Double, private val feature: Array<IntArray>, private val threshold: Array<DoubleArray>,
        private val left: Array<IntArray>, private val right: Array<IntArray>, private val missingLeft: Array<BooleanArray>,
        private val value: Array<DoubleArray>,
    ) {
        fun minutes(x: DoubleArray): Double {
            var total = baseline
            for (t in feature.indices) {
                var i = 0
                while (left[t][i] >= 0) {
                    val v = x[feature[t][i]]
                    i = if (if (v.isNaN()) missingLeft[t][i] else v <= threshold[t][i]) left[t][i] else right[t][i]
                }
                total += value[t][i]
            }
            return total.coerceIn(0.0, 60.0)
        }

        companion object {
            val FEATURES = listOf("sunshine", "direct", "global", "diffuse", "elevation", "low")

            fun parse(root: JsonElement): Trees {
                val o = requireNotNull(root.obj())
                require(o.a("features")?.map { it.str() } == FEATURES) { "the model's features differ" }
                val trees = requireNotNull(o.a("trees")).map { requireNotNull(it.obj()) }
                fun ints(k: String) = Array(trees.size) { t -> trees[t].doubles(k).map { requireNotNull(it).toInt() }.toIntArray() }
                fun reals(k: String) = Array(trees.size) { t -> trees[t].doubles(k).map { requireNotNull(it) }.toDoubleArray() }
                return Trees(
                    requireNotNull(o.d("baseline")), ints("f"), reals("t"), ints("l"), ints("r"),
                    Array(trees.size) { t -> trees[t].doubles("m").map { it == 1.0 }.toBooleanArray() }, reals("v"),
                )
            }
        }
    }
}
