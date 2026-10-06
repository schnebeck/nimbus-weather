/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HourlyCard.kt
 * The next 24 hours in a row: symbol, chance, temperature, sunrise and sunset.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.InfoButton
import dev.nimbus.weather.ui.components.Term
import androidx.compose.foundation.layout.offset
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import dev.nimbus.weather.data.model.HourlyPoint

@Composable
fun HourlyCard(data: WeatherData, now: Long) {
    val settings = LocalSettings.current
    val tf = LocalTimeFormat.current
    val hours = remember(data, now) { Insights.upcomingHours(data, now) }
    if (hours.isEmpty()) return
    val items = remember(hours, data.daily) {
        val list = mutableListOf<HourItem>()
        hours.forEachIndexed { i, h -> list += HourItem.Hour(h, i == 0) }
        val start = hours.first().time
        val end = hours.last().time
        data.daily.forEach { d ->
            d.sunrise?.let { if (it in (now + 1)..end) list += HourItem.Sun(it, true) }
            d.sunset?.let { if (it in (now + 1)..end) list += HourItem.Sun(it, false) }
        }
        list.sortedBy { if (it is HourItem.Hour && it.isNow) start - 1 else it.time }
    }
    val summary = outlookText(data, now)
    // One width for every cell: wide enough for the widest label, chance and temperature in the
    // font size set ("Jetzt", "07:25", "-12°") – the cells grow with a large font, all alike
    val nowLabel = stringResource(R.string.now)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellWidth = remember(items, nowLabel, settings.temperatureUnit, density) {
        fun widest(texts: List<String>, style: androidx.compose.ui.text.TextStyle) =
            with(density) { (texts.maxOfOrNull { measurer.measure(it, style, softWrap = false, density = density).size.width } ?: 0).toDp() }
        val labels = items.map { if (it is HourItem.Hour) (if (it.isNow) nowLabel else tf.hour(it.point.time)) else tf.time(it.time) }
        val temps = items.mapNotNull { (it as? HourItem.Hour)?.let { h -> Units.temp(if (h.isNow) data.current.temperature else h.point.temperature, settings.temperatureUnit) } }
        val chances = items.mapNotNull { (it as? HourItem.Hour)?.point?.let { p -> Insights.chanceText(p.precipitationProbability, p.precipitation) + NBSP + "%" } }
        maxOf(
            52.dp,
            widest(labels, androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) + 8.dp,
            widest(temps, androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)) + 8.dp,
            widest(chances, ChanceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)) + 6.dp,
        )
    }
    // The short forecast replaces the title: one card instead of two.
    GlassCard(title = null) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                summary, Modifier.weight(1f).padding(top = 8.dp), fontSize = 15.sp, lineHeight = 21.sp, color = Color.White,
                style = androidx.compose.ui.text.TextStyle(hyphens = androidx.compose.ui.text.style.Hyphens.Auto),
            )
            InfoButton(Term.HOURLY, Modifier.offset(x = 8.dp))
        }
        Spacer(Modifier.height(6.dp))
        HairlineDivider()
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items, key = { (if (it is HourItem.Sun) "s" else "h") + it.time }) { item ->
                when (item) {
                    // "Now" shows the current (possibly measured) weather, like the header.
                    is HourItem.Hour -> HourCell(
                        label = if (item.isNow) stringResource(R.string.now) else tf.hour(item.point.time),
                        condition = if (item.isNow) data.current.condition else item.point.condition,
                        isDay = if (item.isNow) data.current.isDay else item.point.isDay,
                        precipProb = item.point.precipitationProbability,
                        precipAmount = item.point.precipitation,
                        value = Units.temp(if (item.isNow) data.current.temperature else item.point.temperature, settings.temperatureUnit),
                        bold = item.isNow,
                        width = cellWidth,
                    )
                    is HourItem.Sun -> SunCell(tf.time(item.time), item.rise, cellWidth + 4.dp)
                }
            }
        }
    }
}

@Composable
private fun HourCell(label: String, condition: Condition, isDay: Boolean, precipProb: Double?, precipAmount: Double?, value: String, bold: Boolean, width: androidx.compose.ui.unit.Dp) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium, color = Color.White, maxLines = 1)
        Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(condition, isDay, size = 26.dp)
                // Shown regardless of the symbol: fog or clouds can still come with a 40 % rain risk.
                ChanceText(precipProb, precipAmount)
            }
        }
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun SunCell(time: String, rise: Boolean, width: androidx.compose.ui.unit.Dp) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.Center) { SunHorizonGlyph(rise) }
        // Short label ("Untergang"), shrinking a little rather than being cut off ("Sonnenu…")
        androidx.compose.foundation.text.BasicText(
            stringResource(if (rise) R.string.hour_sunrise else R.string.hour_sunset),
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}

@Composable
private fun SunHorizonGlyph(rise: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val s = size.minDimension
        val horizon = s * 0.66f
        val r = s * 0.26f
        drawArc(Color(0xFFFFC53D), 180f, 180f, true, topLeft = Offset(s / 2 - r, horizon - r), size = Size(r * 2, r * 2))
        val stroke = s * 0.07f
        drawLine(Color.White, Offset(s * 0.06f, horizon), Offset(s * 0.94f, horizon), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        // Arrow above the sun: up for sunrise, down for sunset.
        val top = s * 0.02f
        val bottom = s * 0.3f
        val tip = if (rise) top else bottom
        val tail = if (rise) bottom else top
        val wing = if (rise) s * 0.1f else -s * 0.1f
        drawLine(Color.White, Offset(s / 2, tail), Offset(s / 2, tip), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(s / 2, tip), Offset(s / 2 - s * 0.1f, tip + wing), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(s / 2, tip), Offset(s / 2 + s * 0.1f, tip + wing), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

private sealed interface HourItem {
    val time: Long
    data class Hour(val point: HourlyPoint, val isNow: Boolean) : HourItem { override val time get() = point.time }
    data class Sun(override val time: Long, val rise: Boolean) : HourItem
}
