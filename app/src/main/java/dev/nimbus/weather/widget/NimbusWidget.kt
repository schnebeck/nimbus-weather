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
        val frames = WidgetFrames(context)
        ids.forEach { configs.remove(it); frames.remove(it) }
    }

    override fun onEnabled(context: Context) = Widgets.schedule(context)

    override fun onDisabled(context: Context) {
        Widgets.unschedule(context)
        Widgets.cancelNewDay(context)
        Widgets.cancelSunTurn(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                async { Widgets.show(context, listOf(id), updating = true) }
                Widgets.fetchNow(context, forced = true)
            }
            // the alarm's time, the date's language, the hour of the place, a new day (its week), the sun
            // risen or set (the symbol by day or night): shown anew
            ACTION_NEW_DAY, ACTION_SUN_TURN, android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED, Intent.ACTION_LOCALE_CHANGED,
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
        const val ACTION_SUN_TURN = "dev.nimbus.weather.widget.SUN_TURN"
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
    const val SUN_TURN_WINDOW_MS = 10 * 60_000L
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

    /** The widgets following the app ([WidgetConfig.followApp]) shown anew: another place was looked at. */
    suspend fun showFollowing(context: Context) {
        val configs = WidgetConfigs(context)
        val ids = ids(context).filter { configs.of(it).followApp }
        if (ids.isNotEmpty()) show(context, ids)
    }

    /** Every widget shown anew from the stored weather – after the app loaded a place, say. */
    suspend fun showAll(context: Context) {
        val ids = ids(context)
        if (ids.isNotEmpty()) show(context, ids)
    }

    /**
     * The place of a widget: the one chosen – "my location" ([WidgetConfig.placeId] null) is
     * where the app found the phone last; following the app, the place last looked at there;
     * never located: the first place of the list.
     */
    suspend fun placeOf(store: Store, config: WidgetConfig): Place? {
        val places = store.places.first()
        if (config.followApp) store.lastPlace.first()?.let { id ->
            places.firstOrNull { it.id == id }?.let { return it }
            if (id == LocationProvider.CURRENT_LOCATION_ID) store.cachedWeather(id)?.place?.let { return it }
        }
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

    /**
     * Shown anew a minute after the next sunrise or sunset at any of [places] after [now] – the
     * symbol turns to night or day then, not with the next fetch. Within [SUN_TURN_WINDOW_MS]
     * after that: a plain inexact alarm may come up to an hour late, a window of 10 minutes is the
     * least that needs no exact alarm (and no permission for one).
     */
    fun scheduleSunTurn(context: Context, places: List<Place>, now: Long) {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val next = places.mapNotNull { dev.nimbus.weather.util.Moon.nextSunTurn(now, it.latitude, it.longitude) }.minOrNull()
        if (next == null) { am.cancel(sunTurn(context)); return }
        runCatching { am.setWindow(android.app.AlarmManager.RTC, next + 60_000L, SUN_TURN_WINDOW_MS, sunTurn(context)) }
    }

    fun cancelSunTurn(context: Context) {
        context.getSystemService(android.app.AlarmManager::class.java)?.cancel(sunTurn(context))
    }

    private fun sunTurn(context: Context) = android.app.PendingIntent.getBroadcast(
        context, 1, Intent(context, NimbusWidget::class.java).setAction(NimbusWidget.ACTION_SUN_TURN),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    private fun newDay(context: Context) = android.app.PendingIntent.getBroadcast(
        context, 0, Intent(context, NimbusWidget::class.java).setAction(NimbusWidget.ACTION_NEW_DAY),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun show(context: Context, ids: List<Int>, updating: Boolean = false) {
        val app = context.applicationContext
        val store = (app as? NimbusApp)?.container?.store ?: Store(app)
        val configs = WidgetConfigs(app)
        val frames = WidgetFrames(app)
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
            val sizes = sizesOf(manager, id)
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val figures = figures(sizes, options)
            noteSizes(id, figures)
            val offers = offers(sizes, options?.let { o ->
                Bounds(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
            })
            val views = if (android.os.Build.VERSION.SDK_INT >= 31 && offers.isNotEmpty()) {
                // each picture with its pixel: the one the launcher fetches tells the frame (WidgetFrames)
                val search = frames.next(id, figures, offers)
                RemoteViews(
                    search.offered.associate { o ->
                        android.util.SizeF(o.widthDp.toFloat(), o.heightDp.toFloat()) to
                            build(o).apply { setImageViewUri(dev.nimbus.weather.R.id.widget_probe, WidgetProbe.uri(app, id, search.round, o)) }
                    },
                )
            } else build(sizeOf(manager, id))
            runCatching { manager.updateAppWidget(id, views) }
        }
        scheduleNewDay(app)
        // the places of all widgets, not only of those just shown: one alarm stands for them all
        scheduleSunTurn(app, ids(app).mapNotNull { placeOf(store, configs.of(it)) }, now)
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

    /** At most this many pictures in one update (each carries its bitmaps; the sizes reported come after those from the bounds). */
    private const val MAX_SIZES = 6

    /** The steps of height between the least and the largest ([offers]). */
    private const val HEIGHT_STEPS = 4

    /**
     * The size a picture is planned for and offered at: a little below the size the launcher
     * reports. The system takes the largest picture whose size fits the frame, the smallest when
     * none fits – and a launcher whose frame keeps a margin inside it shows the widget a few dp
     * smaller than it reports: a picture offered at the full size fits nowhere, the smallest (for
     * the other orientation) came, and the frame stayed half empty.
     */
    fun offered(reported: android.util.SizeF): WidgetSize = WidgetSize(
        (reported.width - maxOf(OFFER_MARGIN_W_DP, reported.width * OFFER_SHARE_W)).toInt().coerceAtLeast(40),
        (reported.height - maxOf(OFFER_MARGIN_H_DP, reported.height * OFFER_SHARE_H)).toInt().coerceAtLeast(40),
    )

    /** The launcher's least and largest size of the widget (dp; 0: not told). */
    data class Bounds(val minWidth: Int, val minHeight: Int, val maxWidth: Int, val maxHeight: Int)

    /**
     * The sizes a picture is made for: those the launcher reports ([offered]), and from its least
     * and largest size ([bounds]) the least one and one between – the system takes the closest
     * that fits the frame, the smallest when none fits. A launcher may report sizes well above
     * its frame (one showed a widget of 411 × 260 dp and none of its sizes fitted): with the least
     * size among them, one always fits, and the heights between keep the chart where there is room.
     * Those from the bounds first (they hold for any launcher), then the reported ones, the
     * largest first – at most [MAX_SIZES].
     */
    fun offers(sizes: List<android.util.SizeF>?, bounds: Bounds?): List<WidgetSize> {
        val all = mutableListOf<WidgetSize>()
        if (bounds != null && bounds.minWidth > 0 && bounds.minHeight > 0) {
            // upright: the least width, the height in steps from the least to the largest
            val top = maxOf(bounds.maxHeight, bounds.minHeight)
            for (k in 0..HEIGHT_STEPS) all += WidgetSize(bounds.minWidth, bounds.minHeight + (top - bounds.minHeight) * k / HEIGHT_STEPS)
            // sideways: the largest width, the least height
            if (bounds.maxWidth > bounds.minWidth) all += WidgetSize(bounds.maxWidth, bounds.minHeight)
        }
        sizes?.map { offered(it) }?.sortedByDescending { it.widthDp * it.heightDp }?.let { all += it }
        return all.filter { it.widthDp >= 40 && it.heightDp >= 40 }.distinct().take(MAX_SIZES)
    }

    private const val OFFER_MARGIN_W_DP = 16f
    private const val OFFER_SHARE_W = 0.06f
    private const val OFFER_MARGIN_H_DP = 24f
    private const val OFFER_SHARE_H = 0.10f

    /** What each widget was last told of its sizes – noted when it changes, for a look at a launcher's figures. */
    private val noted = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** The sizes the launcher reports, as text – for the log, and the frame searched anew when they change. */
    fun figures(sizes: List<android.util.SizeF>?, o: Bundle?): String =
        "sizes ${sizes?.joinToString { "${it.width.toInt()}×${it.height.toInt()}" } ?: "none"}; " +
            "min ${o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}×${o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}, " +
            "max ${o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)}×${o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} dp"

    private fun noteSizes(id: Int, text: String) {
        if (noted.put(id, text) != text) {
            // in the system's log as well: a launcher's figures can be read off the phone without the activity log on
            android.util.Log.i("NimbusWidget", "widget $id: $text")
            ActivityLog.note("widget $id: $text")
        }
    }

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
                val d = container.repository.load(place, settings, previous = cached, refresh = due)
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
