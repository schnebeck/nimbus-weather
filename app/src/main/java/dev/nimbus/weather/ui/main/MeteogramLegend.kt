/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/MeteogramLegend.kt
 * What the day charts' lines and bars mean, with the day's totals, and the values at the cursor.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.Units
import kotlin.math.roundToInt

/** A duration in minutes as "9:54 h" / "9:54 Std." */
@Composable
fun hoursMinutes(minutes: Double): String {
    val m = minutes.roundToInt()
    return stringResource(R.string.duration_h_min, m / 60, m % 60)
}

/** A legend entry: a colour swatch (or a line) and its text; the swatch stays at the first line when the text wraps. */
@Composable
fun LegendItem(color: Color, text: String, line: Boolean = false, dashed: Boolean = false, frame: Boolean = false, brush: Brush? = null) = Row(verticalAlignment = Alignment.Top) {
    val lineH = 15.sp
    Box(Modifier.height(with(LocalDensity.current) { lineH.toDp() }), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (line) 16.dp else 12.dp, 10.dp)) {
            if (line && brush != null) drawLine(brush, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(), StrokeCap.Round)
            else if (line) drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null,   // as the forecast curve
            ) else if (frame) {
                val w = HourBars.FRAME_DP.dp.toPx()
                drawRoundRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(2.dp.toPx()), style = Stroke(w))
            } else drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
    Spacer(Modifier.width(5.dp))
    Text(text, fontSize = 11.sp, lineHeight = lineH, color = NimbusColors.Secondary)
}

/** Legend entries in a flowing row, as below the meteogram. */
@Composable
fun LegendRow(content: @Composable () -> Unit) = androidx.compose.foundation.layout.FlowRow(
    Modifier.padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) { content() }

/**
 * What the curves and bars mean, with the day's totals – readable without the cursor: the
 * temperature curve in its colours ([tempColors]: of the day's lowest and highest value) and, in
 * the look-back, the dashed forecast ([tempForecast]); sunshine ([sun], null: no sunshine row) and
 * precipitation as blocks (measured) and frames (expected), each with its total.
 */
@Composable
internal fun BarLegend(
    rain: HourBar, sun: HourBar?, chance: Boolean = false,
    tempColors: Pair<Color, Color>, tempForecast: Boolean = false,
    /** The hours over have no precipitation / sunshine reading at all. */
    rainNotMeasured: Boolean = false, sunNotMeasured: Boolean = false,
) {
    val s = LocalSettings.current
    val unit = stringResource(Texts.precipUnit(s.precipitationUnit))
    fun amount(v: Double?) = Units.precipitationNumber(v ?: 0.0, s.precipitationUnit) + NBSP + unit
    // Temperature, sunshine, precipitation – each named, the chart shows all of them
    LegendRow {
        val temp = Brush.horizontalGradient(listOf(tempColors.first, tempColors.second))
        LegendItem(tempColors.second, stringResource(R.string.legend_temperature), line = true, brush = temp)
        if (tempForecast) LegendItem(ForecastLine, stringResource(R.string.legend_temp_forecast), line = true, dashed = true)
        if (sun != null) BarLegendItems(
            HourBars.Sun, sun, R.string.legend_sunshine, R.string.legend_sun_measured, R.string.legend_sun_forecast,
            R.string.legend_sun_not_measured.takeIf { sunNotMeasured },
        ) { hoursMinutes(it) }
        BarLegendItems(
            HourBars.Rain, rain, R.string.legend_precip, R.string.legend_precip_measured, R.string.legend_precip_forecast,
            R.string.legend_precip_not_measured.takeIf { rainNotMeasured },
        ) { amount(it) }
        if (chance) LegendItem(ChanceLine, stringResource(R.string.legend_chance), line = true)
    }
}

/**
 * Values at the cursor position as a table – every value in its own fixed cell, so nothing
 * jumps while the cursor moves, and the table always has the same rows (the card keeps its
 * height). Look-back: measurement and forecast side by side.
 */
@Composable
internal fun Readout(
    h: MeteoPoint, highlighted: Boolean, compare: Boolean,
    /** Today's chart: the forecast's feels-like temperature and humidity in rows of their own. */
    extraRows: Boolean = false,
    /** A day to come: while no cursor is set, the whole day's figures instead of an hour's. */
    day: DayOverview? = null,
) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val dirs = Texts.compass.map { stringResource(it) }
    val wUnit = stringResource(Texts.windUnit(s.windUnit))
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    fun t(v: Double?) = v?.let { Units.temp(it, s.temperatureUnit) } ?: NO_VALUE
    fun p(v: Double?) = v?.let { Units.precipitationNumber(it, s.precipitationUnit) + NBSP + pUnit } ?: NO_VALUE
    fun w(v: Double?, dir: Double? = null) =
        v?.let { Units.windNumber(it, s.windUnit) + NBSP + wUnit + (dir?.let { d -> NBSP + dirs[Units.compassIndex(d)] } ?: "") } ?: NO_VALUE
    fun sun(v: Double?) = v?.let { "${it.roundToInt()}" + NBSP + "min" } ?: NO_VALUE
    fun pct(v: Double?, amount: Double?) = v?.let { (Insights.chanceText(it, amount) ?: "0") + NBSP + "%" } ?: NO_VALUE
    // measured or expected: the columns say it (look-back, today); a day to come is all forecast
    val whole = day?.takeIf { !highlighted && !compare }
    val condition = stringResource(if (whole != null) Texts.condition(whole.condition, true) else Texts.condition(h.condition, h.isDay))
    val lTemp = stringResource(R.string.readout_temperature)
    val lPrecip = stringResource(R.string.precipitation)
    val lChance = stringResource(R.string.readout_chance)
    val lWind = stringResource(R.string.wind)
    val lGust = stringResource(R.string.gusts)
    val lSun = stringResource(R.string.sunshine_short)
    // Time, symbol and weather in one line; the table below gets the full width
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the hour the values cover (and the cursor stands on)
            Text(
                if (whole != null) stringResource(R.string.readout_whole_day) else tf.time(h.time - 3_600_000L) + "–" + tf.time(h.time), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (highlighted) Color.White else NimbusColors.Secondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(8.dp))
            if (whole != null) WeatherIcon(whole.condition, true, size = 24.dp) else WeatherIcon(h.condition, h.isDay, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(condition, Modifier.weight(1f), fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            if (compare) {
                // Look-back and today: measured and expected side by side – an hour to come, or a
                // quantity not measured, has an empty cell for the measurement
                val c = h.compare ?: HourCompare(
                    null, h.temperature, null, h.precipitation, h.precipitationChance,
                    null, null, h.windSpeed, null, h.windGust, null, h.sunshine,
                )
                ReadoutTable(
                    listOf(stringResource(R.string.readout_measured), stringResource(R.string.readout_expected)),
                    listOfNotNull(
                        lTemp to listOf(t(c.tempM), t(c.tempF)),
                        if (extraRows) stringResource(R.string.readout_feels) to listOf(NO_VALUE, t(c.feelsF)) else null,
                        lPrecip to listOf(p(c.precipM), p(c.precipF)),
                        lChance to listOf(NO_VALUE, pct(c.chanceF, c.precipF)),
                        lWind to listOf(w(c.windM, c.windDirM), w(c.windF)),
                        lGust to listOf(w(c.gustM), w(c.gustF)),
                        lSun to listOf(sun(c.sunM), sun(c.sunF)),
                        if (extraRows) stringResource(R.string.humidity) to listOf(NO_VALUE, c.humidityF?.let { "${it.roundToInt()}" + NBSP + "%" } ?: NO_VALUE) else null,
                    ),
                )
            } else {
                val hour = listOf(
                    lTemp to t(h.temperature),
                    stringResource(R.string.readout_feels) to t(h.apparentTemperature),
                    lPrecip to p(h.precipitation ?: 0.0),
                    lChance to pct(h.precipitationChance, h.precipitation),
                    lWind to w(h.windSpeed, h.windDirection),
                    lGust to w(h.windGust),
                    lSun to sun(h.sunshine?.takeIf { h.isDay || it >= 1.0 } ?: if (h.sunshine != null) 0.0 else null),
                    stringResource(R.string.humidity) to (h.humidity?.let { "${it.roundToInt()}" + NBSP + "%" } ?: NO_VALUE),
                )
                // The day in the same eight cells: the card keeps its height when the cursor comes or goes
                val dayPairs = day?.let { d ->
                    listOf(
                        stringResource(R.string.readout_high) to t(d.high),
                        stringResource(R.string.readout_low) to t(d.low),
                        lPrecip to p(d.precipitation ?: 0.0),
                        lChance to pct(d.chance, d.precipitation),
                        lWind to w(d.wind, d.windDirection),
                        lGust to w(d.gust),
                        lSun to (d.sunMinutes?.let { hoursMinutes(it) } ?: NO_VALUE),
                        stringResource(R.string.uv_index) to (d.uv?.let { "${it.roundToInt()}" } ?: NO_VALUE),
                    )
                }
                ReadoutPairs(
                    dayPairs?.takeIf { whole != null } ?: hour,
                    // the widest values to expect, in the units set
                    // (every compass direction; a rarer wider value, e.g. a gust of 120 km/h, still fits
                    // its cell beside the short label – the layout never switches for it)
                    reserve = listOf(t(-88.0), p(88.8), "100" + NBSP + "%", sun(60.0)) + (0 until 8).map { w(88.0, it * 45.0) } +
                        listOfNotNull(day?.let { hoursMinutes(14 * 60.0 + 59) }),
                    // both sets of labels: the layout is the same with and without cursor
                    labels = hour.map { it.first } + dayPairs.orEmpty().map { it.first },
                )
            }
        }
    }
}
