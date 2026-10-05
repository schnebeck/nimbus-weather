/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/CoverageMask.kt
 * Where a composite's radars reach: a mask read once from a picture of its service and kept on
 * the device – for the DWD, MET Norway and any composite to come.
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
import dev.nimbus.weather.data.remote.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * A mask of [w] × [h] pixels over [lon0]…[lon1], [lat0]…[lat1] (degrees, row 0 in the north):
 * where the radars reach. Read from the picture at [url] by [classify], kept as [fileName] in
 * [RadarComposites.dir] and renewed after [maxAgeMs] – the radars rarely change.
 */
class CoverageMask(
    private val fileName: String,
    val lon0: Double, val lat0: Double, val lon1: Double, val lat1: Double, val w: Int, val h: Int,
    private val url: suspend (OkHttpClient) -> String?,
    private val classify: (argb: IntArray, w: Int, h: Int) -> BooleanArray,
    private val maxAgeMs: Long = 30L * 24 * 3_600_000L,
) {
    @Volatile private var mask: BooleanArray? = null
    private val mutex = Mutex()

    /** Whether the mask is loaded (until then a composite tells by its rectangle, or not at all). */
    val ready: Boolean get() = mask != null

    /** Inside the area its radars reach (false while the mask is not loaded). */
    fun covers(lat: Double, lon: Double): Boolean {
        val m = mask ?: return false
        val x = ((lon - lon0) / (lon1 - lon0) * w).toInt()
        val y = ((lat1 - lat) / (lat1 - lat0) * h).toInt()
        if (x !in 0 until w || y !in 0 until h) return false
        return m[y * w + x]
    }

    /** Loads the mask from the device or, at most every [maxAgeMs], from the service. */
    suspend fun ensure(http: OkHttpClient) = mutex.withLock {
        if (mask != null) return@withLock
        val px = keptPicture(http, fileName, w, h, maxAgeMs) { url(http) } ?: return@withLock
        mask = withContext(Dispatchers.Default) { classify(px, w, h) }
    }

    /** For tests: the mask as given. */
    internal fun set(m: BooleanArray) { mask = m }
}

/**
 * A small picture of a service ([w] × [h], its ARGB pixels, not premultiplied) kept as [fileName]
 * in [RadarComposites.dir]: from the device while younger than [maxAgeMs], else fetched from
 * [url] and stored. Null if it can be had neither way.
 */
internal suspend fun keptPicture(http: OkHttpClient, fileName: String, w: Int, h: Int, maxAgeMs: Long, url: suspend () -> String?): IntArray? {
    val file = RadarComposites.dir?.let { File(it, fileName) }
    val now = System.currentTimeMillis()
    val bytes = withContext(Dispatchers.IO) { file?.takeIf { it.exists() && now - it.lastModified() < maxAgeMs }?.readBytes() }
        ?: url()?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).build()).await().use { r ->
                        if (r.isSuccessful && r.header("Content-Type")?.startsWith("image/png") == true) r.body.bytes() else null
                    }
                }.getOrNull()?.also { b -> file?.let { runCatching { it.parentFile?.mkdirs(); it.writeBytes(b) } } }
            }
        }
        ?: return null
    return withContext(Dispatchers.Default) {
        val opts = BitmapFactory.Options().apply { inPremultiplied = false }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
        if (bmp.width != w || bmp.height != h) { bmp.recycle(); return@withContext null }
        IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h); bmp.recycle() }
    }
}
