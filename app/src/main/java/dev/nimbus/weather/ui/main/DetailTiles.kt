/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DetailTiles.kt
 * The small tiles: feels like, UV, wind, humidity, visibility, pressure.
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * The small tiles shown, in the user's order (settings) – hidden ones and those without data left
 * out – each with its card ([WeatherCard]) and drawn into a modifier giving its size.
 */
@Composable
fun detailTiles(data: WeatherData, now: Long): List<Pair<WeatherCard, @Composable (Modifier) -> Unit>> {
    val hours = remember(data, now) { Insights.upcomingHours(data, now) }
    val c = data.current
    val cards = LocalSettings.current
    return cards.orderedTiles().filter { cards.shows(it) }.mapNotNull { t ->
        when (t) {
            WeatherCard.FEELS_LIKE -> @Composable { m: Modifier -> FeelsLikeTile(data, m) }
            WeatherCard.UV_INDEX -> @Composable { m: Modifier -> UvTile(data, hours, m) }
            WeatherCard.WIND -> @Composable { m: Modifier -> WindTile(data, m) }
            WeatherCard.HUMIDITY -> if (c.humidity != null) @Composable { m: Modifier -> HumidityTile(data, m) } else null
            WeatherCard.VISIBILITY -> if (c.visibility != null) @Composable { m: Modifier -> VisibilityTile(data, m) } else null
            WeatherCard.PRESSURE -> if (c.pressure != null) @Composable { m: Modifier -> PressureTile(data, hours, m) } else null
            else -> null
        }?.let { t to it }
    }
}

/** The small tiles in one column of cards: two side by side, square. */
@Composable
fun DetailTiles(data: WeatherData, now: Long) {
    val tiles = detailTiles(data, now).map { it.second }
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

/**
 * A small tile as a card of its own in the columns of a tablet or a phone sideways: the width of
 * its column, half as high (as a single tile below the others) – so the tiles fill whichever
 * column is shorter, their titles with room.
 */
@Composable
fun LaneTile(tile: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val half = maxWidth / 2
        // a height of its own (as a row of two has): the tiles share theirs out by weight – in a
        // column of the grid, without one, the wind's compass and the pressure's value got none
        Box(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            tile(Modifier.fillMaxWidth().heightIn(min = half).fillMaxHeight())
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
internal fun Caption(text: String, modifier: Modifier = Modifier) {
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
            if (data.current.visibilityMeasured && station != null) stringResource(R.string.visibility_measured, (data.current.stationNetwork ?: dev.nimbus.weather.data.model.StationNetwork.DWD).label, station)
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
