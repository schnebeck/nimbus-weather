/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PrecipitationCard.kt
 * Today's precipitation: amount so far and to come, readings, chance – or one line on a dry day;
 * the notice of rain now or soon.
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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.foundation.layout.size
import dev.nimbus.weather.ui.components.HairlineDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Today's precipitation: the amount so far and until midnight, the station's readings, the
 * highest chance, the nowcast notice – and whether the day stays [dry] (then the card shrinks to
 * one line with the next precipitation in the forecast, [nextWet], or is hidden – setting).
 */
class PrecipToday(
    val todaySum: Double, val next: Double, val readings: Map<Long, Double>,
    val peak: HourlyPoint?, val notice: Insights.PrecipNotice?, val dry: Boolean, val nextWet: HourlyPoint?,
) {
    companion object {
        /** Below this an amount shows as 0.0 mm – nothing. */
        const val DRY_MM = 0.05
        /** A day stays dry while no hour reaches this chance … */
        const val DRY_CHANCE = 20.0
        /** … and the next precipitation is the first hour with this chance or [WET_MM]. */
        const val WET_CHANCE = 40.0
        const val WET_MM = 0.2

        fun of(data: WeatherData, now: Long, raining: Boolean, measured: TodayMeasured?, tf: TimeFormat): PrecipToday? {
            // Today from 00:00 to 24:00 (like the meteogram); each value covers the hour before its time
            val dayStart = tf.zoned(now).toLocalDate().atStartOfDay(tf.zone).toInstant().toEpochMilli()
            val hours = data.hourly.filter { it.time > dayStart && it.time <= dayStart + 24 * HOUR }
            if (hours.size < 2) return null
            val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
            // Hours already over with a station reading count as measured, the others as forecast
            val readings = measured?.precipitation.orEmpty().filterKeys { it > dayStart && it <= now }
            val todaySum = if (readings.isEmpty()) today?.precipitationSum ?: 0.0
            else readings.values.sum() + hours.filter { it.time !in readings }.sumOf { it.precipitation ?: 0.0 }
            // The rest of the day: hours not yet over
            val rest = hours.filter { it.time > now }
            val notice = Insights.precipNotice(data.minutely, data.hourly, data.current.condition, now, raining)
            val dry = !raining && notice == null && todaySum < DRY_MM &&
                rest.all { (it.precipitation ?: 0.0) < DRY_MM && (it.precipitationProbability ?: 0.0) < DRY_CHANCE }
            val nextWet = data.hourly.firstOrNull { it.time > now && ((it.precipitationProbability ?: 0.0) >= WET_CHANCE || (it.precipitation ?: 0.0) >= WET_MM) }
            return PrecipToday(
                todaySum, rest.sumOf { it.precipitation ?: 0.0 }, readings,
                rest.maxByOrNull { it.precipitationProbability ?: 0.0 }, notice, dry, nextWet,
            )
        }

        private const val HOUR = 3_600_000L
    }
}

@Composable
fun PrecipitationCard(data: WeatherData, now: Long, raining: Boolean = false, measured: TodayMeasured? = null) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val p = remember(data, now / 60_000L, raining, measured, tf) { PrecipToday.of(data, now, raining, measured, tf) } ?: return
    val unit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val todaySum = p.todaySum; val next = p.next; val readings = p.readings; val peak = p.peak; val notice = p.notice
    val peakChance = peak?.precipitationProbability ?: 0.0
    if (p.dry) {
        // A dry day: one line – and when the forecast has some, the next precipitation
        GlassCard(title = stringResource(R.string.precip_title), icon = Icons.Outlined.WaterDrop, info = Term.PRECIP_PROBABILITY) {
            Text(stringResource(R.string.precip_dry_today), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
            val w = p.nextWet
            Text(
                if (w == null) stringResource(R.string.precip_dry_ahead)
                else stringResource(
                    R.string.precip_next_wet, tf.weekdayShort(w.time - 3_600_000L), tf.time(w.time - 3_600_000L),
                    (w.precipitationProbability ?: 0.0).roundToInt(),
                ),
                fontSize = 14.sp, color = NimbusColors.Secondary,
            )
        }
        return
    }
    // One card for all precipitation: a one-line notice on top when it rains now or within 2 hours.
    GlassCard(title = stringResource(R.string.precip_title), icon = Icons.Outlined.WaterDrop, info = Term.PRECIP_PROBABILITY) {
        if (notice != null) {
            PrecipNoticeText(notice, now)
            Spacer(Modifier.height(10.dp))
            HairlineDivider()
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.precip_today_amount, Units.precipitationNumber(todaySum, s.precipitationUnit) + NBSP + unit),
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
            )
        }
        Text(stringResource(R.string.precip_next_amount, Units.precipitationNumber(next, s.precipitationUnit) + NBSP + unit), fontSize = 14.sp, color = NimbusColors.Secondary)
        // What the station has measured so far today
        if (readings.isNotEmpty()) {
            Text(
                stringResource(R.string.precip_measured_so_far, Units.precipitationNumber(readings.values.sum(), s.precipitationUnit) + NBSP + unit, measured?.station ?: "DWD"),
                fontSize = 14.sp, color = NimbusColors.Secondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (peak != null && peakChance >= 10) stringResource(R.string.precip_max_chance, peakChance.roundToInt(), tf.time(peak.time))
            else stringResource(R.string.precip_no_chance),
            fontSize = 14.sp, color = Color.White,
        )
        // The hour-by-hour chart is part of the 10-day forecast (in the temperature chart, or as
        // a chart of its own – setting "precipitation as its own chart")
    }
}

/**
 * Umbrellas for the precipitation line, drawn in one style (24 × 24): same tip, shaft and hooked
 * handle; the canopy is either spread (scalloped dome) or furled (slim, pointed, with a strap).
 */
private fun umbrella(name: String, canopy: String, shaftTop: Float): androidx.compose.ui.graphics.vector.ImageVector {
    fun nodes(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes()
    val ink = androidx.compose.ui.graphics.SolidColor(Color.Black)
    return androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(nodes(canopy), fill = ink, pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd)
        .addPath(
            nodes("M12,1.6 L12,3.2 M12,$shaftTop L12,19.2 A2.1,2.1 0 0 1 7.8,19.2"),
            stroke = ink, strokeLineWidth = 1.7f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        .build()
}

private val OpenUmbrella by lazy {
    umbrella("OpenUmbrella", "M2,12.5 A10,9.5 0 0 1 22,12.5 A3.333,2.2 0 0 0 15.333,12.5 A3.333,2.2 0 0 0 8.667,12.5 A3.333,2.2 0 0 0 2,12.5 Z", 12f)
}

private val ClosedUmbrella by lazy {
    // furled canopy with a cut-out strap across its middle
    umbrella(
        "ClosedUmbrella",
        "M12,2.6 C15.6,6.2 15.3,11.2 12.8,14.6 L11.2,14.6 C8.7,11.2 8.4,6.2 12,2.6 Z M9.3,8.7 L14.7,8.7 L14.6,9.9 L9.4,9.9 Z",
        14.4f,
    )
}

/** One line on top of the precipitation card: "Light rain in about 40 min", "Thunderstorms until about 11:30". */
@Composable
fun PrecipNoticeText(n: Insights.PrecipNotice, now: Long) {
    val tf = LocalTimeFormat.current
    val what = stringResource(
        when (n.kind) {
            Insights.PrecipKind.THUNDERSTORM -> R.string.pn_thunderstorm
            Insights.PrecipKind.HAIL -> R.string.pn_hail
            Insights.PrecipKind.DRIZZLE -> R.string.pn_drizzle
            Insights.PrecipKind.SLEET -> R.string.pn_sleet
            Insights.PrecipKind.SNOW -> when (n.intensity) {
                Insights.Intensity.LIGHT -> R.string.pn_snow_light
                Insights.Intensity.MODERATE -> R.string.pn_snow_moderate
                Insights.Intensity.HEAVY -> R.string.pn_snow_heavy
            }
            Insights.PrecipKind.RAIN -> when (n.intensity) {
                Insights.Intensity.LIGHT -> R.string.pn_rain_light
                Insights.Intensity.MODERATE -> R.string.pn_rain_moderate
                Insights.Intensity.HEAVY -> R.string.pn_rain_heavy
            }
        },
    )
    // Clock times throughout ("from about 11:15", "until about 11:45", "until at least 13:00")
    val time = tf.time(n.at ?: now)
    val text = when (n.state) {
        is Insights.Nowcast.StartsIn -> stringResource(R.string.pn_starts, what, time)
        is Insights.Nowcast.StopsIn -> stringResource(R.string.pn_until, what, time)
        else -> stringResource(R.string.pn_continues, what, time)
    }
    // An umbrella – the line only ever announces unpleasant weather: open while it is wet,
    // closed while the precipitation is still to come.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (n.now) OpenUmbrella else ClosedUmbrella, null, tint = PrecipBlue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
