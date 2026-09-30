/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/GaugeCard.kt
 * Water level card: tides at the coast and on tidal rivers, otherwise the nearest river gauge.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Waves
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
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Tides
import dev.nimbus.weather.util.Units
import kotlin.math.abs
import kotlin.math.roundToInt

private val MeasuredLine = Color.White
private val PredictedLine = Color(0xFF9CC8FF)
private val LowColor = Color(0xFFFFC56B)
private val HighColor = Color(0xFFFF7A5C)

@Composable
fun GaugeCard(g: GaugeInfo, now: Long) {
    GlassCard(
        title = stringResource(if (g.tidal) R.string.gauge_title_tides else R.string.gauge_title_level),
        icon = Icons.Outlined.Waves,
        info = if (g.tidal) Term.TIDES else Term.GAUGE,
    ) {
        if (g.tidal && g.extremes.isNotEmpty()) TideContent(g, now) else LevelContent(g, now)
        Spacer(Modifier.height(8.dp))
        val place = stringResource(R.string.gauge_station, titleCase(g.name), titleCase(g.water), Units.oneDecimal(g.distanceKm))
        val zero = g.gaugeZero?.let { " · " + stringResource(R.string.gauge_zero, String.format(java.util.Locale.getDefault(), "%.2f", it)) } ?: ""
        Text(place + zero, fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp)
        if (g.tidal) Text(stringResource(R.string.gauge_tide_note), fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TideContent(g: GaugeInfo, now: Long) {
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
private fun LevelContent(g: GaugeInfo, now: Long) {
    val tf = LocalTimeFormat.current
    val level = g.level
    Row(verticalAlignment = Alignment.Bottom) {
        Text(level?.let { "${it.roundToInt()}${NBSP}cm" } ?: "–", fontSize = 30.sp, color = Color.White)
        trend(g.history)?.let { t ->
            Text("  " + when { t > 0 -> "↑"; t < 0 -> "↓"; else -> "→" }, fontSize = 22.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
    val mark = highestMark(g)
    val stateText = when {
        mark != null -> stringResource(R.string.gauge_mark_exceeded, mark)
        g.state == "high" -> stringResource(R.string.gauge_state_high)
        g.state == "low" -> stringResource(R.string.gauge_state_low)
        g.state == "normal" -> stringResource(R.string.gauge_state_normal)
        else -> null
    }
    stateText?.let {
        Text(it, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = when { mark != null || g.state == "high" -> HighColor; g.state == "low" -> LowColor; else -> Color.White })
    }
    val mw = g.marks["MW"]
    val details = listOfNotNull(
        if (level != null && mw != null) {
            val d = (level - mw).roundToInt()
            stringResource(if (d >= 0) R.string.gauge_above_mw else R.string.gauge_below_mw, abs(d))
        } else null,
        g.discharge?.let { stringResource(R.string.gauge_discharge, it.roundToInt()) },
        g.levelTime?.takeIf { now - it > 3 * 3_600_000L }?.let { stringResource(R.string.gauge_measured_at, tf.dayMonth(it) + " " + tf.time(it)) },
    )
    if (details.isNotEmpty()) Text(details.joinToString(" · "), fontSize = 13.sp, color = NimbusColors.Secondary)
    if (g.history.size >= 2) {
        Spacer(Modifier.height(10.dp))
        val start = g.history.first().time
        val lines = listOfNotNull(
            g.marks["MNW"]?.let { R.string.mark_mnw to it },
            g.marks["MW"]?.let { R.string.mark_mw to it },
            g.marks["MHW"]?.let { R.string.mark_mhw to it },
        )
        LevelChart(g.history, emptyList(), emptyList(), lines, start, maxOf(now, g.history.last().time), now, days = true)
    }
}

/** Trend over the last two hours in cm/h: sign only (±1), 0 for steady. */
private fun trend(h: List<LevelSample>): Int? {
    if (h.size < 2) return null
    val last = h.last()
    val before = h.lastOrNull { it.time <= last.time - 2 * 3_600_000L } ?: return null
    val perHour = (last.value - before.value) / ((last.time - before.time) / 3_600_000.0)
    return when { perHour > 1.0 -> 1; perHour < -1.0 -> -1; else -> 0 }
}

/** Highest flood mark (Hochwassermarke I/II/III) the level is above, as "I", "II", "III". */
private fun highestMark(g: GaugeInfo): String? {
    val level = g.level ?: return null
    return listOf("M_III" to "III", "M_II" to "II", "M_I" to "I").firstOrNull { (k, _) -> g.marks[k]?.let { level >= it } == true }?.second
}

/** PEGELONLINE names are upper case: "CUXHAVEN STEUBENHÖFT" -> "Cuxhaven Steubenhöft". */
fun titleCase(s: String): String = Regex("[\\p{L}]+").replace(s.lowercase()) { m -> m.value.replaceFirstChar { it.uppercase() } }

/**
 * Water level over time: measured (solid), predicted (dashed), reference lines, "now" and the
 * predicted high/low waters with their times. Units: cm above gauge zero.
 */
@Composable
private fun LevelChart(
    history: List<LevelSample>, prediction: List<LevelSample>, extremes: List<Tides.Extreme>,
    lines: List<Pair<Int, Double>>, start: Long, end: Long, now: Long, days: Boolean = false,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val lineLabels = lines.map { (res, v) -> stringResource(res) to v }
    val values = history.map { it.value } + prediction.map { it.value }
    if (values.isEmpty()) return
    // Reference lines only if they are near the data (a flood mark far above would flatten the curve).
    val dataLo = values.min(); val dataHi = values.max()
    val pad = maxOf(10.0, (dataHi - dataLo) * 0.15)
    val shown = lineLabels.filter { it.second in (dataLo - 3 * pad)..(dataHi + 3 * pad) }
    val lo = minOf(dataLo, shown.minOfOrNull { it.second } ?: dataLo) - pad
    val hi = maxOf(dataHi, shown.maxOfOrNull { it.second } ?: dataHi) + pad
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val labelH = measurer.measure("0", labelStyle).size.height
        val top = labelH + 4.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val axisW = measurer.measure("${hi.roundToInt()}", labelStyle).size.width + 4.dp.toPx()
        val l = axisW; val r = size.width
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
    }
}
