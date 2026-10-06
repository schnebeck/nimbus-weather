/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ModelComparisonCard.kt
 * The forecast models side by side: temperature and precipitation of the next days.
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Units
import kotlin.math.floor
import kotlin.math.roundToInt

val ModelColors = listOf(
    Color(0xFF64B5F6), Color(0xFF4DD0E1), Color(0xFFFFB74D), Color(0xFFE57373),
    Color(0xFFBA68C8), Color(0xFF81C784), Color(0xFFF06292), Color(0xFFFFF176),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelComparisonCard(models: List<ModelSeries>?, now: Long, onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }
    val tf = LocalTimeFormat.current
    val s = LocalSettings.current
    var highlighted by remember { mutableStateOf<String?>(null) }
    val measurer = rememberTextMeasurer()
    GlassCard(title = stringResource(R.string.model_comparison), icon = Icons.Outlined.QueryStats, info = Term.MODELS) {
        Text(stringResource(R.string.model_comparison_hint), fontSize = 13.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.height(10.dp))
        if (models == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
            return@GlassCard
        }
        if (models.isEmpty()) {
            Text(stringResource(R.string.models_unavailable), fontSize = 14.sp, color = Color.White)
            return@GlassCard
        }
        val times = models.first().times
        val start = times.indexOfFirst { it >= now - 3600_000L }.coerceAtLeast(0)
        val end = (start + 72).coerceAtMost(times.size)
        val all = models.flatMap { m -> m.temperature.subList(start, end).filterNotNull() }
        if (all.isEmpty()) return@GlassCard
        val lo = floor(Units.temperature(all.min(), s.temperatureUnit) - 1)
        val hi = Units.temperature(all.max(), s.temperatureUnit) + 1
        Row {
            Column(Modifier.height(170.dp).width(30.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${hi.roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
                Text("${((hi + lo) / 2).roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
                Text("${lo.roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
            }
            Canvas(Modifier.weight(1f).height(190.dp)) {
                val n = (end - start).coerceAtLeast(2)
                val dx = size.width / (n - 1)
                val labelBand = 20.dp.toPx()
                val plotH = size.height - labelBand
                fun y(v: Double) = (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
                for (k in 0..4) {
                    val yy = plotH * k / 4
                    drawLine(Color(0x1FFFFFFF), Offset(0f, yy), Offset(size.width, yy), 1f)
                }
                // midnight separators and a weekday label centred on each day
                val midnights = (start until end).filter { tf.zoned(times[it]).hour == 0 }
                midnights.forEach { i ->
                    val x = (i - start) * dx
                    drawLine(Color(0x40FFFFFF), Offset(x, 0f), Offset(x, plotH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                }
                val bounds = (listOf(start) + midnights + listOf(end - 1)).distinct()
                bounds.zipWithNext().forEach { (a, b) ->
                    if (b - a < 4) return@forEach
                    val layout = measurer.measure(tf.weekdayShort(times[a] + 3_600_000L), TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary))
                    val cx = ((a + b) / 2f - start) * dx
                    drawText(layout, topLeft = Offset(axisLabelLeft(cx, layout.size.width, size.width), plotH + 4.dp.toPx()))
                }
                // now marker
                val nowX = ((now - times[start]) / 3600_000f) * dx
                drawLine(Color.White.copy(alpha = 0.6f), Offset(nowX, 0f), Offset(nowX, plotH), 1.5f)
                models.forEachIndexed { mi, m ->
                    val path = Path()
                    var started = false
                    for (i in start until end) {
                        val v = m.temperature.getOrNull(i) ?: continue
                        val p = Offset((i - start) * dx, y(Units.temperature(v, s.temperatureUnit)))
                        if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                    }
                    val isHi = highlighted == null || highlighted == m.modelId
                    drawPath(
                        path, ModelColors[mi % ModelColors.size], alpha = if (isHi) 0.95f else 0.18f,
                        style = Stroke(width = (if (highlighted == m.modelId) 3.dp else 1.8.dp).toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEachIndexed { mi, m ->
                val selected = highlighted == m.modelId
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0x40FFFFFF) else Color(0x1AFFFFFF))
                        .clickable { highlighted = if (selected) null else m.modelId }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ModelColors[mi % ModelColors.size]))
                    Spacer(Modifier.width(5.dp))
                    Text(m.label, fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}
