/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetTest.kt
 * The home-screen widget: the page's values and words, the rain of the next 90 minutes, the
 * chosen pollen, its parts as chosen and as they fit, the taps, its place, its setup and its look.
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
import android.provider.AlarmClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.ui.main.precipNoticeText
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.widget.WidgetConfig
import dev.nimbus.weather.widget.WidgetConfigs
import dev.nimbus.weather.widget.WidgetLayout
import dev.nimbus.weather.widget.WidgetSetup
import dev.nimbus.weather.widget.WidgetSize
import dev.nimbus.weather.widget.WidgetViews
import dev.nimbus.weather.widget.WidgetWeather
import dev.nimbus.weather.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class WidgetTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val q = 15 * 60_000L
    private val day = 1_790_812_800_000L          // 2026-10-01 00:00 UTC
    private val now = day + 10 * h + 20 * 60_000L // 10:20
    private val tf = TimeFormat("UTC", true, java.util.Locale.GERMANY)
    private val app get() = RuntimeEnvironment.getApplication()

    /** Hannover at 10:20: 12.4 °C, 14/6 for the day, half sunny; [rainFrom] (minutes ahead, quarter ends) with [mm] per quarter for an hour. */
    private fun data(rainFrom: Int? = null, mm: Double = 0.3, place: Place = Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320)): WeatherData {
        val hourly = (1..48).map { k -> HourlyPoint(day + k * h, 12.0, condition = Condition.CLOUDY, isDay = k % 24 in 7..18, precipitation = 0.0) }
        val daily = (0..2).map { DailyPoint(day + it * 24 * h, Condition.CLOUDY, 14.0, 6.0) }
        val first = (now / q + 1) * q                                   // 10:30, the end of the running quarter
        val minutely = (-4..16).map { k ->
            val end = first + k * q
            val wet = rainFrom != null && end - now >= rainFrom * 60_000L && end - now < (rainFrom + 60) * 60_000L
            MinutelyPoint(end, if (wet) mm else 0.0, 12.0, sunshine = 8.0)
        }
        val cur = CurrentWeather(now, 12.4, 11.0, Condition.PARTLY_CLOUDY, true, 70.0, 7.0, 1015.0, 12.0, 25.0, 240.0, 60.0, 20000.0, 2.0, 0.0)
        val pollen = PollenForecast(
            PollenSourceKind.DWD, "Niedersachsen",
            listOf(PollenDay(day, mapOf(PollenType.BIRCH to 2.0f, PollenType.GRASS to 0.5f, PollenType.HAZEL to 0f))),
        )
        return WeatherData(place, "UTC", 0, cur, hourly, daily, minutely, pollen = pollen, sources = emptyList(), fetchedAt = now - 5 * 60_000L)
    }

    /** The widget's values are the head of the weather page's: the temperature now, the symbol, the day's high and low. */
    @Test fun theWeatherAsTheHeadOfThePage() {
        val w = WidgetWeather.of(data(), now, emptyList())
        assertEquals(12.4, w.temperature, 0.0)
        assertEquals(Condition.PARTLY_CLOUDY, w.condition)
        assertEquals(14.0, w.max!!, 0.0)
        assertEquals(6.0, w.min!!, 0.0)
        assertEquals("Hannover", w.place)
        assertFalse(w.myLocation)
        assertEquals(now - 5 * 60_000L, w.asOf)
    }

    /**
     * The next 90 minutes in quarter hours on the clock's quarters; rain within them worded as the precipitation card
     * words it – rain after them is not announced (the bars would not show it).
     */
    @Test fun theRainOfTheNext90Minutes() {
        val soon = data(rainFrom = 40)
        val rain = WidgetWeather.of(soon, now, emptyList()).rain!!
        // on the clock's quarters: from the running one's start (10:15), six of them
        assertEquals(day + 10 * h + 15 * 60_000L, rain.from)
        assertEquals(rain.from + 90 * 60_000L, rain.to)
        assertEquals(now, rain.now)
        assertEquals(6, rain.quarters.size)
        // the quarter hours the notice looks at: the running one first, the last one reaching the end
        assertTrue("quarters ${rain.quarters.map { tf.time(it.at) }}", rain.quarters.first().at in now - q..now && rain.quarters.last().at + q >= rain.to)
        assertEquals(Insights.nowcastPoints(soon.minutely, now).filter { it.time >= rain.from && it.time < rain.to }.map { it.time }, rain.quarters.map { it.at })
        assertTrue(rain.quarters.any { it.mmPerHour > 0.0 })
        val card = Insights.precipNotice(soon.minutely, soon.hourly, soon.current.condition, now, false)!!
        assertEquals(precipNoticeText(app.resources, card, now, tf), precipNoticeText(app.resources, rain.notice!!, now, tf))

        val later = WidgetWeather.of(data(rainFrom = 100), now, emptyList()).rain!!
        assertNull(later.notice)
        assertTrue(later.quarters.all { it.mmPerHour == 0.0 })
        val views = render(WidgetConfig(), WidgetSize(320, 200), data(rainFrom = 100))
        assertEquals("Kein Niederschlag bis 11:45", views.text(R.id.widget_rain_text))
    }

    /** Today's level of each chosen type, in the order chosen; a type the forecast does not give is left out. */
    @Test fun theChosenPollenOfToday() {
        val w = WidgetWeather.of(data(), now, listOf(PollenType.BIRCH, PollenType.RAGWEED, PollenType.GRASS))
        assertEquals(listOf(PollenType.BIRCH to 2.0f, PollenType.GRASS to 0.5f), w.pollen)
        // shown as the pollen card's gauge: three segments, birch "medium" two of them filled in its colour
        val gauge = WidgetViews.pollenGauge(2.0f, 300, 30, 1f)
        val medium = 0xFFF7C948.toInt()
        assertEquals(medium, gauge.getPixel(50, 15))
        assertEquals(medium, gauge.getPixel(150, 15))
        assertTrue(gauge.getPixel(250, 15) != medium)
        val v = render(WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS)), WidgetSize(320, 300), data())
        assertEquals("Birke", v.text(R.id.widget_pollen_0_name))
        assertEquals("Gräser", v.text(R.id.widget_pollen_1_name))
        // side by side over the whole width: one row, its third place kept empty
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_pollen_row_0).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_pollen_row_1).visibility)
        assertEquals(View.INVISIBLE, v.findViewById<View>(R.id.widget_pollen_2).visibility)
        assertTrue(WidgetWeather.of(data(), now, emptyList()).pollen.isEmpty())
    }

    /**
     * Where the gauges do not fit (two rows of cells), the pollen as points in the line beside the
     * alarm: only those that fly noteworthily (yellow, red – green is the rule, not news), in the
     * pollen card's colour of the level; none of them: the highest level in words.
     */
    @Test fun thePollenAsPointsWhereGaugesDoNotFit() {
        val all = listOf(PollenType.BIRCH, PollenType.GRASS, PollenType.HAZEL)
        val w = WidgetWeather.of(data(), now, all)
        val l = WidgetLayout.of(WidgetConfig(pollen = all), WidgetSize(320, 180), alarm = "06:00", weather = w, pollenText = WidgetViews.pollenDots(app, w.pollen).toString())
        assertTrue(l.pollenDots && l.pollenRows == 0 && l.alarm && l.chart)
        // birch "medium" yellow; grass "none to low" and hazel "none" left out
        val dots = WidgetViews.pollenDots(app, w.pollen) as android.text.Spanned
        assertEquals("● Birke", dots.toString().replace('\u00A0', ' '))
        val colors = dots.getSpans(0, dots.length, android.text.style.ForegroundColorSpan::class.java).map { it.foregroundColor }
        assertEquals(listOf(0xFFF7C948.toInt()), colors)
        val red = WidgetViews.pollenDots(app, listOf(PollenType.BIRCH to 3f, PollenType.GRASS to 1.5f, PollenType.ALDER to 1f)) as android.text.Spanned
        assertEquals("● Birke  ● Gräser", red.toString().replace('\u00A0', ' '))
        assertEquals(0xFFFF5B36.toInt(), red.getSpans(0, red.length, android.text.style.ForegroundColorSpan::class.java).first().foregroundColor)
        assertEquals("Pollen: keine bis gering", WidgetViews.pollenDots(app, listOf(PollenType.BIRCH to 0.5f, PollenType.GRASS to 0f)).toString())
        // taller: the gauges with their half steps, every type chosen
        val tall = WidgetLayout.of(WidgetConfig(pollen = all), WidgetSize(320, 300), alarm = "06:00", weather = w, pollenText = dots.toString())
        assertTrue(!tall.pollenDots && tall.pollenRows == 1)
    }

    /**
     * Nothing to draw in the 90 minutes (no sunshine, dry): no empty chart – its line in its place,
     * by day saying the sun stays away too. Precipitation coming in two rows of cells: its line
     * beside the alarm, the pollen give way.
     */
    @Test fun theChartOnlyWithSomethingToDraw() {
        val dull = data(rainFrom = 100).let { d -> d.copy(minutely = d.minutely.map { it.copy(sunshine = 0.0) }) }
        val v = render(WidgetConfig(), WidgetSize(320, 200), dull)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_chart).visibility)
        assertEquals("Kein Niederschlag, keine Sonne bis 11:45", v.text(R.id.widget_rain_text))
        val night = dull.copy(current = dull.current.copy(isDay = false))
        assertEquals("Kein Niederschlag bis 11:45", render(WidgetConfig(), WidgetSize(320, 200), night).text(R.id.widget_rain_text))
        // precipitation coming, two rows of cells: the line beside the alarm, the chart below
        alarmAt(day + 30 * h)
        val all = listOf(PollenType.BIRCH, PollenType.GRASS)
        val rain = render(WidgetConfig(pollen = all), WidgetSize(320, 180), data(rainFrom = 40))
        assertEquals(View.VISIBLE, rain.findViewById<View>(R.id.widget_chart).visibility)
        assertEquals(View.VISIBLE, rain.findViewById<View>(R.id.widget_alarm_line).visibility)
        assertEquals("Leichter Regen ab etwa 11:00", rain.text(R.id.widget_pollen_dots))
    }

    /**
     * The month always written out, the weekday too where there is the room ("Donnerstag,
     * 1. Oktober"); or, as chosen, the date in figures with the weekday short and the calendar week
     * ("Do., 01.10.2026 KW 40") – shown anew just after midnight, the week too.
     */
    @Test fun theDateWrittenOutOrInFiguresWithItsWeek() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        val written = render(WidgetConfig(), WidgetSize(320, 200), data())
        assertTrue(written.findViewById<TextClock>(R.id.widget_date).format24Hour.toString().contains("MMMM"))
        // the weekday written out where the place leaves the room (four cells wide)
        assertTrue(written.findViewById<TextClock>(R.id.widget_date).format24Hour.toString().contains("EEEE"))
        assertEquals(View.GONE, written.findViewById<View>(R.id.widget_week).visibility)
        val figures = render(WidgetConfig(dateWithWeek = true), WidgetSize(320, 200), data())
        assertEquals("EEE, dd.MM.y", figures.findViewById<TextClock>(R.id.widget_date).format24Hour.toString().replace("yyyy", "y"))
        // 1 October 2026, a Thursday: ISO week 40
        assertEquals("KW 40", figures.text(R.id.widget_week))
        assertEquals(53, WidgetViews.weekOf(java.time.LocalDate.of(2026, 12, 31).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), java.util.Locale.GERMANY))
        Widgets.scheduleNewDay(app)
        val alarm = shadowOf(app.getSystemService(android.app.AlarmManager::class.java)).peekNextScheduledAlarm()!!
        assertEquals("shown anew just after midnight", 60_000L, alarm.triggerAtMs % (24 * h))
    }

    /**
     * The calendar week over many turns of the year, against the rule worked out on its own: weeks
     * start on Monday, week 1 is the one with the year's first Thursday – so the last days of
     * December can be week 1 and the first days of January week 52 or 53. Every day 1970–2100.
     */
    @Test fun theCalendarWeekOverManyTurnsOfTheYear() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        fun at(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        fun week(y: Int, m: Int, d: Int) = WidgetViews.weekOf(at(y, m, d), java.util.Locale.GERMANY)
        // turns of the year known from the calendar
        assertEquals(53, week(2020, 12, 31)); assertEquals(53, week(2021, 1, 3)); assertEquals(1, week(2021, 1, 4))
        assertEquals(52, week(2021, 12, 31)); assertEquals(52, week(2022, 1, 2)); assertEquals(1, week(2022, 1, 3))
        assertEquals(52, week(2023, 1, 1)); assertEquals(1, week(2023, 1, 2))
        assertEquals(1, week(2024, 1, 1)); assertEquals(52, week(2024, 12, 29)); assertEquals(1, week(2024, 12, 30))
        assertEquals(1, week(2025, 12, 29)); assertEquals(1, week(2026, 1, 4)); assertEquals(2, week(2026, 1, 5))
        assertEquals(53, week(2026, 12, 31)); assertEquals(53, week(2027, 1, 3)); assertEquals(1, week(2027, 1, 4))
        assertEquals(52, week(2028, 1, 2)); assertEquals(1, week(2028, 1, 3))
        // every day, against the rule: the week's Thursday, its day of the year
        var d = java.time.LocalDate.of(1970, 1, 1)
        val end = java.time.LocalDate.of(2100, 12, 31)
        val wrong = mutableListOf<String>()
        while (!d.isAfter(end)) {
            val thursday = d.plusDays((4 - d.dayOfWeek.value).toLong())
            val expected = (thursday.dayOfYear - 1) / 7 + 1
            val got = WidgetViews.weekOf(d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), java.util.Locale.GERMANY)
            if (got != expected) wrong += "$d: $got, expected $expected"
            d = d.plusDays(1)
        }
        assertTrue(wrong.take(10).joinToString("\n"), wrong.isEmpty())
    }

    /** The widget may stand on the lock screen too, where the system offers it. */
    @Test fun onTheLockScreenToo() {
        val info = java.io.File("src/main/res/xml/widget_info.xml").readText()
        assertTrue(info.contains("android:widgetCategory=\"home_screen|keyguard\""))
    }

    /** What is chosen shows as far as it fits: clock and weather first, then the rain, the pollen, the time of the data. */
    @Test fun thePartsAsChosenAndAsTheyFit() {
        val all = WidgetConfig(pollen = listOf(PollenType.BIRCH))
        val w = WidgetWeather.of(data(), now, all.pollen)
        val big = WidgetLayout.of(all, WidgetSize(320, 300), alarm = "06:30", weather = w)
        assertTrue(big.clock && big.date && big.alarm && big.weather && big.rain && big.chart && big.pollenRows == 1 && big.status)
        assertEquals(46f, big.bigDp, 0f)
        // two rows of cells: the alarm under the clock where there is the height, the chart still there
        val two = WidgetLayout.of(all, WidgetSize(320, 180), alarm = "06:30", weather = w)
        assertTrue(two.chart && two.alarm && !two.alarmBeside)
        // less height: beside the clock, so that the chart keeps its room
        val low = WidgetLayout.of(all, WidgetSize(320, 160), alarm = "06:30", weather = w)
        assertTrue(low.chart && low.alarmBeside && !low.alarm)
        // one row: date, clock, place and weather – smaller, nothing below
        val row = WidgetLayout.of(all, WidgetSize(320, 70), alarm = "06:30", weather = w)
        assertTrue(row.clock && row.date && row.weather && row.secondLine)
        assertTrue(row.bigDp < 40f)
        assertFalse(row.chart || row.pollenRows > 0 || row.status || row.alarm)
        // no line for the alarm under the clock: beside it
        assertTrue(row.alarmBeside)
        assertFalse(big.alarmBeside)
        // four small cells: the figures smaller, side by side still
        val small = WidgetLayout.of(all, WidgetSize(250, 110), alarm = "06:30", weather = w)
        assertTrue(small.clock && small.weather && small.bigDp < big.bigDp)
        // taller: the chart takes the height there is
        val tall = WidgetLayout.of(all, WidgetSize(320, 480), alarm = "06:30", weather = w)
        assertTrue(tall.chartDp > big.chartDp)
        // switched off
        val chosen = WidgetLayout.of(all.copy(clock = false, rain = false), WidgetSize(320, 220), alarm = "06:30", weather = w)
        assertFalse(chosen.clock || chosen.rain)
        assertTrue(chosen.date && chosen.weather)
        // two columns: the weather alone
        val narrow = WidgetLayout.of(all, WidgetSize(150, 110), alarm = "06:30", weather = w)
        assertTrue(narrow.weather)
        assertFalse(narrow.clock || narrow.date)
        // the views follow
        val v = render(all.copy(clock = false), WidgetSize(320, 220), data())
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_clock).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_date).visibility)
        assertEquals("12°", v.text(R.id.widget_temp))
        assertEquals("↑14°", v.text(R.id.widget_max))
        assertEquals("Stand 10:15", v.text(R.id.widget_as_of))
    }

    /** A tap on the time opens the alarms, on the date the calendar at today, on the weather the place's page. */
    @Test fun tapsLeadToAlarmsCalendarAndThePage() {
        val v = render(WidgetConfig(), WidgetSize(320, 200), data())
        fun tap(id: Int): Intent {
            v.findViewById<View>(id).performClick()
            return shadowOf(app).nextStartedActivity ?: throw AssertionError("nothing started")
        }
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, tap(R.id.widget_clock).action)
        val cal = tap(R.id.widget_date_row)
        assertEquals(Intent.ACTION_VIEW, cal.action)
        assertEquals("content://com.android.calendar/time/$now", cal.data.toString())
        val page = tap(R.id.widget_weather)
        assertEquals(MainActivity::class.java.name, page.component?.className)
        assertEquals("p", page.getStringExtra(WidgetViews.EXTRA_PLACE))
    }

    /**
     * "My location" is where the app found the phone last – never located, the first place of the
     * list; a chosen place is that place. The widget asks for no position of its own.
     */
    @Test fun myLocationIsWhereTheAppFoundThePhone() = runBlocking {
        val store = Store(app)
        val saved = Place("berlin", "Berlin", latitude = 52.52, longitude = 13.40)
        store.updatePlaces { listOf(saved) }
        assertEquals("berlin", Widgets.placeOf(store, WidgetConfig())?.id)
        val here = Place(LocationProvider.CURRENT_LOCATION_ID, "Linden", latitude = 52.36, longitude = 9.71, isCurrentLocation = true)
        store.cacheWeather(data(place = here))
        assertEquals("Linden", Widgets.placeOf(store, WidgetConfig())?.name)
        assertEquals("berlin", Widgets.placeOf(store, WidgetConfig(placeId = "berlin"))?.id)
        assertTrue(WidgetWeather.of(store.cachedWeather(here.id)!!, now, emptyList()).myLocation)
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    /**
     * Fetched about every 30 minutes – data the app has just loaded are not fetched again (a tap
     * on refresh does); the pollen with them when shown and out of date.
     */
    @Test fun fetchedEveryHalfHour() {
        assertEquals(30L, Widgets.INTERVAL_MINUTES)
        val d = data()
        assertTrue(Widgets.due(d, pollen = false, now = now).isEmpty())
        assertEquals(setOf(DataPart.FORECAST), Widgets.due(d, pollen = false, now = now + 30 * 60_000L))
        assertEquals(setOf(DataPart.FORECAST, DataPart.POLLEN), Widgets.due(d, pollen = true, now = now + 30 * 60_000L))
        assertEquals(setOf(DataPart.FORECAST, DataPart.POLLEN), Widgets.due(null, pollen = false, now = now))
        // a tap on refresh: the forecast anew, however fresh
        assertEquals(setOf(DataPart.FORECAST), Widgets.due(d, pollen = false, now = now, forced = true))
    }

    /** The setup: parts switched, pollen types picked – "Done" keeps them for this widget. */
    @Test fun theSetupKeepsTheChoice() {
        val configs = WidgetConfigs(app)
        var done = false
        compose.setContent { WidgetSetup(7, Store(app), configs) { done = true } }
        compose.waitForIdle()
        fun tap(tag: String) {
            compose.onNodeWithTag("widget-setup").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
        }
        tap("part-clock")
        tap("pollen-birch")
        tap("pollen-grass")
        tap("widget-done")
        compose.waitUntil(5_000) { done }
        val c = configs.get(7)!!
        assertFalse(c.clock)
        assertTrue(c.date && c.weather && c.rain)
        assertEquals(listOf(PollenType.BIRCH, PollenType.GRASS), c.pollen)
        assertNull(c.placeId)
    }

    /** Its look in the system's light and dark: clock, date, weather, the rain bars, the pollen. */
    @Test fun theWidgetLight() = look("widget_light")

    @Config(qualifiers = "+night")
    @Test fun theWidgetDark() = look("widget_dark")

    /** The picture in the launcher's widget list: the widget as it is, without a wallpaper behind (drawable-nodpi/widget_preview.png). */
    @Test fun theWidgetInThePicker() {
        val v = render(WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS)), WidgetSize(320, 200), data(rainFrom = 40))
        v.fixClocks()
        v.picture(wallpaper = null).captureRoboImage("src/test/screenshots/widget_picker.png")
    }

    /** The same in English (drawable-nodpi; the German one in drawable-de-nodpi). */
    @Config(qualifiers = "en-rGB-w411dp-h891dp-xxhdpi")
    @Test fun theWidgetInThePickerInEnglish() {
        val v = render(WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS)), WidgetSize(320, 200), data(rainFrom = 40))
        v.fixClocks()
        assertEquals("Light rain from about 11:00", v.text(R.id.widget_rain_text))
        v.picture(wallpaper = null).captureRoboImage("src/test/screenshots/widget_picker_en.png")
    }

    /**
     * One row of cells: the alarm small beside the clock, at a fixed place – a time of other
     * figures does not move it, and it stays clear of the widest time.
     */
    @Test fun theAlarmBesideTheClockInOneRow() {
        alarmAt(day + 30 * h)
        val places = listOf("'10:20'", "'11:11'", "'20:08'").map { time ->
            val v = render(WidgetConfig(), WidgetSize(320, 70), data())
            val clock = v.findViewById<TextClock>(R.id.widget_clock)
            clock.format24Hour = time; clock.format12Hour = time
            v.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, v.width, v.height)
            val beside = v.findViewById<ViewGroup>(R.id.widget_alarm_beside)
            assertEquals(View.VISIBLE, beside.visibility)
            assertEquals("06:00", v.text(R.id.widget_alarm_beside_text))
            val textLeft = beside.left + beside.paddingLeft
            assertTrue("$time: alarm at $textLeft, clock to ${clock.right}", textLeft >= clock.right)
            assertTrue(beside.right <= v.findViewById<View>(R.id.widget_weather).left)
            textLeft
        }
        assertEquals("the alarm's place", 1, places.distinct().size)
    }

    /** One row of cells: clock, date, place and weather. */
    @Test fun theWidgetInOneRow() {
        val v = render(WidgetConfig(), WidgetSize(320, 70), data(rainFrom = 40))
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_rain).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_clock).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_place).visibility)
        v.fixClocks()
        v.picture().captureRoboImage("src/test/screenshots/widget_row.png")
    }

    /** Four small cells, two rows: everything a little smaller, nothing cut. */
    @Test fun theWidgetOnASmallPhone() {
        val v = render(WidgetConfig(pollen = listOf(PollenType.BIRCH)), WidgetSize(250, 150), data(rainFrom = 40))
        v.fixClocks()
        val clock = v.findViewById<TextView>(R.id.widget_clock)
        val temp = v.findViewById<View>(R.id.widget_temp)
        assertTrue("clock and weather overlap", clock.right <= v.findViewById<View>(R.id.widget_weather).left + 1)
        assertTrue(temp.right <= v.width)
        assertEquals(clock.layout.getLineCount(), 1)
        v.picture().captureRoboImage("src/test/screenshots/widget_small.png")
    }

    private fun look(name: String) {
        alarmAt(day + 30 * h)                                          // tomorrow 06:00
        val v = render(WidgetConfig(pollen = listOf(PollenType.BIRCH, PollenType.GRASS, PollenType.HAZEL)), WidgetSize(320, 200), data(rainFrom = 40))
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_chart).visibility)
        assertTrue(v.findViewById<View>(R.id.widget_pollen).visibility == View.VISIBLE || v.findViewById<View>(R.id.widget_pollen_dots).visibility == View.VISIBLE)
        assertNotNull(v.findViewById<TextView>(R.id.widget_rain_text).text)
        assertEquals("06:00", v.text(R.id.widget_alarm))
        assertTrue(v.findViewById<View>(R.id.widget_alarm_line).visibility == View.VISIBLE || v.findViewById<View>(R.id.widget_alarm_beside).visibility == View.VISIBLE)
        v.fixClocks()
        v.picture().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** The clock app's next alarm at [t]. */
    private fun alarmAt(t: Long) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        val am = app.getSystemService(android.app.AlarmManager::class.java)
        am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(t, null), android.app.PendingIntent.getBroadcast(app, 0, Intent("alarm"), android.app.PendingIntent.FLAG_IMMUTABLE))
    }

    /** The widget [config] at [size] showing [d], laid out at that size. */
    private fun render(config: WidgetConfig, size: WidgetSize, d: WeatherData): ViewGroup {
        val weather = WidgetWeather.of(d, now, config.pollen)
        val views = WidgetViews.build(app, 1, config, size, weather, d.place.id, TemperatureUnit.CELSIUS, now)
        val frame = FrameLayout(app)
        val v = views.apply(app, frame)
        frame.addView(v)
        val density = app.resources.displayMetrics.density
        val w = (size.widthDp * density).toInt()
        val hh = (size.heightDp * density).toInt()
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w, hh)
        return frame
    }

    /** The widget drawn as the launcher draws it, on a grey wallpaper ([wallpaper] null: none). */
    private fun ViewGroup.picture(wallpaper: Int? = 0xFF6E7F8F.toInt()): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        wallpaper?.let(c::drawColor)
        draw(c)
        return bmp
    }

    private fun ViewGroup.text(id: Int) = findViewById<TextView>(id).text.toString()

    /** The system's clock at a fixed time and date (a quoted pattern shows as it is): the pictures stay the same. */
    private fun ViewGroup.fixClocks() {
        for (id in listOf(R.id.widget_clock, R.id.widget_date)) {
            findViewById<TextClock>(id).apply {
                val text = "'" + java.text.SimpleDateFormat(format24Hour.toString(), app.resources.configuration.locales[0]).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now)) + "'"
                format24Hour = text; format12Hour = text
            }
        }
        val w = width; val hh = height
        measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
        layout(0, 0, w, hh)
    }
}
