/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/ExtraCards.kt
 * The citizen sensors' card and the sources at the end of the page.
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
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
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlin.math.roundToInt

@Composable
fun CommunityCard(data: WeatherData) {
    val c = data.community ?: return
    val s = LocalSettings.current
    GlassCard(title = stringResource(R.string.community_sensors), icon = Icons.Outlined.Groups, info = Term.COMMUNITY) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            c.temperature?.let { Text(Units.temp(it, s.temperatureUnit), fontSize = 34.sp, color = Color.White) }
            Column(Modifier.padding(bottom = 4.dp)) {
                c.humidity?.let { Text("${stringResource(R.string.humidity)} ${it.roundToInt()}%", fontSize = 14.sp, color = Color.White) }
                c.pressure?.let { Text("${stringResource(R.string.pressure)} ${it.roundToInt()}${NBSP}hPa", fontSize = 14.sp, color = Color.White) }
                if (c.pm25 != null || c.pm10 != null) {
                    Text(stringResource(R.string.pm_values, c.pm25?.roundToInt()?.toString() ?: "–", c.pm10?.roundToInt()?.toString() ?: "–"), fontSize = 14.sp, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val detail = stringResource(R.string.community_sensors_detail, c.sensorCount, c.radiusKm.roundToInt().toString())
        val spread = c.temperatureSpread?.let { " · " + stringResource(R.string.community_spread, Units.oneDecimal(it / 2) + "°") } ?: ""
        Text(detail + spread, fontSize = 12.sp, color = NimbusColors.Secondary)
    }
}

@Composable
fun SourcesFooter(data: WeatherData) {
    val tf = LocalTimeFormat.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Outlined.Info, null, tint = NimbusColors.Secondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.data_sources), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NimbusColors.Secondary)
        }
        Spacer(Modifier.height(4.dp))
        // a single model with its grid: "KNMI Harmonie, 2 km"
        @Composable
        fun part(p: dev.nimbus.weather.data.model.ModelPart) =
            stringResource(R.string.src_part, p.name, if (p.km % 1.0 == 0.0) p.km.toInt().toString() else Units.oneDecimal(p.km))
        val labels = data.sources.map { src ->
            when (src.kind) {
                SourceKind.MODEL_DWD_ICON -> stringResource(R.string.src_dwd_icon)
                SourceKind.MODEL_ECMWF -> stringResource(R.string.src_ecmwf)
                SourceKind.MODEL_METEO_FRANCE -> stringResource(R.string.src_meteofrance)
                SourceKind.MODEL_REGIONAL -> src.part?.let { stringResource(R.string.src_regional, part(it)) } ?: stringResource(R.string.src_best_match)
                SourceKind.MODEL_BEST_MATCH -> stringResource(R.string.src_best_match) +
                    (src.part?.let { " (" + stringResource(R.string.src_part_now, part(it)) + ")" } ?: "")
                // filling what the chosen model lacks – and from its last hour on, everything
                SourceKind.GAP_FILL -> (src.since?.let { stringResource(R.string.src_gap_fill_from, tf.weekdayShort(it) + " " + tf.time(it)) }
                    ?: stringResource(R.string.src_gap_fill)) + (src.part?.let { " (" + part(it) + ")" } ?: "")
                SourceKind.DWD_STATION, SourceKind.STATION -> (src.network ?: dev.nimbus.weather.data.model.StationNetwork.DWD).let { n ->
                    stringResource(R.string.src_station, n.label, src.detail ?: "", n.provider)
                }
                SourceKind.DWD_WARNINGS -> stringResource(R.string.src_warnings)
                SourceKind.CAMS -> stringResource(R.string.src_cams)
                SourceKind.COMMUNITY -> stringResource(R.string.src_community)
                SourceKind.DWD_POLLEN -> stringResource(R.string.src_dwd_pollen)
                SourceKind.PEGELONLINE -> stringResource(R.string.src_pegelonline, titleCase(src.detail ?: ""))
                SourceKind.NLWKN -> stringResource(R.string.src_nlwkn, src.detail ?: "")
                SourceKind.GAUGES -> stringResource(
                    R.string.src_gauges,
                    src.detail.orEmpty().split(",").mapNotNull { n -> runCatching { providerName(dev.nimbus.weather.data.model.GaugeProvider.valueOf(n)) }.getOrNull() }.joinToString(", "),
                )
                SourceKind.LHP_ALERTS -> stringResource(R.string.src_lhp_alerts)
                SourceKind.BATHING -> stringResource(R.string.src_bathing, src.detail?.takeIf { it.isNotBlank() }?.let { ", " + it.replace(",", ", ") } ?: "")
            }
        } + stringResource(R.string.src_moon)
        Text(
            labels.joinToString(" · "), fontSize = 11.sp, color = NimbusColors.Secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.updated_at, tf.time(data.fetchedAt)), fontSize = 11.sp, color = NimbusColors.Tertiary)
    }
}
