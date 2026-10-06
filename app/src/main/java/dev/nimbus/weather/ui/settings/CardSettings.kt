/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/CardSettings.kt
 * Settings of the cards: their order and which are shown, with undo.
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

package dev.nimbus.weather.ui.settings

import kotlinx.coroutines.launch
import dev.nimbus.weather.ui.components.ReorderableColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.DragHandle
import dev.nimbus.weather.data.model.WeatherCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.theme.NimbusColors

/**
 * Wraps the changes of the cards list: each one shows a snackbar with what changed and "Undo",
 * which restores the cards as they were before (order, tiles order, hidden and opted-in cards).
 * A drop on the same place changes nothing and shows nothing.
 */
@Composable
internal fun undoableCardsChange(
    settings: Settings, onChange: ((Settings) -> Settings) -> Unit, snackbar: androidx.compose.material3.SnackbarHostState,
): ((Settings) -> Settings) -> Unit {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val labels = WeatherCard.entries.associateWith { stringResource(cardLabel(it)) }
    val undo = stringResource(R.string.undo)
    val msgOrder = stringResource(R.string.cards_undo_order)
    val msgReset = stringResource(R.string.cards_undo_reset)
    val msgShown = stringResource(R.string.cards_undo_shown)
    val msgHidden = stringResource(R.string.cards_undo_hidden)
    val current by androidx.compose.runtime.rememberUpdatedState(settings)
    return { transform ->
        val before = current
        val after = transform(before)
        val toggled = WeatherCard.entries.firstOrNull { before.shows(it) != after.shows(it) }
        val message = when {
            toggled != null -> (if (after.shows(toggled)) msgShown else msgHidden).format(labels.getValue(toggled))
            before.orderedCards() == after.orderedCards() && before.orderedTiles() == after.orderedTiles() -> null
            after.cardOrder.isEmpty() && after.tileOrder.isEmpty() -> msgReset
            else -> msgOrder
        }
        onChange(transform)
        if (message != null) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = undo, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onChange {
                it.copy(cardOrder = before.cardOrder, tileOrder = before.tileOrder, hiddenCards = before.hiddenCards, enabledCards = before.enabledCards)
            }
        }
    }
}

/** Card symbols as on the weather page. */
private fun cardIcon(c: WeatherCard): androidx.compose.ui.graphics.vector.ImageVector = when (c) {
    WeatherCard.ALERTS -> Icons.Rounded.WarningAmber
    WeatherCard.HOURLY -> Icons.Outlined.Schedule
    WeatherCard.DAILY -> Icons.Outlined.CalendarMonth
    WeatherCard.PRECIPITATION -> Icons.Outlined.WaterDrop
    WeatherCard.RADAR -> Icons.Outlined.Map
    WeatherCard.TILES -> Icons.Outlined.GridView
    WeatherCard.FEELS_LIKE -> Icons.Outlined.Thermostat
    WeatherCard.UV_INDEX -> Icons.Outlined.WbSunny
    WeatherCard.WIND -> Icons.Outlined.Air
    WeatherCard.HUMIDITY -> Icons.Outlined.WaterDrop
    WeatherCard.VISIBILITY -> Icons.Outlined.Visibility
    WeatherCard.PRESSURE -> Icons.Outlined.Compress
    WeatherCard.SUN -> Icons.Outlined.WbTwilight
    WeatherCard.MOON -> Icons.Outlined.DarkMode
    WeatherCard.AIR_QUALITY -> Icons.Outlined.Masks
    WeatherCard.POLLEN -> Icons.Outlined.Grass
    WeatherCard.GAUGES -> Icons.Outlined.Waves
    WeatherCard.BATHING -> Icons.Outlined.Pool
    WeatherCard.COMMUNITY -> Icons.Outlined.Groups
    WeatherCard.MODELS -> Icons.Outlined.QueryStats
    WeatherCard.PRESSURE_CHART -> Icons.Outlined.Timeline
}

/**
 * Settings → Cards: an accordion. Closed it tells how many cards are shown; open it lists them
 * in page order – the handle ≡ sorts, the switch shows or hides. The small tiles are one block
 * on the page and open into a list of their own. Weather alerts always stay on top.
 */
@Composable
internal fun CardsAccordion(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var tilesOpen by rememberSaveable { mutableStateOf(false) }
    val all = WeatherCard.entries.filter { it != WeatherCard.TILES }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_cards_arrange), fontSize = 16.sp, color = Color.White)
            Text(stringResource(R.string.settings_cards_count, all.count { settings.shows(it) && (it !in WeatherCard.DEFAULT_TILES || settings.shows(WeatherCard.TILES)) }, all.size), fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Icon(Icons.Rounded.ExpandMore, null, tint = NimbusColors.Secondary, modifier = Modifier.graphicsLayer { rotationZ = rotation })
    }
    // Accordion: opens downwards from the header (the default grows diagonally from a corner)
    androidx.compose.animation.AnimatedVisibility(
        open,
        enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
    ) {
        Column(Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.settings_cards_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
            Spacer(Modifier.size(6.dp))
            CardRow(WeatherCard.ALERTS, settings, onChange, handle = null)
            val up = stringResource(R.string.move_up)
            val down = stringResource(R.string.move_down)
            ReorderableColumn(
                settings.orderedCards(), key = { it }, moveUp = up, moveDown = down,
                onMove = { list -> onChange { it.copy(cardOrder = list) } },
            ) { card, dragging, handle ->
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(if (dragging) Color(0xFF2A3A55) else Color.Transparent)) {
                    CardRow(card, settings, onChange, handle, expandable = card == WeatherCard.TILES, expanded = tilesOpen) { tilesOpen = !tilesOpen }
                    if (card == WeatherCard.TILES && tilesOpen) {
                        ReorderableColumn(
                            settings.orderedTiles(), key = { it }, moveUp = up, moveDown = down,
                            onMove = { list -> onChange { it.copy(tileOrder = list) } },
                            modifier = Modifier.padding(start = 28.dp),
                        ) { tile, tileDragging, tileHandle ->
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (tileDragging) Color(0xFF2A3A55) else Color.Transparent)) {
                                CardRow(tile, settings, onChange, tileHandle, enabled = settings.shows(WeatherCard.TILES))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.settings_cards_reset),
                Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp))
                    .clickable { onChange { it.copy(cardOrder = emptyList(), tileOrder = emptyList()) } }.padding(8.dp),
                fontSize = 14.sp, color = Color(0xFF9CC8FF), fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One card: handle, symbol, name, (group: open/close), switch. Without [handle] it is fixed. */
@Composable
private fun CardRow(
    card: WeatherCard, settings: Settings, onChange: ((Settings) -> Settings) -> Unit, handle: Modifier?,
    expandable: Boolean = false, expanded: Boolean = false, enabled: Boolean = true, onExpand: () -> Unit = {},
) {
    val shown = settings.shows(card)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (handle != null) {
            Icon(
                Icons.Rounded.DragHandle, stringResource(R.string.places_drag), tint = NimbusColors.Secondary,
                modifier = handle.size(40.dp).padding(8.dp),
            )
        } else Spacer(Modifier.size(40.dp))
        Icon(cardIcon(card), null, tint = if (shown && enabled) NimbusColors.Secondary else NimbusColors.Tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f).then(if (expandable) Modifier.clickable(onClick = onExpand) else Modifier)) {
            Text(stringResource(cardLabel(card)), fontSize = 15.sp, color = if (shown && enabled) Color.White else NimbusColors.Tertiary)
            if (handle == null) Text(stringResource(R.string.settings_cards_fixed), fontSize = 12.sp, color = NimbusColors.Tertiary)
        }
        if (expandable) {
            IconButton(onClick = onExpand) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    stringResource(R.string.settings_cards_tiles_expand), tint = NimbusColors.Secondary,
                )
            }
        }
        Switch(
            checked = shown, enabled = enabled,
            onCheckedChange = { v -> onChange { st -> st.withCard(card, v) } },
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3D8BFF), checkedThumbColor = Color.White),
        )
    }
}

/** Card titles as shown on the weather page. */
private fun cardLabel(c: WeatherCard): Int = when (c) {
    WeatherCard.ALERTS -> R.string.alerts
    WeatherCard.HOURLY -> R.string.hourly_forecast
    WeatherCard.DAILY -> R.string.ten_day_forecast
    WeatherCard.PRECIPITATION -> R.string.precip_title
    WeatherCard.RADAR -> R.string.precipitation_map
    WeatherCard.FEELS_LIKE -> R.string.feels_like
    WeatherCard.UV_INDEX -> R.string.uv_index
    WeatherCard.WIND -> R.string.wind
    WeatherCard.HUMIDITY -> R.string.humidity
    WeatherCard.VISIBILITY -> R.string.visibility
    WeatherCard.PRESSURE -> R.string.pressure
    WeatherCard.SUN -> R.string.sun
    WeatherCard.MOON -> R.string.moon
    WeatherCard.AIR_QUALITY -> R.string.air_quality
    WeatherCard.POLLEN -> R.string.pollen_forecast
    WeatherCard.GAUGES -> R.string.gauge_title_level
    WeatherCard.BATHING -> R.string.bathing_title
    WeatherCard.TILES -> R.string.settings_cards_tiles
    WeatherCard.PRESSURE_CHART -> R.string.pressure_chart_title
    WeatherCard.COMMUNITY -> R.string.community_sensors
    WeatherCard.MODELS -> R.string.model_comparison
}
