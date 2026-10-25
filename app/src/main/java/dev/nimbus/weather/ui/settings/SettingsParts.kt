/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/settings/SettingsParts.kt
 * The building blocks of the settings: sections, labels, segmented buttons, switches, model choice.
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

import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.RadarOverlay
import dev.nimbus.weather.ui.radar.ToggleChip
import dev.nimbus.weather.ui.theme.NimbusColors

@Composable
internal fun Section(title: String?, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NimbusColors.Tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).padding(12.dp)) { content() }
    }
}

/**
 * The settings of a card: while the card is on the page its [content]; hidden, the section folds
 * to one line with a switch that brings the card back – and the section opens with it, as who
 * switches a card on mostly sets it up. The line stays: a setting looked for is found.
 */
@Composable
internal fun CardSection(title: String, shown: Boolean, onShow: () -> Unit, content: @Composable () -> Unit) {
    Section(title) {
        androidx.compose.animation.AnimatedVisibility(
            shown,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) { Column { content() } }
        androidx.compose.animation.AnimatedVisibility(
            !shown,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            ToggleRow(stringResource(R.string.settings_card_hidden), stringResource(R.string.settings_card_hidden_desc), false) { on -> if (on) onShow() }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, fontSize = 14.sp, color = NimbusColors.Secondary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
internal fun ToggleRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
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

/** The forecast models to choose from: model, name, what it is good for. */
internal val ModelChoices = listOf(
    Triple(ForecastModel.BEST_MATCH, R.string.model_best_match, R.string.model_best_match_desc),
    Triple(ForecastModel.DWD_ICON, R.string.model_dwd_icon, R.string.model_dwd_icon_desc),
    Triple(ForecastModel.ECMWF, R.string.model_ecmwf, R.string.model_ecmwf_desc),
    Triple(ForecastModel.METEO_FRANCE, R.string.model_meteofrance, R.string.model_meteofrance_desc),
    Triple(ForecastModel.MET_NORWAY, R.string.model_metno, R.string.model_metno_desc),
    Triple(ForecastModel.KNMI, R.string.model_knmi, R.string.model_knmi_desc),
    Triple(ForecastModel.DMI, R.string.model_dmi, R.string.model_dmi_desc),
    Triple(ForecastModel.UKMO, R.string.model_ukmo, R.string.model_ukmo_desc),
    Triple(ForecastModel.METEOSWISS_CH1, R.string.model_ch1, R.string.model_ch1_desc),
    Triple(ForecastModel.METEOSWISS_CH2, R.string.model_ch2, R.string.model_ch2_desc),
    Triple(ForecastModel.GEOSPHERE, R.string.model_geosphere, R.string.model_geosphere_desc),
    Triple(ForecastModel.ITALIA, R.string.model_italia, R.string.model_italia_desc),
)

/** One forecast model to choose (the settings' one, a place's own): a radio button, name and description. */
@Composable
internal fun ModelChoice(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = NimbusColors.Tertiary),
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Color.White)
            Text(description, fontSize = 13.sp, color = NimbusColors.Secondary)
        }
    }
}

/** The layers on when the radar opens, switched as on the radar itself. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RadarOverlaysOnOpening(on: Set<RadarOverlay>, onChange: (Set<RadarOverlay>) -> Unit) {
    Text(stringResource(R.string.settings_radar_overlays), fontSize = 16.sp, color = Color.White)
    Text(stringResource(R.string.settings_radar_overlays_desc), fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp)
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            RadarOverlay.TEMPERATURE to R.string.overlay_temperature,
            RadarOverlay.WIND to R.string.overlay_wind,
            RadarOverlay.SATELLITE to R.string.satellite,
            RadarOverlay.LIGHTNING to R.string.lightning,
            RadarOverlay.WARNINGS to R.string.warnings,
        ).forEach { (o, label) -> ToggleChip(stringResource(label), o in on) { onChange(if (o in on) on - o else on + o) } }
    }
}
