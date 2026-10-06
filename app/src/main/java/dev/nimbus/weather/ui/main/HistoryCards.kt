/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HistoryCards.kt
 * The look-back's cards: the day's summary, its course measured against forecast, its radar.
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

import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units

@Composable
internal fun SummaryCard(sum: DaySummary, history: History, settings: Settings, tf: TimeFormat) {
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
        // the model the look-back came from (outside its area a regional one gives way to the best match)
        val model = modelName(ForecastModel.entries.firstOrNull { it.openMeteoId == history.modelId } ?: settings.model)
        Text(
            if (history.stationName != null && sum.measured) stringResource(
                R.string.history_source_station, history.stationName,
                Units.oneDecimal(history.stationDistanceKm ?: 0.0), model,
            ) else if (history.stationName != null) stringResource(R.string.history_source_pending, history.stationName, model)
            else stringResource(R.string.history_source_model, model),
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
internal fun DayCourseCard(day: HistoryDay, sum: DaySummary, settings: Settings, tf: TimeFormat, history: dev.nimbus.weather.data.remote.History, place: Place) {
    val start = day.date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val points = remember(day, history) { history.lookBackPoints(day, start) }
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
            // "today so far": the line at the time now (the other days do not hold it)
            showNow = true,
            // precipitation in the same card: in the temperature chart or as a chart of its own
            separatePrecip = settings.separatePrecipitation,
            measuredBy = measuredBy(day.hours.mapNotNull { it.measured }, history.stationName),
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
internal fun RadarDayCard(dayStart: Long, tf: TimeFormat, onOpen: () -> Unit) {
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
