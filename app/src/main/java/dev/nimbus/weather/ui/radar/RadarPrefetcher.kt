package dev.nimbus.weather.ui.radar

import android.content.Context
import android.net.ConnectivityManager
import dev.nimbus.weather.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import kotlin.coroutines.resume

/**
 * Loads the radar loop for a place in the background, so the radar screen opens instantly:
 * an invisible MapLibre snapshot of exactly the radar view (same size, camera and frames) makes
 * MapLibre fetch the base map and all radar tiles, which then sit in the HTTP / map caches.
 */
object RadarPrefetcher {
    private val lastRun = HashMap<String, Long>()
    private var running: MapSnapshotter? = null

    /** True while the radar screen is open: it loads its own tiles and has priority. */
    @Volatile var paused = false

    /** Stops a running prefetch, e.g. when the radar screen opens and needs the bandwidth. */
    fun cancelRunning() {
        running?.cancel()
        running = null
    }

    private const val MIN_INTERVAL_MS = 10 * 60_000L

    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }

    suspend fun prefetch(context: Context, http: OkHttpClient, place: Place) {
        if (paused) return
        val key = "%.2f,%.2f".format(place.latitude, place.longitude)
        val now = System.currentTimeMillis()
        synchronized(lastRun) {
            if (now - (lastRun[key] ?: 0L) < MIN_INTERVAL_MS) return
            lastRun[key] = now
        }
        WeatherGridStore.ensure(http, place.latitude, place.longitude)
        val tl = runCatching { RadarSources.timeline(http, HistoryRange.H2) }.getOrNull() ?: return
        withContext(Dispatchers.Main) {
            val dm = context.resources.displayMetrics
            val style = Style.Builder().fromUri(STYLE_URL)
            tl.frames.forEachIndexed { i, f ->
                // Opacity 0 but visibility "visible": MapLibre loads the tiles without drawing them.
                f.rainViewerPath?.let { path ->
                    style.withSource(RasterSource("rv$i", TileSet("2.2.0", RadarSources.rainViewerTileUrl(tl.rainViewerHost, path)).apply { maxZoom = 7f }, 512))
                    style.withLayer(RasterLayer("rv$i", "rv$i").withProperties(PropertyFactory.rasterOpacity(0f)))
                }
                f.dwdTime?.let { t ->
                    style.withSource(RasterSource("dwd$i", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.DWD_LAYER, t)).apply {
                        maxZoom = 10f
                        minZoom = 3f
                        setBounds(1.4f, 45.6f, 18.8f, 56.3f)
                    }, 512))
                    style.withLayer(RasterLayer("dwd$i", "dwd$i").withProperties(PropertyFactory.rasterOpacity(0f)))
                }
            }
            val options = MapSnapshotter.Options((dm.widthPixels / dm.density).toInt(), (dm.heightPixels / dm.density).toInt())
                .withStyleBuilder(style)
                .withCameraPosition(CameraPosition.Builder().target(LatLng(place.latitude, place.longitude)).zoom(RADAR_ZOOM).build())
                .withPixelRatio(1f)
                .withLogo(false)
            val snapshotter = MapSnapshotter(context, options)
            running = snapshotter
            withTimeoutOrNull(90_000L) {
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    snapshotter.start({ if (cont.isActive) cont.resume(Unit) }, { if (cont.isActive) cont.resume(Unit) })
                }
            } ?: snapshotter.cancel()
            if (running === snapshotter) running = null
        }
    }
}
