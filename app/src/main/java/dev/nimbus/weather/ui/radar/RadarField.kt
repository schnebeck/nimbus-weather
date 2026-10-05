/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarField.kt
 * The radar picture of the view: cut from the stored grids, smoothed, moved between frames.
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
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * RainViewer cells (Europe): one 512 × 512 tile per entry, keyed by [key], one byte per pixel –
 * dBZ, plus [SNOW] where the source marks snow, 0 where dry. Missing tiles count as dry.
 */
class RvMosaic(val z: Int, val tiles: Map<Long, ByteArray>) {
    private val size = 2 * O / (1 shl z)

    /** Code at a Mercator position, -1 outside the tiles held. */
    fun at(mx: Double, my: Double): Int {
        val fx = (mx + O) / size
        val fy = (O - my) / size
        val tx = floor(fx).toInt()
        val ty = floor(fy).toInt()
        val t = tiles[key(tx, ty)] ?: return -1
        val px = ((fx - tx) * 512).toInt().coerceIn(0, 511)
        val py = ((fy - ty) * 512).toInt().coerceIn(0, 511)
        return t[py * 512 + px].toInt() and 0xFF
    }

    companion object {
        const val SNOW = 0x80
        private const val O = 20037508.342789244
        fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }
}

/**
 * The Web-Mercator rectangle (metres) shown as [w] × [h] square pixels: the view with a margin,
 * so panning a little needs no new picture.
 */
class FieldGeo(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double, val w: Int, val h: Int) {
    /** Mercator metres per pixel. */
    val pxM: Double get() = (maxX - minX) / w
    fun mx(x: Double) = minX + (x + 0.5) * pxM
    fun my(y: Double) = maxY - (y + 0.5) * pxM
    fun lon(mx: Double) = Math.toDegrees(mx / R)
    fun lat(my: Double) = Math.toDegrees(atan(sinh(my / R)))
    val north get() = lat(maxY)
    val south get() = lat(minY)
    val west get() = lon(minX)
    val east get() = lon(maxX)
    val centerLat get() = lat((minY + maxY) / 2)
    /** Real kilometres per pixel at the centre. */
    val pxKm: Double get() = pxM * cos(Math.toRadians(centerLat)) / 1000.0

    /** True if [other] shows the same area at about the same resolution. */
    fun sameAs(other: FieldGeo?) = other != null && other.w == w && other.h == h &&
        abs(other.minX - minX) < pxM && abs(other.maxY - maxY) < pxM

    /** Does the view [s]..[n], [wst]..[e] lie inside, at a resolution this field still serves? */
    fun serves(s: Double, n: Double, wst: Double, e: Double, viewPxM: Double): Boolean {
        val x0 = R * Math.toRadians(wst); val x1 = R * Math.toRadians(e)
        val y0 = mercY(s); val y1 = mercY(n)
        return x0 >= minX && x1 <= maxX && y0 >= minY && y1 <= maxY && viewPxM in pxM / 2.5..pxM * 1.6
    }

    companion object {
        const val R = 6378137.0
        fun mercY(lat: Double) = R * ln(tan(Math.PI / 4 + Math.toRadians(lat.coerceIn(-85.0, 85.0)) / 2))

        /**
         * The field for a view: its bounds grown by [margin] on every side, at most [maxSide]
         * pixels on the long side, never finer than [minPxKm] (the radar has 1 km cells).
         */
        fun forView(s: Double, n: Double, wst: Double, e: Double, maxSide: Int = 1024, margin: Double = 0.2, minPxKm: Double = 0.12): FieldGeo {
            val x0 = R * Math.toRadians(wst); val x1 = R * Math.toRadians(e)
            val y0 = mercY(s); val y1 = mercY(n)
            val gx = (x1 - x0) * margin; val gy = (y1 - y0) * margin
            val minX = x0 - gx; val maxX = x1 + gx; val minY = y0 - gy; val maxY = y1 + gy
            val latC = Math.toDegrees(atan(sinh((minY + maxY) / 2 / R)))
            val minPxM = minPxKm * 1000.0 / cos(Math.toRadians(latC))
            val pxM = max(max(maxX - minX, maxY - minY) / maxSide, minPxM)
            val w = ceil((maxX - minX) / pxM).toInt().coerceAtLeast(1)
            val h = ceil((maxY - minY) / pxM).toInt().coerceAtLeast(1)
            return FieldGeo(minX, maxY - h * pxM, minX + w * pxM, maxY, w, h)
        }
    }
}

/**
 * One radar frame in the field's pixels: reflectivity (dBZ × 2 as unsigned byte), the wet share
 * (0–255) and, where RainViewer marks snow, the snow share (0–255; null: none).
 */
class ViewFrame(val dbz: ByteArray, val wet: ByteArray, val snow: ByteArray?)

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

/** Pure computations of the radar picture – no Android, unit tested. */
object RadarField {
    /**
     * Radar cells are 1 km: the smoothing reaches about half a cell (see [RadarPalette.field]) –
     * from 1.4 pixels per cell on, so the default view is smooth too (the GPU scales it up).
     */
    fun smoothRadius(pxKm: Double): Int = if (pxKm <= 0.0) 0 else (1.0 / pxKm / 2 + 0.3).toInt().coerceIn(0, 32)

    /**
     * The frame for [geo] from the composites' [layers] – at each pixel the first that covers it
     * and has a value there – else from RainViewer. Cells smaller than a pixel are averaged, cells
     * larger than a pixel are smoothed (two dimensions, on the reflectivity – no blocks).
     */
    fun extract(geo: FieldGeo, layers: List<RadarLayer>, rv: RvMosaic?): ViewFrame {
        val w = geo.w; val h = geo.h; val n = w * h
        val dbz = FloatArray(n); val wet = FloatArray(n)
        var snow: FloatArray? = null
        // Sub-samples per pixel (each axis) so zoomed-out pixels average their grid cells
        val degPx = Math.toDegrees(geo.pxM / FieldGeo.R)
        val k = ceil(degPx / RadarComposite.STEP).toInt().coerceIn(1, 3)
        val colLon = Array(k) { s -> DoubleArray(w) { x -> geo.lon(geo.minX + (x + (s + 0.5) / k) * geo.pxM) } }
        val rowLat = Array(k) { s -> DoubleArray(h) { y -> geo.lat(geo.maxY - (y + (s + 0.5) / k) * geo.pxM) } }
        val colMx = Array(k) { s -> DoubleArray(w) { x -> geo.minX + (x + (s + 0.5) / k) * geo.pxM } }
        val rowMy = Array(k) { s -> DoubleArray(h) { y -> geo.maxY - (y + (s + 0.5) / k) * geo.pxM } }
        // each layer's cell of every sub-sample column and row in its window (-1: outside it)
        val cols = layers.map { l -> Array(k) { s -> IntArray(w) { x -> (floor((colLon[s][x] - l.composite.lon0) / RadarComposite.STEP).toInt() - l.window.col0).takeIf { it in 0 until l.window.w } ?: -1 } } }
        val rows = layers.map { l -> Array(k) { s -> IntArray(h) { y -> (floor((l.composite.lat1 - rowLat[s][y]) / RadarComposite.STEP).toInt() - l.window.row0).takeIf { it in 0 until l.window.h } ?: -1 } } }
        val kk = (k * k).toFloat()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                var sum = 0f; var cnt = 0; var sn = 0
                for (sy in 0 until k) for (sx in 0 until k) {
                    var code = -1
                    for (li in layers.indices) {
                        val l = layers[li]
                        if (l.inside != null && !l.inside[i]) continue
                        val r = rows[li][sy][y]; val c = cols[li][sx][x]
                        val v = if (r < 0 || c < 0) RadarComposite.NO_DATA else l.codes[r * l.window.w + c].toInt() and 0xFF
                        if (v != RadarComposite.NO_DATA) { code = v; break }
                    }
                    // beyond the composites: RainViewer – the only one that marks snow
                    val fromRv = code < 0
                    if (fromRv) code = rv?.at(colMx[sx][x], rowMy[sy][y])?.coerceAtLeast(0) ?: 0
                    val d = code and 0x7F
                    if (d >= 8) { sum += d; cnt++; if (fromRv && code and RvMosaic.SNOW != 0) sn++ }
                }
                if (cnt > 0) {
                    dbz[i] = sum / cnt * (cnt / kk)          // weighted by wet share, as the smoothing expects
                    wet[i] = cnt / kk
                    if (sn > 0) (snow ?: FloatArray(n).also { snow = it })[i] = sn / kk
                }
            }
        }
        smooth(dbz, wet, snow, w, h, smoothRadius(geo.pxKm))
        val qd = ByteArray(n); val qw = ByteArray(n)
        val qs = snow?.let { ByteArray(n) }
        for (i in 0 until n) {
            qd[i] = (dbz[i] * 2).roundToInt().coerceIn(0, 255).toByte()
            qw[i] = (wet[i] * 255).roundToInt().coerceIn(0, 255).toByte()
            if (qs != null) qs[i] = (snow!![i] * 255).roundToInt().coerceIn(0, 255).toByte()
        }
        return ViewFrame(qd, qw, qs)
    }

    /**
     * Normalized smoothing as for the tiles ([RadarPalette.field]): [dbz] and [snow] arrive weighted
     * by [wet]; afterwards they are plain values again (dBZ ≥ 8 where wet, snow share 0–1).
     */
    fun smooth(dbz: FloatArray, wet: FloatArray, snow: FloatArray?, w: Int, h: Int, r: Int) {
        val n = w * h
        if (r > 0) {
            val tmp = FloatArray(n); val col = FloatArray(w)
            for (a in listOfNotNull(dbz, wet, snow)) repeat(2) { RadarPalette.boxBlur(a, tmp, col, w, h, r) }
            val inX = FloatArray(w) { 1f }.also { line -> repeat(2) { RadarPalette.boxBlur(line, FloatArray(w), FloatArray(w), w, 1, r) } }
            val inY = FloatArray(h) { 1f }.also { line -> repeat(2) { RadarPalette.boxBlur(line, FloatArray(h), FloatArray(h), h, 1, r) } }
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val c = wet[i]
                if (c > 1e-4f) { dbz[i] = maxOf(dbz[i] / c, RadarPalette.MIN_DBZ); snow?.let { it[i] /= c } } else { dbz[i] = 0f; snow?.let { it[i] = 0f } }
                wet[i] = c / (inX[x] * inY[y])
            }
        } else {
            for (i in 0 until n) {
                val c = wet[i]
                if (c > 1e-4f) { dbz[i] = maxOf(dbz[i] / c, RadarPalette.MIN_DBZ); snow?.let { it[i] /= c } } else dbz[i] = 0f
            }
        }
    }

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

    /**
     * The picture at [t] (0–1) between [a] and [b] ([b] null or [t] 0: [a] itself): every pixel
     * takes [a] from where the motion comes from and [b] from where it goes, blended by [t] – the
     * rain moves instead of fading. Colours as in the legend ([RadarPalette]), [snowAt] the snow
     * share from the temperature per pixel (null: what the source marks).
     *
     * [step] > 1 draws every step-th pixel only (the output is ⌈w/step⌉ × ⌈h/step⌉): while the
     * rain moves, half the resolution is plenty and four times as fast; the GPU scales it up.
     */
    fun render(a: ViewFrame, b: ViewFrame?, flow: Flow?, t: Float, w: Int, h: Int, snowAt: FloatArray?, out: IntArray, step: Int = 1) {
        val interp = b != null && flow != null && t > 0f
        val ow = (w + step - 1) / step; val oh = (h + step - 1) / step
        val off = (step - 1) / 2f
        // The motion is smooth: looked up on a grid of 4 output pixels, used for the pixels in between
        val fs = 4
        val fw = (ow + fs - 1) / fs; val fh = (oh + fs - 1) / fs
        val fu = FloatArray(if (interp) fw * fh else 0); val fv = FloatArray(fu.size)
        if (interp) {
            val mv = FloatArray(2)
            for (gy in 0 until fh) for (gx in 0 until fw) {
                flow!!.at((gx * fs + fs / 2) * step + off, (gy * fs + fs / 2) * step + off, mv)
                fu[gy * fw + gx] = mv[0]; fv[gy * fw + gx] = mv[1]
            }
        }
        val sa = FloatArray(3); val sb = FloatArray(3)
        for (oy in 0 until oh) {
            val y = oy * step + off
            val yi = min((oy * step + step / 2), h - 1)
            for (ox in 0 until ow) {
                val o = oy * ow + ox
                val x = ox * step + off
                val i = yi * w + min(ox * step + step / 2, w - 1)
                val wet: Float; val dbz: Float; val sn: Float
                if (!interp) {
                    wet = (a.wet[i].toInt() and 0xFF) / 255f
                    if (wet < 0.35f) { out[o] = 0; continue }
                    dbz = (a.dbz[i].toInt() and 0xFF) / 2f
                    sn = if (a.snow != null) (a.snow[i].toInt() and 0xFF) / 255f else 0f
                } else {
                    val fi = (oy / fs) * fw + ox / fs
                    val u = fu[fi]; val v = fv[fi]
                    sample(a, w, h, x - t * u, y - t * v, sa)
                    sample(b!!, w, h, x + (1 - t) * u, y + (1 - t) * v, sb)
                    wet = (1 - t) * sa[0] + t * sb[0]
                    if (wet < 0.35f) { out[o] = 0; continue }
                    dbz = ((1 - t) * sa[0] * sa[1] + t * sb[0] * sb[1]) / wet
                    sn = ((1 - t) * sa[0] * sa[2] + t * sb[0] * sb[2]) / wet
                }
                if (dbz < 8f) { out[o] = 0; continue }
                val snow = if (snowAt != null) max(snowAt[i], sn) else sn
                var c = RadarPalette.colorFor(dbz, snow)
                if (wet < 0.65f && c != 0) c = (((c ushr 24) * RadarPalette.edgeAlpha(wet)).toInt() shl 24) or (c and 0xFFFFFF)
                out[o] = c
            }
        }
    }

    /** Bilinear sample of [f] at ([x], [y]): wet share, dBZ (of the wet part), snow share. */
    private fun sample(f: ViewFrame, w: Int, h: Int, x: Float, y: Float, out: FloatArray) {
        val xf = x.coerceIn(0f, (w - 1).toFloat()); val yf = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = min(xf.toInt(), w - 1); val y0 = min(yf.toInt(), h - 1)
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val fx = xf - x0; val fy = yf - y0
        val j00 = y0 * w + x0; val j10 = y0 * w + x1; val j01 = y1 * w + x0; val j11 = y1 * w + x1
        val wet = f.wet; val dbz = f.dbz
        val w00 = (wet[j00].toInt() and 0xFF) * (1 - fx) * (1 - fy)
        val w10 = (wet[j10].toInt() and 0xFF) * fx * (1 - fy)
        val w01 = (wet[j01].toInt() and 0xFF) * (1 - fx) * fy
        val w11 = (wet[j11].toInt() and 0xFF) * fx * fy
        val ws = w00 + w10 + w01 + w11
        out[0] = ws / 255f
        if (ws < 1e-3f) { out[1] = 0f; out[2] = 0f; return }
        out[1] = (w00 * (dbz[j00].toInt() and 0xFF) + w10 * (dbz[j10].toInt() and 0xFF) +
            w01 * (dbz[j01].toInt() and 0xFF) + w11 * (dbz[j11].toInt() and 0xFF)) / (2f * ws)
        val sn = f.snow
        out[2] = if (sn == null) 0f else (w00 * (sn[j00].toInt() and 0xFF) + w10 * (sn[j10].toInt() and 0xFF) +
            w01 * (sn[j01].toInt() and 0xFF) + w11 * (sn[j11].toInt() and 0xFF)) / (255f * ws)
    }
}

/**
 * Loading a long time line coarse to fine: first one step every 2 hours, then every hour, every
 * 30 minutes, every 15, finally all – the player shows the whole time line from the start and moves the rain
 * between the steps it has; every finer level makes it more exact.
 */
object Progressive {
    private val LEVEL_MINUTES = intArrayOf(120, 60, 30, 15, 1)

    /** Strides (in steps) of the levels for steps of [stepMinutes]: 5 min → 24, 12, 6, 3, 1. */
    fun strides(stepMinutes: Int): IntArray =
        LEVEL_MINUTES.map { maxOf(1, it / maxOf(1, stepMinutes)) }.distinct().sortedDescending().toIntArray()

    /** Coarse levels loaded over the whole time line first (an overview: every 2 hours, every hour). */
    private const val OVERVIEW_MINUTES = 60
    /** Loaded before the overview: the steps right after the position. */
    private const val START_MINUTES = 30

    /**
     * Load order of [n] steps: the first [START_MINUTES] from [from], then the overview levels (every [OVERVIEW_MINUTES] and coarser) over the
     * whole time line, coarse first – the last step and [from] with the first –, then all others in
     * playback order from [from] on (those behind last): playback needs them in that order, and
     * they arrive faster than it plays.
     */
    fun order(n: Int, from: Int, strides: IntArray, stepMinutes: Int = 5, overviewMinutes: Int = OVERVIEW_MINUTES): List<Int> {
        if (n <= 0) return emptyList()
        val seen = BooleanArray(n)
        val out = ArrayList<Int>(n)
        fun rank(i: Int) = if (i >= from) i - from else n + (from - i)      // ahead first, then behind
        // The first half hour from the position first: playback can start at once
        (from..minOf(n - 1, from + START_MINUTES / maxOf(1, stepMinutes))).forEach { seen[it] = true; out += it }
        strides.filter { it * stepMinutes >= overviewMinutes }.forEachIndexed { level, s ->
            val take = (0 until n).filter { !seen[it] && (it % s == 0 || level == 0 && (it == n - 1 || it == from)) }
            take.sortedBy { rank(it) }.forEach { seen[it] = true; out += it }
        }
        (0 until n).filter { !seen[it] }.sortedBy { rank(it) }.forEach { out += it }
        return out
    }

    /** Frames further apart than this are not moved into each other (measured: beyond it the motion is guessed, not found). */
    const val MAX_MOTION_MS = 30 * 60_000L

    /**
     * How to show [t] (0–1) between two frames [gapMs] apart: moving the rain (true, at [t]) – or,
     * across a wider gap (finer steps still loading), without motion: the earlier frame, a short
     * blend in the middle, then the later one (returned fraction).
     */
    fun blend(gapMs: Long, t: Float): Pair<Boolean, Float> =
        if (gapMs <= MAX_MOTION_MS) true to t else false to ((t - 0.4f) / 0.2f).coerceIn(0f, 1f)

    /**
     * Steps a long live time line keeps for good while windowed: every [keepMinutes] by the clock
     * (:00, :20, :40 …; at most [MAX_MOTION_MS] apart, so playback can always move between them),
     * the first and the last one. The loop then starts over without preparing its start again –
     * and since they are chosen by time, a refreshed time line (a step later every 5 minutes)
     * keeps the same ones.
     */
    fun keepSteps(times: List<Long>, keepMinutes: Int): Set<Int> {
        val every = minOf(keepMinutes.toLong() * 60_000L, MAX_MOTION_MS)
        if (times.isEmpty()) return emptySet()
        return (times.indices.filter { times[it] % every == 0L } + 0 + times.lastIndex).toSet()
    }

    /** Can position [p] play: on a frame, or between two at most [MAX_MOTION_MS] apart ([timeOf] of an index). */
    fun playable(p: Float, n: Int, has: (Int) -> Boolean, timeOf: (Int) -> Long): Boolean {
        val (a, b) = bracket(p, n, has) ?: return false
        return if (b == a) abs(p - a) < 1e-3f || a == n - 1 else timeOf(b) - timeOf(a) <= MAX_MOTION_MS
    }

    /**
     * The steps to show at position [p]: the nearest one at or before it and the nearest after it
     * among those [has]; (a, a) right on a step or past the last one there is; null if there is
     * none at or before [p].
     */
    fun bracket(p: Float, n: Int, has: (Int) -> Boolean): Pair<Int, Int>? {
        if (n <= 0) return null
        val i = p.toInt().coerceIn(0, n - 1)
        var a = i
        while (a >= 0 && !has(a)) a--
        if (a < 0) return null
        if (p - i < 1e-4f && a == i) return a to a
        var b = i + 1
        while (b < n && !has(b)) b++
        return if (b < n) a to b else a to a
    }
}

/**
 * One display frame of radar playback – the same rule for the live loop and an archived day: the
 * position moves on at [stepMs] per step while the next step can be shown; when it cannot (not
 * loaded yet, or the frame window of a long time line is still moving), playback buffers – with
 * the loading hint – until [resumeAhead] steps ahead are there. It never just stands still. At the
 * end the live loop rests [restMs] and starts over; an archived day stops.
 */
object Playback {
    data class State(val position: Float, val buffering: Boolean = false, val restedMs: Float = 0f, val playing: Boolean = true)

    fun step(
        s: State, dtMs: Float, last: Int, loop: Boolean, stepMs: Float, canShow: (Float) -> Boolean,
        resumeAhead: Int = 12, restMs: Float = 1400f,
    ): State {
        if (!s.playing) return s
        val p = s.position
        if (p >= last) {
            if (!loop) return s.copy(playing = false, buffering = false)
            val rested = s.restedMs + dtMs
            return if (rested < restMs) s.copy(restedMs = rested, buffering = false)
            else State(0f, buffering = !canShow(minOf(resumeAhead, last).toFloat()))
        }
        // buffering ends with a stretch ahead – and playback moves on in the same frame
        if (s.buffering && !canShow(minOf(p + resumeAhead, last.toFloat()))) return s
        // on to where the time has gone – over several steps when a display frame took long,
        // but never past a step that cannot be shown
        val target = minOf(p + dtMs / stepMs, last.toFloat())
        var reach = p
        var k = p.toInt() + 1
        while (k <= target) {
            if (!canShow(k.toFloat())) return s.copy(position = reach, buffering = true)
            reach = k.toFloat(); k++
        }
        if (target > reach && !canShow(k.toFloat())) return s.copy(position = reach, buffering = reach == p)
        return s.copy(position = target, restedMs = 0f, buffering = false)
    }
}
