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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.ImageSource
import dev.nimbus.weather.util.ActivityLog
import java.util.concurrent.ConcurrentHashMap

/**
 * The radar on the map is one picture of the view (a MapLibre image source), computed from the
 * [RadarStore]: per time step the view's frame ([RadarField.extract]), between two steps the
 * motion ([RadarMotion.motion]) – so playback moves the rain smoothly instead of jumping from step
 * to step. Panning within the picture's margin and zooming a little need nothing new; beyond
 * that the frames are cut again from the store (no download).
 */
class RadarPlayer(
    private val scope: CoroutineScope, private val http: OkHttpClient,
    /** Longest side of the picture area in field pixels – smaller on devices with less app memory ([fieldSideFor]). */
    private val maxSide: Int = 1024,
) {
    private var style: Style? = null
    private var timeline: RadarTimeline? = null
    @Volatile private var geo: FieldGeo? = null
    /** Frames of the current picture area by step ([RadarFrame.id]; they survive a refreshed time line). */
    private val frames = ConcurrentHashMap<String, ViewFrame>()
    private val flows = ConcurrentHashMap<String, Flow>()
    /** Per composite reaching into the picture: the pixels it covers. */
    private var covered: Map<RadarComposite, BooleanArray>? = null
    private var generation = 0
    private var downloader: Job? = null
    private var extractor: Job? = null
    /** Wakes the extractor: a step arrived in the store, or the position moved on. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    /** Store keys of the steps on the device (no need to ask the disk for every frame). */
    private val stored = ConcurrentHashMap.newKeySet<String>()
    /** Steps the DWD did not deliver this time: done for the progress (the motion bridges them). */
    private val failed = ConcurrentHashMap.newKeySet<String>()
    /** What each frame was built from (composites, RainViewer frames complete): a source come since builds it again. */
    private val parts = ConcurrentHashMap<String, Set<String>>()
    /** Steps a source beside the main one has come for since their frame was built. */
    private val dirty = ConcurrentHashMap.newKeySet<String>()
    /** The sources beside the area's own: asked with a short time limit, left out a while when they fail. */
    private val sideHttp = http.newBuilder().callTimeout(SIDE_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS).build()
    private val health = SourceHealth(onRest = { ActivityLog.note("radar source $it rests ${SourceHealth.PAUSE_MS / 60_000} min after ${SourceHealth.FAILS} failures") })
    /** When the loop of the current area began to load (the activity log's times count from here). */
    @Volatile private var loadingSince = 0L
    /** Longest and total time of the frames built for the area so far (activity log). */
    private val buildMs = java.util.concurrent.atomic.AtomicLong()
    private val buildMax = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var allBuiltNoted = false

    private val _loaded = MutableStateFlow(0)
    /** Steps of the time line in the store (downloaded) – the loading progress. */
    val loaded: StateFlow<Int> = _loaded
    private val _ready = MutableStateFlow(0)
    /** Grows with every frame ready to show – the screen checks playback then. */
    val ready: StateFlow<Int> = _ready
    private val _nowcastHere = MutableStateFlow(true)
    /** Whether a composite with a nowcast reaches into the picture area – else its future is empty. */
    val nowcastHere: StateFlow<Boolean> = _nowcastHere
    private val _nowcastEverywhere = MutableStateFlow(true)
    /** Whether the nowcast covers the whole picture area – else its forecast steps are grey beyond it. */
    val nowcastEverywhere: StateFlow<Boolean> = _nowcastEverywhere
    /** The picture area's pixels beyond every nowcast: grey in the forecast steps. */
    @Volatile private var noForecast: BooleanArray? = null
    private val _compositeHere = MutableStateFlow(true)
    /**
     * Whether a weather service's composite reaches into the picture area – else only RainViewer,
     * which keeps two hours (the longer look-back stays empty there).
     */
    val compositeHere: StateFlow<Boolean> = _compositeHere
    /** Smoothing for field pixels larger than screen pixels ([FrameBuilder.screenSmooth]). */
    @Volatile private var screenSmooth = 0

    /** Playback position: frame index plus the fraction to the next one. */
    @Volatile var position = 0f
        set(v) {
            field = v
            requestRender()
            // A long time line (archived day) holds a window of frames: it follows the position
            if (windowed && !(playing && timeline?.day == null) && kotlin.math.abs(v - windowAnchor) > WINDOW_SLIDE) wake.trySend(Unit)
        }

    /**
     * Set by the screen while the loop plays: a long live loop then prepares only the steps it
     * plays between (every 20–30 min) – the 5-minute steps around the position, which it would
     * not show, come when it is paused (for stepping through them).
     */
    @Volatile var playing = false
        set(v) { if (field != v) { field = v; wake.trySend(Unit) } }

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

    private fun key(tl: RadarTimeline, i: Int) = tl.frames[i].id
    private fun extracted(tl: RadarTimeline, i: Int) = frames.containsKey(key(tl, i))

    /** Frame [i] is ready to show (not only approximated between others). */
    fun isLoaded(i: Int): Boolean = timeline?.let { tl -> i in tl.frames.indices && extracted(tl, i) } == true

    /**
     * Can position [p] play – on a frame, or between two at most half an hour apart (wider gaps,
     * while the finer steps load, would only be blended, not moved)?
     */
    fun canShow(p: Float): Boolean {
        val tl = timeline ?: return false
        return Progressive.playable(p, tl.frames.size, { extracted(tl, it) }, { tl.frames[it].time })
    }

    fun setTimeline(tl: RadarTimeline) {
        timeline = tl
        if (stopped) return
        restartDownloader()
        restartExtractor()
    }

    /**
     * Not shown (the app left, the phone locked): downloading and cutting stop – a half-loaded
     * loop kept the phone busy behind the lock screen. What is in the store stays.
     */
    @Volatile private var stopped = false

    fun stop() {
        stopped = true
        downloader?.cancel(); downloader = null
        extractor?.cancel(); extractor = null
    }

    /** Shown again: on where it stopped (the steps in the store are not fetched again). */
    fun start() {
        if (!stopped) return
        stopped = false
        if (timeline == null) return
        restartDownloader()
        restartExtractor()
    }

    /**
     * The visible area changed (camera idle): a new picture area if the old one no longer serves.
     * [screenWidthPx]: the map's width on the screen (0: unknown).
     */
    fun setView(south: Double, north: Double, west: Double, east: Double, screenWidthPx: Int = 0) {
        val want = FieldGeo.forView(south, north, west, east, maxSide = maxSide)
        val g = geo
        val ratio = g?.let { want.pxM / it.pxM } ?: 0.0
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (g != null && inside && ratio in 0.6..1.6) return
        geo = want
        val screenPxM = if (screenWidthPx > 0) FieldGeo.R * Math.toRadians(east - west) / screenWidthPx else 0.0
        screenSmooth = FrameBuilder.screenSmooth(want.pxM, screenPxM)
        covered = null
        noForecast = null
        frames.clear()
        flows.clear()
        parts.clear()
        dirty.clear()
        // a composite loaded in windows of the area: its steps of the new area are others
        val windowed = RadarComposites.all.any { it.windowed && it.serves(want) && it.overlaps(want.west, want.east, want.south, want.north) }
        if (windowed) stored.clear()
        if (stopped) return
        restartExtractor()
        if (downloader == null || windowed) restartDownloader()
    }

    private fun stepMinutes(tl: RadarTimeline) =
        if (tl.frames.size < 2) 5 else ((tl.frames[1].time - tl.frames[0].time) / 60_000L).toInt()

    /** The steps a long live loop plays between (kept for good while windowed); none for a short one. */
    private fun liveKeep(tl: RadarTimeline): Set<Int> =
        if (tl.frames.size > MAX_ALL) Progressive.keepSteps(tl.frames.map { it.time }, tl.range.playMinutes) else emptySet()

    /**
     * Downloads the steps into the store, several at a time, independent of what is shown: the
     * live loop now, the forecast, then the past ([Progressive.liveOrder]); an archived day coarse
     * to fine ([Progressive.order]): every 2 hours, every hour, … over the whole day.
     */
    private fun restartDownloader() {
        val tl = timeline ?: return
        downloader?.cancel()
        // no picture area yet: nothing to download – the area starts it ([setView]); started
        // without one, the downloader passed every step by and ended, and the area started none
        if (geo == null) { downloader = null; return }
        failed.clear()
        loadingSince = android.os.SystemClock.elapsedRealtime()
        buildMs.set(0); buildMax.set(0); allBuiltNoted = false
        downloader = scope.launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                tl.frames.forEach { f -> geo?.let { g -> RadarPicture.composites(f, g).takeIf { it.isNotEmpty() && it.all { c -> RadarStore.hasArea(c, f, g) } } }?.let { stored += f.id } }
            }
            countStored(tl)
            wake.trySend(Unit)
            // live: now, the forecast, then the past; an archived day coarse to fine from the position
            val order = if (tl.day == null) Progressive.liveOrder(tl.frames.size, tl.nowIndex, liveKeep(tl))
            else Progressive.order(tl.frames.size, position.toInt().coerceIn(0, tl.frames.lastIndex), Progressive.strides(stepMinutes(tl)), stepMinutes(tl))
            // A fixed number of workers takes the steps strictly in this order (each download
            // on its own reordered them: the step needed next could end up far back in the queue)
            val queue = Channel<Int>(Channel.UNLIMITED)
            order.forEach { queue.trySend(it) }
            queue.close()
            // The sources beside the area's own (another composite, RainViewer) in a lane of their own
            val besides = Channel<Pair<Int, RadarComposite?>>(Channel.UNLIMITED)
            val rvAsked = HashSet<String>()
            val rvNeeded = geo?.let { RadarPicture.needsRainViewer(RadarPicture.coverage(it), it.w * it.h) } == true
            val beside = java.util.concurrent.ConcurrentHashMap<String, IntArray>()       // per source: answered, failed
            if (ActivityLog.enabled) geo?.let { g ->
                val inArea = tl.frames.lastOrNull { !it.isForecast }?.let { RadarPicture.composites(it, g) }.orEmpty()
                val own = mainOf(inArea, g)?.id ?: RAINVIEWER
                val others = inArea.map { it.id }.filter { it != own } + listOfNotNull(RAINVIEWER.takeIf { rvNeeded && own != RAINVIEWER })
                ActivityLog.note("radar loop: ${tl.frames.size} steps, own source $own, beside ${others.ifEmpty { listOf("none") }.joinToString(", ")}")
            }
            kotlinx.coroutines.coroutineScope {
                launch {
                    kotlinx.coroutines.coroutineScope {
                        repeat(DOWNLOAD_WORKERS) {
                            launch {
                                for (i in queue) {
                                    val f = tl.frames[i]
                                    val k = f.id
                                    val g = geo ?: continue
                                    val needed = RadarPicture.composites(f, g)
                                    val own = mainOf(needed, g)
                                    // RainViewer's frames of the step: in the main lane where it is the area's own source
                                    if (rvNeeded) for (path in RvSteps.paths(tl, i)) if (synchronized(rvAsked) { rvAsked.add(path) }) {
                                        if (own == null) { RadarPicture.mosaic(http, tl.rainViewerHost, path, g); dirtyFor(tl, path) } else besides.trySend(i to null)
                                    }
                                    if (k in stored) continue
                                    needed.filter { it !== own }.forEach { besides.trySend(i to it) }
                                    // the area's own source counts: the step plays with it, the others come on their own
                                    if (own == null) stored += k
                                    else if (RadarStore.forArea(http, own, f, g) != null) stored += k else failed += k
                                    wake.trySend(Unit)
                                    countStored(tl)
                                }
                            }
                        }
                    }
                    besides.close()
                    if (ActivityLog.enabled) ActivityLog.note(
                        "radar own source: ${tl.frames.count { it.id in stored }} of ${tl.frames.size} steps after ${since()} s, ${tl.frames.count { it.id in failed }} not delivered",
                    )
                }
                launch {
                    kotlinx.coroutines.coroutineScope {
                        repeat(SIDE_WORKERS) {
                            launch {
                                for ((i, c) in besides) {
                                    val f = tl.frames[i]
                                    val g = geo ?: continue
                                    val source = c?.id ?: RAINVIEWER
                                    if (!health.usable(source)) continue
                                    val ok = if (c == null) {
                                        RvSteps.paths(tl, i).all { path -> RadarPicture.mosaic(sideHttp, tl.rainViewerHost, path, g, side = true).complete.also { if (it) dirtyFor(tl, path) } }
                                    } else RadarStore.forArea(sideHttp, c, f, g, side = true) != null
                                    beside.getOrPut(source) { IntArray(2) }.let { n -> synchronized(n) { n[if (ok) 0 else 1]++ } }
                                    if (ok) { health.answered(source); dirty += f.id; wake.trySend(Unit) } else health.failed(source)
                                }
                            }
                        }
                    }
                    if (ActivityLog.enabled && beside.isNotEmpty()) ActivityLog.note(
                        "radar beside: " + beside.entries.joinToString(", ") { (k, n) -> "$k ${n[0]} answered, ${n[1]} failed" } + " after ${since()} s",
                    )
                }
            }
        }
    }

    /**
     * The area's own source among [needed]: the composite at the middle of the area (the place's,
     * as the radar opens there) – null where none covers it: RainViewer's then.
     */
    private fun mainOf(needed: List<RadarComposite>, g: FieldGeo): RadarComposite? =
        needed.firstOrNull { it.covers((g.south + g.north) / 2, (g.west + g.east) / 2) }

    /** Every step made of RainViewer's frame [path]: built again with it. */
    private fun dirtyFor(tl: RadarTimeline, path: String) {
        tl.frames.indices.filter { path in RvSteps.paths(tl, it) }.forEach { dirty += tl.frames[it].id }
        wake.trySend(Unit)
    }

    /** Seconds since the loop began to load (activity log). */
    private fun since() = "%.1f".format(java.util.Locale.ROOT, (android.os.SystemClock.elapsedRealtime() - loadingSince) / 1000.0)

    private fun countStored(tl: RadarTimeline) {
        val g = geo
        _loaded.value = tl.frames.count { f -> g == null || RadarPicture.composites(f, g).isEmpty() || f.id.let { it in stored || it in failed } }
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
            if (covered == null) covered = RadarPicture.coverage(g)
            val inArea = covered!!
            _nowcastHere.value = inArea.any { (c, mask) -> c.hasNowcast && mask.any { it } }
            val nowcasts = inArea.filterKeys { it.hasNowcast }.values
            noForecast = BooleanArray(g.w * g.h) { i -> nowcasts.none { it[i] } }.also { m -> _nowcastEverywhere.value = m.none { it } }
            _compositeHere.value = inArea.values.any { mask -> mask.any { it } }
            val builder = FrameBuilder(http, tl, g, inArea, screenSmooth)
            wake.trySend(Unit)
            val strides = Progressive.strides(stepMinutes(tl))
            for (w in wake) {
                val p = position.toInt().coerceIn(0, tl.frames.lastIndex)
                windowAnchor = p.toFloat()
                // A long live loop keeps a step every 20–30 minutes for good (it plays between
                // them); the 5-minute steps only around the position (for stepping with ‹ ›)
                val live = tl.day == null
                val keepSteps = if (windowed && live) Progressive.keepSteps(tl.frames.map { it.time }, tl.range.playMinutes) else emptySet()
                val ahead = if (live) LIVE_WINDOW_AHEAD else WINDOW_AHEAD
                val coarseOnly = windowed && live && playing
                val lo = if (coarseOnly) p else if (windowed) maxOf(0, p - WINDOW_BEHIND) else 0
                val hi = if (coarseOnly) p - 1 else if (windowed) minOf(tl.frames.lastIndex, p + ahead) else tl.frames.lastIndex
                if (windowed) {
                    val keep = ((lo..hi) + keepSteps).map { key(tl, it) }.toSet()
                    frames.keys.filter { it !in keep }.forEach { frames.remove(it) }
                    flows.keys.filter { k -> k.substringBefore('>') !in keep || k.substringAfter('>') !in keep }.forEach { flows.remove(it) }
                }
                // live: in the order they are loaded (now, the forecast, the past); an archived day
                // from the position on, coarse to fine
                val order = if (live) Progressive.liveOrder(tl.frames.size, tl.nowIndex, keepSteps)
                else Progressive.order(tl.frames.size, p, strides, stepMinutes(tl))
                for (i in order) {
                    if (gen != generation) return@launch
                    if (i !in lo..hi && i !in keepSteps) continue
                    val f = tl.frames[i]
                    val k = f.id
                    if (frames.containsKey(k) && k !in dirty) continue
                    val needed = RadarPicture.composites(f, g)
                    val layers = withContext(Dispatchers.IO) {
                        needed.mapNotNull { c -> RadarStore.peekArea(c, f, g)?.let { (codes, w) -> RadarLayer(c, codes, covered?.get(c), w) } }
                    }
                    // the area's own source not downloaded yet: a later pass (one it did not deliver: without it)
                    val own = mainOf(needed, g)
                    if (own != null && layers.none { it.composite === own } && k !in failed) continue
                    if (own == null && k !in stored) continue
                    val have = layers.map { it.composite.id }.toSet() + builder.rvPaths(i).filter { RadarPicture.rvAtHand(it, g) }
                    dirty.remove(k)
                    // come again for a source already in it: nothing new
                    if (frames.containsKey(k) && parts[k] == have) continue
                    frames.remove(k)
                    flows.keys.filter { it.substringBefore('>') == k || it.substringAfter('>') == k }.forEach { flows.remove(it) }
                    val t0 = System.nanoTime()
                    val vf = builder.build(i, layers)
                    parts[k] = have
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    buildMs.addAndGet(ms); buildMax.accumulateAndGet(ms, ::maxOf)
                    if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPlayer", "frame $i ${g.w}x${g.h} extract ${(System.nanoTime() - t0) / 1_000_000} ms")
                    if (gen != generation) return@launch
                    frames[k] = vf
                    _ready.value++
                    if (!allBuiltNoted && frames.size == tl.frames.size && ActivityLog.enabled) ActivityLog.note(
                        "radar frames: all ${tl.frames.size} built after ${since()} s, ${buildMs.get()} ms in all, longest ${buildMax.get()} ms",
                    ).also { allBuiltNoted = true }
                    requestRender()
                    // moved on meanwhile: plan the window around the new position
                    if (windowed && !coarseOnly && kotlin.math.abs(position - windowAnchor) > WINDOW_SLIDE) { wake.trySend(Unit); break }
                }
            }
        }
    }

    fun requestRender() { renders.trySend(Unit) }

    private suspend fun renderNow() {
        val tl = timeline ?: return
        val g = geo ?: return
        val p = position.coerceIn(0f, tl.frames.lastIndex.toFloat())
        // The nearest frames at hand before and after the position – while a day still loads
        // they may be hours apart
        val (ia, ib) = Progressive.bracket(p, tl.frames.size) { extracted(tl, it) } ?: return
        val fa = tl.frames[ia]
        val a = frames[fa.id] ?: return
        val fb = if (ib != ia) tl.frames[ib] else null
        val b = fb?.let { frames[it.id] }
        val tSpan = if (b != null) ((p - ia) / (ib - ia)).coerceIn(0f, 1f) else 0f
        // Moving the rain only across short gaps; wider ones (finer steps still loading) are
        // blended in place – a motion guessed over an hour shifted showers to wrong places
        val (move, t) = if (fb != null) Progressive.blend(fb.time - fa.time, tSpan) else (true to 0f)
        val flow = when {
            fb == null || b == null || t <= 0.002f -> null
            !move -> Flow.still()
            else -> flows.getOrPut(fa.id + ">" + fb.id) {
                // fast showers move up to ~150 km/h
                val hours = (fb.time - fa.time) / 3_600_000.0
                RadarMotion.motion(a, b, g.w, g.h, (150 * hours / g.pxKm).toFloat().coerceAtLeast(2f))
            }
        }
        // While the rain moves: half the resolution (four times as fast); standing still: all of it
        val step = if (flow != null) 2 else 1
        val ow = (g.w + step - 1) / step; val oh = (g.h + step - 1) / step
        val out = IntArray(ow * oh)
        val t0 = System.nanoTime()
        // a step of the forecast: grey where there is none (beyond the nowcast), not dry
        val forecast = tl.day == null && (fa.isForecast || fb?.isForecast == true && tSpan > 0f)
        RadarField.render(a, if (flow != null) b else null, flow, t, g.w, g.h, RadarPicture.snowAt(g, fa.time), out, step, if (forecast) noForecast else null)
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
        /** Downloads at a time for the sources beside the area's own. */
        private const val SIDE_WORKERS = 2
        /** Time limit for one answer of a source beside the area's own. */
        private const val SIDE_TIMEOUT_S = 8L
        private const val RAINVIEWER = "rainviewer"
        /** Up to this many frames are all kept (live loop); more (a day of 288) use a window. */
        private const val MAX_ALL = 60

        /**
         * The picture area's longest side for an app memory of [memoryClassMb] (what Android grants
         * the app, ActivityManager.memoryClass) on a [lowRam] device or not. A frame takes about
         * 2 bytes per field pixel and a long loop holds up to ~70 of them: at 1024 px that is
         * 70–100 MB – too much where the app may use 192 MB or less. The picture is scaled up on
         * the map; a smaller field is a little softer, nothing else.
         */
        fun fieldSideFor(memoryClassMb: Int, lowRam: Boolean): Int = when {
            lowRam || memoryClassMb < 160 -> 576
            memoryClassMb < 256 -> 768
            memoryClassMb < 384 -> 896
            else -> 1024
        }
        private const val WINDOW_BEHIND = 8
        private const val WINDOW_AHEAD = 36
        /** A long live loop: the 5-minute steps ahead of the position (the rest are the kept ones). */
        private const val LIVE_WINDOW_AHEAD = 12
        private const val WINDOW_SLIDE = 12f
    }
}
