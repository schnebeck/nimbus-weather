/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdCoverage.kt
 * Where the DWD radar composite measures – RainViewer is shown only outside of it.
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

import okhttp3.OkHttpClient

/**
 * DWD and RainViewer stacked, each at 85 %, show the same rain over Germany twice as opaque as
 * the forecast (DWD only). The area the DWD composite covers is read once from a DWD image (its
 * "no data" grey surrounds it) and kept on disk; inside it RainViewer pixels are dropped.
 */
internal object DwdCoverage {
    // Lat/lon grid over the DWD bounds, 0.025° per pixel
    const val W = 696
    const val H = 428
    const val LON0 = 1.4
    const val LON1 = 18.8
    const val LAT0 = 45.6
    const val LAT1 = 56.3

    /** One DWD image of the newest step (~20 kB), at most once a month. */
    private val mask = CoverageMask("dwd_coverage.png", LON0, LAT0, LON1, LAT1, W, H, url = { http ->
        (RadarLatest.known(DwdRadar) ?: RadarLatest.check(http, DwdRadar))?.let { time ->
            DwdRadar.WMS + "?service=WMS&version=1.1.1&request=GetMap&layers=${DwdRadar.LAYER}" +
                "&styles=&format=image/png&transparent=true&srs=EPSG:4326&bbox=$LON0,$LAT0,$LON1,$LAT1" +
                "&width=$W&height=$H&time=${RadarSources.isoTime(time)}"
        }
    }, classify = ::compute)

    /** Inside the DWD radar area (false while the mask is not loaded). */
    fun covers(lat: Double, lon: Double): Boolean = mask.covers(lat, lon)

    val ready: Boolean get() = mask.ready

    suspend fun ensure(http: OkHttpClient) = mask.ensure(http)

    /**
     * Covered pixels from a DWD composite image ([argb], row by row): everything that is not the
     * semi-transparent "no data" grey and cannot be reached from the image border without
     * crossing it – the area inside the grey ring (rain or valid "no rain").
     */
    fun compute(argb: IntArray, w: Int, h: Int): BooleanArray {
        fun grey(i: Int) = (argb[i] ushr 24) in 1..199
        val outside = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        fun push(i: Int) { if (!outside[i] && !grey(i)) { outside[i] = true; stack.addLast(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return BooleanArray(w * h) { !grey(it) && !outside[it] }
    }
}
