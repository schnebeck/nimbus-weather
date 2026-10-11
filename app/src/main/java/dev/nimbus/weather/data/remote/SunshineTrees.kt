/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SunshineTrees.kt
 * Gradient-boosted trees learnt by tools/sunshine_calibration.py, read from the app's resources and evaluated.
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

/**
 * Learnt trees: the sum of one leaf of each tree (and the baseline) is the minutes of sunshine.
 * Each tree a list of nodes; a leaf has no left child (−1); a missing feature (NaN) goes the way
 * the learning sent missing values.
 */
internal class SunshineTrees(
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
        /** [features]: the order the evaluating code builds them in – the file must name the same. */
        fun parse(root: JsonElement, features: List<String>): SunshineTrees {
            val o = requireNotNull(root.obj())
            require(o.a("features")?.map { it.str() } == features) { "the model's features differ" }
            val trees = requireNotNull(o.a("trees")).map { requireNotNull(it.obj()) }
            fun ints(k: String) = Array(trees.size) { t -> trees[t].doubles(k).map { requireNotNull(it).toInt() }.toIntArray() }
            fun reals(k: String) = Array(trees.size) { t -> trees[t].doubles(k).map { requireNotNull(it) }.toDoubleArray() }
            return SunshineTrees(
                requireNotNull(o.d("baseline")), ints("f"), reals("t"), ints("l"), ints("r"),
                Array(trees.size) { t -> trees[t].doubles("m").map { it == 1.0 }.toBooleanArray() }, reals("v"),
            )
        }

        /**
         * The trees at the absolute resource [path] (the release build renames the classes and their
         * packages); null if unreadable – the callers then keep Open-Meteo's value.
         */
        fun load(path: String, features: List<String>): SunshineTrees? = runCatching {
            parse(JsonCodec.parseToJsonElement(
                requireNotNull(SunshineTrees::class.java.getResourceAsStream(path)) { "$path missing" }.bufferedReader().use { it.readText() },
            ), features)
        }.onFailure { android.util.Log.w("Nimbus", "sunshine model unreadable: ${it.message}") }.getOrNull()
    }
}

/**
 * The sun over the hour ending [hourEnd] at [lat]/[lon] and what a clear sky gives then – as the
 * learning computed them (tools/sunshine_calibration.py): six steps through the hour, the direct
 * beam by Meinel's model, the global irradiance by Haurwitz's.
 */
internal class SunHour(hourEnd: Long, lat: Double, lon: Double) {
    /** The sun's height in radians, every ten minutes from five past the hour's start. */
    private val heights = DoubleArray(6) { k -> Moon.sunAltitude(hourEnd - (5 + 10 * k) * 60_000L, lat, lon) * PI / 180 }
    /** The sun above the horizon in some part of the hour. */
    val up: Boolean get() = heights.max() > 0
    /** Mean height (degrees). */
    val elevation: Double get() = heights.average() * 180 / PI
    /** Mean direct normal irradiance of a clear sky (W/m²). */
    val clearDirect: Double = heights.sumOf { if (it <= 0.01) 0.0 else 1367 * 0.7.pow((1 / sin(it)).pow(0.678)) } / 6
    /** Mean global irradiance of a clear sky (W/m²). */
    val clearGlobal: Double = heights.sumOf { if (it <= 0.01) 0.0 else 1098 * sin(it) * exp(-0.057 / sin(it)) } / 6
}
