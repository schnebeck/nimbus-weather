/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/MoonCard.kt
 * The moon, calculated on the device: phase, rise and set, next full moon, its course over the day.
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.foundation.layout.size
import dev.nimbus.weather.ui.components.drawMoonPhase
import dev.nimbus.weather.util.Moon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.nimbus.weather.util.SkyCourse
import kotlin.math.roundToInt
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.LocalExplain
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors

@Composable
fun MoonCard(data: WeatherData, now: Long) {
    val tf = LocalTimeFormat.current
    val explain = LocalExplain.current
    val lat = data.place.latitude
    val lon = data.place.longitude
    val info = remember(now / 600_000) { Moon.preciseIllumination(now) }
    val date = tf.zoned(now).toLocalDate()
    val dayStart = date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val times = remember(lat, lon, dayStart) { Moon.times(dayStart, lat, lon) }
    // its course over today and when it stands in each direction, for the chart
    val course = remember(lat, lon, dayStart) { SkyCourse.moon(dayStart, dayEnd, lat, lon) }
    val crossings = remember(course) { SkyCourse.crossings(course) }
    // the hours the sun is down: when the moon is seen in a dark sky
    val dark = remember(lat, lon, dayStart) { nights(dayStart, dayEnd, lat, lon) }
    val altNow = Math.toDegrees(Moon.altitude(now, lat, lon))
    // its highest only where it culminates within the day: at midnight's edge it is still rising
    // or already sinking, and the edge's altitude is not its highest
    val highest = remember(course) {
        (1 until course.lastIndex).filter { course[it].altitude >= course[it - 1].altitude && course[it].altitude >= course[it + 1].altitude }
            .map { course[it] }.maxByOrNull { it.altitude }?.takeIf { it.altitude > 0 }
    }
    val nextFull = remember(now / 3_600_000) { Moon.nextFullMoon(now) }
    val phaseName = stringResource(
        when (Moon.phaseOf(info.phase)) {
            Moon.Phase.NEW -> R.string.moon_new
            Moon.Phase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
            Moon.Phase.FIRST_QUARTER -> R.string.moon_first_quarter
            Moon.Phase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
            Moon.Phase.FULL -> R.string.moon_full
            Moon.Phase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
            Moon.Phase.LAST_QUARTER -> R.string.moon_last_quarter
            Moon.Phase.WANING_CRESCENT -> R.string.moon_waning_crescent
        },
    )
    GlassCard(
        title = stringResource(R.string.moon), icon = Icons.Outlined.DarkMode, info = Term.MOON, onClick = { explain(Term.MOON) },
        summary = stringResource(R.string.moon_summary, phaseName, Moon.percent(info)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(84.dp)) {
                drawMoonPhase(center, size.minDimension / 2 * 0.92f, info.phase.toFloat(), lat < 0)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(phaseName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 24.sp)
                Text(stringResource(R.string.moon_illumination, Moon.percent(info)), fontSize = 14.sp, color = NimbusColors.Secondary)
                Spacer(Modifier.height(8.dp))
                when {
                    times.alwaysUp -> Caption(stringResource(R.string.moon_always_up))
                    times.alwaysDown -> Caption(stringResource(R.string.moon_always_down))
                    else -> {
                        // where on the horizon: within a month it moves between north-east and south-east
                        MoonRow(stringResource(R.string.moonrise), times.rise?.let { tf.time(it) + " · " + directionName(Moon.azimuth(it, lat, lon)) } ?: "–")
                        MoonRow(stringResource(R.string.moonset), times.set?.let { tf.time(it) + " · " + directionName(Moon.azimuth(it, lat, lon)) } ?: "–")
                    }
                }
                MoonRow(stringResource(R.string.next_full_moon), tf.dayMonth(nextFull))
            }
        }
        Spacer(Modifier.height(12.dp))
        MoonChart(
            course, SkyCourse.moonMaxAltitude(lat), dayStart, dayEnd, times, now, altNow, info.phase.toFloat(), lat < 0, dark, crossings, highest,
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
        )
        Spacer(Modifier.height(10.dp))
        val deg = { v: Double -> "${kotlin.math.abs(v).roundToInt()}°" }
        Caption(
            buildString {
                append(
                    if (altNow >= 0) stringResource(R.string.moon_now_up, deg(altNow), directionName(Moon.azimuth(now, lat, lon)))
                    else stringResource(R.string.moon_now_down, deg(altNow)),
                )
                if (highest != null) append(" · ").append(stringResource(R.string.sun_highest, deg(highest.altitude), tf.time(highest.time)))
            },
        )
    }
}

@Composable
private fun MoonRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = NimbusColors.Secondary, maxLines = 1)
        Text(value, fontSize = 13.sp, color = Color.White, maxLines = 1)
    }
}

private val MoonSilver = Color(0xFFD8E2F5)

/**
 * The moon's course over today (00–24 h, as the sun's): its altitude on a scale fixed for the
 * place (± its highest there), the hours the sun is down shaded, the moon now in its phase, and
 * on the axis when it stands in which direction.
 */
@Composable
private fun MoonChart(
    course: List<SkyCourse.Point>, maxAlt: Double, start: Long, end: Long, times: Moon.Times, now: Long, altNow: Double,
    phase: Float, southern: Boolean, dark: List<LongRange>, crossings: List<SkyCourse.Crossing>, highest: SkyCourse.Point?, modifier: Modifier,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val horizonLabel = stringResource(R.string.horizon)
    val names = directionNames()
    val hi = maxAlt + 5
    val lo = -maxAlt - 1
    Canvas(modifier.height(166.dp)) {
        val top = 2.dp.toPx()
        val bottom = size.height - skyAxisHeight(measurer)
        val halfLabel = measurer.measure("24", SkyAxisStyle).size.width / 2f
        val l = halfLabel; val r = size.width - halfLabel
        val span = (end - start).toFloat()
        fun x(t: Long) = l + (r - l) * ((t - start) / span)
        fun y(a: Double) = (top + (hi - a) / (hi - lo) * (bottom - top)).toFloat()
        val horizon = y(0.0)
        dark.forEach { n -> drawRect(NightShade, Offset(x(n.first), top), Size(x(n.last + 1) - x(n.first), bottom - top)) }
        var mark = start
        while (mark <= end) {
            val xm = x(mark)
            drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            mark += 6 * 3_600_000L
        }
        drawSkyAxis(start, end, ::x, tf, measurer, crossings, names, MoonSilver)
        // moonlight under the arc
        val area = Path().apply {
            moveTo(x(course.first().time), horizon)
            course.forEach { p -> lineTo(x(p.time), minOf(y(p.altitude), horizon)) }
            lineTo(x(course.last().time), horizon); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(MoonSilver.copy(alpha = 0.28f), MoonSilver.copy(alpha = 0.04f)), y(maxAlt), horizon))
        // bright above, faint below the horizon
        val path = Path()
        course.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.time), y(p.altitude)) else path.lineTo(x(p.time), y(p.altitude)) }
        clipRect(bottom = horizon) { drawPath(path, MoonSilver.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
        clipRect(top = horizon) { drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))) }
        drawLine(Color.White.copy(alpha = 0.45f), Offset(l, horizon), Offset(r, horizon), 1.dp.toPx())
        val hl = measurer.measure(horizonLabel, SkyAxisStyle)
        drawText(hl, topLeft = Offset(l + 4.dp.toPx(), horizon - hl.size.height - 1.dp.toPx()))
        listOfNotNull(times.rise, times.set).forEach { t -> drawCircle(MoonSilver, 3.dp.toPx(), Offset(x(t), horizon)) }
        if (highest != null) {
            val nl = measurer.measure("${highest.altitude.roundToInt()}°", SkyAxisStyle)
            drawText(nl, topLeft = Offset(axisLabelLeft(x(highest.time), nl.size.width, size.width), y(highest.altitude) - nl.size.height - 2.dp.toPx()))
        }
        // now: the moon in its phase, faint while below the horizon
        val c = Offset(x(now), y(altNow))
        drawCircle(Color(0xFF1A2A40), 9.dp.toPx(), c)
        drawMoonPhase(c, 7.5.dp.toPx(), phase, southern, alpha = if (altNow >= 0) 1f else 0.5f, darkSide = Color(0x40FFFFFF))
    }
}
