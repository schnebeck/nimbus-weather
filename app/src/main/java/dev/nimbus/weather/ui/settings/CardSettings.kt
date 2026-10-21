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
 * Wraps the changes of the cards – in the settings and on the weather page: each one shows a
 * snackbar with what changed and "Undo", which restores the cards as they were before (order,
 * tiles order, hidden, opted-in and folded cards). A drop on the same place changes nothing and
 * shows nothing; folding alone shows nothing either (it is seen at once and undone the same way).
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
    val msgView = stringResource(R.string.cards_undo_view)
    val views = dev.nimbus.weather.data.model.PageView.entries.associateWith { stringResource(viewLabel(it)) }
    val current by androidx.compose.runtime.rememberUpdatedState(settings)
    return { transform ->
        val before = current
        val after = transform(before)
        val toggled = WeatherCard.entries.firstOrNull { before.shows(it) != after.shows(it) }
        val view = after.view()
        val message = when {
            view != null && view != before.view() -> msgView.format(views.getValue(view))
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
                it.copy(
                    cardOrder = before.cardOrder, tileOrder = before.tileOrder, hiddenCards = before.hiddenCards,
                    enabledCards = before.enabledCards, foldedCards = before.foldedCards,
                )
            }
        }
    }
}

/** The names of the views as the settings and the page's note show them. */
internal fun viewLabel(v: dev.nimbus.weather.data.model.PageView): Int = when (v) {
    dev.nimbus.weather.data.model.PageView.COMPACT -> R.string.view_compact
    dev.nimbus.weather.data.model.PageView.NORMAL -> R.string.view_normal
    dev.nimbus.weather.data.model.PageView.FULL -> R.string.view_full
}

/** Card symbols as on the weather page. */
internal fun cardIcon(c: WeatherCard): androidx.compose.ui.graphics.vector.ImageVector = when (c) {
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
 * Settings → Cards: the page's view in one choice ([PageView]), then an accordion – closed it
 * tells how many cards are shown, open it lists them in page order, each with its switch. The
 * small tiles are one block on the page and open into a list of their own. Arranging, folding
 * and hiding happen on the page itself (hold a card); here the order can go back to the default.
 * Weather alerts always stay on top.
 */
@Composable
internal fun CardsAccordion(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var tilesOpen by rememberSaveable { mutableStateOf(false) }
    val all = WeatherCard.entries.filter { it != WeatherCard.TILES }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Text(stringResource(R.string.settings_view), fontSize = 16.sp, color = Color.White)
    Text(stringResource(R.string.settings_view_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
    Spacer(Modifier.size(10.dp))
    val views = dev.nimbus.weather.data.model.PageView.entries
    Segmented(views.map { stringResource(viewLabel(it)) }, views.indexOf(settings.view())) { i ->
        onChange { it.withView(views[i]) }
    }
    Spacer(Modifier.size(14.dp))
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
            CardRow(WeatherCard.ALERTS, settings, onChange, fixed = true)
            settings.orderedCards().forEach { card ->
                CardRow(card, settings, onChange, expandable = card == WeatherCard.TILES, expanded = tilesOpen) { tilesOpen = !tilesOpen }
                if (card == WeatherCard.TILES && tilesOpen) {
                    Column(Modifier.padding(start = 28.dp)) {
                        settings.orderedTiles().forEach { tile -> CardRow(tile, settings, onChange, enabled = settings.shows(WeatherCard.TILES)) }
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

/** One card: symbol, name, (group: open/close), switch. [fixed]: it always stays on top. */
@Composable
private fun CardRow(
    card: WeatherCard, settings: Settings, onChange: ((Settings) -> Settings) -> Unit, fixed: Boolean = false,
    expandable: Boolean = false, expanded: Boolean = false, enabled: Boolean = true, onExpand: () -> Unit = {},
) {
    val shown = settings.shows(card)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(8.dp))
        Icon(cardIcon(card), null, tint = if (shown && enabled) NimbusColors.Secondary else NimbusColors.Tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f).then(if (expandable) Modifier.clickable(onClick = onExpand) else Modifier)) {
            Text(stringResource(cardLabel(card)), fontSize = 15.sp, color = if (shown && enabled) Color.White else NimbusColors.Tertiary)
            if (fixed) Text(stringResource(R.string.settings_cards_fixed), fontSize = 12.sp, color = NimbusColors.Tertiary)
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
internal fun cardLabel(c: WeatherCard): Int = when (c) {
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
