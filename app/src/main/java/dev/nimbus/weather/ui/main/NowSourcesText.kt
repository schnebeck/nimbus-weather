/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/NowSourcesText.kt
 * Where each value of the weather now comes from, for the station line's explanation.
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.NowValue
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.util.Units

/** The values a line names, in the order of the header and the tiles. */
private val named = listOf(
    NowValue.TEMPERATURE to R.string.now_value_temperature,
    NowValue.HUMIDITY to R.string.now_value_humidity,
    NowValue.DEW_POINT to R.string.now_value_dew_point,
    NowValue.PRESSURE to R.string.now_value_pressure,
    NowValue.WIND to R.string.now_value_wind,
    NowValue.GUSTS to R.string.now_value_gusts,
    NowValue.VISIBILITY to R.string.now_value_visibility,
)

/**
 * One line per value: measured (at which station) or the model's – the sky with what decided it.
 * Empty for weather stored before the values were noted (it cannot say).
 */
@Composable
fun nowSourcesText(c: CurrentWeather): String {
    if (c.measured.isEmpty()) return ""
    val at = c.measured.associateBy { it.value }
    val lines = named.map { (v, name) ->
        val label = stringResource(name)
        at[v]?.let { stringResource(R.string.now_source_measured, label, it.station, Units.oneDecimal(it.distanceKm)) }
            ?: stringResource(R.string.now_source_model, label)
    }
    val sky = at[NowValue.SKY]
    val skyLine = when {
        c.sky == SkyBasis.MEASURED_SUNSHINE && sky != null -> stringResource(R.string.now_sky_measured_sunshine, sky.station, Units.oneDecimal(sky.distanceKm))
        c.sky == SkyBasis.OBSERVED && sky != null -> stringResource(R.string.now_sky_observed, sky.station, Units.oneDecimal(sky.distanceKm))
        c.sky == SkyBasis.MODEL_SUNSHINE -> stringResource(R.string.now_sky_model_sunshine)
        else -> stringResource(R.string.now_sky_model)
    }
    return (listOf(stringResource(R.string.now_sources_title)) + (lines + skyLine).map { "• $it" }).joinToString("\n")
}
