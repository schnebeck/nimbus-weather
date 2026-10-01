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
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBars),
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
                Section(stringResource(R.string.settings_cards)) {
                    Text(stringResource(R.string.settings_cards_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
                    Spacer(Modifier.size(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WeatherCard.entries.forEach { c ->
                            dev.nimbus.weather.ui.main.SelectChip(stringResource(cardLabel(c)), settings.shows(c)) {
                                onChange { st -> st.copy(hiddenCards = if (st.shows(c)) st.hiddenCards + c else st.hiddenCards - c) }
                            }
                        }
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
