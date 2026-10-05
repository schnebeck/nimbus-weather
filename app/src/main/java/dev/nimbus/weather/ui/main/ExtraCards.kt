/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ExtraCards.kt
 * Citizen sensor card, model comparison, radar preview and the data sources footer.
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.cancel
import dev.nimbus.weather.ui.radar.RadarPreview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import dev.nimbus.weather.ui.radar.RadarSources
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------
// Community sensors

@Composable
fun CommunityCard(data: WeatherData) {
    val c = data.community ?: return
    val s = LocalSettings.current
    GlassCard(title = stringResource(R.string.community_sensors), icon = Icons.Outlined.Groups, info = Term.COMMUNITY) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            c.temperature?.let { Text(Units.temp(it, s.temperatureUnit), fontSize = 34.sp, color = Color.White) }
            Column(Modifier.padding(bottom = 4.dp)) {
                c.humidity?.let { Text("${stringResource(R.string.humidity)} ${it.roundToInt()}%", fontSize = 14.sp, color = Color.White) }
                c.pressure?.let { Text("${stringResource(R.string.pressure)} ${it.roundToInt()}${NBSP}hPa", fontSize = 14.sp, color = Color.White) }
                if (c.pm25 != null || c.pm10 != null) {
                    Text(stringResource(R.string.pm_values, c.pm25?.roundToInt()?.toString() ?: "–", c.pm10?.roundToInt()?.toString() ?: "–"), fontSize = 14.sp, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val detail = stringResource(R.string.community_sensors_detail, c.sensorCount, c.radiusKm.roundToInt().toString())
        val spread = c.temperatureSpread?.let { " · " + stringResource(R.string.community_spread, Units.oneDecimal(it / 2) + "°") } ?: ""
        Text(detail + spread, fontSize = 12.sp, color = NimbusColors.Secondary)
    }
}

// ---------------------------------------------------------------------------------------
// Model comparison

val ModelColors = listOf(
    Color(0xFF64B5F6), Color(0xFF4DD0E1), Color(0xFFFFB74D), Color(0xFFE57373),
    Color(0xFFBA68C8), Color(0xFF81C784), Color(0xFFF06292), Color(0xFFFFF176),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelComparisonCard(models: List<ModelSeries>?, now: Long, onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }
    val tf = LocalTimeFormat.current
    val s = LocalSettings.current
    var highlighted by remember { mutableStateOf<String?>(null) }
    val measurer = rememberTextMeasurer()
    GlassCard(title = stringResource(R.string.model_comparison), icon = Icons.Outlined.QueryStats, info = Term.MODELS) {
        Text(stringResource(R.string.model_comparison_hint), fontSize = 13.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.height(10.dp))
        if (models == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
            return@GlassCard
        }
        if (models.isEmpty()) {
            Text(stringResource(R.string.models_unavailable), fontSize = 14.sp, color = Color.White)
            return@GlassCard
        }
        val times = models.first().times
        val start = times.indexOfFirst { it >= now - 3600_000L }.coerceAtLeast(0)
        val end = (start + 72).coerceAtMost(times.size)
        val all = models.flatMap { m -> m.temperature.subList(start, end).filterNotNull() }
        if (all.isEmpty()) return@GlassCard
        val lo = floor(Units.temperature(all.min(), s.temperatureUnit) - 1)
        val hi = Units.temperature(all.max(), s.temperatureUnit) + 1
        Row {
            Column(Modifier.height(170.dp).width(30.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${hi.roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
                Text("${((hi + lo) / 2).roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
                Text("${lo.roundToInt()}°", fontSize = 11.sp, color = NimbusColors.Tertiary)
            }
            Canvas(Modifier.weight(1f).height(190.dp)) {
                val n = (end - start).coerceAtLeast(2)
                val dx = size.width / (n - 1)
                val labelBand = 20.dp.toPx()
                val plotH = size.height - labelBand
                fun y(v: Double) = (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
                for (k in 0..4) {
                    val yy = plotH * k / 4
                    drawLine(Color(0x1FFFFFFF), Offset(0f, yy), Offset(size.width, yy), 1f)
                }
                // midnight separators and a weekday label centred on each day
                val midnights = (start until end).filter { tf.zoned(times[it]).hour == 0 }
                midnights.forEach { i ->
                    val x = (i - start) * dx
                    drawLine(Color(0x40FFFFFF), Offset(x, 0f), Offset(x, plotH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                }
                val bounds = (listOf(start) + midnights + listOf(end - 1)).distinct()
                bounds.zipWithNext().forEach { (a, b) ->
                    if (b - a < 4) return@forEach
                    val layout = measurer.measure(tf.weekdayShort(times[a] + 3_600_000L), TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary))
                    val cx = ((a + b) / 2f - start) * dx
                    drawText(layout, topLeft = Offset(axisLabelLeft(cx, layout.size.width, size.width), plotH + 4.dp.toPx()))
                }
                // now marker
                val nowX = ((now - times[start]) / 3600_000f) * dx
                drawLine(Color.White.copy(alpha = 0.6f), Offset(nowX, 0f), Offset(nowX, plotH), 1.5f)
                models.forEachIndexed { mi, m ->
                    val path = Path()
                    var started = false
                    for (i in start until end) {
                        val v = m.temperature.getOrNull(i) ?: continue
                        val p = Offset((i - start) * dx, y(Units.temperature(v, s.temperatureUnit)))
                        if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                    }
                    val isHi = highlighted == null || highlighted == m.modelId
                    drawPath(
                        path, ModelColors[mi % ModelColors.size], alpha = if (isHi) 0.95f else 0.18f,
                        style = Stroke(width = (if (highlighted == m.modelId) 3.dp else 1.8.dp).toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEachIndexed { mi, m ->
                val selected = highlighted == m.modelId
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0x40FFFFFF) else Color(0x1AFFFFFF))
                        .clickable { highlighted = if (selected) null else m.modelId }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ModelColors[mi % ModelColors.size]))
                    Spacer(Modifier.width(5.dp))
                    Text(m.label, fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Radar preview (static map + latest radar), tap opens the full radar

@Composable
fun RadarPreviewCard(data: WeatherData, onOpen: () -> Unit) {
    val context = LocalContext.current
    val http = remember { (context.applicationContext as NimbusApp).container.http }
    val mapHttp = remember { (context.applicationContext as NimbusApp).container.mapHttp }
    val density = LocalDensity.current
    val tf = LocalTimeFormat.current
    var radarTime by remember { mutableStateOf<Long?>(null) }
    GlassCard(title = stringResource(R.string.precipitation_map), icon = Icons.Outlined.Map, onClick = onOpen, contentPadding = false, info = Term.RADAR) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))) {
            val wDp = maxWidth.value.toInt()
            val hDp = maxHeight.value.toInt()
            val lat = data.place.latitude
            val lon = data.place.longitude
            val lang = context.resources.configuration.locales[0].language
            val key = RadarPreview.key(lat, lon, wDp, hDp)
            SideEffect { RadarPreview.cardSize = wDp to hDp }
            var base by remember(key) { mutableStateOf<android.graphics.Bitmap?>(null) }
            var overlay by remember(key) { mutableStateOf<android.graphics.Bitmap?>(null) }
            var lines by remember(key) { mutableStateOf<android.graphics.Bitmap?>(null) }
            DisposableEffect(key) {
                val scope = kotlinx.coroutines.MainScope()
                val t0 = System.currentTimeMillis()
                fun lap(what: String) { if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPreview", "${data.place.name}: $what after ${System.currentTimeMillis() - t0} ms") }
                // 1. at once: the stored base map and the last radar picture of this place
                scope.launch {
                    RadarPreview.storedOverlay(key)?.let { o -> if (overlay == null) { overlay = o.bitmap; radarTime = o.time }; lap("stored radar") }
                    RadarPreview.storedLines(key, lang)?.let { lines = it }
                    RadarPreview.storedBase(key, lang)?.let { base = it; lap("stored base") }
                        ?: run {
                            // first time for this place and size: render the base map once
                            RadarPreview.renderBase(context, mapHttp, lat, lon, wDp, hDp, density.density, lang)?.let {
                                base = it; lines = RadarPreview.storedLines(key, lang); lap("rendered base")
                            }
                        }
                }
                // 2. meanwhile: the newest radar picture, drawn as in the radar loop
                scope.launch {
                    dev.nimbus.weather.ui.radar.RadarPrefetcher.previewStarted()
                    // rain/snow colours need the temperature grid – but never wait long for it
                    kotlinx.coroutines.withTimeoutOrNull(3_000L) { dev.nimbus.weather.ui.radar.WeatherGridStore.ensure(http, lat, lon) }
                    val anchor = dev.nimbus.weather.ui.radar.RadarComposites.anchorFor(lat, lon)
                    val timeline = runCatching { RadarSources.timeline(http, anchor = anchor) }.getOrNull()
                    val frame = timeline?.frames?.getOrNull(timeline.nowIndex)
                    lap("timeline")
                    if (timeline != null && frame != null) RadarPreview.fetchOverlay(http, timeline, frame, lat, lon, wDp, hDp)?.let { o ->
                        overlay = o.bitmap; radarTime = o.time; lap("radar")
                    }
                    dev.nimbus.weather.ui.radar.RadarPrefetcher.previewRendered()
                }
                onDispose { scope.cancel() }
            }
            val bmp = base
            val radar = overlay
            Canvas(Modifier.fillMaxSize().background(Color(0xFF55657A))) {
                val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                if (bmp != null) {
                    drawImage(bmp.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height), dstOffset = IntOffset.Zero, dstSize = dst)
                }
                // Radar on top, as in the radar screen (85 % opacity); 1 px per dp, smoothly scaled
                if (radar != null) {
                    drawImage(
                        radar.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(radar.width, radar.height),
                        dstOffset = IntOffset.Zero, dstSize = dst,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    )
                }
                // Roads, borders and names above the radar, as on the radar screen
                val ln = lines
                if (ln != null) {
                    drawImage(ln.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(ln.width, ln.height), dstOffset = IntOffset.Zero, dstSize = dst)
                }
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color(0x553D8BFF), 16.dp.toPx(), c)
                drawCircle(Color.White, 7.dp.toPx(), c)
                drawCircle(Color(0xFF3D8BFF), 5.dp.toPx(), c)
            }
            if (bmp == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
            radarTime?.let {
                Text(
                    tf.time(it), Modifier.align(Alignment.TopStart).padding(10.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0x8C1C2636)).padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.open_radar) + " ›",
                // Top right: the bottom edge carries the map attribution.
                Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0x8C1C2636)).padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Sources footer

@Composable
fun SourcesFooter(data: WeatherData) {
    val tf = LocalTimeFormat.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Outlined.Info, null, tint = NimbusColors.Secondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.data_sources), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NimbusColors.Secondary)
        }
        Spacer(Modifier.height(4.dp))
        // a single model with its grid: "KNMI Harmonie, 2 km"
        @Composable
        fun part(p: dev.nimbus.weather.data.model.ModelPart) =
            stringResource(R.string.src_part, p.name, if (p.km % 1.0 == 0.0) p.km.toInt().toString() else Units.oneDecimal(p.km))
        val labels = data.sources.map { src ->
            when (src.kind) {
                SourceKind.MODEL_DWD_ICON -> stringResource(R.string.src_dwd_icon)
                SourceKind.MODEL_ECMWF -> stringResource(R.string.src_ecmwf)
                SourceKind.MODEL_METEO_FRANCE -> stringResource(R.string.src_meteofrance)
                SourceKind.MODEL_REGIONAL -> src.part?.let { stringResource(R.string.src_regional, part(it)) } ?: stringResource(R.string.src_best_match)
                SourceKind.MODEL_BEST_MATCH -> stringResource(R.string.src_best_match) +
                    (src.part?.let { " (" + stringResource(R.string.src_part_now, part(it)) + ")" } ?: "")
                // filling what the chosen model lacks – and from its last hour on, everything
                SourceKind.GAP_FILL -> (src.since?.let { stringResource(R.string.src_gap_fill_from, tf.weekdayShort(it) + " " + tf.time(it)) }
                    ?: stringResource(R.string.src_gap_fill)) + (src.part?.let { " (" + part(it) + ")" } ?: "")
                SourceKind.DWD_STATION, SourceKind.STATION -> (src.network ?: dev.nimbus.weather.data.model.StationNetwork.DWD).let { n ->
                    stringResource(R.string.src_station, n.label, src.detail ?: "", n.provider)
                }
                SourceKind.DWD_WARNINGS -> stringResource(R.string.src_warnings)
                SourceKind.CAMS -> stringResource(R.string.src_cams)
                SourceKind.COMMUNITY -> stringResource(R.string.src_community)
                SourceKind.DWD_POLLEN -> stringResource(R.string.src_dwd_pollen)
                SourceKind.PEGELONLINE -> stringResource(R.string.src_pegelonline, titleCase(src.detail ?: ""))
                SourceKind.NLWKN -> stringResource(R.string.src_nlwkn, src.detail ?: "")
                SourceKind.GAUGES -> stringResource(
                    R.string.src_gauges,
                    src.detail.orEmpty().split(",").mapNotNull { n -> runCatching { providerName(dev.nimbus.weather.data.model.GaugeProvider.valueOf(n)) }.getOrNull() }.joinToString(", "),
                )
                SourceKind.LHP_ALERTS -> stringResource(R.string.src_lhp_alerts)
                SourceKind.BATHING -> stringResource(R.string.src_bathing, src.detail?.takeIf { it.isNotBlank() }?.let { ", " + it.replace(",", ", ") } ?: "")
            }
        } + stringResource(R.string.src_moon)
        Text(
            labels.joinToString(" · "), fontSize = 11.sp, color = NimbusColors.Secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.updated_at, tf.time(data.fetchedAt)), fontSize = 11.sp, color = NimbusColors.Tertiary)
    }
}
