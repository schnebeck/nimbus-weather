package dev.nimbus.weather.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.Units
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val PrecipBar = Color(0xB38FD3FF)
private val NightShade = Color(0x26000000)
private val GridLine = Color(0x1FFFFFFF)

/** Wind colour by speed (km/h): calm white → Bft 6 yellow → gale orange → storm red. */
fun windColor(kmh: Double): Color = when {
    kmh < 39 -> Color.White
    kmh < 62 -> Color(0xFFFFE08A)
    kmh < 89 -> Color(0xFFFFA54A)
    else -> Color(0xFFFF5A4A)
}

/**
 * Compact meteogram of [start]..[end] (e.g. one day 00–24 h): time labels and weather symbols
 * every 3 h, temperature curve (left axis), precipitation per hour (bars, right axis), wind
 * arrows, night shading. Tapping or dragging moves a cursor; its values are shown below.
 */
@Composable
fun Meteogram(hours: List<HourlyPoint>, start: Long, end: Long, daily: List<DailyPoint>, now: Long, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pts = remember(hours, start, end) { hours.filter { it.time in start..end }.sortedBy { it.time } }
    if (pts.size < 2) return
    val span = (end - start).toFloat()
    // Cursor: "now" if it lies in the window, else noon.
    var selected by remember(start) {
        mutableStateOf(pts.indexOfLast { it.time <= now }.takeIf { now in start..end } ?: pts.indexOfFirst { it.time >= start + 12 * 3_600_000L }.coerceAtLeast(0))
    }
    val temps = pts.map { Units.temperature(it.temperature, s.temperatureUnit) }
    val tLo = floor(temps.min() - 1).toInt()
    // Even range, so the middle grid line is a whole degree as well.
    val tHi = ceil(temps.max() + 1).toInt().let { if (it - tLo < 4) tLo + 4 else it }.let { if ((it - tLo) % 2 == 1) it + 1 else it }
    val precipMax = maxOf(1.0, pts.maxOf { Units.precipitationValue(it.precipitation ?: 0.0, s.precipitationUnit) })
        .let { if (s.precipitationUnit == dev.nimbus.weather.data.model.PrecipitationUnit.MM) ceil(it) else ceil(it * 10) / 10 }
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))

    // Vertical layout (dp): time labels | symbols | plot | wind arrows
    val axisL = 30.dp
    val axisR = 46.dp
    val labelsH = 14.dp
    val iconsH = 30.dp
    val plotH = 120.dp
    val windH = 26.dp

    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(labelsH + iconsH + plotH + windH)) {
            val totalW = maxWidth
            val plotW = totalW - axisL - axisR
            fun xDp(t: Long) = axisL + plotW * ((t - start) / span)
            fun indexAt(xPx: Float): Int {
                val x = with(density) { xPx.toDp() }
                val t = start + ((x - axisL) / plotW).coerceIn(0f, 1f) * span
                return pts.indices.minBy { kotlin.math.abs(pts[it].time - t) }
            }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(pts) { detectTapGestures { selected = indexAt(it.x) } }
                    .pointerInput(pts) {
                        detectHorizontalDragGestures(onDragStart = { selected = indexAt(it.x) }) { change, _ ->
                            change.consume()
                            selected = indexAt(change.position.x)
                        }
                    },
            ) {
                val l = axisL.toPx(); val r = size.width - axisR.toPx()
                val top = (labelsH + iconsH).toPx(); val bottom = top + plotH.toPx()
                fun x(t: Long) = l + (r - l) * ((t - start) / span)
                fun yT(v: Double) = (bottom - (v - tLo) / (tHi - tLo) * (bottom - top)).toFloat()
                fun yP(v: Double) = (bottom - v / precipMax * (bottom - top)).toFloat()

                // Night shading from sunrise/sunset
                var t = start
                while (t < end) {
                    val d = daily.lastOrNull { it.date <= t }
                    val rise = d?.sunrise; val set = d?.sunset
                    val nextDay = t - (t - (d?.date ?: t)) + 24 * 3_600_000L
                    if (rise != null && set != null) {
                        listOf(maxOf(t, d.date) to minOf(rise, end), maxOf(set, start) to minOf(nextDay, end)).forEach { (a, b) ->
                            if (b > a) drawRect(NightShade, Offset(x(a), top), Size(x(b) - x(a), bottom - top + windH.toPx()))
                        }
                    }
                    t = nextDay
                }
                // Grid: temperature lines and 3-hourly verticals with labels
                for (k in 0..2) {
                    val v = tLo + (tHi - tLo) * k / 2.0
                    drawLine(GridLine, Offset(l, yT(v)), Offset(r, yT(v)), 1f)
                    val lt = measurer.measure("${v.roundToInt()}°", labelStyle)
                    drawText(lt, topLeft = Offset(l - lt.size.width - 4.dp.toPx(), yT(v) - lt.size.height / 2f))
                    // precipitation axis on the right, ticks on the same grid lines
                    val pv = precipMax * k / 2
                    val lp = measurer.measure((if (k == 0) "0" else Units.oneDecimal(pv)) + NBSP + pUnit, labelStyle)
                    drawText(lp, topLeft = Offset(r + 4.dp.toPx(), yP(pv) - lp.size.height / 2f))
                }
                var mark = start
                while (mark <= end) {
                    val xm = x(mark)
                    drawLine(GridLine, Offset(xm, top), Offset(xm, bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                    val lt = measurer.measure(tf.hour(mark), labelStyle)
                    drawText(lt, topLeft = Offset((xm - lt.size.width / 2f).coerceIn(0f, size.width - lt.size.width), 0f))
                    mark += 3 * 3_600_000L
                }
                // Precipitation bars (sum of the hour before the timestamp)
                val hourW = (r - l) / (span / 3_600_000f)
                pts.forEach { h ->
                    val p = Units.precipitationValue(h.precipitation ?: 0.0, s.precipitationUnit)
                    if (p > 0.0) {
                        val xb = x(h.time) - hourW
                        drawRoundRect(PrecipBar, Offset(xb + hourW * 0.12f, yP(p)), Size(hourW * 0.76f, bottom - yP(p)), CornerRadius(2.dp.toPx()))
                    }
                }
                // Temperature curve
                val path = Path()
                pts.forEachIndexed { i, h -> if (i == 0) path.moveTo(x(h.time), yT(temps[i])) else path.lineTo(x(h.time), yT(temps[i])) }
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(Insights.temperatureColor(pts.maxOf { it.temperature }), Insights.temperatureColor(pts.minOf { it.temperature })),
                        startY = top, endY = bottom,
                    ),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                // Wind arrows every 3 h, pointing where the wind blows to
                val windY = bottom + windH.toPx() / 2
                pts.filter { tf.zoned(it.time).hour % 3 == 0 && it.time < end }.forEach { h ->
                    val dir = h.windDirection ?: return@forEach
                    val kmh = h.windSpeed ?: 0.0
                    val len = (7 + (kmh / 50.0).coerceAtMost(1.0) * 6).dp.toPx()
                    val c = Offset(x(h.time), windY)
                    rotate((dir + 180).toFloat(), c) {
                        val tip = c + Offset(0f, -len)
                        drawLine(windColor(kmh), c + Offset(0f, len), tip, 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(-3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                    }
                }
                // Cursor
                val sel = pts[selected.coerceIn(0, pts.lastIndex)]
                val xs = x(sel.time)
                drawLine(Color.White.copy(alpha = 0.85f), Offset(xs, top - 2.dp.toPx()), Offset(xs, bottom + windH.toPx()), 1.5.dp.toPx())
                drawCircle(Color(0xFF1A2A40), 5.5.dp.toPx(), Offset(xs, yT(temps[selected.coerceIn(0, pts.lastIndex)])))
                drawCircle(Color.White, 3.5.dp.toPx(), Offset(xs, yT(temps[selected.coerceIn(0, pts.lastIndex)])))
            }
            // Weather symbols every 3 h
            pts.filter { tf.zoned(it.time).hour % 3 == 0 && it.time < end }.forEach { h ->
                WeatherIcon(
                    h.condition, h.isDay, size = 22.dp,
                    modifier = Modifier.offset(x = xDp(h.time) - 11.dp, y = labelsH + 4.dp),
                )
            }
        }
        Readout(pts[selected.coerceIn(0, pts.lastIndex)])
    }
}

/** Values at the cursor position. */
@Composable
private fun Readout(h: HourlyPoint) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val dirs = Texts.compass.map { stringResource(it) }
    val wUnit = stringResource(Texts.windUnit(s.windUnit))
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val humidity = stringResource(R.string.humidity)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tf.time(h.time), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            WeatherIcon(h.condition, h.isDay, size = 26.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Units.temp(h.temperature, s.temperatureUnit) + " · " + stringResource(Texts.condition(h.condition, h.isDay)) +
                    (h.apparentTemperature?.let { " · " + stringResource(R.string.feels_like_short, Units.temp(it, s.temperatureUnit)) } ?: ""),
                fontSize = 14.sp, color = Color.White,
            )
            val precip = buildString {
                append(stringResource(R.string.precipitation)).append(' ')
                append(Units.precipitationNumber(h.precipitation ?: 0.0, s.precipitationUnit)).append(NBSP).append(pUnit)
                h.precipitationProbability?.let { append(" · ").append(Insights.chanceLabel(it)).append(NBSP).append('%') }
            }
            Text(precip, fontSize = 13.sp, color = NimbusColors.Secondary)
            val wind = buildString {
                append(stringResource(R.string.wind)).append(' ').append(Units.windNumber(h.windSpeed, s.windUnit)).append(NBSP).append(wUnit)
                h.windDirection?.let { append(' ').append(stringResource(R.string.from_direction, dirs[Units.compassIndex(it)])) }
                h.windGust?.let { append(" · ").append(stringResource(R.string.gusts)).append(' ').append(Units.windNumber(it, s.windUnit)) }
                h.humidity?.let { append(" · ").append(humidity).append(' ').append(it.roundToInt()).append(NBSP).append('%') }
            }
            Text(wind, fontSize = 13.sp, color = NimbusColors.Secondary)
        }
    }
}
