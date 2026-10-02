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

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import dev.nimbus.weather.data.repo.WeatherRepository
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import dev.nimbus.weather.ui.components.drawHeaderShade
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

/** Legend swatch of a temperature curve: the colours of the temperature scale, cool to warm. */
private val TempSwatch = Brush.horizontalGradient(listOf(Insights.temperatureColor(5.0), Insights.temperatureColor(15.0), Insights.temperatureColor(25.0)))
private val ModelColor = Color(0xD9FFFFFF)
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
fun HistoryPage(
    place: Place, state: PlaceState?, settings: Settings, dayIndex: Int, isActive: Boolean, onRetry: () -> Unit,
    onOpenRadarDay: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val history = state?.history
    val day = history?.days?.getOrNull(dayIndex)
    val summary = remember(day) { day?.takeIf { it.hours.isNotEmpty() }?.let { DaySummary.of(it, history?.fetchedAt ?: Long.MAX_VALUE) } }
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
            dev.nimbus.weather.ui.components.LocalCardFill provides scene.cardFill,
            dev.nimbus.weather.ui.components.LocalHeaderShade provides scene.headerShade,
        ) {
            val clipTop = with(androidx.compose.ui.platform.LocalDensity.current) { (statusTop + 52.dp).toPx() }
            // Shade behind the top bar and the day's header text (see WeatherPage)
            val headerShade = dev.nimbus.weather.ui.components.LocalHeaderShade.current
            val shadeBottom = with(androidx.compose.ui.platform.LocalDensity.current) { (statusTop + HeaderTop + 96.dp).toPx() }
            val shadeFade = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            Box(Modifier.fillMaxSize().drawBehind { drawHeaderShade(headerShade, shadeBottom, shadeFade) })
            // Cards keep their title at the line below the top bar and slide away under it (GlassCard)
            val listTop = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            CompositionLocalProvider(dev.nimbus.weather.ui.components.LocalPinLine provides { listTop.floatValue + clipTop }) {
            LazyColumn(
                // Content scrolls away below the top bar instead of running under menu and radar button.
                Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 760.dp)
                    .onGloballyPositioned { listTop.floatValue = it.positionInRoot().y }
                    .drawWithContent { clipRect(top = clipTop) { this@drawWithContent.drawContent() } },
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
                        if (day.hours.size >= 2) item(key = "course") { DayCourseCard(day, summary, settings, tf, history) }
                        if (day.hours.count { it.model != null || it.measured?.precipitation != null } >= 2) item(key = "precip") { PrecipDayCard(day, tf) }
                        // The DWD keeps about 3½ days of radar: the whole day, in 5-minute steps (Germany)
                        if (WeatherRepository.isInDwdArea(place.latitude, place.longitude)) item(key = "radar") {
                            val start = day.date.atStartOfDay(history.zone).toInstant().toEpochMilli()
                            RadarDayCard(start, tf) { onOpenRadarDay(start) }
                        }
                    }
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
            Canvas(Modifier.size(16.dp, 8.dp)) { drawLine(TempSwatch, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.history_legend_model, modelName(settings.model)), fontSize = 11.sp, color = NimbusColors.Secondary)
        }
        return
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp, 8.dp)) {
            if (measuredAsBars) drawRoundRect(PrecipColor, Offset(size.width * 0.25f, 0f), Size(size.width * 0.5f, size.height), CornerRadius(1.dp.toPx()))
            else drawLine(TempSwatch, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
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

/** The day as meteogram: measurement (temperature colours) against forecast (white, dashed), plus precipitation and wind. */
@Composable
private fun DayCourseCard(day: HistoryDay, sum: DaySummary, settings: Settings, tf: TimeFormat, history: dev.nimbus.weather.data.remote.History) {
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val points = remember(day) {
        // With precipitation readings the bars show what fell; the forecast stays pale behind them
        val measuredRain = day.hours.any { it.measured?.precipitation != null }
        day.hours.mapNotNull { h ->
            val m = h.measured
            val f = h.model
            // No reading (yet): the forecast only, dashed – never the forecast drawn as measured
            if (m?.temperature == null) {
                val ft = f?.temperature ?: return@mapNotNull null
                return@mapNotNull MeteoPoint(
                    time = h.time, temperature = ft, condition = f.condition, isDay = f.isDay,
                    precipitation = if (measuredRain) null else f.precipitation,
                    forecastPrecipitation = if (measuredRain) f.precipitation else null, windSpeed = f.windSpeed, windDirection = f.windDirection, windGust = f.windGust,
                    forecastTemperature = ft, sunshine = f.sunshineMinutes, forecastOnly = true,
                    compare = HourCompare(
                        null, ft, null, f.precipitation, f.chance, null, null, f.windSpeed, null, f.windGust, null, f.sunshineMinutes,
                    ),
                )
            }
            val temp = m?.temperature ?: f?.temperature ?: return@mapNotNull null
            MeteoPoint(
                time = h.time,
                temperature = temp,
                condition = m?.condition ?: f?.condition ?: Condition.CLOUDY,
                isDay = f?.isDay ?: true,
                precipitation = if (measuredRain) m?.precipitation else f?.precipitation,
                windSpeed = m?.windSpeed ?: f?.windSpeed,
                windDirection = m?.windDirection ?: f?.windDirection,
                windGust = m?.windGust ?: f?.windGust,
                forecastTemperature = f?.temperature,
                forecastPrecipitation = if (measuredRain) f?.precipitation else null,
                sunshine = m?.sunshineMinutes ?: f?.sunshineMinutes,
                // the readout table shows both apart, an empty cell where one is missing
                compare = HourCompare(
                    m?.temperature, f?.temperature, m?.precipitation, f?.precipitation, f?.chance,
                    m?.windSpeed, m?.windDirection, f?.windSpeed, m?.windGust, f?.windGust, m?.sunshineMinutes, f?.sunshineMinutes,
                ),
            )
        }
    }
    val hasMeasured = day.hours.any { it.measured?.temperature != null }
    // The curves in the finest resolution there is: station reports every 10 minutes (SYNOP, about
    // the last 1½ days), the model every 15 minutes; hourly values where there are no finer ones
    val curves = remember(day, history) {
        val from = start - 3_600_000L; val to = start + 24 * 3_600_000L
        fun fine(m: Map<Long, Double>, step: Long) = m.filterKeys { it in from..to }.map { (t, v) -> CurvePoint(t, v, step) }
        val measured = Curve.merge(
            fine(history.fineMeasured, 10 * 60_000L),
            day.hours.mapNotNull { h -> h.measured?.temperature?.let { CurvePoint(h.time, it) } },
        )
        val model = Curve.merge(
            fine(history.fineModel, 15 * 60_000L),
            day.hours.mapNotNull { h -> h.model?.temperature?.let { CurvePoint(h.time, it) } },
        )
        measured to model
    }
    GlassCard(title = stringResource(R.string.history_course), icon = Icons.Outlined.Thermostat) {
        Meteogram(
            points, start, start + 24 * 3_600_000L, nightsFromFlags(points), System.currentTimeMillis(),
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
            curve = curves.first, forecastCurve = curves.second,
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

/**
 * The day's precipitation: measured amount (dark blue) under the forecast amount (translucent
 * light blue), and the forecast chance as a line – hour by hour, with a cursor.
 */
@Composable
private fun PrecipDayCard(day: HistoryDay, tf: TimeFormat) {
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val asOf = remember(day) { System.currentTimeMillis() }
    // Each value covers the hour before its time: 01:00 … 24:00 make the day
    val hours = remember(day) {
        day.hours.filter { it.time > start && it.time <= start + 24 * 3_600_000L }
            .map { h -> PrecipHour(h.time, h.model?.precipitation, h.model?.chance, h.measured?.precipitation) }
    }
    if (hours.size < 2) return
    val nights = remember(day) {
        nightsFromFlags(day.hours.mapNotNull { h -> h.model?.let { MeteoPoint(h.time, 0.0, it.condition, it.isDay, null) } })
    }
    dev.nimbus.weather.ui.components.GlassCard(title = stringResource(R.string.history_precip_title), icon = Icons.Outlined.WaterDrop) {
        PrecipChart(hours, nights, asOf, compare = true, Modifier.fillMaxWidth().bleed(CARD_BLEED), endOfDay = start + 24 * 3_600_000L)
    }
}

/** Entry to the radar of the shown day (look-back). */
@Composable
private fun RadarDayCard(dayStart: Long, tf: TimeFormat, onOpen: () -> Unit) {
    dev.nimbus.weather.ui.components.GlassCard(
        title = stringResource(R.string.history_radar_title), icon = Icons.Outlined.Map, onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.history_radar_text, tf.weekdayLong(dayStart)), fontSize = 15.sp, color = Color.White, lineHeight = 20.sp)
                Text(stringResource(R.string.history_radar_hint), fontSize = 12.sp, color = NimbusColors.Secondary, lineHeight = 16.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NimbusColors.Secondary)
        }
    }
}
