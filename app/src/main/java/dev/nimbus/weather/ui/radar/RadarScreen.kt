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

import dev.nimbus.weather.ui.components.LaunchedWhileShown
import dev.nimbus.weather.ui.components.statusBarsStable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import android.os.Bundle
import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.TimeFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import java.util.TimeZone

const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
const val RADAR_ZOOM = 6.6
/** How long the radar waits for the temperature grid before it starts without (it follows). */
private const val GRID_WAIT_MS = 8_000L
/** Time per step while playing: the live loop, and an archived day (5-minute steps, a day in ~55 s). */
private const val FRAME_MS = 450f
private const val ARCHIVE_STEP_MS = 180f
/** The live loop rests this long on its last frame before starting over. */
private const val LOOP_PAUSE_MS = 1400L
private const val STYLE_TIMEOUT_MS = 15_000L
/** How often the open live radar asks whether a newer DWD analysis is out. */
private const val RADAR_REFRESH_CHECK_MS = 60_000L
/** Buffering of an archived day: playback starts and resumes with this many steps ready ahead. */
private const val BUFFER_START = 6
private const val BUFFER_RESUME = 12

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
    val sat = remember { SatelliteLayer(scope, container.http) }
    // a new picture area for the satellite after panning or zooming
    var satView by remember { mutableIntStateOf(0) }
    val player = remember {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        RadarPlayer(scope, container.http, RadarPlayer.fieldSideFor(am.memoryClass, am.isLowRamDevice))
    }
    var timeline by remember { mutableStateOf<RadarTimeline?>(null) }
    var error by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    /** Frames with complete tiles, for the progress hint while the loop loads. */
    var loadedFrames by remember { mutableIntStateOf(0) }
    /** Incremented by "load again". */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** Tile requests that failed or were answered from the cache while this screen is open. */
    val netStatus by RadarNetStatus.state.collectAsState()
    val nowcastHere by player.nowcastHere.collectAsState()
    val compositeHere by player.compositeHere.collectAsState()
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
                Lifecycle.Event.ON_START -> { mapView.onStart(); player.start() }
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); player.stop() }
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
                controller.installBase(style, sat)
                overlays.install(style, fieldBelow = "sat", linesBelow = controller.anchor)
                overlays.setVisible(showTemp, showWind)
                // The radar picture: one image between satellite and warnings
                player.install(style, below = "warn")
                // Panned far away: load the temperature/wind grid for the new area; a new radar
                // picture area once the view leaves the old one
                map.addOnCameraIdleListener {
                    gridCheck++
                    map.projection.visibleRegion.latLngBounds.let { b ->
                        player.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast, mapView.width)
                        sat.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast)
                    }
                    satView++
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
        controller.map?.projection?.visibleRegion?.latLngBounds?.let { b -> player.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast, mapView.width) }
        launch { player.loaded.collect { loadedFrames = it } }
        player.ready.collect {
            val tl = timeline ?: return@collect
            // An archived day plays as soon as the first coarse steps bridge the start; the
            // finer ones arrive while it plays
            val canPlay = player.canShow(minOf(frame + BUFFER_START, tl.frames.lastIndex).toFloat())
            if (!ready && canPlay) {
                ready = true
                playing = true
                if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "loop starts after ${System.currentTimeMillis() - openedAt} ms")
            }
        }
    }

    // The time line follows the composite the place lies in (the DWD's in Germany, the KNMI's or MET
    // Norway's beyond it) – decided once their areas are known (masks, from the device after the first time)
    var anchor by remember(place) { mutableStateOf(RadarComposites.anchorFor(place?.latitude ?: 51.1, place?.longitude ?: 10.4)) }

    // (Re)load the frames for the selected history range.
    LaunchedEffect(range, styleReady, reloadKey) {
        // The temperature grid is needed before the tiles: their colouring tells rain from snow.
        val lat = place?.latitude ?: 51.1
        val lon = place?.longitude ?: 10.4
        RadarNetStatus.reset()
        // Grid and time line in parallel; neither may hold up the other.
        val gridJob = async { WeatherGridStore.ensure(container.http, lat, lon, from = archiveDay) }
        // What the composites need before their first picture – the DWD's area mask (from disk after the first time)
        kotlinx.coroutines.withTimeoutOrNull(5_000L) { RadarComposites.prepare(container.http) }
        anchor = RadarComposites.anchorFor(lat, lon)
        val tl = runCatching {
            if (archiveDay != null) RadarSources.dayTimeline(container.http, archiveDay, anchor = anchor)
            else RadarSources.timeline(container.http, range, force = reloadKey > 0, anchor = anchor)
        }.getOrNull()
        if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "timeline after ${System.currentTimeMillis() - openedAt} ms")
        // the grid colours snow: waited for a while, never for ever (a slow request held the radar for minutes)
        val grid = kotlinx.coroutines.withTimeoutOrNull(GRID_WAIT_MS) { gridJob.await() }
        if (grid == null) launch { gridJob.await()?.let { overlays.setGrid(it) } }
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
    // Live radar left open: once a minute (while the app is shown – behind the lock screen it slept
    // through every minute) ask the DWD for its newest analysis; when it starts a new step of the
    // loop (10 minutes in the 2-hour range), the time line is rebuilt – playback goes on, the frames
    // already loaded come from the cache.
    LaunchedWhileShown(range, styleReady, archive) {
        if (archive || !styleReady) return@LaunchedWhileShown
        while (true) {
            delay(RADAR_REFRESH_CHECK_MS)
            val tl = timeline ?: continue
            val latest = RadarLatest.check(container.http, anchor) ?: continue
            val shown = tl.frames[tl.nowIndex].time
            if (latest / HistoryRange.STEP_MS > shown / HistoryRange.STEP_MS) {
                if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "new analysis ${RadarSources.isoTime(latest)}, refreshing")
                val atNow = frame == tl.nowIndex
                val fresh = runCatching { RadarSources.timeline(container.http, range, force = true, anchor = anchor) }.getOrNull() ?: continue
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
        // a past day too: the grid holds its hours (model values, up to four days back)
        overlays.setVisible(showTemp, showWind)
    }
    // The visible map has priority over background preloading.
    DisposableEffect(Unit) {
        RadarPrefetcher.paused = true
        RadarPrefetcher.cancelRunning()
        onDispose { RadarPrefetcher.paused = false }
    }

    LaunchedEffect(satellite, warnings) { controller.setOverlays(satellite, warnings && !archive) }
    // The satellite picture at the radar's time: every 10 minutes when standing, every hour while
    // playing (the next hour loaded ahead) – live and on a past day
    val satTime = timeline?.let { tl -> SatelliteLayer.timeFor(tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time, System.currentTimeMillis(), playing) }
    LaunchedEffect(satellite, satTime, satView, styleReady) {
        if (!satellite || !styleReady || satTime == null) return@LaunchedEffect
        if (satView == 0) controller.map?.projection?.visibleRegion?.latLngBounds?.let { b -> sat.setView(b.latitudeSouth, b.latitudeNorth, b.longitudeWest, b.longitudeEast) }
        sat.show(satTime, if (playing) satTime + SatelliteLayer.PLAY_STEP_MS else null)
    }
    // Playback runs continuously: the position moves on with every display frame and the player
    // draws the rain moved between the steps – no jumping from step to step.
    // panned where the future is empty: back to "now" if it stood in it
    LaunchedEffect(nowcastHere, timeline) {
        val tl = timeline ?: return@LaunchedEffect
        val last = tl.lastShown(nowcastHere)
        if (frame > last) { frame = last; player.position = last.toFloat() }
    }
    LaunchedEffect(playing, timeline, nowcastHere) {
        val tl = timeline ?: return@LaunchedEffect
        if (!playing) { buffering = false; player.playing = false; player.position = frame.toFloat(); return@LaunchedEffect }
        val archived = tl.day != null
        // time per 5-minute step: the live ranges show [HistoryRange.playMinutes] per beat
        val stepMs = if (archived) ARCHIVE_STEP_MS else FRAME_MS * HistoryRange.STEP_MINUTES / tl.range.playMinutes
        player.playing = true
        // where no nowcast reaches into the area: the loop ends at "now"
        val lastIndex = tl.lastShown(nowcastHere)
        var state = Playback.State(frame.coerceAtMost(lastIndex).toFloat(), buffering = !player.canShow(minOf(frame + BUFFER_START, lastIndex).toFloat()))
        var last = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000f
            last = now
            // Live loop and archived day alike: buffer (with the hint) instead of standing still
            state = Playback.step(state, dt, lastIndex, loop = !archived, stepMs = stepMs, canShow = player::canShow, resumeAhead = BUFFER_RESUME, restMs = LOOP_PAUSE_MS.toFloat())
            buffering = state.buffering
            if (!state.playing) { playing = false; break }
            player.position = state.position
            if (state.position.toInt() != frame) frame = state.position.toInt()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF15181D))) {
        AndroidView({ mapView }, Modifier.fillMaxSize())

        // Top bar
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBarsStable)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                dev.nimbus.weather.ui.components.FitText(
                    stringResource(R.string.radar_title), stringResource(R.string.radar_title_short),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                )
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
            (buffering || !player.canShow(frame.toFloat()) || loadedFrames < total)
        RadarStatusHint(
            error, stillLoading, loadedFrames, total, netStatus,
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBarsStable).padding(top = 64.dp, start = 24.dp, end = 24.dp),
        ) {
            error = false; ready = false; playing = false
            if (styleReady) reloadKey++ else styleAttempt++
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
                // the last step to choose: "now" where the area has no nowcast
                val last = tl.lastShown(nowcastHere)
                if (!archive) Row(verticalAlignment = Alignment.CenterVertically) {
                    HistoryRange.entries.forEach { r ->
                        ToggleChip(stringResource(R.string.radar_range_hours, r.hours), range == r) {
                            if (range != r) { playing = false; ready = false; range = r }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    // what the picture area has, not where the place lies: panned on, it may be other
                    if (range != HistoryRange.H2 && !compositeHere) {
                        Text(stringResource(R.string.radar_history_germany_only), fontSize = 11.sp, color = Color(0xFFFFD27A), lineHeight = 13.sp)
                    }
                }
                if (!archive) Spacer(Modifier.height(8.dp))
                // the forecast part greyed out: said why
                if (!archive && !nowcastHere && tl.lastShown(false) < tl.frames.lastIndex) {
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
                            Text(timeLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
                            Spacer(Modifier.width(8.dp))
                            // what is between time and step buttons gives way first: the buttons always fit
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            val delta = ((f.time - tl.frames[tl.nowIndex].time) / 60_000L).toInt()
                            val label = when {
                                delta == 0 -> stringResource(R.string.now)
                                delta <= -120 -> stringResource(R.string.radar_hours_ago, -delta / 60)
                                delta < 0 -> stringResource(R.string.radar_minutes_ago, -delta)
                                else -> stringResource(R.string.radar_minutes_ahead, delta)
                            }
                            if (!archive) Text(
                                label, fontSize = 13.sp, color = NimbusColors.Secondary, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(bottom = 2.dp),
                            )
                            if (f.isForecast) {
                                Spacer(Modifier.width(8.dp))
                                dev.nimbus.weather.ui.components.FitText(
                                    stringResource(R.string.forecast).uppercase(), stringResource(R.string.forecast_short).uppercase(),
                                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(4.dp)).background(Color(0x40FFD27A)).padding(horizontal = 5.dp, vertical = 1.dp),
                                    androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD27A)),
                                )
                            }
                            }
                            // Step by step: one 5-minute step back or forward (the slider is too
                            // fine for that with hundreds of steps)
                            fun stepTo(i: Int) { playing = false; frame = i.coerceIn(0, last); player.position = frame.toFloat() }
                            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.radar_step_back), frame > 0) { stepTo(frame - 1) }
                            Spacer(Modifier.width(6.dp))
                            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.radar_step_forward), frame < last) { stepTo(frame + 1) }
                        }
                        TimelineSlider(tl, frame, last) {
                            playing = false
                            frame = it
                            player.position = it.toFloat()
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Legend(showTemp, snowLegend, temperatureUnit)
                Spacer(Modifier.height(8.dp))
                // Temperature, wind and satellite for a past day too; the warnings are live only
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ToggleChip(stringResource(R.string.overlay_temperature), showTemp) { showTemp = !showTemp }
                    Spacer(Modifier.width(6.dp))
                    ToggleChip(stringResource(R.string.overlay_wind), showWind) { showWind = !showWind }
                    Spacer(Modifier.width(6.dp))
                    ToggleChip(stringResource(R.string.satellite), satellite) { satellite = !satellite }
                    if (!archive) {
                        Spacer(Modifier.width(6.dp))
                        ToggleChip(stringResource(R.string.warnings), warnings) { warnings = !warnings }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.radar_attribution), fontSize = 9.sp, color = NimbusColors.Tertiary, maxLines = 2)
            // the satellite's licence (CC BY 4.0) asks for this line while its picture is shown
            if (satellite && satTime != null) Text(
                String.format(java.util.Locale.ROOT, SatelliteLayer.ATTRIBUTION, java.time.Instant.ofEpochMilli(satTime).atZone(java.time.ZoneOffset.UTC).year),
                fontSize = 9.sp, color = NimbusColors.Tertiary, maxLines = 1,
            )
        }
    }
}
