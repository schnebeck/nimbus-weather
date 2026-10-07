/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/NimbusApp.kt
 * Application class and the small dependency container (HTTP, sources, repository).
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

package dev.nimbus.weather

import kotlinx.coroutines.flow.first
import android.app.Application
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.radar.MapCacheInterceptor
import dev.nimbus.weather.ui.radar.RetryInterceptor
import dev.nimbus.weather.ui.radar.StaleFallbackInterceptor
import okhttp3.Cache
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NimbusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // first: the log sees the app's first calls too (when switched on)
        dev.nimbus.weather.util.ActivityLog.init(this)
        container = AppContainer(this)
        dev.nimbus.weather.ui.radar.WeatherGridStore.cacheDir = java.io.File(cacheDir, "grid")
        dev.nimbus.weather.ui.radar.RadarLatest.dir = java.io.File(cacheDir, "radar")
        dev.nimbus.weather.ui.radar.RadarPreview.dir = java.io.File(cacheDir, "previews")
        dev.nimbus.weather.ui.radar.RadarComposites.dir = java.io.File(cacheDir, "radar")
        // Decoded radar steps; expired ones are removed in the background
        dev.nimbus.weather.ui.radar.RadarStore.dir = java.io.File(cacheDir, "radarstore")
        Thread { runCatching { dev.nimbus.weather.ui.radar.RadarStore.prune() } }.start()
        // What nobody needs any more goes (at most once a day; see Housekeeping)
        Thread {
            runCatching {
                val ids = kotlinx.coroutines.runBlocking { container.store.places.first() }.map { it.id } +
                    dev.nimbus.weather.data.repo.LocationProvider.CURRENT_LOCATION_ID
                dev.nimbus.weather.data.repo.Housekeeping.runIfDue(this, ids)
            }
        }.start()
        MapLibre.getInstance(this)
        // MapLibre stops requesting tiles while Android reports no connection and waits for it to
        // come back. Our HTTP client answers from its cache when offline (StaleFallbackInterceptor),
        // so let MapLibre always ask: the stored map and radar appear immediately.
        MapLibre.setConnected(true)
        HttpRequestUtil.setOkHttpClient(container.mapHttp)
        // no work in the background: what earlier versions scheduled is called off
        dev.nimbus.weather.data.repo.BackgroundWork.stopAll(this)
    }
}

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(app.cacheDir, "http"), 20L * 1024 * 1024))
        .retryOnConnectionFailure(true)
        // every call of the app (and of the map, built from this) for the activity log
        .eventListenerFactory(dev.nimbus.weather.util.ActivityLog.events)
        .build()

    /** Client used by MapLibre: the base map, the DWD's warnings, the satellite picture (the radar comes from [dev.nimbus.weather.ui.radar.RadarStore]). */
    val mapHttp: OkHttpClient = http.newBuilder()
        // Map tiles come from few hosts; OkHttp's default of 5 parallel requests per host is too low.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 12 })
        // A slow tile must not block its slot for long: MapLibre asks again, and the cache
        // fallback below answers with the stored copy.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        // Base map and overlay tiles; the OS may trim it when storage is low.
        .cache(Cache(File(app.cacheDir, "maptiles"), 150L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .addInterceptor(StaleFallbackInterceptor(setOf("maps.dwd.de", "tiles.openfreemap.org")))
        .addInterceptor(RetryInterceptor(setOf("maps.dwd.de")))
        .addNetworkInterceptor(MapCacheInterceptor())
        .build()

    val store = Store(app)
    val location = LocationProvider(app, http)
    val openMeteo = OpenMeteoSource(http)
    val history = dev.nimbus.weather.data.remote.HistorySource(http, spot = dev.nimbus.weather.data.remote.SpotSource(http))
    val repository = WeatherRepository(
        openMeteo, BrightSkySource(http), CommunitySource(http), PollenSource(http),
        dev.nimbus.weather.data.remote.GaugeSource(http, File(app.cacheDir, "tides")),
        bathing = dev.nimbus.weather.data.remote.BathingSource(http, File(app.cacheDir, "bathing")) {
            dev.nimbus.weather.ui.radar.RadarPrefetcher.isUnmetered(app)
        },
        stations = dev.nimbus.weather.data.remote.StationNetworks(http),
    )
}
