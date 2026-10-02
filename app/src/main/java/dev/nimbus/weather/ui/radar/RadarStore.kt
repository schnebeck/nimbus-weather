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

import android.graphics.BitmapFactory
import android.util.LruCache
import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.remote.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The radar loop no longer comes from hundreds of map tiles per view. Every time step is loaded
 * once – the whole DWD composite in one image, RainViewer (Europe) as the few tiles around the
 * view – decoded to reflectivity (one byte per cell) and stored compressed (about 60 kB per DWD
 * step). The radar screen cuts, smooths, colours and animates every view from here, panning and
 * zooming need no network, and an archived day plays from the disk.
 *
 * Each step is stored whole: a difference to the step before (plain, or after the motion) came
 * out larger than the step itself – the rain moves, so most of its edges change.
 *
 * Lifetime: analyses as long as the DWD keeps them (about 3½ days), nowcast steps until the next
 * analysis replaces them, RainViewer steps 3 hours.
 */
object RadarStore {
    @Volatile var dir: File? = null
    private const val MAGIC = 0x4E524431          // "NRD1"
    private const val ANALYSIS_KEEP_MS = 4L * 24 * 3_600_000L
    private const val NOWCAST_KEEP_MS = 30 * 60_000L
    private const val RV_KEEP_MS = 3L * 3_600_000L
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** Decoded steps in memory (DWD grid 1.8 MB each, RainViewer tiles 256 kB). */
    private val memory by lazy {
        object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    }
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<ByteArray?>>()
    /** DWD answers one big image at a time quickly; a few in parallel keep the line busy. */
    private val downloads = Semaphore(4)

    /** Store name of a DWD step; nowcast steps carry the analysis they were computed from. */
    fun dwdKey(frame: RadarFrame, issue: Long? = RadarSources.latestAnalysis): String =
        if (frame.isForecast) "dwd_${frame.time / 60_000L}_n${(issue ?: 0L) / 60_000L}" else "dwd_${frame.time / 60_000L}"

    fun rvKey(path: String, z: Int, x: Int, y: Int) = "rv_${path.trim('/').replace('/', '-')}_${z}_${x}_$y"

    /** The step if it is in memory or on disk – no download. */
    fun peek(key: String): ByteArray? = memory.get(key) ?: read(key)?.also { memory.put(key, it) }

    fun has(key: String) = memory.get(key) != null || file(key)?.exists() == true

    /** The DWD composite of [frame] (one byte per cell of [DwdGrid]), null if it cannot be had. */
    suspend fun dwd(http: OkHttpClient, frame: RadarFrame): ByteArray? {
        val t = frame.dwdTime ?: return null
        val key = dwdKey(frame)
        return get(key, DwdGrid.W * DwdGrid.H) {
            val url = RadarSources.DWD_WMS + "?service=WMS&version=1.1.1&request=GetMap&layers=${RadarSources.DWD_LAYER}" +
                "&styles=&format=image/png&transparent=true&srs=EPSG:4326" +
                "&bbox=${DwdGrid.LON0},${"%.2f".format(java.util.Locale.ROOT, DwdGrid.LAT0)},${"%.2f".format(java.util.Locale.ROOT, DwdGrid.LON1)},${DwdGrid.LAT1}" +
                "&width=${DwdGrid.W}&height=${DwdGrid.H}&time=$t"
            download(http, url, DwdGrid.W, DwdGrid.H, RadarPalette.Source.DWD)
        } ?: if (frame.isForecast) newestNowcast(frame) else null
    }

    /** A RainViewer tile (512 × 512 bytes), null if it cannot be had. */
    suspend fun rvTile(http: OkHttpClient, host: String, path: String, z: Int, x: Int, y: Int): ByteArray? =
        get(rvKey(path, z, x, y), 512 * 512) {
            download(http, "$host$path/512/$z/$x/$y/2/1_1.png", 512, 512, RadarPalette.Source.RAINVIEWER)
        }

    /** Offline: a nowcast step from an earlier analysis is better than none. */
    private fun newestNowcast(frame: RadarFrame): ByteArray? {
        val prefix = "dwd_${frame.time / 60_000L}_n"
        val f = dir?.listFiles { f -> f.name.startsWith(prefix) }?.maxByOrNull { it.name.substringAfterLast("_n").substringBefore('.').toLongOrNull() ?: 0L }
        return f?.let { read(it.name.removeSuffix(".nrd")) }
    }

    private suspend fun get(key: String, size: Int, fetch: suspend () -> ByteArray?): ByteArray? {
        memory.get(key)?.let { return it }
        withContext(Dispatchers.IO) { read(key) }?.let { memory.put(key, it); return it }
        val mine = CompletableDeferred<ByteArray?>()
        val running = inflight.putIfAbsent(key, mine)
        if (running != null) return running.await()
        return try {
            val data = downloads.withPermit { runCatching { fetch() }.getOrNull() }
            if (data != null && data.size == size) {
                memory.put(key, data)
                withContext(Dispatchers.IO) { write(key, data) }
                mine.complete(data); data
            } else { mine.complete(null); null }
        } finally {
            inflight.remove(key)
        }
    }

    private suspend fun download(http: OkHttpClient, url: String, w: Int, h: Int, source: RadarPalette.Source): ByteArray? {
        val bytes = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).await().use { r ->
                if (!r.isSuccessful || r.header("Content-Type")?.startsWith("image/png") != true) null else r.body.bytes()
            }
        } ?: return null
        return withContext(Dispatchers.Default) {
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
            if (bmp.width != w || bmp.height != h) { bmp.recycle(); return@withContext null }
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            bmp.recycle()
            codes(px, source)
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
    fun prune(now: Long = System.currentTimeMillis(), latestIssue: Long? = RadarSources.latestAnalysis) {
        val files = dir?.listFiles()?.filter { it.name.endsWith(".nrd") } ?: return
        val keep = files.filter { f -> !expired(f.name, f.lastModified(), now, latestIssue).also { if (it) f.delete() } }
        var total = keep.sumOf { it.length() }
        if (total > MAX_BYTES) keep.sortedBy { it.lastModified() }.forEach { f -> if (total > MAX_BYTES) { total -= f.length(); f.delete() } }
    }

    /** Expiry rule for a stored file by its name (see [prune]); written at [modified]. */
    fun expired(name: String, modified: Long, now: Long, latestIssue: Long?): Boolean {
        val base = name.removeSuffix(".nrd")
        return when {
            base.startsWith("rv_") -> now - modified > RV_KEEP_MS
            base.startsWith("dwd_") && "_n" in base -> {
                val issue = base.substringAfter("_n").toLongOrNull()?.times(60_000L) ?: return true
                val time = base.removePrefix("dwd_").substringBefore("_n").toLongOrNull()?.times(60_000L) ?: return true
                time < now - NOWCAST_KEEP_MS ||
                    latestIssue != null && issue < latestIssue && now - modified > NOWCAST_KEEP_MS
            }
            base.startsWith("dwd_") -> {
                val time = base.removePrefix("dwd_").toLongOrNull()?.times(60_000L) ?: return true
                now - time > ANALYSIS_KEEP_MS
            }
            else -> true
        }
    }
}
