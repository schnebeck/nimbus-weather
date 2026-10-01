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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import dev.nimbus.weather.data.model.WeatherCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
    Text(text, modifier, fontSize = 13.sp, color = Color.White, lineHeight = 17.sp, maxLines = 3)
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
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Units.windNumber(c.windSpeed, s.windUnit), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White, lineHeight = 22.sp)
                Text(unit, fontSize = 11.sp, color = Color.White, lineHeight = 12.sp)
            }
        }
        val gust = c.windGust
        val dirText = c.windDirection?.let { stringResource(R.string.from_direction, dirLabels[Units.compassIndex(it)]) }
        Caption(listOfNotNull(dirText, gust?.let { stringResource(R.string.gusts) + "\u00A0" + Units.windNumber(it, s.windUnit) + NBSP + unit }).joinToString(" · "))
    }
}

/** Chance and amount of precipitation hour by hour for the next 24 hours. */
@Composable
fun PrecipitationCard(data: WeatherData, now: Long, raining: Boolean = false, measured: TodayMeasured? = null) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    // Today from 00:00 to 24:00 (like the meteogram); each value covers the hour before its time
    val dayStart = remember(now / 3_600_000L) { tf.zoned(now).toLocalDate().atStartOfDay(tf.zone).toInstant().toEpochMilli() }
    val hours = remember(data, dayStart) { data.hourly.filter { it.time > dayStart && it.time <= dayStart + 24 * 3_600_000L } }
    if (hours.size < 2) return
    val unit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
    // Hours already over with a station reading count as measured, the others as forecast
    val readings = measured?.precipitation.orEmpty().filterKeys { it > dayStart && it <= now }
    val todaySum = if (readings.isEmpty()) today?.precipitationSum ?: 0.0
    else readings.values.sum() + hours.filter { it.time !in readings }.sumOf { it.precipitation ?: 0.0 }
    // The rest of the day: hours not yet over
    val rest = hours.filter { it.time > now }
    val next = rest.sumOf { it.precipitation ?: 0.0 }
    val peak = rest.maxByOrNull { it.precipitationProbability ?: 0.0 }
    val peakChance = peak?.precipitationProbability ?: 0.0
    val notice = remember(data, now, raining) { Insights.precipNotice(data.minutely, data.hourly, data.current.condition, now, raining) }
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
        Spacer(Modifier.height(10.dp))
        PrecipChart(
            hours.map { PrecipHour(it.time, it.precipitation, it.precipitationProbability, readings[it.time]) },
            nightsFromDaily(data.daily, dayStart, dayStart + 24 * 3_600_000L), now, compare = false,
            Modifier.fillMaxWidth().bleed(CARD_BLEED),
        )
    }
}

/**
 * Today from 00:00 to 24:00 in the style of the meteogram (hours already over paler, a dashed
 * mark at the current time): amount per hour as bars (right axis, mm or in) – measured (dark blue)
 * for the hours already over, where a station reading exists, else forecast –, chance
 * of precipitation as a line (left axis, %) over the forecast hours, night shading. Each bar covers the hour before its
 * time stamp (like the model values). A long press shows a cursor with the values of the hour –
 * dragging moves it, it fades out after 10 s, as in the 10-day forecast.
 *
 * [compare] (look-back): measured and forecast amount together – the measurement as a dark blue
 * bar under the translucent forecast – and the chance over all hours.
 */
@Composable
fun PrecipChart(
    hours: List<PrecipHour>, nights: List<LongRange>, now: Long, compare: Boolean, modifier: Modifier,
    /** End of the time axis (24:00) when the last hours are missing. */
    endOfDay: Long? = null,
) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val unitStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary, fontWeight = FontWeight.SemiBold)
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val inch = s.precipitationUnit == dev.nimbus.weather.data.model.PrecipitationUnit.INCH
    val start = hours.first().time - 3_600_000L
    val end = maxOf(hours.last().time, endOfDay ?: 0L)
    val span = (end - start).toFloat()
    val amounts = hours.map { Units.precipitationValue(it.forecast ?: 0.0, s.precipitationUnit) }
    val measuredAmounts = hours.map { h -> h.measured?.takeIf { compare || h.time <= now }?.let { Units.precipitationValue(it, s.precipitationUnit) } }
    val hasMeasured = measuredAmounts.any { it != null }
    // Today the measurement replaces the forecast; in the look-back both are drawn
    val shownForecast = if (compare) amounts else amounts.filterIndexed { i, _ -> measuredAmounts[i] == null }
    val amountMax = maxOf(if (inch) 0.04 else 1.0, shownForecast.maxOrNull() ?: 0.0, measuredAmounts.maxOf { it ?: 0.0 }).let { if (inch) kotlin.math.ceil(it * 20) / 20 else kotlin.math.ceil(it) }
    fun amountLabel(k: Int): String = when {
        k == 0 -> "0"
        inch -> String.format(java.util.Locale.getDefault(), "%.2f", amountMax * k / 2)
        else -> Units.oneDecimal(amountMax * k / 2)
    }

    var selected by remember(hours) { mutableStateOf(hours.indexOfFirst { it.time > now }.coerceAtLeast(0)) }
    var cursorOn by remember { mutableStateOf(false) }
    var touched by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(touched, cursorOn) {
        if (cursorOn) { kotlinx.coroutines.delay(10_000L); cursorOn = false }
    }
    val cursorAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (cursorOn) 1f else 0f, androidx.compose.animation.core.tween(if (cursorOn) 150 else 700), label = "cursor",
    )
    // Plot edges, shared by drawing and touch handling
    var plotL by remember { mutableStateOf(0f) }
    var plotR by remember { mutableStateOf(1f) }
    fun indexAt(xPx: Float): Int {
        val t = start + ((xPx - plotL) / (plotR - plotL)).coerceIn(0f, 1f) * span
        // the bar of an hour spans [time - 1 h, time]
        return hours.indices.minBy { kotlin.math.abs(hours[it].time - 1_800_000L - t) }
    }

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(124.dp)
                .pointerInput(hours) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { cursorOn = true; selected = indexAt(it.x); touched++ },
                    ) { change, _ ->
                        change.consume()
                        selected = indexAt(change.position.x)
                        touched++
                    }
                }
                .pointerInput(hours) {
                    detectTapGestures(onTap = { if (cursorOn) { selected = indexAt(it.x); touched++ } })
                },
        ) {
            val gap = 3.dp.toPx()
            val axisL = listOf("100", "50", "0").maxOf { measurer.measure(it, labelStyle).size.width } + gap
            val axisR = maxOf((0..2).maxOf { measurer.measure(amountLabel(it), labelStyle).size.width }, measurer.measure(pUnit, unitStyle).size.width) + gap
            val unitP = measurer.measure("%", unitStyle)
            val unitA = measurer.measure(pUnit, unitStyle)
            val top = unitP.size.height + measurer.measure("0", labelStyle).size.height / 2f + 4.dp.toPx()
            val labelH = measurer.measure("00", labelStyle).size.height
            val bottom = size.height - labelH - 4.dp.toPx()
            val l = axisL
            val r = size.width - axisR
            plotL = l; plotR = r
            fun x(t: Long) = l + (r - l) * ((t - start) / span)
            fun yP(chance: Double) = (bottom - (chance / 100.0).coerceIn(0.0, 1.0) * (bottom - top)).toFloat()
            fun yA(v: Double) = (bottom - (v / amountMax).coerceIn(0.0, 1.0) * (bottom - top)).toFloat()
            drawRect(DayTint, Offset(l, top), Size(r - l, bottom - top))
            nights.forEach { n ->
                val a = maxOf(n.first, start); val b = minOf(n.last + 1, end)
                if (b > a) drawRect(NightShade, Offset(x(a), top), Size(x(b) - x(a), bottom - top))
            }
            drawText(unitP, topLeft = Offset(0f, 0f))
            drawText(unitA, topLeft = Offset(size.width - unitA.size.width, 0f))
            for (k in 0..2) {
                val y = top + (bottom - top) * k / 2
                drawLine(Color(0x1FFFFFFF), Offset(l, y), Offset(r, y), 1f)
                val t = measurer.measure("${100 - 50 * k}", labelStyle)
                drawText(t, topLeft = Offset(l - t.size.width - gap, y - t.size.height / 2f))
                val ta = measurer.measure(amountLabel(2 - k), labelStyle)
                drawText(ta, topLeft = Offset(r + gap, y - ta.size.height / 2f))
            }
            // Time axis every 3 hours, 00 … 24
            var mark = start
            while (mark <= end) {
                val xm = x(mark)
                drawLine(Color(0x1FFFFFFF), Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                val lt = measurer.measure(if (mark == end) tf.hourEnd(mark) else tf.hour(mark), labelStyle)
                drawText(lt, topLeft = Offset((xm - lt.size.width / 2f).coerceIn(0f, size.width - lt.size.width), bottom + 4.dp.toPx()))
                mark += 3 * 3_600_000L
            }
            val bw = (r - l) / (span / 3_600_000f)
            fun centre(i: Int) = x(hours[i].time) - bw / 2
            // Amount: bars
            amounts.forEachIndexed { i, v ->
                val bh = bottom - yA(v)
                val m = measuredAmounts[i]
                val left = centre(i) - bw * 0.36f
                if (m != null) {
                    // Hour over, with a station reading: the measured amount instead of the forecast –
                    // in the look-back with the forecast laid over it in translucent light blue and a
                    // thin outline (a light overhang: less fell than forecast; dark above it: more)
                    val w = bw * 0.72f
                    val mh = bottom - yA(m)
                    if (mh > 0.5f) drawRoundRect(MeasuredBar, Offset(left, bottom - mh), Size(w, mh), CornerRadius(2.dp.toPx()))
                    if (compare && bh > 0.5f) {
                        drawRoundRect(ForecastOverlay, Offset(left, bottom - bh), Size(w, bh), CornerRadius(2.dp.toPx()))
                        drawRoundRect(
                            AmountBar, Offset(left, bottom - bh), Size(w, bh), CornerRadius(2.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    }
                } else {
                    // hours already over without a reading: paler (model values, not measurements)
                    val c = if (hours[i].time <= now && !compare) AmountBar.copy(alpha = 0.4f) else AmountBar
                    if (bh > 0.5f) drawRoundRect(c, Offset(left, bottom - bh), Size(bw * 0.72f, bh), CornerRadius(2.dp.toPx()))
                }
            }
            // Chance: a line over the hours without a measurement (a chance makes no sense for those)
            val line = androidx.compose.ui.graphics.Path()
            var lineStarted = false
            hours.forEachIndexed { i, h ->
                if (measuredAmounts[i] != null && !compare) return@forEachIndexed
                val y = yP(h.chance ?: 0.0)
                if (!lineStarted) { line.moveTo(centre(i), y); lineStarted = true } else line.lineTo(centre(i), y)
            }
            drawPath(line, ChanceLine, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            // Current time
            if (now in start..end) {
                val xn = x(now)
                drawLine(Color(0xB3FFFFFF), Offset(xn, top), Offset(xn, bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            }
            // Cursor
            if (cursorAlpha > 0f) {
                val i = selected.coerceIn(0, hours.lastIndex)
                val xc = centre(i)
                drawLine(Color.White.copy(alpha = 0.85f * cursorAlpha), Offset(xc, top - 2.dp.toPx()), Offset(xc, bottom), 1.5.dp.toPx())
                if (measuredAmounts[i] == null || compare) {
                    val yc = yP(hours[i].chance ?: 0.0)
                    drawCircle(Color(0xFF1A2A40).copy(alpha = cursorAlpha), 5.dp.toPx(), Offset(xc, yc))
                    drawCircle(ChanceLine.copy(alpha = cursorAlpha), 3.dp.toPx(), Offset(xc, yc))
                }
            }
        }
        // Legend, or the values of the hour under the cursor – same place, so the card keeps its height
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // One line in every language and font size (shrinks instead of wrapping)
            androidx.compose.foundation.text.BasicText(
                stringResource(if (hasMeasured) R.string.precip_chart_hint_measured else R.string.precip_chart_hint), Modifier.fillMaxWidth().alpha(1f - cursorAlpha),
                style = TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary), maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            )
            val sel = selected.coerceIn(0, hours.lastIndex)
            val h = hours[sel]
            fun amount(v: Double?) = v?.let { Units.precipitationNumber(it, s.precipitationUnit) + NBSP + pUnit } ?: NO_VALUE
            val chance = h.chance?.let { (Insights.chanceText(it, h.forecast) ?: "0") + NBSP + "%" } ?: NO_VALUE
            val time = stringResource(R.string.readout_time) to tf.time(h.time - 3_600_000L) + "–" + tf.time(h.time)
            val m = measuredAmounts[sel]?.let { h.measured }
            // Fixed cells: sliding the cursor changes the values, nothing moves
            ReadoutCells(
                if (compare) listOf(
                    time,
                    stringResource(R.string.history_legend_measured) to amount(m),
                    stringResource(R.string.forecast) to amount(h.forecast),
                    stringResource(R.string.readout_chance) to chance,
                ) else listOf(
                    time,
                    (if (m != null) stringResource(R.string.history_legend_measured) else stringResource(R.string.forecast)) to amount(m ?: h.forecast ?: 0.0),
                    stringResource(R.string.readout_chance) to if (m != null) NO_VALUE else chance,
                ),
                Modifier.alpha(cursorAlpha),
            )
        }
    }
}

/** One hour of a precipitation chart: forecast amount and chance, the measured amount (null: none). */
data class PrecipHour(val time: Long, val forecast: Double?, val chance: Double?, val measured: Double?)

/** Forecast laid over a measured bar (look-back): translucent light blue. */
private val ForecastOverlay = Color(0x668CC8FF)
/** Forecast amount: light blue. */
private val AmountBar = Color(0xE08CC8FF)
/** Measured amount (DWD station): dark blue. */
private val MeasuredBar = Color(0xFF2563EB)
/** Chance of precipitation: white line, like the other curves (the bars are blue). */
private val ChanceLine = Color(0xF2FFFFFF)

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
            SunFact(stringResource(R.string.sunrise), rise?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f))
            SunFact(stringResource(R.string.sunset), set?.let { tf.time(it) } ?: "–", null, Modifier.weight(1f))
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
private fun SunFact(label: String, value: String, note: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 1)
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
