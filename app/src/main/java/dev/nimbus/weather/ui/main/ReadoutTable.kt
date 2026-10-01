/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ReadoutTable.kt
 * The values under a chart cursor as a table: fixed cells, so nothing jumps while sliding.
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.ui.theme.NimbusColors

/** Digits of equal width: a value changing from 9 to 10 does not shift its neighbours. */
private val Value = TextStyle(fontSize = 13.sp, color = Color.White, fontFeatureSettings = "tnum")
private val Label = TextStyle(fontSize = 13.sp, color = NimbusColors.Secondary)
private val Head = TextStyle(fontSize = 11.sp, color = NimbusColors.Tertiary, fontWeight = FontWeight.SemiBold)

/** Empty cell: the value does not exist for this hour (e.g. no reading yet). */
const val NO_VALUE = "–"

/**
 * Rows of a label and one value per column, the columns headed by [columns] (e.g. "Measured",
 * "Forecast"). Every cell has a fixed width and holds one value only.
 */
@Composable
fun ReadoutTable(columns: List<String>, rows: List<Pair<String, List<String>>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.3f))
            columns.forEach { Text(it, Modifier.weight(1f), style = Head, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        rows.forEach { (label, values) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, Modifier.weight(1.3f), style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                values.forEach { Text(it, Modifier.weight(1f), style = Value, textAlign = TextAlign.End, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** Label/value pairs, two per row: "Temperature 15° | Feels like 14°". */
@Composable
fun ReadoutPairs(pairs: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        pairs.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, (label, value) ->
                    if (i > 0) Spacer(Modifier.width(14.dp))
                    Text(label, Modifier.weight(1.15f), style = Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(value, Modifier.weight(1f), style = Value, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                }
                if (row.size == 1) { Spacer(Modifier.width(14.dp)); Spacer(Modifier.weight(2.15f)) }
            }
        }
    }
}

/** One line of cells, each a small heading over its value: "21–22 | Amount 0.3 mm | Chance 80 %". */
@Composable
fun ReadoutCells(cells: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(label, style = Head, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = Value.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
            }
        }
    }
}
