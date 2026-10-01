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

    Box(Modifier.fillMaxSize()) {
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
