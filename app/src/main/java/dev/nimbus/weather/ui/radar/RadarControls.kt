/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarControls.kt
 * The radar screen's controls: time line slider, step buttons, legend, chips, the loading hint.
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

package dev.nimbus.weather.ui.radar

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.ui.theme.NimbusColors
import java.util.TimeZone

@Composable
internal fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).size(32.dp),
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineSlider(
    tl: RadarTimeline, frame: Int,
    /** The last step that can be chosen ([lastShown]); beyond it the track is greyed out. */
    last: Int = tl.frames.lastIndex,
    onChange: (Int) -> Unit,
) {
    val n = tl.frames.size - 1
    Slider(
        value = frame.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceAtMost(last)) },
        valueRange = 0f..n.toFloat(),
        steps = n - 1,
        // the slider runs almost to the screen's edges: dragging it there is for the slider, not
        // the system's back gesture (gesture navigation)
        modifier = Modifier.height(36.dp).systemGestureExclusion(),
        thumb = {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val y = size.height / 2
                val h = 4.dp.toPx()
                val pos = size.width * (state.value / n)
                if (tl.day != null) {
                    // Archived day: one track, a tick every 3 hours (longer every 6 hours)
                    drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
                    drawLine(Color(0xCCFFFFFF), Offset(0f, y), Offset(pos, y), h, StrokeCap.Round)
                    val perHour = (3_600_000L / RadarSources.ARCHIVE_STEP_MS).toInt()
                    for (i in 0..n step 3 * perHour) {
                        val x = size.width * i / n
                        val long = i % (6 * perHour) == 0
                        drawLine(Color(0x80FFFFFF), Offset(x, y + 6.dp.toPx()), Offset(x, y + (if (long) 11 else 9).dp.toPx()), 1.dp.toPx())
                    }
                    return@Canvas
                }
                val nowX = size.width * tl.nowIndex / n
                // past = white-ish, forecast = amber – grey where the area has none to show
                drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(nowX, y), h, StrokeCap.Round)
                drawLine(if (last < n) ForecastOff else Color(0x66FFD27A), Offset(nowX, y), Offset(size.width, y), h, StrokeCap.Round)
                drawLine(Color(0xCCFFFFFF), Offset(0f, y), Offset(minOf(pos, nowX), y), h, StrokeCap.Round)
                if (pos > nowX) drawLine(Color(0xFFFFD27A), Offset(nowX, y), Offset(pos, y), h, StrokeCap.Round)
                // ticks at full hours (24 h: every 3 hours) and the "now" marker
                val tz = java.util.TimeZone.getDefault()
                val every = if (tl.range.hours >= 24) 3 else 1
                tl.frames.forEachIndexed { i, f ->
                    val local = f.time + tz.getOffset(f.time)
                    if (local % 3_600_000L != 0L || (local / 3_600_000L) % every != 0L) return@forEachIndexed
                    val x = size.width * i / n
                    drawLine(Color(0x80FFFFFF), Offset(x, y + 6.dp.toPx()), Offset(x, y + 9.dp.toPx()), 1.dp.toPx())
                }
                drawLine(Color.White, Offset(nowX, y - 7.dp.toPx()), Offset(nowX, y + 7.dp.toPx()), 1.5.dp.toPx())
            }
        },
    )
}

/** The forecast part of the time line where the picture area has no nowcast: there, but not to be chosen. */
internal val ForecastOff = Color(0x1FFFFFFF)

/** Land colour of the slate map style, under the legend bars. */
private val MapLand = Color(0xFF505E6F)

/**
 * The steps of the scales: the reflectivity (dBZ) where each lies on the bars and its rain rate
 * (mm/h, Marshall–Palmer) – drizzle at the weakest step drawn, then light, moderate, heavy, severe.
 */
internal val LegendSteps = listOf(
    8 to (R.string.radar_scale_drizzle to 0.1), 23 to (R.string.radar_scale_light to 1.0), 34 to (R.string.radar_scale_moderate to 5.0),
    44 to (R.string.radar_scale_heavy to 20.0), 50 to (R.string.radar_scale_severe to 50.0),
)

/** Where [dbz] lies on a legend bar (0 at its left end, 1 at its right). */
internal fun legendX(dbz: Int): Float =
    ((dbz - RadarPalette.LEGEND_MIN_DBZ).toFloat() / (RadarPalette.LEGEND_MAX_DBZ - RadarPalette.LEGEND_MIN_DBZ)).coerceIn(0f, 1f)

/**
 * Rain and, where it can snow, snow scale – each a bar in the width of the screen – with the
 * steps under them (word and rain rate), plus the temperature scale when shown.
 */
@Composable
internal fun Legend(showTemp: Boolean, showSnow: Boolean, unit: dev.nimbus.weather.data.model.TemperatureUnit) {
    val labelW = 48.dp
    @Composable
    fun Bar(label: String, colors: List<Color>) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(labelW), fontSize = 10.sp, color = NimbusColors.Secondary, maxLines = 1)
            Canvas(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                // On the map's land colour: the weakest, see-through steps look exactly as on the map
                drawRect(MapLand)
                drawRect(Brush.horizontalGradient(colors))
            }
        }
    }
    Bar(stringResource(R.string.legend_rain), RadarPalette.legendRain.map { Color(it) })
    if (showSnow) {
        Spacer(Modifier.height(3.dp))
        Bar(stringResource(R.string.legend_snow), RadarPalette.legendSnow.map { Color(it) })
    }
    val p = dev.nimbus.weather.ui.main.LocalSettings.current.precipitationUnit
    val inch = p == dev.nimbus.weather.data.model.PrecipitationUnit.INCH
    val rateUnit = if (inch) "in/h" else "mm/h"
    val steps = LegendSteps.mapIndexed { i, (dbz, s) ->
        val (word, mm) = s
        val v = mm / (if (inch) 25.4 else 1.0)
        val rate = when {
            inch -> String.format(java.util.Locale.getDefault(), if (v < 0.1) "%.3f" else if (v < 1) "%.2f" else "%.1f", v)
            mm < 1 -> dev.nimbus.weather.util.Units.oneDecimal(mm)
            else -> mm.roundToInt().toString()
        }
        Triple(legendX(dbz), stringResource(word), if (i == LegendSteps.lastIndex) rate + dev.nimbus.weather.util.NBSP + rateUnit else rate)
    }
    Row { Spacer(Modifier.width(labelW)); LegendTicks(steps, Modifier.weight(1f)) }
    if (showTemp) {
        Spacer(Modifier.height(4.dp))
        // Discrete bands like on the map (areas of equal temperature), in the display unit.
        val f = unit == dev.nimbus.weather.data.model.TemperatureUnit.FAHRENHEIT
        val lo = if (f) -4 else -20
        val hi = if (f) 104 else 40
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            val n = hi - lo
            val bw = size.width / n
            for (k in 0 until n) {
                drawRect(
                    Color(WeatherOverlays.bandColor(lo + k, unit)),
                    androidx.compose.ui.geometry.Offset(k * bw, 0f), androidx.compose.ui.geometry.Size(bw + 0.5f, size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..3).map { lo + (hi - lo) * it / 3 }.forEach {
                Text("$it°", fontSize = 10.sp, color = NimbusColors.Tertiary)
            }
        }
    }
}

@Composable
internal fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Color(0xFF14305E) else Color.White,
    )
}

/**
 * The hint under the top bar while the loop loads ([stillLoading]: steps downloaded of [total],
 * then their cutting for the view), or when something failed – the screen [error], or tiles the
 * network did not deliver ([net]) – with "load again" ([onReload]).
 */
@Composable
internal fun RadarStatusHint(
    error: Boolean, stillLoading: Boolean, loadedFrames: Int, total: Int, net: RadarNetStatus.State,
    modifier: Modifier, onReload: () -> Unit,
) {
    val trouble = net.failed > 0 || net.fromCache > 0
    if (!error && !stillLoading && !trouble) return
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC0B1424)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (stillLoading && !error) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    // all downloaded: the steps are being cut and smoothed for the view
                    if (loadedFrames >= total) stringResource(R.string.radar_preparing)
                    else stringResource(R.string.radar_loading_frames, loadedFrames, total),
                    color = Color.White, fontSize = 13.sp,
                )
            }
        }
        val msg = when {
            error -> R.string.radar_error
            net.failed > 0 && net.fromCache > 0 -> R.string.radar_partly_cached
            net.failed > 0 -> R.string.radar_partly_missing
            else -> null
        }
        if (msg != null) {
            if (stillLoading && !error) Spacer(Modifier.height(6.dp))
            Text(stringResource(msg), color = Color(0xFFFFD27A), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.radar_reload), color = Color(0xFF9CC8FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReload).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Words and rates at their places under the bars ([steps]: position 0–1, word, rate): the first
 * from the left end, the last up to the right end, the others centred; a step that would run into
 * the one before (large fonts) is left out rather than drawn over it.
 */
@Composable
private fun LegendTicks(steps: List<Triple<Float, String, String>>, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val wordStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary)
    val rateStyle = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = NimbusColors.Tertiary)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val h = with(density) { (measurer.measure("Ag", wordStyle).size.height + measurer.measure("0", rateStyle).size.height).toDp() } + 3.dp
    Canvas(modifier.height(h)) {
        var right = -Float.MAX_VALUE
        val gap = 6.dp.toPx()
        steps.forEachIndexed { i, (x, word, rate) ->
            val w = measurer.measure(word, wordStyle)
            val r = measurer.measure(rate, rateStyle)
            val width = maxOf(w.size.width, r.size.width).toFloat()
            val left = when (i) {
                0 -> 0f
                steps.lastIndex -> size.width - width
                else -> x * size.width - width / 2
            }.coerceIn(0f, maxOf(0f, size.width - width))
            if (left < right + gap) return@forEachIndexed
            // word and rate aligned like the step: left, centred or right
            fun at(tw: Int) = when (i) { 0 -> left; steps.lastIndex -> left + width - tw; else -> left + (width - tw) / 2 }
            drawText(w, topLeft = Offset(at(w.size.width), 2.dp.toPx()))
            drawText(r, topLeft = Offset(at(r.size.width), 2.dp.toPx() + w.size.height))
            right = left + width
        }
    }
}
