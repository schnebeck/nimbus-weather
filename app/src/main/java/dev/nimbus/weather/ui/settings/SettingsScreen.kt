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

import dev.nimbus.weather.ui.components.statusBarsStable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import dev.nimbus.weather.ui.components.NimbusSnackbarHost
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.model.WindUnit
import dev.nimbus.weather.ui.theme.NimbusColors

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onOpenLicenses: () -> Unit, onBack: () -> Unit) {
    val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val cardsChange = undoableCardsChange(settings, onChange, snackbar)
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF111D33))))
            .windowInsetsPadding(WindowInsets.statusBarsStable)
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
                    ModelChoices.forEach { (m, title, desc) ->
                        ModelChoice(settings.model == m, stringResource(title), stringResource(desc)) { onChange { it.copy(model = m) } }
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
                    ToggleRow(stringResource(R.string.settings_fullscreen), stringResource(R.string.settings_fullscreen_desc), settings.fullscreen) { v ->
                        onChange { it.copy(fullscreen = v) }
                    }
                    ToggleRow(stringResource(R.string.settings_fullscreen_button), stringResource(R.string.settings_fullscreen_button_desc), settings.fullscreenButton) { v ->
                        onChange { it.copy(fullscreenButton = v) }
                    }
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
                    CardsAccordion(settings, cardsChange)
                    Spacer(Modifier.size(8.dp))
                    ToggleRow(stringResource(R.string.settings_separate_precip), stringResource(R.string.settings_separate_precip_desc), settings.separatePrecipitation) { v ->
                        onChange { it.copy(separatePrecipitation = v) }
                    }
                    ToggleRow(stringResource(R.string.settings_show_dry_precip), stringResource(R.string.settings_show_dry_precip_desc), settings.showDryPrecipitation) { v ->
                        onChange { it.copy(showDryPrecipitation = v) }
                    }
                }
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
