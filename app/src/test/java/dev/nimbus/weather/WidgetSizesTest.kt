/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetSizesTest.kt
 * The widget at the launcher's usual sizes, light and dark: nothing cut off anywhere; the
 * pictures land in build/widget-previews for a look at all of them.
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class WidgetSizesTest {
    private val h = 3_600_000L
    private val q = 15 * 60_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L // 10:20
    private val app get() = RuntimeEnvironment.getApplication()

    /**
     * The weather of [look]: "rain" – showers now (60 %), rain for the first half hour and again
     * from 11:15, sun and clouds by turns; "sunny" – clear by day, all sunshine, dry; "dull" –
     * overcast by day, dry, no sunshine; "night" – clear by night, dry (at Honolulu: night there at
     * 10:20 UTC – the widget takes day and night from the sun). Birch, grass and hazel.
     */
    private fun data(look: String = "rain"): WeatherData {
        val night = look == "night"
        val condition = when (look) { "rain" -> Condition.SHOWERS; "dull" -> Condition.CLOUDY; else -> Condition.CLEAR }
        val hourly = (1..48).map { k ->
            HourlyPoint(day + k * h, 12.0, condition = condition, isDay = !night, precipitation = if (look == "rain") 0.4 else 0.0,
                precipitationProbability = if (look == "rain") 60.0 else 5.0, sunshine = if (look == "sunny") 60.0 else if (look == "rain") 20.0 else 0.0)
        }
        val daily = (0..2).map { DailyPoint(day + it * 24 * h, condition, 14.0, 6.0) }
        val quarter = now / q * q                                        // 10:15, the running quarter's start
        val minutely = (-4..12).map { k ->
            val at = quarter + k * q
            val rain = look == "rain" && (k in 0..1 || k >= 4)
            val sun = when (look) { "sunny" -> 15.0; "rain" -> listOf(15.0, 12.0, 9.0, 11.0, 6.0, 3.0, 8.0)[k.coerceIn(0, 6)]; else -> 0.0 }
            MinutelyPoint(at, if (rain) 0.2 + 0.25 * (k % 3) else 0.0, 12.0, sunshine = sun)
        }
        val cur = CurrentWeather(now, 12.4, 11.0, condition, !night, 70.0, 7.0, 1015.0, 12.0, 25.0, 240.0, 60.0, 20000.0, 2.0, 0.2)
        val pollen = PollenForecast(
            PollenSourceKind.DWD, "Niedersachsen",
            listOf(PollenDay(day, mapOf(PollenType.BIRCH to 2.0f, PollenType.GRASS to 0.5f, PollenType.HAZEL to 1.5f))),
        )
        val place = if (night) Place("p", "Honolulu", latitude = 21.31, longitude = -157.86, isCurrentLocation = true)
            else Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320, isCurrentLocation = true)
        return WeatherData(place, "UTC", 0, cur, hourly, daily, minutely, pollen = pollen, sources = emptyList(), fetchedAt = now - 5 * 60_000L)
    }

    /** Cells as a phone's launcher gives them (about 80 × 100 dp each), and a few in between. */
    private val sizes = listOf(
        "4x1" to WidgetSize(320, 70), "4x2" to WidgetSize(320, 180), "4x3" to WidgetSize(320, 280),
        "4x4" to WidgetSize(320, 380), "3x2" to WidgetSize(240, 180), "5x2" to WidgetSize(400, 180),
        "2x2" to WidgetSize(160, 180), "4x2s" to WidgetSize(250, 150),
    )

    @Test fun nothingCutOffLight() = all("light")

    @Config(qualifiers = "+night")
    @Test fun nothingCutOffDark() = all("dark")

    private fun all(look: String) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        val am = app.getSystemService(android.app.AlarmManager::class.java)
        am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(day + 30 * h, null), android.app.PendingIntent.getBroadcast(app, 0, Intent("alarm"), android.app.PendingIntent.FLAG_IMMUTABLE))
        val config = WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS, PollenType.HAZEL))
        val out = File("build/widget-previews").apply { mkdirs() }
        val problems = mutableListOf<String>()
        for ((name, size) in sizes) {
            val v = render(config, size, data())
            problems += cut(v).map { "$name: $it" }
            v.picture().compress(Bitmap.CompressFormat.PNG, 100, File(out, "widget_${name}_$look.png").outputStream())
        }
        // the weather by turns: sunny, dull, night – the chart only with something to draw
        for (weather in listOf("sunny", "dull", "night")) for ((name, size) in listOf("4x2" to WidgetSize(320, 180), "4x3" to WidgetSize(320, 280), "4x2s" to WidgetSize(250, 150))) {
            val v = render(config, size, data(weather))
            problems += cut(v).map { "$name $weather: $it" }
            v.picture().compress(Bitmap.CompressFormat.PNG, 100, File(out, "widget_${name}_${weather}_$look.png").outputStream())
        }
        // the date in figures with its week
        for ((name, size) in listOf("4x2kw" to WidgetSize(320, 180), "4x1kw" to WidgetSize(320, 70))) {
            val v = render(config.copy(dateWithWeek = true), size, data())
            problems += cut(v).map { "$name: $it" }
            v.picture().compress(Bitmap.CompressFormat.PNG, 100, File(out, "widget_${name}_$look.png").outputStream())
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Every view shown lies within the widget and each text within its view: not shortened, not cut at the bottom or the side. */
    private fun cut(root: ViewGroup): List<String> {
        val found = mutableListOf<String>()
        val loc = IntArray(2); val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            v.getLocationInWindow(loc)
            val x = loc[0] - rootLoc[0]; val y = loc[1] - rootLoc[1]
            val id = runCatching { app.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName)
            if (v.width > 0 && v.height > 0 && (x < 0 || y < 0 || x + v.width > root.width + 1 || y + v.height > root.height + 1)) found += "$id outside (${x},${y} ${v.width}×${v.height})"
            if (v is TextView && v.layout != null && v.layout.lineCount > 0 && v.layout.getEllipsisCount(v.layout.lineCount - 1) > 0) found += "$id shortened: ${v.text}"
            if (v is TextView && v.layout != null && v.layout.height > v.height + 1) found += "$id taller than its view"
            // the letters' width, a line's break not counted
            if (v is TextView && v.layout != null && v.text.isNotEmpty() && (0 until v.layout.lineCount).any { v.layout.getLineMax(it) > v.width - v.totalPaddingLeft - v.totalPaddingRight + 1 }) found += "$id cut at the side: ${v.text}"
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return found
    }

    private fun render(config: WidgetConfig, size: WidgetSize, d: WeatherData): ViewGroup {
        val weather = WidgetWeather.of(d, now, config.pollen)
        val views = WidgetViews.build(app, 1, config, size, weather, d.place.id, TemperatureUnit.CELSIUS, now)
        val frame = FrameLayout(app)
        frame.addView(views.apply(app, frame))
        fun lay() {
            val density = app.resources.displayMetrics.density
            val w = (size.widthDp * density).toInt(); val hh = (size.heightDp * density).toInt()
            frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, w, hh)
        }
        lay()
        // the system's clock at a fixed time and date (a quoted pattern shows as it is)
        for (id in listOf(R.id.widget_clock, R.id.widget_date)) frame.findViewById<TextClock>(id).apply {
            val text = "'" + java.text.SimpleDateFormat(format24Hour.toString(), app.resources.configuration.locales[0])
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now)) + "'"
            format24Hour = text; format12Hour = text
        }
        lay()
        return frame
    }

    /** On a grey wallpaper, as the launcher shows it. */
    private fun ViewGroup.picture(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF6E7F8F.toInt())
        draw(c)
        return bmp
    }
}
