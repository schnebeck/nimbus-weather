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

import android.app.Application
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.radar.RadarCacheInterceptor
import dev.nimbus.weather.ui.radar.RadarTileInterceptor
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
        container = AppContainer(this)
        dev.nimbus.weather.ui.radar.WeatherGridStore.cacheDir = java.io.File(cacheDir, "grid")
        dev.nimbus.weather.ui.radar.RadarSources.stateDir = java.io.File(cacheDir, "radar")
        MapLibre.getInstance(this)
        // MapLibre stops requesting tiles while Android reports no connection and waits for it to
        // come back. Our HTTP client answers from its cache when offline (StaleFallbackInterceptor),
        // so let MapLibre always ask: the stored map and radar appear immediately.
        MapLibre.setConnected(true)
        HttpRequestUtil.setOkHttpClient(container.mapHttp)
        dev.nimbus.weather.data.repo.RefreshWorker.schedule(this)
        dev.nimbus.weather.data.repo.RadarWorker.schedule(this)
    }
}

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(app.cacheDir, "http"), 20L * 1024 * 1024))
        .retryOnConnectionFailure(true)
        .build()

    /** Client used by MapLibre for map and radar tiles; recolors radar images on the fly. */
    val mapHttp: OkHttpClient = http.newBuilder()
        // Map tiles come from few hosts; OkHttp's default of 5 parallel requests per host is too low.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 12 })
        // A slow tile must not block its slot for long: MapLibre asks again, and the cache
        // fallback below answers with the stored copy.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        // Base map, three radar ranges and their nowcasts; the OS may trim it when storage is low.
        .cache(Cache(File(app.cacheDir, "maptiles"), 150L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .addInterceptor(RadarTileInterceptor())
        .addInterceptor(StaleFallbackInterceptor(setOf("maps.dwd.de", "tilecache.rainviewer.com", "api.rainviewer.com", "tiles.openfreemap.org")))
        .addInterceptor(RetryInterceptor(setOf("maps.dwd.de", "tilecache.rainviewer.com")))
        .addNetworkInterceptor(RadarCacheInterceptor())
        .build()

    val store = Store(app)
    val location = LocationProvider(app, http)
    val openMeteo = OpenMeteoSource(http)
    val history = dev.nimbus.weather.data.remote.HistorySource(http)
    val repository = WeatherRepository(
        openMeteo, BrightSkySource(http), CommunitySource(http), PollenSource(http),
        dev.nimbus.weather.data.remote.GaugeSource(http, File(app.cacheDir, "tides")),
    )
}
