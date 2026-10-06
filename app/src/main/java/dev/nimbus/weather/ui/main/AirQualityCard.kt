/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/AirQualityCard.kt
 * Air quality: the European index with particulate matter.
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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.util.Texts
import kotlin.math.roundToInt

@Composable
fun AirQualityCard(data: WeatherData) {
    val aq = data.airQuality ?: return
    val aqi = aq.europeanAqi ?: return
    GlassCard(title = stringResource(R.string.air_quality), icon = Icons.Outlined.Masks, info = Term.AIR_QUALITY) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(aqi.roundToInt().toString(), fontSize = 34.sp, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(Texts.aqiLevel(aqi)), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        GradientScale(
            (aqi / 120.0).toFloat(),
            listOf(Color(0xFF50F0E6), Color(0xFF50CCAA), Color(0xFFF0E641), Color(0xFFFF5050), Color(0xFF960032), Color(0xFF7D2181)),
        )
        Spacer(Modifier.height(10.dp))
        Caption(stringResource(R.string.aqi_detail, aq.pm25?.roundToInt()?.toString() ?: "–", aq.pm10?.roundToInt()?.toString() ?: "–"))
    }
}
