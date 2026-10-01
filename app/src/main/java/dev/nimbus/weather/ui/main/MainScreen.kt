/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/MainScreen.kt
 * Main screen with top bar, pages per place and the swipe into the past days.
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

import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.ui.LocationStatus
import dev.nimbus.weather.ui.UiState
import dev.nimbus.weather.ui.background.WeatherBackground
import dev.nimbus.weather.ui.theme.NimbusColors
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MainScreen(
    state: UiState,
    onSelect: (String) -> Unit,
    onRefresh: (String) -> Unit,
    onOpenRadar: (String?) -> Unit,
    onOpenRadarDay: (String, Long) -> Unit,
    onOpenPlaces: () -> Unit,
    onRequestModels: (String) -> Unit,
    onRequestHistory: (String) -> Unit,
    onRequestLocation: () -> Unit,
) {
    val pages = state.pages
    if (pages.isEmpty()) {
        WelcomeScreen(state, onRequestLocation, onOpenPlaces)
        return
    }
    // Places are chosen from the menu; swiping moves through time instead:
    // day before yesterday | yesterday | today so far | now (start page).
    val place = pages.firstOrNull { it.id == state.selectedPlaceId } ?: pages.first()
    val pageCount = HISTORY_DAYS + 1
    val pagerState = key(place.id) { rememberPagerState(initialPage = pageCount - 1) { pageCount } }
    val placeState = state.states[place.id]

    // Load the history as soon as the user starts swiping back.
    LaunchedEffect(pagerState, place.id) {
        snapshotFlow { pagerState.currentPage < pageCount - 1 || pagerState.targetPage < pageCount - 1 }
            .distinctUntilChanged().collect { back -> if (back) onRequestHistory(place.id) }
    }

    // Tablet in landscape: the places as a sidebar on the left, the weather on the right
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val sidebar = maxWidth >= 1000.dp && maxWidth > maxHeight
    val contentWidth = if (sidebar) maxWidth - SidebarWidth else maxWidth
    Row(Modifier.fillMaxSize()) {
    if (sidebar) PlacesSidebar(state, place.id, onSelect, onOpenPlaces, Modifier.width(SidebarWidth).fillMaxHeight())
    androidx.compose.runtime.CompositionLocalProvider(LocalContentWidth provides contentWidth) {
    Box(Modifier.weight(1f).fillMaxHeight()) {
        HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { it }, beyondViewportPageCount = 0) { page ->
            if (page == pageCount - 1) {
                WeatherPage(
                    place = place,
                    state = placeState,
                    settings = state.settings,
                    demo = state.demo,
                    isActive = pagerState.currentPage == page,
                    onRefresh = { onRefresh(place.id) },
                    onOpenRadar = { onOpenRadar(place.id) },
                    onRequestModels = { onRequestModels(place.id) },
                    onRequestHistory = { onRequestHistory(place.id) },
                )
            } else {
                // page 0 = two days ago, page HISTORY_DAYS - 1 = today so far
                HistoryPage(
                    place = place,
                    state = placeState,
                    settings = state.settings,
                    dayIndex = page,
                    isActive = pagerState.currentPage == page,
                    onRetry = { onRequestHistory(place.id) },
                    onOpenRadarDay = { day -> onOpenRadarDay(place.id, day) },
                )
            }
        }
        TopBar(
            count = pageCount,
            current = pagerState.currentPage,
            onRadar = { onOpenRadar(place.id) },
            onMenu = onOpenPlaces,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
    }
    }
}

private val SidebarWidth = 340.dp

/**
 * Places as a sidebar (tablet in landscape), like Apple Weather on the iPad: every place as a
 * small weather card, the shown one outlined; the button on top opens search, editing and settings.
 */
@Composable
private fun PlacesSidebar(state: UiState, selectedId: String, onSelect: (String) -> Unit, onOpenPlaces: () -> Unit, modifier: Modifier) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33)))),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weather), Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onOpenPlaces) { Icon(Icons.Rounded.Search, stringResource(R.string.places), tint = Color.White) }
            }
        }
        items(state.pages.size, key = { state.pages[it].id }) { i ->
            val p = state.pages[i]
            val selected = p.id == selectedId
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(16.dp)),
            ) { dev.nimbus.weather.ui.places.PlaceCard(p, state.states[p.id], state.settings) { onSelect(p.id) } }
        }
    }
}

/** Past days reachable by swiping right: today so far, yesterday, the day before. */
const val HISTORY_DAYS = 3

/** Menu (places & settings) top left, radar top right, page dots in between. */
@Composable
private fun TopBar(count: Int, current: Int, onRadar: () -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 6.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, stringResource(R.string.places), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // Time position: history pages as small dots, "now" as a larger dot on the right.
            for (i in 0 until count) {
                val color = if (i == current) Color.White else Color(0x66FFFFFF)
                val size = if (i == count - 1) 8.dp else 6.dp
                Box(Modifier.padding(horizontal = 4.dp).size(size).clip(CircleShape).background(color))
            }
        }
        // A labelled pill – a bare map icon was not recognisable as "rain radar".
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x4D0A1A33))
                .border(0.6.dp, Color(0x40FFFFFF), RoundedCornerShape(20.dp)).clickable(onClick = onRadar)
                .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Radar, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.radar), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun WelcomeScreen(state: UiState, onRequestLocation: () -> Unit, onSearch: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        WeatherBackground(placeholderScene(), animate = state.settings.animationsEnabled)
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x66061A3A), Color(0x22061A3A), Color.Transparent))))
        Column(
            Modifier.fillMaxSize().padding(32.dp).windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.welcome_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, style = TextStyle(shadow = HeadlineShadow))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_text), fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp), style = TextStyle(shadow = HeadlineShadow))
            Spacer(Modifier.height(36.dp))
            if (state.locationStatus == LocationStatus.LOADING) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = onRequestLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF14305E)),
                    modifier = Modifier.widthIn(min = 240.dp),
                ) {
                    Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.use_my_location), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSearch, modifier = Modifier.widthIn(min = 240.dp), border = BorderStroke(1.dp, Color(0xCCFFFFFF))) {
                Text(stringResource(R.string.search_city), color = Color.White)
            }
            val msg = when (state.locationStatus) {
                LocationStatus.DENIED -> R.string.location_permission_denied
                LocationStatus.UNAVAILABLE -> R.string.location_unavailable
                else -> null
            }
            if (msg != null) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(msg), fontSize = 13.sp, color = NimbusColors.Secondary, textAlign = TextAlign.Center)
            }
        }
    }
}

private val HeadlineShadow = androidx.compose.ui.graphics.Shadow(Color(0x88001030), androidx.compose.ui.geometry.Offset(0f, 2f), 12f)
