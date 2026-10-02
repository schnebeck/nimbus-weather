/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarPlayer.kt
 * Plays the radar loop as one picture on the map, moving the rain smoothly between the steps.
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

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.ImageSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The radar on the map is one picture of the view (a MapLibre image source), computed from the
 * [RadarStore]: per time step the view's frame ([RadarField.extract]), between two steps the
 * motion ([RadarField.motion]) – so playback moves the rain smoothly instead of jumping from step
 * to step. Panning within the picture's margin and zooming a little need nothing new; beyond
 * that the frames are cut again from the store (no download).
 */
class RadarPlayer(private val scope: CoroutineScope, private val http: OkHttpClient) {
    private var style: Style? = null
    private var timeline: RadarTimeline? = null
    @Volatile private var geo: FieldGeo? = null
    /** Frames of the current picture area by store key (survive a refreshed time line). */
    private val frames = ConcurrentHashMap<String, ViewFrame>()
    private val flows = ConcurrentHashMap<String, Flow>()
    private var inDwd: BooleanArray? = null
    private var generation = 0
    private var downloader: Job? = null
    private var extractor: Job? = null
    /** Wakes the extractor: a step arrived in the store, or the position moved on. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    /** Store keys of the steps on the device (no need to ask the disk for every frame). */
    private val stored = ConcurrentHashMap.newKeySet<String>()
    /** Steps the DWD did not deliver this time: done for the progress (the motion bridges them). */
    private val failed = ConcurrentHashMap.newKeySet<String>()

    private val _loaded = MutableStateFlow(0)
    /** Steps of the time line in the store (downloaded) – the loading progress. */
    val loaded: StateFlow<Int> = _loaded
    private val _ready = MutableStateFlow(0)
    /** Grows with every frame ready to show – the screen checks playback then. */
    val ready: StateFlow<Int> = _ready

    /** Playback position: frame index plus the fraction to the next one. */
    @Volatile var position = 0f
        set(v) {
            field = v
            requestRender()
            // A long time line (archived day) holds a window of frames: it follows the position
            if (windowed && kotlin.math.abs(v - windowAnchor) > WINDOW_SLIDE) wake.trySend(Unit)
        }

    /** Long time lines keep only [WINDOW_BEHIND] … [WINDOW_AHEAD] frames around the position. */
    private val windowed get() = (timeline?.frames?.size ?: 0) > MAX_ALL
    @Volatile private var windowAnchor = 0f

    private val renders = Channel<Unit>(Channel.CONFLATED)
    /** Two buffers per resolution (full / half), drawn alternately. */
    private var bitmaps = arrayOfNulls<Bitmap>(4)
    private var flip = 0
    private var shownGeo: FieldGeo? = null
    private var renderCount = 0

    fun install(style: Style, below: String) {
        this.style = style
        val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        style.addSource(ImageSource(SOURCE, LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0)), empty))
        style.addLayerBelow(
            RasterLayer(SOURCE, SOURCE).withProperties(
                PropertyFactory.rasterOpacity(1f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.rasterResampling("linear"),
            ),
            below,
        )
        scope.launch(Dispatchers.Default) { for (r in renders) renderNow() }
    }

    private fun key(tl: RadarTimeline, i: Int) = RadarStore.dwdKey(tl.frames[i])
    private fun extracted(tl: RadarTimeline, i: Int) = frames.containsKey(key(tl, i))

    /** Frame [i] is ready to show (not only approximated between others). */
    fun isLoaded(i: Int): Boolean = timeline?.let { tl -> i in tl.frames.indices && extracted(tl, i) } == true

    /** Can position [p] be shown – a frame at or before it and one after it (or exactly on one)? */
    fun canShow(p: Float): Boolean {
        val tl = timeline ?: return false
        val (a, b) = Progressive.bracket(p, tl.frames.size) { extracted(tl, it) } ?: return false
        return b > a || kotlin.math.abs(p - a) < 1e-3f
    }

    /** From the first to the last frame ready: the part of the live loop that can play. */
    fun playableRange(): IntRange {
        val tl = timeline ?: return IntRange.EMPTY
        val have = tl.frames.indices.filter { extracted(tl, it) }
        return if (have.isEmpty()) IntRange.EMPTY else have.first()..have.last()
    }

    fun setTimeline(tl: RadarTimeline) {
        timeline = tl
        restartDownloader()
        restartExtractor()
    }

    /** The visible area changed (camera idle): a new picture area if the old one no longer serves. */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val want = FieldGeo.forView(south, north, west, east)
        val g = geo
        val ratio = g?.let { want.pxM / it.pxM } ?: 0.0
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (g != null && inside && ratio in 0.6..1.6) return
        geo = want
        inDwd = null
        frames.clear()
        flows.clear()
        restartExtractor()
        if (downloader == null) restartDownloader()
    }

    private fun stepMinutes(tl: RadarTimeline) =
        if (tl.frames.size < 2) 5 else ((tl.frames[1].time - tl.frames[0].time) / 60_000L).toInt()

    /**
     * Downloads the steps into the store, coarse to fine ([Progressive]): every 2 hours, every
     * hour, … – over the whole time line, independent of what is shown, several at a time.
     */
    private fun restartDownloader() {
        val tl = timeline ?: return
        downloader?.cancel()
        failed.clear()
        downloader = scope.launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) { tl.frames.forEach { f -> RadarStore.dwdKey(f).let { k -> if (RadarStore.has(k)) stored += k } } }
            countStored(tl)
            wake.trySend(Unit)
            val order = Progressive.order(tl.frames.size, position.toInt().coerceIn(0, tl.frames.lastIndex), Progressive.strides(stepMinutes(tl)))
            // A fixed number of workers takes the steps strictly in this order (each download
            // on its own reordered them: the step needed next could end up far back in the queue)
            val queue = Channel<Int>(Channel.UNLIMITED)
            order.forEach { queue.trySend(it) }
            queue.close()
            kotlinx.coroutines.coroutineScope {
                repeat(DOWNLOAD_WORKERS) {
                    launch {
                        for (i in queue) {
                            val f = tl.frames[i]
                            val k = RadarStore.dwdKey(f)
                            if (f.dwdTime == null || k in stored) continue
                            if (geo?.let { overlapsDwd(it) } == false) continue
                            if (RadarStore.dwd(http, f) != null) {
                                stored += k
                                wake.trySend(Unit)
                            } else failed += k
                            countStored(tl)
                        }
                    }
                }
            }
        }
    }

    private fun countStored(tl: RadarTimeline) {
        _loaded.value = tl.frames.count { f -> f.dwdTime == null || RadarStore.dwdKey(f).let { it in stored || it in failed } }
    }

    /**
     * Cuts the frames of the picture area from the store – whatever is there, coarse levels
     * first, in the window around the position for long time lines; woken by every new step.
     */
    private fun restartExtractor() {
        val tl = timeline ?: return
        val g = geo ?: return
        extractor?.cancel()
        val gen = ++generation
        extractor = scope.launch(Dispatchers.Default) {
            if (inDwd == null) inDwd = coverage(g)
            wake.trySend(Unit)
            val strides = Progressive.strides(stepMinutes(tl))
            for (w in wake) {
                val p = position.toInt().coerceIn(0, tl.frames.lastIndex)
                windowAnchor = p.toFloat()
                val lo = if (windowed) maxOf(0, p - WINDOW_BEHIND) else 0
                val hi = if (windowed) minOf(tl.frames.lastIndex, p + WINDOW_AHEAD) else tl.frames.lastIndex
                if (windowed) {
                    val keep = (lo..hi).map { key(tl, it) }.toSet()
                    frames.keys.filter { it !in keep }.forEach { frames.remove(it) }
                    flows.keys.filter { k -> k.substringBefore('>') !in keep || k.substringAfter('>') !in keep }.forEach { flows.remove(it) }
                }
                for (i in Progressive.order(tl.frames.size, p, strides)) {
                    if (gen != generation) return@launch
                    if (i !in lo..hi) continue
                    val f = tl.frames[i]
                    val k = RadarStore.dwdKey(f)
                    if (frames.containsKey(k)) continue
                    val needDwd = f.dwdTime != null && overlapsDwd(g)
                    val dwd = if (needDwd) withContext(Dispatchers.IO) { if (k in stored || RadarStore.has(k)) RadarStore.peek(k) else null } else null
                    if (needDwd && dwd == null) continue                 // not downloaded yet: a later pass
                    val rv = f.rainViewerPath?.takeIf { needsRainViewer(g) }?.let { mosaic(tl, it, g) }
                    if (!needDwd && rv == null) continue
                    val t0 = System.nanoTime()
                    val vf = RadarField.extract(g, dwd, rv, inDwd)
                    if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPlayer", "frame $i ${g.w}x${g.h} extract ${(System.nanoTime() - t0) / 1_000_000} ms")
                    if (gen != generation) return@launch
                    frames[k] = vf
                    _ready.value++
                    requestRender()
                    // moved on meanwhile: plan the window around the new position
                    if (windowed && kotlin.math.abs(position - windowAnchor) > WINDOW_SLIDE) { wake.trySend(Unit); break }
                }
            }
        }
    }

    private suspend fun mosaic(tl: RadarTimeline, path: String, g: FieldGeo): RvMosaic {
        // RainViewer zoom whose pixels match the picture's, at most 7 (their finest)
        val z = (ln(2 * Math.PI * FieldGeo.R / (512 * g.pxM)) / ln(2.0)).roundToInt().coerceIn(3, 7)
        val size = 2 * O / (1 shl z)
        val x0 = floor((g.minX + O) / size).toInt(); val x1 = floor((g.maxX + O) / size).toInt()
        val y0 = floor((O - g.maxY) / size).toInt(); val y1 = floor((O - g.minY) / size).toInt()
        val tiles = HashMap<Long, ByteArray>()
        for (y in y0..y1) for (x in x0..x1) {
            if (x !in 0 until (1 shl z) || y !in 0 until (1 shl z)) continue
            RadarStore.rvTile(http, tl.rainViewerHost, path, z, x, y)?.let { tiles[RvMosaic.key(x, y)] = it }
        }
        return RvMosaic(z, tiles)
    }

    private fun overlapsDwd(g: FieldGeo) = g.east > DwdGrid.LON0 && g.west < DwdGrid.LON1 && g.north > DwdGrid.LAT0 && g.south < DwdGrid.LAT1

    /** Does the picture reach beyond the DWD radar area (where RainViewer fills in)? */
    private fun needsRainViewer(g: FieldGeo): Boolean = inDwd?.any { !it } ?: true

    /** Field pixels inside the DWD radar area; without the area mask: inside the DWD grid. */
    private fun coverage(g: FieldGeo): BooleanArray {
        val out = BooleanArray(g.w * g.h)
        val lat = DoubleArray(g.h) { g.lat(g.my(it.toDouble())) }
        val lon = DoubleArray(g.w) { g.lon(g.mx(it.toDouble())) }
        val mask = DwdCoverage.ready
        for (y in 0 until g.h) for (x in 0 until g.w) {
            out[y * g.w + x] = if (mask) DwdCoverage.covers(lat[y], lon[x])
            else lat[y] in DwdGrid.LAT0..DwdGrid.LAT1 && lon[x] in DwdGrid.LON0..DwdGrid.LON1
        }
        return out
    }

    fun requestRender() { renders.trySend(Unit) }

    /** Snow share from the 2 m temperature for every pixel, per hour of the grid. */
    private val snowCache = HashMap<String, FloatArray?>()

    private fun snowAt(g: FieldGeo, time: Long): FloatArray? {
        val grid = WeatherGridStore.gridOverlapping(TileGeo(g.minX, g.minY, g.maxX, g.maxY), time) ?: return null
        val hour = grid.hourIndex(time)
        val key = "${System.identityHashCode(grid)}_${hour}_${g.minX}_${g.maxY}_${g.w}"
        return snowCache.getOrPut(key) {
            if (snowCache.size > 24) snowCache.clear()
            val field = grid.temp[hour]
            val lat = DoubleArray(g.h) { g.lat(g.my(it.toDouble())) }
            val lon = DoubleArray(g.w) { g.lon(g.mx(it.toDouble())) }
            // the grid is coarse: sample every 4th pixel and spread, fine enough for rain/snow
            val out = FloatArray(g.w * g.h)
            for (y in 0 until g.h step 4) for (x in 0 until g.w step 4) {
                val s = grid.sampleNear(field, lat[y], lon[x])?.let { RadarPalette.snowFraction(it) } ?: 0f
                for (yy in y until minOf(y + 4, g.h)) for (xx in x until minOf(x + 4, g.w)) out[yy * g.w + xx] = s
            }
            out.takeIf { a -> a.any { it > 0f } }
        }
    }

    private suspend fun renderNow() {
        val tl = timeline ?: return
        val g = geo ?: return
        val p = position.coerceIn(0f, tl.frames.lastIndex.toFloat())
        // The nearest frames at hand before and after the position – while a day still loads
        // they may be hours apart; the motion between them fills the gap
        val (ia, ib) = Progressive.bracket(p, tl.frames.size) { extracted(tl, it) } ?: return
        val fa = tl.frames[ia]
        val a = frames[RadarStore.dwdKey(fa)] ?: return
        val fb = if (ib != ia) tl.frames[ib] else null
        val b = fb?.let { frames[RadarStore.dwdKey(it)] }
        val t = if (b != null) ((p - ia) / (ib - ia)).coerceIn(0f, 1f) else 0f
        val flow = if (b != null && fb != null && t > 0.002f) {
            val k = RadarStore.dwdKey(fa) + ">" + RadarStore.dwdKey(fb)
            flows.getOrPut(k) {
                // fast showers move up to ~150 km/h
                val hours = (fb.time - fa.time) / 3_600_000.0
                RadarField.motion(a, b, g.w, g.h, (150 * hours / g.pxKm).toFloat().coerceAtLeast(2f))
            }
        } else null
        // While the rain moves: half the resolution (four times as fast); standing still: all of it
        val step = if (flow != null) 2 else 1
        val ow = (g.w + step - 1) / step; val oh = (g.h + step - 1) / step
        val out = IntArray(ow * oh)
        val t0 = System.nanoTime()
        RadarField.render(a, if (flow != null) b else null, flow, t, g.w, g.h, snowAt(g, fa.time), out, step)
        if (dev.nimbus.weather.BuildConfig.DEBUG && renderCount++ % 30 == 0) android.util.Log.d("NimbusPlayer", "render ${ow}x$oh ${(System.nanoTime() - t0) / 1_000_000} ms")
        val slot = flip * 2 + step - 1
        val bmp = bitmaps[slot]?.takeIf { it.width == ow && it.height == oh }
            ?: Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888).also { bitmaps[slot] = it }
        bmp.setPixels(out, 0, ow, 0, 0, ow, oh)
        flip = 1 - flip
        withContext(Dispatchers.Main) {
            val src = style?.getSource(SOURCE) as? ImageSource ?: return@withContext
            if (shownGeo !== g) {
                src.setCoordinates(LatLngQuad(LatLng(g.north, g.west), LatLng(g.north, g.east), LatLng(g.south, g.east), LatLng(g.south, g.west)))
                shownGeo = g
            }
            src.setImage(bmp)
        }
    }

    companion object {
        const val SOURCE = "radar-picture"
        /** Parallel downloads of time steps (one image of all of Germany each, ~200 kB). */
        private const val DOWNLOAD_WORKERS = 6
        /** Up to this many frames are all kept (live loop); more (a day of 288) use a window. */
        private const val MAX_ALL = 60
        private const val WINDOW_BEHIND = 8
        private const val WINDOW_AHEAD = 36
        private const val WINDOW_SLIDE = 12f
        private const val O = 20037508.342789244
    }
}
