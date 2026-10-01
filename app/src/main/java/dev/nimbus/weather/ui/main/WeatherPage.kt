/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/WeatherPage.kt
 * One place's weather page: collapsing header and all cards below it.
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

import dev.nimbus.weather.data.model.WeatherCard
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.Demo
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.background.WeatherBackground
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.components.windiness
import androidx.compose.foundation.layout.width
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay
import java.util.Calendar

val LocalSettings = staticCompositionLocalOf { Settings() }
/** Changes the settings from anywhere below the root (e.g. the pollen card's type picker). */
val LocalSettingsUpdater = staticCompositionLocalOf<((Settings) -> Settings) -> Unit> { {} }
val LocalTimeFormat = staticCompositionLocalOf { TimeFormat("UTC", true) }

private val ExpandedHeader = 316.dp
private val CollapsedHeader = 118.dp
/** Top of the city name, below the top bar with menu and radar buttons. */
val HeaderTop = 58.dp

/** Applies demo overrides (visual QA) to the real weather. */
fun WeatherData.withDemo(demo: Demo?): WeatherData {
    if (demo == null) return this
    var c = current
    if (demo.condition != null) c = c.copy(condition = demo.condition, isDay = !demo.night)
    if (demo.wind != null) c = c.copy(windSpeed = kotlin.math.abs(demo.wind) * 60.0, windGust = kotlin.math.abs(demo.wind) * 85.0)
    return copy(current = c)
}

fun SkyScene.withDemo(demo: Demo?): SkyScene {
    if (demo == null) return this
    return copy(
        condition = demo.condition ?: condition,
        daylight = if (demo.condition != null) (if (demo.night) 0f else 1f) else daylight,
        twilight = if (demo.condition != null) 0f else twilight,
        season = demo.season ?: season,
        wind = demo.wind ?: wind,
        pollen = demo.pollen ?: pollen,
        temperature = if (demo.season == dev.nimbus.weather.ui.background.Season.WINTER) -5.0
        else if (demo.season == dev.nimbus.weather.ui.background.Season.SUMMER) 20.0 else temperature,
        autumnProgress = if (demo.season == dev.nimbus.weather.ui.background.Season.AUTUMN) 0.5f else autumnProgress,
    )
}

@Composable
fun rememberNow(): Long {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Ticks only while the app is visible – no wake-ups in the background.
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            value = System.currentTimeMillis()
            while (true) {
                delay(30_000)
                value = System.currentTimeMillis()
            }
        }
    }
    return now
}

/** Scene used while no data is available yet. */
fun placeholderScene(): SkyScene {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val (season, autumn) = SkyScene.seasonOf(java.time.LocalDate.now(), southern = false)
    return SkyScene(
        Condition.PARTLY_CLOUDY, if (h in 7..19) 1f else 0f, 0f, 0.25f, season = season, autumnProgress = autumn,
        moonPhase = dev.nimbus.weather.util.Moon.preciseIllumination(System.currentTimeMillis()).phase.toFloat(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherPage(
    place: Place,
    state: PlaceState?,
    settings: Settings,
    demo: Demo?,
    isActive: Boolean,
    onRefresh: () -> Unit,
    onOpenRadar: () -> Unit,
    onRequestModels: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow()
    val raw = state?.data
    val data = remember(raw, demo) { raw?.withDemo(demo) }
    val scene = remember(raw, demo, now / 300_000) {
        (raw?.let { SkyScene.from(it, now) } ?: placeholderScene()).withDemo(demo)
    }
    val tf = remember(data?.timezone) { TimeFormat(data?.timezone ?: "UTC", DateFormat.is24HourFormat(context)) }

    Box(Modifier.fillMaxSize()) {
        WeatherBackground(scene, animate = isActive && settings.animationsEnabled)
        if (data == null) {
            LoadingOrError(place, state, onRefresh)
            return@Box
        }
        CompositionLocalProvider(
            LocalSettings provides settings, LocalTimeFormat provides tf,
            dev.nimbus.weather.ui.components.LocalCardShade provides scene.cardShade,
        ) {
            WeatherContent(data, state ?: PlaceState(data), now, onRefresh, onOpenRadar, onRequestModels)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(
    data: WeatherData,
    state: PlaceState,
    now: Long,
    onRefresh: () -> Unit,
    onOpenRadar: () -> Unit,
    onRequestModels: () -> Unit,
) {
    val density = LocalDensity.current
    val cards = LocalSettings.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val expandedPx = with(density) { (ExpandedHeader + statusTop).toPx() }
    val collapsedPx = with(density) { (CollapsedHeader + statusTop).toPx() }
    val scrolled by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val progress by remember { derivedStateOf { (scrolled / (expandedPx - collapsedPx)).coerceIn(0f, 1f) } }
    val raining = data.current.condition.isPrecipitation
    val stale = state.error && now - data.fetchedAt > 30 * 60_000L

    // Only show the spinner for a refresh the user pulled, not for automatic background updates.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }
    PullToRefreshBox(
        isRefreshing = pulled && state.loading,
        onRefresh = { pulled = true; onRefresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Cards slide *under* the header instead of over it.
                    val clipTop = (expandedPx - scrolled).coerceAtLeast(collapsedPx)
                    clipRect(top = clipTop) { this@drawWithContent.drawContent() }
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header-space") { Spacer(Modifier.height(ExpandedHeader + statusTop - 12.dp)) }
            if (stale) item(key = "offline") { OfflineBanner(data) }
            if (data.alerts.isNotEmpty() && cards.shows(WeatherCard.ALERTS)) item(key = "alerts") { AlertsCard(data.alerts) }
            // The cards in the order chosen in the settings (Settings → Cards)
            cards.orderedCards().filter { cards.shows(it) }.forEach { card ->
                when (card) {
                    WeatherCard.HOURLY -> item(key = "hourly") { HourlyCard(data, now) }
                    WeatherCard.DAILY -> item(key = "daily") { DailyCard(data, now) }
                    WeatherCard.PRECIPITATION -> item(key = "precip") { PrecipitationCard(data, now, raining) }
                    WeatherCard.RADAR -> item(key = "radar") { RadarPreviewCard(data, onOpenRadar) }
                    WeatherCard.TILES -> if (cards.orderedTiles().any(cards::shows)) item(key = "tiles") { DetailTiles(data, now) }
                    WeatherCard.SUN -> item(key = "sun") { SunCard(data, now) }
                    WeatherCard.PRESSURE_CHART -> item(key = "pressure-chart") { PressureCard(data, now) }
                    WeatherCard.MOON -> item(key = "moon") { MoonCard(data, now) }
                    WeatherCard.AIR_QUALITY -> if (data.airQuality?.europeanAqi != null) item(key = "aqi") { AirQualityCard(data) }
                    WeatherCard.POLLEN -> data.pollen?.let { p -> item(key = "pollen") { PollenForecastCard(p, now) } }
                    WeatherCard.GAUGES -> if (data.gauges.isNotEmpty()) item(key = "gauge") { GaugeCard(data.gauges, now) }
                    WeatherCard.BATHING -> if (data.bathing.isNotEmpty()) item(key = "bathing") { BathingCard(data.bathing, now) }
                    WeatherCard.COMMUNITY -> if (data.community != null) item(key = "community") { CommunityCard(data) }
                    WeatherCard.MODELS -> item(key = "models") { ModelComparisonCard(state.models, now, onRequestModels) }
                    else -> Unit
                }
            }
            item(key = "sources") { SourcesFooter(data) }
        }
        Header(data, progress, statusTop)
    }
}

@Composable
private fun Header(data: WeatherData, progress: Float, statusTop: androidx.compose.ui.unit.Dp) {
    val s = LocalSettings.current
    val c = data.current
    val today = data.daily.lastOrNull { it.date <= System.currentTimeMillis() } ?: data.daily.firstOrNull()
    val condition = stringResource(Texts.condition(c.condition, c.isDay))
    Column(
        Modifier.fillMaxWidth().padding(top = statusTop + HeaderTop, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (data.place.isCurrentLocation) {
                // Location pin (not an arrow – an arrow next to weather data reads as wind direction).
                Icon(
                    Icons.Rounded.LocationOn, stringResource(R.string.my_location), tint = Color.White,
                    modifier = Modifier.size(22.dp).padding(end = 2.dp),
                )
            }
            Text(
                data.place.name, fontSize = 32.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
        }
        Box(contentAlignment = Alignment.TopCenter) {
            // Collapsed line: "12° | Cloudy"
            Text(
                "${Units.tempFull(c.temperature, s.temperatureUnit)} | $condition",
                Modifier.graphicsLayer { alpha = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f) },
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = NimbusColors.Secondary,
            )
            Column(
                Modifier.graphicsLayer {
                    alpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
                    translationY = -progress * 60.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Combined symbol (sun/moon, clouds, rain/snow, wind), temperature with unit and the
                // day's max/min stacked like on a weather station display.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        c.condition, c.isDay, size = 64.dp, wind = windiness(c.windSpeed, c.windGust),
                        description = condition,
                    )
                    Spacer(Modifier.width(8.dp))
                    dev.nimbus.weather.ui.components.BigTemperature(c.temperature, s.temperatureUnit, 96.sp, shadow = TextShadow)
                    if (today != null) {
                        Spacer(Modifier.width(12.dp))
                        dev.nimbus.weather.ui.components.MaxMinStack(today.tempMax, today.tempMin, s.temperatureUnit, fontSize = 18.sp, shadow = TextShadow)
                    }
                }
                Text(condition, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Color(0xE6FFFFFF), style = androidx.compose.ui.text.TextStyle(shadow = TextShadow))
                if (c.stationName != null && c.stationDistanceKm != null) {
                    val explain = dev.nimbus.weather.ui.components.LocalExplain.current
                    Row(
                        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { explain(dev.nimbus.weather.ui.components.Term.STATION) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.measured_at_station, c.stationName, Units.oneDecimal(c.stationDistanceKm)),
                            fontSize = 12.sp, color = NimbusColors.Secondary, textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
                        )
                        Icon(androidx.compose.material.icons.Icons.Outlined.Info, null, tint = NimbusColors.Tertiary, modifier = Modifier.padding(start = 4.dp).size(12.dp))
                    }
                }
            }
        }
    }
}

private val TextShadow = androidx.compose.ui.graphics.Shadow(Color(0x59000000), androidx.compose.ui.geometry.Offset(0f, 2f), 10f)

@Composable
private fun OfflineBanner(data: WeatherData) {
    val tf = LocalTimeFormat.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.offline_cached, tf.weekdayShort(data.fetchedAt) + " " + tf.time(data.fetchedAt)), fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun LoadingOrError(place: Place, state: PlaceState?, onRetry: () -> Unit) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier.fillMaxSize().padding(top = statusTop + HeaderTop, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(place.name, fontSize = 32.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(80.dp))
        if (state?.error == true) {
            Text(stringResource(R.string.error_loading), fontSize = 17.sp, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x40FFFFFF), contentColor = Color.White),
            ) { Text(stringResource(R.string.retry)) }
        } else {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.loading), fontSize = 15.sp, color = NimbusColors.Secondary, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

