package dev.nimbus.weather.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.alpha
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val PrecipBar = Color(0xB38FD3FF)
private val NightShade = Color(0x26000000)
private val GridLine = Color(0x1FFFFFFF)
private val ForecastLine = Color(0xFFFFC56B)
private const val CURSOR_TIMEOUT_MS = 10_000L

/** Wind colour by speed (km/h): calm white → Bft 6 yellow → gale orange → storm red. */
fun windColor(kmh: Double): Color = when {
    kmh < 39 -> Color.White
    kmh < 62 -> Color(0xFFFFE08A)
    kmh < 89 -> Color(0xFFFFA54A)
    else -> Color(0xFFFF5A4A)
}

/**
 * One hour of a meteogram. For the look back [temperature] etc. are measurements and
 * [forecastTemperature] / [forecastPrecipitation] what the model had predicted.
 */
data class MeteoPoint(
    val time: Long,
    val temperature: Double,
    val condition: Condition,
    val isDay: Boolean,
    val precipitation: Double?,
    val precipitationChance: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGust: Double? = null,
    val humidity: Double? = null,
    val apparentTemperature: Double? = null,
    val forecastTemperature: Double? = null,
    val forecastPrecipitation: Double? = null,
)

fun HourlyPoint.toMeteo() = MeteoPoint(
    time, temperature, condition, isDay, precipitation, precipitationProbability,
    windSpeed, windDirection, windGust, humidity, apparentTemperature,
)

/** Night intervals from sunrise/sunset of the daily forecast. */
fun nightsFromDaily(daily: List<DailyPoint>, start: Long, end: Long): List<LongRange> =
    daily.filter { it.date < end && it.date + 24 * 3_600_000L > start }.flatMap { d ->
        val rise = d.sunrise ?: return@flatMap emptyList()
        val set = d.sunset ?: return@flatMap emptyList()
        listOf(d.date until rise, set until d.date + 24 * 3_600_000L)
    }

/** Night intervals from the hourly day/night flag (when no sunrise/sunset is at hand). */
fun nightsFromFlags(points: List<MeteoPoint>): List<LongRange> =
    points.zipWithNext().filter { (a, b) -> !a.isDay && !b.isDay }.map { (a, b) -> a.time until b.time }

/**
 * Compact meteogram of [start]..[end] (one day 00–24 h): time labels and weather symbols every
 * 3 h, temperature (left axis; in comparison mode measured white and forecast dashed), hourly
 * precipitation (bars, right axis), wind arrows, night shading. A long press shows a cursor –
 * dragging moves it, it fades out after 10 s. Plain swipes stay free for paging.
 */
@Composable
fun Meteogram(points: List<MeteoPoint>, start: Long, end: Long, nights: List<LongRange>, now: Long, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pts = remember(points, start, end) { points.filter { it.time in start..end }.sortedBy { it.time } }
    if (pts.size < 2) return
    val compare = pts.any { it.forecastTemperature != null }
    val span = (end - start).toFloat()
    var selected by remember(start) {
        mutableStateOf(pts.indexOfLast { it.time <= now }.takeIf { now in start..end && it >= 0 } ?: pts.indexOfFirst { it.time >= start + 12 * 3_600_000L }.coerceAtLeast(0))
    }
    var cursorOn by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(touched, cursorOn) {
        if (cursorOn) { delay(CURSOR_TIMEOUT_MS); cursorOn = false }
    }
    val cursorAlpha by animateFloatAsState(if (cursorOn) 1f else 0f, tween(if (cursorOn) 150 else 700), label = "cursor")

    val temps = pts.map { Units.temperature(it.temperature, s.temperatureUnit) }
    val fTemps = pts.map { p -> p.forecastTemperature?.let { Units.temperature(it, s.temperatureUnit) } }
    val allT = temps + fTemps.filterNotNull()
    val tLo = floor(allT.min() - 1).toInt()
    // Even range, so the middle grid line is a whole degree as well.
    val tHi = ceil(allT.max() + 1).toInt().let { if (it - tLo < 4) tLo + 4 else it }.let { if ((it - tLo) % 2 == 1) it + 1 else it }
    val inch = s.precipitationUnit == PrecipitationUnit.INCH
    val precipMax = maxOf(if (inch) 0.04 else 1.0, pts.maxOf { Units.precipitationValue(it.precipitation ?: 0.0, s.precipitationUnit) })
        .let { if (inch) ceil(it * 20) / 20 else ceil(it) }
    val labelStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Tertiary)
    val unitStyle = TextStyle(fontSize = 10.sp, color = NimbusColors.Secondary, fontWeight = FontWeight.SemiBold)
    val tUnit = if (s.temperatureUnit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    // Symbols and wind arrows stand for 3-hour blocks and sit in their middle (01:30, 04:30 …):
    // the point closest to the block centre represents the block.
    val blocks = remember(pts, start, end) {
        generateSequence(start) { it + 3 * 3_600_000L }.takeWhile { it < end }.mapNotNull { b ->
            val centre = b + 90 * 60_000L
            pts.minByOrNull { kotlin.math.abs(it.time - centre) }?.let { centre to it }
        }.toList()
    }

    // Vertical layout: time labels | symbols | plot | wind arrows. Units sit in the top corners,
    // the axes carry numbers only and are exactly as wide as their widest label.
    fun precipLabel(k: Int): String {
        val pv = precipMax * k / 2
        return when {
            k == 0 -> "0"
            inch -> String.format(Locale.getDefault(), "%.2f", pv)
            else -> Units.oneDecimal(pv)
        }
    }
    val gap = 3.dp
    val axisL = with(density) {
        (0..2).maxOf { k -> measurer.measure("${(tLo + (tHi - tLo) * k / 2.0).roundToInt()}", labelStyle).size.width }.toDp()
    } + gap
    // Dry day: no precipitation axis, the plot grows to the right (only half an hour label stays free).
    val wet = pts.any { (it.precipitation ?: 0.0) > 0.0 || (it.forecastPrecipitation ?: 0.0) > 0.0 }
    val axisR = with(density) {
        if (wet) maxOf((0..2).maxOf { measurer.measure(precipLabel(it), labelStyle).size.width }, measurer.measure(pUnit, unitStyle).size.width).toDp() + gap
        else (measurer.measure(tf.hour(end), labelStyle).size.width / 2).toDp()
    }
    val labelsH = 14.dp
    val iconsH = 30.dp
    val plotH = 120.dp
    val windH = 26.dp

    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(labelsH + iconsH + plotH + windH)) {
            val plotW = maxWidth - axisL - axisR
            fun xDp(t: Long) = axisL + plotW * ((t - start) / span)
            fun indexAt(xPx: Float): Int {
                val x = with(density) { xPx.toDp() }
                val t = start + ((x - axisL) / plotW).coerceIn(0f, 1f) * span
                return pts.indices.minBy { kotlin.math.abs(pts[it].time - t) }
            }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(pts) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { cursorOn = true; selected = indexAt(it.x); touched++ },
                        ) { change, _ ->
                            change.consume()
                            selected = indexAt(change.position.x)
                            touched++
                        }
                    }
                    .pointerInput(pts) {
                        detectTapGestures(onTap = { if (cursorOn) { selected = indexAt(it.x); touched++ } })
                    },
            ) {
                val l = axisL.toPx(); val r = size.width - axisR.toPx()
                val top = (labelsH + iconsH).toPx(); val bottom = top + plotH.toPx()
                fun x(t: Long) = l + (r - l) * ((t - start) / span)
                fun yT(v: Double) = (bottom - (v - tLo) / (tHi - tLo) * (bottom - top)).toFloat()
                fun yP(v: Double) = (bottom - v / precipMax * (bottom - top)).toFloat()

                nights.forEach { n ->
                    val a = maxOf(n.first, start); val b = minOf(n.last + 1, end)
                    if (b > a) drawRect(NightShade, Offset(x(a), top), Size(x(b) - x(a), bottom - top + windH.toPx()))
                }
                // Units in the top corners (free: symbols sit in the block middles), numbers on the axes
                val ut = measurer.measure(tUnit, unitStyle)
                drawText(ut, topLeft = Offset(0f, top - ut.size.height - 3.dp.toPx()))
                if (wet) {
                    val up = measurer.measure(pUnit, unitStyle)
                    drawText(up, topLeft = Offset(size.width - up.size.width, top - up.size.height - 3.dp.toPx()))
                }
                for (k in 0..2) {
                    val v = tLo + (tHi - tLo) * k / 2.0
                    drawLine(GridLine, Offset(l, yT(v)), Offset(r, yT(v)), 1f)
                    val lt = measurer.measure("${v.roundToInt()}", labelStyle)
                    drawText(lt, topLeft = Offset(l - lt.size.width - gap.toPx(), yT(v) - lt.size.height / 2f))
                    if (wet) {
                        val lp = measurer.measure(precipLabel(k), labelStyle)
                        drawText(lp, topLeft = Offset(r + gap.toPx(), yP(precipMax * k / 2) - lp.size.height / 2f))
                    }
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
                // Forecast (comparison mode): dashed
                if (compare) {
                    val fp = Path()
                    var started = false
                    pts.forEachIndexed { i, h ->
                        val v = fTemps[i] ?: run { started = false; return@forEachIndexed }
                        if (!started) { fp.moveTo(x(h.time), yT(v)); started = true } else fp.lineTo(x(h.time), yT(v))
                    }
                    drawPath(fp, ForecastLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
                }
                // Temperature curve (measured in comparison mode: white)
                val path = Path()
                pts.forEachIndexed { i, h -> if (i == 0) path.moveTo(x(h.time), yT(temps[i])) else path.lineTo(x(h.time), yT(temps[i])) }
                val brush = if (compare) Brush.linearGradient(listOf(Color.White, Color.White)) else Brush.verticalGradient(
                    listOf(Insights.temperatureColor(pts.maxOf { it.temperature }), Insights.temperatureColor(pts.minOf { it.temperature })),
                    startY = top, endY = bottom,
                )
                drawPath(path, brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                // Wind arrows every 3 h, pointing where the wind blows to
                val windY = bottom + windH.toPx() / 2
                blocks.forEach { (centre, h) ->
                    val dir = h.windDirection ?: return@forEach
                    val kmh = h.windSpeed ?: 0.0
                    val len = (7 + (kmh / 50.0).coerceAtMost(1.0) * 6).dp.toPx()
                    val c = Offset(x(centre), windY)
                    rotate((dir + 180).toFloat(), c) {
                        val tip = c + Offset(0f, -len)
                        drawLine(windColor(kmh), c + Offset(0f, len), tip, 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(-3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                        drawLine(windColor(kmh), tip, tip + Offset(3.5.dp.toPx(), 4.5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                    }
                }
                // Cursor (long press)
                if (cursorAlpha > 0f) {
                    val i = selected.coerceIn(0, pts.lastIndex)
                    val xs = x(pts[i].time)
                    drawLine(Color.White.copy(alpha = 0.85f * cursorAlpha), Offset(xs, top - 2.dp.toPx()), Offset(xs, bottom + windH.toPx()), 1.5.dp.toPx())
                    drawCircle(Color(0xFF1A2A40).copy(alpha = cursorAlpha), 5.5.dp.toPx(), Offset(xs, yT(temps[i])))
                    drawCircle(Color.White.copy(alpha = cursorAlpha), 3.5.dp.toPx(), Offset(xs, yT(temps[i])))
                }
            }
            // Weather symbols every 3 h
            blocks.forEach { (centre, h) ->
                WeatherIcon(h.condition, h.isDay, size = 22.dp, modifier = Modifier.offset(x = xDp(centre) - 11.dp, y = labelsH + 4.dp))
            }
        }
        Readout(pts[selected.coerceIn(0, pts.lastIndex)], pts, highlighted = cursorOn)
        // Always laid out (only faded), so the card does not change height with the cursor
        Text(
            stringResource(R.string.meteogram_hint), fontSize = 11.sp, color = NimbusColors.Tertiary,
            modifier = Modifier.padding(top = 4.dp).alpha(1f - cursorAlpha),
        )
    }
}

/** The three text lines of the readout for one hour. */
@Composable
private fun readoutLines(h: MeteoPoint): List<String> {
    val s = LocalSettings.current
    val dirs = Texts.compass.map { stringResource(it) }
    val wUnit = stringResource(Texts.windUnit(s.windUnit))
    val pUnit = stringResource(Texts.precipUnit(s.precipitationUnit))
    val humidity = stringResource(R.string.humidity)
    val forecastWord = stringResource(R.string.forecast)
    val first = buildString {
        append(Units.temp(h.temperature, s.temperatureUnit)).append(" · ").append(stringResource(Texts.condition(h.condition, h.isDay)))
        when {
            h.forecastTemperature != null -> append(" · ").append(forecastWord).append(' ').append(Units.temp(h.forecastTemperature, s.temperatureUnit))
            h.apparentTemperature != null -> append(" · ").append(stringResource(R.string.feels_like_short, Units.temp(h.apparentTemperature, s.temperatureUnit)))
        }
    }
    val precip = buildString {
        append(stringResource(R.string.precipitation)).append(' ')
        append(Units.precipitationNumber(h.precipitation ?: 0.0, s.precipitationUnit)).append(NBSP).append(pUnit)
        h.precipitationChance?.let { append(" · ").append(Insights.chanceLabel(it)).append(NBSP).append('%') }
        h.forecastPrecipitation?.let {
            append(" · ").append(forecastWord).append(' ').append(Units.precipitationNumber(it, s.precipitationUnit)).append(NBSP).append(pUnit)
        }
    }
    val wind = buildString {
        append(stringResource(R.string.wind)).append(' ').append(Units.windNumber(h.windSpeed, s.windUnit)).append(NBSP).append(wUnit)
        h.windDirection?.let { append(' ').append(stringResource(R.string.from_direction, dirs[Units.compassIndex(it)])) }
        h.windGust?.let { append(" · ").append(stringResource(R.string.gusts)).append(' ').append(Units.windNumber(it, s.windUnit)) }
        h.humidity?.let { append(" · ").append(humidity).append(' ').append(it.roundToInt()).append(NBSP).append('%') }
    }
    return listOf(first, precip, wind)
}

/**
 * Values at the cursor position. The longest line of every kind is laid out invisibly
 * underneath, so the card keeps its height while the cursor moves (no jumping list).
 */
@Composable
private fun Readout(h: MeteoPoint, all: List<MeteoPoint>, highlighted: Boolean) {
    val tf = LocalTimeFormat.current
    val lines = readoutLines(h)
    val longest = all.map { readoutLines(it) }.let { ls -> (0..2).map { k -> ls.maxBy { it[k].length }[k] } }
    val sizes = listOf(14.sp, 13.sp, 13.sp)
    val colors = listOf(Color.White, NimbusColors.Secondary, NimbusColors.Secondary)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tf.time(h.time), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (highlighted) Color.White else NimbusColors.Secondary,
            )
            WeatherIcon(h.condition, h.isDay, size = 26.dp)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            Column(Modifier.alpha(0f)) { longest.forEachIndexed { k, t -> Text(t, fontSize = sizes[k]) } }
            Column { lines.forEachIndexed { k, t -> Text(t, fontSize = sizes[k], color = colors[k]) } }
        }
    }
}

/** Inner padding of the glass cards the meteogram may extend into (for a wider plot). */
val CARD_BLEED = 10.dp

/** Extends the element by [amount] on both sides beyond the parent's padding. */
fun Modifier.bleed(amount: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = amount.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    },
)
