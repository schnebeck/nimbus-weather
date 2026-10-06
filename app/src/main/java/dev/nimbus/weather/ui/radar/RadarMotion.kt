/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarMotion.kt
 * How the rain moves between two radar frames: block matching, outliers, smoothing.
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

package dev.nimbus.weather.ui.radar

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Motion between two frames on a coarse grid of block centres, in field pixels per frame step. */
class Flow(val gw: Int, val gh: Int, private val x0: Float, private val y0: Float, private val step: Float, val u: FloatArray, val v: FloatArray) {
    /** Bilinear motion at field pixel ([x], [y]) into [out] (u, v). */
    fun at(x: Float, y: Float, out: FloatArray) {
        val gx = ((x - x0) / step).coerceIn(0f, (gw - 1).toFloat())
        val gy = ((y - y0) / step).coerceIn(0f, (gh - 1).toFloat())
        val ix = min(gx.toInt(), gw - 2).coerceAtLeast(0)
        val iy = min(gy.toInt(), gh - 2).coerceAtLeast(0)
        if (gw < 2 || gh < 2) { out[0] = u[0]; out[1] = v[0]; return }
        val fx = gx - ix; val fy = gy - iy
        val i = iy * gw + ix
        out[0] = (u[i] * (1 - fx) + u[i + 1] * fx) * (1 - fy) + (u[i + gw] * (1 - fx) + u[i + gw + 1] * fx) * fy
        out[1] = (v[i] * (1 - fx) + v[i + 1] * fx) * (1 - fy) + (v[i + gw] * (1 - fx) + v[i + gw + 1] * fx) * fy
    }

    /** The same motion over [k] times the time (a velocity carried over a longer gap). */
    fun scaled(k: Float) = Flow(gw, gh, x0, y0, step, FloatArray(u.size) { u[it] * k }, FloatArray(v.size) { v[it] * k })

    companion object {
        fun still() = Flow(1, 1, 0f, 0f, 1f, floatArrayOf(0f), floatArrayOf(0f))
    }
}

/** How the rain moves between two frames – pure computations, unit tested. */
object RadarMotion {
    /**
     * Motion from [a] to [b] by block matching on a coarse copy (intensity = wet × dBZ): for every
     * block with rain the shift with the smallest difference, refined below a coarse pixel, then
     * smoothed; blocks without rain take the motion of their surroundings (or of the whole field).
     * A block whose motion is far off the motion around it (or of the whole field, when it stands
     * alone) is not believed: a cell that dissolves while another forms nearby matches the new one
     * – the pictures cannot tell "moved" from "gone and new elsewhere", but rain drifts with the
     * flow it is in. Such a block takes the motion around it, and the cell fades where it is.
     * [maxShift]: the largest motion to look for, in field pixels.
     */
    fun motion(a: ViewFrame, b: ViewFrame, w: Int, h: Int, maxShift: Float): Flow {
        val f = max(1, ceil(max(w, h) / 128.0).toInt())
        val cw = (w + f - 1) / f; val ch = (h + f - 1) / f
        val ca = coarse(a, w, h, f, cw, ch); val cb = coarse(b, w, h, f, cw, ch)
        val bs = 12; val stride = 6
        val r = ceil(maxShift / f).toInt().coerceIn(2, 16)
        val gw = max(1, (cw - bs) / stride + 1); val gh = max(1, (ch - bs) / stride + 1)
        val u = FloatArray(gw * gh); val v = FloatArray(gw * gh); val wt = FloatArray(gw * gh)
        val sad = FloatArray((2 * r + 1) * (2 * r + 1))
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val bx = gx * stride; val by = gy * stride
            var mass = 0f
            for (y in by until min(by + bs, ch)) for (x in bx until min(bx + bs, cw)) mass += ca[y * cw + x]
            if (mass < 0.4f) continue
            var best = Float.MAX_VALUE; var bdx = 0; var bdy = 0
            for (dy in -r..r) for (dx in -r..r) {
                var s = 0f
                for (y in by until min(by + bs, ch)) {
                    val yy = y + dy
                    for (x in bx until min(bx + bs, cw)) {
                        val xx = x + dx
                        val vb = if (yy in 0 until ch && xx in 0 until cw) cb[yy * cw + xx] else 0f
                        s += abs(ca[y * cw + x] - vb)
                    }
                }
                // a slight preference for small motions: still areas stay still
                s += (abs(dx) + abs(dy)) * mass * 0.002f
                sad[(dy + r) * (2 * r + 1) + dx + r] = s
                if (s < best) { best = s; bdx = dx; bdy = dy }
            }
            fun at(dx: Int, dy: Int) = sad[(dy + r) * (2 * r + 1) + dx + r]
            // Below a coarse pixel: the vertex of a parabola through the best value and its neighbours
            fun sub(m: Float, p: Float, c: Float): Float { val d = m - 2 * c + p; return if (d > 1e-6f) ((m - p) / (2 * d)).coerceIn(-0.5f, 0.5f) else 0f }
            val sx = if (bdx in -r + 1 until r) sub(at(bdx - 1, bdy), at(bdx + 1, bdy), best) else 0f
            val sy = if (bdy in -r + 1 until r) sub(at(bdx, bdy - 1), at(bdx, bdy + 1), best) else 0f
            val i = gy * gw + gx
            u[i] = (bdx + sx) * f; v[i] = (bdy + sy) * f; wt[i] = mass
        }
        rejectOutliers(u, v, wt, gw, gh, tolerance = 2.5f * f)
        // Fill and smooth: weighted mean of the 3 × 3 neighbourhood, twice; empty blocks get the field's mean
        val total = wt.sum()
        val mu = if (total > 0) (0 until u.size).sumOf { (u[it] * wt[it]).toDouble() }.toFloat() / total else 0f
        val mv = if (total > 0) (0 until v.size).sumOf { (v[it] * wt[it]).toDouble() }.toFloat() / total else 0f
        var cu = u; var cv = v; var cwt = wt
        repeat(2) {
            val nu = FloatArray(gw * gh); val nv = FloatArray(gw * gh); val nw = FloatArray(gw * gh)
            for (gy in 0 until gh) for (gx in 0 until gw) {
                var su = 0f; var sv = 0f; var sw = 0f
                for (yy in max(0, gy - 1)..min(gh - 1, gy + 1)) for (xx in max(0, gx - 1)..min(gw - 1, gx + 1)) {
                    val j = yy * gw + xx
                    val ww = cwt[j]
                    su += cu[j] * ww; sv += cv[j] * ww; sw += ww
                }
                val i = gy * gw + gx
                val prior = 0.05f * (total / (gw * gh)).coerceAtLeast(1e-3f)
                nu[i] = (su + mu * prior) / (sw + prior); nv[i] = (sv + mv * prior) / (sw + prior); nw[i] = sw / 9f
            }
            cu = nu; cv = nv; cwt = nw
        }
        val x0 = (bs / 2f) * f; val step = (stride * f).toFloat()
        return Flow(gw, gh, x0, x0, step, cu, cv)
    }

    /** Blocks of the surroundings (beyond the block's own cell) that a block's motion is compared with. */
    private const val AROUND = 6

    /**
     * Drops ([wt] = 0) the blocks whose motion is further than [tolerance] (field pixels, or 60 %
     * of the reference motion if more) from the motion they are in:
     * - a rain area (connected blocks) smaller than half the rain, against the whole field –
     *   a small cell cannot be checked against itself;
     * - a single block against its surroundings: the mass-weighted median of the blocks
     *   2 … [AROUND] blocks away – or of the whole field when nothing rains there.
     */
    private fun rejectOutliers(u: FloatArray, v: FloatArray, wt: FloatArray, gw: Int, gh: Int, tolerance: Float) {
        fun median(idx: List<Int>, c: FloatArray): Float {
            val s = idx.sortedBy { c[it] }
            val half = idx.sumOf { wt[it].toDouble() } / 2
            var acc = 0.0
            for (j in s) { acc += wt[j]; if (acc >= half) return c[j] }
            return c[s.last()]
        }
        fun off(du: Float, dv: Float, ru: Float, rv: Float) = hypot(du - ru, dv - rv) > max(tolerance, 0.6f * hypot(ru, rv))
        val all = wt.indices.filter { wt[it] > 0f }
        if (all.size < 2) return
        val gu = median(all, u); val gv = median(all, v)
        val total = all.sumOf { wt[it].toDouble() }
        // Rain areas: blocks with rain connected to each other (8 neighbours)
        val area = IntArray(wt.size) { -1 }
        val areas = ArrayList<List<Int>>()
        for (s in all) {
            if (area[s] >= 0) continue
            val members = ArrayList<Int>(); val queue = ArrayDeque<Int>()
            area[s] = areas.size; queue.add(s)
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); members += i
                val x = i % gw; val y = i / gw
                for (yy in max(0, y - 1)..min(gh - 1, y + 1)) for (xx in max(0, x - 1)..min(gw - 1, x + 1)) {
                    val j = yy * gw + xx
                    if (wt[j] > 0f && area[j] < 0) { area[j] = areas.size; queue.add(j) }
                }
            }
            areas += members
        }
        val dropAreas = areas.filter { m ->
            m.sumOf { wt[it].toDouble() } < total / 2 && off(median(m, u), median(m, v), gu, gv)
        }
        val drop = ArrayList<Int>()
        dropAreas.forEach { drop += it }
        for (i in all) {
            val gx = i % gw; val gy = i / gw
            val around = ArrayList<Int>()
            for (yy in max(0, gy - AROUND)..min(gh - 1, gy + AROUND)) for (xx in max(0, gx - AROUND)..min(gw - 1, gx + AROUND)) {
                if (abs(xx - gx) <= 1 && abs(yy - gy) <= 1) continue
                val j = yy * gw + xx
                if (wt[j] > 0f) around += j
            }
            val ru = if (around.isEmpty()) gu else median(around, u)
            val rv = if (around.isEmpty()) gv else median(around, v)
            if (off(u[i], v[i], ru, rv)) drop += i
        }
        for (i in drop) wt[i] = 0f
    }

    private fun coarse(a: ViewFrame, w: Int, h: Int, f: Int, cw: Int, ch: Int): FloatArray {
        val out = FloatArray(cw * ch)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val wet = (a.wet[i].toInt() and 0xFF) / 255f
            if (wet <= 0f) continue
            val d = (a.dbz[i].toInt() and 0xFF) / 2f
            out[(y / f) * cw + x / f] += wet * d / 60f
        }
        val inv = 1f / (f * f)
        for (i in out.indices) out[i] *= inv
        return out
    }
}
