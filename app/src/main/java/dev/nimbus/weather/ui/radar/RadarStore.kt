/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarStore.kt
 * Radar frames downloaded once, decoded to reflectivity and kept on disk until they expire.
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

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The radar loop no longer comes from hundreds of map tiles per view. Every time step is loaded
 * once – each composite whole ([RadarComposite]: the DWD's, the KNMI's), RainViewer (Europe) as
 * the few tiles around the view – decoded to reflectivity (one byte per cell) and stored
 * compressed (about 60 kB per DWD step). The radar screen cuts, smooths, colours and animates
 * every view from here, panning and zooming need no network, and an archived day plays from disk.
 *
 * Each step is stored whole: a difference to the step before (plain, or after the motion) came
 * out larger than the step itself – the rain moves, so most of its edges change.
 *
 * Lifetime: each composite's own rule ([RadarComposite.expired]), RainViewer steps 3 hours.
 */
object RadarStore {
    @Volatile var dir: File? = null
    private const val MAGIC = 0x4E524431          // "NRD1"
    private const val RV_KEEP_MS = 3L * 3_600_000L
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** Decoded steps in memory (DWD grid 1.8 MB each, RainViewer tiles 256 kB). */
    private val memory by lazy {
        object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    }
    private val inflight = InFlight<String, ByteArray?>()
    /**
     * A service answers one big image at a time quickly; several in parallel keep the line busy.
     * Fewer than OkHttp's 5 per host: one stays free for the preview's small requests.
     */
    private val downloads = Semaphore(4)
    /** The preview's small requests: a lane of their own, never behind a queue of whole grids. */
    private val small = Semaphore(4)

    fun rvKey(path: String, z: Int, x: Int, y: Int) = "rv_${path.trim('/').replace('/', '-')}_${z}_${x}_$y"

    /** The step if it is in memory or on disk – no download. */
    fun peek(key: String): ByteArray? = memory.get(key) ?: read(key)?.also { memory.put(key, it) }

    fun has(key: String) = memory.get(key) != null || file(key)?.exists() == true

    /** The step [frame] of [composite] (one byte per cell of its grid), null if it cannot be had. */
    suspend fun grid(http: OkHttpClient, composite: RadarComposite, frame: RadarFrame): ByteArray? =
        get(composite.key(frame), composite.w * composite.h, downloads) { composite.fetch(http, frame) }
            ?: composite.stalePrefix(frame)?.let { newest(it) }

    /**
     * The cells [window] of the step [frame] of [composite]: cut from the whole step if it is here
     * (the radar loop's), else fetched alone – a few kB instead of the whole grid. Not kept: the
     * preview keeps its picture.
     */
    suspend fun window(http: OkHttpClient, composite: RadarComposite, frame: RadarFrame, window: GridWindow): ByteArray? {
        withContext(Dispatchers.IO) { peek(composite.key(frame)) }?.let { return composite.cut(it, window) }
        return small.withPermit { runCatching { composite.fetch(http, frame, window) }.getOrNull() }?.takeIf { it.size == window.size }
    }

    /** A RainViewer tile (512 × 512 bytes), null if it cannot be had; [preview]: in the small requests' lane. */
    suspend fun rvTile(http: OkHttpClient, host: String, path: String, z: Int, x: Int, y: Int, preview: Boolean = false): ByteArray? =
        get(rvKey(path, z, x, y), 512 * 512, if (preview) small else downloads) {
            RadarDecode.png(
                http, "$host$path/512/$z/$x/$y/2/1_1.png", 512, 512, RadarPalette.Source.RAINVIEWER,
                if (preview) Dispatchers.Default else RadarDecode.decoding,
            )
        }

    /** The newest stored step whose name begins with [prefix] (its suffix: the issue it came from). */
    private fun newest(prefix: String): ByteArray? {
        val f = dir?.listFiles { f -> f.name.startsWith(prefix) }?.maxByOrNull { it.name.substringAfterLast("_n").substringBefore('.').toLongOrNull() ?: 0L }
        return f?.let { read(it.name.removeSuffix(".nrd")) }
    }

    private suspend fun get(key: String, size: Int, lane: Semaphore, fetch: suspend () -> ByteArray?): ByteArray? {
        memory.get(key)?.let { return it }
        withContext(Dispatchers.IO) { read(key) }?.let { memory.put(key, it); return it }
        return inflight.get(key) {
            val data = lane.withPermit { runCatching { fetch() }.getOrNull() }
            data?.takeIf { it.size == size }?.also {
                memory.put(key, it)
                withContext(Dispatchers.IO) { write(key, it) }
            }
        }
    }

    // ---- disk -----------------------------------------------------------------------------

    private fun file(key: String) = dir?.let { File(it, "$key.nrd") }

    private fun read(key: String): ByteArray? = runCatching { file(key)?.takeIf { it.exists() }?.readBytes()?.let { unpack(it) } }.getOrNull()

    private fun write(key: String, data: ByteArray) {
        val f = file(key) ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(pack(data))
            tmp.renameTo(f)
        }
    }

    /** Header (magic, length) and the deflated bytes. */
    fun pack(data: ByteArray): ByteArray {
        val d = Deflater(6).apply { setInput(data); finish() }
        val out = ByteArrayOutputStream(data.size / 16 + 64)
        out.write(intBytes(MAGIC)); out.write(intBytes(data.size))
        val buf = ByteArray(64 * 1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    fun unpack(bytes: ByteArray): ByteArray? {
        if (bytes.size < 8 || readInt(bytes, 0) != MAGIC) return null
        val size = readInt(bytes, 4)
        val inf = Inflater().apply { setInput(bytes, 8, bytes.size - 8) }
        val out = ByteArray(size)
        var n = 0
        while (n < size && !inf.finished()) { val k = inf.inflate(out, n, size - n); if (k == 0 && inf.needsInput()) break; n += k }
        inf.end()
        return if (n == size) out else null
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun readInt(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    /**
     * Deletes what has expired: analyses older than the DWD archive, nowcast steps of an older
     * analysis (after a grace period), RainViewer steps after 3 hours – and the oldest files when
     * the store grows beyond its size.
     */
    fun prune(now: Long = System.currentTimeMillis(), latestIssue: Long? = null) {
        val files = dir?.listFiles()?.filter { it.name.endsWith(".nrd") } ?: return
        val keep = files.filter { f -> !expired(f.name, f.lastModified(), now, latestIssue).also { if (it) f.delete() } }
        var total = keep.sumOf { it.length() }
        if (total > MAX_BYTES) keep.sortedBy { it.lastModified() }.forEach { f -> if (total > MAX_BYTES) { total -= f.length(); f.delete() } }
    }

    /** Expiry rule for a stored file by its name (see [prune]); written at [modified]. */
    fun expired(name: String, modified: Long, now: Long, latestIssue: Long?): Boolean {
        val base = name.removeSuffix(".nrd")
        if (base.startsWith("rv_")) return now - modified > RV_KEEP_MS
        // a nowcast's issue against the newest one of its composite
        return RadarComposites.all.firstNotNullOfOrNull { it.expired(base, modified, now, latestIssue ?: RadarLatest.known(it)) } ?: true
    }
}
