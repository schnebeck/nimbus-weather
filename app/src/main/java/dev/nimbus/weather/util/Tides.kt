/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/Tides.kt
 * Tide prediction from gauge measurements: harmonic analysis with eight partial tides.
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

package dev.nimbus.weather.util

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The water level at a tide gauge is modelled as a mean plus eight partial tides (constituents)
 * of known speed: the principal lunar and solar semidiurnal tides M2, S2, N2, the diurnal K1 and
 * O1, and the shallow-water tides M4, MS4 and M6 that shape the curve in the Wadden Sea and the
 * estuaries. Their amplitudes and phases are fitted by least squares to about four weeks of
 * measurements (enough to separate M2 from N2); the fit then predicts the coming days.
 *
 * Checked against measured high and low waters: about ±10–30 minutes at Cuxhaven, Norderney,
 * Büsum and Hamburg. Wind (surges) shifts the real curve – good for planning a walk on the beach,
 * not for navigation.
 */
object Tides {
    /** Angular speeds in degrees per hour: M2, S2, N2, K1, O1, M4, MS4, M6. */
    private val SPEEDS = doubleArrayOf(28.9841042, 30.0, 28.4397295, 15.0410686, 13.9430356, 57.9682084, 58.9841042, 86.9523127)
    private const val N2_INDEX = 2

    /** Fitted model; times relative to [refMs] in hours. */
    @Serializable
    data class Model(
        val refMs: Long,
        val mean: Double,
        val cosCoef: List<Double>,
        val sinCoef: List<Double>,
        /** Constituents used (N2 needs ≥ 25 days of data). */
        val speeds: List<Double>,
        val fittedAt: Long,
        /** Root mean square of the residuals (surge, noise), in the unit of the input. */
        val rms: Double,
    ) {
        fun at(tMs: Long): Double {
            val h = (tMs - refMs) / 3_600_000.0
            var v = mean
            for (k in speeds.indices) {
                val w = Math.toRadians(speeds[k]) * h
                v += cosCoef[k] * cos(w) + sinCoef[k] * sin(w)
            }
            return v
        }
    }

    @Serializable
    data class Extreme(val time: Long, val high: Boolean, val level: Double)

    /**
     * Fits the model to measurements (times in ms, ascending). Values are first averaged into
     * 10-minute bins, which removes waves and keeps the system small. Returns null with less than
     * 14 days of data.
     */
    fun fit(times: LongArray, values: DoubleArray, now: Long = System.currentTimeMillis()): Model? {
        if (times.size < 100) return null
        val span = times.last() - times.first()
        if (span < 14 * 24 * 3_600_000L) return null
        val bin = 10 * 60_000L
        val t = ArrayList<Long>()
        val v = ArrayList<Double>()
        var i = 0
        while (i < times.size) {
            val b = times[i] / bin
            var sum = 0.0
            var n = 0
            while (i < times.size && times[i] / bin == b) { if (!values[i].isNaN()) { sum += values[i]; n++ }; i++ }
            if (n > 0) { t += b * bin + bin / 2; v += sum / n }
        }
        val speeds = SPEEDS.filterIndexed { k, _ -> k != N2_INDEX || span >= 25 * 24 * 3_600_000L }
        val ref = t.first()
        val p = 1 + 2 * speeds.size
        // Normal equations AᵀA x = Aᵀy
        val ata = Array(p) { DoubleArray(p) }
        val aty = DoubleArray(p)
        val row = DoubleArray(p)
        for (j in t.indices) {
            val h = (t[j] - ref) / 3_600_000.0
            row[0] = 1.0
            for (k in speeds.indices) {
                val w = Math.toRadians(speeds[k]) * h
                row[1 + 2 * k] = cos(w)
                row[2 + 2 * k] = sin(w)
            }
            for (a in 0 until p) {
                aty[a] += row[a] * v[j]
                for (b in 0 until p) ata[a][b] += row[a] * row[b]
            }
        }
        val x = solve(ata, aty) ?: return null
        val model = Model(
            refMs = ref, mean = x[0],
            cosCoef = speeds.indices.map { x[1 + 2 * it] }, sinCoef = speeds.indices.map { x[2 + 2 * it] },
            speeds = speeds, fittedAt = now, rms = 0.0,
        )
        var sq = 0.0
        for (j in t.indices) { val d = v[j] - model.at(t[j]); sq += d * d }
        return model.copy(rms = kotlin.math.sqrt(sq / t.size))
    }

    /** Predicted curve from [from] to [to] in [stepMs] steps. */
    fun curve(model: Model, from: Long, to: Long, stepMs: Long = 10 * 60_000L): List<Pair<Long, Double>> =
        generateSequence(from) { it + stepMs }.takeWhile { it <= to }.map { it to model.at(it) }.toList()

    /**
     * High and low waters between [from] and [to]: local extremes of the predicted curve (1-minute
     * resolution), at least 3 hours apart – the shallow-water tides can put a small dent on top.
     */
    fun extremes(model: Model, from: Long, to: Long): List<Extreme> {
        val step = 60_000L
        val out = ArrayList<Extreme>()
        var prev = model.at(from - step)
        var cur = model.at(from)
        var t = from
        while (t < to) {
            val next = model.at(t + step)
            val high = cur >= prev && cur > next
            val low = cur <= prev && cur < next
            if (high || low) {
                val last = out.lastOrNull()
                if (last != null && last.high == high && t - last.time < 3 * 3_600_000L) {
                    if ((high && cur > last.level) || (low && cur < last.level)) out[out.lastIndex] = Extreme(t, high, cur)
                } else out += Extreme(t, high, cur)
            }
            prev = cur; cur = next; t += step
        }
        // A high directly followed by a high (dent removed) keeps only the more pronounced one.
        return out.filterIndexed { k, e -> k == 0 || out[k - 1].high != e.high || abs(out[k - 1].time - e.time) >= 3 * 3_600_000L }
    }

    /** Gaussian elimination with partial pivoting; null if singular. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { a[it].copyOf() }
        val y = b.copyOf()
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[piv][c])) piv = r
            if (abs(m[piv][c]) < 1e-12) return null
            val tmp = m[c]; m[c] = m[piv]; m[piv] = tmp
            val ty = y[c]; y[c] = y[piv]; y[piv] = ty
            for (r in c + 1 until n) {
                val f = m[r][c] / m[c][c]
                for (k in c until n) m[r][k] -= f * m[c][k]
                y[r] -= f * y[c]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = y[r]
            for (k in r + 1 until n) s -= m[r][k] * x[k]
            x[r] = s / m[r][r]
        }
        return x
    }
}
