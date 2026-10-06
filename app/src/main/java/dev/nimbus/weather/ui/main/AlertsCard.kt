/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/AlertsCard.kt
 * Weather warnings and the states' flood alerts.
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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors

fun severityColor(s: AlertSeverity): Color = when (s) {
    AlertSeverity.MINOR -> Color(0xFFFFE14D)
    AlertSeverity.MODERATE -> Color(0xFFFF9F1C)
    AlertSeverity.SEVERE -> Color(0xFFFF3B30)
    AlertSeverity.EXTREME -> Color(0xFFB0189A)
}

@Composable
fun AlertsCard(alerts: List<WeatherAlert>) {
    val tf = LocalTimeFormat.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassCard(
        title = stringResource(R.string.alerts) + " · DWD",
        icon = Icons.Rounded.WarningAmber,
        tint = Color(0x4D3A1010),
        onClick = { expanded = !expanded },
        info = Term.ALERTS,
    ) {
        val shown = if (expanded) alerts else alerts.take(2)
        shown.forEachIndexed { i, a ->
            if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(severityColor(a.severity)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.animateContentSize()) {
                    Text(a.headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    val period = when {
                        a.onset != null && a.expires != null -> stringResource(
                            R.string.alert_valid,
                            tf.weekdayShort(a.onset) + " " + tf.time(a.onset),
                            tf.weekdayShort(a.expires) + " " + tf.time(a.expires),
                        )
                        a.expires != null -> stringResource(R.string.alert_until, tf.weekdayShort(a.expires) + " " + tf.time(a.expires))
                        else -> null
                    }
                    if (period != null) Text(period, fontSize = 13.sp, color = NimbusColors.Secondary)
                    if (expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(a.description, fontSize = 14.sp, color = Color.White)
                        a.instruction?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontSize = 13.sp, color = NimbusColors.Secondary)
                        }
                    }
                }
            }
        }
        if (!expanded && alerts.size > 2) {
            Text(stringResource(R.string.more_alerts, alerts.size - 2), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Text(
            stringResource(if (expanded) R.string.show_less else R.string.show_more),
            Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD27A),
        )
    }
}
