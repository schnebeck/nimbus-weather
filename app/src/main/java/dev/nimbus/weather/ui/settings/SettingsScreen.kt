/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/SettingsScreen.kt
 * Settings: forecast model, units, station data, radar preload, animations.
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

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import dev.nimbus.weather.ui.components.NimbusSnackbarHost
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.BuildConfig
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WindUnit
import dev.nimbus.weather.ui.theme.NimbusColors

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onOpenLicenses: () -> Unit, onBack: () -> Unit) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val cardsChange = undoableCardsChange(settings, onChange, snackbar)
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBars)
            // Tablet: a readable column in the middle, the background still fills the screen
            .wrapContentWidth().widthIn(max = 720.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close), tint = Color.White) }
            Text(stringResource(R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(stringResource(R.string.settings_forecast_source)) {
                    val models = listOf(
                        Triple(ForecastModel.DWD_ICON, R.string.model_dwd_icon, R.string.model_dwd_icon_desc),
                        Triple(ForecastModel.BEST_MATCH, R.string.model_best_match, R.string.model_best_match_desc),
                        Triple(ForecastModel.ECMWF, R.string.model_ecmwf, R.string.model_ecmwf_desc),
                        Triple(ForecastModel.METEO_FRANCE, R.string.model_meteofrance, R.string.model_meteofrance_desc),
                    )
                    models.forEach { (m, title, desc) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange { it.copy(model = m) } }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.model == m, onClick = { onChange { it.copy(model = m) } },
                                colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = NimbusColors.Tertiary),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(title), fontSize = 16.sp, color = Color.White)
                                Text(stringResource(desc), fontSize = 13.sp, color = NimbusColors.Secondary)
                            }
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.settings_units)) {
                    Label(stringResource(R.string.settings_temperature))
                    Segmented(listOf("°C", "°F"), TemperatureUnit.entries.indexOf(settings.temperatureUnit)) { i ->
                        onChange { it.copy(temperatureUnit = TemperatureUnit.entries[i]) }
                    }
                    Spacer(Modifier.size(12.dp))
                    Label(stringResource(R.string.settings_wind))
                    Segmented(
                        listOf(R.string.unit_kmh, R.string.unit_ms, R.string.unit_mph, R.string.unit_kn, R.string.unit_bft).map { stringResource(it) },
                        WindUnit.entries.indexOf(settings.windUnit),
                    ) { i -> onChange { it.copy(windUnit = WindUnit.entries[i]) } }
                    Spacer(Modifier.size(12.dp))
                    Label(stringResource(R.string.settings_precipitation))
                    Segmented(listOf(stringResource(R.string.unit_mm), stringResource(R.string.unit_inch)), PrecipitationUnit.entries.indexOf(settings.precipitationUnit)) { i ->
                        onChange { it.copy(precipitationUnit = PrecipitationUnit.entries[i]) }
                    }
                }
            }
            item {
                Section(null) {
                    ToggleRow(stringResource(R.string.settings_stations), stringResource(R.string.settings_stations_desc), settings.useStationObservations) { v ->
                        onChange { it.copy(useStationObservations = v) }
                    }
                    Spacer(Modifier.size(8.dp))
                    ToggleRow(stringResource(R.string.settings_animations), stringResource(R.string.settings_animations_desc), settings.animationsEnabled) { v ->
                        onChange { it.copy(animationsEnabled = v) }
                    }
                    Spacer(Modifier.size(8.dp))
                    ToggleRow(stringResource(R.string.settings_preload_radar), stringResource(R.string.settings_preload_radar_desc), settings.preloadRadar) { v ->
                        onChange { it.copy(preloadRadar = v) }
                    }
                }
            }
            item {
                Section(stringResource(R.string.settings_cards)) { CardsAccordion(settings, cardsChange) }
            }
            item {
                Section(stringResource(R.string.settings_radar_colors)) {
                    Text(stringResource(R.string.settings_radar_colors_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                    Spacer(Modifier.size(10.dp))
                    Segmented(
                        listOf(stringResource(R.string.radar_colors_contrast), stringResource(R.string.radar_colors_blue)),
                        dev.nimbus.weather.data.model.RadarColors.entries.indexOf(settings.radarColors),
                    ) { i -> onChange { it.copy(radarColors = dev.nimbus.weather.data.model.RadarColors.entries[i]) } }
                    Spacer(Modifier.size(10.dp))
                    RadarScalePreview(settings.radarColors)
                }
            }
            item {
                Section(stringResource(R.string.bathing_title)) {
                    Text(stringResource(R.string.settings_bathing_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                    Spacer(Modifier.size(10.dp))
                    Segmented(BathingRadii.map { "$it km" }, BathingRadii.indexOf(settings.bathingRadiusKm).coerceAtLeast(0)) { i ->
                        onChange { it.copy(bathingRadiusKm = BathingRadii[i]) }
                    }
                }
            }
            item {
                Section(stringResource(R.string.pollen_forecast)) {
                    Text(stringResource(R.string.settings_pollen_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                    Spacer(Modifier.size(10.dp))
                    dev.nimbus.weather.ui.main.PollenTypePicker(settings.pollenTypes) { types -> onChange { it.copy(pollenTypes = types) } }
                }
            }
            item {
                Section(stringResource(R.string.settings_language)) {
                    Text(stringResource(R.string.settings_language_desc), fontSize = 14.sp, color = NimbusColors.Secondary)
                }
            }
            item {
                Section(stringResource(R.string.settings_about)) {
                    Text(stringResource(R.string.settings_about_text), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), fontSize = 12.sp, color = NimbusColors.Tertiary)
                    Spacer(Modifier.size(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenLicenses).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.licenses_title), fontSize = 16.sp, color = Color.White)
                            Text(stringResource(R.string.licenses_settings_desc), fontSize = 13.sp, color = NimbusColors.Secondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NimbusColors.Tertiary)
                    }
                }
            }
        }
    }
    NimbusSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 12.dp))
    }
}

private val BathingRadii = listOf(10, 25, 50, 100)

/** Both scales of the chosen radar colours on the map's land colour, as on the radar screen. */
@Composable
private fun RadarScalePreview(colors: dev.nimbus.weather.data.model.RadarColors) {
    val (rain, snow) = remember(colors) {
        val before = dev.nimbus.weather.ui.radar.RadarPalette.scheme
        dev.nimbus.weather.ui.radar.RadarPalette.scheme = colors
        val r = dev.nimbus.weather.ui.radar.RadarPalette.legendRain.map { Color(it) }
        val s = dev.nimbus.weather.ui.radar.RadarPalette.legendSnow.map { Color(it) }
        dev.nimbus.weather.ui.radar.RadarPalette.scheme = before
        r to s
    }
    @Composable
    fun bar(label: String, c: List<Color>) {
        Text(label, fontSize = 12.sp, color = NimbusColors.Secondary)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            drawRect(Color(0xFF505E6F))
            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(c))
        }
        Spacer(Modifier.size(6.dp))
    }
    bar(stringResource(R.string.legend_rain), rain)
    bar(stringResource(R.string.legend_snow), snow)
}

/**
 * Wraps the changes of the cards list: each one shows a snackbar with what changed and "Undo",
 * which restores the cards as they were before (order, tiles order, hidden and opted-in cards).
 * A drop on the same place changes nothing and shows nothing.
 */
@Composable
private fun undoableCardsChange(
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
private fun CardsAccordion(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
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

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NimbusColors.Tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).padding(12.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 14.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color(0x40FFFFFF), activeContentColor = Color.White,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = NimbusColors.Secondary,
                    activeBorderColor = Color(0x40FFFFFF), inactiveBorderColor = Color(0x40FFFFFF),
                ),
                icon = {},
            ) { Text(label, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = Color.White)
            Text(desc, fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3D8BFF), checkedThumbColor = Color.White),
        )
    }
}
