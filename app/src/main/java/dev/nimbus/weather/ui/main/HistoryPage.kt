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
    val scene = remember(summary, day?.date) {
        val (season, autumn) = SkyScene.seasonOf(day?.date ?: java.time.LocalDate.now(), place.latitude < 0)
        SkyScene(
            condition = summary?.condition ?: Condition.CLOUDY, daylight = 1f, twilight = 0f,
            wind = ((summary?.meanWind ?: 10.0) / 60.0).toFloat().coerceIn(0.08f, 1f),
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
        CompositionLocalProvider(LocalSettings provides settings, LocalTimeFormat provides tf) {
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
                        item(key = "temp") { TemperatureChartCard(day, summary, settings) }
                        item(key = "precip") { PrecipitationChartCard(day, settings) }
                        item(key = "wind") { WindChartCard(day, settings) }
                        item(key = "hours") { HoursCard(day, settings) }
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
                Text(
                    stringResource(R.string.high_low, Units.temp(summary.tempMax, s.temperatureUnit), Units.temp(summary.tempMin, s.temperatureUnit)),
                    fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White,
                )
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
            SummaryRow(stringResource(R.string.history_row_sun), stringResource(R.string.history_hours_value, Units.oneDecimal(it)), null)
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
            ) else stringResource(R.string.history_source_model, modelName(settings.model)),
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

/** Line chart over the hours of the day with an x axis from 0 to 24 h. */
@Composable
private fun DayChart(
    day: HistoryDay, lines: List<Triple<List<Double?>, Color, Boolean>>, bars: List<Double?> = emptyList(),
    minRange: Double = 4.0, zeroBased: Boolean = false, label: (Double) -> String,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val xs = day.hours.map { ((it.time - start) / 3_600_000.0).toFloat() }
    val values = lines.flatMap { it.first.filterNotNull() } + bars.filterNotNull()
    if (values.isEmpty()) return
    var lo = if (zeroBased) 0.0 else values.min()
    var hi = values.max()
    if (hi - lo < minRange) { val mid = (hi + lo) / 2; lo = if (zeroBased) 0.0 else mid - minRange / 2; hi = lo + minRange }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val axisW = 30.dp.toPx()
        val axisH = 16.dp.toPx()
        val w = size.width - axisW
        val h = size.height - axisH
        fun x(hour: Float) = axisW + w * hour / 24f
        fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
        for (k in 0..2) {
            val v = lo + (hi - lo) * k / 2
            drawLine(Color(0x1FFFFFFF), Offset(axisW, y(v)), Offset(size.width, y(v)), 1f)
            val l = measurer.measure(label(v), style)
            drawText(l, topLeft = Offset(axisW - l.size.width - 4.dp.toPx(), (y(v) - l.size.height / 2f).coerceIn(0f, h - l.size.height)))
        }
        for (hr in 0..24 step 6) {
            drawLine(Color(0x1FFFFFFF), Offset(x(hr.toFloat()), 0f), Offset(x(hr.toFloat()), h), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val l = measurer.measure("%02d".format(hr % 24), style)
            drawText(l, topLeft = Offset((x(hr.toFloat()) - l.size.width / 2f).coerceIn(axisW, size.width - l.size.width), h + 2.dp.toPx()))
        }
        val hourW = w / 24f
        bars.forEachIndexed { i, v ->
            if (v != null && v > 0) {
                val top = y(v)
                // Sums refer to the hour before the timestamp: the bar spans that hour.
                drawRoundRect(PrecipColor, Offset(x(xs[i] - 1f) + hourW * 0.15f, top), Size(hourW * 0.7f, h - top), CornerRadius(2.dp.toPx()))
            }
        }
        lines.forEach { (vals, color, dashed) ->
            val path = Path()
            var started = false
            vals.forEachIndexed { i, v ->
                if (v == null) { started = false; return@forEachIndexed }
                if (!started) { path.moveTo(x(xs[i]), y(v)); started = true } else path.lineTo(x(xs[i]), y(v))
            }
            drawPath(
                path, color,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null),
            )
        }
    }
}

@Composable
private fun TemperatureChartCard(day: HistoryDay, sum: DaySummary, settings: Settings) {
    val measured = day.hours.map { it.measured?.temperature?.let { t -> Units.temperature(t, settings.temperatureUnit) } }
    val model = day.hours.map { it.model?.temperature?.let { t -> Units.temperature(t, settings.temperatureUnit) } }
    val hasMeasured = measured.any { it != null }
    GlassCard(title = stringResource(R.string.history_temp_chart), icon = Icons.Outlined.Thermostat) {
        DayChart(
            day,
            listOfNotNull(
                Triple(model, if (hasMeasured) ModelColor else MeasuredColor, hasMeasured),
                if (hasMeasured) Triple(measured, MeasuredColor, false) else null,
            ),
        ) { "${Math.round(it)}°" }
        Legend(model = hasMeasured, settings = settings)
        sum.tempError?.let {
            Text(
                stringResource(R.string.history_error_mean, Units.oneDecimal(it) + (if (settings.temperatureUnit == dev.nimbus.weather.data.model.TemperatureUnit.CELSIUS) "${NBSP}K" else "${NBSP}°F")),
                fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PrecipitationChartCard(day: HistoryDay, settings: Settings) {
    val measured = day.hours.map { it.measured?.precipitation?.let { p -> Units.precipitationValue(p, settings.precipitationUnit) } }
    val model = day.hours.map { it.model?.precipitation?.let { p -> Units.precipitationValue(p, settings.precipitationUnit) } }
    val hasMeasured = measured.any { it != null }
    val unit = stringResource(Texts.precipUnit(settings.precipitationUnit))
    GlassCard(title = stringResource(R.string.history_precip_chart), icon = Icons.Outlined.WaterDrop) {
        val any = (measured + model).any { (it ?: 0.0) >= 0.05 }
        if (!any) {
            Text(stringResource(if (hasMeasured) R.string.history_dry_day else R.string.history_dry_day_model), fontSize = 15.sp, color = Color.White)
            return@GlassCard
        }
        DayChart(
            day, if (hasMeasured) listOf(Triple(model, ModelColor, true)) else emptyList(),
            bars = if (hasMeasured) measured else model, minRange = 1.0, zeroBased = true,
        ) { Units.oneDecimal(it) + NBSP + unit }
        Legend(model = hasMeasured, settings = settings, measuredAsBars = true)
    }
}

@Composable
private fun WindChartCard(day: HistoryDay, settings: Settings) {
    val m = settings.windUnit
    val speed = day.hours.map { (it.measured?.windSpeed ?: it.model?.windSpeed)?.let { v -> Units.windValue(v, m) } }
    val gust = day.hours.map { (it.measured?.windGust ?: it.model?.windGust)?.let { v -> Units.windValue(v, m) } }
    val unit = stringResource(Texts.windUnit(m))
    GlassCard(title = stringResource(R.string.history_wind_chart), icon = Icons.Outlined.Air) {
        DayChart(day, listOf(Triple(gust, Color(0xFFFFB08A), false), Triple(speed, MeasuredColor, false)), minRange = 10.0, zeroBased = true) {
            "${Math.round(it)}"
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(16.dp, 8.dp)) { drawLine(MeasuredColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.history_legend_wind, unit), fontSize = 11.sp, color = NimbusColors.Secondary)
            Spacer(Modifier.width(14.dp))
            Canvas(Modifier.size(16.dp, 8.dp)) { drawLine(Color(0xFFFFB08A), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.gusts), fontSize = 11.sp, color = NimbusColors.Secondary)
        }
    }
}

@Composable
private fun HoursCard(day: HistoryDay, settings: Settings) {
    val tf = LocalTimeFormat.current
    GlassCard(title = stringResource(R.string.history_hours), icon = Icons.Outlined.Schedule) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(day.hours, key = { it.time }) { h ->
                val cond = h.measured?.condition ?: h.model?.condition ?: Condition.CLOUDY
                val temp = h.measured?.temperature ?: h.model?.temperature
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tf.hour(h.time), fontSize = 14.sp, color = Color.White)
                    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) { WeatherIcon(cond, h.model?.isDay ?: true, size = 26.dp) }
                    Text(Units.temp(temp, settings.temperatureUnit), fontSize = 17.sp, color = Color.White, textAlign = TextAlign.Center)
                    val p = h.measured?.precipitation ?: h.model?.precipitation
                    Text(
                        if (p != null && p >= 0.1) Units.precipitationNumber(p, settings.precipitationUnit) else " ",
                        fontSize = 11.sp, color = PrecipColor,
                    )
                }
            }
        }
    }
}
