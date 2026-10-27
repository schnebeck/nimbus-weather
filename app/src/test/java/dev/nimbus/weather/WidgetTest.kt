/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetTest.kt
 * The home-screen widget: the page's values and words, the rain of the next two hours, the
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
import dev.nimbus.weather.data.model.PrecipitationUnit
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
import androidx.compose.ui.graphics.toArgb
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
     * The next two hours in quarter hours on the clock's quarters, as the precipitation card looks
     * ahead; rain within them worded as the card words it – rain after them is not announced
     * (the bars would not show it).
     */
    @Test fun theRainOfTheNextTwoHours() {
        val soon = data(rainFrom = 40)
        val rain = WidgetWeather.of(soon, now, emptyList()).rain!!
        // on the clock's quarters: from the running one's start (10:15) to the end of the one two hours from now ends in (12:30)
        assertEquals(day + 10 * h + 15 * 60_000L, rain.from)
        assertEquals(day + 12 * h + 30 * 60_000L, rain.to)
        assertEquals(now, rain.now)
        assertEquals(9, rain.quarters.size)
        // the quarter hours the notice looks at: the running one first, the last one reaching the end
        assertTrue("quarters ${rain.quarters.map { tf.time(it.at) }}", rain.quarters.first().at in now - q..now && rain.quarters.last().at + q >= rain.to)
        assertEquals(Insights.nowcastPoints(soon.minutely, now).filter { it.time >= rain.from && it.time < rain.to }.map { it.time }, rain.quarters.map { it.at })
        assertTrue(rain.quarters.any { it.mmPerHour > 0.0 })
        val card = Insights.precipNotice(soon.minutely, soon.hourly, soon.current.condition, now, false)!!
        assertEquals(precipNoticeText(app.resources, card, now, tf), precipNoticeText(app.resources, rain.notice!!, now, tf))

        val later = WidgetWeather.of(data(rainFrom = 130), now, emptyList()).rain!!
        assertNull(later.notice)
        assertTrue(later.quarters.all { it.mmPerHour == 0.0 })
        val views = render(WidgetConfig(), WidgetSize(320, 200), data(rainFrom = 130))
        assertEquals("Kein Niederschlag bis 12:30", views.text(R.id.widget_rain_text))
    }

    /**
     * Strong rain an hour and three quarters ahead, moderate before it: the widget words it as the
     * weather page's card does, looking just as far – not milder for a shorter look.
     */
    @Test fun theWordsOfTheWeatherPage() {
        val d = data(rainFrom = 40, mm = 0.4).let { d ->
            d.copy(minutely = d.minutely.map { if (it.time - now in 100 * 60_000L..120 * 60_000L) it.copy(precipitation = 3.0) else it })
        }
        val card = Insights.precipNotice(d.minutely, d.hourly, d.current.condition, now, false)!!
        val widget = WidgetWeather.of(d, now, emptyList()).rain!!
        assertEquals(Insights.Intensity.HEAVY, card.intensity)
        assertEquals(card, widget.notice)
        assertTrue(widget.quarters.any { it.mmPerHour >= 12.0 })
    }

    /** The rain sentence ends with the chance of the hour the rain starts in, as the hour strip writes it. */
    @Test fun theChanceOfTheHourTheRainStartsIn() {
        val d = data(rainFrom = 40).let { d ->
            d.copy(hourly = d.hourly.map { it.copy(precipitationProbability = if (it.time == day + 12 * h) 40.0 else 20.0) })
        }
        val rain = WidgetWeather.of(d, now, emptyList()).rain!!
        assertEquals(day + 11 * h, rain.notice!!.at)
        assertEquals("40", rain.chance)
        val words = precipNoticeText(app.resources, rain.notice!!, now, tf)
        assertEquals("$words\u00A0· 40%", render(WidgetConfig(), WidgetSize(320, 200), d).text(R.id.widget_rain_text))
        // dry: no chance to tell
        assertNull(WidgetWeather.of(data(rainFrom = 130), now, emptyList()).rain!!.chance)
    }

    /**
     * A shower of five minutes the radar sees in a quarter hour counts, however little its sum; an
     * echo too weak to be sure is perhaps drizzle – said, and drawn faint.
     */
    @Test fun aShowerByTheRadarsSteps() {
        val eleven = day + 11 * h
        fun with(peak: Double) = data().let { d -> d.copy(minutely = d.minutely.map { if (it.time == eleven) it.copy(precipitation = 0.02, peak = peak) else it }) }
        val shower = WidgetWeather.of(with(0.4), now, emptyList()).rain!!
        assertTrue(shower.quarters.single { it.at == eleven }.wet)
        assertEquals("Leichter Regen ab etwa 11:00", render(WidgetConfig(), WidgetSize(320, 200), with(0.4)).text(R.id.widget_rain_text))
        val weak = WidgetWeather.of(with(0.13), now, emptyList()).rain!!
        assertTrue(weak.quarters.single { it.at == eleven }.possible)
        assertTrue(weak.eventful)
        assertEquals("Vielleicht etwas Niesel ab etwa 11:00", render(WidgetConfig(), WidgetSize(320, 200), with(0.13)).text(R.id.widget_rain_text))
    }

    /**
     * A launcher whose frame keeps a margin inside shows the widget a few dp smaller than it
     * reports: each picture is planned for and offered at a little less, so it still fits the
     * frame – and a 4×3 keeps its chart.
     */
    @Test fun offeredALittleBelowTheReportedSize() {
        val o = dev.nimbus.weather.widget.Widgets.offered(android.util.SizeF(320f, 280f))
        assertTrue("$o", o.widthDp <= 320 - 16 && o.heightDp <= 280 - 24)
        assertTrue("$o", o.widthDp >= 290 && o.heightDp >= 240)
        val v = render(WidgetConfig(), o, data(rainFrom = 40))
        assertEquals(android.view.View.VISIBLE, v.findViewById<android.view.View>(R.id.widget_chart).visibility)
    }

    /**
     * A launcher reporting sizes well above its frame (Nova: a frame of 411 × 260 dp): among the
     * pictures one from the least size it reports, which fits – and one between that keeps the
     * chart; at most six.
     */
    @Test fun aPictureThatFitsTheFrame() {
        val reported = listOf(android.util.SizeF(432f, 380f), android.util.SizeF(860f, 190f))
        val bounds = dev.nimbus.weather.widget.Widgets.Bounds(minWidth = 400, minHeight = 190, maxWidth = 860, maxHeight = 380)
        val offers = dev.nimbus.weather.widget.Widgets.offers(reported, bounds)
        assertTrue("$offers", offers.size <= 6)
        val frame = 410.67f to 259.56f
        val fitting = offers.filter { it.widthDp <= frame.first && it.heightDp <= frame.second }
        assertTrue("$offers", fitting.isNotEmpty())
        // the system takes the closest that fits: a step of height 22 dp below the frame, tall enough for the chart
        val taken = fitting.minBy { (frame.first - it.widthDp) * (frame.first - it.widthDp) + (frame.second - it.heightDp) * (frame.second - it.heightDp) }
        assertEquals(dev.nimbus.weather.widget.WidgetSize(400, 237), taken)
        // a launcher whose least and largest sizes are its frames (upright and sideways): both among them
        assertTrue(offers.contains(dev.nimbus.weather.widget.WidgetSize(400, 380)) && offers.contains(dev.nimbus.weather.widget.WidgetSize(860, 190)))
        val v = render(WidgetConfig(), taken, data(rainFrom = 40))
        assertEquals(android.view.View.VISIBLE, v.findViewById<android.view.View>(R.id.widget_chart).visibility)
    }

    /**
     * No chance beside perhaps some drizzle (the radar's weak echo, not the model's precipitation),
     * and never "0%" – beside precipitation coming it only contradicts.
     */
    @Test fun noChanceThatContradicts() {
        val eleven = day + 11 * h
        fun with(peak: Double, mm: Double) = data().let { d ->
            d.copy(
                minutely = d.minutely.map { if (it.time == eleven) it.copy(precipitation = mm, peak = peak) else it },
                hourly = d.hourly.map { it.copy(precipitationProbability = 0.0) },
            )
        }
        assertEquals("Vielleicht etwas Niesel ab etwa 11:00", render(WidgetConfig(), WidgetSize(320, 200), with(0.13, 0.01)).text(R.id.widget_rain_text))
        assertNull(WidgetWeather.of(with(0.13, 0.01), now, emptyList()).rain!!.chance)
        // a sure shower, the model's hour at 0 %: the sentence without a chance
        assertEquals("Leichter Regen ab etwa 11:00", render(WidgetConfig(), WidgetSize(320, 200), with(0.4, 0.1)).text(R.id.widget_rain_text))
    }

    /** Today's high and low hold the temperature measured now – the other days stay as forecast. */
    @Test fun theDaysHighAndLowHoldTheTemperatureNow() {
        val d = data()
        fun at(t: Double) = d.current.copy(temperature = t)
        val warm = dev.nimbus.weather.data.repo.NowWeather.dailyWithNow(d.daily, at(14.6), now)
        assertEquals(14.6, warm[0].tempMax, 0.0)
        assertEquals(6.0, warm[0].tempMin, 0.0)
        assertEquals(d.daily.drop(1), warm.drop(1))
        assertEquals(5.2, dev.nimbus.weather.data.repo.NowWeather.dailyWithNow(d.daily, at(5.2), now)[0].tempMin, 0.0)
        assertEquals(d.daily, dev.nimbus.weather.data.repo.NowWeather.dailyWithNow(d.daily, at(12.4), now))
        // a forecast whose days are all over: nothing to hold
        assertEquals(d.daily, dev.nimbus.weather.data.repo.NowWeather.dailyWithNow(d.daily, at(20.0), day + 4 * 24 * h))
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
        // beside the chart, one under the other: those flying, the strongest first
        val v = render(WidgetConfig(pollen = listOf(PollenType.GRASS, PollenType.HAZEL, PollenType.BIRCH)), WidgetSize(320, 300), data())
        assertEquals("Birke", v.text(R.id.widget_side_0_name))
        assertEquals("Gräser", v.text(R.id.widget_side_1_name))
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_side_2).visibility)
        // without the chart: side by side over the whole width – one row, its third place kept empty
        val dull = data(rainFrom = 130).let { d -> d.copy(minutely = d.minutely.map { it.copy(sunshine = 0.0) }) }
        val g = render(WidgetConfig(pollen = listOf(PollenType.GRASS, PollenType.HAZEL, PollenType.BIRCH)), WidgetSize(320, 300), dull)
        assertEquals("Birke", g.text(R.id.widget_pollen_0_name))
        assertEquals(View.VISIBLE, g.findViewById<View>(R.id.widget_pollen_row_0).visibility)
        assertEquals(View.GONE, g.findViewById<View>(R.id.widget_pollen_row_1).visibility)
        assertEquals(View.INVISIBLE, g.findViewById<View>(R.id.widget_pollen_2).visibility)
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
        // with the chart: beside it, not as points
        val l = WidgetLayout.of(WidgetConfig(pollen = all), WidgetSize(320, 180), alarm = "06:00", weather = w, pollenText = WidgetViews.pollenDots(app, w.pollen).toString())
        assertTrue(l.pollenSide && !l.pollenDots && l.pollenRows == 0 && l.chart)
        // nothing to draw and little height: as points beside the alarm
        val dull = data(rainFrom = 130).let { d -> d.copy(minutely = d.minutely.map { it.copy(sunshine = 0.0) }) }
        val wd = WidgetWeather.of(dull, now, all)
        val low = WidgetLayout.of(WidgetConfig(pollen = all, rain = false), WidgetSize(320, 140), alarm = "06:00", weather = wd, pollenText = WidgetViews.pollenDots(app, wd.pollen).toString())
        assertTrue(low.pollenDots && !low.chart && low.alarm)
        // birch "medium" yellow; grass "none to low" and hazel "none" left out
        val dots = WidgetViews.pollenDots(app, w.pollen) as android.text.Spanned
        assertEquals("● Birke", dots.toString().replace('\u00A0', ' '))
        val colors = dots.getSpans(0, dots.length, android.text.style.ForegroundColorSpan::class.java).map { it.foregroundColor }
        assertEquals(listOf(0xFFF7C948.toInt()), colors)
        val red = WidgetViews.pollenDots(app, listOf(PollenType.BIRCH to 3f, PollenType.GRASS to 1.5f, PollenType.ALDER to 1f)) as android.text.Spanned
        assertEquals("● Birke  ● Gräser", red.toString().replace('\u00A0', ' '))
        assertEquals(0xFFFF5B36.toInt(), red.getSpans(0, red.length, android.text.style.ForegroundColorSpan::class.java).first().foregroundColor)
        assertEquals("Pollen: keine bis gering", WidgetViews.pollenDots(app, listOf(PollenType.BIRCH to 0.5f, PollenType.GRASS to 0f)).toString())
        // a German noun keeps its capital
        assertEquals("Pollen: keine Belastung", WidgetViews.quietPollen(app, 0f))
        // taller: the gauges with their half steps – beside the chart
        val tall = WidgetLayout.of(WidgetConfig(pollen = all), WidgetSize(320, 300), alarm = "06:00", weather = w, pollenText = dots.toString())
        assertTrue(!tall.pollenDots && tall.pollenSide && tall.pollenSideRows >= 2)
    }

    /**
     * Nothing to draw in the two hours (no sunshine, dry): no empty chart – its line in its place,
     * by day saying the sun stays away too. Precipitation coming in two rows of cells: its line
     * beside the alarm, the pollen give way.
     */
    /**
     * The precipitation's scale is linear from 0: 2.5 mm/h stands a quarter as high as 10 mm/h.
     * Its top is the strongest bar rounded up to a whole mm/h, at least 1 mm/h, half of it in the
     * middle – as in the meteogram.
     */
    @Test fun theRainScaleIsLinearFromZero() {
        val from = day + 10 * h
        fun rain(vararg mm: Double) = WidgetWeather.Rain(from, from + 8 * q, mm.mapIndexed { k, v -> WidgetWeather.Quarter(from + k * q, v, null) }, null)
        val r = rain(0.0, 2.5, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(10.0, WidgetViews.rainTop(r, PrecipitationUnit.MM), 0.0)
        assertEquals(1.0, WidgetViews.rainTop(rain(0.3, 0.4), PrecipitationUnit.MM), 0.0)
        assertEquals(3.0, WidgetViews.rainTop(rain(2.5), PrecipitationUnit.MM), 0.0)
        assertEquals(0.1 * 25.4, WidgetViews.rainTop(rain(2.5), PrecipitationUnit.INCH), 1e-9)
        val s = WidgetViews.scales(app, PrecipitationUnit.MM, java.util.Locale.GERMANY, 3.0)
        assertEquals("3" to "1,5", s.rainTop to s.rainMid)
        // the bars' tops over the ground line: the quarter at 2.5 mm/h and the one at 10
        val bmp = WidgetViews.chart(r, 400, 200, 1f, WidgetViews.scales(app, PrecipitationUnit.MM, java.util.Locale.GERMANY, 10.0), tf)
        val blue = dev.nimbus.weather.ui.main.HourBars.Rain.frame.toArgb() and 0xFFFFFF
        fun topOf(k: Int): Int {
            val plot = WidgetViews.plot(WidgetViews.scales(app, PrecipitationUnit.MM, java.util.Locale.GERMANY, 10.0), 1f, 1f)
            val x = (plot.left + (400 - plot.left - plot.right) * (k + 0.5f) / 8).toInt()
            return (0 until 200).first { y -> val c = bmp.getPixel(x, y); (c ushr 24) > 128 && (c and 0xFFFFFF) == blue }
        }
        val base = 200 - 14 - 1
        val high = base - topOf(3)
        val low = base - topOf(1)
        assertEquals("$low of $high px", 0.25, low.toDouble() / high, 0.03)
    }

    @Test fun theChartOnlyWithSomethingToDraw() {
        val dull = data(rainFrom = 130).let { d -> d.copy(minutely = d.minutely.map { it.copy(sunshine = 0.0) }) }
        val v = render(WidgetConfig(), WidgetSize(320, 200), dull)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_chart).visibility)
        assertEquals("Kein Niederschlag, keine Sonne bis 12:30", v.text(R.id.widget_rain_text))
        val night = dull.copy(current = dull.current.copy(isDay = false))
        assertEquals("Kein Niederschlag bis 12:30", render(WidgetConfig(), WidgetSize(320, 200), night).text(R.id.widget_rain_text))
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

    /**
     * A launcher reporting more height than it gives (a frame of 320 × 182 dp, the pictures from
     * its figures in steps of 45 dp): planned for the step below the frame, the alarm stood small
     * beside a smaller clock, the room for its line left empty above the precipitation; planned
     * for the frame the widget finds, the alarm on its line under the clock, the clock and the chart kept.
     */
    @Test fun theAlarmUnderTheClockWhereTheFrameHasTheRoom() {
        val all = WidgetConfig(pollen = listOf(PollenType.BIRCH))
        val w = WidgetWeather.of(data(), now, all.pollen)
        val base = Widgets.offers(listOf(android.util.SizeF(320f, 300f)), Widgets.Bounds(320, 120, 320, 300))
        fun shown(offered: List<WidgetSize>) = offered.filter { it.widthDp <= 320f && it.heightDp <= 182.4f }
            .minBy { (320f - it.widthDp) * (320f - it.widthDp) + (182.4f - it.heightDp) * (182.4f - it.heightDp) }
        val step = shown(base)
        val before = WidgetLayout.of(all, step, alarm = "09:00", weather = w)
        assertTrue("$step", before.alarmBeside && !before.alarm && before.chart)
        var search = dev.nimbus.weather.widget.FrameSearch("figures")
        repeat(6) {
            search = search.next(base, now)
            search = search.heard(search.round, shown(search.offered)) ?: return@repeat
        }
        assertTrue("$search", search.settled)
        val found = WidgetLayout.of(all, search.fit!!, alarm = "09:00", weather = w)
        assertTrue("${search.fit}", found.alarm && !found.alarmBeside && found.chart)
        assertTrue(found.bigDp > before.bigDp)
    }

    /**
     * A low widget (Nova: a frame of 426 × 111 dp): the alarm on its line under the clock where
     * that line fits as it is drawn (2 dp above, a line of 13 sp) – not small beside a smaller clock.
     */
    @Test fun theAlarmUnderTheClockInALowFrame() {
        alarmAt(day + 4 * 24 * h + 8 * h)
        val config = WidgetConfig(dateWithWeek = true)
        val l = WidgetLayout.of(config, WidgetSize(426, 111), alarm = "Sa 08:30", weather = WidgetWeather.of(data(), now, config.pollen))
        assertTrue("$l", l.alarm && !l.alarmBeside)
        assertEquals(46f, l.bigDp, 0f)
        val v = render(config, WidgetSize(426, 111), data())
        val line = v.findViewById<View>(R.id.widget_alarm_line)
        assertEquals(View.VISIBLE, line.visibility)
        // within the widget's padding at the bottom: nothing cut off
        val density = app.resources.displayMetrics.density
        var bottom = line.bottom
        var p = line.parent as View
        while (p !== v) { bottom += p.top; p = p.parent as View }
        assertTrue("${bottom / density} dp", bottom <= v.height - 8 * density + 0.5f)
    }

    /**
     * The pollen beside the chart: the time of the data under them, its letters on the line of the
     * chart's times – the chart reaching down to the bottom, its height gained by the chart; the
     * pollen above it, clear of it and of the refresh.
     */
    @Test fun theTimeOfTheDataOnTheLineOfTheChartsTimes() {
        alarmAt(day + 4 * 24 * h + 8 * h)
        val config = WidgetConfig(dateWithWeek = true, pollen = listOf(PollenType.BIRCH, PollenType.GRASS, PollenType.HAZEL))
        val size = WidgetSize(426, 276)
        val w = WidgetWeather.of(data(), now, config.pollen)
        val l = WidgetLayout.of(config, size, alarm = "Sa 08:30", weather = w)
        assertTrue("$l", l.pollenSide && l.statusSide && l.status && l.chart)
        val own = WidgetLayout.of(config.copy(pollen = emptyList()), size, alarm = "Sa 08:30", weather = WidgetWeather.of(data(), now, emptyList()))
        assertTrue("$own", !own.statusSide && own.status)
        assertEquals(WidgetLayout.STATUS, l.chartDp - own.chartDp, 0.01f)

        val v = render(config, size, data())
        val density = app.resources.displayMetrics.density
        val asOf = v.findViewById<TextView>(R.id.widget_as_of_side)
        assertEquals(View.VISIBLE, asOf.visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_status).visibility)
        val chart = v.findViewById<View>(R.id.widget_chart)
        // the chart's times written at its bottom, standing on their descent (10 sp × 1.1)
        val times = android.graphics.Paint().apply { textSize = 11 * density }
        val timesLine = v.topOf(chart) + chart.height - times.descent()
        val asOfLine = v.topOf(asOf) + asOf.baseline
        assertEquals("the lines of the chart's times and of the time of the data", timesLine, asOfLine.toFloat(), 1.0f * density)
        // the pollen clear of the time of the data
        val rows = listOf(R.id.widget_side_0, R.id.widget_side_1, R.id.widget_side_2).map { v.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { v.topOf(it) + it.height <= v.topOf(asOf) })
    }

    /**
     * The two hours' line midway: its letters as far from the alarm's above as from the chart below
     * (to the chart's first drawn row, its units) – within a dp.
     */
    @Test fun theTwoHoursLineMidwayBetweenTheAlarmAndTheChart() {
        alarmAt(day + 4 * 24 * h + 8 * h)
        for (size in listOf(WidgetSize(426, 276), WidgetSize(426, 300), WidgetSize(360, 344))) {
            val config = WidgetConfig(dateWithWeek = true, pollen = listOf(PollenType.BIRCH))
            val v = render(config, size, data(rainFrom = 40))
            val density = app.resources.displayMetrics.density
            val alarm = v.findViewById<TextView>(R.id.widget_alarm)
            val line = v.findViewById<TextView>(R.id.widget_rain_text)
            val chart = v.findViewById<android.widget.ImageView>(R.id.widget_chart)
            assertTrue("$size", listOf(alarm, line, chart).all { it.visibility == View.VISIBLE })
            val ink = android.graphics.Rect()
            line.paint.getTextBounds(line.text.toString(), 0, line.text.length, ink)
            val above = v.topOf(line) + line.totalPaddingTop + line.layout.getLineBaseline(0) + ink.top - (v.topOf(alarm) + alarm.baseline)
            val bmp = (chart.drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val firstRow = (0 until bmp.height).first { y -> (0 until bmp.width).any { x -> bmp.getPixel(x, y) ushr 24 != 0 } }
            val below = v.topOf(chart) + firstRow - (v.topOf(line) + line.totalPaddingTop + line.layout.getLineBaseline(line.lineCount - 1))
            assertEquals("$size: above ${above / density} dp, below ${below / density} dp", above.toFloat(), below.toFloat(), 1.0f * density)
        }
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
        assertTrue(big.clock && big.date && big.alarm && big.weather && big.rain && big.chart && big.pollenSide && big.status)
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
        // the pollen beside the chart: the time of the data under them
        assertEquals("Stand 10:15", v.text(R.id.widget_as_of_side))
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_status).visibility)
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
     * The parts as bookmarks of the page: the weather to the hours, the precipitation (chart or
     * line) to today opened, the pollen to theirs; anywhere else the page's head.
     */
    @Test fun thePartsAreBookmarksOfThePage() {
        fun section(v: View, id: Int): String? {
            v.findViewById<View>(id).performClick()
            val i = shadowOf(app).nextStartedActivity ?: throw AssertionError("nothing started")
            assertEquals("p", i.getStringExtra(WidgetViews.EXTRA_PLACE))
            return i.getStringExtra(WidgetViews.EXTRA_SECTION)
        }
        val all = listOf(PollenType.BIRCH, PollenType.GRASS)
        val big = render(WidgetConfig(pollen = all), WidgetSize(320, 260), data(rainFrom = 40))
        assertEquals("HOURLY", section(big, R.id.widget_weather))
        assertEquals("TODAY", section(big, R.id.widget_rain))
        val pollen = if (big.findViewById<View>(R.id.widget_side).visibility == View.VISIBLE) R.id.widget_side else R.id.widget_pollen
        assertEquals(View.VISIBLE, big.findViewById<View>(pollen).visibility)
        assertEquals("POLLEN", section(big, pollen))
        assertEquals("HEAD", section(big, android.R.id.background))
        // two rows of cells: the precipitation's line beside the alarm leads to today as well
        alarmAt(day + 30 * h)
        val small = render(WidgetConfig(pollen = all), WidgetSize(320, 180), data(rainFrom = 40))
        assertEquals("Leichter Regen ab etwa 11:00", small.text(R.id.widget_pollen_dots))
        assertEquals("TODAY", section(small, R.id.widget_pollen_dots))
    }

    /**
     * Nimbus in English on a German phone: the date in English all through ("Thursday, October 1"),
     * not the clock's German names in the English order; the same language: the clock's pattern.
     */
    @Test fun theDateInNimbussLanguage() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        val english = WidgetViews.datePattern("EEEEdMMMM", java.util.Locale.US, java.util.Locale.GERMANY, now)
        assertTrue(english, "'Thursday'" in english && "'October'" in english && "d" in english.replace("'Thursday'", "").replace("'October'", ""))
        // written by a clock that speaks German
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("Thursday, October 1", android.text.format.DateFormat.format(english, now).toString())
            val short = WidgetViews.datePattern("EEEddMMyyyy", java.util.Locale.US, java.util.Locale.GERMANY, now)
            assertEquals("Thu, 10/01/2026", android.text.format.DateFormat.format(short, now).toString())
        } finally {
            java.util.Locale.setDefault(before)
        }
        val same = WidgetViews.datePattern("EEEEdMMMM", java.util.Locale.GERMANY, java.util.Locale.GERMANY, now)
        assertEquals(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.GERMANY, "EEEEdMMMM"), same)
    }

    /**
     * A tap on the time opens the alarms on phones whose clock does not answer the standard request,
     * or answers it only to apps allowed to set alarms (ColorOS): the next alarm's own entry where it
     * opens a page, else the clock app, by the request it answers or by its name, else an app whose
     * name says clock; where an app answers the standard request to Nimbus, that one.
     */
    @Test fun theTimeOpensTheAlarmsOnEveryPhone() {
        fun tapClock(): Intent {
            val v = render(WidgetConfig(), WidgetSize(320, 200), data())
            v.findViewById<View>(R.id.widget_clock).performClick()
            return shadowOf(app).nextStartedActivity ?: throw AssertionError("nothing started")
        }
        val pm = shadowOf(app.packageManager)
        fun installApp(pkg: String, cls: String, filter: android.content.IntentFilter, permission: String? = null) {
            val cn = android.content.ComponentName(pkg, cls)
            pm.addActivityIfNotPresent(cn)
            if (permission != null) pm.addOrUpdateActivity(app.packageManager.getActivityInfo(cn, 0).apply { this.permission = permission })
            pm.addIntentFilterForActivity(cn, filter)
        }
        val launcher = android.content.IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val showAlarms = android.content.IntentFilter(AlarmClock.ACTION_SHOW_ALARMS).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        // a clock unknown by its name: found by "clock" in it
        installApp("org.example.smartclock", "org.example.smartclock.Main", launcher)
        assertEquals("org.example.smartclock", tapClock().component?.packageName)
        // ColorOS: its clock answers the standard request only to apps allowed to set alarms – opened as an app
        installApp("org.example.timer", "org.example.timer.Main", launcher)
        installApp("org.example.timer", "org.example.timer.HandleApi", showAlarms, permission = "com.android.alarm.permission.SET_ALARM")
        assertEquals("org.example.timer.Main", tapClock().component?.className)
        // the next alarm's entry where it only tells the clock (ColorOS's): not that one
        val am = app.getSystemService(android.app.AlarmManager::class.java)
        val ring = android.app.PendingIntent.getBroadcast(app, 0, Intent("alarm"), android.app.PendingIntent.FLAG_IMMUTABLE)
        val indicator = android.app.PendingIntent.getBroadcast(app, 8, Intent("indicator"), android.app.PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(day + 30 * h, indicator), ring)
        assertEquals("org.example.timer.Main", tapClock().component?.className)
        // the next alarm's own page, set by the clock app, before all
        val own = Intent("com.coloros.alarmclock.SHOW_ALARM").setPackage("com.coloros.alarmclock")
        am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(day + 30 * h, android.app.PendingIntent.getActivity(app, 7, own, android.app.PendingIntent.FLAG_IMMUTABLE)), ring)
        assertEquals("com.coloros.alarmclock.SHOW_ALARM", tapClock().action)
        am.cancel(ring)
        // a clock answering the standard request to anyone: that one
        installApp("com.google.android.deskclock", "com.android.deskclock.DeskClock", showAlarms)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, tapClock().action)
    }

    /**
     * The refresh at any size, in the corner, clear of the other parts: beside "As of" where it has
     * its line, else alone – the parts above making way at the right.
     */
    @Test fun theRefreshAtAnySize() {
        alarmAt(day + 30 * h)
        val all = listOf(PollenType.BIRCH, PollenType.GRASS)
        for (size in listOf(WidgetSize(320, 70), WidgetSize(250, 110), WidgetSize(250, 150), WidgetSize(180, 110), WidgetSize(320, 200), WidgetSize(400, 400))) {
            val v = render(WidgetConfig(pollen = all), size, data(rainFrom = 40))
            val refresh = v.findViewById<View>(R.id.widget_refresh)
            assertEquals("$size", View.VISIBLE, refresh.visibility)
            fun rect(x: View): android.graphics.Rect {
                val r = android.graphics.Rect(0, 0, x.width, x.height)
                (v as ViewGroup).offsetDescendantRectToMyCoords(x, r)
                return r
            }
            val r = rect(refresh)
            // the glyph (within the padding) in the corner, inside the widget
            val glyph = android.graphics.Rect(r.left + refresh.paddingLeft, r.top + refresh.paddingTop, r.right - refresh.paddingRight, r.bottom - refresh.paddingBottom)
            assertTrue("$size $glyph in ${v.width}x${v.height}", glyph.right <= v.width && glyph.bottom <= v.height && glyph.left > v.width * 0.7)
            // no part shown under it
            fun leaves(g: View): List<View> = if (g is ViewGroup && g !is android.widget.TextClock) (0 until g.childCount).map { g.getChildAt(it) }.filter { it.visibility == View.VISIBLE }.flatMap(::leaves) else listOf(g)
            val parts = leaves(v).filter { it.id != R.id.widget_refresh && it.id != R.id.widget_bg && it.width > 0 && it.height > 0 }
            for (part in parts) {
                val p = rect(part)
                // a text's own box may be wider than its letters: where it is right-aligned or narrow, its box counts
                assertFalse("$size: ${app.resources.getResourceEntryName(part.id.takeIf { it != View.NO_ID } ?: R.id.widget_bg)} $p under the refresh $glyph", android.graphics.Rect.intersects(p, glyph))
            }
        }
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
     * The pictures in the launcher's list are the widget of now: its reference pictures at two
     * thirds – compared in blocks of 20 px, so a chart of another kind or length shows.
     */
    @Test fun thePickersPictureIsTheWidgetOfNow() {
        fun img(path: String) = javax.imageio.ImageIO.read(java.io.File(path))
        for ((ref, res) in listOf("widget_picker.png" to "drawable-de-nodpi", "widget_picker_en.png" to "drawable-nodpi")) {
            val shown = img("src/main/res/$res/widget_preview.png")
            val made = img("src/test/screenshots/$ref")
            assertEquals(made.width * 2 / 3, shown.width)
            assertEquals(made.height * 2 / 3, shown.height)
            val scaled = java.awt.image.BufferedImage(shown.width, shown.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            scaled.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                drawImage(made, 0, 0, shown.width, shown.height, null); dispose()
            }
            fun block(i: java.awt.image.BufferedImage, bx: Int, by: Int): Double {
                var sum = 0.0
                for (y in by until by + 20) for (x in bx until bx + 20) {
                    // as seen: the colour by its opacity (a clear corner is clear, whatever its colour)
                    val c = i.getRGB(x, y)
                    val a = (c ushr 24) / 255.0
                    sum += a * (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF))
                }
                return sum / (20 * 20 * 3)
            }
            for (by in 0 until shown.height - 20 step 20) for (bx in 0 until shown.width - 20 step 20) {
                val d = kotlin.math.abs(block(shown, bx, by) - block(scaled, bx, by))
                assertTrue("$res at $bx,$by: $d", d < 12)
            }
        }
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
        assertTrue(listOf(R.id.widget_pollen, R.id.widget_pollen_dots, R.id.widget_side).any { v.findViewById<View>(it).visibility == View.VISIBLE })
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

    /** Where [child] stands from this view's top, in px. */
    private fun ViewGroup.topOf(child: View): Int {
        var top = child.top
        var p = child.parent as View
        while (p !== this) { top += p.top; p = p.parent as View }
        return top
    }

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
