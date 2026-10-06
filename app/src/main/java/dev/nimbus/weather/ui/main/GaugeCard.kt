/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/GaugeCard.kt
 * Water level card: tides at the coast and on tidal rivers, otherwise the nearest river gauge.
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

import dev.nimbus.weather.ui.components.HairlineDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Waves
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
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units
import kotlin.math.roundToInt

internal val MeasuredLine = Color.White

internal val PredictedLine = Color(0xFF9CC8FF)

private val LowColor = Color(0xFFFFC56B)
private val HighColor = Color(0xFFFF7A5C)

/**
 * Water level card: tides first where there is a tide gauge, then one row per water body; the
 * selected row shows its details below (level, rating, course of the week).
 */
@Composable
fun GaugeCard(gauges: List<GaugeInfo>, now: Long) {
    if (gauges.isEmpty()) return
    val tide = gauges.firstOrNull { it.tidal && it.extremes.isNotEmpty() }
    val others = gauges.filter { it !== tide }
    var selectedId by androidx.compose.runtime.saveable.rememberSaveable(others.map { it.uuid }) { mutableStateOf(others.firstOrNull()?.uuid) }
    val selected = others.firstOrNull { it.uuid == selectedId } ?: others.firstOrNull()
    GlassCard(
        title = stringResource(if (tide != null) R.string.gauge_title_tides else R.string.gauge_title_level),
        icon = Icons.Outlined.Waves,
        info = if (tide != null) Term.TIDES else Term.GAUGE,
    ) {
        if (tide != null) {
            GaugeTitle(tide)
            TideContent(tide, now)
            Spacer(Modifier.height(8.dp))
            StationLine(tide)
            Text(stringResource(R.string.gauge_tide_note), fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp)
        }
        if (others.isNotEmpty()) {
            if (tide != null) {
                Spacer(Modifier.height(10.dp))
                HairlineDivider()
                Spacer(Modifier.height(8.dp))
            }
            if (others.size > 1 || tide != null) {
                others.forEach { g -> GaugeRow(g, g === selected && others.size > 1) { selectedId = g.uuid } }
                Spacer(Modifier.height(8.dp))
            }
            selected?.let { g ->
                if (others.size > 1 || tide != null) { HairlineDivider(); Spacer(Modifier.height(10.dp)) }
                else GaugeTitle(g)      // a single gauge has no list naming its river
                LevelContent(g, now)
                Spacer(Modifier.height(8.dp))
                StationLine(g)
            }
        }
    }
}

/** "Leine · Herrenhausen · 4,3 km" – which water the card is about, above the values. */
@Composable
private fun GaugeTitle(g: GaugeInfo) {
    val water = titleCase(g.water)
    Row(verticalAlignment = Alignment.Bottom) {
        if (water.isNotBlank()) {
            Text(water, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            titleCase(g.name) + " · " + Units.oneDecimal(g.distanceKm) + NBSP + "km",
            fontSize = 13.sp, color = NimbusColors.Secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 1.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
}

/** "Pegel Heinde (Innerste), 0,8 km entfernt · Daten: NLWKN · Pegelnull …" */
@Composable
private fun StationLine(g: GaugeInfo) {
    val place = stringResource(R.string.gauge_station, titleCase(g.name), titleCase(g.water), Units.oneDecimal(g.distanceKm))
    val by = " · " + stringResource(R.string.gauge_data_by, providerName(g.provider))
    val zero = g.gaugeZero?.let { " · " + stringResource(R.string.gauge_zero, String.format(java.util.Locale.getDefault(), "%.2f", it)) } ?: ""
    Text(place + by + zero, fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp)
}

fun providerName(p: dev.nimbus.weather.data.model.GaugeProvider): String = when (p) {
    dev.nimbus.weather.data.model.GaugeProvider.PEGELONLINE -> "PEGELONLINE (WSV)"
    dev.nimbus.weather.data.model.GaugeProvider.NLWKN -> "NLWKN"
    dev.nimbus.weather.data.model.GaugeProvider.LANUK_NRW -> "LANUK NRW"
    dev.nimbus.weather.data.model.GaugeProvider.LFULG_SACHSEN -> "LfULG Sachsen"
    dev.nimbus.weather.data.model.GaugeProvider.HLNUG_HESSEN -> "HLNUG"
    dev.nimbus.weather.data.model.GaugeProvider.LHP -> "LHP"
}

/** Status of a gauge in words and colour: alert level, LHP class, flood mark or MNW/MHW rating. */
@Composable
internal fun gaugeStatus(g: GaugeInfo): Pair<String, Color>? {
    val stage = g.alertStage
    val mark = highestMark(g)
    val kind = stringResource(alertKindName(g.alertKind))
    return when {
        stage != null && stage > 0 -> stringResource(R.string.gauge_alert_stage, kind, stage) to HighColor
        (g.lhpClass ?: 0) > 0 -> (g.lhpClassName ?: stringResource(R.string.gauge_state_high)) to HighColor
        mark != null -> stringResource(R.string.gauge_mark_exceeded, mark) to HighColor
        g.state == "high" -> stringResource(R.string.gauge_state_high) to HighColor
        g.state == "low" -> stringResource(R.string.gauge_state_low) to LowColor
        g.stateText != null -> g.stateText to (if (g.stateText.contains("Niedrig", true)) LowColor else Color.White)
        g.state == "normal" -> stringResource(R.string.gauge_state_normal) to Color.White
        stage == 0 && g.alertLevels.isNotEmpty() -> stringResource(R.string.gauge_alert_none, kind) to Color.White
        g.lhpClass == -1 -> stringResource(R.string.gauge_no_data) to NimbusColors.Secondary
        g.lhpClassName != null -> g.lhpClassName to Color.White
        else -> null
    }
}

internal fun alertKindName(k: dev.nimbus.weather.data.model.AlertKind) = when (k) {
    dev.nimbus.weather.data.model.AlertKind.MELDESTUFE -> R.string.alert_kind_ms
    dev.nimbus.weather.data.model.AlertKind.INFORMATIONSWERT -> R.string.alert_kind_iw
    dev.nimbus.weather.data.model.AlertKind.ALARMSTUFE -> R.string.alert_kind_as
}

internal fun alertKindShort(k: dev.nimbus.weather.data.model.AlertKind) = when (k) {
    dev.nimbus.weather.data.model.AlertKind.MELDESTUFE -> R.string.alert_kind_ms_short
    dev.nimbus.weather.data.model.AlertKind.INFORMATIONSWERT -> R.string.alert_kind_iw_short
    dev.nimbus.weather.data.model.AlertKind.ALARMSTUFE -> R.string.alert_kind_as_short
}

/** One water body: river name, gauge, level with trend or the classification. */
@Composable
private fun GaugeRow(g: GaugeInfo, selected: Boolean, onClick: () -> Unit) {
    val status = gaugeStatus(g)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(status?.second ?: NimbusColors.Tertiary))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(titleCase(g.water), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
            Text(titleCase(g.name) + " · " + Units.oneDecimal(g.distanceKm) + NBSP + "km", fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (g.level != null) {
                val t = g.tendency ?: trend(g.history)
                Text("${g.level.roundToInt()}${NBSP}cm" + (t?.let { " " + arrow(it) } ?: ""), fontSize = 15.sp, color = Color.White)
            }
            status?.let { Text(it.first, fontSize = 12.sp, color = it.second, maxLines = 1) }
        }
    }
}

internal fun arrow(t: Int) = when { t > 0 -> "↑"; t < 0 -> "↓"; else -> "→" }

/** PEGELONLINE names are upper case: "CUXHAVEN STEUBENHÖFT" -> "Cuxhaven Steubenhöft". */
fun titleCase(s: String): String = Regex("[\\p{L}]+").replace(s.lowercase()) { m -> m.value.replaceFirstChar { it.uppercase() } }
