/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/NimbusWidget.kt
 * The home-screen widget: shown from the weather the app keeps, fetched anew about every half
 * hour, on a tap and whenever the app loads its place.
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

package dev.nimbus.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.util.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NimbusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.schedule(context)
        async { Widgets.show(context, ids.toList()) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        async { Widgets.show(context, listOf(id)) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val configs = WidgetConfigs(context)
        ids.forEach(configs::remove)
    }

    override fun onEnabled(context: Context) = Widgets.schedule(context)

    override fun onDisabled(context: Context) {
        Widgets.unschedule(context)
        Widgets.cancelNewDay(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                async { Widgets.show(context, listOf(id), updating = true) }
                Widgets.fetchNow(context, forced = true)
            }
            // the alarm's time, the date's language, the hour of the place, a new day (its week): shown anew
            ACTION_NEW_DAY, android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> async { Widgets.showAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    /** [block] off the main thread, the broadcast held open until it is done. */
    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runCatching { block() } } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.nimbus.weather.widget.REFRESH"
        const val ACTION_NEW_DAY = "dev.nimbus.weather.widget.NEW_DAY"
    }
}

/**
 * The widgets: shown from the weather the app keeps per place ([Store.cachedWeather]), fetched
 * anew by [WidgetWorker]. Fetched data go to that store too – the app opens with them.
 */
object Widgets {
    private const val WORK = "widget"
    private const val WORK_NOW = "widget-now"
    const val INTERVAL_MINUTES = 30L
    /** Data younger than this are not fetched again (the app has just loaded them). */
    const val FRESH_MS = 20 * 60_000L

    fun ids(context: Context): List<Int> = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NimbusWidget::class.java)).toList()
    }.getOrDefault(emptyList())

    private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** About every half hour while there is a widget – over any network. */
    fun schedule(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).setConstraints(network()).build(),
        )
    }

    fun unschedule(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.cancelUniqueWork(WORK)
        wm.cancelUniqueWork(WORK_NOW)
    }

    /**
     * Fetched now (a widget just set up, a tap on its refresh) – as soon as there is a network;
     * [forced] (the tap): the forecast anew however fresh it is.
     */
    fun fetchNow(context: Context, forced: Boolean = false) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<WidgetWorker>().setConstraints(network())
            .setInputData(androidx.work.workDataOf(WidgetWorker.FORCED to forced)).build()
        wm.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Every widget shown anew from the stored weather – after the app loaded a place, say. */
    suspend fun showAll(context: Context) {
        val ids = ids(context)
        if (ids.isNotEmpty()) show(context, ids)
    }

    /**
     * The place of a widget: the one chosen – "my location" ([WidgetConfig.placeId] null) is
     * where the app found the phone last; never located: the first place of the list.
     */
    suspend fun placeOf(store: Store, config: WidgetConfig): Place? {
        val places = store.places.first()
        if (config.placeId != null) places.firstOrNull { it.id == config.placeId }?.let { return it }
        return store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)?.place ?: places.firstOrNull()
    }

    /**
     * Shown anew just after midnight: the calendar week, the alarm's weekday – the system's clock
     * keeps only the time and the date going. Not waking the phone: at its next waking is soon enough.
     */
    fun scheduleNewDay(context: Context) {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val zone = java.time.ZoneId.systemDefault()
        val next = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant().toEpochMilli()
        runCatching { am.set(android.app.AlarmManager.RTC, next, newDay(context)) }
    }

    fun cancelNewDay(context: Context) {
        context.getSystemService(android.app.AlarmManager::class.java)?.cancel(newDay(context))
    }

    private fun newDay(context: Context) = android.app.PendingIntent.getBroadcast(
        context, 0, Intent(context, NimbusWidget::class.java).setAction(NimbusWidget.ACTION_NEW_DAY),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun show(context: Context, ids: List<Int>, updating: Boolean = false) {
        val app = context.applicationContext
        val store = (app as? NimbusApp)?.container?.store ?: Store(app)
        val configs = WidgetConfigs(app)
        val settings = store.settings.first()
        val manager = AppWidgetManager.getInstance(app)
        val now = System.currentTimeMillis()
        for (id in ids) {
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) continue
            val config = configs.of(id)
            val place = placeOf(store, config)
            val data = place?.let { store.cachedWeather(it.id) }
            val weather = data?.let { WidgetWeather.of(it, now, config.pollen) }
            val build = { size: WidgetSize -> WidgetViews.build(app, id, config, size, weather, place?.id, settings.temperatureUnit, now, updating, settings.precipitationUnit) }
            val views = sizesOf(manager, id)?.let { sizes -> RemoteViews(sizes.associateWith { build(WidgetSize(it.width.toInt(), it.height.toInt())) }) }
                ?: build(sizeOf(manager, id))
            runCatching { manager.updateAppWidget(id, views) }
        }
        scheduleNewDay(app)
    }

    /**
     * The sizes the launcher shows the widget at (from Android 12 on: portrait, landscape, …) – a
     * picture is made for each and the launcher takes the one of the size it gives: nothing
     * planned for more room than there is. Null: not told, [sizeOf] stands in.
     */
    fun sizesOf(manager: AppWidgetManager, id: Int): List<android.util.SizeF>? {
        if (android.os.Build.VERSION.SDK_INT < 31) return null
        @Suppress("DEPRECATION")
        val sizes = runCatching { manager.getAppWidgetOptions(id).getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) }.getOrNull()
        return sizes?.filter { it.width > 0 && it.height > 0 }?.distinct()?.take(MAX_SIZES)?.takeIf { it.isNotEmpty() }
    }

    /** At most this many pictures in one update (each carries its bitmaps). */
    private const val MAX_SIZES = 4

    fun sizeOf(manager: AppWidgetManager, id: Int): WidgetSize {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return WidgetSize(if (w > 0) w else 250, if (h > 0) h else 110)
    }

    /**
     * What to fetch for a widget's place: the forecast, the pollen too when shown and out of date;
     * nothing while [cached] is fresh – unless [forced] (a tap on refresh): then the forecast anyway.
     */
    fun due(cached: WeatherData?, pollen: Boolean, now: Long, forced: Boolean = false): Set<DataPart> {
        if (cached == null) return setOf(DataPart.FORECAST, DataPart.POLLEN)
        val parts = mutableSetOf<DataPart>()
        if (forced || now - cached.fetchedAt(DataPart.FORECAST) >= FRESH_MS) parts += DataPart.FORECAST
        if (pollen && now - cached.fetchedAt(DataPart.POLLEN) >= Freshness.lifeMs(DataPart.POLLEN)) parts += DataPart.POLLEN
        // the pollen with the forecast: a load always brings the forecast along
        return if (parts.isEmpty()) parts else parts + DataPart.FORECAST
    }
}

/** Fetches the weather of every widget's place and shows it. */
class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val ids = Widgets.ids(ctx)
        if (ids.isEmpty()) return Result.success()
        val container = (ctx as? NimbusApp)?.container ?: return Result.success()
        val store = container.store
        val configs = WidgetConfigs(ctx)
        val settings = store.settings.first()
        val german = ctx.resources.configuration.locales[0].language == "de"
        val now = System.currentTimeMillis()
        // each place once, however many widgets show it
        val wanted = ids.mapNotNull { id -> val c = configs.of(id); Widgets.placeOf(store, c)?.let { it to c.pollen.isNotEmpty() } }
            .groupBy({ it.first.id }) { it }.values.map { list -> list.first().first to list.any { it.second } }
        for ((place, pollen) in wanted) {
            val cached = store.cachedWeather(place.id)
            val due = Widgets.due(cached, pollen, now, inputData.getBoolean(FORCED, false))
            if (due.isEmpty()) continue
            val t0 = android.os.SystemClock.elapsedRealtime()
            runCatching {
                val d = container.repository.load(place, settings, german, previous = cached, refresh = due)
                store.cacheWeather(d)
            }.onSuccess {
                ActivityLog.note("widget: ${place.name} fetched in ${(android.os.SystemClock.elapsedRealtime() - t0) / 1000} s")
            }.onFailure {
                ActivityLog.note("widget: ${place.name} not fetched – ${it.javaClass.simpleName}")
            }
        }
        Widgets.show(ctx, ids)
        return Result.success()
    }

    companion object {
        /** Input: fetched on a tap – the forecast anew however fresh. */
        const val FORCED = "forced"
    }
}
