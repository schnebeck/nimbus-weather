/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/BackgroundWork.kt
 * Nimbus works only while it is shown: periodic background work still scheduled is called off.
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
import dev.nimbus.weather.ui.radar.RadarPrefetcher
import java.util.concurrent.TimeUnit

/**
 * Work in the background: only the radar loop ahead ([RadarAheadWorker]) – switched in the
 * settings, about every half hour while the battery is not low, over Wi-Fi or any network as set.
 * Work scheduled under former names (hourly weather, the 15-minute radar) is called off at the
 * start: opened, the app loads what is out of date anyway.
 */
object BackgroundWork {
    /** The names background work was scheduled under – called off wherever it is still there. */
    val formerNames = listOf("hourly-refresh", "radar-prefetch")
    const val RADAR_AHEAD = "radar-ahead"
    const val RADAR_AHEAD_MINUTES = 30L

    fun stopAll(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        formerNames.forEach { wm.cancelUniqueWork(it) }
    }

    /** The radar loop loaded ahead in the background ([on]), over mobile data too with [mobile]. */
    fun radarAhead(context: Context, on: Boolean, mobile: Boolean) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!on) { wm.cancelUniqueWork(RADAR_AHEAD); return }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (mobile) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RadarAheadWorker>(RADAR_AHEAD_MINUTES, TimeUnit.MINUTES).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(RADAR_AHEAD, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Loads the past radar steps around the place last seen on the radar – if the radar is in use. */
class RadarAheadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!RadarPrefetcher.usedRecently(ctx)) return Result.success()
        val (lat, lon) = RadarPrefetcher.lastPlace(ctx) ?: return Result.success()
        runCatching { RadarPrefetcher.loadSteps(ctx, lat, lon) }
        return Result.success()
    }
}
