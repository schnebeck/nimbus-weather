/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarScreen.kt
 * The rain radar: animated map with time slider and weather overlays.
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

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import android.os.Bundle
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nimbus.weather.BuildConfig
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.TimeFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Point
import java.util.TimeZone

const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
const val RADAR_ZOOM = 6.6
/** Time per step while playing: the live loop, and an archived day (5-minute steps, a day in ~55 s). */
private const val FRAME_MS = 450f
private const val ARCHIVE_STEP_MS = 180f
/** The live loop rests this long on its last frame before starting over. */
private const val LOOP_PAUSE_MS = 1400L
private const val MIN_FRAMES_TO_PLAY = 5
private const val STYLE_TIMEOUT_MS = 15_000L
/** How often the open live radar asks whether a newer DWD analysis is out. */
private const val RADAR_REFRESH_CHECK_MS = 60_000L
/** Buffering of an archived day: playback starts and resumes with this many steps ready ahead. */
private const val BUFFER_START = 6
private const val BUFFER_RESUME = 12
/**
 * MapLibre loads the tiles of every layer whose visibility is "visible" – even at opacity 0.
 * Layers that must not load yet are therefore switched to visibility "none".
 */
private fun RasterLayer.state(visible: Boolean, opacity: Float) = setProperties(
    PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
    PropertyFactory.rasterOpacity(opacity),
)

/**
 * Holds the MapLibre objects: satellite and warning layers and the location. The radar itself is
 * one picture between them, drawn by the [RadarPlayer].
 */
private class RadarMapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    var satellite = false
    var warnings = false
    /** First layer drawn above the radar (lines and names); overlays such as isolines go below it. */
    var anchor: String? = null

    /** Satellite and warning layers; the radar picture is inserted between them. */
    fun installBase(style: Style) {
        this.style = style
        // No animated property changes (MapLibre fades every change over 300 ms by default)
        style.transition = org.maplibre.android.style.layers.TransitionOptions(0, 0, false)
        // Satellite, radar and warnings between the areas and the lines: rivers, roads, borders
        // and names stay visible on top of the (opaque) radar colours
        val below = style.layers.firstOrNull { it is SymbolLayer || it is org.maplibre.android.style.layers.LineLayer }?.id
        anchor = below
        fun add(layer: RasterLayer) = if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        style.addSource(RasterSource("sat", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.SAT_LAYER, null)).apply { maxZoom = 9f }, 512).apply { prefetchZoomDelta = 0 })
        add(RasterLayer("sat", "sat").withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
        style.addSource(RasterSource("warn", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.WARN_LAYER, null)).apply {
            maxZoom = 10f
            setBounds(5.5f, 47.0f, 15.5f, 55.2f)
        }, 512).apply { prefetchZoomDelta = 0 })
        add(RasterLayer("warn", "warn").withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
    }

    fun addLocation(style: Style, place: Place) {
        style.addSource(GeoJsonSource("me", Point.fromLngLat(place.longitude, place.latitude)))
        style.addLayer(
            CircleLayer("me-halo", "me").withProperties(
                PropertyFactory.circleRadius(14f), PropertyFactory.circleColor("#3D8BFF"), PropertyFactory.circleOpacity(0.3f),
            ),
        )
        style.addLayer(
            CircleLayer("me", "me").withProperties(
                PropertyFactory.circleRadius(6f), PropertyFactory.circleColor("#3D8BFF"),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }

    fun setOverlays(sat: Boolean, warn: Boolean) {
        val s = style ?: return
        satellite = sat
        warnings = warn
        (s.getLayer("sat") as? RasterLayer)?.state(sat, if (sat) 0.75f else 0f)
        (s.getLayer("warn") as? RasterLayer)?.state(warn, if (warn) 0.55f else 0f)
    }
}

@Composable
fun RadarScreen(
    place: Place?, temperatureUnit: dev.nimbus.weather.data.model.TemperatureUnit,
    /** Start (local midnight) of a past day to show in full, from the look-back; null = live radar. */
    archiveDay: Long? = null,
    onBack: () -> Unit,
) {
    val archive = archiveDay != null
    val context = LocalContext.current
    val container = remember { (context.applicationContext as NimbusApp).container }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val controller = remember { RadarMapController() }
    val player = remember { RadarPlayer(scope, container.http) }
    var timeline by remember { mutableStateOf<RadarTimeline?>(null) }
    var error by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    /** Frames with complete tiles, for the progress hint while the loop loads. */
    var loadedFrames by remember { mutableIntStateOf(0) }
    /** Incremented by "load again". */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** Tile requests that failed or were answered from the cache while this screen is open. */
    val netStatus by RadarNetStatus.state.collectAsState()
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    /** Archived day: playback holds until enough frames ahead are loaded. */
    var buffering by remember { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(false) }
    var warnings by rememberSaveable { mutableStateOf(false) }
    var showTemp by rememberSaveable { mutableStateOf(false) }
    var showWind by rememberSaveable { mutableStateOf(false) }
    val overlays = remember { WeatherOverlays(temperatureUnit) }
    var gridCheck by remember { mutableIntStateOf(0) }
    var snowLegend by remember { mutableStateOf(true) }
    val tf = remember { TimeFormat(TimeZone.getDefault().id, DateFormat.is24HourFormat(context)) }
    val openedAt = remember { System.currentTimeMillis() }

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(false)
            .attributionEnabled(false).logoEnabled(false).compassEnabled(false)
        MapView(context, options).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            overlays.dispose()
        }
    }

    var range by rememberSaveable { mutableStateOf(HistoryRange.H2) }
    var styleReady by remember { mutableStateOf(false) }
    /** Incremented by "load again" while the map style never arrived. */
    var styleAttempt by remember { mutableIntStateOf(0) }

    // Without any map style (no network and nothing stored) the screen must not wait forever.
    LaunchedEffect(styleAttempt) {
        delay(STYLE_TIMEOUT_MS)
        if (!styleReady) error = true
    }
    LaunchedEffect(styleAttempt) {
        val styleBuilder = MapStyle.builder(container.mapHttp, context.resources.configuration.locales[0].language)
        if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "style JSON after ${System.currentTimeMillis() - openedAt} ms")
        mapView.getMapAsync { map ->
            controller.map = map
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            val target = place?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(51.1, 10.4)
            map.cameraPosition = CameraPosition.Builder().target(target).zoom(if (place != null) RADAR_ZOOM else 5.2).build()
            map.setMaxZoomPreference(10.0)
            map.setMinZoomPreference(3.0)
            map.setStyle(styleBuilder) { style ->
                controller.installBase(style)
                overlays.install(style, fieldBelow = "sat", linesBelow = controller.anchor)
                overlays.setVisible(showTemp, showWind)
                // The radar picture: one image between satellite and warnings
                player.install(style, below = "warn")
                // Panned far away: load the temperature/wind grid for the new area; a new radar
                // picture area once the view leaves the old one
                map.addOnCameraIdleListener {
                    gridCheck++
                    map.projection.visibleRegion.latLngBounds.let { b -> player.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast) }
                }
                place?.let { controller.addLocation(style, it) }
                controller.setOverlays(satellite, warnings)
                // "fully" = every tile of every visible layer is loaded (MapLibre render flag).
                styleReady = true
                if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "style ready after ${System.currentTimeMillis() - openedAt} ms")
            }
        }
    }

    // Progress and the start of playback: the player loads the steps from the store (one image
    // per step, kept on disk) from the shown frame outwards.
    LaunchedEffect(timeline, styleReady) {
        if (!styleReady || timeline == null) return@LaunchedEffect
        // the first picture area once the map has its size
        controller.map?.projection?.visibleRegion?.latLngBounds?.let { b -> player.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast) }
        launch { player.loaded.collect { loadedFrames = it } }
        player.ready.collect {
            val tl = timeline ?: return@collect
            // An archived day plays as soon as the first coarse steps bridge the start; the
            // finer ones arrive while it plays
            val canPlay = if (tl.day != null) player.canShow(minOf(frame + BUFFER_START, tl.frames.lastIndex).toFloat())
            else playable(tl, player).count() >= MIN_FRAMES_TO_PLAY || player.loaded.value >= tl.frames.size
            if (!ready && canPlay) {
                ready = true
                playing = true
                if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "loop starts after ${System.currentTimeMillis() - openedAt} ms")
            }
        }
    }

    // (Re)load the frames for the selected history range.
    LaunchedEffect(range, styleReady, reloadKey) {
        // The temperature grid is needed before the tiles: their colouring tells rain from snow.
        val lat = place?.latitude ?: 51.1
        val lon = place?.longitude ?: 10.4
        RadarNetStatus.reset()
        // Grid and time line in parallel; neither may hold up the other.
        val gridJob = async { WeatherGridStore.ensure(container.http, lat, lon, from = archiveDay) }
        // The DWD area mask before the first RainViewer tiles are coloured (from disk after the first time)
        val coverageJob = async { kotlinx.coroutines.withTimeoutOrNull(5_000L) { DwdCoverage.ensure(container.http) } }
        val tl = runCatching {
            if (archiveDay != null) RadarSources.dayTimeline(container.http, archiveDay)
            else RadarSources.timeline(container.http, range, force = reloadKey > 0)
        }.getOrNull()
        if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "timeline after ${System.currentTimeMillis() - openedAt} ms")
        val grid = gridJob.await()
        coverageJob.await()
        if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "grid after ${System.currentTimeMillis() - openedAt} ms")
        if (tl == null) { error = true; return@LaunchedEffect }
        error = false
        timeline = tl
        frame = tl.nowIndex
        if (styleReady) {
            grid?.let { overlays.setGrid(it) }
            player.position = tl.nowIndex.toFloat()
            player.setTimeline(tl)
            overlays.update(tl.frames[tl.nowIndex].time)
        }
    }
    LaunchedEffect(frame, timeline) {
        timeline?.let { tl -> overlays.update(tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time) }
    }
    // Live radar left open: once a minute (while the screen is visible) ask the DWD for its newest
    // analysis; when it starts a new step of the loop (10 minutes in the 2-hour range), the time
    // line is rebuilt – playback goes on, the frames already loaded come from the cache.
    LaunchedEffect(range, styleReady, archive) {
        if (archive || !styleReady) return@LaunchedEffect
        while (true) {
            delay(RADAR_REFRESH_CHECK_MS)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
            val tl = timeline ?: continue
            val latest = RadarSources.checkLatest(container.http) ?: continue
            val shown = tl.frames[tl.nowIndex].time
            if (latest / HistoryRange.STEP_MS > shown / HistoryRange.STEP_MS) {
                if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "new analysis ${RadarSources.isoTime(latest)}, refreshing")
                val atNow = frame == tl.nowIndex
                val fresh = runCatching { RadarSources.timeline(container.http, range, force = true) }.getOrNull() ?: continue
                timeline = fresh
                // Stay on "now" if the user was there; otherwise keep the same moment in time
                frame = if (atNow) fresh.nowIndex
                else fresh.frames.indexOfFirst { it.time >= tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time }.takeIf { it >= 0 } ?: fresh.nowIndex
                player.position = frame.toFloat()
                player.setTimeline(fresh)
            }
        }
    }
    // After panning or zooming: a grid that matches the zoom and covers the view. Finer grids are
    // only fetched while an overlay is shown (each costs 99 API calls); otherwise the coarse one.
    LaunchedEffect(gridCheck, showTemp, showWind, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val cam = controller.map?.cameraPosition ?: return@LaunchedEffect
        val c = cam.target ?: return@LaunchedEffect
        val step = if (showTemp || showWind) WeatherGrid.stepForZoom(cam.zoom) else WeatherGrid.STEP
        WeatherGridStore.ensure(container.http, c.latitude, c.longitude, step, from = archiveDay)?.let {
            overlays.setGrid(it)
            timeline?.let { tl -> overlays.update(tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time) }
        }
    }
    // Snow scale only where it can get cold enough (≤ 3 °C) in view during the shown time range.
    LaunchedEffect(gridCheck, timeline, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val tl = timeline ?: return@LaunchedEffect
        val b = controller.map?.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        snowLegend = RadarPalette.showSnowLegend(
            WeatherGridStore.minTemperature(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast, tl.frames.first().time, tl.frames.last().time),
        )
    }
    LaunchedEffect(showTemp, showWind, styleReady) {
        if (!styleReady) return@LaunchedEffect
        overlays.setVisible(showTemp && !archive, showWind && !archive)
    }
    // The visible map has priority over background preloading.
    DisposableEffect(Unit) {
        RadarPrefetcher.paused = true
        RadarPrefetcher.cancelRunning()
        onDispose { RadarPrefetcher.paused = false }
    }

    LaunchedEffect(satellite, warnings) { controller.setOverlays(satellite, warnings) }
    // Playback runs continuously: the position moves on with every display frame and the player
    // draws the rain moved between the steps – no jumping from step to step.
    LaunchedEffect(playing, timeline) {
        val tl = timeline ?: return@LaunchedEffect
        if (!playing) { buffering = false; player.position = frame.toFloat(); return@LaunchedEffect }
        val archived = tl.day != null
        // time per 5-minute step: the live ranges show [HistoryRange.playMinutes] per beat
        val stepMs = if (archived) ARCHIVE_STEP_MS else FRAME_MS * HistoryRange.STEP_MINUTES / tl.range.playMinutes
        var p = frame.toFloat()
        var last = withFrameNanos { it }
        var restAt = 0L
        if (archived) buffering = !player.canShow(minOf(frame + BUFFER_START, tl.frames.lastIndex).toFloat())
        while (playing) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000f
            last = now
            if (archived) {
                // Like a video player: hold until a stretch ahead is ready instead of stuttering
                val next = p.toInt() + 1
                if (next > tl.frames.lastIndex) { playing = false; break }
                if (buffering) {
                    if (player.canShow(minOf(p + BUFFER_RESUME, tl.frames.lastIndex.toFloat()))) buffering = false else continue
                }
                if (!player.canShow(next.toFloat())) { buffering = true; continue }
                p = minOf(p + dt / stepMs, next.toFloat())
            } else {
                // Only the loaded part of the loop; it grows while the rest loads
                val range = playable(tl, player).takeIf { !it.isEmpty() } ?: continue
                if (p >= range.last.toFloat()) {
                    if (restAt == 0L) restAt = now
                    if ((now - restAt) / 1_000_000L < LOOP_PAUSE_MS) continue
                    restAt = 0L
                    p = range.first.toFloat()
                } else p = minOf(p + dt / stepMs, range.last.toFloat())
                if (p < range.first.toFloat()) p = range.first.toFloat()
            }
            player.position = p
            if (p.toInt() != frame) frame = p.toInt()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF15181D))) {
        AndroidView({ mapView }, Modifier.fillMaxSize())

        // Top bar
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(R.string.radar_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                val dayLabel = archiveDay?.let { tf.dayMonth(it) }
                place?.let { Text(listOfNotNull(it.name, dayLabel).joinToString(" · "), fontSize = 13.sp, color = NimbusColors.Secondary) }
            }
            IconButton(onClick = {
                place?.let { p -> controller.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 7.5)) }
            }) { Icon(Icons.Rounded.MyLocation, stringResource(R.string.my_position), tint = Color.White) }
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).size(36.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Only the very first step (map style and time line) blocks the screen; the radar frames
        // load in the background with a small progress hint, the map stays usable.
        if (!error && (!styleReady || timeline == null)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.radar_loading), color = Color.White, fontSize = 14.sp)
            }
        }
        val total = timeline?.frames?.size ?: 0
        val stillLoading = timeline != null && styleReady &&
            buffering || !player.canShow(frame.toFloat()) || loadedFrames < total
        val trouble = netStatus.failed > 0 || netStatus.fromCache > 0
        if (error || stillLoading || trouble) {
            Column(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xCC0B1424)).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stillLoading && !error) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.radar_loading_frames, loadedFrames, total),
                            color = Color.White, fontSize = 13.sp,
                        )
                    }
                }
                val msg = when {
                    error -> R.string.radar_error
                    netStatus.failed > 0 && netStatus.fromCache > 0 -> R.string.radar_partly_cached
                    netStatus.failed > 0 -> R.string.radar_partly_missing
                    else -> null
                }
                if (msg != null) {
                    if (stillLoading && !error) Spacer(Modifier.height(6.dp))
                    Text(stringResource(msg), color = Color(0xFFFFD27A), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.radar_reload), color = Color(0xFF9CC8FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            error = false; ready = false; playing = false
                            if (styleReady) reloadKey++ else styleAttempt++
                        }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Bottom controls
        val tl = timeline
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05080D), Color(0xEE05080D))))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp)
                // Tablet: the controls stay a readable width in the middle
                .wrapContentWidth().widthIn(max = 760.dp),
        ) {
            if (tl != null) {
                val f = tl.frames[frame.coerceIn(0, tl.frames.lastIndex)]
                val outsideGermany = place != null && !WeatherRepository.isInDwdArea(place.latitude, place.longitude)
                if (!archive) Row(verticalAlignment = Alignment.CenterVertically) {
                    HistoryRange.entries.forEach { r ->
                        ToggleChip(stringResource(R.string.radar_range_hours, r.hours), range == r) {
                            if (range != r) { playing = false; ready = false; range = r }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (range != HistoryRange.H2 && outsideGermany) {
                        Text(stringResource(R.string.radar_history_germany_only), fontSize = 11.sp, color = Color(0xFFFFD27A), lineHeight = 13.sp)
                    }
                }
                if (!archive) Spacer(Modifier.height(8.dp))
                if (f.isForecast && outsideGermany) {
                    Text(
                        stringResource(R.string.radar_forecast_germany_only),
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp, color = Color(0xFFFFD27A),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (!playing && archive && frame >= tl.frames.lastIndex) frame = 0
                            playing = !playing
                        },
                        modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).size(44.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (playing) R.string.pause else R.string.play), tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            // Frames of another day (24 h history) get the weekday in front.
                            val timeLabel = if (archive || tf.isSameDay(f.time, System.currentTimeMillis())) tf.time(f.time)
                            else tf.weekdayShort(f.time) + "\u00A0" + tf.time(f.time)
                            Text(timeLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            val delta = ((f.time - tl.frames[tl.nowIndex].time) / 60_000L).toInt()
                            val label = when {
                                delta == 0 -> stringResource(R.string.now)
                                delta <= -120 -> stringResource(R.string.radar_hours_ago, -delta / 60)
                                delta < 0 -> stringResource(R.string.radar_minutes_ago, -delta)
                                else -> stringResource(R.string.radar_minutes_ahead, delta)
                            }
                            if (!archive) Text(label, fontSize = 13.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 2.dp))
                            if (f.isForecast) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.forecast).uppercase(),
                                    Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x40FFD27A)).padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD27A),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // Step by step: one 5-minute step back or forward (the slider is too
                            // fine for that with hundreds of steps)
                            fun stepTo(i: Int) { playing = false; frame = i.coerceIn(0, tl.frames.lastIndex); player.position = frame.toFloat() }
                            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.radar_step_back), frame > 0) { stepTo(frame - 1) }
                            Spacer(Modifier.width(6.dp))
                            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.radar_step_forward), frame < tl.frames.lastIndex) { stepTo(frame + 1) }
                        }
                        TimelineSlider(tl, frame) {
                            playing = false
                            frame = it
                            player.position = it.toFloat()
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Legend(showTemp && !archive, snowLegend, temperatureUnit)
                Spacer(Modifier.height(8.dp))
                // Temperature, wind, satellite and warnings are live layers – not offered for a past day.
                if (!archive) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ToggleChip(stringResource(R.string.overlay_temperature), showTemp) { showTemp = !showTemp }
                    Spacer(Modifier.width(6.dp))
                    ToggleChip(stringResource(R.string.overlay_wind), showWind) { showWind = !showWind }
                    Spacer(Modifier.width(6.dp))
                    ToggleChip(stringResource(R.string.satellite), satellite) { satellite = !satellite }
                    Spacer(Modifier.width(6.dp))
                    ToggleChip(stringResource(R.string.warnings), warnings) { warnings = !warnings }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.radar_attribution), fontSize = 9.sp, color = NimbusColors.Tertiary, maxLines = 2)
        }
    }
}

/**
 * The part of the live loop that can play: from the first to the last frame at hand, if "now"
 * lies in it (gaps are bridged by the motion between the frames around them).
 */
private fun playable(tl: RadarTimeline, player: RadarPlayer): IntRange =
    player.playableRange().takeIf { tl.nowIndex in it } ?: IntRange.EMPTY

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).size(32.dp),
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSlider(tl: RadarTimeline, frame: Int, onChange: (Int) -> Unit) {
    val n = tl.frames.size - 1
    Slider(
        value = frame.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..n.toFloat(),
        steps = n - 1,
        modifier = Modifier.height(36.dp),
        thumb = {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val y = size.height / 2
                val h = 4.dp.toPx()
                val pos = size.width * (state.value / n)
                if (tl.day != null) {
                    // Archived day: one track, a tick every 3 hours (longer every 6 hours)
                    drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
                    drawLine(Color(0xCCFFFFFF), Offset(0f, y), Offset(pos, y), h, StrokeCap.Round)
                    val perHour = (3_600_000L / RadarSources.ARCHIVE_STEP_MS).toInt()
                    for (i in 0..n step 3 * perHour) {
                        val x = size.width * i / n
                        val long = i % (6 * perHour) == 0
                        drawLine(Color(0x80FFFFFF), Offset(x, y + 6.dp.toPx()), Offset(x, y + (if (long) 11 else 9).dp.toPx()), 1.dp.toPx())
                    }
                    return@Canvas
                }
                val nowX = size.width * tl.nowIndex / n
                // past = white-ish, forecast = amber
                drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(nowX, y), h, StrokeCap.Round)
                drawLine(Color(0x66FFD27A), Offset(nowX, y), Offset(size.width, y), h, StrokeCap.Round)
                drawLine(Color(0xCCFFFFFF), Offset(0f, y), Offset(minOf(pos, nowX), y), h, StrokeCap.Round)
                if (pos > nowX) drawLine(Color(0xFFFFD27A), Offset(nowX, y), Offset(pos, y), h, StrokeCap.Round)
                // ticks at full hours (24 h: every 3 hours) and the "now" marker
                val tz = java.util.TimeZone.getDefault()
                val every = if (tl.range.hours >= 24) 3 else 1
                tl.frames.forEachIndexed { i, f ->
                    val local = f.time + tz.getOffset(f.time)
                    if (local % 3_600_000L != 0L || (local / 3_600_000L) % every != 0L) return@forEachIndexed
                    val x = size.width * i / n
                    drawLine(Color(0x80FFFFFF), Offset(x, y + 6.dp.toPx()), Offset(x, y + 9.dp.toPx()), 1.dp.toPx())
                }
                drawLine(Color.White, Offset(nowX, y - 7.dp.toPx()), Offset(nowX, y + 7.dp.toPx()), 1.5.dp.toPx())
            }
        },
    )
}

/** Rain (green → yellow → red) and, where it can snow, snow (turquoise → white → violet) scales, plus the temperature scale when shown. */
/** Land colour of the slate map style, under the legend bars. */
private val MapLand = Color(0xFF505E6F)

@Composable
private fun Legend(showTemp: Boolean, showSnow: Boolean, unit: dev.nimbus.weather.data.model.TemperatureUnit) {
    @Composable
    fun Bar(label: String, colors: List<Color>, modifier: Modifier) {
        Column(modifier) {
            Text(label, fontSize = 10.sp, color = NimbusColors.Secondary)
            Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                // On the map's land colour: the weakest, fading-in steps look exactly as on the map
                drawRect(MapLand)
                drawRect(Brush.horizontalGradient(colors))
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Bar(stringResource(R.string.legend_rain), RadarPalette.legendRain.map { Color(it) }, Modifier.weight(1f))
        if (showSnow) {
            Spacer(Modifier.width(10.dp))
            Bar(stringResource(R.string.legend_snow), RadarPalette.legendSnow.map { Color(it) }, Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.radar_light), fontSize = 10.sp, color = NimbusColors.Tertiary)
        Text(stringResource(R.string.radar_heavy), fontSize = 10.sp, color = NimbusColors.Tertiary)
    }
    if (showTemp) {
        Spacer(Modifier.height(4.dp))
        // Discrete bands like on the map (areas of equal temperature), in the display unit.
        val f = unit == dev.nimbus.weather.data.model.TemperatureUnit.FAHRENHEIT
        val lo = if (f) -4 else -20
        val hi = if (f) 104 else 40
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            val n = hi - lo
            val bw = size.width / n
            for (k in 0 until n) {
                drawRect(
                    Color(WeatherOverlays.bandColor(lo + k, unit)),
                    androidx.compose.ui.geometry.Offset(k * bw, 0f), androidx.compose.ui.geometry.Size(bw + 0.5f, size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..3).map { lo + (hi - lo) * it / 3 }.forEach {
                Text("$it°", fontSize = 10.sp, color = NimbusColors.Tertiary)
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Color(0xFF14305E) else Color.White,
    )
}
