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
import dev.nimbus.weather.data.remote.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * The preview used to render the whole map (roads, labels, radar) off-screen for every new radar
 * frame – 3 to 12 seconds per place. Now the base map is rendered once per place and size and
 * kept on disk (it never changes); the radar is one WMS image of exactly the preview area (a few
 * kB, recoloured by [RadarTileInterceptor]), refreshed when a new analysis is out. Both are shown
 * from disk at once, so switching places shows the last picture immediately. DWD area only; other
 * places keep the full snapshot.
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
        if (!dev.nimbus.weather.data.repo.WeatherRepository.isInDwdArea(lat, lon)) return
        val key = key(lat, lon, w, h)
        if (withBase && storedBase(key, lang) == null) renderBase(context, mapHttp, lat, lon, w, h, density, lang)
        val timeline = runCatching { RadarSources.timeline(http) }.getOrNull() ?: return
        timeline.frames.getOrNull(timeline.nowIndex)?.let { fetchOverlay(mapHttp, it, lat, lon, w, h) }
    }

    /** Same place and size share the pictures; a moving location gets new ones every ~100 m. */
    fun key(lat: Double, lon: Double, wDp: Int, hDp: Int) =
        String.format(Locale.ROOT, "%.3f_%.3f_%dx%d", lat, lon, wDp, hDp)

    /** EPSG:3857 box of a [wDp] × [hDp] view at [ZOOM] around the place (MapLibre: 512 dp tiles). */
    fun bbox(lat: Double, lon: Double, wDp: Int, hDp: Int, zoom: Double = ZOOM): String {
        val metersPerDp = 2 * PI * R / (512.0 * Math.pow(2.0, zoom))
        val x = R * Math.toRadians(lon)
        val y = R * ln(tan(PI / 4 + Math.toRadians(lat) / 2))
        val hw = wDp * metersPerDp / 2
        val hh = hDp * metersPerDp / 2
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", x - hw, y - hh, x + hw, y + hh)
    }

    // v2: areas only; the lines and names are kept apart and drawn above the radar
    private fun baseFile(key: String, lang: String) = dir?.let { File(it, "base2_${key}_$lang.png") }
    private fun linesFile(key: String, lang: String) = dir?.let { File(it, "lines2_${key}_$lang.png") }

    /** Roads, borders and names of the place (transparent), drawn above the radar picture. */
    suspend fun storedLines(key: String, lang: String): Bitmap? = withContext(Dispatchers.IO) {
        linesFile(key, lang)?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }
    // The radar picture depends on the colour scale (setting): kept per scale
    private fun ok(key: String) = "${key}_c${RadarPalette.scheme.ordinal}_v${RadarSources.TILE_VERSION}"
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
     * Radar picture of the latest analysis for the preview area: one WMS request through the map
     * client (cached and recoloured like the tiles). Null on errors; an overlay without bitmap
     * means "no precipitation".
     */
    suspend fun fetchOverlay(mapHttp: OkHttpClient, frame: RadarFrame, lat: Double, lon: Double, wDp: Int, hDp: Int): Overlay? {
        val key = key(lat, lon, wDp, hDp)
        overlays[ok(key)]?.takeIf { it.time == frame.time }?.let { return it }
        val t = frame.dwdTime ?: return null
        val url = RadarSources.DWD_WMS + "?service=WMS&version=1.1.1&request=GetMap&layers=${RadarSources.DWD_LAYER}" +
            "&styles=&format=image/png&transparent=true&srs=EPSG:3857&bbox=${bbox(lat, lon, wDp, hDp)}" +
            "&width=$wDp&height=$hDp&time=$t&v=${RadarSources.TILE_VERSION}&c=${RadarPalette.scheme.ordinal}"
        val started = System.currentTimeMillis()
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                mapHttp.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                    when {
                        r.code == 204 -> Result.success<Bitmap?>(null)   // recoloured to "no precipitation"
                        !r.isSuccessful -> Result.failure(IllegalStateException("HTTP ${r.code}"))
                        else -> Result.success(r.body.bytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) })
                    }
                }
            }.getOrElse { Result.failure(it) }
        }.getOrElse { return null }
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPreview", "radar request ${System.currentTimeMillis() - started} ms, time $t")
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
