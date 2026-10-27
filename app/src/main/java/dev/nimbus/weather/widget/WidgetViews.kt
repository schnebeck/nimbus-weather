/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetViews.kt
 * The widget's picture in two columns: its parts as chosen and as its size allows, the weather
 * symbol, the two hours' chart and the pollen gauges drawn, a tap on each part to where it leads.
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

import android.app.AlarmManager
import android.app.PendingIntent
import dev.nimbus.weather.ui.main.PageSection
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.nimbus.weather.MainActivity
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.components.drawWeatherSymbol
import dev.nimbus.weather.ui.main.HourBars
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.ui.main.pollenLevelColor
import dev.nimbus.weather.ui.main.pollenName
import dev.nimbus.weather.ui.main.pollenLevelText
import dev.nimbus.weather.ui.main.precipNoticeText
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units
import java.util.Locale

/** The widget's size in dp (as the launcher gives it: its width and height in portrait). */
data class WidgetSize(val widthDp: Int, val heightDp: Int)

/**
 * The heights of the widget's lines of text in dp, their font's padding with them: the head line
 * (14 sp), a line of the two hours (13 sp), the line under the columns (the alarm, 13 sp), a pollen
 * row (12 sp); [spare]: room kept below the parts for what is not known to the dp.
 */
data class WidgetLines(val head: Float, val text: Float, val mid: Float, val small: Float, val spare: Float) {
    companion object {
        /** Estimated, a little generous: where the font is not known. */
        fun estimated(fontScale: Float) = WidgetLines(19f * fontScale + 2f, 19f * fontScale, 18f * fontScale, 18f * fontScale, 6f)

        /**
         * As the system's font draws them at [fontScale] – the launcher draws the widget with the
         * same font, so the parts have the height there is and no more is kept free.
         */
        fun measured(fontScale: Float): WidgetLines {
            val p = Paint().apply { textSize = 100f }
            val perSp = p.fontMetrics.let { it.bottom - it.top } / 100f
            // a pixel or so of rounding each
            val text = 13 * fontScale * perSp + 0.5f
            return WidgetLines(14 * fontScale * perSp + 0.5f, text, text, 12 * fontScale * perSp + 0.5f, 2f)
        }
    }
}

/**
 * Which parts the widget shows. On top two columns while there is room for both: on the left
 * the date, the time and the next alarm; on the right the place and the weather. Below them,
 * with some space, over the whole width: the next two hours (the chart of precipitation and
 * sunshine, its line), the pollen ([pollenPerRow] side by side) and the time of the data. Those
 * chosen, as far as they fit: the clock and the weather first, then the chart, the pollen, the
 * two hours' line, the time of the data with its refresh last; what height is left the chart
 * takes. The big figures ([bigDp]: the clock's height, the symbol's size) grow with the room;
 * [secondLine]: the date over the clock and the place over the weather fit; [longDate]: the
 * weekday and month written out. The pollen as gauges below ([pollenRows]) where they all fit, else
 * as points in a line under the columns ([pollenDots]) – only those that fly noteworthily –,
 * beside the alarm where it stands there; with the chart, on the third beside it, one under the
 * other ([pollenSide], [pollenSideRows] of them). Only the types that fly at all. The chart only with something to draw (sunshine,
 * precipitation); else its line takes its place. Precipitation coming, its line comes before the
 * pollen – where it has no room over the chart, in the line under the columns ([midRain]).
 * The alarm on a line under the clock ([alarm]) where there is the
 * height for it – or small beside it ([alarmBeside]) where that line would cost the chart its room.
 * With the alarm on its line and no pollen as points beside it, the two hours' line stands there
 * at the right ([midRain]) where it fits: the line it leaves the chart takes. The alarm's line
 * reaches up into the room under the clock's figures ([tuckDp]: so much higher), the chart
 * close under it.
 * The time of the data under the pollen beside the chart, on the line of the chart's times
 * ([statusSide]): it takes no line of its own, the chart has its height.
 */
data class WidgetLayout(
    val clock: Boolean, val date: Boolean, val alarm: Boolean, val alarmBeside: Boolean, val weather: Boolean, val right: Boolean,
    val secondLine: Boolean, val rain: Boolean, val rainLine: Boolean, val midRain: Boolean, val chart: Boolean, val chartDp: Float,
    val pollenRows: Int, val pollenPerRow: Int, val pollenDots: Boolean, val pollenSide: Boolean, val pollenSideRows: Int, val status: Boolean, val bigDp: Float, val longDate: Boolean, val widthDp: Float,
    /** No room for "As of": its line empty, the refresh alone in it. */
    val refreshLine: Boolean = false,
    /** Not even for that line: the parts keep [REFRESH_ROOM] free at the right for the refresh in the corner. */
    val refreshRoom: Boolean = false,
    val statusSide: Boolean = false,
    /** The line under the clock so much higher than its place below the columns, in dp. */
    val tuckDp: Float = 0f,
) {
    companion object {
        private const val PADDING_V = 16
        private const val PADDING_H = 28
        private const val BIG_MAX = 46f
        private const val BIG_MIN = 16f
        /** A line of the big font, by its size (no font padding). */
        private const val BIG_LINE = 1.17f
        /** The clock "10:20" and the weather (symbol and temperature) by the big size; the weather's high and low besides. */
        private const val CLOCK_W = 2.5f
        private const val WEATHER_W = 2.4f
        private const val WEATHER_FIX = 40f
        /** Room between the columns. */
        private const val GAP = 10f
        /** Above the line under the columns (the alarm, the pollen as points, 13 sp). */
        const val MID_TOP = 2f
        /**
         * Of the clock's size, how far the alarm's line under it reaches up: the clock's figures end
         * on its baseline, under them its font keeps a quarter of its size free.
         */
        private const val TUCK = 0.18f
        /** A character of the head line (14 sp); the place's pin. */
        private const val HEAD_CHAR = 7.6f
        private const val PIN = 15f
        private const val ALARM_ICON = 26f
        private const val CHAR = 7.2f
        /** The alarm beside the clock: its distance, its icon, a character of its smaller text. */
        private const val BESIDE_GAP = 6f
        private const val BESIDE_ICON = 14f
        private const val BESIDE_CHAR = 6.8f
        /** Below the columns: the space above the two hours, the chart's least height and the times under it. */
        const val BELOW_GAP = 10f
        const val CHART_TOP = 3f
        /** The space above the chart right under the alarm's line, all of it: the line's descent keeps them apart already. */
        const val CHART_UNDER_ALARM = 2f
        /**
         * Of the room above the chart, what goes over the two hours' line standing on it: the text's
         * own room above its letters is larger than the chart's above its units, so the letters stand
         * as far from the line above as from the chart.
         */
        const val LINE_OVER_CHART = 5f
        private const val CHART_MIN = 34f
        private const val AXIS = 14f
        /** The pollen: the space above, a row, the least width of a type with its gauge. */
        private const val POLLEN_TOP = 6f
        private const val POLLEN_CELL = 92f
        /** The least width of the third beside the chart: a name and its gauge. */
        private const val SIDE_MIN = 92f
        /** The line of "As of" with the refresh beside it: kept free under the pollen beside the chart too. */
        const val STATUS = 24f
        /** The line of "As of" without the room around it: the refresh alone in it. */
        private const val STATUS_LINE = 17f
        /** Kept free at the right for the refresh in the corner where "As of" has no line. */
        const val REFRESH_ROOM = 14

        /**
         * [alarm]: the next alarm as the widget writes it, null: none; [rainText]: the two hours'
         * line; [pollenText]: the pollen as points; [placeText] and [longDateText]: the head line's
         * place and the date with its weekday written out; [textWidth]: a text's width in dp in the
         * font of the line under the columns (13 sp) – measured where the font is known, else
         * estimated; [tuck]: the alarm's line may reach up under the clock (its margin is set from
         * Android 12 on).
         */
        fun of(
            config: WidgetConfig, size: WidgetSize, alarm: String?, weather: WidgetWeather?,
            fontScale: Float = 1f, rainText: String? = null, pollenText: String? = null,
            placeText: String? = null, longDateText: String? = null,
            lines: WidgetLines = WidgetLines.estimated(fontScale),
            textWidth: (String) -> Float = { it.length * CHAR * fontScale },
            tuck: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        ): WidgetLayout {
            val l = plan(config, size, alarm, weather, fontScale, rainText, pollenText, placeText, longDateText, lines, textWidth, tuck)
            if (l.status || l.refreshLine) return l
            // the refresh alone in the corner: the parts make way at the right (a widget is seldom short of width)
            // – the pollen beside the chart as at the full width: their third is only a little narrower
            return plan(config, size.copy(widthDp = size.widthDp - REFRESH_ROOM), alarm, weather, fontScale, rainText, pollenText, placeText, longDateText, lines, textWidth, tuck, sideFrom = REFRESH_ROOM)
                .copy(status = false, refreshLine = false, refreshRoom = true)
        }

        private fun plan(
            config: WidgetConfig, size: WidgetSize, alarm: String?, weather: WidgetWeather?,
            fontScale: Float, rainText: String?, pollenText: String?,
            placeText: String?, longDateText: String?, lines: WidgetLines,
            textWidth: (String) -> Float, tuck: Boolean,
            /** Width taken from the parts for the refresh, still counted for the pollen beside the chart. */
            sideFrom: Int = 0,
        ): WidgetLayout {
            val line = lines.text
            val midLine = MID_TOP + lines.mid
            val height = (size.heightDp - PADDING_V).toFloat()
            val width = (size.widthDp - PADDING_H).toFloat()
            // the pollen: those flying; chosen but none flying, a line saying so
            val pollenWanted = config.pollen.isNotEmpty() && weather?.pollen?.isNotEmpty() == true
            val pollenCount = if (pollenWanted) weather!!.pollenFlying.size else 0
            val rainWanted = config.rain && weather?.rain != null
            val rightWanted = config.weather || weather == null
            // narrow: one column on top – the weather when chosen
            val narrow = size.widthDp < 200
            val showClock = (config.clock || config.date) && !(narrow && rightWanted)
            val bigClock = showClock && config.clock
            val bigWeather = config.weather
            val perDp = (if (bigClock) CLOCK_W else 0f) + (if (bigWeather) WEATHER_W else 0f)
            val byWidth = if (perDp > 0f) (width - (if (bigWeather) WEATHER_FIX else 0f) - (if (showClock && rightWanted) GAP else 0f)) / perDp else BIG_MAX
            val secondLine = height >= 50
            val byHeight = (height - if (secondLine) lines.head else 0f) / BIG_LINE
            val date = showClock && config.date && (secondLine || !bigClock)
            val fitBig = minOf(BIG_MAX, byWidth, byHeight).coerceAtLeast(BIG_MIN)
            // the head line over both columns: the date and the place
            val headH = if (date || (rightWanted && secondLine)) lines.head else 0f
            val alarmWanted = showClock && config.alarm && alarm != null
            val perRow = (width / (POLLEN_CELL * fontScale)).toInt().coerceIn(1, 3)
            val pollenRowsWanted = (pollenCount + perRow - 1) / perRow
            val rainLines = if (rainText != null && textWidth(rainText) > width) 2 else 1

            class Plan(
                val big: Float, val below: Boolean, val beside: Boolean, val chart: Boolean, val rows: Int,
                val dots: Boolean, val side: Boolean, val rainLine: Boolean, val midRain: Boolean, val status: Boolean, val chartDp: Float,
                val refreshLine: Boolean = false, val tuck: Float = 0f,
            ) {
                val rainShown get() = rainLine || midRain
            }
            /** How far the alarm's line [below] the clock at [big] reaches up into the room under its figures. */
            fun tuckOf(below: Boolean, big: Float) = if (tuck && below && bigClock) TUCK * big else 0f
            val dotsW = pollenText?.let(textWidth) ?: 0f
            val rainW = rainText?.let(textWidth) ?: 0f
            val rain = weather?.rain
            // the chart only with something to draw; else, as when precipitation comes, the line comes first
            val chartWanted = rainWanted && rain?.eventful == true
            val rainFirst = rainWanted && (rain?.notice != null || !chartWanted)
            /**
             * The parts with the alarm [below] the clock or [beside] it (the figures smaller where it
             * would not fit beside); [rainBesideAlarm]: the two hours' line beside the alarm below.
             */
            fun plan(below: Boolean, beside: Boolean, rainBesideAlarm: Boolean = false): Plan {
                var big = fitBig
                val besideW = BESIDE_GAP + BESIDE_ICON + BESIDE_CHAR * (alarm?.length ?: 0) * fontScale
                if (beside) big = minOf(big, (width - (if (bigWeather) WEATHER_FIX else 0f) - (if (rightWanted) GAP else 0f) - besideW) / perDp)
                val bigH = if (bigClock || bigWeather) big * BIG_LINE else 0f
                // the line under both columns: the alarm (under the clock), beside it the two hours' line or the pollen as points
                val tucked = tuckOf(below, big)
                var left = height - lines.spare - headH - bigH - (if (below) midLine - tucked else 0f)
                fun fits(h: Float) = (left >= h).also { if (it) left -= h }
                val alarmW = if (below) ALARM_ICON + (alarm?.let(textWidth) ?: 0f) + GAP else 0f
                // right under the alarm's line the chart closer; a line between them takes the space it leaves
                val chart = chartWanted && fits((if (below) CHART_UNDER_ALARM else BELOW_GAP + CHART_TOP) + CHART_MIN + AXIS)
                fun rainLineFits(): Boolean {
                    val top = if (!chart) BELOW_GAP else if (below) BELOW_GAP + CHART_TOP - CHART_UNDER_ALARM else 0f
                    return fits(top + line * rainLines) || rainLines == 2 && fits(top + line)
                }
                // precipitation coming, or nothing to draw: the line over the chart – else in the line under the columns
                var midRain = rainBesideAlarm && below && rainWanted && rainW <= width - alarmW
                var rainLine = rainFirst && !midRain && rainLineFits()
                if (rainFirst && !rainLine && !midRain) midRain = rainW <= width - alarmW && (below || fits(midLine))
                // the pollen: beside the chart, one under the other; without the chart all as gauges
                // below, else as points in the line under the columns
                var rows = 0
                var dots = false
                // beside the chart only where its third holds a name and a gauge
                val side = chart && pollenWanted && (width + sideFrom - 12f) / 3f >= SIDE_MIN * fontScale
                if (pollenWanted && !side) {
                    when {
                        pollenRowsWanted > 0 && fits((if (chart || rainLine) POLLEN_TOP else BELOW_GAP) + pollenRowsWanted * lines.small) -> rows = pollenRowsWanted
                        !midRain && dotsW <= width - alarmW && (below || fits(midLine)) -> dots = true
                    }
                }
                if (rainWanted && !rainFirst && !midRain) rainLine = rainLineFits()
                // beside the chart under the pollen: the chart reaches down to its line
                val status = side || fits(STATUS * fontScale)
                // no room for "As of": its line for the refresh alone, where that fits
                val refreshLine = !status && fits(STATUS_LINE * fontScale)
                return Plan(big, below, beside, chart, rows, dots, side, rainLine, midRain, status, if (chart) CHART_MIN + left.coerceAtLeast(0f) else 0f, refreshLine, tucked)
            }
            val none = plan(below = false, beside = false)
            val p = when {
                !alarmWanted -> none
                else -> {
                    // the two hours' line beside the alarm where the pollen do not stand there as points
                    // and nothing else gives way: the chart gains its line
                    val under = plan(below = true, beside = false).let { u ->
                        plan(below = true, beside = false, rainBesideAlarm = true)
                            .takeIf { b -> u.chart && b.midRain && !u.dots && b.chart && b.rows >= u.rows && (b.status || !u.status) } ?: u
                    }
                    val by = if (bigClock) plan(below = false, beside = true).takeIf { it.big >= BIG_MIN } else null
                    val leftFits = height - headH - (if (bigClock || bigWeather) fitBig * BIG_LINE else 0f) >= midLine - tuckOf(true, fitBig)
                    // under the clock where there is the height – the pollen give way; the chart and a
                    // coming precipitation's line do not: else beside it
                    val keeps = under.chart == none.chart && (under.rainShown || !(by ?: none).rainShown || !rainFirst)
                    when {
                        leftFits && keeps -> under
                        by != null -> by
                        leftFits -> under
                        else -> none
                    }
                }
            }
            val besideW = BESIDE_GAP + BESIDE_ICON + BESIDE_CHAR * (alarm?.length ?: 0) * fontScale
            // the weekday written out where the place leaves the room for it
            val placeW = if (rightWanted && secondLine) PIN + (placeText?.length ?: 0) * HEAD_CHAR * fontScale + GAP else 0f
            val longDate = date && !config.dateWithWeek && (longDateText?.length ?: 22) * HEAD_CHAR * fontScale <= width - placeW
            return WidgetLayout(
                clock = bigClock, date = date, alarm = p.below, alarmBeside = p.beside && besideW > 0, weather = bigWeather, right = rightWanted,
                secondLine = secondLine, rain = p.chart || p.rainLine, rainLine = p.rainLine, midRain = p.midRain, chart = p.chart, chartDp = p.chartDp,
                pollenRows = p.rows, pollenPerRow = perRow, pollenDots = p.dots, pollenSide = p.side,
                pollenSideRows = if (p.side) ((p.chartDp + AXIS - STATUS * fontScale) / lines.small).toInt().coerceIn(1, 6) else 0, status = p.status, bigDp = p.big, longDate = longDate, widthDp = width,
                refreshLine = p.refreshLine, statusSide = p.side, tuckDp = p.tuck,
            )
        }
    }
}

object WidgetViews {
    const val EXTRA_PLACE = "dev.nimbus.weather.widget.PLACE"
    /** The part of the page a tap leads to ([dev.nimbus.weather.ui.main.PageSection]'s name). */
    const val EXTRA_SECTION = "dev.nimbus.weather.widget.SECTION"

    /** The rows of the pollen beside the chart: row, name, gauge. */
    private val SIDE_ROWS = listOf(
        Triple(R.id.widget_side_0, R.id.widget_side_0_name, R.id.widget_side_0_gauge),
        Triple(R.id.widget_side_1, R.id.widget_side_1_name, R.id.widget_side_1_gauge),
        Triple(R.id.widget_side_2, R.id.widget_side_2_name, R.id.widget_side_2_gauge),
        Triple(R.id.widget_side_3, R.id.widget_side_3_name, R.id.widget_side_3_gauge),
        Triple(R.id.widget_side_4, R.id.widget_side_4_name, R.id.widget_side_4_gauge),
        Triple(R.id.widget_side_5, R.id.widget_side_5_name, R.id.widget_side_5_gauge),
    )

    /** The pollen rows, and in them the cells: their row, name and gauge. */
    private val POLLEN_ROWS = listOf(
        R.id.widget_pollen_row_0, R.id.widget_pollen_row_1, R.id.widget_pollen_row_2, R.id.widget_pollen_row_3, R.id.widget_pollen_row_4,
        R.id.widget_pollen_row_5, R.id.widget_pollen_row_6, R.id.widget_pollen_row_7, R.id.widget_pollen_row_8,
    )
    private val POLLEN_CELLS = listOf(
        Triple(R.id.widget_pollen_0, R.id.widget_pollen_0_name, R.id.widget_pollen_0_gauge),
        Triple(R.id.widget_pollen_1, R.id.widget_pollen_1_name, R.id.widget_pollen_1_gauge),
        Triple(R.id.widget_pollen_2, R.id.widget_pollen_2_name, R.id.widget_pollen_2_gauge),
        Triple(R.id.widget_pollen_3, R.id.widget_pollen_3_name, R.id.widget_pollen_3_gauge),
        Triple(R.id.widget_pollen_4, R.id.widget_pollen_4_name, R.id.widget_pollen_4_gauge),
        Triple(R.id.widget_pollen_5, R.id.widget_pollen_5_name, R.id.widget_pollen_5_gauge),
        Triple(R.id.widget_pollen_6, R.id.widget_pollen_6_name, R.id.widget_pollen_6_gauge),
        Triple(R.id.widget_pollen_7, R.id.widget_pollen_7_name, R.id.widget_pollen_7_gauge),
        Triple(R.id.widget_pollen_8, R.id.widget_pollen_8_name, R.id.widget_pollen_8_gauge),
        Triple(R.id.widget_pollen_9, R.id.widget_pollen_9_name, R.id.widget_pollen_9_gauge),
        Triple(R.id.widget_pollen_10, R.id.widget_pollen_10_name, R.id.widget_pollen_10_gauge),
        Triple(R.id.widget_pollen_11, R.id.widget_pollen_11_name, R.id.widget_pollen_11_gauge),
        Triple(R.id.widget_pollen_12, R.id.widget_pollen_12_name, R.id.widget_pollen_12_gauge),
        Triple(R.id.widget_pollen_13, R.id.widget_pollen_13_name, R.id.widget_pollen_13_gauge),
        Triple(R.id.widget_pollen_14, R.id.widget_pollen_14_name, R.id.widget_pollen_14_gauge),
        Triple(R.id.widget_pollen_15, R.id.widget_pollen_15_name, R.id.widget_pollen_15_gauge),
        Triple(R.id.widget_pollen_16, R.id.widget_pollen_16_name, R.id.widget_pollen_16_gauge),
        Triple(R.id.widget_pollen_17, R.id.widget_pollen_17_name, R.id.widget_pollen_17_gauge),
        Triple(R.id.widget_pollen_18, R.id.widget_pollen_18_name, R.id.widget_pollen_18_gauge),
        Triple(R.id.widget_pollen_19, R.id.widget_pollen_19_name, R.id.widget_pollen_19_gauge),
        Triple(R.id.widget_pollen_20, R.id.widget_pollen_20_name, R.id.widget_pollen_20_gauge),
        Triple(R.id.widget_pollen_21, R.id.widget_pollen_21_name, R.id.widget_pollen_21_gauge),
        Triple(R.id.widget_pollen_22, R.id.widget_pollen_22_name, R.id.widget_pollen_22_gauge),
        Triple(R.id.widget_pollen_23, R.id.widget_pollen_23_name, R.id.widget_pollen_23_gauge),
        Triple(R.id.widget_pollen_24, R.id.widget_pollen_24_name, R.id.widget_pollen_24_gauge),
        Triple(R.id.widget_pollen_25, R.id.widget_pollen_25_name, R.id.widget_pollen_25_gauge),
        Triple(R.id.widget_pollen_26, R.id.widget_pollen_26_name, R.id.widget_pollen_26_gauge),
    )

    /**
     * The widget [id] showing [weather] (null: none yet – "Open Nimbus") as [config] chooses at
     * [size]; [placeId]: the place a tap opens; [updating]: a fetch is under way.
     */
    fun build(
        context: Context, id: Int, config: WidgetConfig, size: WidgetSize, weather: WidgetWeather?,
        placeId: String?, unit: TemperatureUnit, now: Long, updating: Boolean = false,
        precip: PrecipitationUnit = PrecipitationUnit.MM,
    ): RemoteViews {
        val res = context.resources
        val v = RemoteViews(context.packageName, R.layout.widget_nimbus)
        val use24h = android.text.format.DateFormat.is24HourFormat(context)
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val tf = TimeFormat(weather?.timezone ?: java.util.TimeZone.getDefault().id, use24h, locale)
        val alarm = nextAlarm(context)?.let { alarmText(it, now, use24h, locale) }
        val rain = weather?.rain
        // the precipitation card's words; dry: with "no sunshine" by day where none comes either
        val rainText = rain?.let { r ->
            r.notice?.let { n ->
                val words = precipNoticeText(res, n, now, tf)
                r.chance?.let { res.getString(R.string.widget_rain_chance, words, it) } ?: words
            }
                ?: res.getString(if (!r.eventful && weather.isDay) R.string.widget_rain_dry_dull else R.string.widget_rain_dry, tf.time(r.to))
        }
        val dots = weather?.pollen?.takeIf { it.isNotEmpty() && config.pollen.isNotEmpty() }?.let { pollenDots(context, it) }
        val placeText = weather?.place ?: res.getString(R.string.widget_no_data)
        val longDateText = android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now).toString()
        val density = res.displayMetrics.density
        // the line under the columns' font (13 sp) in dp
        val midFont = Paint().apply { textSize = 13 * res.configuration.fontScale }
        val l = WidgetLayout.of(
            config, size, alarm, weather, res.configuration.fontScale, rainText, dots?.toString(), placeText, longDateText,
            WidgetLines.measured(res.configuration.fontScale), textWidth = { midFont.measureText(it) },
        )

        v.setInt(R.id.widget_bg, "setImageAlpha", (config.opacity.coerceIn(0f, 1f) * 255).toInt())

        // left: the date, the time, the next alarm – the system keeps the clock and the date going
        v.visible(R.id.widget_clock_block, l.clock || l.date)
        v.visible(R.id.widget_clock_row, l.clock)
        v.visible(R.id.widget_date_row, l.date)
        // the month always written out, the weekday too where there is the room; or the date in figures with
        // the weekday short and the calendar week
        val datePattern = datePattern(
            if (config.dateWithWeek) "EEEddMMyyyy" else if (l.longDate) "EEEEdMMMM" else "EEEdMMMM",
            locale, systemLocale(context) ?: locale, now,
        )
        v.setCharSequence(R.id.widget_date, "setFormat24Hour", datePattern)
        v.setCharSequence(R.id.widget_date, "setFormat12Hour", datePattern)
        v.visible(R.id.widget_week, config.dateWithWeek)
        if (config.dateWithWeek) v.setTextViewText(R.id.widget_week, res.getString(R.string.widget_week, weekOf(now, locale)))
        v.visible(R.id.widget_clock, l.clock)
        v.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, l.bigDp)
        v.visible(R.id.widget_alarm_line, l.alarm)
        v.visible(R.id.widget_alarm_beside, l.alarmBeside)
        if (alarm != null) {
            v.setTextViewText(R.id.widget_alarm, alarm)
            v.setContentDescription(R.id.widget_alarm_line, res.getString(R.string.widget_alarm_desc, alarm))
            v.setTextViewText(R.id.widget_alarm_beside_text, alarm)
            v.setContentDescription(R.id.widget_alarm_beside, res.getString(R.string.widget_alarm_desc, alarm))
        }
        if (l.alarmBeside) {
            // where the widest time ends ("00:00": figures of one width), on the clock's baseline
            val big = clockPaint(l.bigDp * density)
            val small = Paint().apply { textSize = 12 * res.configuration.fontScale * density }
            v.setViewPadding(
                R.id.widget_alarm_beside, (big.measureText("00:00") + 6 * density).toInt(), 0, 0,
                (big.fontMetrics.descent - small.fontMetrics.descent).toInt().coerceAtLeast(0),
            )
        }
        val alarms = alarms(context, id)
        v.setOnClickPendingIntent(R.id.widget_clock, alarms)
        v.setOnClickPendingIntent(R.id.widget_alarm_line, alarms)
        v.setOnClickPendingIntent(R.id.widget_alarm_beside, alarms)
        val calendar = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, now) }.build()
        v.setOnClickPendingIntent(R.id.widget_date_row, activity(context, id, 2, Intent(Intent.ACTION_VIEW, calendar)))

        // the parts lead to theirs on the place's page: the weather to the hours, the precipitation to
        // today opened, the pollen to theirs; anywhere else to the page's head
        fun open(section: PageSection) = activity(
            context, id, 3 + section.ordinal,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_PLACE, placeId).putExtra(EXTRA_SECTION, section.name).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        v.setOnClickPendingIntent(android.R.id.background, open(PageSection.HEAD))
        v.setOnClickPendingIntent(R.id.widget_weather, open(PageSection.HOURLY))
        v.visible(R.id.widget_weather, l.right)
        v.setOnClickPendingIntent(R.id.widget_rain, open(PageSection.TODAY))
        v.setOnClickPendingIntent(R.id.widget_side, open(PageSection.POLLEN))
        v.setOnClickPendingIntent(R.id.widget_pollen, open(PageSection.POLLEN))
        v.visible(R.id.widget_place_row, l.right && (l.secondLine || weather == null))
        v.visible(R.id.widget_head, l.date || (l.right && (l.secondLine || weather == null)))
        val side = if (l.clock || l.date) android.view.Gravity.END else android.view.Gravity.START
        v.setInt(R.id.widget_place_row, "setGravity", (if (l.date) android.view.Gravity.END else side) or android.view.Gravity.CENTER_VERTICAL)
        v.setInt(R.id.widget_now, "setGravity", side or android.view.Gravity.CENTER_VERTICAL)
        v.visible(R.id.widget_now, l.weather && weather != null)
        if (weather == null) {
            v.visible(R.id.widget_pin, false)
            v.setTextViewText(R.id.widget_place, res.getString(R.string.widget_no_data))
        } else {
            v.visible(R.id.widget_pin, weather.myLocation)
            v.setTextViewText(R.id.widget_place, weather.place)
            v.setImageViewBitmap(R.id.widget_icon, symbol(weather, (l.bigDp * (if (weather.chance != null) 0.8f else 1f) * density).toInt()))
            v.setContentDescription(R.id.widget_icon, res.getString(Texts.condition(weather.condition, weather.isDay)))
            v.visible(R.id.widget_chance, weather.chance != null)
            // small under the symbol, the sign close to the figure
            weather.chance?.let { v.setTextViewText(R.id.widget_chance, "$it%") }
            v.setTextViewText(R.id.widget_temp, Units.temp(weather.temperature, unit))
            v.setTextViewTextSize(R.id.widget_temp, TypedValue.COMPLEX_UNIT_DIP, l.bigDp * 0.87f)
            v.visible(R.id.widget_maxmin, weather.max != null && weather.min != null)
            // two lines beside the temperature: never taller than it
            val small = minOf(13f * res.configuration.fontScale, l.bigDp * 0.45f)
            v.setTextViewTextSize(R.id.widget_max, TypedValue.COMPLEX_UNIT_DIP, small)
            v.setTextViewTextSize(R.id.widget_min, TypedValue.COMPLEX_UNIT_DIP, small)
            v.setTextViewText(R.id.widget_max, "↑" + Units.temp(weather.max, unit))
            v.setTextViewText(R.id.widget_min, "↓" + Units.temp(weather.min, unit))
        }

        // the next two hours: the precipitation card's line, the chart of precipitation and sunshine
        val lineShown = l.rainLine && rain != null && rainText != null
        val chartShown = l.chart && rain != null && rainText != null
        v.visible(R.id.widget_rain_text, lineShown)
        v.visible(R.id.widget_rain, chartShown)
        v.setOnClickPendingIntent(R.id.widget_rain_text, open(PageSection.TODAY))
        val chartTop = WidgetLayout.BELOW_GAP + WidgetLayout.CHART_TOP
        v.setViewPadding(R.id.widget_rain_text, 0, ((if (chartShown) WidgetLayout.LINE_OVER_CHART else WidgetLayout.BELOW_GAP) * density).toInt(), 0, 0)
        val chartPad = when {
            lineShown -> chartTop - WidgetLayout.LINE_OVER_CHART
            l.alarm -> WidgetLayout.CHART_UNDER_ALARM
            else -> chartTop
        }
        v.setViewPadding(R.id.widget_rain, 0, (chartPad * density).toInt(), 0, 0)
        if (rain != null && rainText != null) {
            v.setTextViewText(R.id.widget_rain_text, rainText)
            v.visible(R.id.widget_chart, l.chart)
            if (l.chart) {
                val fontScale = res.configuration.fontScale
                val chartPx = ((l.chartDp + AXIS_DP * fontScale) * density).toInt()
                val chartW = if (l.pollenSide) (l.widthDp - 12f) * 2f / 3f else l.widthDp
                v.setImageViewBitmap(R.id.widget_chart, chart(rain, (chartW.coerceAtLeast(60f) * density).toInt(), chartPx, density, scales(context, precip, locale, rainTop(rain, precip)), tf, fontScale))
            }
        }

        // beside the chart: the pollen flying, the strongest first, each with the pollen card's gauge; none: a line saying so
        v.visible(R.id.widget_side, l.pollenSide)
        v.setViewPadding(R.id.widget_side_rows, 0, 0, 0, if (l.statusSide) (WidgetLayout.STATUS * res.configuration.fontScale * density).toInt() else 0)
        val flying = weather?.pollenFlying.orEmpty()
        v.visible(R.id.widget_side_quiet, l.pollenSide && flying.isEmpty())
        if (l.pollenSide && flying.isEmpty() && weather != null) {
            v.setTextViewText(R.id.widget_side_quiet, quietPollen(context, 0f))
        }
        SIDE_ROWS.forEachIndexed { i, (row, name, gauge) ->
            val p = flying.getOrNull(i)?.takeIf { l.pollenSide && i < l.pollenSideRows }
            v.visible(row, p != null)
            if (p != null) {
                v.setTextViewText(name, res.getString(pollenName(p.first)))
                v.setImageViewBitmap(gauge, pollenGauge(p.second, (36 * density).toInt(), (7 * density).toInt(), density))
                v.setContentDescription(gauge, res.getString(pollenLevelText(p.second)))
            }
        }

        // today's pollen of the chosen types: as points in a line, or side by side with a gauge each, as the pollen card's
        // the line under the columns: the alarm, beside it the two hours' line or the pollen as points
        v.visible(R.id.widget_mid, l.alarm || l.pollenDots || l.midRain)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setViewLayoutMargin(R.id.widget_mid, RemoteViews.MARGIN_TOP, WidgetLayout.MID_TOP - l.tuckDp, TypedValue.COMPLEX_UNIT_DIP)
        }
        v.visible(R.id.widget_pollen_dots, l.pollenDots || l.midRain)
        if (l.midRain && rainText != null) v.setTextViewText(R.id.widget_pollen_dots, rainText)
        else if (dots != null) v.setTextViewText(R.id.widget_pollen_dots, dots)
        v.setOnClickPendingIntent(R.id.widget_pollen_dots, open(if (l.midRain) PageSection.TODAY else PageSection.POLLEN))
        v.setInt(R.id.widget_pollen_dots, "setGravity", (if (l.midRain && !l.alarm) android.view.Gravity.START else android.view.Gravity.END) or android.view.Gravity.CENTER_VERTICAL)
        v.visible(R.id.widget_pollen, l.pollenRows > 0)
        POLLEN_ROWS.forEachIndexed { r, row -> v.visible(row, r < l.pollenRows) }
        POLLEN_CELLS.forEachIndexed { i, (cell, name, gauge) ->
            val r = i / 3
            val c = i % 3
            val k = r * l.pollenPerRow + c
            val p = weather?.pollenFlying?.getOrNull(k)?.takeIf { c < l.pollenPerRow && r < l.pollenRows }
            // a cell of the row with no type left keeps its room: the gauges stay in their columns
            v.setViewVisibility(cell, if (c >= l.pollenPerRow) View.GONE else if (p == null) View.INVISIBLE else View.VISIBLE)
            if (p != null) {
                v.setTextViewText(name, res.getString(pollenName(p.first)))
                v.setImageViewBitmap(gauge, pollenGauge(p.second, (40 * density).toInt(), (7 * density).toInt(), density))
                v.setContentDescription(gauge, res.getString(pollenLevelText(p.second)))
            }
        }

        // when the data were fetched; a tap on the refresh in the corner – there at any size – fetches them anew
        v.visible(R.id.widget_status, (l.status && !l.statusSide) || l.refreshLine)
        v.visible(R.id.widget_as_of_side, l.statusSide)
        // its line kept for the refresh, the words left out
        v.setViewVisibility(R.id.widget_as_of, if (l.status) View.VISIBLE else View.INVISIBLE)
        val pad = (14 * density).toInt()
        v.setViewPadding(R.id.widget_content, pad, (8 * density).toInt(), pad + if (l.refreshRoom) (WidgetLayout.REFRESH_ROOM * density).toInt() else 0, (8 * density).toInt())
        v.setTextViewText(
            if (l.statusSide) R.id.widget_as_of_side else R.id.widget_as_of,
            when {
                updating -> res.getString(R.string.widget_updating)
                weather == null -> ""
                tf.isSameDay(weather.asOf, now) -> res.getString(R.string.widget_as_of, tf.time(weather.asOf))
                else -> res.getString(R.string.widget_as_of, tf.dayMonth(weather.asOf) + " " + tf.time(weather.asOf))
            },
        )
        v.setContentDescription(R.id.widget_refresh, res.getString(R.string.widget_refresh))

        // the height spread in equal gaps between what does not belong together: the head with the
        // clock, the weather and the alarm; the two hours, their line midway between the head and the
        // chart; the pollen; the time of the data at the bottom (beside the chart: on its times' line).
        // The line under the columns without the alarm stands on its own.
        val parts = listOf(
            R.id.widget_gap_top to false,
            R.id.widget_gap_mid to (!l.alarm && (l.pollenDots || l.midRain)),
            R.id.widget_gap_rain to (l.rain && rain != null),
            R.id.widget_gap_chart to (lineShown && chartShown),
            R.id.widget_gap_pollen to (l.pollenRows > 0),
            R.id.widget_gap_status to ((l.status && !l.statusSide) || l.refreshLine),
        )
        var before = true
        for ((gap, shown) in parts) {
            v.visible(gap, shown && before)
            before = before || shown
        }
        v.setOnClickPendingIntent(R.id.widget_refresh, refresh(context, id))
        return v
    }

    /** The clock's font at [px]: light, its figures of one width. */
    fun clockPaint(px: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        textSize = px
        fontFeatureSettings = "tnum"
    }

    private fun RemoteViews.visible(viewId: Int, on: Boolean) = setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)

    private fun activity(context: Context, id: Int, kind: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, id * 8 + kind, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun refresh(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, NimbusWidget::class.java).setAction(NimbusWidget.ACTION_REFRESH).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * The phone's language – the one the launcher's clock writes in. Nimbus set to a language of its
     * own (Android 13 on), its process sees that one everywhere, the system's resources too: the
     * locale manager knows the phone's.
     */
    private fun systemLocale(context: Context): Locale? =
        if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java)?.systemLocales?.get(0)
        else android.content.res.Resources.getSystem().configuration.locales[0]

    /**
     * The date's pattern for [skeleton] in Nimbus's language [locale]. The system's clock writes
     * the weekday's and the month's names in the phone's language [system]: where Nimbus speaks
     * another, the names of [now] go in as text ("Sunday, October 25", not "Sonntag, October 25")
     * – the widget is drawn anew just after midnight, the day's figure the clock keeps going.
     */
    fun datePattern(skeleton: String, locale: Locale, system: Locale, now: Long): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        if (system.language == locale.language) return pattern
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                // quoted text stays as it is
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length - 1 else it }
                out.append(pattern, i, end + 1)
                i = end + 1
                continue
            }
            var j = i
            while (j < pattern.length && pattern[j] == c) j++
            val run = pattern.substring(i, j)
            val named = (c == 'E' || c == 'c') || ((c == 'M' || c == 'L') && run.length >= 3)
            if (named) {
                val name = java.text.SimpleDateFormat(run.replace('c', 'E').replace('L', 'M'), locale).format(java.util.Date(now))
                out.append('\'').append(name.replace("'", "''")).append('\'')
            } else out.append(run)
            i = j
        }
        return out.toString()
    }

    /** The calendar week of [now] on the phone's clock, counted as is usual where [locale] is (ISO weeks in Germany). */
    fun weekOf(now: Long, locale: Locale): Int =
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .get(java.time.temporal.WeekFields.of(locale).weekOfWeekBasedYear())

    /**
     * What a tap on the time opens: the alarms. The next alarm's own entry where it opens a page –
     * ColorOS's only tells its clock to update the status bar –, then the standard request where
     * Nimbus may send it: some clocks answer it only to apps allowed to set alarms (ColorOS's), and
     * Nimbus asks for no such right. Else the clock app that answers the request, a clock app
     * known by its name, any app whose name says clock.
     */
    fun alarms(context: Context, id: Int): PendingIntent {
        runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.showIntent }.getOrNull()
            ?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.isActivity }?.let { return it }
        return activity(context, id, 1, alarmsIntent(context))
    }

    /** The clock apps of the makers, by their package names. */
    internal val CLOCK_APPS = listOf(
        "com.google.android.deskclock", "com.coloros.alarmclock", "com.oplus.alarmclock", "com.oneplus.deskclock",
        "com.sec.android.app.clockpackage", "com.android.deskclock", "com.huawei.deskclock", "com.hihonor.deskclock",
        "com.asus.deskclock", "com.lge.clock",
    )

    /** The alarms by the standard request where Nimbus may send it, else a clock app (see [alarms]). */
    internal fun alarmsIntent(context: Context): Intent {
        val pm = context.packageManager
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val answers = pm.queryIntentActivities(show, 0).map { it.activityInfo }
        if (answers.any { a -> a.permission.let { it == null || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } }) return show
        (answers.map { it.packageName } + CLOCK_APPS).firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }?.let { return it }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0).firstOrNull { r ->
            val name = r.activityInfo.packageName.lowercase()
            "clock" in name || "alarm" in name
        }?.let { r -> return Intent(launcher).setClassName(r.activityInfo.packageName, r.activityInfo.name) }
        return show
    }

    /** When the next alarm rings (the system's clock app sets it), or null. */
    fun nextAlarm(context: Context): Long? =
        runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime }.getOrNull()

    /** "6:30" within the next day, "Mo 6:30" after. */
    fun alarmText(at: Long, now: Long, use24h: Boolean, locale: Locale): String {
        val tf = TimeFormat(java.util.TimeZone.getDefault().id, use24h, locale)
        return if (at - now < 20 * 3_600_000L) tf.time(at) else tf.weekdayShort(at) + " " + tf.time(at)
    }

    /** The weather symbol of the page's head, [px] square, with a soft shadow: it stands out on light and dark alike. */
    fun symbol(weather: WidgetWeather, px: Int): Bitmap {
        val glyph = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val inset = px * 0.08f
        val inner = px - 2 * inset
        val canvas = Canvas(glyph)
        canvas.translate(inset, inset)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(inner, inner)) {
            drawWeatherSymbol(weather.condition, weather.isDay, weather.wind)
        }
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val offset = IntArray(2)
        val shadow = glyph.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(px * 0.04f, BlurMaskFilter.Blur.NORMAL) }, offset)
        c.drawBitmap(shadow, offset[0].toFloat(), offset[1] + px * 0.015f, Paint().apply { color = 0x59102030 })
        c.drawBitmap(glyph, 0f, 0f, null)
        return out
    }

    /** Grey lines and empty tracks: seen on the light and the dark background alike. */
    private const val GREY = 0x66808080

    /** The times under the chart, by font scale 1. */
    private const val AXIS_DP = 14f
    /** Grey of the times: read on the light and the dark background alike. */
    private const val TIME_GREY = 0xFF8C8C94.toInt()

    /** The sunshine's scale: its share of the quarter hour, all of it at the top. */
    private const val SUN_TOP = 15.0

    /**
     * The labels of the chart's two scales: their units over the axes, the values at their ticks;
     * [rainTopMm]: the precipitation (mm/h) at the top, its half in the middle.
     */
    data class Scales(val sunUnit: String, val sunTop: String, val sunMid: String, val rainUnit: String, val rainTop: String, val rainMid: String, val rainTopMm: Double)

    /**
     * The precipitation at the chart's top in mm/h, as the meteogram finds its own: the strongest
     * bar of [rain] rounded up to a whole mm/h (0.05 in/h), at least 1 mm/h (0.04 in/h) – a linear
     * scale from 0 whose top the rain fills.
     */
    fun rainTop(rain: WidgetWeather.Rain, unit: PrecipitationUnit): Double {
        val inch = unit == PrecipitationUnit.INCH
        val most = Units.precipitationValue(rain.quarters.filter { it.wet || it.possible }.maxOfOrNull { it.rate } ?: 0.0, unit)
        val top = maxOf(if (inch) 0.04 else 1.0, most).let { if (inch) kotlin.math.ceil(it * 20) / 20 else kotlin.math.ceil(it) }
        return if (inch) top * 25.4 else top
    }

    /** The scales in [unit] (precipitation, [rainTopMm] mm/h at the top) and the language of [locale]. */
    fun scales(context: Context, unit: PrecipitationUnit, locale: Locale, rainTopMm: Double): Scales {
        val f = java.text.DecimalFormat(if (unit == PrecipitationUnit.INCH) "0.###" else "0.#", java.text.DecimalFormatSymbols.getInstance(locale))
        val top = Units.precipitationValue(rainTopMm, unit)
        return Scales(
            "%", "100", "50",
            context.getString(Texts.precipUnit(unit)) + "/h", f.format(top), f.format(top / 2), rainTopMm,
        )
    }

    /** Where the chart's drawing lies within its picture: the room its scales take at the left, right and top, in px. */
    data class Plot(val left: Float, val right: Float, val top: Float)

    private fun labelPaint(color: Int, density: Float, fontScale: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = 10 * fontScale * density
    }

    /** The room the [s] labels take beside and above the drawing. */
    fun plot(s: Scales, density: Float, fontScale: Float): Plot {
        val p = labelPaint(0, density, fontScale)
        val pad = 4 * density
        return Plot(
            maxOf(p.measureText(s.sunTop), p.measureText(s.sunMid), p.measureText(s.sunUnit)) + pad,
            maxOf(p.measureText(s.rainTop), p.measureText(s.rainMid), p.measureText(s.rainUnit)) + pad,
            // the units over the axes, some room down to the top values
            p.fontSpacing + 5 * density,
        )
    }

    /**
     * The quarter hours of [rain] over the two hours on one axis, [w] × [h] px – as the day charts
     * draw an expected hour, in unfilled frames of their colours: the sunshine behind, a little
     * wider than the precipitation in front of it, its top seen through the precipitation's bar.
     * Each over its quarter hour as the notice reads it, from the running one on, a tick under
     * each and the clock's quarter hours under them – as many as there is room for, evenly from
     * the start to the end (every half hour, 45 minutes, hour, or the two ends); a fine line at now. Two scales in the colours of their bars: on the left how
     * much of the quarter hour is sunny (all of it fills the height), on the right the precipitation
     * per hour, linear from 0 to [Scales.rainTopMm] ([rainTop]); their units over them.
     */
    fun chart(rain: WidgetWeather.Rain, w: Int, h: Int, density: Float, s: Scales, tf: TimeFormat, fontScale: Float = 1f): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val plot = plot(s, density, fontScale)
        val x0 = plot.left
        val x1 = w - plot.right
        val span = (rain.to - rain.from).toFloat()
        fun x(t: Long) = x0 + ((t - rain.from) / span).coerceIn(0f, 1f) * (x1 - x0)
        val gap = density * 2f
        val narrower = density * 1.5f
        val times = labelPaint(TIME_GREY, density, fontScale * 1.1f).apply { textAlign = Paint.Align.CENTER }
        val top = plot.top + density * 2
        val base = h - AXIS_DP * fontScale * density - density
        fun sunY(m: Double) = base - (m / SUN_TOP).toFloat().coerceIn(0f, 1f) * (base - top)
        fun rainY(mmH: Double) = base - (mmH / s.rainTopMm).coerceIn(0.0, 1.0).toFloat() * (base - top)
        val sunFrame = HourBars.Sun.frame.toArgb()
        val rainFrame = HourBars.Rain.frame.toArgb()
        for (q in rain.quarters) {
            val l = x(q.at) + gap / 2
            val r = x(q.at + 15 * 60_000L) - gap / 2
            if (r - 2 * narrower <= l) continue
            // both unfilled: the sunshine's top stays seen through the precipitation's bar in front
            q.sunMinutes?.takeIf { it >= WidgetWeather.Rain.SUN_LEAST }?.let { m -> frame(c, RectF(l, sunY(m), r, base), sunFrame, 1.5f * density, density) }
            // perhaps drizzle (an echo too weak to be sure): the bar faint
            if (q.wet || q.possible) frame(c, RectF(l + narrower, rainY(q.rate), r - narrower, base), if (q.wet) rainFrame else faint(rainFrame), 2f * density, density)
        }
        // the axes: the ground line with a tick each quarter hour and their clock times, now, the two scales at the sides
        val line = Paint().apply { color = GREY; strokeWidth = density }
        c.drawLine(x0, base + density / 2, x1, base + density / 2, line)
        val quarter = 15 * 60_000L
        val marks = ((rain.to - rain.from) / quarter).toInt()
        val labelW = times.measureText(tf.time(rain.from)) + 8 * density
        // as many times as there is room for, the end among them – or the start alone
        val every = (2..marks).filter { marks % it == 0 }.firstOrNull { (x1 - x0) * it / marks >= labelW } ?: (marks + 1)
        for (k in 0..marks) {
            val t = rain.from + k * quarter
            c.drawLine(x(t), base, x(t), base + 3 * density, line)
            if (k % every == 0) {
                val label = tf.time(t)
                val tx = x(t).coerceIn(times.measureText(label) / 2, w - times.measureText(label) / 2)
                c.drawText(label, tx, h - times.descent(), times)
            }
        }
        val nowX = x(rain.now)
        val dash = Paint(line).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(2 * density, 2 * density), 0f) }
        if (rain.now > rain.from) c.drawLine(nowX, top, nowX, base, dash)
        c.drawLine(x0, top, x0, base, line)
        c.drawLine(x1, top, x1, base, line)
        // the units in the colours of their bars, the values grey; the middle values only where the drawing has the height
        val sunText = labelPaint(sunFrame, density, fontScale)
        val rainText = labelPaint(rainFrame, density, fontScale).apply { textAlign = Paint.Align.LEFT }
        val values = labelPaint(TIME_GREY, density, fontScale)
        val half = -(sunText.ascent() + sunText.descent()) / 2
        val roomy = base - top >= 34 * density
        fun tick(x: Float, y: Float, toLeft: Boolean) = c.drawLine(x, y, x + if (toLeft) -3 * density else 3 * density, y, line)
        // left: how much of the quarter hour is sunny
        for ((m, label) in listOf(SUN_TOP to s.sunTop, SUN_TOP / 2 to s.sunMid)) {
            val y = sunY(m)
            tick(x0, y, true)
            if (m == SUN_TOP || roomy) c.drawText(label, x0 - 4 * density - values.measureText(label), (y + half).coerceAtLeast(top + half), values)
        }
        // the unit over its values, ending where they end
        c.drawText(s.sunUnit, x0 - 4 * density - sunText.measureText(s.sunUnit), -sunText.ascent(), sunText)
        // right: the precipitation per hour
        for ((v, label) in listOf(s.rainTopMm to s.rainTop, s.rainTopMm / 2 to s.rainMid)) {
            val y = rainY(v)
            tick(x1, y, false)
            if (v == s.rainTopMm || roomy) c.drawText(label, x1 + 4 * density, (y + half).coerceAtLeast(top + half), values)
        }
        c.drawText(s.rainUnit, x1 + 4 * density, -rainText.ascent(), rainText)
        return bmp
    }

    /** [color] at half its opacity. */
    private fun faint(color: Int) = (((color ushr 24) / 2) shl 24) or (color and 0xFFFFFF)

    /** An unfilled frame, the line inside [r]: an expected amount, as the day charts draw it. */
    private fun frame(c: Canvas, r: RectF, color: Int, stroke: Float, density: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke }
        if (r.height() < stroke) {
            c.drawLine(r.left, r.bottom - stroke / 2, r.right, r.bottom - stroke / 2, p)
            return
        }
        c.drawRoundRect(RectF(r.left + stroke / 2, r.top + stroke / 2, r.right - stroke / 2, r.bottom - stroke / 2), 2 * density, 2 * density, p)
    }

    /** From this level on a type is worth its point: yellow (low to medium, medium) and red – green is the rule, not news. */
    private const val NOTEWORTHY = 1f

    /**
     * "● Birch  ● Grasses": the types that fly noteworthily, each with a point in the pollen card's
     * colour of its level – yellow up to medium, red above. None of them: the highest level in
     * words ("Pollen: none to low") – so the line says the pollen were looked at.
     */
    /**
     * "Pollen: none to low": the level's words after the colon, only their first letter small –
     * a German noun keeps its capital ("keine Belastung").
     */
    fun quietPollen(context: Context, level: Float): String =
        context.getString(R.string.widget_pollen_quiet, context.getString(pollenLevelText(level)).replaceFirstChar { it.lowercase(context.resources.configuration.locales[0]) })

    fun pollenDots(context: Context, pollen: List<Pair<dev.nimbus.weather.data.model.PollenType, Float>>): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        val noteworthy = pollen.filter { it.second > NOTEWORTHY }
        if (noteworthy.isEmpty()) {
            val highest = pollen.maxOf { it.second }
            return quietPollen(context, highest)
        }
        noteworthy.forEachIndexed { i, (type, level) ->
            if (i > 0) sb.append("  ")
            val start = sb.length
            sb.append("●")
            sb.setSpan(android.text.style.ForegroundColorSpan(pollenLevelColor(level).toArgb() or (0xFF shl 24)), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append('\u00A0').append(context.getString(pollenName(type)))
        }
        return sb
    }

    /** Today's [level] (0..3) in three segments, half steps half filled – the pollen card's gauge, [w] × [h] px. */
    fun pollenGauge(level: Float, w: Int, h: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gap = 2 * density
        val sw = (w - 2 * gap) / 3
        val r = h / 2f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREY }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pollenLevelColor(level).toArgb() }
        for (i in 0 until 3) {
            val x = i * (sw + gap)
            c.drawRoundRect(RectF(x, 0f, x + sw, h.toFloat()), r, r, track)
            val f = (level - i).coerceIn(0f, 1f)
            if (f > 0f) c.drawRoundRect(RectF(x, 0f, x + sw * f, h.toFloat()), r, r, fill)
        }
        return bmp
    }
}
