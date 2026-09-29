/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ForecastCards.kt
 * Hourly and 10-day forecast, precipitation nowcast and weather alerts.
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.InfoButton
import dev.nimbus.weather.ui.components.Term
import androidx.compose.foundation.layout.offset
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units

private val PrecipBlue = Color(0xFF8FD3FF)

/** Chance of precipitation under a weather symbol: always shown, dimmed below 10 %. */
@Composable
private fun ChanceText(probability: Double?) {
    val v = Insights.chanceLabel(probability) ?: return
    val relevant = v >= Insights.CHANCE_RELEVANT
    Text(
        "$v${NBSP}%", fontSize = 11.sp,
        fontWeight = if (relevant) FontWeight.Bold else FontWeight.Medium,
        color = if (relevant) PrecipBlue else Color(0x99FFFFFF),
        style = ChanceStyle,
    )
}

/** Soft dark shadow keeps the blue percentages readable on bright, cloudy skies. */
private val ChanceStyle = androidx.compose.ui.text.TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 1f), 4f),
)

// ---------------------------------------------------------------------------------------
// Hourly

private sealed interface HourItem {
    val time: Long
    data class Hour(val point: HourlyPoint, val isNow: Boolean) : HourItem { override val time get() = point.time }
    data class Sun(override val time: Long, val rise: Boolean) : HourItem
}

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
    GlassCard(title = stringResource(R.string.hourly_forecast), icon = Icons.Outlined.Schedule, info = Term.HOURLY) {
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
                        value = Units.temp(if (item.isNow) data.current.temperature else item.point.temperature, settings.temperatureUnit),
                        bold = item.isNow,
                    )
                    is HourItem.Sun -> SunCell(tf.time(item.time), item.rise)
                }
            }
        }
    }
}

@Composable
private fun HourCell(label: String, condition: Condition, isDay: Boolean, precipProb: Double?, value: String, bold: Boolean) {
    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium, color = Color.White, maxLines = 1)
        Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(condition, isDay, size = 26.dp)
                // Shown regardless of the symbol: fog or clouds can still come with a 40 % rain risk.
                ChanceText(precipProb)
            }
        }
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun SunCell(time: String, rise: Boolean) {
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) { SunHorizonGlyph(rise) }
        Text(stringResource(if (rise) R.string.sunrise else R.string.sunset), fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ---------------------------------------------------------------------------------------
// Daily

@Composable
fun DailyCard(data: WeatherData, now: Long) {
    val settings = LocalSettings.current
    val tf = LocalTimeFormat.current
    val days = remember(data, now) {
        val todayIdx = data.daily.indexOfLast { it.date <= now }.coerceAtLeast(0)
        data.daily.drop(todayIdx).take(10)
    }
    if (days.isEmpty()) return
    val lo = days.minOf { it.tempMin }
    val hi = days.maxOf { it.tempMax }
    var expanded by rememberSaveable(data.place.id) { mutableStateOf<Long?>(null) }
    GlassCard(
        title = stringResource(if (days.size == 10) R.string.ten_day_forecast else R.string.n_day_forecast, days.size),
        icon = Icons.Outlined.CalendarMonth,
        info = Term.DAILY,
    ) {
        days.forEachIndexed { i, d ->
            if (i > 0) HairlineDivider()
            val isToday = i == 0
            DayRow(
                day = d,
                label = if (isToday) stringResource(R.string.today) else tf.weekdayShort(d.date),
                min = lo, max = hi,
                currentTemp = if (isToday) data.current.temperature else null,
                expanded = expanded == d.date,
                onClick = { expanded = if (expanded == d.date) null else d.date },
                // 00:00 of the next day closes the curve at 24 h
                hours = data.hourly.filter { it.time in d.date..d.date + 24 * 3_600_000L },
                daily = data.daily,
                now = now,
            )
        }
    }
}

@Composable
private fun DayRow(
    day: DailyPoint, label: String, min: Double, max: Double, currentTemp: Double?,
    expanded: Boolean, onClick: () -> Unit, hours: List<HourlyPoint>, daily: List<DailyPoint>, now: Long,
) {
    val settings = LocalSettings.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(62.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(day.condition, true, size = 26.dp)
                ChanceText(day.precipitationProbability)
            }
            Text(
                Units.temp(day.tempMin, settings.temperatureUnit), Modifier.width(44.dp), fontSize = 18.sp,
                color = NimbusColors.Tertiary, textAlign = TextAlign.End, fontWeight = FontWeight.Medium,
            )
            TemperatureRangeBar(day.tempMin, day.tempMax, min, max, currentTemp, Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(
                Units.temp(day.tempMax, settings.temperatureUnit), Modifier.width(40.dp), fontSize = 18.sp,
                color = Color.White, fontWeight = FontWeight.Medium,
            )
        }
        // No clipping animation (like animateContentSize): the meteogram extends into the card padding.
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded && hours.size >= 2,
            enter = androidx.compose.animation.expandVertically(clip = false) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(clip = false) + androidx.compose.animation.fadeOut(),
        ) {
            val end = day.date + 24 * 3_600_000L
            Meteogram(
                hours.map { it.toMeteo() }, day.date, end, nightsFromDaily(daily, day.date, end), now,
                Modifier.fillMaxWidth().bleed(CARD_BLEED).padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
fun TemperatureRangeBar(low: Double, high: Double, min: Double, max: Double, current: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier.height(6.dp)) {
        val span = (max - min).coerceAtLeast(1.0)
        val h = size.height
        drawRoundRect(Color(0x33000000), size = size, cornerRadius = CornerRadius(h / 2))
        val x0 = ((low - min) / span * size.width).toFloat()
        val x1 = ((high - min) / span * size.width).toFloat().coerceAtLeast(x0 + h)
        val brush = Brush.horizontalGradient(
            listOf(Insights.temperatureColor(low), Insights.temperatureColor((low + high) / 2), Insights.temperatureColor(high)),
            startX = x0, endX = x1,
        )
        drawRoundRect(brush, topLeft = Offset(x0, 0f), size = Size(x1 - x0, h), cornerRadius = CornerRadius(h / 2))
        if (current != null) {
            val cx = ((current.coerceIn(low, high) - min) / span * size.width).toFloat().coerceIn(x0 + h / 2, x1 - h / 2)
            drawCircle(Color(0xFF1A2A40), h * 0.95f, Offset(cx, h / 2))
            drawCircle(Color.White, h * 0.62f, Offset(cx, h / 2))
        }
    }
}


// ---------------------------------------------------------------------------------------
// Nowcast (next 3 hours, 15-minute resolution)

@Composable
fun NowcastCard(points: List<MinutelyPoint>, now: Long, rainingNow: Boolean = false) {
    val tf = LocalTimeFormat.current
    val summary = when (val n = Insights.nowcast(points, now, rainingNow)) {
        Insights.Nowcast.Dry -> stringResource(R.string.summary_no_rain)
        Insights.Nowcast.Continues -> stringResource(R.string.summary_rain_continues)
        is Insights.Nowcast.StartsIn -> stringResource(R.string.summary_rain_starting, n.minutes)
        is Insights.Nowcast.StopsIn -> stringResource(R.string.summary_rain_now, n.minutes)
    }
    GlassCard(title = stringResource(R.string.next_hours_precip), icon = Icons.Outlined.Umbrella, info = Term.NOWCAST) {
        Text(summary, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val n = points.size.coerceAtLeast(1)
            val bw = size.width / n
            val maxP = maxOf(1.0, points.maxOfOrNull { it.precipitation } ?: 0.0)
            for (k in 1..3) {
                val y = size.height * k / 4
                drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            }
            points.forEachIndexed { i, p ->
                val bh = (p.precipitation / maxP * size.height).toFloat().coerceAtLeast(if (p.precipitation > 0) 3f else 0f)
                if (bh > 0) drawRoundRect(
                    PrecipBlue, Offset(i * bw + bw * 0.12f, size.height - bh), Size(bw * 0.76f, bh),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.now), fontSize = 11.sp, color = NimbusColors.Tertiary)
            points.getOrNull(points.size / 2)?.let { Text(tf.time(it.time), fontSize = 11.sp, color = NimbusColors.Tertiary) }
            points.lastOrNull()?.let { Text(tf.time(it.time), fontSize = 11.sp, color = NimbusColors.Tertiary) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Alerts

fun severityColor(s: AlertSeverity): Color = when (s) {
    AlertSeverity.MINOR -> Color(0xFFFFE14D)
    AlertSeverity.MODERATE -> Color(0xFFFF9F1C)
    AlertSeverity.SEVERE -> Color(0xFFFF3B30)
    AlertSeverity.EXTREME -> Color(0xFFB0189A)
}

@Composable
fun AlertsCard(alerts: List<WeatherAlert>) {
    val tf = LocalTimeFormat.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassCard(
        title = stringResource(R.string.alerts) + " · DWD",
        icon = Icons.Rounded.WarningAmber,
        tint = Color(0x4D3A1010),
        onClick = { expanded = !expanded },
        info = Term.ALERTS,
    ) {
        val shown = if (expanded) alerts else alerts.take(2)
        shown.forEachIndexed { i, a ->
            if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(severityColor(a.severity)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.animateContentSize()) {
                    Text(a.headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    val period = when {
                        a.onset != null && a.expires != null -> stringResource(
                            R.string.alert_valid,
                            tf.weekdayShort(a.onset) + " " + tf.time(a.onset),
                            tf.weekdayShort(a.expires) + " " + tf.time(a.expires),
                        )
                        a.expires != null -> stringResource(R.string.alert_until, tf.weekdayShort(a.expires) + " " + tf.time(a.expires))
                        else -> null
                    }
                    if (period != null) Text(period, fontSize = 13.sp, color = NimbusColors.Secondary)
                    if (expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(a.description, fontSize = 14.sp, color = Color.White)
                        a.instruction?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontSize = 13.sp, color = NimbusColors.Secondary)
                        }
                    }
                }
            }
        }
        if (!expanded && alerts.size > 2) {
            Text(stringResource(R.string.more_alerts, alerts.size - 2), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Text(
            stringResource(if (expanded) R.string.show_less else R.string.show_more),
            Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD27A),
        )
    }
}
