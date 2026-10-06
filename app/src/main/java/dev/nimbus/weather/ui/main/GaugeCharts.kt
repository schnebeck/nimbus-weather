/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/GaugeCharts.kt
 * A gauge's water level and tide: the content of its card, and the level chart.
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

import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.LevelSample
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Tides
import dev.nimbus.weather.util.Units
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TideContent(g: GaugeInfo, now: Long) {
    val tf = LocalTimeFormat.current
    val rising = g.prediction.let { p ->
        val a = p.lastOrNull { it.time <= now }
        val b = p.firstOrNull { it.time > now + 20 * 60_000L }
        if (a != null && b != null) b.value > a.value else null
    }
    val next = g.extremes.firstOrNull { it.time > now }
    Text(
        stringResource(if (rising == true) R.string.tide_flood else R.string.tide_ebb),
        fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White,
    )
    next?.let { e ->
        val mins = ((e.time - now) / 60_000L).toInt()
        Text(
            stringResource(
                if (e.high) R.string.tide_next_high else R.string.tide_next_low,
                tf.time(e.time), stringResource(R.string.duration_h_min, mins / 60, mins % 60),
            ),
            fontSize = 14.sp, color = NimbusColors.Secondary,
        )
    }
    Spacer(Modifier.height(8.dp))
    // Next four high and low waters
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        g.extremes.filter { it.time > now }.take(4).forEach { e ->
            val day = if (tf.isSameDay(e.time, now)) "" else tf.weekdayShort(e.time) + NBSP
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (e.high) R.string.tide_hw else R.string.tide_lw), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (e.high) Color.White else NimbusColors.Secondary)
                Text(NBSP + day + tf.time(e.time), fontSize = 14.sp, color = Color.White)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    val start = now - 24 * 3_600_000L
    val end = now + 36 * 3_600_000L
    LevelChart(
        history = g.history.filter { it.time >= start }, prediction = g.prediction.filter { it.time in now - 3_600_000L..end },
        extremes = g.extremes.filter { it.time in start..end }, lines = listOfNotNull(
            g.marks["MThw"]?.let { R.string.mark_mthw to it }, g.marks["MTnw"]?.let { R.string.mark_mtnw to it },
        ), start = start, end = end, now = now,
    )
    Spacer(Modifier.height(4.dp))
    val range = g.extremes.filter { it.high }.map { it.level }.average() - g.extremes.filter { !it.high }.map { it.level }.average()
    val current = g.level?.let { stringResource(R.string.gauge_now, it.roundToInt(), g.levelTime?.let { t -> tf.time(t) } ?: "") }
    Text(
        listOfNotNull(current, if (!range.isNaN()) stringResource(R.string.tide_range, Units.oneDecimal(range / 100.0)) else null).joinToString(" · "),
        fontSize = 12.sp, color = NimbusColors.Secondary,
    )
}

@Composable
internal fun LevelContent(g: GaugeInfo, now: Long) {
    val tf = LocalTimeFormat.current
    val level = g.level
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    if (level != null) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${level.roundToInt()}${NBSP}cm", fontSize = 30.sp, color = Color.White)
            (g.tendency ?: trend(g.history))?.let { t ->
                Text("  " + arrow(t), fontSize = 22.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    } else {
        Text(titleCase(g.water) + " · " + titleCase(g.name), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
    gaugeStatus(g)?.let { (text, color) -> Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color) }
    val mw = g.marks["MW"]
    val details = listOfNotNull(
        if (level != null && mw != null) {
            val d = (level - mw).roundToInt()
            stringResource(if (d >= 0) R.string.gauge_above_mw else R.string.gauge_below_mw, abs(d))
        } else null,
        // Distance to the next flood alert level (state gauges)
        if (level != null) g.alertLevels.entries.sortedBy { it.key }.firstOrNull { it.value > level }?.let { (s, v) ->
            stringResource(R.string.gauge_next_alert, stringResource(alertKindName(g.alertKind)), s, v.roundToInt(), (v - level).roundToInt())
        } else null,
        g.discharge?.let { stringResource(R.string.gauge_discharge, it.roundToInt()) },
        g.levelTime?.takeIf { now - it > 3 * 3_600_000L }?.let { stringResource(R.string.gauge_measured_at, tf.dayMonth(it) + " " + tf.time(it)) },
    )
    if (details.isNotEmpty()) Text(details.joinToString(" · "), fontSize = 13.sp, color = NimbusColors.Secondary)
    if (level == null) Text(stringResource(R.string.gauge_values_at_state), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 17.sp)
    if (g.history.size >= 2) {
        Spacer(Modifier.height(10.dp))
        val start = g.history.first().time
        val lines = listOfNotNull(
            g.marks["MNW"]?.let { R.string.mark_mnw to it },
            g.marks["MW"]?.let { R.string.mark_mw to it },
            g.marks["MHW"]?.let { R.string.mark_mhw to it },
        )
        val short = stringResource(alertKindShort(g.alertKind))
        val alertLines = g.alertLevels.entries.sortedBy { it.key }.map { (s, v) -> "$short$NBSP$s" to v }
        val end = maxOf(now, g.forecast.lastOrNull()?.time ?: 0L, g.history.last().time)
        LevelChart(g.history, g.forecast, emptyList(), lines, start, end, now, days = true, extraLines = alertLines)
        if (g.forecast.isNotEmpty()) Text(stringResource(R.string.gauge_forecast_note), fontSize = 11.sp, color = NimbusColors.Tertiary)
    }
    g.link?.let { url ->
        Text(
            stringResource(R.string.gauge_open_state_page), fontSize = 14.sp, color = Color(0xFF9CC8FF), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { uri.openUri(url) }.padding(vertical = 4.dp),
        )
    }
}

/** Trend over the last two hours in cm/h: sign only (±1), 0 for steady. */
internal fun trend(h: List<LevelSample>): Int? {
    if (h.size < 2) return null
    val last = h.last()
    val before = h.lastOrNull { it.time <= last.time - 2 * 3_600_000L } ?: return null
    val perHour = (last.value - before.value) / ((last.time - before.time) / 3_600_000.0)
    return when { perHour > 1.0 -> 1; perHour < -1.0 -> -1; else -> 0 }
}

/** Highest flood mark (Hochwassermarke I/II/III) the level is above, as "I", "II", "III". */
internal fun highestMark(g: GaugeInfo): String? {
    val level = g.level ?: return null
    return listOf("M_III" to "III", "M_II" to "II", "M_I" to "I").firstOrNull { (k, _) -> g.marks[k]?.let { level >= it } == true }?.second
}

/**
 * Water level over time: measured (solid), predicted (dashed), reference lines, "now" and the
 * predicted high/low waters with their times. Units: cm above gauge zero.
 */
@Composable
private fun LevelChart(
    history: List<LevelSample>, prediction: List<LevelSample>, extremes: List<Tides.Extreme>,
    lines: List<Pair<Int, Double>>, start: Long, end: Long, now: Long, days: Boolean = false,
    extraLines: List<Pair<String, Double>> = emptyList(),
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val lineLabels = lines.map { (res, v) -> stringResource(res) to v } + extraLines
    val values = history.map { it.value } + prediction.map { it.value }
    if (values.isEmpty()) return
    // Reference lines only if they are near the data (a flood mark far above would flatten the curve).
    val dataLo = values.min(); val dataHi = values.max()
    val pad = maxOf(10.0, (dataHi - dataLo) * 0.15)
    val shown = lineLabels.filter { it.second in (dataLo - 3 * pad)..(dataHi + 3 * pad) }
    val lo = minOf(dataLo, shown.minOfOrNull { it.second } ?: dataLo) - pad
    val hi = maxOf(dataHi, shown.maxOfOrNull { it.second } ?: dataHi) + pad
    // Long-press cursor as in the meteogram: measured and predicted points, nearest by time
    val points = remember(history, prediction) {
        (history.map { it to false } + prediction.map { it to true }).filter { it.first.time in start..end }.sortedBy { it.first.time }
    }
    var selected by remember(points) { mutableStateOf(points.indexOfLast { it.first.time <= now }.coerceAtLeast(0)) }
    var cursorOn by remember { mutableStateOf(false) }
    var touched by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(touched, cursorOn) {
        if (cursorOn) { kotlinx.coroutines.delay(10_000L); cursorOn = false }
    }
    val cursorAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (cursorOn) 1f else 0f, androidx.compose.animation.core.tween(if (cursorOn) 150 else 700), label = "cursor",
    )
    var plotL by remember { mutableStateOf(0f) }
    var plotR by remember { mutableStateOf(1f) }
    fun indexAt(xPx: Float): Int {
        val t = start + ((xPx - plotL) / (plotR - plotL)).coerceIn(0f, 1f) * (end - start)
        return points.indices.minByOrNull { kotlin.math.abs(points[it].first.time - t) } ?: 0
    }
    Column {
    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .pointerInput(points) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { cursorOn = true; selected = indexAt(it.x); touched++ },
                ) { change, _ -> change.consume(); selected = indexAt(change.position.x); touched++ }
            }
            .pointerInput(points) { detectTapGestures(onTap = { if (cursorOn) { selected = indexAt(it.x); touched++ } }) },
    ) {
        val labelH = measurer.measure("0", labelStyle).size.height
        val top = labelH + 4.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val axisW = measurer.measure("${hi.roundToInt()}", labelStyle).size.width + 4.dp.toPx()
        val l = axisW; val r = size.width
        plotL = l; plotR = r
        val span = (end - start).toFloat()
        fun x(t: Long) = l + (r - l) * ((t - start) / span)
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        drawText(measurer.measure("cm", labelStyle), topLeft = Offset(0f, 0f))
        listOf(lo + pad, (lo + hi) / 2, hi - pad).forEach { v ->
            val t = measurer.measure("${v.roundToInt()}", labelStyle)
            drawText(t, topLeft = Offset(axisW - t.size.width - 4.dp.toPx(), y(v) - t.size.height / 2f))
        }
        shown.forEach { (name, v) ->
            drawLine(Color(0x40FFFFFF), Offset(l, y(v)), Offset(r, y(v)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            // Label at the left end: the time labels of high and low waters sit at the curve's extremes.
            val t = measurer.measure(name, labelStyle)
            drawText(t, topLeft = Offset(l + 2.dp.toPx(), y(v) - t.size.height - 1.dp.toPx()))
        }
        // Time axis: days (rivers) or every 6 hours (tides)
        val zone = tf.zone
        var mark = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val step = if (days) 24 * 3_600_000L else 6 * 3_600_000L
        while (mark <= end) {
            if (mark >= start) {
                val xm = x(mark)
                drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f)
                val text = if (days) tf.weekdayShort(mark) else tf.hour(mark)
                val t = measurer.measure(text, labelStyle)
                drawText(t, topLeft = Offset((xm - t.size.width / 2f).coerceIn(l, size.width - t.size.width), bottom + 3.dp.toPx()))
            }
            mark += step
        }
        fun path(points: List<LevelSample>) = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.time), y(p.value)) else lineTo(x(p.time), y(p.value)) }
        }
        if (prediction.size >= 2) {
            drawPath(path(prediction), PredictedLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
        }
        if (history.size >= 2) drawPath(path(history), MeasuredLine, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        // now
        val xn = x(now)
        if (xn in l..r) drawLine(Color(0x99FFFFFF), Offset(xn, top), Offset(xn, bottom), 1.dp.toPx())
        // high and low waters
        extremes.filter { it.time in start..end }.forEach { e ->
            val c = Offset(x(e.time), y(e.level))
            drawCircle(if (e.high) PredictedLine else NimbusColors.Secondary, 3.dp.toPx(), c)
            val t = measurer.measure(tf.time(e.time), labelStyle)
            val ty = if (e.high) c.y - t.size.height - 3.dp.toPx() else c.y + 3.dp.toPx()
            drawText(t, topLeft = Offset((c.x - t.size.width / 2f).coerceIn(l, size.width - t.size.width), ty.coerceIn(0f, bottom - t.size.height)))
        }
        // Cursor (long press)
        if (cursorAlpha > 0f && points.isNotEmpty()) {
            val (p, _) = points[selected.coerceIn(0, points.lastIndex)]
            val xc = x(p.time)
            drawLine(Color.White.copy(alpha = 0.85f * cursorAlpha), Offset(xc, top), Offset(xc, bottom), 1.5.dp.toPx())
            drawCircle(Color(0xFF1A2A40).copy(alpha = cursorAlpha), 5.dp.toPx(), Offset(xc, y(p.value)))
            drawCircle(Color.White.copy(alpha = cursorAlpha), 3.dp.toPx(), Offset(xc, y(p.value)))
        }
    }
    // Hint, or the value under the cursor – same place, so the card keeps its height
    Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(stringResource(R.string.gauge_chart_hint), fontSize = 11.sp, color = NimbusColors.Tertiary, modifier = Modifier.alpha(1f - cursorAlpha))
        if (points.isNotEmpty()) {
            val (p, predicted) = points[selected.coerceIn(0, points.lastIndex)]
            Text(
                (if (tf.isSameDay(p.time, now)) tf.time(p.time) else tf.weekdayShort(p.time) + NBSP + tf.time(p.time)) +
                    " · " + p.value.roundToInt() + NBSP + "cm" + (if (predicted) " · " + stringResource(R.string.gauge_chart_predicted) else ""),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.alpha(cursorAlpha),
            )
        }
    }
    }
}
