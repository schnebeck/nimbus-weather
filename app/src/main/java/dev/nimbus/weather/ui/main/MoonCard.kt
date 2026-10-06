/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/MoonCard.kt
 * The moon, calculated on the device: phase, rise and set, next full moon.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.foundation.layout.size
import dev.nimbus.weather.ui.components.drawMoonPhase
import dev.nimbus.weather.util.Moon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.nimbus.weather.ui.components.LocalExplain
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import kotlin.math.roundToInt

@Composable
fun MoonCard(data: WeatherData, now: Long) {
    val tf = LocalTimeFormat.current
    val explain = LocalExplain.current
    val lat = data.place.latitude
    val lon = data.place.longitude
    val info = remember(now / 600_000) { Moon.preciseIllumination(now) }
    val times = remember(data.place.id, tf.zoned(now).toLocalDate()) {
        Moon.times(tf.zoned(now).toLocalDate().atStartOfDay(tf.zone).toInstant().toEpochMilli(), lat, lon)
    }
    val nextFull = remember(now / 3_600_000) { Moon.nextFullMoon(now) }
    val phaseName = stringResource(
        when (Moon.phaseOf(info.phase)) {
            Moon.Phase.NEW -> R.string.moon_new
            Moon.Phase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
            Moon.Phase.FIRST_QUARTER -> R.string.moon_first_quarter
            Moon.Phase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
            Moon.Phase.FULL -> R.string.moon_full
            Moon.Phase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
            Moon.Phase.LAST_QUARTER -> R.string.moon_last_quarter
            Moon.Phase.WANING_CRESCENT -> R.string.moon_waning_crescent
        },
    )
    GlassCard(title = stringResource(R.string.moon), icon = Icons.Outlined.DarkMode, info = Term.MOON, onClick = { explain(Term.MOON) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(84.dp)) {
                drawMoonPhase(center, size.minDimension / 2 * 0.92f, info.phase.toFloat(), lat < 0)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(phaseName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 24.sp)
                Text(stringResource(R.string.moon_illumination, (info.fraction * 100).roundToInt()), fontSize = 14.sp, color = NimbusColors.Secondary)
                Spacer(Modifier.height(8.dp))
                when {
                    times.alwaysUp -> Caption(stringResource(R.string.moon_always_up))
                    times.alwaysDown -> Caption(stringResource(R.string.moon_always_down))
                    else -> {
                        MoonRow(stringResource(R.string.moonrise), times.rise?.let { tf.time(it) } ?: "–")
                        MoonRow(stringResource(R.string.moonset), times.set?.let { tf.time(it) } ?: "–")
                    }
                }
                MoonRow(stringResource(R.string.next_full_moon), tf.dayMonth(nextFull))
            }
        }
    }
}

@Composable
private fun MoonRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = NimbusColors.Secondary, maxLines = 1)
        Text(value, fontSize = 13.sp, color = Color.White, maxLines = 1)
    }
}
