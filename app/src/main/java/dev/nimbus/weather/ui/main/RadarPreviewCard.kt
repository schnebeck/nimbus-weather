/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/RadarPreviewCard.kt
 * The radar preview card: the stored base map with the newest radar picture; a tap opens the radar.
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
import dev.nimbus.weather.ui.radar.RadarPreview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import kotlinx.coroutines.launch
import dev.nimbus.weather.ui.radar.RadarSources
import kotlin.math.roundToInt

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
            LaunchedEffect(key) {
                val t0 = System.currentTimeMillis()
                fun lap(what: String) { if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusPreview", "${data.place.name}: $what after ${System.currentTimeMillis() - t0} ms") }
                // 1. at once: the stored base map and the last radar picture of this place
                launch {
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
                launch {
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
