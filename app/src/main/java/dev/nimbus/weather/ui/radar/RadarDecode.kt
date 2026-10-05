/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarDecode.kt
 * Radar images to codes: a coloured picture (DWD, RainViewer) read back to the reflectivity of each
 * pixel, on a share of the cores.
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object RadarDecode {
    /**
     * Decoding (PNG and colours of 1.9 million pixels, a grid of numbers) gets at most half the
     * cores: cutting and drawing the frames shown must never wait behind a queue of downloads.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val decoding = Dispatchers.Default.limitedParallelism(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))

    /** Up to this many cells a picture is small (the preview's area: about 50,000). */
    private const val SMALL_CELLS = 100_000

    /** A small picture is decoded at once – never behind a queue of whole grids; a big one on [decoding]. */
    fun lane(cells: Int): CoroutineDispatcher = if (cells <= SMALL_CELLS) Dispatchers.Default else decoding

    /** A coloured radar image of [w] × [h] pixels as codes, null if it is not one. */
    suspend fun png(
        http: OkHttpClient, url: String, w: Int, h: Int, source: RadarPalette.Source, on: CoroutineDispatcher = lane(w * h),
    ): ByteArray? = pixels(http, url, w, h, on)?.let { px -> withContext(on) { codes(px, source) } }

    /** The pixels (ARGB, not premultiplied) of a PNG of [w] × [h], null if it is not one. */
    suspend fun pixels(http: OkHttpClient, url: String, w: Int, h: Int, on: CoroutineDispatcher = lane(w * h)): IntArray? {
        val bytes = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).await().use { r ->
                if (!r.isSuccessful || r.header("Content-Type")?.startsWith("image/png") != true) null else r.body.bytes()
            }
        } ?: return null
        return withContext(on) {
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
            if (bmp.width != w || bmp.height != h) { bmp.recycle(); return@withContext null }
            IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h); bmp.recycle() }
        }
    }

    /** A text answer (a grid of numbers), null on an error. */
    suspend fun text(http: OkHttpClient, url: String): String? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).await().use { r ->
            if (r.isSuccessful) r.body.string() else null
        }
    }

    /** Source pixels to one byte each: dBZ (8–95), plus 0x80 for snow (RainViewer), 0 where dry. */
    fun codes(px: IntArray, source: RadarPalette.Source): ByteArray {
        val out = ByteArray(px.size)
        var lastArgb = 0; var lastCode = 0
        for (i in px.indices) {
            val argb = px[i]
            if (argb == lastArgb) { out[i] = lastCode.toByte(); continue }
            val c = RadarPalette.decode(argb, source)
            val b = if (c < 0) 0 else (c and 0x7F) or (if (c and 0x100 != 0) RvMosaic.SNOW else 0)
            lastArgb = argb; lastCode = b
            out[i] = b.toByte()
        }
        return out
    }
}
