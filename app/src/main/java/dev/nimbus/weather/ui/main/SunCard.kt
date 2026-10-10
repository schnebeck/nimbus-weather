/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/SunCard.kt
 * The course of the sun today: rise and set, day length, light phases, the arc.
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

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.foundation.layout.size
import dev.nimbus.weather.util.Moon
import dev.nimbus.weather.util.SunPhases
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.LocalExplain
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import kotlin.math.roundToInt

/**
 * Course of the sun today (00–24 h, like the meteogram). The altitude scale is fixed per place
 * (± the midsummer noon altitude), so the size of the arc shows the season. The background is
 * tinted by light phase: day, golden hour, blue hour, night ([SunPhases]).
 */
@Composable
fun SunCard(data: WeatherData, now: Long) {
    val tf = LocalTimeFormat.current
    val explain = LocalExplain.current
    val lat = data.place.latitude
    val lon = data.place.longitude
    val date = tf.zoned(now).toLocalDate()
    val dayStart = date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(tf.zone).toInstant().toEpochMilli()
    fun sample(from: Long, to: Long) =
        generateSequence(from) { it + 5 * 60_000L }.takeWhile { it <= to }.map { it to Moon.sunAltitude(it, lat, lon) }.toList()
    val curve = remember(lat, lon, dayStart) { sample(dayStart, dayEnd) }
    val spans = remember(curve) { SunPhases.spans(curve) }
    val today = data.daily.lastOrNull { it.date <= now }?.takeIf { tf.isSameDay(it.date, now) }
    val tomorrow = data.daily.firstOrNull { it.date >= dayEnd }
    val own = remember(lat, lon, dayStart) { Moon.sunTimes(dayStart, lat, lon) }
    val rise = today?.sunrise ?: own.first
    val set = today?.sunset ?: own.second
    val deltaMin = remember(lat, lon, dayStart) {
        val y = Moon.sunTimes(date.minusDays(1).atStartOfDay(tf.zone).toInstant().toEpochMilli(), lat, lon)
        if (own.first != null && own.second != null && y.first != null && y.second != null)
            (((own.second!! - own.first!!) - (y.second!! - y.first!!)) / 60_000.0).roundToInt() else null
    }
    val noon = curve.maxBy { it.second }
    val altNow = Moon.sunAltitude(now, lat, lon)

    // Twilight times worth mentioning: this morning, this evening or tomorrow morning.
    val twilight = remember(spans, now / 60_000) {
        val morning = spans.filter { it.morning && it.phase in TWILIGHT }
        val evening = spans.filter { !it.morning && it.phase in TWILIGHT }
        when {
            morning.isNotEmpty() && now < morning.last().end -> R.string.sun_phases_morning to morning
            evening.isNotEmpty() && now < evening.last().end -> R.string.sun_phases_evening to evening
            else -> {
                val next = SunPhases.spans(sample(dayEnd, dayEnd + 24 * 3_600_000L)).filter { it.morning && it.phase in TWILIGHT }
                if (next.isEmpty()) null else R.string.sun_phases_tomorrow to next
            }
        }
    }

    GlassCard(title = stringResource(R.string.sun), icon = Icons.Outlined.WbTwilight, info = Term.SUN, onClick = { explain(Term.SUN) }) {
        Row(Modifier.fillMaxWidth()) {
            SunFact(stringResource(R.string.sunrise), rise?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f), stringResource(R.string.sunrise_short))
            SunFact(stringResource(R.string.sunset), set?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f), stringResource(R.string.sunset_short))
            val len = if (rise != null && set != null) (set - rise) / 60_000L else null
            SunFact(
                stringResource(R.string.day_length),
                len?.let { stringResource(R.string.duration_h_min, (it / 60).toInt(), (it % 60).toInt()) }
                    ?: stringResource(if (altNow > 0) R.string.polar_day else R.string.polar_night),
                deltaMin?.let { d ->
                    val sign = when { d > 0 -> "+"; d < 0 -> "\u2212"; else -> "\u00B1" }
                    stringResource(R.string.day_length_delta, "$sign${kotlin.math.abs(d)}${NBSP}min")
                },
                Modifier.weight(1.1f),
                stringResource(R.string.day_length_short),
            )
        }
        Spacer(Modifier.height(12.dp))
        SunChart(
            curve, spans, SunPhases.maxAltitude(lat), dayStart, dayEnd, rise, set, noon, now, altNow,
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
        )
        Spacer(Modifier.height(8.dp))
        SunLegend()
        Spacer(Modifier.height(10.dp))
        val deg = { v: Double -> "${kotlin.math.abs(v).roundToInt()}°" }
        Caption(
            buildString {
                append(stringResource(if (altNow >= 0) R.string.sun_now_up else R.string.sun_now_down, deg(altNow)))
                append(" · ")
                if (set != null && now > set && tomorrow?.sunrise != null) append(stringResource(R.string.sunrise_tomorrow, tf.time(tomorrow.sunrise)))
                else append(stringResource(R.string.sun_highest, deg(noon.second), tf.time(noon.first)))
            },
        )
        twilight?.let { (label, list) ->
            val names = list.map { sp ->
                stringResource(
                    when {
                        sp.phase == SunPhases.Phase.BLUE -> R.string.phase_blue
                        sp.morning -> R.string.phase_golden_morning
                        else -> R.string.phase_golden_evening
                    },
                ) + " " + tf.time(sp.start) + "\u2013" + tf.time(sp.end)
            }
            Spacer(Modifier.height(2.dp))
            Text(stringResource(label, names.joinToString(" · ")), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 17.sp)
        }
    }
}

private val TWILIGHT = setOf(SunPhases.Phase.GOLDEN, SunPhases.Phase.BLUE)

@Composable
private fun SunFact(label: String, value: String, note: String?, modifier: Modifier, short: String = label) {
    Column(modifier) {
        // "Sonnenuntergang" in a third of a narrow card: "Untergang" rather than "Sonnenuntergan"
        dev.nimbus.weather.ui.components.FitText(label, short, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = NimbusColors.Secondary))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        if (note != null) Text(note, fontSize = 11.sp, color = NimbusColors.Tertiary, maxLines = 1)
    }
}

private val SunYellow = Color(0xFFFFD66B)

/** Background tint of each light phase (over the dark card). */
private fun phaseTint(p: SunPhases.Phase): Color = when (p) {
    SunPhases.Phase.DAY -> Color(0x33FFE08A)
    SunPhases.Phase.GOLDEN -> Color(0x5CFF9448)
    SunPhases.Phase.BLUE -> Color(0x7A3F6FD8)
    SunPhases.Phase.NIGHT -> NightShade
}

@Composable
private fun SunLegend() {
    val items = listOf(
        SunPhases.Phase.DAY to R.string.phase_day,
        SunPhases.Phase.GOLDEN to R.string.phase_golden,
        SunPhases.Phase.BLUE to R.string.phase_blue,
        SunPhases.Phase.NIGHT to R.string.phase_night,
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (phase, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp, 10.dp)) {
                    drawRoundRect(phaseTint(phase), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(Color(0x33FFFFFF), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1f))
                }
                Spacer(Modifier.width(5.dp))
                Text(stringResource(name), fontSize = 11.sp, color = NimbusColors.Secondary)
            }
        }
    }
}

@Composable
private fun SunChart(
    curve: List<Pair<Long, Double>>, spans: List<SunPhases.Span>, maxAlt: Double, start: Long, end: Long,
    rise: Long?, set: Long?, noon: Pair<Long, Double>, now: Long, altNow: Double, modifier: Modifier,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val horizonLabel = stringResource(R.string.horizon)
    // Fixed scale for the place: midsummer noon at the top, midwinter midnight at the bottom.
    val hi = maxAlt + 5
    val lo = -maxAlt - 1
    Canvas(modifier.height(150.dp)) {
        val labelH = measurer.measure("00", labelStyle).size.height
        val top = 2.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val halfLabel = measurer.measure("24", labelStyle).size.width / 2f
        val l = halfLabel; val r = size.width - halfLabel
        val span = (end - start).toFloat()
        fun x(t: Long) = l + (r - l) * ((t - start) / span)
        fun y(a: Double) = (top + (hi - a) / (hi - lo) * (bottom - top)).toFloat()
        val horizon = y(0.0)
        // Light phases as background columns
        spans.forEach { sp -> drawRect(phaseTint(sp.phase), Offset(x(sp.start), top), Size(x(sp.end) - x(sp.start), bottom - top)) }
        // Time grid
        var mark = start
        while (mark <= end) {
            val xm = x(mark)
            drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val lt = measurer.measure(if (mark == end) tf.hourEnd(mark) else tf.hour(mark), labelStyle)
            // inside the chart: "12 AM" is wider than the "24" the margins are made for (it would be cut off)
            drawText(lt, topLeft = Offset(axisLabelLeft(xm, lt.size.width, size.width), bottom + 4.dp.toPx()))
            mark += 6 * 3_600_000L
        }
        // Daylight under the arc
        val area = Path().apply {
            moveTo(x(curve.first().first), horizon)
            curve.forEach { (t, a) -> lineTo(x(t), minOf(y(a), horizon)) }
            lineTo(x(curve.last().first), horizon); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(SunYellow.copy(alpha = 0.35f), SunYellow.copy(alpha = 0.05f)), y(maxAlt), horizon))
        // Curve: bright above, faint below the horizon
        val path = Path()
        curve.forEachIndexed { i, (t, a) -> if (i == 0) path.moveTo(x(t), y(a)) else path.lineTo(x(t), y(a)) }
        clipRect(bottom = horizon) { drawPath(path, SunYellow.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
        clipRect(top = horizon) { drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))) }
        // Horizon
        drawLine(Color.White.copy(alpha = 0.45f), Offset(l, horizon), Offset(r, horizon), 1.dp.toPx())
        val hl = measurer.measure(horizonLabel, labelStyle)
        drawText(hl, topLeft = Offset(l + 4.dp.toPx(), horizon - hl.size.height - 1.dp.toPx()))
        // Sunrise / sunset marks
        listOfNotNull(rise, set).forEach { t -> drawCircle(SunYellow, 3.dp.toPx(), Offset(x(t), horizon)) }
        // Highest and lowest point
        if (noon.second > 0) {
            val nl = measurer.measure("${noon.second.roundToInt()}°", labelStyle)
            drawText(nl, topLeft = Offset(x(noon.first) - nl.size.width / 2f, y(noon.second) - nl.size.height - 2.dp.toPx()))
        }
        // Now
        val c = Offset(x(now), y(altNow))
        if (altNow >= 0) {
            drawCircle(SunYellow.copy(alpha = 0.25f), 11.dp.toPx(), c)
            drawCircle(SunYellow, 6.dp.toPx(), c)
        } else {
            drawCircle(Color(0xFF1A2A40), 6.dp.toPx(), c)
            drawCircle(Color.White.copy(alpha = 0.8f), 5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
    }
}
