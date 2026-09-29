package dev.nimbus.weather.ui.main

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
import dev.nimbus.weather.ui.components.drawMoonPhase
import dev.nimbus.weather.util.Moon
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
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { m -> FeelsLikeTile(data, m) }
        add { m -> UvTile(data, hours, m) }
        add { m -> WindTile(data, m) }
        if (c.humidity != null) add { m -> HumidityTile(data, m) }
        if (c.visibility != null) add { m -> VisibilityTile(data, m) }
        if (c.pressure != null) add { m -> PressureTile(data, hours, m) }
        add { m -> SunTile(data, now, m) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A single tile in the last row spans the full width instead of leaving a gap.
                if (row.size == 1) row[0](Modifier.fillMaxWidth().aspectRatio(2f))
                else row.forEach { tile -> tile(Modifier.weight(1f).aspectRatio(1f)) }
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
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
fun PrecipitationCard(data: WeatherData, now: Long) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val hours = remember(data, now) { Insights.upcomingHours(data, now).drop(1).take(24) }
    if (hours.isEmpty()) return
    val unit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
    val next = hours.sumOf { it.precipitation ?: 0.0 }
    val peak = hours.maxByOrNull { it.precipitationProbability ?: 0.0 }
    val peakChance = peak?.precipitationProbability ?: 0.0
    GlassCard(title = stringResource(R.string.precip_24h), icon = Icons.Outlined.WaterDrop, info = Term.PRECIP_PROBABILITY) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.precip_today_amount, Units.precipitationNumber(today?.precipitationSum ?: 0.0, s.precipitationUnit) + NBSP + unit),
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
            )
        }
        Text(stringResource(R.string.precip_next_amount, Units.precipitationNumber(next, s.precipitationUnit) + NBSP + unit), fontSize = 14.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.height(4.dp))
        Text(
            if (peak != null && peakChance >= 10) stringResource(R.string.precip_max_chance, peakChance.roundToInt(), tf.time(peak.time))
            else stringResource(R.string.precip_no_chance),
            fontSize = 14.sp, color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
        // Axis labels are drawn in the chart itself so they sit exactly on grid lines and bars.
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val axisW = 36.dp.toPx()
            val top = 6.dp.toPx()
            val plotH = size.height - 20.dp.toPx() - top
            val plotW = size.width - axisW
            val n = hours.size
            val bw = plotW / n
            for (k in 0..2) {
                val y = top + plotH * k / 2
                drawLine(Color(0x22FFFFFF), Offset(axisW, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                val l = measurer.measure("${100 - 50 * k}${NBSP}%", labelStyle)
                drawText(l, topLeft = Offset(axisW - l.size.width - 6.dp.toPx(), y - l.size.height / 2f))
            }
            hours.forEachIndexed { i, h ->
                val p = ((h.precipitationProbability ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                val amount = (h.precipitation ?: 0.0).toFloat()
                // light blue for a mere chance, saturated blue for substantial amounts
                val strength = (amount / 2f).coerceIn(0f, 1f)
                val color = androidx.compose.ui.graphics.lerp(Color(0x6690C8FF), Color(0xFF3D8BFF), strength)
                val bh = plotH * p
                if (bh > 0.5f) drawRoundRect(color, Offset(axisW + i * bw + bw * 0.15f, top + plotH - bh), Size(bw * 0.7f, bh), CornerRadius(2.dp.toPx()))
                if (i % 6 == 0) {
                    val l = measurer.measure(tf.hour(h.time), labelStyle)
                    val cx = axisW + i * bw + bw / 2
                    drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceAtMost(size.width - l.size.width), top + plotH + 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.precip_chart_hint), fontSize = 11.sp, color = NimbusColors.Tertiary)
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

@Composable
private fun SunTile(data: WeatherData, now: Long, modifier: Modifier) {
    val tf = LocalTimeFormat.current
    val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
    val tomorrow = data.daily.firstOrNull { it.date > now }
    val rise = today?.sunrise
    val set = today?.sunset
    val upcomingIsSunset = rise != null && set != null && now in rise until set
    val nextEvent = when {
        rise == null || set == null -> null
        now < rise -> rise
        now < set -> set
        else -> tomorrow?.sunrise
    }
    val title = stringResource(if (upcomingIsSunset) R.string.sunset else R.string.sunrise)
    Tile(title, Icons.Outlined.WbTwilight, modifier) {
        if (nextEvent == null) {
            Caption(stringResource(if (data.current.isDay) R.string.polar_day else R.string.polar_night))
            return@Tile
        }
        BigValue(tf.time(nextEvent))
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp)) {
            val w = size.width
            val h = size.height
            val horizon = h * 0.6f
            // Sine-shaped sun path over the day.
            val path = Path()
            val steps = 60
            for (i in 0..steps) {
                val x = w * i / steps
                val y = horizon - sin((i.toFloat() / steps) * 2 * PI - PI / 2).toFloat() * h * 0.36f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x33FFFFFF)), 0f, h), style = Stroke(1.6.dp.toPx()))
            drawLine(Color(0x88FFFFFF), Offset(0f, horizon), Offset(w, horizon), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            if (rise != null && set != null) {
                // 24 h on the x axis: sunrise and sunset hit the horizon at 1/4 and 3/4 width,
                // the night part wraps around the edges.
                val dayLen = (set - rise).toFloat()
                val nightLen = (24 * 3600_000f - dayLen).coerceAtLeast(1f)
                val fc = when {
                    now < rise -> (0.25f - 0.5f * ((rise - now) / nightLen)).let { if (it < 0f) it + 1f else it }
                    now > set -> (0.75f + 0.5f * ((now - set) / nightLen)).let { if (it > 1f) it - 1f else it }
                    else -> 0.25f + 0.5f * ((now - rise) / dayLen)
                }
                val x = w * fc
                val y = horizon - sin(fc * 2 * PI - PI / 2).toFloat() * h * 0.36f
                drawCircle(Color.White, 5.dp.toPx(), Offset(x, y))
                drawCircle(Color(0x55FFFFFF), 9.dp.toPx(), Offset(x, y))
            }
        }
        val other = if (upcomingIsSunset) rise else set
        if (other != null) Caption(stringResource(if (upcomingIsSunset) R.string.sunrise_at else R.string.sunset_at, tf.time(if (upcomingIsSunset) (tomorrow?.sunrise ?: other) else other)))
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
