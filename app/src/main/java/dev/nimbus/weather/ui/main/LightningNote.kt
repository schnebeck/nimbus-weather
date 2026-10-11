/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/LightningNote.kt
 * The note of lightning near the place, on top of its page while there is any; tapped, the radar with the lightning.
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.remote.LightningNearby
import dev.nimbus.weather.data.remote.LightningSource
import dev.nimbus.weather.ui.components.LaunchedWhileShown
import dev.nimbus.weather.ui.theme.NimbusColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Asked again this often while the page is shown: a storm moves on within minutes. */
private const val ASK_EVERY_MS = 3 * 60_000L

/** Closer than this the satellite cannot tell apart from overhead (its pixel is 4–5 km). */
private const val OVERHEAD_KM = 5.0

/**
 * The lightning near [place] while its page is shown – asked for at once and every few minutes
 * (a box around the place: a few hundred bytes without lightning), never in the background.
 */
@Composable
fun rememberLightningNearby(place: Place): LightningNearby? {
    val context = LocalContext.current
    val source = remember { (context.applicationContext as? NimbusApp)?.container?.http?.let(::LightningSource) }
    var nearby by remember(place.id) { mutableStateOf<LightningNearby?>(null) }
    LaunchedWhileShown(place.id, source) {
        if (source == null) return@LaunchedWhileShown
        while (true) {
            nearby = source.nearby(place.latitude, place.longitude, System.currentTimeMillis())
            delay(ASK_EVERY_MS)
        }
    }
    return nearby
}

/** "Lightning nearby – nearest 12 km away · 3 min ago · 27 flashes in 15 min"; tapped, the radar's lightning. */
@Composable
fun LightningNote(nearby: LightningNearby, onOpen: () -> Unit) {
    val where = if (nearby.distanceKm < OVERHEAD_KM) stringResource(R.string.lightning_overhead)
    else stringResource(R.string.lightning_nearest_km, nearby.distanceKm.roundToInt())
    val ago = if (nearby.minutesAgo < 1) stringResource(R.string.lightning_just_now) else stringResource(R.string.lightning_minutes_ago, nearby.minutesAgo)
    val count = pluralStringResource(if (nearby.more) R.plurals.lightning_flashes_more else R.plurals.lightning_flashes, nearby.flashes, nearby.flashes)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bolt, null, tint = Color(0xFFFFF176), modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.lightning_near), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("$where · $ago · $count", fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NimbusColors.Tertiary)
    }
}
