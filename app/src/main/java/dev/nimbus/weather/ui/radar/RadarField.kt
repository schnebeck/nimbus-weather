/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarField.kt
 * The radar picture of the view: cut from the stored grids, smoothed and drawn.
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

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One radar frame in the field's pixels: reflectivity (dBZ × 2 as unsigned byte), the wet share
 * (0–255) and, where RainViewer marks snow, the snow share (0–255; null: none).
 */
class ViewFrame(val dbz: ByteArray, val wet: ByteArray, val snow: ByteArray?)

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
     * [fromRv]: marks the pixels no composite has a value for (RainViewer's, or nobody's);
     * [minSmooth]: at least this smoothing (pixels) – a field pixel larger than a screen pixel.
     */
    fun extract(geo: FieldGeo, layers: List<RadarLayer>, rv: RvMosaic?, fromRv: BooleanArray? = null, minSmooth: Int = 0): ViewFrame {
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
        val cols = layers.map { l -> Array(k) { s -> IntArray(w) { x -> (floor((colLon[s][x] - l.composite.lon0) / l.composite.step).toInt() - l.window.col0).takeIf { it in 0 until l.window.w } ?: -1 } } }
        val rows = layers.map { l -> Array(k) { s -> IntArray(h) { y -> (floor((l.composite.lat1 - rowLat[s][y]) / l.composite.step).toInt() - l.window.row0).takeIf { it in 0 until l.window.h } ?: -1 } } }
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
                    val viaRv = code < 0
                    if (viaRv) { fromRv?.set(i, true); code = rv?.at(colMx[sx][x], rowMy[sy][y])?.coerceAtLeast(0) ?: 0 }
                    val d = code and 0x7F
                    if (d >= 8) { sum += d; cnt++; if (viaRv && code and RvMosaic.SNOW != 0) sn++ }
                }
                if (cnt > 0) {
                    dbz[i] = sum / cnt * (cnt / kk)          // weighted by wet share, as the smoothing expects
                    wet[i] = cnt / kk
                    if (sn > 0) (snow ?: FloatArray(n).also { snow = it })[i] = sn / kk
                }
            }
        }
        smooth(dbz, wet, snow, w, h, max(smoothRadius(geo.pxKm), minSmooth))
        val qd = ByteArray(n); val qw = ByteArray(n)
        val qs = snow?.let { ByteArray(n) }
        for (i in 0 until n) {
            qd[i] = (dbz[i] * 2).roundToInt().coerceIn(0, 255).toByte()
            qw[i] = (wet[i] * 255).roundToInt().coerceIn(0, 255).toByte()
            if (qs != null) qs[i] = (snow[i] * 255).roundToInt().coerceIn(0, 255).toByte()
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
                flow.at((gx * fs + fs / 2) * step + off, (gy * fs + fs / 2) * step + off, mv)
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
                    sample(b, w, h, x + (1 - t) * u, y + (1 - t) * v, sb)
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
    internal fun sample(f: ViewFrame, w: Int, h: Int, x: Float, y: Float, out: FloatArray) {
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
