/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PressureCard.kt
 * Air pressure of the day, 00:00 to 24:00, as a curve with a long-press cursor.
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val FastFall = Color(0xFFFF9F43)
private const val NORMAL_HPA = 1013.25

/** A fall of at least this much within 3 hours hints at a storm (shown in orange). */
const val FAST_FALL_HPA_3H = 3.0

/** Hours [i, i + 1] that lie in a fall of [FAST_FALL_HPA_3H] or more within the next 3 hours. */
fun fastFallHours(p: List<Double?>): Set<Int> = buildSet {
    for (i in p.indices) {
        val a = p[i] ?: continue
        val b = p.getOrNull(i + 3) ?: continue
        if (a - b >= FAST_FALL_HPA_3H) { add(i); add(i + 1); add(i + 2) }
    }
}

/**
 * Today's air pressure from 00:00 to 24:00, like the day meteogram: the hours so far as a solid
 * line, the rest of the day (forecast) dashed, a dashed mark at the current time, normal pressure
 * (1013 hPa) as a reference and fast falls in orange. A long press shows the value of the hour.
 */
@Composable
fun PressureCard(data: WeatherData, now: Long, measured: TodayMeasured? = null) {
    val tf = LocalTimeFormat.current
    val start = remember(now / 3_600_000L) { tf.zoned(now).toLocalDate().atStartOfDay(tf.zone).toInstant().toEpochMilli() }
    val end = start + 24 * 3_600_000L
    // Hours already over show the station's reading instead of the forecast (the comparison of the
    // two is in the look-back)
    val readings = measured?.pressure.orEmpty()
    val points = remember(data, start, readings, now / 3_600_000L) {
        data.hourly.filter { it.time in start..end && it.pressure != null }
            .map { p -> readings[p.time]?.takeIf { p.time <= now }?.let { p.copy(pressure = it) } ?: p }
    }
    if (points.size < 2) return
    val current = data.current.pressure ?: points.minBy { abs(it.time - now) }.pressure!!
    val in3h = points.firstOrNull { it.time >= now + 3 * 3_600_000L }?.pressure
        ?: data.hourly.firstOrNull { it.time >= now + 3 * 3_600_000L }?.pressure
    val delta = in3h?.let { it - current }

    GlassCard(title = stringResource(R.string.pressure_chart_title), icon = Icons.Outlined.Timeline, info = Term.PRESSURE) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(current.roundToInt().toString(), fontSize = 30.sp, color = Color.White)
            Text(NBSP + "hPa", fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.width(12.dp))
            delta?.let { d ->
                Text(
                    stringResource(
                        when {
                            d > 1.0 -> R.string.pressure_chart_rising
                            d < -1.0 -> R.string.pressure_chart_falling
                            else -> R.string.pressure_chart_steady
                        },
                        (if (d >= 0) "+" else "−") + Units.oneDecimal(abs(d)),
                    ),
                    fontSize = 14.sp, color = if (d <= -FAST_FALL_HPA_3H) FastFall else NimbusColors.Secondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PressureChart(points, start, end, now, remember(start, data.place) { nights(start, end, data.place.latitude, data.place.longitude) }, readings.filterKeys { it in start..now })
    }
}

@Composable
private fun PressureChart(
    points: List<HourlyPoint>, start: Long, end: Long, now: Long, nights: List<LongRange>,
    /** Station readings (hPa) of the hours so far – already in [points]; marks which values are measured. */
    measured: Map<Long, Double>,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val unitStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary, fontWeight = FontWeight.SemiBold)
    val values = points.map { it.pressure!! }
    val hasMeasured = measured.isNotEmpty()
    val allValues = values
    // At least 6 hPa span around the data; the axis in whole hPa
    val mid = (allValues.min() + allValues.max()) / 2
    val half = maxOf(3.0, (allValues.max() - allValues.min()) / 2 + 1.0)
    val lo = floor(mid - half)
    val hi = ceil(mid + half)
    val fast = remember(points) { fastFallHours(values) }
    val span = (end - start).toFloat()

    var selected by remember(points) { mutableStateOf(points.indexOfLast { it.time <= now }.coerceAtLeast(0)) }
    var cursorOn by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(touched, cursorOn) { if (cursorOn) { delay(10_000L); cursorOn = false } }
    val cursorAlpha by animateFloatAsState(if (cursorOn) 1f else 0f, tween(if (cursorOn) 150 else 700), label = "cursor")
    var plotL by remember { mutableStateOf(0f) }
    var plotR by remember { mutableStateOf(1f) }
    fun indexAt(xPx: Float): Int {
        val t = start + ((xPx - plotL) / (plotR - plotL)).coerceIn(0f, 1f) * span
        return points.indices.minBy { abs(points[it].time - t) }
    }

    Column {
        Canvas(
            Modifier.fillMaxWidth().height(130.dp)
                .pointerInput(points) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { cursorOn = true; selected = indexAt(it.x); touched++ },
                    ) { change, _ -> change.consume(); selected = indexAt(change.position.x); touched++ }
                }
                .pointerInput(points) { detectTapGestures(onTap = { if (cursorOn) { selected = indexAt(it.x); touched++ } }) },
        ) {
            val gap = 3.dp.toPx()
            val axisL = listOf(lo, hi).maxOf { measurer.measure(it.roundToInt().toString(), labelStyle).size.width } + gap
            val unit = measurer.measure("hPa", unitStyle)
            val top = unit.size.height + measurer.measure("0", labelStyle).size.height / 2f + 4.dp.toPx()
            val labelH = measurer.measure("00", labelStyle).size.height
            val bottom = size.height - labelH - 4.dp.toPx()
            val l = axisL
            val r = size.width - measurer.measure(tf.hourEnd(end), labelStyle).size.width / 2f
            plotL = l; plotR = r
            fun x(t: Long) = l + (r - l) * ((t - start) / span)
            fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
            drawRect(DayTint, Offset(l, top), Size(r - l, bottom - top))
            nights.forEach { n ->
                val a = maxOf(n.first, start); val b = minOf(n.last + 1, end)
                if (b > a) drawRect(NightShade, Offset(x(a), top), Size(x(b) - x(a), bottom - top))
            }
            drawText(unit, topLeft = Offset(0f, 0f))
            for (k in 0..2) {
                val v = lo + (hi - lo) * k / 2
                drawLine(Color(0x1FFFFFFF), Offset(l, y(v)), Offset(r, y(v)), 1f)
                val t = measurer.measure(v.roundToInt().toString(), labelStyle)
                drawText(t, topLeft = Offset(l - t.size.width - gap, y(v) - t.size.height / 2f))
            }
            // Normal pressure as a reference, when it is in range
            if (NORMAL_HPA in lo..hi) {
                drawLine(Color(0x55FFFFFF), Offset(l, y(NORMAL_HPA)), Offset(r, y(NORMAL_HPA)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val t = measurer.measure("1013", labelStyle)
                drawText(t, topLeft = Offset(r - t.size.width - 2.dp.toPx(), y(NORMAL_HPA) - t.size.height - 1.dp.toPx()))
            }
            // Time axis every 3 hours, "24" at the end
            var mark = start
            while (mark <= end) {
                val xm = x(mark)
                drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                val t = measurer.measure(if (mark == end) tf.hourEnd(mark) else tf.hour(mark), labelStyle)
                drawText(t, topLeft = Offset((xm - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), bottom + 4.dp.toPx()))
                mark += 3 * 3_600_000L
            }
            // The day so far solid (measured where there are readings), the forecast dashed; fast falls in orange
            val past = Path(); val future = Path()
            var pastStarted = false; var futureStarted = false
            points.forEachIndexed { i, p ->
                val o = Offset(x(p.time), y(p.pressure!!))
                if (p.time <= now) { if (!pastStarted) { past.moveTo(o.x, o.y); pastStarted = true } else past.lineTo(o.x, o.y) }
                if (p.time >= now || points.getOrNull(i + 1)?.let { it.time > now } == true) {
                    if (!futureStarted) { future.moveTo(o.x, o.y); futureStarted = true } else future.lineTo(o.x, o.y)
                }
            }
            drawPath(past, Color.White, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(future, Color.White.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            points.indices.filter { it in fast && it + 1 in fast && it + 1 <= points.lastIndex }.forEach { i ->
                drawLine(FastFall, Offset(x(points[i].time), y(points[i].pressure!!)), Offset(x(points[i + 1].time), y(points[i + 1].pressure!!)), 3.dp.toPx(), StrokeCap.Round)
            }
            // Current time
            if (now in start..end) {
                val xn = x(now)
                drawLine(Color(0xB3FFFFFF), Offset(xn, top), Offset(xn, bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            }
            // Cursor (long press)
            if (cursorAlpha > 0f) {
                val p = points[selected.coerceIn(0, points.lastIndex)]
                val xc = x(p.time)
                drawLine(Color.White.copy(alpha = 0.85f * cursorAlpha), Offset(xc, top - 2.dp.toPx()), Offset(xc, bottom), 1.5.dp.toPx())
                val v = p.pressure!!
                drawCircle(Color(0xFF1A2A40).copy(alpha = cursorAlpha), 5.dp.toPx(), Offset(xc, y(v)))
                drawCircle(Color.White.copy(alpha = cursorAlpha), 3.dp.toPx(), Offset(xc, y(v)))
            }
        }
        // Hint, or the value under the cursor – same place, so the card keeps its height
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val hint = when {
                fast.isNotEmpty() -> stringResource(R.string.pressure_chart_hint_fall)
                hasMeasured -> stringResource(R.string.pressure_chart_hint_measured)
                else -> stringResource(R.string.pressure_chart_hint)
            }
            androidx.compose.foundation.text.BasicText(
                hint, Modifier.fillMaxWidth().alpha(1f - cursorAlpha), style = TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary), maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            )
            val p = points[selected.coerceIn(0, points.lastIndex)]
            val m = measured[p.time]?.takeIf { p.time <= now }
            // Fixed cells: sliding the cursor changes the values, nothing moves
            ReadoutCells(
                listOf(
                    stringResource(R.string.readout_time) to tf.time(p.time),
                    stringResource(if (m != null) R.string.history_legend_measured else R.string.forecast) to "${(m ?: p.pressure!!).roundToInt()}" + NBSP + "hPa",
                ),
                Modifier.alpha(cursorAlpha),
            )
        }
    }
}
