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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * Loads the radar loop for a place in the background, so the radar screen opens instantly:
 * an invisible MapLibre snapshot of exactly the radar view (same size, camera and frames) makes
 * MapLibre fetch the base map and all radar tiles, which then sit in the HTTP / map caches.
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

    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }

    /**
     * [background]: called by the periodic worker – there is no preview card to wait for, and the
     * snapshot runs without a visible activity.
     */
    suspend fun prefetch(context: Context, http: OkHttpClient, place: Place, background: Boolean = false) {
        if (paused) return
        val key = "%.2f,%.2f".format(place.latitude, place.longitude)
        val now = System.currentTimeMillis()
        synchronized(lastRun) {
            if (now - (lastRun[key] ?: 0L) < MIN_INTERVAL_MS) {
                if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "prefetch skipped: last run ${(now - lastRun[key]!!) / 1000} s ago")
                return
            }
            lastRun[key] = now
        }
        if (!background) withTimeoutOrNull(30_000L) { previewReady.await() }
        if (paused) return
        WeatherGridStore.ensure(http, place.latitude, place.longitude)
        val tl = runCatching { RadarSources.timeline(http, HistoryRange.H2) }.getOrNull() ?: return
        // Opacity 0: MapLibre loads the tiles of visible layers without drawing them.
        val style = MapStyle.builder(
            http, context.resources.configuration.locales[0].language,
            RadarSnapshot.rasters(tl, tl.frames.withIndex().map { it.index to it.value }, 0f),
        )
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
