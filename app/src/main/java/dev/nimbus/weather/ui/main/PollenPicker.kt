/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PollenPicker.kt
 * Choice of the pollen types the pollen card shows (trees, grasses, herbs or single types).
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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.ui.theme.NimbusColors

/** Groups for quick selection. */
enum class PollenGroup(val label: Int, val types: Set<PollenType>) {
    TREES(R.string.pollen_group_trees, setOf(PollenType.HAZEL, PollenType.ALDER, PollenType.ASH, PollenType.BIRCH, PollenType.OLIVE)),
    GRASSES(R.string.pollen_group_grasses, setOf(PollenType.GRASS, PollenType.RYE)),
    HERBS(R.string.pollen_group_herbs, setOf(PollenType.MUGWORT, PollenType.RAGWEED)),
}

/** Toggles a type; the last selected type cannot be switched off (the card would be empty). */
fun toggled(selected: Set<PollenType>, type: PollenType): Set<PollenType> =
    if (type in selected) (selected - type).ifEmpty { selected } else selected + type

/** A group chip selects exactly the group; tapping the selected group again selects all. */
fun groupSelection(selected: Set<PollenType>, group: PollenGroup): Set<PollenType> =
    if (selected == group.types) PollenType.entries.toSet() else group.types

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollenTypePicker(selected: Set<PollenType>, onChange: (Set<PollenType>) -> Unit) {
    Column {
        Text(stringResource(R.string.pollen_pick_groups), fontSize = 13.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.size(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val all = selected.size == PollenType.entries.size
            Chip(stringResource(R.string.pollen_group_all), all, null) { onChange(PollenType.entries.toSet()) }
            PollenGroup.entries.forEach { g ->
                Chip(stringResource(g.label), selected == g.types, null) { onChange(groupSelection(selected, g)) }
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.pollen_pick_types), fontSize = 13.sp, color = NimbusColors.Secondary)
        Spacer(Modifier.size(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PollenType.entries.forEach { t ->
                Chip(stringResource(pollenName(t)), t in selected, PollenColors.getValue(t)) { onChange(toggled(selected, t)) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) Color(0x40FFFFFF) else Color.Transparent)
            .border(1.dp, if (on) Color(0x80FFFFFF) else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) dot else dot.copy(alpha = 0.35f)))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, color = if (on) Color.White else NimbusColors.Secondary)
    }
}
