/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarPrefetcher.kt
 * Loads the radar loop in the background so the radar opens instantly.
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
import android.net.ConnectivityManager
import dev.nimbus.weather.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * Loads the radar loop for a place ahead, so the radar screen opens instantly: the past time
 * steps go into the [RadarStore] (one image per step, decoded and kept on disk) and an invisible
 * MapLibre snapshot of the radar view fetches the base map into the map cache. Only for someone
 * who uses the radar ([usedRecently]); and never the nowcast: its steps are replaced with every
 * analysis (every 5 minutes) – loaded ahead they were out of date when the radar opened, and
 * were most of the 60 MB a day measured on the phone.
 */
object RadarPrefetcher {
    private val lastRun = HashMap<String, Long>()
    private var running: MapSnapshotter? = null

    /**
     * Completed when the radar preview card has rendered. The preview is what the user sees; the
     * prefetch of ~300 tiles would otherwise block its few tiles in the request queue.
     */
    @Volatile private var previewReady = kotlinx.coroutines.CompletableDeferred<Unit>()

    fun previewRendered() { previewReady.complete(Unit) }

    fun previewStarted() { if (previewReady.isCompleted) previewReady = kotlinx.coroutines.CompletableDeferred() }

    /** True while the radar screen is open: it loads its own tiles and has priority. */
    @Volatile var paused = false

    /** Stops a running prefetch, e.g. when the radar screen opens and needs the bandwidth. */
    fun cancelRunning() {
        running?.cancel()
        running = null
    }

    private const val MIN_INTERVAL_MS = 8 * 60_000L
    /** The radar opened within this long: its loop is loaded ahead. */
    const val USED_WITHIN_MS = 7 * 24 * 3_600_000L
    private const val PREFS = "radar"
    private const val OPENED_AT = "openedAt"
    private const val LAT = "lat"
    private const val LON = "lon"

    /** The radar screen was opened (now), around [place]: the place the background keeps loaded. */
    fun radarOpened(context: Context, place: Place?, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(OPENED_AT, now).apply {
            if (place != null) putString(LAT, place.latitude.toString()).putString(LON, place.longitude.toString())
        }.apply()
    }

    /** Where the radar was last opened; null: not yet. */
    fun lastPlace(context: Context): Pair<Double, Double>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = p.getString(LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(LON, null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    /** Whether the radar was opened within [USED_WITHIN_MS] before [now]. */
    fun usedRecently(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        usedRecently(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(OPENED_AT, 0L), now)

    fun usedRecently(openedAt: Long, now: Long): Boolean = openedAt > 0 && now - openedAt < USED_WITHIN_MS

    /**
     * The steps to load ahead of [tl] for the composites [here]: the past ones – the nowcast's
     * are left to the radar screen (they are new with every analysis).
     */
    fun stepsAhead(tl: RadarTimeline, here: List<RadarComposite>): List<Pair<RadarComposite, RadarFrame>> =
        tl.frames.filter { !it.isForecast }.flatMap { f -> here.map { c -> c to f } }

    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }

    /** A network to load ahead over: Wi-Fi, or any with [mobile] (setting). */
    fun networkAllows(context: Context, mobile: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (mobile) cm.activeNetwork != null else isUnmetered(context)
    }

    /**
     * The past steps of the radar at [lat]/[lon] into the store, with the temperature and wind
     * grid – what the screen needs besides the nowcast; false without a time line.
     */
    suspend fun loadSteps(context: Context, lat: Double, lon: Double): Boolean {
        val container = (context.applicationContext as dev.nimbus.weather.NimbusApp).container
        WeatherGridStore.ensure(container.mapHttp, lat, lon)
        val anchor = RadarComposites.anchorFor(lat, lon)
        val tl = runCatching { RadarSources.timeline(container.mapHttp, HistoryRange.H2, anchor = anchor) }.getOrNull() ?: return false
        kotlinx.coroutines.coroutineScope {
            // the steps of every composite the place lies in (a windowed one only for a picture
            // area – the radar view loads it); raw images through the plain client (the map
            // client recolours them)
            val here = RadarComposites.all.filter { it.covers(lat, lon) && !it.windowed }
            stepsAhead(tl, here).map { (c, f) -> this.async { RadarStore.grid(container.http, c, f) } }.forEach { it.await() }
        }
        withContext(Dispatchers.IO) { RadarStore.prune() }
        return true
    }

    suspend fun prefetch(context: Context, http: OkHttpClient, place: Place) {
        if (paused || !usedRecently(context)) return
        val key = "%.2f,%.2f".format(place.latitude, place.longitude)
        val now = System.currentTimeMillis()
        synchronized(lastRun) {
            if (now - (lastRun[key] ?: 0L) < MIN_INTERVAL_MS) {
                if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "prefetch skipped: last run ${(now - lastRun[key]!!) / 1000} s ago")
                return
            }
            lastRun[key] = now
        }
        withTimeoutOrNull(30_000L) { previewReady.await() }
        if (paused) return
        if (!loadSteps(context, place.latitude, place.longitude) || paused) return
        // The base map of the radar view (no radar layers: the radar comes from the store)
        val style = MapStyle.builder(http, context.resources.configuration.locales[0].language)
        withContext(Dispatchers.Main) {
            val dm = context.resources.displayMetrics
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
