/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarPreview.kt
 * Radar preview card: a stored base map per place plus a single small radar image on top.
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Rendering the whole map (roads, labels, radar) off-screen for every new radar frame takes 3 to
 * 12 seconds per place. The base map is therefore rendered once per place and size and kept on
 * disk (it never changes); the radar picture of exactly the preview area is drawn as in
 * the radar loop ([RadarPicture.still]: the composites, RainViewer beyond them), refreshed when a
 * new step is out. Both are shown from disk at once, so switching places shows the last picture
 * immediately – anywhere.
 */
object RadarPreview {
    const val ZOOM = 6.4
    private const val R = 6378137.0
    private const val MAX_FILE_AGE_MS = 30L * 24 * 3600_000L

    @Volatile var dir: File? = null

    data class Overlay(val time: Long, val bitmap: Bitmap?)

    private val overlays = java.util.concurrent.ConcurrentHashMap<String, Overlay>()
    /** One off-screen render at a time (each one holds a map renderer). */
    private val renderMutex = kotlinx.coroutines.sync.Mutex()
    /** The other places' pictures one after the other: the card shown never queues behind them. */
    private val prefetching = kotlinx.coroutines.sync.Mutex()
    /** A picture asked for by the card while the prefetching computes it: computed once. */
    private val computing = InFlight<String, Overlay?>()
    /** Size of the preview card as laid out; prefetching renders for this size. */
    val cardSizeFlow = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Int>?>(null)
    var cardSize: Pair<Int, Int>?
        get() = cardSizeFlow.value
        set(v) { if (v != null && v != cardSizeFlow.value) cardSizeFlow.value = v }

    /**
     * Prepares the preview of a place in the background (after its weather loaded), so switching
     * to it shows a current picture at once: the base map once, then just the radar picture.
     */
    suspend fun prefetch(
        context: Context, http: OkHttpClient, mapHttp: OkHttpClient, lat: Double, lon: Double, density: Float, lang: String,
        /** The base map costs some data once (vector tiles): only on Wi-Fi. */
        withBase: Boolean = RadarPrefetcher.isUnmetered(context),
    ) {
        val (w, h) = cardSize ?: return
        val key = key(lat, lon, w, h)
        if (withBase && storedBase(key, lang) == null) renderBase(context, mapHttp, lat, lon, w, h, density, lang)
        prefetching.withLock {
            val timeline = runCatching { RadarSources.timeline(http, anchor = RadarComposites.anchorFor(lat, lon)) }.getOrNull() ?: return
            timeline.frames.getOrNull(timeline.nowIndex)?.let { fetchOverlay(http, timeline, it, lat, lon, w, h) }
        }
    }

    private const val OVERLAY_SCALE = 2
    /** Bumped when the radar picture is drawn differently (9: from the composites' grids, 10: RainViewer at [RadarPicture.STILL_RV_ZOOM]). */
    private const val PICTURE_VERSION = 10

    /** Same place and size share the pictures; a moving location gets new ones every ~100 m. */
    fun key(lat: Double, lon: Double, wDp: Int, hDp: Int) =
        String.format(Locale.ROOT, "%.3f_%.3f_%dx%d", lat, lon, wDp, hDp)

    /**
     * The area of a [wDp] × [hDp] view at [ZOOM] around the place (MapLibre: 512 dp tiles), at
     * [OVERLAY_SCALE] pixels per dp: enough pixels per radar cell for the smoothing (at 1 px per dp
     * the cells stayed blocks, scaled up on the screen).
     */
    fun geo(lat: Double, lon: Double, wDp: Int, hDp: Int, zoom: Double = ZOOM): FieldGeo {
        val metersPerDp = 2 * PI * R / (512.0 * Math.pow(2.0, zoom))
        val x = R * Math.toRadians(lon)
        val y = R * ln(tan(PI / 4 + Math.toRadians(lat) / 2))
        val hw = wDp * metersPerDp / 2
        val hh = hDp * metersPerDp / 2
        return FieldGeo(x - hw, y - hh, x + hw, y + hh, wDp * OVERLAY_SCALE, hDp * OVERLAY_SCALE)
    }

    // v2: areas only; the lines and names are kept apart and drawn above the radar
    private fun baseFile(key: String, lang: String) = dir?.let { File(it, "base2_${key}_$lang.png") }
    private fun linesFile(key: String, lang: String) = dir?.let { File(it, "lines2_${key}_$lang.png") }

    /** Roads, borders and names of the place (transparent), drawn above the radar picture. */
    suspend fun storedLines(key: String, lang: String): Bitmap? = withContext(Dispatchers.IO) {
        linesFile(key, lang)?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }
    // The radar picture depends on the colour scale (setting): kept per scale
    private fun ok(key: String) = "${key}_c${RadarPalette.scheme.ordinal}_v${PICTURE_VERSION}_s$OVERLAY_SCALE"
    private fun overlayFile(key: String) = dir?.let { File(it, "radar_${ok(key)}.png") }
    private fun overlayTimeFile(key: String) = dir?.let { File(it, "radar_${ok(key)}.time") }

    suspend fun storedBase(key: String, lang: String): Bitmap? = withContext(Dispatchers.IO) {
        baseFile(key, lang)?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }

    /** Last radar picture of the place: from memory, else from disk. */
    suspend fun storedOverlay(key: String): Overlay? = overlays[ok(key)] ?: withContext(Dispatchers.IO) {
        val t = overlayTimeFile(key)?.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return@withContext null
        val f = overlayFile(key)
        // An empty picture (no precipitation) is stored as time only
        val bmp = f?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
        Overlay(t, bmp).also { overlays[ok(key)] = it }
    }

    /** Renders the base map (no radar) off-screen and stores it. Must be started on the main thread. */
    suspend fun renderBase(context: Context, mapHttp: OkHttpClient, lat: Double, lon: Double, wDp: Int, hDp: Int, density: Float, lang: String): Bitmap? {
        suspend fun snapshot(part: MapStyle.Part): Bitmap? {
            val style = MapStyle.builder(mapHttp, lang, part = part)
            return renderMutex.withLock { suspendCancellableCoroutine<Bitmap?> { cont ->
                val snap = RadarSnapshot.create(context, style, lat, lon, wDp, hDp, density)
                snap.start({ s -> if (cont.isActive) cont.resume(s.bitmap) }, { _ -> if (cont.isActive) cont.resume(null) })
                cont.invokeOnCancellation { snap.cancel() }
            } }
        }
        val bmp = snapshot(MapStyle.Part.AREAS) ?: return null
        val lines = snapshot(MapStyle.Part.LINES)
        withContext(Dispatchers.IO) {
            runCatching { lines?.let { l -> linesFile(key(lat, lon, wDp, hDp), lang)?.let { f -> f.parentFile?.mkdirs(); f.outputStream().use { l.compress(Bitmap.CompressFormat.PNG, 100, it) } } } }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val d = dir ?: return@runCatching
                d.mkdirs()
                d.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > MAX_FILE_AGE_MS }?.forEach { it.delete() }
                baseFile(key(lat, lon, wDp, hDp), lang)?.outputStream()?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return bmp
    }

    /**
     * The radar picture of step [frame] for the preview area ([RadarPicture.still]). Null on
     * errors; an overlay without bitmap means "no precipitation".
     */
    suspend fun fetchOverlay(http: OkHttpClient, tl: RadarTimeline, frame: RadarFrame, lat: Double, lon: Double, wDp: Int, hDp: Int): Overlay? {
        val key = key(lat, lon, wDp, hDp)
        overlays[ok(key)]?.takeIf { it.time == frame.time }?.let { return it }
        return computing.get("${ok(key)}_${frame.time}") { compute(http, tl, frame, key, geo(lat, lon, wDp, hDp)) }
    }

    private suspend fun compute(http: OkHttpClient, tl: RadarTimeline, frame: RadarFrame, key: String, g: FieldGeo): Overlay? {
        val started = System.currentTimeMillis()
        val picture = runCatching { RadarPicture.still(http, tl, frame, g) }.getOrNull() ?: return null
        // nothing falls: no picture to keep
        val bmp = picture.takeIf { p -> IntArray(p.width * p.height).also { p.getPixels(it, 0, p.width, 0, 0, p.width, p.height) }.any { it ushr 24 != 0 } }
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPreview", "radar picture ${System.currentTimeMillis() - started} ms, step ${RadarSources.isoTime(frame.time)}")
        val overlay = Overlay(frame.time, bmp)
        overlays[ok(key)] = overlay
        withContext(Dispatchers.IO) {
            runCatching {
                dir?.mkdirs()
                if (bmp != null) overlayFile(key)?.outputStream()?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                else overlayFile(key)?.delete()
                overlayTimeFile(key)?.writeText(frame.time.toString())
            }
        }
        return overlay
    }
}
