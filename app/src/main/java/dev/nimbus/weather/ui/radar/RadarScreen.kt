package dev.nimbus.weather.ui.radar

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
private const val FRAME_MS = 450L
private const val MIN_FRAMES_TO_PLAY = 5
private const val LOAD_POLL_MS = 250L
private const val BATCH_TIMEOUT_MS = 8000L
/**
 * MapLibre loads the tiles of every layer whose visibility is "visible" – even at opacity 0.
 * Layers that must not load yet are therefore switched to visibility "none".
 */
private fun RasterLayer.state(visible: Boolean, opacity: Float) = setProperties(
    PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
    PropertyFactory.rasterOpacity(opacity),
)

/** Holds the MapLibre objects and switches the visible radar frame by layer opacity. */
private class RadarMapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    var frames: List<RadarFrame> = emptyList()
    var shown = -1
    var satellite = false
    var warnings = false
    /** Frames whose tiles are being loaded ("warm"); grows in small batches, see [warmNextBatch]. */
    private val warm = HashSet<Int>()
    /** Frames whose tiles are complete: everything that was warm when the map last became idle. */
    private val loaded = HashSet<Int>()
    /** Load order: from "now" outwards (now, −10 min, +10 min, −20 min …). */
    private var order: List<Int> = emptyList()
    private var nowIndex = 0
    val allWarm: Boolean get() = frames.isNotEmpty() && warm.size >= frames.size
    val allLoaded: Boolean get() = frames.isNotEmpty() && loaded.size >= frames.size

    /** The map is idle: all warm frames have their tiles. */
    fun markIdle() { loaded += warm }

    /** Contiguous range of loaded frames around "now" – the part of the loop that can play. */
    fun playableRange(): IntRange {
        if (nowIndex !in loaded) return IntRange.EMPTY
        var lo = nowIndex
        var hi = nowIndex
        while (lo - 1 in loaded) lo--
        while (hi + 1 in loaded) hi++
        return lo..hi
    }
    /** Frame opacity; 1 when the temperature field is shown so colours do not mix. */
    var frameOpacity = 0.85f

    /** Satellite and warning layers; the radar frames are inserted between them. */
    fun installBase(style: Style) {
        this.style = style
        val below = style.layers.firstOrNull { it is SymbolLayer }?.id
        fun add(layer: RasterLayer) = if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        style.addSource(RasterSource("sat", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.SAT_LAYER, null)).apply { maxZoom = 9f }, 512).apply { prefetchZoomDelta = 0 })
        add(RasterLayer("sat", "sat").withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
        style.addSource(RasterSource("warn", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.WARN_LAYER, null)).apply {
            maxZoom = 10f
            setBounds(5.5f, 47.0f, 15.5f, 55.2f)
        }, 512).apply { prefetchZoomDelta = 0 })
        add(RasterLayer("warn", "warn").withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
    }

    /** Replaces the animation frames, e.g. when the history range changes. */
    fun replaceFrames(timeline: RadarTimeline) {
        val style = style ?: return
        frames.indices.forEach { i ->
            listOf("dwd$i", "rv$i").forEach { id ->
                style.getLayer(id)?.let { style.removeLayer(it) }
                style.getSource(id)?.let { style.removeSource(it) }
            }
        }
        frames = timeline.frames
        shown = -1
        warm.clear()
        loaded.clear()
        nowIndex = timeline.nowIndex
        order = buildList {
            add(nowIndex)
            for (d in 1..frames.size) {
                if (nowIndex - d >= 0) add(nowIndex - d)
                if (nowIndex + d <= frames.lastIndex) add(nowIndex + d)
            }
        }
        fun add(layer: RasterLayer) = style.addLayerBelow(layer, "warn")

        timeline.frames.forEachIndexed { i, f ->
            f.rainViewerPath?.let { path ->
                val ts = TileSet("2.2.0", RadarSources.rainViewerTileUrl(timeline.rainViewerHost, path)).apply { maxZoom = 7f }
                // No low-zoom placeholder tiles (default: 4 levels lower): with ~50 radar sources
                // they would multiply the requests and delay the frames that are actually shown.
                style.addSource(RasterSource("rv$i", ts, 512).apply { prefetchZoomDelta = 0 })
                add(
                    RasterLayer("rv$i", "rv$i").withProperties(
                        PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f),
                        PropertyFactory.visibility(Property.NONE),
                        PropertyFactory.rasterResampling("linear"),
                    ),
                )
            }
            f.dwdTime?.let { t ->
                val ts = TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.DWD_LAYER, t)).apply {
                    maxZoom = 10f
                    minZoom = 3f
                    setBounds(1.4f, 45.6f, 18.8f, 56.3f)
                }
                style.addSource(RasterSource("dwd$i", ts, 512).apply { prefetchZoomDelta = 0 })
                add(
                    RasterLayer("dwd$i", "dwd$i").withProperties(
                        PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f),
                        PropertyFactory.visibility(Property.NONE),
                        PropertyFactory.rasterResampling("linear"),
                    ),
                )
            }
        }
        show(timeline.nowIndex)
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

    fun show(index: Int, force: Boolean = false) {
        val s = style ?: return
        if (index == shown && !force) return
        // Warm frames are "visible" at opacity 0, so their tiles are loaded ahead and the loop
        // plays without flicker; all others stay "none" and cost no requests yet.
        frames.indices.forEach { i -> if (i != index) setState(s, i, i in warm, 0f) }
        setState(s, index, true, frameOpacity)
        warm += index
        shown = index
    }

    private fun setState(s: Style, i: Int, visible: Boolean, opacity: Float) {
        (s.getLayer("dwd$i") as? RasterLayer)?.state(visible, opacity)
        (s.getLayer("rv$i") as? RasterLayer)?.state(visible, opacity)
    }

    /**
     * Starts loading the next few frames in playback order. Called whenever the map becomes idle,
     * so the base map and the visible frame always load first and never starve behind ~300
     * radar tiles in MapLibre's request queue.
     */
    fun warmNextBatch(size: Int = 8) {
        val s = style ?: return
        order.filter { it !in warm }.take(size).forEach { i ->
            warm += i
            setState(s, i, true, 0f)
        }
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
fun RadarScreen(place: Place?, temperatureUnit: dev.nimbus.weather.data.model.TemperatureUnit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as NimbusApp).container }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val controller = remember { RadarMapController() }
    var timeline by remember { mutableStateOf<RadarTimeline?>(null) }
    var error by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(false) }
    var warnings by rememberSaveable { mutableStateOf(false) }
    var showTemp by rememberSaveable { mutableStateOf(false) }
    var showWind by rememberSaveable { mutableStateOf(false) }
    val overlays = remember { WeatherOverlays(temperatureUnit) }
    var gridCheck by remember { mutableIntStateOf(0) }
    val tf = remember { TimeFormat(TimeZone.getDefault().id, DateFormat.is24HourFormat(context)) }
    val openedAt = remember { System.currentTimeMillis() }

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
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
        }
    }

    var range by rememberSaveable { mutableStateOf(HistoryRange.H2) }
    var styleReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val styleBuilder = MapStyle.builder(container.mapHttp, context.resources.configuration.locales[0].language)
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
                overlays.install(style, fieldBelow = "sat")
                overlays.setVisible(showTemp, showWind)
                // Panned far away: load the temperature/wind grid for the new area.
                map.addOnCameraIdleListener { gridCheck++ }
                place?.let { controller.addLocation(style, it) }
                controller.setOverlays(satellite, warnings)
                // Base map + visible frame first; each time the map is idle the next frames are
                // warmed up. The loop starts once all frames are loaded.
                // Frames are loaded batch by batch, from "now" outwards: the next batch is requested
                // only when the map reports all tiles of the previous one loaded, so the frames next
                // to "now" are complete early. The loop starts with the first few and grows.
                // "fully" = every tile of every visible layer is loaded (MapLibre render flag).
                var fullyRendered = false
                mapView.addOnDidFinishRenderingFrameListener { fully, _, _ -> fullyRendered = fully }
                scope.launch {
                    var waited = 0L
                    while (!controller.allLoaded) {
                        delay(LOAD_POLL_MS)
                        if (controller.frames.isEmpty()) continue
                        waited += LOAD_POLL_MS
                        // Wait at least one poll after a batch so the flag reflects the new layers;
                        // a tile that never answers must not stall the rest: move on after a while.
                        if ((fullyRendered && waited > LOAD_POLL_MS) || waited >= BATCH_TIMEOUT_MS) {
                            controller.markIdle()
                            fullyRendered = false
                            controller.warmNextBatch()
                            waited = 0
                        }
                        if (!ready && (controller.playableRange().count() >= MIN_FRAMES_TO_PLAY || controller.allLoaded)) {
                            ready = true
                            playing = true
                            if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "loop starts after ${System.currentTimeMillis() - openedAt} ms")
                        }
                    }
                    if (BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "all frames after ${System.currentTimeMillis() - openedAt} ms")
                }
                styleReady = true
            }
        }
    }

    // (Re)load the frames for the selected history range.
    LaunchedEffect(range, styleReady) {
        // The temperature grid first: the tile recolouring uses it to tell rain from snow.
        val lat = place?.latitude ?: 51.1
        val lon = place?.longitude ?: 10.4
        val grid = WeatherGridStore.ensure(container.http, lat, lon)
        val tl = runCatching { RadarSources.timeline(container.http, range) }.getOrNull()
        if (tl == null) { error = true; return@LaunchedEffect }
        error = false
        timeline = tl
        frame = tl.nowIndex
        if (styleReady) {
            grid?.let { overlays.setGrid(it) }
            controller.replaceFrames(tl)
            overlays.update(tl.frames[tl.nowIndex].time)
        }
    }
    LaunchedEffect(frame, timeline) {
        timeline?.let { tl -> overlays.update(tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time) }
    }
    // After panning or zooming: a grid that matches the zoom and covers the view. Finer grids are
    // only fetched while an overlay is shown (each costs 99 API calls); otherwise the coarse one.
    LaunchedEffect(gridCheck, showTemp, showWind, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val cam = controller.map?.cameraPosition ?: return@LaunchedEffect
        val c = cam.target ?: return@LaunchedEffect
        val step = if (showTemp || showWind) WeatherGrid.stepForZoom(cam.zoom) else WeatherGrid.STEP
        WeatherGridStore.ensure(container.http, c.latitude, c.longitude, step)?.let {
            overlays.setGrid(it)
            timeline?.let { tl -> overlays.update(tl.frames[frame.coerceIn(0, tl.frames.lastIndex)].time) }
        }
    }
    LaunchedEffect(showTemp, showWind, styleReady) {
        if (!styleReady) return@LaunchedEffect
        overlays.setVisible(showTemp, showWind)
        controller.frameOpacity = if (showTemp) 1f else 0.85f
        controller.show(frame, force = true)
    }
    // The visible map has priority over background preloading.
    DisposableEffect(Unit) {
        RadarPrefetcher.paused = true
        RadarPrefetcher.cancelRunning()
        onDispose { RadarPrefetcher.paused = false }
    }

    LaunchedEffect(frame) { controller.show(frame) }
    LaunchedEffect(satellite, warnings) { controller.setOverlays(satellite, warnings) }
    LaunchedEffect(playing, timeline) {
        timeline ?: return@LaunchedEffect
        while (playing) {
            // Play only the loaded part of the loop; it grows while the remaining frames load.
            val range = controller.playableRange().takeIf { !it.isEmpty() } ?: (frame..frame)
            val last = frame >= range.last
            delay(if (last) 1400L else FRAME_MS)
            frame = if (last || frame !in range) range.first else frame + 1
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
                place?.let { Text(it.name, fontSize = 13.sp, color = NimbusColors.Secondary) }
            }
            IconButton(onClick = {
                place?.let { p -> controller.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 7.5)) }
            }) { Icon(Icons.Rounded.MyLocation, stringResource(R.string.my_position), tint = Color.White) }
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).size(36.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (!ready && !error) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.radar_loading), color = Color.White, fontSize = 14.sp)
            }
        }
        if (error) {
            Text(
                stringResource(R.string.radar_error), Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(16.dp),
                color = Color.White,
            )
        }

        // Bottom controls
        val tl = timeline
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05080D), Color(0xEE05080D))))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
        ) {
            if (tl != null) {
                val f = tl.frames[frame.coerceIn(0, tl.frames.lastIndex)]
                val outsideGermany = place != null && !WeatherRepository.isInDwdArea(place.latitude, place.longitude)
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.height(8.dp))
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
                        onClick = { playing = !playing },
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
                            val timeLabel = if (tf.isSameDay(f.time, System.currentTimeMillis())) tf.time(f.time)
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
                            Text(label, fontSize = 13.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 2.dp))
                            if (f.isForecast) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.forecast).uppercase(),
                                    Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x40FFD27A)).padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD27A),
                                )
                            }
                        }
                        TimelineSlider(tl, frame) { playing = false; frame = it }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Legend(showTemp, temperatureUnit)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
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
                val nowX = size.width * tl.nowIndex / n
                val pos = size.width * (state.value / n)
                // past = white-ish, forecast = amber
                drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(nowX, y), h, StrokeCap.Round)
                drawLine(Color(0x66FFD27A), Offset(nowX, y), Offset(size.width, y), h, StrokeCap.Round)
                drawLine(Color(0xCCFFFFFF), Offset(0f, y), Offset(minOf(pos, nowX), y), h, StrokeCap.Round)
                if (pos > nowX) drawLine(Color(0xFFFFD27A), Offset(nowX, y), Offset(pos, y), h, StrokeCap.Round)
                // hour ticks and "now" marker
                for (i in 0..n step 6) {
                    val x = size.width * i / n
                    drawLine(Color(0x80FFFFFF), Offset(x, y + 6.dp.toPx()), Offset(x, y + 9.dp.toPx()), 1.dp.toPx())
                }
                drawLine(Color.White, Offset(nowX, y - 7.dp.toPx()), Offset(nowX, y + 7.dp.toPx()), 1.5.dp.toPx())
            }
        },
    )
}

/** Rain (blue → red) and snow (pink → violet) scales, plus the temperature scale when shown. */
@Composable
private fun Legend(showTemp: Boolean, unit: dev.nimbus.weather.data.model.TemperatureUnit) {
    @Composable
    fun Bar(label: String, colors: List<Color>, modifier: Modifier) {
        Column(modifier) {
            Text(label, fontSize = 10.sp, color = NimbusColors.Secondary)
            Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                drawRect(Brush.horizontalGradient(colors))
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Bar(stringResource(R.string.legend_rain), RadarPalette.legendRain.map { Color(it) }, Modifier.weight(1.4f))
        Spacer(Modifier.width(10.dp))
        Bar(stringResource(R.string.legend_snow), RadarPalette.legendSnow.map { Color(it) }, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.radar_light), fontSize = 10.sp, color = NimbusColors.Tertiary)
        Text(stringResource(R.string.radar_heavy), fontSize = 10.sp, color = NimbusColors.Tertiary)
    }
    if (showTemp) {
        Spacer(Modifier.height(4.dp))
        // Discrete bands like on the map (areas of equal temperature).
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            val bands = (-10 until 20)        // -20 °C … 40 °C in 2 °C steps; labels at thirds
            val bw = size.width / bands.count()
            bands.forEachIndexed { i, b ->
                drawRect(
                    dev.nimbus.weather.ui.main.Insights.temperatureColor((b + 0.5) * WeatherOverlays.BAND),
                    androidx.compose.ui.geometry.Offset(i * bw, 0f), androidx.compose.ui.geometry.Size(bw + 0.5f, size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(-20.0, 0.0, 20.0, 40.0).forEach {
                Text(dev.nimbus.weather.util.Units.temp(it, unit), fontSize = 10.sp, color = NimbusColors.Tertiary)
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
