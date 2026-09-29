/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PollenCard.kt
 * Pollen forecast card with levels per type and the composition of the next hours.
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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import kotlin.math.roundToInt

val PollenColors = mapOf(
    PollenType.HAZEL to Color(0xFFD4A96A), PollenType.ALDER to Color(0xFFA9825A), PollenType.ASH to Color(0xFF9DB6C4),
    PollenType.BIRCH to Color(0xFFF0DA6E), PollenType.GRASS to Color(0xFF7ED957), PollenType.RYE to Color(0xFFC3DE6F),
    PollenType.MUGWORT to Color(0xFFCB92DD), PollenType.RAGWEED to Color(0xFFFF7F5E), PollenType.OLIVE to Color(0xFF6FB08A),
)

fun pollenName(type: PollenType): Int = when (type) {
    PollenType.HAZEL -> R.string.pollen_hazel
    PollenType.ALDER -> R.string.pollen_alder
    PollenType.ASH -> R.string.pollen_ash
    PollenType.BIRCH -> R.string.pollen_birch
    PollenType.GRASS -> R.string.pollen_grass
    PollenType.RYE -> R.string.pollen_rye
    PollenType.MUGWORT -> R.string.pollen_mugwort
    PollenType.RAGWEED -> R.string.pollen_ragweed
    PollenType.OLIVE -> R.string.pollen_olive
}

/** DWD wording for the seven levels 0, 0–1, 1, … 3. */
fun pollenLevelText(level: Float): Int = when {
    level <= 0f -> R.string.pollen_level_0
    level <= 0.5f -> R.string.pollen_level_05
    level <= 1f -> R.string.pollen_level_1
    level <= 1.5f -> R.string.pollen_level_15
    level <= 2f -> R.string.pollen_level_2
    level <= 2.5f -> R.string.pollen_level_25
    else -> R.string.pollen_level_3
}

fun pollenLevelColor(level: Float): Color = when {
    level <= 0f -> Color(0x33FFFFFF)
    level <= 1f -> Color(0xFF7ED957)
    level <= 2f -> Color(0xFFF7C948)
    else -> Color(0xFFFF5B36)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollenForecastCard(pollen: PollenForecast, now: Long) {
    val tf = LocalTimeFormat.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val days = remember(pollen, now) { pollen.days.filter { it.date + 24 * 3600_000L > now }.take(3) }
    val today = days.firstOrNull()
    // Rows: every type that is active on any of the shown days; the rest is summarised.
    val allTypes = remember(pollen) { pollen.days.flatMap { it.levels.keys }.distinct().sortedBy { it.ordinal } }
    val active = remember(days) {
        allTypes.filter { t -> days.any { (it.levels[t] ?: 0f) > 0f } }
            .sortedByDescending { t -> days.maxOf { it.levels[t] ?: 0f } }
    }
    val todayMax = today?.levels?.values?.maxOrNull() ?: 0f
    val leaders = today?.levels?.filterValues { it > 0f && it >= todayMax - 0.5f }?.keys?.sortedByDescending { today.levels[it] }.orEmpty()

    GlassCard(
        title = stringResource(R.string.pollen_forecast), icon = Icons.Outlined.Grass, info = Term.POLLEN,
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.animateContentSize()) {
            // Summary
            Text(stringResource(pollenLevelText(todayMax)), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(
                if (leaders.isNotEmpty()) leaders.map { stringResource(pollenName(it)) }.joinToString(", ")
                else stringResource(R.string.pollen_none_active, allTypes.size),
                fontSize = 14.sp, color = NimbusColors.Secondary,
            )
            Spacer(Modifier.height(10.dp))

            // Day table
            if (days.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1.3f))
                    days.forEachIndexed { i, d ->
                        Text(
                            when (i) {
                                0 -> stringResource(R.string.today)
                                1 -> stringResource(R.string.tomorrow)
                                else -> tf.weekdayShort(d.date)
                            },
                            Modifier.weight(1f), fontSize = 12.sp, color = NimbusColors.Tertiary, textAlign = TextAlign.Center,
                        )
                    }
                }
                val rows = if (expanded || active.isEmpty()) (active + allTypes.filter { it !in active }) else active
                rows.forEach { type ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PollenColors.getValue(type)))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(pollenName(type)), fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        days.forEach { d -> LevelGauge(d.levels[type], Modifier.weight(1f).padding(horizontal = 6.dp)) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                LevelLegend()
            }

            // Composition from CAMS concentrations
            val composition = remember(pollen, now) { composition(pollen, now) }
            Spacer(Modifier.height(12.dp))
            HairlineDivider()
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.pollen_composition), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (composition == null) {
                Text(stringResource(R.string.pollen_composition_none), fontSize = 13.sp, color = NimbusColors.Secondary)
            } else {
                Spacer(Modifier.height(6.dp))
                val total = composition.values.sum()
                Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                    var x = 0f
                    composition.forEach { (type, v) ->
                        val w = (v / total * size.width).toFloat()
                        drawRect(PollenColors.getValue(type), Offset(x, 0f), Size(w, size.height))
                        x += w
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    composition.forEach { (type, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PollenColors.getValue(type)))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(pollenName(type)) + " " + (v / total * 100).roundToInt() + NBSP + "%",
                                fontSize = 12.sp, color = NimbusColors.Secondary,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.pollen_composition_total, String.format("%.1f", total)),
                    fontSize = 11.sp, color = NimbusColors.Tertiary, modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Hourly course (expanded)
            if (expanded && pollen.hourlyTimes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.pollen_course), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                PollenCourse(pollen, now)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (pollen.source == PollenSourceKind.DWD) stringResource(R.string.pollen_source_dwd, pollen.region ?: "")
                else stringResource(R.string.pollen_source_cams),
                fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp,
            )
            Text(
                stringResource(if (expanded) R.string.show_less else R.string.pollen_show_details),
                Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9FD8FF),
            )
        }
    }
}

/** Three segments (low / moderate / high); half levels fill half a segment. */
@Composable
private fun LevelGauge(level: Float?, modifier: Modifier) {
    Canvas(modifier.height(8.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - 2 * gap) / 3
        val r = CornerRadius(size.height / 2)
        if (level == null) {
            drawLine(Color(0x55FFFFFF), Offset(size.width * 0.4f, size.height / 2), Offset(size.width * 0.6f, size.height / 2), 1.5.dp.toPx())
            return@Canvas
        }
        val color = pollenLevelColor(level)
        for (i in 0 until 3) {
            val x = i * (w + gap)
            drawRoundRect(Color(0x26FFFFFF), Offset(x, 0f), Size(w, size.height), r)
            val fill = (level - i).coerceIn(0f, 1f)
            if (fill > 0f) drawRoundRect(color, Offset(x, 0f), Size(w * fill, size.height), r)
        }
    }
}

@Composable
private fun LevelLegend() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(1f to R.string.pollen_level_1, 2f to R.string.pollen_level_2, 3f to R.string.pollen_level_3).forEach { (l, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelGauge(l, Modifier.width(34.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(s), fontSize = 11.sp, color = NimbusColors.Tertiary)
            }
        }
    }
}

/** Mean concentration per type over the next 24 h; null if there is (almost) nothing in the air. */
fun composition(pollen: PollenForecast, now: Long): Map<PollenType, Double>? {
    val idx = pollen.hourlyTimes.indices.filter { pollen.hourlyTimes[it] in now - 3600_000L..now + 24 * 3600_000L }
    if (idx.isEmpty()) return null
    val means = pollen.hourly.mapValues { (_, v) -> idx.mapNotNull { v.getOrNull(it) }.average().takeIf { !it.isNaN() } ?: 0.0 }
        .filterValues { it >= 0.05 }
    if (means.values.sum() < 0.5) return null
    return means.entries.sortedByDescending { it.value }.associate { it.key to it.value }
}

/** Stacked area chart of the hourly concentrations. */
@Composable
private fun PollenCourse(pollen: PollenForecast, now: Long) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val start = pollen.hourlyTimes.indexOfFirst { it >= now - 3600_000L }.coerceAtLeast(0)
    val times = pollen.hourlyTimes.drop(start)
    val types = pollen.hourly.keys.sortedBy { it.ordinal }
    val series = types.associateWith { t -> pollen.hourly.getValue(t).drop(start).map { it ?: 0.0 } }
    val totals = times.indices.map { i -> types.sumOf { series.getValue(it).getOrElse(i) { 0.0 } } }
    val max = (totals.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        if (times.size < 2) return@Canvas
        val axisH = 16.dp.toPx()
        val h = size.height - axisH
        val dx = size.width / (times.size - 1)
        for (k in 1..3) {
            val y = h * k / 4
            drawLine(Color(0x1FFFFFFF), Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        val base = DoubleArray(times.size)
        types.forEach { t ->
            val v = series.getValue(t)
            val path = Path()
            for (i in times.indices) {
                val x = i * dx
                val y = (h - (base[i] + v.getOrElse(i) { 0.0 }) / max * h).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in times.indices.reversed()) path.lineTo(i * dx, (h - base[i] / max * h).toFloat())
            path.close()
            drawPath(path, PollenColors.getValue(t).copy(alpha = 0.85f))
            for (i in times.indices) base[i] += v.getOrElse(i) { 0.0 }
        }
        times.forEachIndexed { i, t ->
            val z = tf.zoned(t)
            if (z.hour == 0 && i > 0) {
                drawLine(Color(0x55FFFFFF), Offset(i * dx, 0f), Offset(i * dx, h), 1f)
            }
            if (z.hour == 12) {
                val l = measurer.measure(tf.weekdayShort(t), labelStyle)
                drawText(l, topLeft = Offset((i * dx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), h + 2.dp.toPx()))
            }
        }
        val maxLabel = measurer.measure("${max.roundToInt()}${NBSP}/m³", labelStyle)
        drawText(maxLabel, topLeft = Offset(2.dp.toPx(), 0f))
    }
}
