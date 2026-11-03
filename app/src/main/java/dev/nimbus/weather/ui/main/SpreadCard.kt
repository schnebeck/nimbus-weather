/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/SpreadCard.kt
 * The spread of the forecast: the runs of an ensemble over the next ten days.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StackedLineChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Spread
import dev.nimbus.weather.data.model.SpreadDay
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val AllRuns = Color(0x26FFFFFF)
private val InnerRuns = Color(0x4DFFFFFF)
private val DayMark = Color(0x40FFFFFF)
private val Grid = Color(0x1FFFFFFF)

/** The days of [spread] still ahead at [now] – today from now on. */
internal fun Spread.daysAhead(now: Long): List<SpreadDay> = days.filter { it.start + 24 * Spread.HOUR_MS > now && it.highs.isNotEmpty() }

/** The temperature axis over all runs from hour [from] on (display unit); null: no values. */
internal fun Spread.tempAxis(from: Int, unit: dev.nimbus.weather.data.model.TemperatureUnit): ChartAxis? {
    val all = (from until times.size).flatMap { listOfNotNull(lowest[it], highest[it]) }.map { Units.temperature(it, unit) }
    return if (all.isEmpty()) null else TempAxis.of(all.min(), all.max(), unit)
}

/** [day]'s high as the inner runs have it: from 10 % to 90 % of them. */
internal fun SpreadDay.innerHighs(): Pair<Double, Double>? {
    val lo = Spread.quantile(highs, (1 - Spread.INNER) / 2) ?: return null
    val hi = Spread.quantile(highs, 1 - (1 - Spread.INNER) / 2) ?: return null
    return lo to hi
}

/**
 * The spread of the forecast: the ensemble's hourly temperature as a fan – the middle run, the
 * inner runs (80 %), all of them – and each day's precipitation, the middle run's sum with the
 * inner runs' range, above it the share of the runs with a wet day. Folded: how far apart
 * tomorrow's high and that of the last day lie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpreadCard(spread: Spread?, tried: Boolean, now: Long, onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }
    val tf = LocalTimeFormat.current
    val s = LocalSettings.current
    val measurer = rememberTextMeasurer()
    val days = remember(spread, now / Spread.HOUR_MS) { spread?.daysAhead(now).orEmpty() }
    val tomorrow = days.firstOrNull { it.start > now }?.innerHighs()
    val last = days.lastOrNull()?.takeIf { it.start > now }
    val lastHighs = last?.innerHighs()
    val summary = if (tomorrow != null && last != null && lastHighs != null && last != days.firstOrNull { it.start > now }) stringResource(
        R.string.spread_summary,
        Units.temp(tomorrow.first, s.temperatureUnit), Units.temp(tomorrow.second, s.temperatureUnit),
        tf.weekdayShort(last.start + 12 * Spread.HOUR_MS),
        Units.temp(lastHighs.first, s.temperatureUnit), Units.temp(lastHighs.second, s.temperatureUnit),
    ) else stringResource(R.string.spread_card)
    GlassCard(title = stringResource(R.string.spread_card), icon = Icons.Outlined.StackedLineChart, info = Term.SPREAD, summary = summary) {
        if (spread == null) {
            if (!tried) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            } else Text(stringResource(R.string.spread_unavailable), fontSize = 14.sp, color = Color.White)
            return@GlassCard
        }
        Text(stringResource(R.string.spread_hint), fontSize = 13.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.height(10.dp))
        val from = spread.from(now)
        if (spread.times.size - from < 2) return@GlassCard
        val wet = millimetres(Spread.WET_MM, s.precipitationUnit) + NBSP + stringResource(Texts.precipUnit(s.precipitationUnit))
        val precipUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
        Canvas(Modifier.fillMaxWidth().height(286.dp).testTag("spread-chart")) {
            drawSpread(spread, from, days, tf, s.temperatureUnit, s.precipitationUnit, precipUnit, measurer)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Key(Modifier.width(14.dp).height(2.dp).background(Color.White), stringResource(R.string.spread_median))
            // drawn over the band of all runs, as in the chart
            Key(Modifier.size(14.dp, 10.dp).background(AllRuns).background(InnerRuns), stringResource(R.string.spread_inner))
            Key(Modifier.size(14.dp, 10.dp).background(AllRuns), stringResource(R.string.spread_all))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.spread_rain, wet), fontSize = 12.sp, color = NimbusColors.Tertiary)
    }
}

/** Whole millimetres as such ("5", not "5.0"); inches as always. */
private fun millimetres(mm: Double, unit: dev.nimbus.weather.data.model.PrecipitationUnit): String =
    if (unit == dev.nimbus.weather.data.model.PrecipitationUnit.MM && mm == floor(mm)) mm.roundToInt().toString() else Units.precipitationNumber(mm, unit)

@Composable
private fun Key(swatch: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(swatch.clip(RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = NimbusColors.Secondary)
    }
}

/**
 * The fan above, the precipitation of the days below, on one time axis – the hours from [from]
 * on; the days split by dashed lines at midnight, each named below its middle.
 */
private fun DrawScope.drawSpread(
    spread: Spread, from: Int, days: List<SpreadDay>, tf: TimeFormat,
    tempUnit: dev.nimbus.weather.data.model.TemperatureUnit, precipUnit: dev.nimbus.weather.data.model.PrecipitationUnit, precipLabel: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val axis = TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary)
    val gutter = 30.dp.toPx()
    val w = size.width - gutter
    val tempH = 160.dp.toPx()
    val chanceTop = tempH + 12.dp.toPx()
    val barTop = chanceTop + 16.dp.toPx()
    val barBottom = size.height - 20.dp.toPx()
    val t0 = spread.times[from]
    val t1 = spread.times.last()
    fun x(t: Long) = gutter + (t - t0).toFloat() / (t1 - t0) * w

    // temperature: the scale over all runs, the marks counted from the freezing point (it stronger, in the blue of ice)
    val shown = from until spread.times.size
    val tAxis = spread.tempAxis(from, tempUnit) ?: return
    val lo = tAxis.lo
    val hi = tAxis.hi
    fun yd(v: Double) = (tempH * (1 - (v - lo) / (hi - lo))).toFloat()
    fun y(c: Double) = yd(Units.temperature(c, tempUnit))
    tAxis.ticks.forEach { v ->
        val frost = tAxis.isAnchor(v)
        if (frost) drawFrostLine(Offset(gutter, yd(v)), Offset(size.width, yd(v)))
        else drawLine(Grid, Offset(gutter, yd(v)), Offset(size.width, yd(v)), 1f)
        val l = measurer.measure("${v.roundToInt()}°", if (frost) axis.copy(color = TempAxis.FrostLabel) else axis)
        drawText(l, topLeft = Offset(0f, (yd(v) - l.size.height / 2f).coerceIn(0f, tempH - l.size.height)))
    }
    fun band(lower: List<Double?>, upper: List<Double?>, color: Color) {
        val idx = shown.filter { lower[it] != null && upper[it] != null }
        if (idx.size < 2) return
        val p = Path()
        idx.forEachIndexed { k, i -> val o = Offset(x(spread.times[i]), y(upper[i]!!)); if (k == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
        idx.asReversed().forEach { i -> p.lineTo(x(spread.times[i]), y(lower[i]!!)) }
        p.close()
        drawPath(p, color)
    }
    band(spread.lowest, spread.highest, AllRuns)
    band(spread.low, spread.high, InnerRuns)
    val median = Path()
    var started = false
    shown.forEach { i ->
        val v = spread.median[i] ?: run { started = false; return@forEach }
        val o = Offset(x(spread.times[i]), y(v))
        if (!started) { median.moveTo(o.x, o.y); started = true } else median.lineTo(o.x, o.y)
    }
    drawPath(median, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

    // precipitation: the scale up to the wettest inner run, at least a few millimetres
    val inner = days.mapNotNull { d -> Spread.quantile(d.sums, 1 - (1 - Spread.INNER) / 2) }
    val top = maxOf(5.0, inner.maxOrNull() ?: 0.0).let { ceil(it / 5) * 5 }
    fun yp(mm: Double) = (barBottom - (mm.coerceIn(0.0, top) / top) * (barBottom - barTop)).toFloat()
    drawLine(Grid, Offset(gutter, barBottom), Offset(size.width, barBottom), 1f)
    drawLine(Grid, Offset(gutter, barTop), Offset(size.width, barTop), 1f)
    measurer.measure(millimetres(top, precipUnit), axis).let { drawText(it, topLeft = Offset(0f, barTop - it.size.height / 2f)) }
    measurer.measure(precipLabel, axis).let { drawText(it, topLeft = Offset(0f, barTop + it.size.height / 2f)) }

    val chanceStyle = TextStyle(fontSize = 10.sp)
    days.forEach { d ->
        val a = maxOf(d.start, t0)
        val b = minOf(d.start + 24 * Spread.HOUR_MS, t1)
        if (d.start > t0) drawLine(DayMark, Offset(x(d.start), 0f), Offset(x(d.start), barBottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        // a few hours left of a day: no room for its name and its bar
        if (b - a < 4 * Spread.HOUR_MS) return@forEach
        val cx = (x(a) + x(b)) / 2
        val name = measurer.measure(tf.weekdayShort(d.start + 12 * Spread.HOUR_MS), axis)
        drawText(name, topLeft = Offset(axisLabelLeft(cx - gutter, name.size.width, w) + gutter, barBottom + 4.dp.toPx()))
        val mid = Spread.quantile(d.sums, 0.5) ?: return@forEach
        val barW = minOf((x(b) - x(a)) * 0.45f, 14.dp.toPx())
        if (mid > 0) drawRect(PrecipBlue.copy(alpha = 0.75f), Offset(cx - barW / 2, yp(mid)), Size(barW, barBottom - yp(mid)))
        val low = Spread.quantile(d.sums, (1 - Spread.INNER) / 2) ?: return@forEach
        val high = Spread.quantile(d.sums, 1 - (1 - Spread.INNER) / 2) ?: return@forEach
        if (high > low) {
            val cap = barW / 3
            val stroke = 1.5.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.8f), Offset(cx, yp(low)), Offset(cx, yp(high)), stroke)
            drawLine(Color.White.copy(alpha = 0.8f), Offset(cx - cap, yp(high)), Offset(cx + cap, yp(high)), stroke)
        }
        d.wetShare()?.let { share ->
            // a dry day for most runs: the share faint, a wet one in the colour of rain
            val l = measurer.measure("${(share * 100).roundToInt()}$NBSP%", chanceStyle.copy(color = if (share >= 0.3) PrecipBlue else Color(0x99FFFFFF)))
            drawText(l, topLeft = Offset(axisLabelLeft(cx - gutter, l.size.width, w) + gutter, chanceTop))
        }
    }
}
