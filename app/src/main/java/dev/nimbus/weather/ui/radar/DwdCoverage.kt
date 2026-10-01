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

import android.graphics.BitmapFactory
import dev.nimbus.weather.data.remote.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Past radar frames used to stack two layers – DWD and RainViewer, each at 85 % – so over
 * Germany, where both show the same rain, the past looked more opaque than the forecast (DWD
 * only). The area the DWD composite covers is read once from a DWD image (its "no data" grey
 * surrounds it) and kept on disk; inside it RainViewer pixels are dropped.
 */
object DwdCoverage {
    // Lat/lon grid over the DWD bounds, 0.025° per pixel
    const val W = 696
    const val H = 428
    const val LON0 = 1.4
    const val LON1 = 18.8
    const val LAT0 = 45.6
    const val LAT1 = 56.3
    private const val MAX_AGE_MS = 30L * 24 * 3_600_000L

    @Volatile private var mask: BooleanArray? = null
    @Volatile var dir: File? = null
    private val mutex = Mutex()

    /** Inside the DWD radar area (false while the mask is not loaded). */
    fun covers(lat: Double, lon: Double): Boolean {
        val m = mask ?: return false
        val x = ((lon - LON0) / (LON1 - LON0) * W).toInt()
        val y = ((LAT1 - lat) / (LAT1 - LAT0) * H).toInt()
        if (x !in 0 until W || y !in 0 until H) return false
        return m[y * W + x]
    }

    val ready: Boolean get() = mask != null

    /** Loads the mask from disk or, at most once a month, from one DWD image (~20 kB). */
    suspend fun ensure(http: OkHttpClient) = mutex.withLock {
        if (mask != null) return@withLock
        val file = dir?.let { File(it, "dwd_coverage.png") }
        val now = System.currentTimeMillis()
        val bytes = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() && now - it.lastModified() < MAX_AGE_MS }?.readBytes()
        } ?: run {
            val time = RadarSources.latestAnalysis ?: RadarSources.checkLatest(http) ?: return@withLock
            val url = RadarSources.DWD_WMS + "?service=WMS&version=1.1.1&request=GetMap&layers=${RadarSources.DWD_LAYER}" +
                "&styles=&format=image/png&transparent=true&srs=EPSG:4326&bbox=$LON0,$LAT0,$LON1,$LAT1" +
                "&width=$W&height=$H&time=${RadarSources.isoTime(time)}"
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                        if (r.isSuccessful && r.header("Content-Type")?.startsWith("image/png") == true) r.body.bytes() else null
                    }
                }.getOrNull()?.also { b -> file?.let { runCatching { it.parentFile?.mkdirs(); it.writeBytes(b) } } }
            }
        } ?: return@withLock
        val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return@withLock
        if (bmp.width != W || bmp.height != H) return@withLock
        val px = IntArray(W * H).also { bmp.getPixels(it, 0, W, 0, 0, W, H) }
        mask = withContext(Dispatchers.Default) { compute(px, W, H) }
    }

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
