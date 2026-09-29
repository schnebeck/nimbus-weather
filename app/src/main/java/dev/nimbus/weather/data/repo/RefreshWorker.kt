/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/RefreshWorker.kt
 * Hourly background refresh of the weather for all places.
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
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Hourly background refresh of all places, so the app opens with current data (also offline).
 * The "current location" entry is refreshed at its last known coordinates: a fresh GPS fix in
 * the background would need the separate "allow all the time" location permission.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NimbusApp).container
        val store = container.store
        val settings = store.settings.first()
        val german = applicationContext.resources.configuration.locales[0].language == Locale.GERMAN.language
        val places = store.places.first() + listOfNotNull(store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)?.place)
        var failures = 0
        places.distinctBy { it.id }.forEach { place ->
            runCatching { container.repository.load(place, settings, german) }
                .onSuccess { store.cacheWeather(it) }
                .onFailure { failures++ }
        }
        return if (failures == places.size && places.isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "hourly-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
