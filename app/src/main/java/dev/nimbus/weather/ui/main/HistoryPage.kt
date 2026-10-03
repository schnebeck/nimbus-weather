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

import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import dev.nimbus.weather.data.repo.WeatherRepository
import android.text.format.DateFormat
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Thermostat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    // The day in parts (early … night), growing in the course of today
    val parts = remember(day, history?.fetchedAt) {
        if (day == null || history == null) emptyList() else dev.nimbus.weather.data.remote.DayParts.of(day, history.zone, history.fetchedAt)
    }
    // The sky shows the parts one after the other, every 5 s – the row of the table it shows is lit
    var shown by remember(parts) { androidx.compose.runtime.mutableIntStateOf(parts.lastIndex.coerceAtLeast(0)) }
    LaunchedEffect(parts, isActive) {
        if (!isActive || parts.size < 2) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(PART_SHOW_MS)
            shown = (shown + 1) % parts.size
        }
    }
    // Same sky as the main page at the current time (day/night, twilight, moon) – only the weather
    // is that of the shown day. A bright day sky at night made the glass cards pale.
    val sky = remember(summary, day?.date, state?.data) {
        val (season, autumn) = SkyScene.seasonOf(day?.date ?: java.time.LocalDate.now(), place.latitude < 0)
        val base = state?.data?.let { SkyScene.from(it) } ?: placeholderScene()
        base.copy(
            condition = summary?.condition ?: Condition.CLOUDY,
            wind = ((summary?.meanWind ?: 10.0) / 60.0).toFloat().coerceIn(0.08f, 1f),
            gustiness = 0f, pollen = 0f,
            season = season, autumnProgress = autumn, temperature = summary?.tempMax ?: 15.0,
        )
    }
    // each part in its weather and in the light of its time of day (the middle of the part)
    val skies = remember(sky, parts, day?.date) {
        parts.map { p ->
            val mid = day!!.date.atStartOfDay(history!!.zone).toInstant().toEpochMilli() + (p.part.from + p.part.to) * 1_800_000L
            SkyScene.atTime(sky.copy(condition = p.condition), mid, place.latitude, place.longitude)
        }.ifEmpty { listOf(sky) }
    }
    val scene = skies.getOrNull(shown) ?: skies.first()
    // Glass and header shade for the brightest sky of the round: they do not pulse every 5 s
    val cardFill = skies.maxBy { it.cardFill.alpha }.cardFill
    val headerStyle = dev.nimbus.weather.ui.components.HeaderStyle(skies.maxOf { it.headerHalo }, skies.maxBy { it.headerPill.alpha }.headerPill)
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
        // one weather into the next: clouds and rain fade over instead of switching
        androidx.compose.animation.Crossfade(scene, animationSpec = androidx.compose.animation.core.tween(1200), label = "sky") { sc ->
            WeatherBackground(sc, animate = isActive && settings.animationsEnabled)
        }
        CompositionLocalProvider(
            LocalSettings provides settings, LocalTimeFormat provides tf,
            dev.nimbus.weather.ui.components.LocalCardFill provides cardFill,
            dev.nimbus.weather.ui.components.LocalHeaderStyle provides headerStyle,
        ) {
            val clipTop = with(androidx.compose.ui.platform.LocalDensity.current) { (statusTop + 52.dp).toPx() }
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
                item(key = "header") { HistoryHeader(place, title, day, summary, tf, parts, shown) }
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
                        if (day.hours.size >= 2) item(key = "course") { DayCourseCard(day, summary, settings, tf, history, place) }
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
private fun HistoryHeader(
    place: Place, title: String, day: HistoryDay?, summary: DaySummary?, tf: TimeFormat,
    parts: List<dev.nimbus.weather.data.remote.DayPartWeather>, shown: Int,
) {
    val s = LocalSettings.current
    // On the open sky: white with a dark halo, as the weather page's header
    val halo = androidx.compose.ui.text.TextStyle(shadow = dev.nimbus.weather.ui.components.LocalHeaderStyle.current.shadow)
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (place.isCurrentLocation) Icon(Icons.Rounded.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(place.name, fontSize = 26.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = halo)
        }
        // a low window (a phone held sideways): a smaller title, the cards get the height
        val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 500
        Text(
            title, fontSize = if (compact) 30.sp else 40.sp, fontWeight = FontWeight.Light, color = Color.White,
            lineHeight = if (compact) 34.sp else 46.sp, style = halo,
        )
        day?.let {
            val ms = it.date.atStartOfDay(tf.zone).toInstant().toEpochMilli() + 12 * 3600_000L
            Text(tf.weekdayLong(ms) + ", " + tf.dayMonth(ms).substringAfter('\u00A0'), fontSize = 19.sp, color = Color.White, style = halo)
        }
        if (summary != null) {
            Spacer(Modifier.height(8.dp))
            dev.nimbus.weather.ui.components.MaxMinStack(
                summary.tempMax, summary.tempMin, s.temperatureUnit, fontSize = 20.sp,
                shadow = halo.shadow, labelColor = Color.White,
            )
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DayPartsTable(parts, shown, halo)
        }
    }
}

/** How long the sky shows each part of the day. */
private const val PART_SHOW_MS = 5_000L

/**
 * The day in parts as a table of three columns and two rows – early, morning, forenoon above,
 * afternoon, evening, night below –, each with its name, symbol and weather, for the parts that
 * have begun; the part the sky shows now is lit, the others dimmed. One font size for all cells,
 * small enough (on narrow phones, with a large system font) that the longest single word
 * ("Überwiegend", "Nachmittags") fits its column: lines break between words only, never inside one.
 */
@Composable
internal fun DayPartsTable(parts: List<dev.nimbus.weather.data.remote.DayPartWeather>, shown: Int, halo: androidx.compose.ui.text.TextStyle) {
    val labels = parts.map { stringResource(dayPartLabel(it.part)) }
    val weathers = parts.map { stringResource(Texts.condition(it.condition, it.isDay)) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = minOf(maxWidth / PART_COLUMNS, 120.dp) - 4.dp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val columnPx = with(density) { column.toPx() }
        // the largest size (up to [max]) at which the widest of [words] fits the column: scaled,
        // then measured again and stepped down – small sizes do not scale exactly (glyphs snap to pixels)
        fun fit(words: List<String>, max: androidx.compose.ui.unit.TextUnit, weight: FontWeight): androidx.compose.ui.unit.TextUnit {
            // with the density of now (the measurer keeps the one it was made with)
            fun widest(size: Float) = words.maxOfOrNull { w ->
                measurer.measure(w, halo.copy(fontSize = size.sp, fontWeight = weight), softWrap = false, density = density).size.width
            } ?: 0
            var size = max.value
            val first = widest(size)
            if (first <= columnPx) return max
            size *= columnPx / first
            while (size > 6f && widest(size) > columnPx) size -= 0.25f
            return size.sp
        }
        // keyed by the density too: a larger system font needs a smaller size
        val labelSize = remember(labels, columnPx, density) { fit(labels, 13.sp, FontWeight.Normal) }
        val weatherSize = remember(weathers, columnPx, density) { fit(weathers.flatMap { it.split(' ') }, 13.sp, FontWeight.Medium) }
        // a fixed grid: a row not full yet (the afternoon alone at 13:00) starts in the first column
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            parts.indices.chunked(PART_COLUMNS).forEach { row ->
                Row(Modifier.width((column + 4.dp) * PART_COLUMNS)) {
                    row.forEach { i -> DayPartCell(parts[i], labels[i], weathers[i], i == shown, column, labelSize, weatherSize, halo) }
                }
            }
        }
    }
}

/** One part of the day: name, symbol, weather (always two lines, so the rows keep their height). */
@Composable
private fun DayPartCell(
    p: dev.nimbus.weather.data.remote.DayPartWeather, label: String, weather: String, lit: Boolean,
    column: androidx.compose.ui.unit.Dp, labelSize: androidx.compose.ui.unit.TextUnit, weatherSize: androidx.compose.ui.unit.TextUnit,
    halo: androidx.compose.ui.text.TextStyle,
) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (lit) 1f else 0.55f, androidx.compose.animation.core.tween(600), label = "lit")
    Column(Modifier.width(column + 4.dp).alpha(alpha).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = labelSize, color = Color.White, style = halo, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        WeatherIcon(p.condition, p.isDay, size = 34.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            weather, fontSize = weatherSize, lineHeight = weatherSize * 1.2f, color = Color.White, style = halo,
            fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2, maxLines = 2,
        )
    }
}

/** Columns of the day-parts table: two rows of three. */
private const val PART_COLUMNS = 3

private fun dayPartLabel(p: dev.nimbus.weather.data.remote.DayPart) = when (p) {
    dev.nimbus.weather.data.remote.DayPart.EARLY -> R.string.day_part_early
    dev.nimbus.weather.data.remote.DayPart.MORNING -> R.string.day_part_morning
    dev.nimbus.weather.data.remote.DayPart.FORENOON -> R.string.day_part_forenoon
    dev.nimbus.weather.data.remote.DayPart.AFTERNOON -> R.string.day_part_afternoon
    dev.nimbus.weather.data.remote.DayPart.EVENING -> R.string.day_part_evening
    dev.nimbus.weather.data.remote.DayPart.NIGHT -> R.string.day_part_night
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
            stringResource(R.string.history_row_temp) to stringResource(R.string.history_row_temp_short), "${t(sum.tempMax)} / ${t(sum.tempMin)}",
            if (sum.measured && sum.modelTempMax != null) model + "${t(sum.modelTempMax)} / ${t(sum.modelTempMin)}" else null,
        )
        HairlineDivider(Modifier.padding(vertical = 6.dp))
        SummaryRow(
            stringResource(R.string.precipitation) to stringResource(R.string.precipitation_short), p(sum.precipitation),
            if (sum.measured && sum.modelPrecipitation != null) model + p(sum.modelPrecipitation) else null,
        )
        sum.sunshineHours?.let {
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            SummaryRow(stringResource(R.string.history_row_sun) to stringResource(R.string.history_row_sun_short), hoursMinutes(it * 60.0), null)
        }
        sum.maxGust?.let { g ->
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            SummaryRow(
                stringResource(R.string.history_row_gust) to stringResource(R.string.history_row_gust_short), Units.windNumber(g, settings.windUnit) + NBSP + wUnit,
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
private fun SummaryRow(label: Pair<String, String>, value: String, secondary: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // the label in full, or abbreviated where it does not fit beside the value ("Niederschl.")
        dev.nimbus.weather.ui.components.FitText(
            label.first, label.second, Modifier.weight(1f).padding(end = 8.dp),
            androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = NimbusColors.Secondary),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (secondary != null) Text(secondary, fontSize = 12.sp, color = NimbusColors.Tertiary)
        }
    }
}

/** The day as meteogram: measurement (temperature colours) against forecast (white, dashed), plus precipitation and wind. */
@Composable
private fun DayCourseCard(day: HistoryDay, sum: DaySummary, settings: Settings, tf: TimeFormat, history: dev.nimbus.weather.data.remote.History, place: Place) {
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val points = remember(day, history) {
        // With precipitation readings the bars show what fell; the forecast beside them
        val measuredRain = day.hours.any { it.measured?.precipitation != null }
        // 00:00 … 24:00 and the 24 column (into tomorrow's first hour)
        history.chartHours(start).mapNotNull { h ->
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
    // The curves in the finest resolution there is: station reports every 10 minutes (SYNOP, about
    // the last 1½ days), the model every 15 minutes; hourly values where there are no finer ones
    val curves = remember(day, history) {
        val from = start - 3_600_000L; val to = start + 25 * 3_600_000L
        fun fine(m: Map<Long, Double>, step: Long) = m.filterKeys { it in from..to }.map { (t, v) -> CurvePoint(t, v, step) }
        val measured = Curve.merge(
            fine(history.fineMeasured, 10 * 60_000L),
            history.chartHours(start).mapNotNull { h -> h.measured?.temperature?.let { CurvePoint(h.time, it) } },
        )
        val model = Curve.merge(
            fine(history.fineModel, 15 * 60_000L),
            history.chartHours(start).mapNotNull { h -> h.model?.temperature?.let { CurvePoint(h.time, it) } },
        )
        measured to model
    }
    GlassCard(title = stringResource(R.string.history_course), icon = Icons.Outlined.Thermostat) {
        Meteogram(
            points, start, start + 24 * 3_600_000L,
            remember(start) { nights(start, HourAxis.dayAxisEnd(start + 24 * 3_600_000L), place.latitude, place.longitude) }, System.currentTimeMillis(),
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
            curve = curves.first, forecastCurve = curves.second,
            // precipitation in the same card: in the temperature chart or as a chart of its own
            separatePrecip = settings.separatePrecipitation,
            // the result first, then what the lines and bars mean; the press hint ends the card
            summary = sum.tempError?.let { err ->
                {
                    Text(
                        stringResource(
                            R.string.history_error_mean,
                            Units.oneDecimal(err) + (if (settings.temperatureUnit == dev.nimbus.weather.data.model.TemperatureUnit.CELSIUS) "${NBSP}K" else "${NBSP}°F"),
                        ),
                        fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
        )
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
