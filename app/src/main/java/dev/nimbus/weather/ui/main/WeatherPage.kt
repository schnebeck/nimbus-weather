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

import dev.nimbus.weather.ui.components.statusBarsStable
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import dev.nimbus.weather.ui.components.shrinkToFit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.components.LocalCardStatus
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

// Expanded: the big header ends with the station line; half the former gap below it
private val ExpandedHeader = 288.dp
// Collapsed: name and "15 °C | Rain" end at about 124 dp – the cards start a little below
private val CollapsedHeader = 134.dp
/** Below this window height (dp) – a phone held sideways – the header is compact. */
private const val COMPACT_HEIGHT_DP = 500
private val CompactExpandedHeader = 200.dp
/** The pull-to-refresh spinner starts this far below the status bar: under the top bar (52 dp). */
private val RefreshBelow = 56.dp
/** Space between the bottom of the open header and the first card. */
private val HeaderGap = 20.dp
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
    onRequestHistory: () -> Unit = {},
    location: LocationMark? = null,
    /** The model, for a place in the list more than once ([dev.nimbus.weather.ui.places.PlaceTwins.label]). */
    modelLabel: String? = null,
) {
    val context = LocalContext.current
    val now = rememberNow()
    // Today's station readings for the day charts (measured instead of forecast for the hours over), Germany only
    LaunchedEffect(place.id, isActive) {
        if (isActive && dev.nimbus.weather.data.repo.WeatherRepository.isInDwdArea(place.latitude, place.longitude) &&
            (settings.shows(WeatherCard.PRECIPITATION) || settings.shows(WeatherCard.PRESSURE_CHART) || settings.shows(WeatherCard.DAILY))
        ) onRequestHistory()
    }
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
            dev.nimbus.weather.ui.components.LocalCardFill provides scene.cardFill,
            dev.nimbus.weather.ui.components.LocalHeaderStyle provides dev.nimbus.weather.ui.components.HeaderStyle(scene.headerHalo, scene.headerPill),
        ) {
            WeatherContent(data, state ?: PlaceState(data), now, onRefresh, onOpenRadar, onRequestModels, location, modelLabel)
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
    location: LocationMark?,
    modelLabel: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cards = LocalSettings.current
    val statusTop = WindowInsets.statusBarsStable.asPaddingValues().calculateTopPadding()
    val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
    // a low window (a phone held sideways): a compact header, the cards get the height
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val compact = config.screenHeightDp < COMPACT_HEIGHT_DP
    // ... and lying wide: the header as a pane of its own on the left that stays, the cards in
    // the full height beside it – a header over the cards left them a strip of sky
    val sideways = sideways(config.screenWidthDp, config.screenHeightDp)
    val paneWidth = if (sideways) headerPaneWidth(LocalContentWidth.current) else 0.dp
    // the camera's cut-out at the side (phone sideways): nothing of the page under it
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal).asPaddingValues()
    val cutStart = cutout.calculateStartPadding(dir)
    val cutEnd = cutout.calculateEndPadding(dir)
    // Phone: one column. Tablet (from 600 dp): the cards flow in two columns, three from 1150 dp.
    val listWidth = LocalContentWidth.current - paneWidth
    val columns = when {
        // beside the header pane: two columns of cards – three fixed columns in all
        sideways -> 2
        listWidth >= 1150.dp -> 3
        listWidth >= 600.dp -> 2
        else -> 1
    }
    val listState = rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    // The header as tall as it is: a large system font makes it taller than [ExpandedHeader] –
    // the first card started under the station line then
    var headerPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val expandedBase = if (compact) CompactExpandedHeader else ExpandedHeader
    // sideways the list begins below the top bar (the header is beside it)
    val expandedPx = with(density) { if (sideways) (statusTop + HeaderTop).toPx() else maxOf((expandedBase + statusTop).toPx(), headerPx + HeaderGap.toPx()) }
    val collapsedPx = with(density) { if (sideways) expandedPx else (CollapsedHeader + statusTop).toPx() }
    val scrolled by remember(columns) {
        derivedStateOf {
            val (index, offset) = if (columns == 1) listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            else gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            if (index > 0) Float.MAX_VALUE else offset.toFloat()
        }
    }
    // Keyed like [scrolled]: turning the phone switches between list and grid, and a progress kept
    // from before still read the list – the header stayed open over the grid's cards
    val progress by remember(columns, expandedPx, collapsedPx) {
        derivedStateOf { if (expandedPx <= collapsedPx) 0f else (scrolled / (expandedPx - collapsedPx)).coerceIn(0f, 1f) }
    }
    val raining = data.current.condition.isPrecipitation
    val tfToday = LocalTimeFormat.current
    val todayMeasured = remember(state.history, now / 600_000L) { TodayMeasured.of(state.history, tfToday.zoned(now).toLocalDate()) }
    val stale = state.error && now - data.fetchedAt > 30 * 60_000L

    // A dry day hides the precipitation card (setting) – worked out once per minute, not per frame
    val dryToday = remember(data, now / 60_000L, raining, todayMeasured) {
        PrecipToday.of(data, now, raining, todayMeasured, tfToday)?.dry == true
    }

    // The page as a list of cards. In columns only the sky, the alerts, the offline note and the
    // sources span them all: a card over all columns closed them like a line – the shorter one
    // stayed empty above it beside a long card (an opened day of the 10-day forecast)
    val items = spanLonely(buildList {
        // the sky above the cards: double-tapping it switches full screen (the header drawn over it has no touch of its own)
        add(PageItem("header-space", true) { Spacer(Modifier.fillMaxWidth().height(with(density) { expandedPx.toDp() } - 12.dp).fullscreenByDoubleTap()) })
        if (stale) add(PageItem("offline", true) { OfflineBanner(data) })
        if (data.alerts.isNotEmpty() && cards.shows(WeatherCard.ALERTS)) add(PageItem("alerts", true) { AlertsCard(data.alerts) })
        // The cards in the order chosen in the settings (Settings → Cards)
        cards.orderedCards().filter { cards.shows(it) }.forEach { card ->
            when (card) {
                WeatherCard.HOURLY -> add(PageItem("hourly", columns == 1) { HourlyCard(data, now) })
                WeatherCard.DAILY -> add(PageItem("daily") { DailyCard(data, now, todayMeasured) })
                // on a dry day hidden (setting) – decided here, an empty card would leave a gap
                WeatherCard.PRECIPITATION -> if (cards.showDryPrecipitation || !dryToday)
                    add(PageItem("precip") { PrecipitationCard(data, now, raining, todayMeasured) })
                WeatherCard.RADAR -> add(PageItem("radar") { RadarPreviewCard(data, onOpenRadar) })
                // one column: two side by side; in columns each a card of its own (room for its title)
                WeatherCard.TILES -> if (columns == 1) {
                    if (cards.orderedTiles().any(cards::shows)) add(PageItem("tiles") { DetailTiles(data, now) })
                } else detailTiles(data, now).forEach { (t, tile) -> add(PageItem("tile-${t.name.lowercase()}") { LaneTile(tile) }) }
                WeatherCard.SUN -> add(PageItem("sun") { SunCard(data, now) })
                WeatherCard.PRESSURE_CHART -> add(PageItem("pressure-chart") { PressureCard(data, now, todayMeasured) })
                WeatherCard.MOON -> add(PageItem("moon") { MoonCard(data, now) })
                WeatherCard.AIR_QUALITY -> if (data.airQuality?.europeanAqi != null) add(PageItem("aqi") { AirQualityCard(data) })
                WeatherCard.POLLEN -> data.pollen?.let { p -> add(PageItem("pollen") { PollenForecastCard(p, now) }) }
                WeatherCard.GAUGES -> if (data.gauges.isNotEmpty()) add(PageItem("gauge") { GaugeCard(data.gauges, now) })
                WeatherCard.BATHING -> if (data.bathing.isNotEmpty()) add(PageItem("bathing") { BathingCard(data.bathing, now) })
                WeatherCard.COMMUNITY -> if (data.community != null) add(PageItem("community") { CommunityCard(data) })
                WeatherCard.MODELS -> add(PageItem("models") { ModelComparisonCard(state.models, now, onRequestModels) })
                else -> Unit
            }
        }
        add(PageItem("sources", true) { SourcesFooter(data) })
    })
    // Cards whose data comes after the page is shown (the extras of a new place) pop in
    val arrivals = remember(data.place.id) { Arrivals() }
    arrivals.note(items.map { it.key })

    // Only show the spinner for a refresh the user pulled, not for automatic background updates.
    var pulled by remember { mutableStateOf(false) }
    // busy: loading – or, for "my location", looking for its position first (its data wait for it)
    val busy = state.loading || location?.searching == true
    LaunchedEffect(busy) { if (!busy) pulled = false }
    val refreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val onLocate: () -> Unit = {
        if (location?.off == true) {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        } else onRefresh()
    }
    if (sideways) {
        // in the middle of the space below the top bar – beside the cards that begin there
        Box(
            Modifier.width(cutStart + paneWidth).fillMaxHeight().testTag("header-pane").fullscreenByDoubleTap()
                .padding(start = cutStart, top = statusTop + HeaderTop),
            contentAlignment = Alignment.Center,
        ) {
            Header(data, { 0f }, statusTop, compact = true, location, onLocate, top = 0.dp, modelLabel = modelLabel) { }
        }
    }
    Box(Modifier.fillMaxSize().padding(start = if (sideways) cutStart + paneWidth else 0.dp)) {
    PullToRefreshBox(
        isRefreshing = pulled && busy,
        onRefresh = { pulled = true; onRefresh() },
        modifier = Modifier.fillMaxSize(),
        state = refreshState,
        // The spinner below the status bar and the top bar (menu, page dots, radar) – from the
        // top edge it came down into the front camera ("a black snowman"), in full screen too
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = refreshState, isRefreshing = pulled && busy,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = statusTop + RefreshBelow),
            )
        },
    ) {
        // Cards slide *under* the header instead of over it. The cards themselves keep their title
        // at this line and slide away below it (see GlassCard); the clip catches everything else.
        val listTop = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val clip = Modifier.fillMaxSize()
            .onGloballyPositioned { listTop.floatValue = it.positionInRoot().y }
            .drawWithContent {
                val clipTop = (expandedPx - scrolled).coerceAtLeast(collapsedPx)
                clipRect(top = clipTop) { this@drawWithContent.drawContent() }
            }
        val pinLine: () -> Float = { listTop.floatValue + (expandedPx - scrolled).coerceAtLeast(collapsedPx) }
        val side = if (columns == 1) 16.dp else 24.dp
        val start = side + if (sideways) 0.dp else cutStart
        val end = side + cutEnd
        CompositionLocalProvider(dev.nimbus.weather.ui.components.LocalPinLine provides pinLine) {
        if (columns == 1) {
            LazyColumn(
                state = listState, modifier = clip,
                contentPadding = PaddingValues(start = start, end = end, bottom = navBottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items.size, key = { items[it].key }) { Card(items[it], arrivals, data.place.id) }
            }
        } else {
            androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(columns),
                state = gridState, modifier = clip,
                contentPadding = PaddingValues(start = start, end = end, bottom = navBottom + 24.dp),
                verticalItemSpacing = 14.dp,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items.size, key = { items[it].key },
                    span = { if (items[it].fullSpan) androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine else androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.SingleLane },
                ) { Card(items[it], arrivals, data.place.id) }
            }
        }
        }
    }
        // read while drawing only: scrolling never recomposes the page (and rebuilds its cards)
        if (!sideways) Header(data, { progress }, statusTop, compact, location, onLocate, modelLabel = modelLabel) { headerPx = it }
    }
}

@Composable
private fun Header(
    data: WeatherData, progress: () -> Float, statusTop: androidx.compose.ui.unit.Dp, compact: Boolean = false,
    location: LocationMark? = null, onLocate: () -> Unit = {},
    /** Space above it: below the status bar and the top bar – none in the header pane (sideways). */
    top: androidx.compose.ui.unit.Dp = statusTop + HeaderTop,
    modelLabel: String? = null,
    onHeight: (Int) -> Unit = {},
) {
    val s = LocalSettings.current
    val c = data.current
    val today = data.daily.lastOrNull { it.date <= System.currentTimeMillis() } ?: data.daily.firstOrNull()
    val condition = stringResource(Texts.condition(c.condition, c.isDay))
    // Straight on the sky: pure white, large, with a dark halo as strong as the brightest sky
    // behind needs (Legibility) – the sky itself stays as it is
    val header = dev.nimbus.weather.ui.components.LocalHeaderStyle.current
    val TextShadow = header.shadow
    Column(
        Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) }.padding(top = top, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val locate = stringResource(R.string.location_locate)
        Row(
            if (location != null) Modifier.clickable(onClickLabel = locate, onClick = onLocate) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.place.isCurrentLocation) LocationPin(location)
            val nameSize = if (compact) 26.sp else 32.sp
            Text(
                data.place.name, Modifier.weight(1f, fill = false).alignByBaseline(),
                fontSize = nameSize, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
            // the status dot after the name, as on the cards after their title – not on the pin
            if (location != null) LocationDot(location, nameSize)
        }
        dev.nimbus.weather.ui.places.TwinModelLine(modelLabel, androidx.compose.ui.text.TextStyle(shadow = TextShadow))
        Box(contentAlignment = Alignment.TopCenter) {
            // Collapsed line: "12° | Cloudy"
            Text(
                "${Units.tempFull(c.temperature, s.temperatureUnit)} | $condition",
                Modifier.graphicsLayer { alpha = ((progress() - 0.65f) / 0.35f).coerceIn(0f, 1f) },
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
            Column(
                Modifier.graphicsLayer {
                    val p = progress()
                    alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    translationY = -p * 60.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Combined symbol (sun/moon, clouds, rain/snow, wind), temperature with unit and the
                // day's max/min stacked like on a weather station display – shrunk as a whole
                // where it does not fit (a narrow phone, a large font), never cut off
                Row(Modifier.shrinkToFit(), verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        c.condition, c.isDay, size = if (compact) 46.dp else 64.dp, wind = windiness(c.windSpeed, c.windGust),
                        description = condition,
                    )
                    Spacer(Modifier.width(8.dp))
                    dev.nimbus.weather.ui.components.BigTemperature(c.temperature, s.temperatureUnit, if (compact) 56.sp else 96.sp, shadow = TextShadow)
                    if (today != null) {
                        Spacer(Modifier.width(12.dp))
                        dev.nimbus.weather.ui.components.MaxMinStack(today.tempMax, today.tempMin, s.temperatureUnit, fontSize = 18.sp, shadow = TextShadow, labelColor = Color.White)
                    }
                }
                Text(condition, fontSize = if (compact) 18.sp else 21.sp, fontWeight = FontWeight.Medium, color = Color.White, style = androidx.compose.ui.text.TextStyle(shadow = TextShadow))
                if (c.stationName != null && c.stationDistanceKm != null) {
                    val explain = dev.nimbus.weather.ui.components.LocalExplainCase.current
                    val sources = nowSourcesText(c)
                    // small text: on a pill of its own glass, dark enough for the sky behind
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(header.pill)
                            .clickable { explain(dev.nimbus.weather.ui.components.Term.STATION, sources) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // wrapped onto two lines (a narrow pane, a large font) the ⓘ keeps its room
                        // the station's name kept together (it moves to the second line as a whole, the
                        // distance stays with it), the lines close and the pill only as wide as they are
                        dev.nimbus.weather.ui.components.TightText(
                            stringResource(
                                R.string.measured_at_station, (c.stationNetwork ?: dev.nimbus.weather.data.model.StationNetwork.DWD).label,
                                keptTogether(c.stationName), Units.oneDecimal(c.stationDistanceKm),
                            ),
                            Modifier.weight(1f, fill = false).testTag("station-line"),
                            androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Color.White, textAlign = TextAlign.Center),
                        )
                        Icon(androidx.compose.material.icons.Icons.Outlined.Info, null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(12.dp))
                    }
                }
                // the device's location is off: "my location" cannot follow – one tap to its setting
                if (location?.off == true) {
                    Text(
                        stringResource(R.string.location_off),
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(header.pill)
                            .clickable(onClick = onLocate).padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}


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
    val statusTop = WindowInsets.statusBarsStable.asPaddingValues().calculateTopPadding()
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

/** One card of the weather page; [fullSpan] cards span all columns on a tablet. */
internal class PageItem(val key: String, val fullSpan: Boolean = false, val content: @Composable () -> Unit)

/** Double-tap: full screen on, double-tap again: off – on the sky above the cards (taps only: scrolling and swiping stay). */
@Composable
internal fun Modifier.fullscreenByDoubleTap(): Modifier {
    val update = LocalSettingsUpdater.current
    return this.then(Modifier.pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { update { it.copy(fullscreen = !it.fullscreen) } })
    })
}

/**
 * In the columns of a tablet, a card between two wide ones (or a wide one and the end) would
 * stand alone in its row, the other column empty – e.g. "precipitation today" right above the
 * hourly row: it gets the full width.
 */
private fun spanLonely(items: List<PageItem>): List<PageItem> =
    items.mapIndexed { i, it -> if (!it.fullSpan && lonely(items.map { p -> p.fullSpan }, i)) PageItem(it.key, true, it.content) else it }

/** Whether the narrow item [i] of a list ([wide]: which items span all columns) stands alone between wide ones. */
internal fun lonely(wide: List<Boolean>, i: Int): Boolean =
    !wide[i] && (i == 0 || wide[i - 1]) && (i == wide.lastIndex || wide[i + 1])

/**
 * Which cards of a page came later than the rest: the first list of a page is there; a key that
 * joins a later list arrived (its data came in, or the card was switched on) and is [fresh] for
 * [FRESH_MS] – long enough to pop in when it is drawn, never again when it scrolls back into view.
 */
internal class Arrivals(private val clock: () -> Long = System::currentTimeMillis) {
    private var known: Set<String>? = null
    private val since = HashMap<String, Long>()

    fun note(keys: List<String>) {
        val k = known
        if (k != null) for (key in keys) if (key !in k) since[key] = clock()
        known = keys.toSet()
    }

    fun fresh(key: String): Boolean = since[key]?.let { clock() - it < FRESH_MS } == true

    companion object { const val FRESH_MS = 2_000L }
}

/**
 * The data part a card of the page shows (its status dot follows it); null: no dot (the header
 * space, banners, the footer, the model comparison loaded on demand).
 */
internal fun cardParts(key: String): Set<dev.nimbus.weather.data.model.DataPart>? = when (key) {
    "header-space", "offline", "sources", "models" -> null
    "alerts" -> setOf(dev.nimbus.weather.data.model.DataPart.FORECAST, dev.nimbus.weather.data.model.DataPart.FLOOD)
    "aqi" -> setOf(dev.nimbus.weather.data.model.DataPart.AIR_QUALITY)
    "pollen" -> setOf(dev.nimbus.weather.data.model.DataPart.POLLEN)
    "community" -> setOf(dev.nimbus.weather.data.model.DataPart.COMMUNITY)
    "gauge" -> setOf(dev.nimbus.weather.data.model.DataPart.GAUGES)
    "bathing" -> setOf(dev.nimbus.weather.data.model.DataPart.BATHING)
    else -> setOf(dev.nimbus.weather.data.model.DataPart.FORECAST)
}

/**
 * Where the position of "my location" stands: [current] taken within
 * [dev.nimbus.weather.data.repo.Freshness.LOCATION_MS], [searching] being looked for, [off] the
 * device's location switched off.
 */
data class LocationMark(val current: Boolean, val searching: Boolean, val off: Boolean)

/** Where the position of "my location" stands at [now] (see [LocationMark]). */
internal fun locationMark(state: dev.nimbus.weather.ui.UiState, shelf: dev.nimbus.weather.data.repo.Shelf): LocationMark = LocationMark(
    // the position's record: out of date when its time is up or it is asked for anew
    current = shelf.position.state == dev.nimbus.weather.data.repo.RecordState.CURRENT,
    searching = state.locationStatus == dev.nimbus.weather.ui.LocationStatus.LOADING,
    off = state.locationOff,
)

/**
 * The pin of "my location"; while its position is looked for it breathes. Its status
 * ([LocationDot]) stands apart from it: on the pin the dot covered half the pin's point.
 */
@Composable
internal fun LocationPin(location: LocationMark?, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val pulse = if (location?.searching == true) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "locating")
        t.animateFloat(
            1f, 0.35f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "pin",
        )
    } else null
    val label = stringResource(
        when {
            location == null -> R.string.my_location
            location.searching -> R.string.location_searching
            location.current -> R.string.location_current
            else -> R.string.location_not_current
        },
    )
    Icon(
        Icons.Rounded.LocationOn, label, tint = Color.White,
        modifier = Modifier.padding(end = 4.dp).testTag("location-pin").size(size).graphicsLayer { alpha = pulse?.value ?: 1f },
    )
}

/**
 * The status dot of "my location", the one of the cards: green, the position is current; yellow,
 * it is older (the page shows the place last found). Behind the name (aligned by its baseline in
 * a [androidx.compose.foundation.layout.RowScope]) with its centre at the height of the capitals
 * of [fontSize] – like an index, not on the pin.
 */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.LocationDot(location: LocationMark, fontSize: androidx.compose.ui.unit.TextUnit) {
    val capPx = with(LocalDensity.current) { fontSize.toPx() * CAP_HEIGHT }
    dev.nimbus.weather.ui.components.StatusDot(
        if (location.current) CardStatus.FRESH else CardStatus.STALE,
        Modifier.padding(start = 6.dp).alignBy { it.measuredHeight / 2 + capPx.toInt() }.testTag("location-dot").clearAndSetSemantics { },
    )
}

/** Height of the capitals in the app's font (Roboto: 1456 of 2048 units per em). */
internal const val CAP_HEIGHT = 0.711f

/** [name] that does not break inside (spaces and hyphens kept): it wraps as a whole. */
internal fun keptTogether(name: String): String = name.replace(' ', '\u00A0').replace('-', '\u2011')

/** A phone held sideways: a low window ([COMPACT_HEIGHT_DP]) wider than high – the header gets a pane of its own. */
internal fun sideways(widthDp: Int, heightDp: Int): Boolean = heightDp < COMPACT_HEIGHT_DP && widthDp > heightDp

/**
 * The header's pane sideways: the first of three fixed columns ([HEADER_SHARE] of the width), the
 * cards in the other two.
 */
internal fun headerPaneWidth(contentWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = contentWidth * HEADER_SHARE

/** The header's share of the width sideways. */
internal const val HEADER_SHARE = 1f / 3

/** Status of the card [key] when [stale] parts still show older values. */
internal fun cardStatus(key: String, stale: Set<dev.nimbus.weather.data.model.DataPart>): CardStatus? =
    cardParts(key)?.let { parts -> if (parts.any { it in stale }) CardStatus.STALE else CardStatus.FRESH }

/**
 * One card of the page: popping in when it arrived, with its status dot – read from the records of
 * its parts: when one of them goes out of date or arrives, this card alone is drawn anew.
 */
@Composable
internal fun Card(item: PageItem, arrivals: Arrivals, placeId: String) {
    val shelf = LocalShelf.current
    val stale = cardParts(item.key)?.filterTo(mutableSetOf()) { shelf.stateOf(placeId, it) != dev.nimbus.weather.data.repo.RecordState.CURRENT }.orEmpty()
    // the protocol of the view (debug builds): each card drawn, with the parts it shows out of date
    if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("NimbusCard", "$placeId ${item.key} stale=$stale")
    PopIn(arrivals.fresh(item.key)) {
        CompositionLocalProvider(LocalCardStatus provides cardStatus(item.key, stale)) {
            Box(Modifier.testTag("card-${item.key}")) { item.content() }
        }
    }
}

/** The model of what is current ([dev.nimbus.weather.data.repo.Shelf]) – provided by the app's root. */
val LocalShelf = androidx.compose.runtime.staticCompositionLocalOf {
    dev.nimbus.weather.data.repo.Shelf(kotlinx.coroutines.MainScope())
}

/** Overshoots a little before it settles – the "pop". */
private val Pop = androidx.compose.animation.core.Easing { t -> val x = t - 1f; x * x * (2.70158f * x + 1.70158f) + 1f }

/**
 * A card that arrives: first its place opens in the list (the cards below slide down), then it
 * pops in from small to its size. [fresh] false: simply there (the same layout, so a card never
 * loses its state when the flag changes).
 */
@Composable
private fun PopIn(fresh: Boolean, content: @Composable () -> Unit) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(!fresh).apply { targetState = true } }
    androidx.compose.animation.AnimatedVisibility(
        state,
        enter = androidx.compose.animation.expandVertically(
            androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            expandFrom = Alignment.Top, clip = false,
        ) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380, delayMillis = 240, easing = Pop), initialScale = 0.6f) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 240)),
    ) { content() }
}

/**
 * Width available to the weather page (the screen minus the places sidebar on a tablet in
 * landscape); decides how many columns the cards use.
 */
val LocalContentWidth = androidx.compose.runtime.staticCompositionLocalOf { 400.dp }
