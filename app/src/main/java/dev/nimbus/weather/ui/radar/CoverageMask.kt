/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/CoverageMask.kt
 * Where a composite's radars reach: a mask read from a picture of its service, kept on the
 * device and renewed by its age – for the DWD, MET Norway and any composite to come.
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
 * [RadarComposites.dir] and renewed once its picture is [maxAgeMs] old.
 */
class CoverageMask(
    private val fileName: String,
    val lon0: Double, val lat0: Double, val lon1: Double, val lat1: Double, val w: Int, val h: Int,
    private val url: suspend (OkHttpClient) -> String?,
    private val classify: (argb: IntArray, w: Int, h: Int) -> BooleanArray,
    private val maxAgeMs: Long = 30L * 24 * 3_600_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var mask: BooleanArray? = null
    /** When the picture behind [mask] was taken. */
    @Volatile private var takenAt = 0L
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

    /**
     * Loads the mask: from the device while its picture is younger than [maxAgeMs], else anew
     * from the service. When the service cannot be reached, the mask in use stays; without one,
     * an older picture on the device is better than none.
     */
    suspend fun ensure(http: OkHttpClient) = mutex.withLock {
        val now = clock()
        if (mask != null && now - takenAt < maxAgeMs) return@withLock
        val kept = keptPictureAt(http, fileName, w, h, maxAgeMs, now, olderIfNone = mask == null) { url(http) } ?: return@withLock
        mask = withContext(Dispatchers.Default) { classify(kept.pixels, w, h) }
        takenAt = kept.at
    }

    /** For tests: the mask as given. */
    internal fun set(m: BooleanArray) { mask = m }
}

/** A kept picture's pixels (ARGB, not premultiplied) and when it was fetched. */
internal class KeptPicture(val pixels: IntArray, val at: Long)

/**
 * A small picture of a service ([w] × [h], its ARGB pixels, not premultiplied) kept as [fileName]
 * in [RadarComposites.dir]: from the device while younger than [maxAgeMs], else fetched from
 * [url] and stored. Null if it can be had neither way.
 */
internal suspend fun keptPicture(http: OkHttpClient, fileName: String, w: Int, h: Int, maxAgeMs: Long, url: suspend () -> String?): IntArray? =
    keptPictureAt(http, fileName, w, h, maxAgeMs, System.currentTimeMillis(), olderIfNone = false, url)?.pixels

/** As [keptPicture], with the time of the picture; [olderIfNone]: the stored one past [maxAgeMs] when none can be fetched. */
internal suspend fun keptPictureAt(
    http: OkHttpClient, fileName: String, w: Int, h: Int, maxAgeMs: Long, now: Long, olderIfNone: Boolean, url: suspend () -> String?,
): KeptPicture? {
    val file = RadarComposites.dir?.let { File(it, fileName) }
    val stored = withContext(Dispatchers.IO) { file?.takeIf { it.exists() }?.let { it.lastModified() to it.readBytes() } }
    val (at, bytes) = stored?.takeIf { now - it.first < maxAgeMs }
        ?: url()?.let { u -> fetchPng(http, u)?.also { b -> store(file, b, now) }?.let { now to it } }
        ?: stored?.takeIf { olderIfNone }
        ?: return null
    val px = decodePicture(bytes, w, h) ?: return null
    return KeptPicture(px, at)
}

private suspend fun fetchPng(http: OkHttpClient, url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).await().use { r ->
            if (r.isSuccessful && r.header("Content-Type")?.startsWith("image/png") == true) r.body.bytes() else null
        }
    }.getOrNull()
}

/** Kept with the time it was fetched: its age decides when it is fetched again. */
private suspend fun store(file: File?, bytes: ByteArray, at: Long) = withContext(Dispatchers.IO) {
    file?.let { runCatching { it.parentFile?.mkdirs(); it.writeBytes(bytes); it.setLastModified(at) } }
}

private suspend fun decodePicture(bytes: ByteArray, w: Int, h: Int): IntArray? = withContext(Dispatchers.Default) {
    val opts = BitmapFactory.Options().apply { inPremultiplied = false }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
    if (bmp.width != w || bmp.height != h) { bmp.recycle(); return@withContext null }
    IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h); bmp.recycle() }
}
