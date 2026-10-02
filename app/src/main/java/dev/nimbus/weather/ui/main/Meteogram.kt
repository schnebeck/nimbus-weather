/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/Meteogram.kt
 * The day's meteogram: temperature, precipitation, sunshine and wind hour by hour.
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

package dev.nimbus.weather.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt

private val PrecipBar = PrecipStyle.Bar
// Sunshine columns: wide and faint, so rain bars and the temperature curve stay readable on top.
// Sunshine row: light grey, no outline – the temperature curve has the warm colours.
private val SunFill = Color(0xFFC3C9D2)
private val SunTrack = Color(0x14FFFFFF)
// Day slightly lighter, night clearly darker than the card: the two must be told apart at a glance.
internal val DayTint = Color(0x14FFFFFF)
internal val NightShade = Color(0x47000000)
private val GridLine = Color(0x1FFFFFFF)
// Look-back: the forecast dashed in white next to the measured curve in the temperature colours
private val ForecastLine = Color(0xD9FFFFFF)
private const val CURSOR_TIMEOUT_MS = 10_000L

/** Wind colour by speed (km/h): calm white → Bft 6 yellow → gale orange → storm red. */
fun windColor(kmh: Double): Color = when {
    kmh < 39 -> Color.White
    kmh < 62 -> Color(0xFFFFE08A)
    kmh < 89 -> Color(0xFFFFA54A)
    else -> Color(0xFFFF5A4A)
}

/**
 * One hour of a meteogram. For the look back [temperature] etc. are measurements and
 * [forecastTemperature] / [forecastPrecipitation] what the model had predicted.
 */
data class MeteoPoint(
    val time: Long,
    val temperature: Double,
    val condition: Condition,
    val isDay: Boolean,
    val precipitation: Double?,
    val precipitationChance: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGust: Double? = null,
    val humidity: Double? = null,
    val apparentTemperature: Double? = null,
    val forecastTemperature: Double? = null,
    val forecastPrecipitation: Double? = null,
    /** Minutes of sunshine in the hour before [time]. */
    val sunshine: Double? = null,
    /** Look-back of today: an hour still to come – only the forecast, drawn dashed and paler. */
    val forecastOnly: Boolean = false,
    /** Today in the forecast: an hour already over, with the station's readings in place of the forecast. */
    val measured: Boolean = false,
    /** Look-back: what was measured and what was forecast, kept apart for the readout table. */
    val compare: HourCompare? = null,
)

/** One hour of the look-back: measured (M) and forecast (F) values; null where there is none. */
data class HourCompare(
    val tempM: Double?, val tempF: Double?,
    val precipM: Double?, val precipF: Double?, val chanceF: Double?,
    val windM: Double?, val windDirM: Double?, val windF: Double?,
    val gustM: Double?, val gustF: Double?,
    val sunM: Double?, val sunF: Double?,
)

fun HourlyPoint.toMeteo() = MeteoPoint(
    time, temperature, condition, isDay, precipitation, precipitationProbability,
    windSpeed, windDirection, windGust, humidity, apparentTemperature, sunshine = sunshine,
)

/** Night intervals from sunrise/sunset of the daily forecast. */
fun nightsFromDaily(daily: List<DailyPoint>, start: Long, end: Long): List<LongRange> =
    daily.filter { it.date < end && it.date + 24 * 3_600_000L > start }.flatMap { d ->
        val rise = d.sunrise ?: return@flatMap emptyList()
        val set = d.sunset ?: return@flatMap emptyList()
        listOf(d.date until rise, set until d.date + 24 * 3_600_000L)
    }

/** Night intervals from the hourly day/night flag (when no sunrise/sunset is at hand). */
fun nightsFromFlags(points: List<MeteoPoint>): List<LongRange> =
    points.zipWithNext().filter { (a, b) -> !a.isDay && !b.isDay }.map { (a, b) -> a.time until b.time } +
        // a night at the last point lasts its hour (the look-back ends at 23:00, not at 24:00 –
        // the last hour was drawn as day)
        listOfNotNull(points.lastOrNull()?.takeIf { !it.isDay }?.let { it.time until it.time + 3_600_000L })

/**
 * Compact meteogram of [start]..[end] (one day 00–24 h): time labels and weather symbols every
 * 3 h, temperature (left axis; in comparison mode measured white and forecast dashed), hourly
 * precipitation (bars, right axis), wind arrows, night shading. A long press shows a cursor –
 * dragging moves it, it fades out after 10 s. Plain swipes stay free for paging.
 */
private val NowMark = Color(0xB3FFFFFF)

@Composable
fun Meteogram(
    points: List<MeteoPoint>, start: Long, end: Long, nights: List<LongRange>, now: Long, modifier: Modifier = Modifier,
    /** Fixed mark at the current time (not interactive, independent of the cursor). */
    showNow: Boolean = false,
    /**
     * The temperature curve in any resolution (°C; 10-minute readings, 15-minute forecast …);
     * null: from the hourly [points]. In comparison mode the measured curve.
     */
    curve: List<CurvePoint>? = null,
    /** Comparison mode: the forecast curve (dashed); null: from the hourly forecast values. */
    forecastCurve: List<CurvePoint>? = null,
) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pts = remember(points, start, end) { points.filter { it.time in start..end }.sortedBy { it.time } }
    if (pts.size < 2) return
    val compare = pts.any { it.forecastTemperature != null || it.compare != null }
    val span = (end - start).toFloat()
    // The cursor stands on an hour (the value of the hour before its time stamp): today the hour
    // running now, else midday
    var selected by remember(start) {
        mutableStateOf(pts.indexOfFirst { it.time > now }.takeIf { now in start..end && it > 0 } ?: pts.indexOfFirst { it.time >= start + 12 * 3_600_000L }.coerceAtLeast(1))
    }
    var cursorOn by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(touched, cursorOn) {
        if (cursorOn) { delay(CURSOR_TIMEOUT_MS); cursorOn = false }
    }
    val cursorAlpha by animateFloatAsState(if (cursorOn) 1f else 0f, tween(if (cursorOn) 150 else 700), label = "cursor")

    val temps = pts.map { Units.temperature(it.temperature, s.temperatureUnit) }
    val fTemps = pts.map { p -> p.forecastTemperature?.let { Units.temperature(it, s.temperatureUnit) } }
    // The curves (°C): as given, else one point per hour (hours still to come in the look-back
    // of today have no measured curve)
    val mainCurve = remember(curve, pts) {
        (curve ?: pts.filter { !it.forecastOnly }.map { CurvePoint(it.time, it.temperature) }).filter { it.time in start - 3_600_000L..end }
    }
    val dashCurve = remember(forecastCurve, pts) {
        if (!compare) emptyList()
        else (forecastCurve ?: pts.mapNotNull { p -> p.forecastTemperature?.let { CurvePoint(p.time, it) } }).filter { it.time in start - 3_600_000L..end }
    }
    val allT = temps + fTemps.filterNotNull() + (mainCurve + dashCurve).map { Units.temperature(it.value, s.temperatureUnit) }
    val tLo = floor(allT.min() - 1).toInt()
    // Even range, so the middle grid line is a whole degree as well.
    val tHi = ceil(allT.max() + 1).toInt().let { if (it - tLo < 4) tLo + 4 else it }.let { if ((it - tLo) % 2 == 1) it + 1 else it }
    val inch = s.precipitationUnit == PrecipitationUnit.INCH
    val precipMax = maxOf(
        if (inch) 0.04 else 1.0,
        pts.maxOf { Units.precipitationValue(maxOf(it.precipitation ?: 0.0, it.forecastPrecipitation ?: 0.0), s.precipitationUnit) },
    )
        .let { if (inch) ceil(it * 20) / 20 else ceil(it) }
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val unitStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary, fontWeight = FontWeight.SemiBold)
    val tUnit = if (s.temperatureUnit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    // Symbols and wind arrows every 3 hours, in the column of their hour's label, bar and cursor
    // (00:30, 03:30 …): the values of that hour (00–01, 03–04 …).
    val blocks = remember(pts, start, end) {
        generateSequence(start) { it + 3 * 3_600_000L }.takeWhile { it < end }.mapNotNull { b ->
            val centre = b + 30 * 60_000L
            (pts.firstOrNull { it.time == b + 3_600_000L } ?: pts.minByOrNull { kotlin.math.abs(it.time - centre) })?.let { centre to it }
        }.toList()
    }

    // Vertical layout: time labels | symbols | plot | wind arrows. Units sit in the top corners,
    // the axes carry numbers only and are exactly as wide as their widest label.
    fun precipLabel(k: Int): String {
        val pv = precipMax * k / 2
        return when {
            k == 0 -> "0"
            inch -> String.format(Locale.getDefault(), "%.2f", pv)
            else -> Units.oneDecimal(pv)
        }
    }
    val gap = 3.dp
    val axisL = with(density) {
        (0..2).maxOf { k -> measurer.measure("${(tLo + (tHi - tLo) * k / 2.0).roundToInt()}", labelStyle).size.width }.toDp()
    } + gap
    // Daily totals (the first point, 00:00, belongs to the hour before the day)
    val dayPts = pts.filter { it.time > start }
    // Totals of what happened (the look-back of today leaves out the hours still to come)
    val sunTotalMin = dayPts.filter { !it.forecastOnly }.mapNotNull { it.sunshine }.takeIf { it.isNotEmpty() }?.sum()
    val precipTotal = dayPts.filter { !it.forecastOnly }.mapNotNull { it.precipitation }.takeIf { it.isNotEmpty() }?.sum()
    // Look-back: the forecast's total next to the measured one
    val forecastTotal = if (compare) dayPts.mapNotNull { it.forecastPrecipitation }.takeIf { it.isNotEmpty() }?.sum() else null
    val axisR = with(density) {
        listOf(
            (0..2).maxOf { measurer.measure(precipLabel(it), labelStyle).size.width },
            measurer.measure(pUnit, unitStyle).size.width,
        ).max().toDp()
    } + gap
    val labelsH = 14.dp
    val iconsH = 30.dp
    val plotH = 120.dp
    val windH = 26.dp
    // The sunshine row keeps a small gap to the plot, where the lowest axis numbers reach down.
    val sunGap = 6.dp
    val sunH = if (sunTotalMin != null) 20.dp + sunGap else 0.dp

    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(labelsH + iconsH + plotH + windH + sunH)) {
            val plotW = maxWidth - axisL - axisR
            fun xDp(t: Long) = axisL + plotW * ((t - start) / span)
            // Touch: the hour whose bar is nearest (same geometry as the drawing, in dp)
            val touchAxis = HourAxis(start, end, axisL.value, (axisL + plotW).value)
            val times = pts.map { it.time }
            fun indexAt(xPx: Float): Int = touchAxis.indexAt(with(density) { xPx.toDp() }.value, times)
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(pts) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { cursorOn = true; selected = indexAt(it.x); touched++ },
                        ) { change, _ ->
                            change.consume()
                            selected = indexAt(change.position.x)
                            touched++
                        }
                    }
                    .pointerInput(pts) {
                        detectTapGestures(onTap = { if (cursorOn) { selected = indexAt(it.x); touched++ } })
                    },
            ) {
                val l = axisL.toPx(); val r = size.width - axisR.toPx()
                val top = (labelsH + iconsH).toPx(); val bottom = top + plotH.toPx()
                fun x(t: Long) = l + (r - l) * ((t - start) / span)
                fun yT(v: Double) = (bottom - (v - tLo) / (tHi - tLo) * (bottom - top)).toFloat()
                fun yP(v: Double) = (bottom - v / precipMax * (bottom - top)).toFloat()
                val below = sunH.toPx() + windH.toPx()   // sunshine row and wind arrows under the plot

                drawRect(DayTint, Offset(l, top), Size(r - l, bottom - top + below))
                nights.forEach { n ->
                    val a = maxOf(n.first, start); val b = minOf(n.last + 1, end)
                    if (b > a) drawRect(NightShade, Offset(x(a), top), Size(x(b) - x(a), bottom - top + below))
                }
                // Units in the top corners (free: symbols sit in the block middles), numbers on the axes
                // Units sit clearly above the top axis numbers (which are centred on the top line).
                val ut = measurer.measure(tUnit, unitStyle)
                val unitY = top - measurer.measure("0", labelStyle).size.height / 2f - 4.dp.toPx()
                drawText(ut, topLeft = Offset(0f, unitY - ut.size.height))
                val up = measurer.measure(pUnit, unitStyle)
                drawText(up, topLeft = Offset(size.width - up.size.width, unitY - up.size.height))
                for (k in 0..2) {
                    val v = tLo + (tHi - tLo) * k / 2.0
                    drawLine(GridLine, Offset(l, yT(v)), Offset(r, yT(v)), 1f)
                    val lt = measurer.measure("${v.roundToInt()}", labelStyle)
                    drawText(lt, topLeft = Offset(l - lt.size.width - gap.toPx(), yT(v) - lt.size.height / 2f))
                    val lp = measurer.measure(precipLabel(k), labelStyle)
                    drawText(lp, topLeft = Offset(r + gap.toPx(), yP(precipMax * k / 2) - lp.size.height / 2f))
                }
                // Time labels every 3 hours in the middle of their hour: under its bar and cursor
                val labelAxis = HourAxis(start, end, l, r)
                labelAxis.labelHours().forEach { hs ->
                    val xm = labelAxis.label(hs)
                    drawLine(GridLine, Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                    val lt = measurer.measure(tf.hour(hs), labelStyle)
                    drawText(lt, topLeft = Offset((xm - lt.size.width / 2f).coerceIn(0f, size.width - lt.size.width), 0f))
                }
                // Hourly bars stand on the hour they cover: the values are the sums of the hour before
                // the time stamp, as delivered by the models and stations. 00:00 is the previous
                // day's last hour – no bar (it showed as half a bar at the start).
                val axis = HourAxis(start, end, l, r)
                val barW = axis.barWidth()
                fun barX(t: Long) = axis.barLeft(t)
                clipRect(left = l, right = r) {
                    // Sunshine row right below the plot: minutes per hour, full row height = 60 min
                    if (sunTotalMin != null) {
                        val rowTop = bottom + sunGap.toPx() + 2.dp.toPx()
                        val rowBottom = bottom + sunH.toPx() - 2.dp.toPx()
                        drawRect(SunTrack, Offset(l, rowTop), Size(r - l, rowBottom - rowTop))
                        pts.filter { it.time > start }.forEach { h ->
                            val m = (h.sunshine ?: 0.0).coerceIn(0.0, 60.0)
                            if (m >= 1.0) {
                                val hgt = (m / 60.0 * (rowBottom - rowTop)).toFloat()
                                drawRoundRect(SunFill, Offset(barX(h.time), rowBottom - hgt), Size(barW, hgt), CornerRadius(1.5.dp.toPx()))
                            }
                        }
                    }
                    pts.filter { it.time > start }.forEach { h ->
                        fun bar(v: Double, c: Color) {
                            if (v > 0.0) drawRoundRect(c, Offset(barX(h.time), yP(v)), Size(barW, bottom - yP(v)), CornerRadius(2.dp.toPx()))
                        }
                        val p = Units.precipitationValue(h.precipitation ?: 0.0, s.precipitationUnit)
                        val measuredBar = compare && !h.forecastOnly
                        val fp = h.forecastPrecipitation?.let { Units.precipitationValue(it, s.precipitationUnit) }
                        bar(p, if (measuredBar) PrecipStyle.Measured else PrecipBar)
                        // Look-back: the forecast as a short bar across the column at its height
                        if (fp != null && fp > 0.0) {
                            val th = PrecipStyle.FORECAST_TICK_DP.dp.toPx()
                            drawRoundRect(PrecipStyle.Forecast, Offset(barX(h.time), yP(fp) - th / 2), Size(barW, th), CornerRadius(th / 2))
                        }
                    }
                }
                // Curves: one point per hour, in the column of its bar and cursor; clipped to the
                // plot (the 00:00 point lies half an hour left of it)
                clipRect(left = l, right = r) {
                    fun yC(c: Double) = yT(Units.temperature(c, s.temperatureUnit))
                    // Forecast (comparison mode): dashed
                    if (compare) {
                        val fp = Path()
                        Curve.segments(dashCurve).forEach { seg ->
                            seg.forEachIndexed { k, c -> val o = axis.point(c.time, c.interval); if (k == 0) fp.moveTo(o, yC(c.value)) else fp.lineTo(o, yC(c.value)) }
                        }
                        drawPath(fp, ForecastLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
                    }
                    // Temperature curve in the temperature colours (in comparison mode the measured one);
                    // every point in the middle of the interval it stands for
                    val path = Path()
                    val single = mutableListOf<Offset>()
                    Curve.segments(mainCurve).forEach { seg ->
                        if (seg.size == 1) single += Offset(axis.point(seg[0].time, seg[0].interval), yC(seg[0].value))
                        else seg.forEachIndexed { k, c -> val o = axis.point(c.time, c.interval); if (k == 0) path.moveTo(o, yC(c.value)) else path.lineTo(o, yC(c.value)) }
                    }
                    val brush = Brush.verticalGradient(
                        listOf(Insights.temperatureColor(pts.maxOf { it.temperature }), Insights.temperatureColor(pts.minOf { it.temperature })),
                        startY = top, endY = bottom,
                    )
                    drawPath(path, brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    // A single reading (just after midnight: only 00:00 so far) makes no line – a dot
                    single.forEach { drawCircle(brush, 3.5.dp.toPx(), it) }
                }
                // Sunshine row label: sun glyph on the left (the day's total is in the legend)
                if (sunTotalMin != null) {
                    val cy = bottom + sunGap.toPx() + (sunH - sunGap).toPx() / 2
                    val c = Offset((l - gap.toPx()) / 2f, cy)
                    val rr = 3.dp.toPx()
                    drawCircle(NimbusColors.Secondary, rr, c)
                    for (i in 0 until 8) {
                        val a = i * PI / 4
                        val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        drawLine(NimbusColors.Secondary, c + d * (rr + 1.5.dp.toPx()), c + d * (rr + 3.5.dp.toPx()), 1.2.dp.toPx(), StrokeCap.Round)
                    }
                }
                // Wind arrows every 3 h, pointing where the wind blows to
                val windY = bottom + sunH.toPx() + windH.toPx() / 2
                drawWindGlyph(Offset((l - gap.toPx()) / 2f, windY), NimbusColors.Secondary)
                blocks.forEach { (centre, h) ->
                    val dir = h.windDirection ?: return@forEach
                    val kmh = h.windSpeed ?: 0.0
                    val len = (7 + (kmh / 50.0).coerceAtMost(1.0) * 6).dp.toPx()
                    val c = Offset(x(centre), windY)
                    rotate((dir + 180).toFloat(), c) {
                        val tip = c + Offset(0f, -len)
                        drawLine(windColor(kmh), c + Offset(0f, len), tip, 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(-3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                    }
                }
                // Current time: thin dashed line with a dot on top (the cursor is a solid line)
                if (showNow && now in start..end) {
                    val xn = x(now)
                    drawLine(NowMark, Offset(xn, top), Offset(xn, bottom + below), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    drawCircle(NowMark, 2.5.dp.toPx(), Offset(xn, top))
                }
                // Cursor (long press)
                if (cursorAlpha > 0f) {
                    val i = selected.coerceIn(1, pts.lastIndex)
                    // in the middle of the hour's bar; the dot on the curve there
                    val xs = axis.cursor(pts[i].time)
                    // on the curve at the cursor (the measured one, else the forecast)
                    val tc = pts[i].time - 1_800_000L
                    val yc = (Curve.at(mainCurve, tc) ?: Curve.at(dashCurve, tc))?.let { yT(Units.temperature(it, s.temperatureUnit)) } ?: yT(temps[i])
                    drawLine(Color.White.copy(alpha = 0.85f * cursorAlpha), Offset(xs, top - 2.dp.toPx()), Offset(xs, bottom + below), 1.5.dp.toPx())
                    drawCircle(Color(0xFF1A2A40).copy(alpha = cursorAlpha), 5.5.dp.toPx(), Offset(xs, yc))
                    drawCircle(Color.White.copy(alpha = cursorAlpha), 3.5.dp.toPx(), Offset(xs, yc))
                }
            }
            // Weather symbols every 3 h
            blocks.forEach { (centre, h) ->
                WeatherIcon(h.condition, h.isDay, size = 22.dp, modifier = Modifier.offset(x = xDp(centre) - 11.dp, y = labelsH + 4.dp))
            }
        }
        // The plot reaches into the card's padding (callers use bleed); the text keeps it, so it
        // does not run up to the card's edge
        Column(Modifier.padding(horizontal = CARD_BLEED)) {
            // The temperatures where the cursor meets the curves (the middle of the hour)
            val sel = pts[selected.coerceIn(1, pts.lastIndex)]
            val tc = sel.time - 1_800_000L
            val tm = Curve.at(mainCurve, tc)
            val tfc = Curve.at(dashCurve, tc)
            val shown = sel.copy(
                temperature = tm ?: sel.temperature,
                compare = sel.compare?.let { c -> c.copy(tempM = if (c.tempM != null) tm ?: c.tempM else null, tempF = tfc ?: c.tempF) },
            )
            Readout(shown, highlighted = cursorOn, compare = compare)
            BarLegend(precipTotal, sunTotalMin, measured = compare, forecastTotal = forecastTotal)
            // Always laid out (only faded), so the card does not change height with the cursor
            Text(
                stringResource(R.string.meteogram_hint), fontSize = 11.sp, lineHeight = 15.sp, color = NimbusColors.Tertiary,
                modifier = Modifier.padding(top = 4.dp).alpha(1f - cursorAlpha),
            )
        }
    }
}

/** Small "wind" symbol (three strokes, two with a curl) as the label of the wind row. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWindGlyph(c: Offset, color: Color) {
    val u = 1.dp.toPx()
    val w = 1.2.dp.toPx()
    val x0 = c.x - 6 * u
    val style = Stroke(w, cap = StrokeCap.Round)
    val rr = 1.7f * u
    // upper stroke curling up
    val y1 = c.y - 3 * u
    drawPath(Path().apply {
        moveTo(x0, y1); lineTo(x0 + 8 * u, y1)
        arcTo(androidx.compose.ui.geometry.Rect(Offset(x0 + 8 * u, y1 - rr), rr), 90f, -250f, false)
    }, color, style = style)
    // middle stroke, straight and longest
    drawLine(color, Offset(x0 - 1 * u, c.y), Offset(x0 + 11 * u, c.y), w, StrokeCap.Round)
    // lower stroke curling down
    val y3 = c.y + 3 * u
    drawPath(Path().apply {
        moveTo(x0 + 1 * u, y3); lineTo(x0 + 6 * u, y3)
        arcTo(androidx.compose.ui.geometry.Rect(Offset(x0 + 6 * u, y3 + rr), rr), -90f, 250f, false)
    }, color, style = style)
}

/** A duration in minutes as "9:54 h" / "9:54 Std." */
@Composable
fun hoursMinutes(minutes: Double): String {
    val m = minutes.roundToInt()
    return stringResource(R.string.duration_h_min, m / 60, m % 60)
}

/** A legend entry: a colour swatch (or a line) and its text; the swatch stays at the first line when the text wraps. */
@Composable
fun LegendItem(color: Color, text: String, line: Boolean = false, dashed: Boolean = false) = Row(verticalAlignment = Alignment.Top) {
    val lineH = 15.sp
    Box(Modifier.height(with(LocalDensity.current) { lineH.toDp() }), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (line) 16.dp else 12.dp, 10.dp)) {
            if (line) drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
            ) else drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
    Spacer(Modifier.width(5.dp))
    Text(text, fontSize = 11.sp, lineHeight = lineH, color = NimbusColors.Secondary)
}

/** Legend entries in a flowing row, as below the meteogram. */
@Composable
fun LegendRow(content: @Composable () -> Unit) = androidx.compose.foundation.layout.FlowRow(
    Modifier.padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) { content() }

/**
 * What the bars mean, with the day's totals – readable without the cursor. [measured]: the bars
 * are (partly) measurements – shown as such, with the forecast's total when there is one.
 */
@Composable
private fun BarLegend(precipTotal: Double?, sunMinutes: Double?, measured: Boolean = false, forecastTotal: Double? = null) {
    val s = LocalSettings.current
    val unit = stringResource(Texts.precipUnit(s.precipitationUnit))
    fun amount(v: Double?) = Units.precipitationNumber(v ?: 0.0, s.precipitationUnit) + NBSP + unit
    LegendRow {
        if (measured) {
            LegendItem(PrecipStyle.Measured, stringResource(R.string.legend_precip_measured, amount(precipTotal)))
            if (forecastTotal != null) LegendItem(PrecipStyle.Forecast, stringResource(R.string.legend_precip_forecast, amount(forecastTotal)), line = true)
        } else LegendItem(PrecipBar, stringResource(R.string.legend_precip, amount(precipTotal)))
        if (sunMinutes != null) LegendItem(SunFill, stringResource(R.string.legend_sunshine, hoursMinutes(sunMinutes)))
    }
}

/** The three text lines of the readout for one hour. */
/**
 * Values at the cursor position as a table – every value in its own fixed cell, so nothing
 * jumps while the cursor moves, and the table always has the same rows (the card keeps its
 * height). Look-back: measurement and forecast side by side.
 */
@Composable
private fun Readout(h: MeteoPoint, highlighted: Boolean, compare: Boolean) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val dirs = Texts.compass.map { stringResource(it) }
    val wUnit = stringResource(Texts.windUnit(s.windUnit))
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    fun t(v: Double?) = v?.let { Units.temp(it, s.temperatureUnit) } ?: NO_VALUE
    fun p(v: Double?) = v?.let { Units.precipitationNumber(it, s.precipitationUnit) + NBSP + pUnit } ?: NO_VALUE
    fun w(v: Double?, dir: Double? = null) =
        v?.let { Units.windNumber(it, s.windUnit) + NBSP + wUnit + (dir?.let { d -> NBSP + dirs[Units.compassIndex(d)] } ?: "") } ?: NO_VALUE
    fun sun(v: Double?) = v?.let { "${it.roundToInt()}" + NBSP + "min" } ?: NO_VALUE
    fun pct(v: Double?, amount: Double?) = v?.let { (Insights.chanceText(it, amount) ?: "0") + NBSP + "%" } ?: NO_VALUE
    val condition = stringResource(Texts.condition(h.condition, h.isDay)) + when {
        h.forecastOnly -> " · " + stringResource(R.string.forecast)
        h.measured -> " · " + stringResource(R.string.measured_word)
        else -> ""
    }
    val lTemp = stringResource(R.string.readout_temperature)
    val lPrecip = stringResource(R.string.precipitation)
    val lChance = stringResource(R.string.readout_chance)
    val lWind = stringResource(R.string.wind)
    val lGust = stringResource(R.string.gusts)
    val lSun = stringResource(R.string.sunshine_short)
    // Time, symbol and weather in one line; the table below gets the full width
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the hour the values cover (and the cursor stands on)
            Text(
                tf.time(h.time - 3_600_000L) + "–" + tf.time(h.time), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (highlighted) Color.White else NimbusColors.Secondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(8.dp))
            WeatherIcon(h.condition, h.isDay, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(condition, Modifier.weight(1f), fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            if (compare) {
                // Look-back: an hour still to come has the forecast only
                val c = h.compare ?: HourCompare(
                    null, h.temperature, null, h.precipitation, h.precipitationChance,
                    null, null, h.windSpeed, null, h.windGust, null, h.sunshine,
                )
                ReadoutTable(
                    listOf(stringResource(R.string.history_legend_measured), stringResource(R.string.forecast)),
                    listOf(
                        lTemp to listOf(t(c.tempM), t(c.tempF)),
                        lPrecip to listOf(p(c.precipM), p(c.precipF)),
                        lChance to listOf(NO_VALUE, pct(c.chanceF, c.precipF)),
                        lWind to listOf(w(c.windM, c.windDirM), w(c.windF)),
                        lGust to listOf(w(c.gustM), w(c.gustF)),
                        lSun to listOf(sun(c.sunM), sun(c.sunF)),
                    ),
                )
            } else {
                ReadoutPairs(
                    listOf(
                        lTemp to t(h.temperature),
                        stringResource(R.string.readout_feels) to t(h.apparentTemperature),
                        lPrecip to p(h.precipitation ?: 0.0),
                        lChance to pct(h.precipitationChance, h.precipitation),
                        lWind to w(h.windSpeed, h.windDirection),
                        lGust to w(h.windGust),
                        lSun to sun(h.sunshine?.takeIf { h.isDay || it >= 1.0 } ?: if (h.sunshine != null) 0.0 else null),
                        stringResource(R.string.humidity) to (h.humidity?.let { "${it.roundToInt()}" + NBSP + "%" } ?: NO_VALUE),
                    ),
                    // the widest values to expect, in the units set
                    reserve = listOf(t(-88.0), p(88.8), w(88.0, 292.5), "100" + NBSP + "%", sun(60.0)),
                )
            }
        }
    }
}

/** Inner padding of the glass cards the meteogram may extend into (for a wider plot). */
val CARD_BLEED = 10.dp

/** Extends the element by [amount] on both sides beyond the parent's padding. */
fun Modifier.bleed(amount: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = amount.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    },
)
