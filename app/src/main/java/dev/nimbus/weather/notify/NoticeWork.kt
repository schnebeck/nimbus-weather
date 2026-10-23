/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/notify/NoticeWork.kt
 * The notices on the phone: their channels, the check in the background and what it remembers.
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

package dev.nimbus.weather.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nimbus.weather.MainActivity
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.ui.main.alertKindName
import dev.nimbus.weather.ui.main.precipNoticeText
import dev.nimbus.weather.ui.main.titleCase
import dev.nimbus.weather.util.ActivityLog
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.widget.WidgetViews
import dev.nimbus.weather.widget.Widgets
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** What the notices remember between the checks, and the place shown last in the app. */
class NoticeState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notices", Context.MODE_PRIVATE)

    fun memory(): NoticeMemory = prefs.getString(MEMORY, null)?.let {
        runCatching { JsonCodec.decodeFromString(NoticeMemory.serializer(), it) }.getOrNull()
    } ?: NoticeMemory()

    fun keep(m: NoticeMemory) { prefs.edit().putString(MEMORY, JsonCodec.encodeToString(NoticeMemory.serializer(), m)).apply() }

    /** The place the app showed last (its id): the notices are about it. */
    var shownPlace: String?
        get() = prefs.getString(PLACE, null)
        set(v) { prefs.edit().putString(PLACE, v).apply() }

    /** The place [shownPlace] names: a saved one, or "my location" where the app found the phone last. */
    suspend fun place(store: Store): Place? {
        val places = store.places.first()
        val id = shownPlace
        if (id != null && id != LocationProvider.CURRENT_LOCATION_ID) places.firstOrNull { it.id == id }?.let { return it }
        return store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)?.place ?: places.firstOrNull()
    }

    private companion object {
        const val MEMORY = "memory"
        const val PLACE = "place"
    }
}

/**
 * The notices on the phone: a channel each for warnings, gauges and precipitation – sound and
 * vibration of each as the user sets them in the system, do not disturb and bedtime as the
 * system keeps them. A notice goes by itself when its reason is over.
 */
object NoticePoster {
    const val WARNINGS = "warnings"
    const val GAUGES = "gauges"
    const val RAIN = "rain"

    /** Whether notices can be shown at all (the permission, from Android 13 on; not blocked for the app). */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The phone is set to do not disturb (or its bedtime): nothing gets through as usual. */
    fun quiet(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } ?: false

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun channel(id: String, name: Int, desc: Int) = NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(desc)
        }
        nm.createNotificationChannels(listOf(
            channel(WARNINGS, R.string.notice_channel_warnings, R.string.notice_channel_warnings_desc),
            channel(GAUGES, R.string.notice_channel_gauges, R.string.notice_channel_gauges_desc),
            channel(RAIN, R.string.notice_channel_rain, R.string.notice_channel_rain_desc),
        ))
    }

    /** The source written out: the gauge's operator by its full name. */
    fun operator(context: Context, p: GaugeProvider): String = context.getString(
        when (p) {
            GaugeProvider.PEGELONLINE -> R.string.operator_wsv
            GaugeProvider.NLWKN -> R.string.operator_nlwkn
            GaugeProvider.LANUK_NRW -> R.string.operator_lanuk
            GaugeProvider.LFULG_SACHSEN -> R.string.operator_lfulg
            GaugeProvider.HLNUG_HESSEN -> R.string.operator_hlnug
            GaugeProvider.LHP -> R.string.operator_lhp
        },
    )

    /** A gauge's level as told: "Meldestufe 1", the flood portal's class name where the state gives none. */
    fun stageName(context: Context, g: GaugeInfo, stage: Int): String =
        if ((g.alertStage ?: 0) > 0 || g.alertLevels.isNotEmpty()) context.getString(alertKindName(g.alertKind)) + " " + stage
        else g.lhpClassName ?: context.getString(R.string.notice_flood_class, stage)

    fun post(context: Context, place: Place, steps: List<NoticeStep>, now: Long, zone: ZoneId) {
        if (steps.isEmpty()) return
        channels(context)
        val nm = NotificationManagerCompat.from(context)
        val shown = context.getSystemService(NotificationManager::class.java)?.activeNotifications?.map { it.id }?.toSet().orEmpty()
        val tf = TimeFormat(zone.id, android.text.format.DateFormat.is24HourFormat(context))
        val open = PendingIntent.getActivity(
            context, place.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(WidgetViews.EXTRA_PLACE, place.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun show(key: String, channel: String, title: String, text: String, loud: Boolean, until: Long?) {
            val id = key.hashCode()
            // a quiet update only of a notice still shown: one put away stays away
            if (!loud && id !in shown) return
            val b = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notice)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(!loud)
                .setSilent(!loud)
                .setWhen(now)
            until?.let { if (it > now) b.setTimeoutAfter(it - now) }
            runCatching { nm.notify(id, b.build()) }
        }
        for (s in steps) when (s) {
            is NoticeStep.Warning -> {
                val a = s.alert
                val period = when {
                    a.onset != null && a.expires != null -> context.getString(R.string.alert_valid, tf.weekdayShort(a.onset) + " " + tf.time(a.onset), tf.weekdayShort(a.expires) + " " + tf.time(a.expires))
                    a.expires != null -> context.getString(R.string.alert_until, tf.weekdayShort(a.expires) + " " + tf.time(a.expires))
                    else -> null
                }
                val source = context.getString(if (Notices.isFlood(a)) R.string.notice_source_lhp else R.string.notice_source_dwd)
                show("w:" + a.id, WARNINGS, a.headline, listOfNotNull(period, place.name).joinToString(" · ") + "\n" + source, s.loud, a.expires)
            }
            is NoticeStep.WarningGone -> nm.cancel(("w:" + s.id).hashCode())
            is NoticeStep.Gauge -> {
                val g = s.gauge
                val where = context.getString(R.string.notice_gauge_where, titleCase(g.water), titleCase(g.name))
                val stage = stageName(context, g, s.stage)
                val title = context.getString(if (s.below) R.string.notice_gauge_below else R.string.notice_gauge_reached, where, stage)
                val level = g.level?.let { l ->
                    "${Math.round(l)} cm" + when (g.tendency) { 1 -> context.getString(R.string.notice_rising); -1 -> context.getString(R.string.notice_falling); else -> "" }
                }
                val text = listOfNotNull(level, context.getString(R.string.notice_source, operator(context, g.provider))).joinToString("\n")
                show("g:" + g.uuid, GAUGES, title, text, s.loud, if (s.below) now + Notices.GAUGE_CALM_MS else null)
            }
            is NoticeStep.GaugeGone -> nm.cancel(("g:" + s.uuid).hashCode())
            is NoticeStep.Rain -> {
                val title = precipNoticeText(context.resources, s.notice, now, tf)
                val text = context.getString(R.string.notice_rain_text, place.name, tf.time(s.until)) + "\n" + context.getString(R.string.notice_source_radar)
                show("r:" + place.id, RAIN, title, text, loud = true, until = s.until)
            }
        }
    }
}

/** The check in the background: every quarter of an hour with the precipitation notice, else every half hour. */
object NoticeWork {
    private const val NAME = "notices"
    /** The forecast is fetched anew when older than this: the radar's nowcast moves on. */
    private const val FRESH_MS = 10 * 60_000L

    fun schedule(context: Context, settings: Settings) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!settings.notices) { wm.cancelUniqueWork(NAME); return }
        val minutes = if (settings.noticeRain) 15L else 30L
        val request = PeriodicWorkRequestBuilder<NoticeWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** What to fetch for the check: the forecast (radar included) when not fresh, the gauges and flood warnings when asked for and out of date. */
    fun due(settings: Settings, fetched: (DataPart) -> Long?, now: Long): Set<DataPart> {
        val parts = mutableSetOf<DataPart>()
        if ((fetched(DataPart.FORECAST) ?: 0L) < now - FRESH_MS) parts += DataPart.FORECAST
        if (settings.noticeGauges && (fetched(DataPart.GAUGES) ?: 0L) < now - Freshness.lifeMs(DataPart.GAUGES)) parts += DataPart.GAUGES
        if (settings.noticeFloods && (fetched(DataPart.FLOOD) ?: 0L) < now - Freshness.lifeMs(DataPart.FLOOD)) parts += DataPart.FLOOD
        return if (parts.isEmpty()) parts else parts + DataPart.FORECAST
    }
}

class NoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val container = (ctx as? NimbusApp)?.container ?: return Result.success()
        val store = container.store
        val settings = store.settings.first()
        if (!settings.notices || !NoticePoster.allowed(ctx)) return Result.success()
        val state = NoticeState(ctx)
        val place = state.place(store) ?: return Result.success()
        val german = ctx.resources.configuration.locales[0].language == "de"
        val now = System.currentTimeMillis()
        val cached = store.cachedWeather(place.id)
        val due = NoticeWork.due(settings, { cached?.fetchedAt(it)?.takeIf { t -> t > 0 } }, now)
        val data = if (due.isEmpty()) cached else runCatching {
            container.repository.load(place, settings, german, previous = cached, refresh = due).also { store.cacheWeather(it) }
        }.onFailure { ActivityLog.note("notices: ${place.name} not fetched – ${it.javaClass.simpleName}") }.getOrNull() ?: cached
        data ?: return Result.success()
        val zone = runCatching { ZoneId.of(data.timezone) }.getOrDefault(ZoneId.systemDefault())
        var memory = state.memory()
        val steps = mutableListOf<NoticeStep>()

        // warnings: the DWD's from the level chosen, the states' flood warnings when asked for
        val alerts = data.alerts.filter { if (Notices.isFlood(it)) settings.noticeFloods else settings.noticeWarnings }
        Notices.warnings(alerts, settings.warningLevel, floods = true, memory.warned).let { (s, m) -> steps += s; memory = memory.copy(warned = m) }

        // gauges: those shown for the place and the ones with the star (asked for near where they were starred)
        if (settings.noticeGauges) {
            val near = data.gauges
            val watched = settings.watchedGauges.filter { w -> near.none { it.uuid == w.uuid } }
            val far = watched.groupBy { "%.1f,%.1f".format(java.util.Locale.ROOT, it.latitude, it.longitude) }.values.flatMap { group ->
                runCatching { container.gauges.nearby(group.first().latitude, group.first().longitude, now) }.getOrDefault(emptyList())
                    .filter { g -> group.any { it.uuid == g.uuid } }
            }
            Notices.gauges(near + far, settings.gaugeStage, memory.gauges, now).let { (s, m) -> steps += s; memory = memory.copy(gauges = m) }
        } else {
            steps += memory.gauges.keys.map { NoticeStep.GaugeGone(it) }
            memory = memory.copy(gauges = emptyMap())
        }

        // precipitation coming after hours without
        if (settings.noticeRain) {
            val recent = container.spot.radarSteps(place.latitude, place.longitude, now - Notices.DRY_MS, now)
            val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            Notices.rain(data.minutely, data.hourly, data.current.condition, recent, memory.rainTold, now, dayStart, NoticePoster.quiet(ctx))?.let { r ->
                steps += r
                memory = memory.copy(rainTold = (memory.rainTold + now).takeLast(10))
            }
        }

        NoticePoster.post(ctx, place, steps, now, zone)
        state.keep(memory)
        if (steps.isNotEmpty()) ActivityLog.note("notices: ${place.name} – ${steps.size} step(s)")
        // the widgets show what was just fetched
        if (due.isNotEmpty()) Widgets.ids(ctx).takeIf { it.isNotEmpty() }?.let { Widgets.show(ctx, it) }
        return Result.success()
    }
}
