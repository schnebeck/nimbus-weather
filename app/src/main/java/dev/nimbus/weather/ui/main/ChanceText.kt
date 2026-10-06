/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ChanceText.kt
 * The chance of precipitation under a weather symbol – in the hourly and the 10-day forecast.
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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.util.NBSP

internal val PrecipBlue = Color(0xFF8FD3FF)

/** Chance of precipitation under a weather symbol: always shown, dimmed below 10 %. */
@Composable
internal fun ChanceText(probability: Double?, amount: Double?) {
    val v = Insights.chanceLabel(probability) ?: return
    val relevant = v >= Insights.CHANCE_RELEVANT
    Text(
        Insights.chanceText(probability, amount) + NBSP + "%", fontSize = 11.sp,
        fontWeight = if (relevant) FontWeight.Bold else FontWeight.Medium,
        color = if (relevant) PrecipBlue else Color(0x99FFFFFF),
        style = ChanceStyle,
    )
}

/** Soft dark shadow keeps the blue percentages readable on bright, cloudy skies. */
internal val ChanceStyle = androidx.compose.ui.text.TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), Offset(0f, 1f), 4f),
)
