/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetOlderAndroidTest.kt
 * The widget on Android 8 to 11: every picture it sends the launcher applies there.
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

import android.content.Intent
import android.widget.FrameLayout
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.widget.WidgetConfig
import dev.nimbus.weather.widget.WidgetSize
import dev.nimbus.weather.widget.WidgetViews
import dev.nimbus.weather.widget.WidgetWeather
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Up to Android 11 the launcher calls on a view of the widget only what that Android version
 * allows a widget (a TextView's gravity, for one, only from Android 12 on); anything else and it
 * shows nothing but its "problem loading the widget". So every combination is built and applied
 * here as the launcher of the oldest Android versions the app runs on does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 28, 30], application = android.app.Application::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class WidgetOlderAndroidTest {
    private val h = 3_600_000L
    private val q = 15 * 60_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L // 10:20
    private val app get() = RuntimeEnvironment.getApplication()

    private fun data(rainFrom: Int?): WeatherData {
        val place = Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320)
        val hourly = (1..48).map { k -> HourlyPoint(day + k * h, 12.0, condition = Condition.CLOUDY, isDay = k % 24 in 7..18, precipitation = 0.0) }
        val daily = (0..2).map { DailyPoint(day + it * 24 * h, Condition.CLOUDY, 14.0, 6.0) }
        val first = (now / q + 1) * q
        val minutely = (-4..16).map { k ->
            val end = first + k * q
            val wet = rainFrom != null && end - now >= rainFrom * 60_000L && end - now < (rainFrom + 60) * 60_000L
            MinutelyPoint(end, if (wet) 0.3 else 0.0, 12.0, sunshine = 8.0)
        }
        val cur = CurrentWeather(now, 12.4, 11.0, Condition.PARTLY_CLOUDY, true, 70.0, 7.0, 1015.0, 12.0, 25.0, 240.0, 60.0, 20000.0, 2.0, 0.0)
        val pollen = PollenForecast(
            PollenSourceKind.DWD, "Niedersachsen",
            listOf(PollenDay(day, mapOf(PollenType.BIRCH to 2.0f, PollenType.GRASS to 0.5f, PollenType.HAZEL to 0f))),
        )
        return WeatherData(place, "UTC", 0, cur, hourly, daily, minutely, pollen = pollen, sources = emptyList(), fetchedAt = now - 5 * 60_000L)
    }

    private fun alarm(on: Boolean) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        val am = app.getSystemService(android.app.AlarmManager::class.java)
        val pi = android.app.PendingIntent.getBroadcast(app, 0, Intent("alarm"), android.app.PendingIntent.FLAG_IMMUTABLE)
        if (on) am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(now + 4 * 24 * h, null), pi) else am.cancel(pi)
    }

    /** Every part on or off, every size from the smallest to a tablet's, with weather and without: each applies. */
    @Test fun everyPictureApplies() {
        val sizes = listOf(110 to 40, 180 to 60, 250 to 110, 291 to 133, 320 to 200, 400 to 190, 454 to 180, 560 to 300)
        val configs = listOf(
            WidgetConfig(),
            WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS, PollenType.HAZEL)),
            WidgetConfig(clock = false, date = false, pollen = listOf(PollenType.BIRCH)),
            WidgetConfig(date = false, alarm = false, rain = false),
            WidgetConfig(clock = false, weather = false, dateWithWeek = true),
            WidgetConfig(weather = false, rain = false, pollen = listOf(PollenType.GRASS)),
        )
        var applied = 0
        for (withAlarm in listOf(false, true)) {
            alarm(withAlarm)
            for (config in configs) for ((w, hh) in sizes) for (rainFrom in listOf(null, 40)) for (withData in listOf(true, false)) {
                val weather = if (withData) WidgetWeather.of(data(rainFrom), now, config.pollen) else null
                val views = WidgetViews.build(app, 1, config, WidgetSize(w, hh), weather, "p", TemperatureUnit.CELSIUS, now)
                val frame = FrameLayout(app)
                val v = runCatching { views.apply(app, frame) }
                    .getOrElse { throw AssertionError("alarm $withAlarm, $config at $w×$hh, rain $rainFrom, data $withData: ${it.cause ?: it}", it) }
                frame.addView(v)
                applied++
            }
        }
        assertEquals(2 * configs.size * sizes.size * 2 * 2, applied)
    }
}
