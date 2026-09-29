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
