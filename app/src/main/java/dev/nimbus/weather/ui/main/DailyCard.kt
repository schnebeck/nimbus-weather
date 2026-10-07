/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DailyCard.kt
 * The 10-day forecast: a row per day, unfolding to its day chart.
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units

@Composable
fun DailyCard(data: WeatherData, now: Long, measured: TodayMeasured? = null) {
    val settings = LocalSettings.current
    val tf = LocalTimeFormat.current
    val days = remember(data, now) {
        val todayIdx = data.daily.indexOfLast { it.date <= now }.coerceAtLeast(0)
        data.daily.drop(todayIdx).take(10)
    }
    if (days.isEmpty()) return
    val lo = days.minOf { it.tempMin }
    val hi = days.maxOf { it.tempMax }
    // Today opens by default; every row (today too) can still be closed.
    var expanded by rememberSaveable(data.place.id) { mutableStateOf<Long?>(days.first().date) }
    GlassCard(
        title = stringResource(if (days.size == 10) R.string.ten_day_forecast else R.string.n_day_forecast, days.size),
        icon = Icons.Outlined.CalendarMonth,
        info = Term.DAILY,
    ) {
        // One set of column widths for all rows (the bars stay aligned): wide enough for the longest
    // day name, chance and temperature in the font size set – fixed widths cut "Heute" to "Heu"
    // and "20°" to "20" with a large system font
    val labels = days.mapIndexed { i, d -> if (i == 0) stringResource(R.string.today) else tf.weekdayShort(d.date) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val natural = remember(labels, days, settings.temperatureUnit, density) {
        fun widest(texts: List<String>, style: androidx.compose.ui.text.TextStyle) =
            with(density) { (texts.maxOfOrNull { measurer.measure(it, style, softWrap = false, density = density).size.width } ?: 0).toDp() }
        val big = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
        val chances = days.mapNotNull { d -> Insights.chanceLabel(d.precipitationProbability)?.let { Insights.chanceText(d.precipitationProbability, d.precipitationSum) + NBSP + "%" } }
        val temps = days.flatMap { listOf(Units.temp(it.tempMin, settings.temperatureUnit), Units.temp(it.tempMax, settings.temperatureUnit)) }
        DayColumns(
            label = maxOf(62.dp, widest(labels, big) + 6.dp),
            symbol = maxOf(44.dp, widest(chances, ChanceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)) + 4.dp),
            temp = maxOf(44.dp, widest(temps, big) + 4.dp),
        )
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    // one line while the bar keeps a useful width (its 10 dp padding on each side included)
    val columns = natural.copy(stacked = maxWidth - (natural.label + natural.symbol + natural.temp * 2 - 4.dp + 20.dp) < MIN_BAR)
    Column {
    days.forEachIndexed { i, d ->
            if (i > 0) HairlineDivider()
            val isToday = i == 0
            DayRow(
                day = d,
                label = labels[i],
                columns = columns,
                min = lo, max = hi,
                currentTemp = if (isToday) data.current.temperature else null,
                expanded = expanded == d.date,
                onClick = { expanded = if (expanded == d.date) null else d.date },
                // 00:00 of the next day closes the curve at 24 h
                // 00:00 of the day through 01:00 of the next: the 24 column
                hours = data.hourly.filter { it.time in d.date..d.date + 25 * 3_600_000L },
                minutely = data.minutely,
                place = data.place,
                daily = data.daily,
                now = now,
                measured = if (isToday) measured else null,
                nowCondition = if (isToday) data.current.condition else null,
            )
        }
    }
    }
    }
}

@Composable
private fun DayRow(
    day: DailyPoint, label: String, columns: DayColumns, min: Double, max: Double, currentTemp: Double?,
    expanded: Boolean, onClick: () -> Unit, hours: List<HourlyPoint>, daily: List<DailyPoint>, now: Long,
    /** The place (night shading from the sun's position there). */
    place: dev.nimbus.weather.data.model.Place,
    /** 15-minute steps of the forecast (temperature curve where the model has them). */
    minutely: List<dev.nimbus.weather.data.model.MinutelyPoint> = emptyList(),
    /** Today: station readings for the hours already over (shown instead of the forecast). */
    measured: TodayMeasured? = null,
    /** Today: the weather now (the header's) – the hour running now shows it. */
    nowCondition: dev.nimbus.weather.data.model.Condition? = null,
) {
    val settings = LocalSettings.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (!columns.stacked) Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(columns.label), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
            Column(Modifier.width(columns.symbol), horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(day.condition, true, size = 26.dp)
                ChanceText(day.precipitationProbability, day.precipitationSum)
            }
            Text(
                Units.temp(day.tempMin, settings.temperatureUnit), Modifier.width(columns.temp), fontSize = 18.sp, softWrap = false,
                color = NimbusColors.Tertiary, textAlign = TextAlign.End, fontWeight = FontWeight.Medium,
            )
            TemperatureRangeBar(day.tempMin, day.tempMax, min, max, currentTemp, Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(
                Units.temp(day.tempMax, settings.temperatureUnit), Modifier.width(columns.temp - 4.dp), fontSize = 18.sp, softWrap = false,
                color = Color.White, fontWeight = FontWeight.Medium,
            )
        } else Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            // Large text: day, symbol and temperatures in full size on one line, the bar below
            // across the whole width – nothing squeezed, nothing shortened
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
                Column(Modifier.width(columns.symbol), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherIcon(day.condition, true, size = 26.dp)
                    ChanceText(day.precipitationProbability, day.precipitationSum)
                }
                Text(
                    Units.temp(day.tempMin, settings.temperatureUnit), Modifier.width(columns.temp), fontSize = 18.sp, softWrap = false,
                    color = NimbusColors.Tertiary, textAlign = TextAlign.End, fontWeight = FontWeight.Medium,
                )
                Text(
                    Units.temp(day.tempMax, settings.temperatureUnit), Modifier.width(columns.temp), fontSize = 18.sp, softWrap = false,
                    color = Color.White, textAlign = TextAlign.End, fontWeight = FontWeight.Medium,
                )
            }
            TemperatureRangeBar(day.tempMin, day.tempMax, min, max, currentTemp, Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp))
        }
        // Unfolds downwards from its row and is clipped while it does (unclipped and growing from
        // the bottom it was drawn over the rows above). The whole animation reaches into the
        // card's padding (bleed), so the clip leaves the meteogram's axis labels there.
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded && hours.size >= 2,
            modifier = Modifier.fillMaxWidth().bleed(CARD_BLEED),
            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
        ) {
            val end = day.date + 24 * 3_600_000L
            Meteogram(
                hours.map { h -> h.toMeteo().let { measured?.apply(it, now) ?: it }.asNow(now, nowCondition) }, day.date, end,
                remember(day.date, place) { nights(day.date, HourAxis.dayAxisEnd(end), place.latitude, place.longitude) }, now,
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                showNow = currentTemp != null,   // today only
                curve = remember(hours, minutely, measured) { dayCurve(hours, minutely, day.date, HourAxis.dayAxisEnd(end), measured) },
                separatePrecip = settings.separatePrecipitation,
                measuredBy = measured?.let { measuredBy(it.hours.values, it.station) },
                dayOverview = if (currentTemp == null) remember(day, hours) { DayOverview.of(day, hours) } else null,
            )
        }
    }
}

/**
 * Widths of the day rows' columns: day name, symbol with chance, temperatures (the bar takes the
 * rest). [stacked]: the bar would get less than [MIN_BAR] – the row puts it on a line of its own.
 */
private data class DayColumns(
    val label: androidx.compose.ui.unit.Dp, val symbol: androidx.compose.ui.unit.Dp, val temp: androidx.compose.ui.unit.Dp,
    val stacked: Boolean = false,
)

/** The temperature bar's least width in a one-line day row. */
private val MIN_BAR = 56.dp

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
