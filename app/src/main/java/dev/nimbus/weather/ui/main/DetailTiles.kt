/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DetailTiles.kt
 * Detail tiles and cards: feels like, UV, wind, humidity, sun, moon, air quality.
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

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import dev.nimbus.weather.data.model.WeatherCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.foundation.layout.size
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.drawMoonPhase
import dev.nimbus.weather.util.Moon
import dev.nimbus.weather.util.SunPhases
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.LocalExplain
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DetailTiles(data: WeatherData, now: Long) {
    val hours = remember(data, now) { Insights.upcomingHours(data, now) }
    val c = data.current
    val cards = LocalSettings.current
    // In the user's order (settings), hidden ones and those without data left out
    val tiles = cards.orderedTiles().filter { cards.shows(it) }.mapNotNull { t ->
        when (t) {
            WeatherCard.FEELS_LIKE -> @Composable { m: Modifier -> FeelsLikeTile(data, m) }
            WeatherCard.UV_INDEX -> @Composable { m: Modifier -> UvTile(data, hours, m) }
            WeatherCard.WIND -> @Composable { m: Modifier -> WindTile(data, m) }
            WeatherCard.HUMIDITY -> if (c.humidity != null) @Composable { m: Modifier -> HumidityTile(data, m) } else null
            WeatherCard.VISIBILITY -> if (c.visibility != null) @Composable { m: Modifier -> VisibilityTile(data, m) } else null
            WeatherCard.PRESSURE -> if (c.pressure != null) @Composable { m: Modifier -> PressureTile(data, hours, m) } else null
            else -> null
        }
    }
    if (tiles.isEmpty()) return
    androidx.compose.foundation.layout.BoxWithConstraints {
    val side = (maxWidth - 12.dp) / 2
    val full = maxWidth
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tiles are square, but a row grows (both tiles alike) when large text or display size
        // needs more room – nothing is cut off.
        tiles.chunked(2).forEach { row ->
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A single tile in the last row spans the full width instead of leaving a gap.
                if (row.size == 1) row[0](Modifier.fillMaxWidth().heightIn(min = full / 2).fillMaxHeight())
                else row.forEach { tile -> tile(Modifier.weight(1f).heightIn(min = side).fillMaxHeight()) }
            }
        }
    }
    }
}

@Composable
private fun Tile(title: String, icon: ImageVector, modifier: Modifier, term: Term? = null, content: @Composable ColumnScope.() -> Unit) {
    val explain = LocalExplain.current
    GlassCard(modifier, title = title, icon = icon, contentPadding = false, info = term, onClick = term?.let { { explain(it) } }) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), content = content)
    }
}

@Composable
private fun BigValue(text: String, unit: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.Normal, color = Color.White, lineHeight = 38.sp)
        if (unit != null) Text(" $unit", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    // hyphenated, not broken anywhere, when a long word meets a narrow tile; the row of tiles
    // grows for the lines a large font needs (no line limit: nothing cut off at the bottom)
    Text(text, modifier, fontSize = 13.sp, color = Color.White, lineHeight = 17.sp, style = dev.nimbus.weather.ui.components.Hyphenated)
}

@Composable
private fun FeelsLikeTile(data: WeatherData, modifier: Modifier) {
    val s = LocalSettings.current
    val c = data.current
    val feels = c.apparentTemperature ?: c.temperature
    Tile(stringResource(R.string.feels_like), Icons.Outlined.Thermostat, modifier, Term.FEELS_LIKE) {
        BigValue(Units.temp(feels, s.temperatureUnit))
        Spacer(Modifier.weight(1f))
        Caption(
            stringResource(
                when {
                    feels < c.temperature - 1.5 -> R.string.summary_feels_colder
                    feels > c.temperature + 1.5 -> R.string.summary_feels_warmer
                    else -> R.string.summary_feels_same
                },
            ),
        )
    }
}

@Composable
private fun UvTile(data: WeatherData, hours: List<dev.nimbus.weather.data.model.HourlyPoint>, modifier: Modifier) {
    val tf = LocalTimeFormat.current
    val uv = data.current.uvIndex ?: hours.firstOrNull()?.uvIndex ?: 0.0
    val until = remember(hours) { Insights.uvProtectUntil(hours) { tf.isSameDay(it, hours.first().time) } }
    Tile(stringResource(R.string.uv_index), Icons.Outlined.WbSunny, modifier, Term.UV_INDEX) {
        BigValue(uv.roundToInt().toString())
        Text(stringResource(Texts.uvLevel(uv)), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        GradientScale(
            fraction = (uv / 11.0).toFloat(),
            colors = listOf(Color(0xFF3CD070), Color(0xFFF7D548), Color(0xFFFF9F1C), Color(0xFFFF3B30), Color(0xFFB54CD8)),
        )
        Spacer(Modifier.weight(1f))
        Caption(if (until != null && uv >= 3) stringResource(R.string.summary_uv_protect, tf.time(until + 3600_000L)) else stringResource(R.string.summary_uv_low))
    }
}

@Composable
fun GradientScale(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val h = size.height
        drawRoundRect(Brush.horizontalGradient(colors), size = size, cornerRadius = CornerRadius(h / 2))
        val x = (fraction.coerceIn(0f, 1f) * size.width).coerceIn(h / 2, size.width - h / 2)
        drawCircle(Color(0xFF1A2A40), h * 1.05f, Offset(x, h / 2))
        drawCircle(Color.White, h * 0.75f, Offset(x, h / 2))
    }
}

@Composable
private fun WindTile(data: WeatherData, modifier: Modifier) {
    val s = LocalSettings.current
    val c = data.current
    val unit = stringResource(Texts.windUnit(s.windUnit))
    val dirLabels = Texts.compass.map { stringResource(it) }
    val measurer = rememberTextMeasurer()
    Tile(stringResource(R.string.wind), Icons.Outlined.Air, modifier, Term.WIND) {
        // Minimum height: with large text the row grows instead of squeezing the compass
        Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 * 0.95f
                val center = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 72) {
                    val a = i * 5f * PI.toFloat() / 180f
                    val len = if (i % 18 == 0) r * 0.12f else r * 0.06f
                    val d = Offset(sin(a), -cos(a))
                    drawLine(
                        Color.White.copy(alpha = if (i % 18 == 0) 0.8f else 0.3f),
                        center + d * (r - len), center + d * r, 1.2.dp.toPx(),
                    )
                }
                listOf(0, 2, 4, 6).forEachIndexed { k, idx ->
                    val a = k * PI.toFloat() / 2f
                    val d = Offset(sin(a), -cos(a))
                    val layout = measurer.measure(dirLabels[idx], TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary, fontWeight = FontWeight.SemiBold))
                    val p = center + d * (r * 0.72f) - Offset(layout.size.width / 2f, layout.size.height / 2f)
                    drawText(layout, topLeft = p)
                }
                val dir = c.windDirection
                if (dir != null) {
                    // Arrow points where the wind blows to.
                    rotate((dir + 180).toFloat(), center) {
                        val tip = center + Offset(0f, -r * 0.92f)
                        val tail = center + Offset(0f, r * 0.92f)
                        drawLine(Color.White, tail, center + Offset(0f, r * 0.42f), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White, center + Offset(0f, -r * 0.42f), tip + Offset(0f, 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        val head = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(tip.x - 5.dp.toPx(), tip.y + 10.dp.toPx())
                            lineTo(tip.x + 5.dp.toPx(), tip.y + 10.dp.toPx())
                            close()
                        }
                        drawPath(head, Color.White)
                        drawCircle(Color.White, 3.dp.toPx(), tail)
                    }
                }
                drawCircle(Color(0x33000000), r * 0.36f, center)
                // Speed and unit in the middle, fitted into its circle: with a large system font
                // they ran over the arrow and the letters of the directions
                val number = Units.windNumber(c.windSpeed, s.windUnit)
                fun lay(k: Float) = measurer.measure(number, TextStyle(fontSize = 22.sp * k, fontWeight = FontWeight.SemiBold, color = Color.White)) to
                    measurer.measure(unit, TextStyle(fontSize = 11.sp * k, color = Color.White))
                var (big, small) = lay(1f)
                val room = r * 0.36f * 2f * 0.92f
                val need = maxOf(maxOf(big.size.width, small.size.width).toFloat(), (big.size.height + small.size.height) * 0.9f)
                if (need > room) lay(room / need).let { big = it.first; small = it.second }
                val top = center.y - (big.size.height + small.size.height) / 2f + small.size.height * 0.1f
                drawText(big, topLeft = Offset(center.x - big.size.width / 2f, top))
                drawText(small, topLeft = Offset(center.x - small.size.width / 2f, top + big.size.height * 0.85f))
            }
        }
        val gust = c.windGust
        val dirText = c.windDirection?.let { stringResource(R.string.from_direction, dirLabels[Units.compassIndex(it)]) }
        Caption(listOfNotNull(dirText, gust?.let { stringResource(R.string.gusts) + "\u00A0" + Units.windNumber(it, s.windUnit) + NBSP + unit }).joinToString(" · "))
    }
}

/** Chance and amount of precipitation hour by hour for the next 24 hours. */
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

@Composable
private fun HumidityTile(data: WeatherData, modifier: Modifier) {
    val s = LocalSettings.current
    Tile(stringResource(R.string.humidity), Icons.Outlined.WaterDrop, modifier, Term.DEW_POINT) {
        BigValue("${data.current.humidity?.roundToInt() ?: "–"}%")
        Spacer(Modifier.weight(1f))
        data.current.dewPoint?.let { Caption(stringResource(R.string.summary_dew_point, Units.temp(it, s.temperatureUnit))) }
    }
}

@Composable
private fun VisibilityTile(data: WeatherData, modifier: Modifier) {
    val v = data.current.visibility
    Tile(stringResource(R.string.visibility), Icons.Outlined.Visibility, modifier, Term.VISIBILITY) {
        BigValue(Units.visibilityKm(v))
        val station = data.current.stationName
        Text(
            if (data.current.visibilityMeasured && station != null) stringResource(R.string.visibility_measured, station)
            else stringResource(R.string.visibility_model),
            fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 2, lineHeight = 15.sp,
        )
        Spacer(Modifier.weight(1f))
        Caption(
            stringResource(
                when {
                    v == null || v >= 20_000 -> R.string.summary_visibility_clear
                    v >= 2_000 -> R.string.summary_visibility_hazy
                    else -> R.string.summary_visibility_fog
                },
            ),
        )
    }
}

@Composable
private fun PressureTile(data: WeatherData, hours: List<dev.nimbus.weather.data.model.HourlyPoint>, modifier: Modifier) {
    val p = data.current.pressure ?: return
    val trend = remember(hours) { Insights.pressureTrend(hours) }
    Tile(stringResource(R.string.pressure), Icons.Outlined.Compress, modifier, Term.PRESSURE) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 * 0.92f
                val center = Offset(size.width / 2, size.height / 2)
                val start = 135f
                val sweep = 270f
                for (i in 0..54) {
                    val a = Math.toRadians((start + sweep * i / 54f).toDouble()).toFloat()
                    val d = Offset(cos(a), sin(a))
                    drawLine(Color.White.copy(alpha = 0.35f), center + d * (r * 0.86f), center + d * r, 1.2.dp.toPx())
                }
                val f = ((p - 960) / (1060 - 960)).toFloat().coerceIn(0f, 1f)
                val a = Math.toRadians((start + sweep * f).toDouble()).toFloat()
                val d = Offset(cos(a), sin(a))
                drawLine(Color.White, center + d * (r * 0.78f), center + d * (r * 1.02f), 3.5.dp.toPx(), StrokeCap.Round)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (trend) { Insights.Trend.RISING -> "↑"; Insights.Trend.FALLING -> "↓"; Insights.Trend.STEADY -> "=" },
                    fontSize = 16.sp, color = Color.White,
                )
                Text(p.roundToInt().toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White, lineHeight = 22.sp)
                Text("hPa", fontSize = 11.sp, color = Color.White)
            }
        }
        Caption(
            stringResource(
                when (trend) {
                    Insights.Trend.RISING -> R.string.summary_pressure_rising
                    Insights.Trend.FALLING -> R.string.summary_pressure_falling
                    Insights.Trend.STEADY -> R.string.summary_pressure_steady
                },
            ),
        )
    }
}

/**
 * Course of the sun today (00–24 h, like the meteogram). The altitude scale is fixed per place
 * (± the midsummer noon altitude), so the size of the arc shows the season. The background is
 * tinted by light phase: day, golden hour, blue hour, night ([SunPhases]).
 */
@Composable
fun SunCard(data: WeatherData, now: Long) {
    val tf = LocalTimeFormat.current
    val explain = LocalExplain.current
    val lat = data.place.latitude
    val lon = data.place.longitude
    val date = tf.zoned(now).toLocalDate()
    val dayStart = date.atStartOfDay(tf.zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(tf.zone).toInstant().toEpochMilli()
    fun sample(from: Long, to: Long) =
        generateSequence(from) { it + 5 * 60_000L }.takeWhile { it <= to }.map { it to Moon.sunAltitude(it, lat, lon) }.toList()
    val curve = remember(lat, lon, dayStart) { sample(dayStart, dayEnd) }
    val spans = remember(curve) { SunPhases.spans(curve) }
    val today = data.daily.lastOrNull { it.date <= now }?.takeIf { tf.isSameDay(it.date, now) }
    val tomorrow = data.daily.firstOrNull { it.date >= dayEnd }
    val own = remember(lat, lon, dayStart) { Moon.sunTimes(dayStart, lat, lon) }
    val rise = today?.sunrise ?: own.first
    val set = today?.sunset ?: own.second
    val deltaMin = remember(lat, lon, dayStart) {
        val y = Moon.sunTimes(date.minusDays(1).atStartOfDay(tf.zone).toInstant().toEpochMilli(), lat, lon)
        if (own.first != null && own.second != null && y.first != null && y.second != null)
            (((own.second!! - own.first!!) - (y.second!! - y.first!!)) / 60_000.0).roundToInt() else null
    }
    val noon = curve.maxBy { it.second }
    val altNow = Moon.sunAltitude(now, lat, lon)

    // Twilight times worth mentioning: this morning, this evening or tomorrow morning.
    val twilight = remember(spans, now / 60_000) {
        val morning = spans.filter { it.morning && it.phase in TWILIGHT }
        val evening = spans.filter { !it.morning && it.phase in TWILIGHT }
        when {
            morning.isNotEmpty() && now < morning.last().end -> R.string.sun_phases_morning to morning
            evening.isNotEmpty() && now < evening.last().end -> R.string.sun_phases_evening to evening
            else -> {
                val next = SunPhases.spans(sample(dayEnd, dayEnd + 24 * 3_600_000L)).filter { it.morning && it.phase in TWILIGHT }
                if (next.isEmpty()) null else R.string.sun_phases_tomorrow to next
            }
        }
    }

    GlassCard(title = stringResource(R.string.sun), icon = Icons.Outlined.WbTwilight, info = Term.SUN, onClick = { explain(Term.SUN) }) {
        Row(Modifier.fillMaxWidth()) {
            SunFact(stringResource(R.string.sunrise), rise?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f), stringResource(R.string.sunrise_short))
            SunFact(stringResource(R.string.sunset), set?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f), stringResource(R.string.sunset_short))
            val len = if (rise != null && set != null) (set - rise) / 60_000L else null
            SunFact(
                stringResource(R.string.day_length),
                len?.let { stringResource(R.string.duration_h_min, (it / 60).toInt(), (it % 60).toInt()) }
                    ?: stringResource(if (altNow > 0) R.string.polar_day else R.string.polar_night),
                deltaMin?.let { d ->
                    val sign = when { d > 0 -> "+"; d < 0 -> "\u2212"; else -> "\u00B1" }
                    stringResource(R.string.day_length_delta, "$sign${kotlin.math.abs(d)}${NBSP}min")
                },
                Modifier.weight(1.1f),
                stringResource(R.string.day_length_short),
            )
        }
        Spacer(Modifier.height(12.dp))
        SunChart(
            curve, spans, SunPhases.maxAltitude(lat), dayStart, dayEnd, rise, set, noon, now, altNow,
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
        )
        Spacer(Modifier.height(8.dp))
        SunLegend()
        Spacer(Modifier.height(10.dp))
        val deg = { v: Double -> "${kotlin.math.abs(v).roundToInt()}°" }
        Caption(
            buildString {
                append(stringResource(if (altNow >= 0) R.string.sun_now_up else R.string.sun_now_down, deg(altNow)))
                append(" · ")
                if (set != null && now > set && tomorrow?.sunrise != null) append(stringResource(R.string.sunrise_tomorrow, tf.time(tomorrow.sunrise!!)))
                else append(stringResource(R.string.sun_highest, deg(noon.second), tf.time(noon.first)))
            },
        )
        twilight?.let { (label, list) ->
            val names = list.map { sp ->
                stringResource(
                    when {
                        sp.phase == SunPhases.Phase.BLUE -> R.string.phase_blue
                        sp.morning -> R.string.phase_golden_morning
                        else -> R.string.phase_golden_evening
                    },
                ) + " " + tf.time(sp.start) + "\u2013" + tf.time(sp.end)
            }
            Spacer(Modifier.height(2.dp))
            Text(stringResource(label, names.joinToString(" · ")), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 17.sp)
        }
    }
}

private val TWILIGHT = setOf(SunPhases.Phase.GOLDEN, SunPhases.Phase.BLUE)

@Composable
private fun SunFact(label: String, value: String, note: String?, modifier: Modifier, short: String = label) {
    Column(modifier) {
        // "Sonnenuntergang" in a third of a narrow card: "Untergang" rather than "Sonnenuntergan"
        dev.nimbus.weather.ui.components.FitText(label, short, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = NimbusColors.Secondary))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
        if (note != null) Text(note, fontSize = 11.sp, color = NimbusColors.Tertiary, maxLines = 1)
    }
}

private val SunYellow = Color(0xFFFFD66B)

/** Background tint of each light phase (over the dark card). */
private fun phaseTint(p: SunPhases.Phase): Color = when (p) {
    SunPhases.Phase.DAY -> Color(0x33FFE08A)
    SunPhases.Phase.GOLDEN -> Color(0x5CFF9448)
    SunPhases.Phase.BLUE -> Color(0x7A3F6FD8)
    SunPhases.Phase.NIGHT -> NightShade
}

@Composable
private fun SunLegend() {
    val items = listOf(
        SunPhases.Phase.DAY to R.string.phase_day,
        SunPhases.Phase.GOLDEN to R.string.phase_golden,
        SunPhases.Phase.BLUE to R.string.phase_blue,
        SunPhases.Phase.NIGHT to R.string.phase_night,
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (phase, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp, 10.dp)) {
                    drawRoundRect(phaseTint(phase), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(Color(0x33FFFFFF), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1f))
                }
                Spacer(Modifier.width(5.dp))
                Text(stringResource(name), fontSize = 11.sp, color = NimbusColors.Secondary)
            }
        }
    }
}

@Composable
private fun SunChart(
    curve: List<Pair<Long, Double>>, spans: List<SunPhases.Span>, maxAlt: Double, start: Long, end: Long,
    rise: Long?, set: Long?, noon: Pair<Long, Double>, now: Long, altNow: Double, modifier: Modifier,
) {
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val horizonLabel = stringResource(R.string.horizon)
    // Fixed scale for the place: midsummer noon at the top, midwinter midnight at the bottom.
    val hi = maxAlt + 5
    val lo = -maxAlt - 1
    Canvas(modifier.height(150.dp)) {
        val labelH = measurer.measure("00", labelStyle).size.height
        val top = 2.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val halfLabel = measurer.measure("24", labelStyle).size.width / 2f
        val l = halfLabel; val r = size.width - halfLabel
        val span = (end - start).toFloat()
        fun x(t: Long) = l + (r - l) * ((t - start) / span)
        fun y(a: Double) = (top + (hi - a) / (hi - lo) * (bottom - top)).toFloat()
        val horizon = y(0.0)
        // Light phases as background columns
        spans.forEach { sp -> drawRect(phaseTint(sp.phase), Offset(x(sp.start), top), Size(x(sp.end) - x(sp.start), bottom - top)) }
        // Time grid
        var mark = start
        while (mark <= end) {
            val xm = x(mark)
            drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val lt = measurer.measure(if (mark == end) tf.hourEnd(mark) else tf.hour(mark), labelStyle)
            drawText(lt, topLeft = Offset(xm - lt.size.width / 2f, bottom + 4.dp.toPx()))
            mark += 6 * 3_600_000L
        }
        // Daylight under the arc
        val area = Path().apply {
            moveTo(x(curve.first().first), horizon)
            curve.forEach { (t, a) -> lineTo(x(t), minOf(y(a), horizon)) }
            lineTo(x(curve.last().first), horizon); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(SunYellow.copy(alpha = 0.35f), SunYellow.copy(alpha = 0.05f)), y(maxAlt), horizon))
        // Curve: bright above, faint below the horizon
        val path = Path()
        curve.forEachIndexed { i, (t, a) -> if (i == 0) path.moveTo(x(t), y(a)) else path.lineTo(x(t), y(a)) }
        clipRect(bottom = horizon) { drawPath(path, SunYellow.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
        clipRect(top = horizon) { drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))) }
        // Horizon
        drawLine(Color.White.copy(alpha = 0.45f), Offset(l, horizon), Offset(r, horizon), 1.dp.toPx())
        val hl = measurer.measure(horizonLabel, labelStyle)
        drawText(hl, topLeft = Offset(l + 4.dp.toPx(), horizon - hl.size.height - 1.dp.toPx()))
        // Sunrise / sunset marks
        listOfNotNull(rise, set).forEach { t -> drawCircle(SunYellow, 3.dp.toPx(), Offset(x(t), horizon)) }
        // Highest and lowest point
        if (noon.second > 0) {
            val nl = measurer.measure("${noon.second.roundToInt()}°", labelStyle)
            drawText(nl, topLeft = Offset(x(noon.first) - nl.size.width / 2f, y(noon.second) - nl.size.height - 2.dp.toPx()))
        }
        // Now
        val c = Offset(x(now), y(altNow))
        if (altNow >= 0) {
            drawCircle(SunYellow.copy(alpha = 0.25f), 11.dp.toPx(), c)
            drawCircle(SunYellow, 6.dp.toPx(), c)
        } else {
            drawCircle(Color(0xFF1A2A40), 6.dp.toPx(), c)
            drawCircle(Color.White.copy(alpha = 0.8f), 5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------------------
// Air quality & pollen

@Composable
fun AirQualityCard(data: WeatherData) {
    val aq = data.airQuality ?: return
    val aqi = aq.europeanAqi ?: return
    GlassCard(title = stringResource(R.string.air_quality), icon = Icons.Outlined.Masks, info = Term.AIR_QUALITY) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(aqi.roundToInt().toString(), fontSize = 34.sp, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(Texts.aqiLevel(aqi)), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        GradientScale(
            (aqi / 120.0).toFloat(),
            listOf(Color(0xFF50F0E6), Color(0xFF50CCAA), Color(0xFFF0E641), Color(0xFFFF5050), Color(0xFF960032), Color(0xFF7D2181)),
        )
        Spacer(Modifier.height(10.dp))
        Caption(stringResource(R.string.aqi_detail, aq.pm25?.roundToInt()?.toString() ?: "–", aq.pm10?.roundToInt()?.toString() ?: "–"))
    }
}


// ---------------------------------------------------------------------------------------
// Moon (calculated on the device)

@Composable
fun MoonCard(data: WeatherData, now: Long) {
    val tf = LocalTimeFormat.current
    val explain = LocalExplain.current
    val lat = data.place.latitude
    val lon = data.place.longitude
    val info = remember(now / 600_000) { Moon.preciseIllumination(now) }
    val times = remember(data.place.id, tf.zoned(now).toLocalDate()) {
        Moon.times(tf.zoned(now).toLocalDate().atStartOfDay(tf.zone).toInstant().toEpochMilli(), lat, lon)
    }
    val nextFull = remember(now / 3_600_000) { Moon.nextFullMoon(now) }
    val phaseName = stringResource(
        when (Moon.phaseOf(info.phase)) {
            Moon.Phase.NEW -> R.string.moon_new
            Moon.Phase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
            Moon.Phase.FIRST_QUARTER -> R.string.moon_first_quarter
            Moon.Phase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
            Moon.Phase.FULL -> R.string.moon_full
            Moon.Phase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
            Moon.Phase.LAST_QUARTER -> R.string.moon_last_quarter
            Moon.Phase.WANING_CRESCENT -> R.string.moon_waning_crescent
        },
    )
    GlassCard(title = stringResource(R.string.moon), icon = Icons.Outlined.DarkMode, info = Term.MOON, onClick = { explain(Term.MOON) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(84.dp)) {
                drawMoonPhase(center, size.minDimension / 2 * 0.92f, info.phase.toFloat(), lat < 0)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(phaseName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 24.sp)
                Text(stringResource(R.string.moon_illumination, (info.fraction * 100).roundToInt()), fontSize = 14.sp, color = NimbusColors.Secondary)
                Spacer(Modifier.height(8.dp))
                when {
                    times.alwaysUp -> Caption(stringResource(R.string.moon_always_up))
                    times.alwaysDown -> Caption(stringResource(R.string.moon_always_down))
                    else -> {
                        MoonRow(stringResource(R.string.moonrise), times.rise?.let { tf.time(it) } ?: "–")
                        MoonRow(stringResource(R.string.moonset), times.set?.let { tf.time(it) } ?: "–")
                    }
                }
                MoonRow(stringResource(R.string.next_full_moon), tf.dayMonth(nextFull))
            }
        }
    }
}

@Composable
private fun MoonRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = NimbusColors.Secondary, maxLines = 1)
        Text(value, fontSize = 13.sp, color = Color.White, maxLines = 1)
    }
}
