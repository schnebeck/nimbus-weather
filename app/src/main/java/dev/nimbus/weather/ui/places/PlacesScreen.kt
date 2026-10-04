/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/places/PlacesScreen.kt
 * List of places with search and current weather per place.
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

package dev.nimbus.weather.ui.places

import dev.nimbus.weather.ui.main.LocationDot
import dev.nimbus.weather.ui.components.statusBarsStable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.ui.LocationStatus
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.UiState
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.Units
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    state: UiState,
    search: suspend (String) -> List<Place>,
    onAdd: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onReorder: (List<String>) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onBack: () -> Unit,
) {
    val now = dev.nimbus.weather.ui.main.rememberNow()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(300)
        results = search(query.trim())
    }
    val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
    // Edit mode (long press on a place): sort by dragging, delete with a confirmation.
    var editing by rememberSaveable { mutableStateOf(false) }
    val saved = state.savedPlaces.filter { it.id != state.currentPlace?.id }
    if (saved.isEmpty()) editing = false
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }

    // Undo after sorting the places (a row moved by accident while scrolling)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo)
    val undoMessage = stringResource(R.string.places_undo_order)
    val reorder: (List<String>) -> Unit = { ids ->
        val before = state.savedPlaces.filter { it.id != state.currentPlace?.id }.map { it.id }
        if (ids != before) {
            onReorder(ids)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(undoMessage, actionLabel = undoLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onReorder(before)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBarsStable)
            .imePadding()
            // Tablet: a readable column in the middle, the background still fills the screen
            .wrapContentWidth().widthIn(max = 720.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.pages.isNotEmpty()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close), tint = Color.White) }
            } else Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.weather), Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (editing) {
                androidx.compose.material3.TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.places_edit_done), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            } else IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Color.White) }
        }
        if (editing) {
            Text(
                stringResource(R.string.places_edit_hint), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 13.sp, color = NimbusColors.Secondary,
            )
            EditList(saved, state, reorder, onRemove, Modifier.padding(top = 8.dp), navBottom)
            return@Column
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x33FFFFFF), unfocusedContainerColor = Color(0x26FFFFFF),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = NimbusColors.Tertiary, unfocusedPlaceholderColor = NimbusColors.Tertiary,
                focusedLeadingIconColor = NimbusColors.Secondary, unfocusedLeadingIconColor = NimbusColors.Secondary,
                focusedTrailingIconColor = NimbusColors.Secondary, unfocusedTrailingIconColor = NimbusColors.Secondary,
                cursorColor = Color.White,
            ),
        )

        val res = results
        if (res != null) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = navBottom + 16.dp)) {
                if (res.isEmpty()) item { Text(stringResource(R.string.no_results), Modifier.padding(12.dp), color = NimbusColors.Secondary) }
                items(res, key = { it.id }) { p ->
                    val already = state.savedPlaces.any { it.id == p.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { if (already) onOpen(p.id) else onAdd(p) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 17.sp, color = Color.White)
                            if (p.subtitle.isNotEmpty()) Text(p.subtitle, fontSize = 13.sp, color = NimbusColors.Secondary)
                        }
                        if (!already) Icon(Icons.Rounded.Add, stringResource(R.string.add), tint = Color.White)
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.currentPlace == null && state.locationStatus != LocationStatus.LOADING) {
                item(key = "loc") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF))
                            .clickable(onClick = onRequestLocation).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, tint = Color.White)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.use_my_location), color = Color.White, fontSize = 16.sp)
                    }
                }
            }
            items(state.pages, key = { it.id }) { place ->
                PlaceCard(
                    place, state.states[place.id], state.settings,
                    onLongClick = if (place.isCurrentLocation) null else ({ editing = true }),
                    location = if (place.isCurrentLocation) dev.nimbus.weather.ui.main.locationMark(state, dev.nimbus.weather.ui.main.LocalShelf.current) else null,
                ) { onOpen(place.id) }
            }
            if (saved.isNotEmpty()) item(key = "edit-hint") {
                Text(
                    stringResource(R.string.places_long_press_hint), Modifier.fillMaxWidth().padding(top = 4.dp),
                    fontSize = 12.sp, color = NimbusColors.Tertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
    dev.nimbus.weather.ui.components.NimbusSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 12.dp))
    }
}

private val EditRowHeight = 64.dp
private val EditRowGap = 10.dp

/**
 * Saved places in edit mode: the handle on the right drags a row to a new position (stored on
 * release), the button on the left asks once more before deleting. "My location" is not listed.
 */
@Composable
private fun EditList(
    places: List<Place>, state: UiState, onReorder: (List<String>) -> Unit, onRemove: (Place) -> Unit,
    modifier: Modifier, navBottom: androidx.compose.ui.unit.Dp,
) {
    var confirmId by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = navBottom + 16.dp)) {
        dev.nimbus.weather.ui.components.ReorderableColumn(
            places, key = { it.id }, gap = EditRowGap,
            moveUp = stringResource(R.string.move_up), moveDown = stringResource(R.string.move_down),
            onMove = { list -> onReorder(list.map { it.id }) },
        ) { place, dragging, handle ->
            val id = place.id
            val st = state.states[id]?.data
            val colors = st?.let { SkyScene.from(it).skyColors } ?: listOf(Color(0xFF2A3B57), Color(0xFF34486A), Color(0xFF3E5579))
            Row(
                Modifier.fillMaxWidth().height(EditRowHeight)
                    .graphicsLayer {
                        scaleX = if (dragging) 1.03f else 1f; scaleY = scaleX
                        shape = RoundedCornerShape(14.dp); clip = true
                    }
                    .background(Brush.linearGradient(colors)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (confirmId == id) {
                    androidx.compose.material3.TextButton(
                        onClick = { confirmId = null; onRemove(place) },
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE5484D)),
                    ) { Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.SemiBold) }
                } else {
                    IconButton(onClick = { confirmId = id }) {
                        Icon(Icons.Rounded.RemoveCircle, stringResource(R.string.delete), tint = Color(0xFFFF6B6E))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp).clickable(enabled = confirmId == id) { confirmId = null }) {
                    Text(place.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (place.subtitle.isNotEmpty()) Text(place.subtitle, fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    Icons.Rounded.DragHandle, stringResource(R.string.places_drag), tint = Color.White,
                    modifier = handle.size(56.dp).padding(16.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaceCard(
    place: Place, st: PlaceState?, settings: dev.nimbus.weather.data.model.Settings,
    onLongClick: (() -> Unit)? = null,
    /** For "my location": whether its position is current – the pin with the status dot as on its page. */
    location: dev.nimbus.weather.ui.main.LocationMark? = null,
    onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    val s = st?.data
    val colors = s?.let { SkyScene.from(it).skyColors } ?: listOf(Color(0xFF2A3B57), Color(0xFF34486A), Color(0xFF3E5579))
    val tf = remember(s?.timezone) { s?.let { TimeFormat(it.timezone, DateFormat.is24HourFormat(context)) } }
    Row(
        Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { f -> { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); f() } },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (place.isCurrentLocation) stringResource(R.string.my_location) else place.name,
                        Modifier.weight(1f, fill = false).alignByBaseline(),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (place.isCurrentLocation) Box(Modifier.padding(start = 6.dp)) { dev.nimbus.weather.ui.main.LocationPin(location, size = 18.dp) }
                    if (location != null) LocationDot(location, 22.sp)
                }
                Text(
                    if (place.isCurrentLocation) place.name else (tf?.time(System.currentTimeMillis()) ?: place.subtitle),
                    fontSize = 14.sp, color = Color.White, maxLines = 1,
                )
            }
            s?.let { Text(stringResource(Texts.condition(it.current.condition, it.current.isDay)), fontSize = 14.sp, color = Color.White, maxLines = 1) }
        }
        Column(Modifier.fillMaxSize().weight(0.6f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            dev.nimbus.weather.ui.components.BigTemperature(s?.current?.temperature, settings.temperatureUnit, 44.sp, weight = FontWeight.Light)
            val today = s?.daily?.lastOrNull { it.date <= System.currentTimeMillis() }
            if (today != null) {
                dev.nimbus.weather.ui.components.MaxMinStack(today.tempMax, today.tempMin, settings.temperatureUnit, fontSize = 13.sp)
            }
        }
    }
}
