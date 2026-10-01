/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HistoryPage.kt
 * The look back: measured values of the past days compared with the forecast.
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

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.background.WeatherBackground
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units

private val MeasuredColor = Color.White
private val ModelColor = Color(0xFFFFC56B)
private val PrecipColor = Color(0xFF8FD3FF)

fun modelName(m: ForecastModel) = when (m) {
    ForecastModel.DWD_ICON -> "DWD ICON"
    ForecastModel.BEST_MATCH -> "Open-Meteo"
    ForecastModel.ECMWF -> "ECMWF IFS"
    ForecastModel.METEO_FRANCE -> "Météo-France"
}

/**
 * One past day: [dayIndex] 0 = two days ago … HISTORY_DAYS − 1 = today so far.
 * Measured DWD station values are compared with what the model had predicted for the same hours.
 */
@Composable
fun HistoryPage(place: Place, state: PlaceState?, settings: Settings, dayIndex: Int, isActive: Boolean, onRetry: () -> Unit) {
    val context = LocalContext.current
    val history = state?.history
    val day = history?.days?.getOrNull(dayIndex)
    val summary = remember(day) { day?.takeIf { it.hours.isNotEmpty() }?.let { DaySummary.of(it) } }
    val tf = remember(history?.zone) { TimeFormat(history?.zone?.id ?: (state?.data?.timezone ?: "UTC"), DateFormat.is24HourFormat(context)) }
    // Same sky as the main page at the current time (day/night, twilight, moon) – only the weather
    // is that of the shown day. A bright day sky at night made the glass cards pale.
    val scene = remember(summary, day?.date, state?.data) {
        val (season, autumn) = SkyScene.seasonOf(day?.date ?: java.time.LocalDate.now(), place.latitude < 0)
        val base = state?.data?.let { SkyScene.from(it) } ?: placeholderScene()
        base.copy(
            condition = summary?.condition ?: Condition.CLOUDY,
            wind = ((summary?.meanWind ?: 10.0) / 60.0).toFloat().coerceIn(0.08f, 1f),
            gustiness = 0f, pollen = 0f,
            season = season, autumnProgress = autumn, temperature = summary?.tempMax ?: 15.0,
        )
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val title = stringResource(
        when (HISTORY_DAYS - 1 - dayIndex) {
            0 -> R.string.history_today
            1 -> R.string.history_yesterday
            else -> R.string.history_day_before
        },
    )

    Box(Modifier.fillMaxSize()) {
        WeatherBackground(scene, animate = isActive && settings.animationsEnabled)
        CompositionLocalProvider(
            LocalSettings provides settings, LocalTimeFormat provides tf,
            dev.nimbus.weather.ui.components.LocalCardShade provides scene.cardShade,
        ) {
            val clipTop = with(androidx.compose.ui.platform.LocalDensity.current) { (statusTop + 52.dp).toPx() }
            LazyColumn(
                // Content scrolls away below the top bar instead of running under menu and radar button.
                Modifier.fillMaxSize().drawWithContent { clipRect(top = clipTop) { this@drawWithContent.drawContent() } },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + HeaderTop, bottom = navBottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { HistoryHeader(place, title, day, summary, tf) }
                when {
                    day == null && state?.historyError == true -> item(key = "error") { HistoryMessage(stringResource(R.string.history_error), onRetry) }
                    day == null -> item(key = "loading") {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.history_loading), color = NimbusColors.Secondary, fontSize = 14.sp)
                        }
                    }
                    summary == null -> item(key = "empty") { HistoryMessage(stringResource(R.string.history_empty), null) }
                    else -> {
                        item(key = "summary") { SummaryCard(summary, history, settings, tf) }
                        // Right after midnight there is only one hour – nothing to draw yet.
                        if (day.hours.size >= 2) item(key = "course") { DayCourseCard(day, summary, settings, tf) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(place: Place, title: String, day: HistoryDay?, summary: DaySummary?, tf: TimeFormat) {
    val s = LocalSettings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (place.isCurrentLocation) Icon(Icons.Rounded.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(place.name, fontSize = 26.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Light, color = Color.White, lineHeight = 46.sp)
        day?.let {
            val ms = it.date.atStartOfDay(tf.zone).toInstant().toEpochMilli() + 12 * 3600_000L
            Text(tf.weekdayLong(ms) + ", " + tf.dayMonth(ms).substringAfter('\u00A0'), fontSize = 16.sp, color = NimbusColors.Secondary)
        }
        if (summary != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(summary.condition, true, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                dev.nimbus.weather.ui.components.MaxMinStack(summary.tempMax, summary.tempMin, s.temperatureUnit, fontSize = 20.sp)
            }
            Text(stringResource(Texts.condition(summary.condition, true)), fontSize = 16.sp, color = NimbusColors.Secondary)
        }
    }
}

@Composable
private fun HistoryMessage(text: String, onRetry: (() -> Unit)?) {
    GlassCard {
        Text(text, fontSize = 15.sp, color = Color.White)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0x40FFFFFF), contentColor = Color.White)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun SummaryCard(sum: DaySummary, history: History, settings: Settings, tf: TimeFormat) {
    val t = { v: Double? -> Units.temp(v, settings.temperatureUnit) }
    val pUnit = stringResource(Texts.precipUnit(settings.precipitationUnit))
    val wUnit = stringResource(Texts.windUnit(settings.windUnit))
    val p = { v: Double? -> Units.precipitationNumber(v, settings.precipitationUnit) + NBSP + pUnit }
    val model = stringResource(R.string.history_model_value, "")
    GlassCard(title = stringResource(R.string.history_summary), icon = Icons.Outlined.History, info = Term.HISTORY) {
        SummaryRow(
            stringResource(R.string.history_row_temp), "${t(sum.tempMax)} / ${t(sum.tempMin)}",
            if (sum.measured && sum.modelTempMax != null) model + "${t(sum.modelTempMax)} / ${t(sum.modelTempMin)}" else null,
        )
        HairlineDivider(Modifier.padding(vertical = 6.dp))
        SummaryRow(
            stringResource(R.string.precipitation), p(sum.precipitation),
            if (sum.measured && sum.modelPrecipitation != null) model + p(sum.modelPrecipitation) else null,
        )
        sum.sunshineHours?.let {
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            SummaryRow(stringResource(R.string.history_row_sun), hoursMinutes(it * 60.0), null)
        }
        sum.maxGust?.let { g ->
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            SummaryRow(
                stringResource(R.string.history_row_gust), Units.windNumber(g, settings.windUnit) + NBSP + wUnit,
                sum.maxGustAt?.let { stringResource(R.string.history_at, tf.time(it)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (history.stationName != null && sum.measured) stringResource(
                R.string.history_source_station, history.stationName,
                Units.oneDecimal(history.stationDistanceKm ?: 0.0), modelName(settings.model),
            ) else if (history.stationName != null) stringResource(R.string.history_source_pending, history.stationName, modelName(settings.model))
            else stringResource(R.string.history_source_model, modelName(settings.model)),
            fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, secondary: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = NimbusColors.Secondary)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (secondary != null) Text(secondary, fontSize = 12.sp, color = NimbusColors.Tertiary)
        }
    }
}

@Composable
private fun Legend(model: Boolean, settings: Settings, measuredAsBars: Boolean = false) {
    if (!model) {
        // No station: the solid line / bars are forecast values.
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(16.dp, 8.dp)) { drawLine(MeasuredColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.history_legend_model, modelName(settings.model)), fontSize = 11.sp, color = NimbusColors.Secondary)
        }
        return
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp, 8.dp)) {
            if (measuredAsBars) drawRoundRect(PrecipColor, Offset(size.width * 0.25f, 0f), Size(size.width * 0.5f, size.height), CornerRadius(1.dp.toPx()))
            else drawLine(MeasuredColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
        }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.history_legend_measured), fontSize = 11.sp, color = NimbusColors.Secondary)
        if (model) {
            Spacer(Modifier.width(14.dp))
            Canvas(Modifier.size(16.dp, 8.dp)) {
                drawLine(ModelColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.history_legend_model, modelName(settings.model)), fontSize = 11.sp, color = NimbusColors.Secondary)
        }
    }
}

/** The day as meteogram: measurement (white) against forecast (dashed), plus precipitation and wind. */
@Composable
private fun DayCourseCard(day: HistoryDay, sum: DaySummary, settings: Settings, tf: TimeFormat) {
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val points = remember(day) {
        day.hours.mapNotNull { h ->
            val m = h.measured
            val f = h.model
            val temp = m?.temperature ?: f?.temperature ?: return@mapNotNull null
            MeteoPoint(
                time = h.time,
                temperature = temp,
                condition = m?.condition ?: f?.condition ?: Condition.CLOUDY,
                isDay = f?.isDay ?: true,
                precipitation = m?.precipitation ?: f?.precipitation,
                windSpeed = m?.windSpeed ?: f?.windSpeed,
                windDirection = m?.windDirection,
                windGust = m?.windGust ?: f?.windGust,
                forecastTemperature = if (m?.temperature != null) f?.temperature else null,
                forecastPrecipitation = if (m?.precipitation != null) f?.precipitation else null,
                sunshine = m?.sunshineMinutes ?: f?.sunshineMinutes,
            )
        }
    }
    val hasMeasured = day.hours.any { it.measured?.temperature != null }
    GlassCard(title = stringResource(R.string.history_course), icon = Icons.Outlined.Thermostat) {
        Meteogram(
            points, start, start + 24 * 3_600_000L, nightsFromFlags(points), System.currentTimeMillis(),
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
        )
        Legend(model = hasMeasured, settings = settings)
        sum.tempError?.let {
            Text(
                stringResource(
                    R.string.history_error_mean,
                    Units.oneDecimal(it) + (if (settings.temperatureUnit == dev.nimbus.weather.data.model.TemperatureUnit.CELSIUS) "${NBSP}K" else "${NBSP}°F"),
                ),
                fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
