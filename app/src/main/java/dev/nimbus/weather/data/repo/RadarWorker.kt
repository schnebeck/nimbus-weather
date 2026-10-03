/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/RadarWorker.kt
 * Keeps the 2-hour radar loop of the current place fresh in the cache while on Wi-Fi.
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

package dev.nimbus.weather.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.ui.radar.RadarPrefetcher
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes (the shortest period Android allows) on an unmetered network, and only with
 * "Preload radar" switched on: loads the 2-hour radar loop of the current place into the cache,
 * so the radar opens with a current picture. Frames already stored are not loaded again; a run
 * typically fetches the newest analysis and the renewed nowcast frames. Android defers the job
 * while the device dozes and runs it less often for rarely used apps.
 */
class RadarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NimbusApp).container
        val store = container.store
        if (!store.settings.first().preloadRadar || !RadarPrefetcher.isUnmetered(applicationContext)) return Result.success()
        // the loop is for opening the radar at once – not worth it while nobody opens the app
        if (!AppUse.worthIt(AppUse.lastUsed(applicationContext), System.currentTimeMillis(), AppUse.RADAR_IDLE_MS)) return Result.success()
        val place = store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)?.place
            ?: store.places.first().firstOrNull()
            ?: return Result.success()
        val result = runCatching { RadarPrefetcher.prefetch(applicationContext, container.mapHttp, place, background = true) }
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusRadar", "background prefetch for ${place.name}: $result")
        return Result.success()
    }

    companion object {
        private const val NAME = "radar-prefetch"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RadarWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
