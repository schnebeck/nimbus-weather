package dev.nimbus.weather.ui.main

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
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
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
        CompositionLocalProvider(LocalSettings provides settings, LocalTimeFormat provides tf) {
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
    val nowcastPoints = remember(data, now) { Insights.nowcastPoints(data.minutely, now) }
    val raining = data.current.condition.isPrecipitation
    val showNowcast = raining || nowcastPoints.any { it.precipitation >= Insights.RAIN_THRESHOLD_MM_15 }
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
            if (data.alerts.isNotEmpty()) item(key = "alerts") { AlertsCard(data.alerts) }
            if (showNowcast) item(key = "nowcast") { NowcastCard(nowcastPoints, now, raining) }
            item(key = "hourly") { HourlyCard(data, now) }
            item(key = "daily") { DailyCard(data, now) }
            item(key = "precip") { PrecipitationCard(data, now) }
            item(key = "radar") { RadarPreviewCard(data, onOpenRadar) }
            item(key = "tiles") { DetailTiles(data, now) }
            item(key = "moon") { MoonCard(data, now) }
            if (data.airQuality?.europeanAqi != null) item(key = "aqi") { AirQualityCard(data) }
            if ((data.airQuality?.pollen?.values?.maxOrNull() ?: 0.0) >= 1.0) item(key = "pollen") { PollenCard(data) }
            if (data.community != null) item(key = "community") { CommunityCard(data) }
            item(key = "models") { ModelComparisonCard(state.models, now, onRequestModels) }
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
                Icon(Icons.Rounded.NearMe, null, tint = Color.White, modifier = Modifier.size(18.dp).padding(end = 2.dp))
            }
            Text(
                data.place.name, fontSize = 32.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
        }
        Box(contentAlignment = Alignment.TopCenter) {
            // Collapsed line: "12° | Cloudy"
            Text(
                "${Units.temp(c.temperature, s.temperatureUnit)} | $condition",
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
                // Combined symbol (sun/moon, clouds, rain/snow, wind) next to the temperature.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        c.condition, c.isDay, size = 68.dp, wind = windiness(c.windSpeed, c.windGust),
                        description = condition,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Units.temp(c.temperature, s.temperatureUnit),
                        fontSize = 100.sp, fontWeight = FontWeight.Thin, color = Color.White, lineHeight = 104.sp,
                        style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
                    )
                }
                Text(condition, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Color(0xE6FFFFFF), style = androidx.compose.ui.text.TextStyle(shadow = TextShadow))
                if (today != null) {
                    Text(
                        stringResource(R.string.high_low, Units.temp(today.tempMax, s.temperatureUnit), Units.temp(today.tempMin, s.temperatureUnit)),
                        fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
                    )
                }
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
