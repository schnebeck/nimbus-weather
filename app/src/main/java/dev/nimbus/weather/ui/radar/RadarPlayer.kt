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
    private var loader: Job? = null
    private val extracting = Semaphore(2)

    private val _loaded = MutableStateFlow(0)
    /** Frames of the time line ready to show. */
    val loaded: StateFlow<Int> = _loaded

    /** Playback position: frame index plus the fraction to the next one. */
    @Volatile var position = 0f
        set(v) {
            field = v
            requestRender()
            // A long time line (archived day) holds a window of frames: it follows the position
            if (windowed && kotlin.math.abs(v - windowAnchor) > WINDOW_SLIDE) restartLoader()
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

    fun isLoaded(i: Int): Boolean = timeline?.frames?.getOrNull(i)?.let { frames.containsKey(RadarStore.dwdKey(it)) } == true

    fun setTimeline(tl: RadarTimeline) {
        timeline = tl
        restartLoader()
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
        restartLoader()
    }

    private fun restartLoader() {
        val tl = timeline ?: return
        val g = geo ?: return
        loader?.cancel()
        val gen = ++generation
        countLoaded()
        loader = scope.launch(Dispatchers.Default) {
            if (inDwd == null) inDwd = coverage(g)
            // From the shown frame outwards, ahead first (playback runs forward)
            val p = position.toInt().coerceIn(0, tl.frames.lastIndex)
            windowAnchor = p.toFloat()
            val lo = if (windowed) maxOf(0, p - WINDOW_BEHIND) else 0
            val hi = if (windowed) minOf(tl.frames.lastIndex, p + WINDOW_AHEAD) else tl.frames.lastIndex
            if (windowed) {
                val keep = (lo..hi).map { RadarStore.dwdKey(tl.frames[it]) }.toSet()
                frames.keys.filter { it !in keep }.forEach { frames.remove(it) }
                flows.keys.filter { k -> k.substringBefore('>') !in keep }.forEach { flows.remove(it) }
                countLoaded()
            }
            val order = buildList {
                add(p)
                for (d in 1..tl.frames.size) {
                    if (p + d <= hi) add(p + d)
                    if (d <= 6 && p - d >= lo) add(p - d)
                }
                for (d in 7..tl.frames.size) if (p - d >= lo) add(p - d)
            }
            val jobs = order.map { i ->
                launch {
                    extracting.withPermit {
                        if (gen != generation) return@withPermit
                        loadFrame(tl, i, g, gen)
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }

    private suspend fun loadFrame(tl: RadarTimeline, i: Int, g: FieldGeo, gen: Int) {
        val f = tl.frames[i]
        val key = RadarStore.dwdKey(f)
        if (frames.containsKey(key)) return
        val dwd = if (overlapsDwd(g)) RadarStore.dwd(http, f) else null
        val rv = f.rainViewerPath?.takeIf { needsRainViewer(g) }?.let { mosaic(tl, it, g) }
        if (gen != generation) return
        if (dwd == null && rv == null && f.dwdTime != null && overlapsDwd(g)) return       // not loadable now
        val t0 = System.nanoTime()
        val vf = withContext(Dispatchers.Default) { RadarField.extract(g, dwd, rv, inDwd) }
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPlayer", "frame $i ${g.w}x${g.h} extract ${(System.nanoTime() - t0) / 1_000_000} ms")
        if (gen != generation) return
        frames[key] = vf
        countLoaded()
        val p = position.toInt()
        if (i == p || i == p + 1) requestRender()
    }

    private fun countLoaded() {
        val tl = timeline ?: return
        _loaded.value = tl.frames.count { frames.containsKey(RadarStore.dwdKey(it)) }
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
        val i = p.toInt()
        val t = p - i
        val fa = tl.frames[i]
        val a = frames[RadarStore.dwdKey(fa)] ?: return          // keeps the last picture until the frame is there
        val fb = tl.frames.getOrNull(i + 1)
        val b = if (t > 0.01f) fb?.let { frames[RadarStore.dwdKey(it)] } else null
        val flow = if (b != null && fb != null) {
            val k = RadarStore.dwdKey(fa) + ">" + RadarStore.dwdKey(fb)
            flows.getOrPut(k) {
                // fast showers move up to ~150 km/h
                val hours = (fb.time - fa.time) / 3_600_000.0
                RadarField.motion(a, b, g.w, g.h, (150 * hours / g.pxKm).toFloat().coerceAtLeast(2f))
            }
        } else null
        // While the rain moves: half the resolution (four times as fast); standing still: all of it
        val step = if (b != null) 2 else 1
        val ow = (g.w + step - 1) / step; val oh = (g.h + step - 1) / step
        val out = IntArray(ow * oh)
        val t0 = System.nanoTime()
        RadarField.render(a, b, flow, if (b != null) t else 0f, g.w, g.h, snowAt(g, fa.time), out, step)
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
        /** Up to this many frames are all kept (live loop); more (a day of 288) use a window. */
        private const val MAX_ALL = 60
        private const val WINDOW_BEHIND = 8
        private const val WINDOW_AHEAD = 36
        private const val WINDOW_SLIDE = 12f
        private const val O = 20037508.342789244
    }
}
