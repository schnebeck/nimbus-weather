/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/BathingCard.kt
 * Bathing waters nearby and favourites: EU classification, water temperature, blue-green algae.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.BathingCategory
import dev.nimbus.weather.data.model.BathingQuality
import dev.nimbus.weather.data.model.BathingSite
import dev.nimbus.weather.data.model.BathingStatus
import dev.nimbus.weather.data.remote.BathingSource
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.components.HairlineDivider
import dev.nimbus.weather.ui.components.Term
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.NBSP
import dev.nimbus.weather.util.Units

private const val SHOWN = 5
private val SEA_PREFIXES = setOf("OSTS", "NORDS", "OSTSEE", "NORDSEE")
private val Amber = Color(0xFFFFC94D)

/**
 * Official EU bathing waters within the chosen radius, nearest first, favourites (star) on top
 * at any distance. Tapping a row shows the details: EU classification, the last sample of the
 * health office (Berlin, Schleswig-Holstein), notices on algae, the sea temperature at coasts.
 */
@Composable
fun BathingCard(sites: List<BathingSite>, now: Long) {
    if (sites.isEmpty()) return
    val s = LocalSettings.current
    val update = LocalSettingsUpdater.current
    val favorites = s.bathingFavorites
    val ordered = sites.filter { it.id in favorites }.sortedBy { it.distanceKm } + sites.filter { it.id !in favorites }
    var all by rememberSaveable(sites.firstOrNull()?.id) { mutableStateOf(false) }
    var selectedId by rememberSaveable(sites.firstOrNull()?.id) { mutableStateOf<String?>(null) }
    val count = maxOf(SHOWN, ordered.count { it.id in favorites } + 2)
    val shown = if (all) ordered else ordered.take(count)
    val tf = LocalTimeFormat.current
    GlassCard(title = stringResource(R.string.bathing_title), icon = Icons.Outlined.Pool, info = Term.BATHING) {
        if (!inSeason(tf.zoned(now).toLocalDate())) {
            Text(stringResource(R.string.bathing_off_season), fontSize = 12.sp, color = NimbusColors.Tertiary, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
        }
        shown.forEachIndexed { i, site ->
            if (i > 0) HairlineDivider()
            val selected = site.id == selectedId
            BathingRow(site, site.id in favorites, selected, now) { selectedId = if (selected) null else site.id }
            if (selected) BathingDetails(site, site.id in favorites, now) {
                update { st -> st.copy(bathingFavorites = if (site.id in st.bathingFavorites) st.bathingFavorites - site.id else st.bathingFavorites + site.id) }
            }
        }
        if (ordered.size > count) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (all) stringResource(R.string.bathing_fewer)
                else stringResource(R.string.bathing_all, ordered.count { it.distanceKm <= s.bathingRadiusKm }, s.bathingRadiusKm),
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(vertical = 6.dp),
                fontSize = 14.sp, color = Color(0xFF9CC8FF), fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.bathing_footer, s.bathingRadiusKm), fontSize = 11.sp, color = NimbusColors.Tertiary, lineHeight = 14.sp)
    }
}

/** Main German bathing season (May 15 – September 15); outside it the last samples are old. */
fun inSeason(d: java.time.LocalDate): Boolean =
    !d.isBefore(java.time.LocalDate.of(d.year, 5, 15)) && !d.isAfter(java.time.LocalDate.of(d.year, 9, 15))

/** Colour of the row dot: the current assessment of the state first, else the EU classification. */
private fun siteColor(site: BathingSite): Color = when {
    site.status == BathingStatus.CLOSED -> Color(0xFFFF5A5A)
    site.status == BathingStatus.WARNING || site.algae -> Amber
    else -> when (site.quality) {
        BathingQuality.EXCELLENT -> Color(0xFF4FB4FF)
        BathingQuality.GOOD -> Color(0xFF5FD39A)
        BathingQuality.SUFFICIENT -> Color(0xFFE6D25A)
        BathingQuality.POOR -> Color(0xFFFF5A5A)
        else -> NimbusColors.Tertiary
    }
}

@Composable
private fun categoryName(c: BathingCategory) = stringResource(
    when (c) {
        BathingCategory.LAKE -> R.string.bathing_lake
        BathingCategory.RIVER -> R.string.bathing_river
        BathingCategory.COAST -> R.string.bathing_coast
        BathingCategory.TRANSITIONAL -> R.string.bathing_transitional
    },
)

@Composable
private fun qualityName(q: BathingQuality) = stringResource(
    when (q) {
        BathingQuality.EXCELLENT -> R.string.bathing_q_excellent
        BathingQuality.GOOD -> R.string.bathing_q_good
        BathingQuality.SUFFICIENT -> R.string.bathing_q_sufficient
        BathingQuality.POOR -> R.string.bathing_q_poor
        BathingQuality.NOT_CLASSIFIED -> R.string.bathing_q_none
    },
)

/** "GORINSEE, SCHÖNWALDE, BADEWIESE AM CAMPINGPLATZ" -> "Gorinsee, Schönwalde, Badewiese am Campingplatz" */
fun bathingName(raw: String): String {
    // Schleswig-Holstein: "OSTS;KIEL;KIELLINIE" (Ostsee/Nordsee prefix, ";" as separator)
    val cleaned = raw.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
        .let { parts -> if (parts.size > 1 && parts.first().uppercase() in SEA_PREFIXES) parts.drop(1) else parts }
        .joinToString(", ")
    val small = setOf("am", "an", "im", "in", "der", "die", "das", "dem", "den", "bei", "zum", "zur", "von", "vom", "und", "auf", "a.", "i.", "b.")
    val words = titleCase(cleaned).split(' ')
    return words.mapIndexed { i, w -> if (i > 0 && w.lowercase() in small) w.lowercase() else w }.joinToString(" ")
}

@Composable
private fun BathingRow(site: BathingSite, favorite: Boolean, selected: Boolean, now: Long, onClick: () -> Unit) {
    val s = LocalSettings.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(siteColor(site)))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    Icon(Icons.Rounded.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(bathingName(site.name), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                categoryName(site.category) + " · " + Units.oneDecimal(site.distanceKm) + NBSP + "km" +
                    (site.quality?.takeIf { it != BathingQuality.NOT_CLASSIFIED }?.let { " · " + qualityName(it) } ?: ""),
                fontSize = 12.sp, color = NimbusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (site.algae || site.status == BathingStatus.CLOSED) {
            Icon(Icons.Rounded.WarningAmber, stringResource(R.string.bathing_algae), tint = if (site.status == BathingStatus.CLOSED) Color(0xFFFF5A5A) else Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        site.waterTemp?.let { t ->
            Column(horizontalAlignment = Alignment.End) {
                Text(Units.temp(t, s.temperatureUnit), fontSize = 17.sp, color = Color.White)
                val time = site.waterTempTime
                val caption = when {
                    site.waterTempFromModel -> stringResource(R.string.bathing_sea_short)
                    time != null -> LocalTimeFormat.current.dayMonth(time)
                    else -> null
                }
                caption?.let { Text(it, fontSize = 10.sp, color = NimbusColors.Tertiary, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun BathingDetails(site: BathingSite, favorite: Boolean, now: Long, onToggleFavorite: () -> Unit) {
    val s = LocalSettings.current
    val tf = LocalTimeFormat.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)) {
        site.status?.let { st ->
            Text(
                stringResource(
                    when (st) {
                        BathingStatus.OK -> R.string.bathing_status_ok
                        BathingStatus.WARNING -> R.string.bathing_status_warning
                        BathingStatus.CLOSED -> R.string.bathing_status_closed
                    },
                ),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = when (st) {
                    BathingStatus.OK -> Color(0xFF5FD39A)
                    BathingStatus.WARNING -> Amber
                    BathingStatus.CLOSED -> Color(0xFFFF5A5A)
                },
            )
        }
        site.notice?.let {
            Text(it, fontSize = 13.sp, color = if (site.algae) Amber else NimbusColors.Secondary, lineHeight = 18.sp)
        }
        if (site.sampleTime != null) {
            val parts = listOfNotNull(
                site.waterTemp?.takeIf { !site.waterTempFromModel }?.let { Units.temp(it, s.temperatureUnit) },
                site.visibilityM?.let { stringResource(R.string.bathing_visibility, Units.oneDecimal(it)) },
            )
            Text(
                stringResource(R.string.bathing_last_sample, tf.dayMonth(site.sampleTime)) +
                    (if (parts.isNotEmpty()) ": " + parts.joinToString(" · ") else "") +
                    (site.provider?.let { " · $it" } ?: ""),
                fontSize = 13.sp, color = NimbusColors.Secondary, lineHeight = 18.sp,
            )
            if (now - site.sampleTime > BathingSource.SAMPLE_FRESH_MS && inSeason(tf.zoned(now).toLocalDate())) {
                Text(stringResource(R.string.bathing_sample_old), fontSize = 12.sp, color = NimbusColors.Tertiary)
            }
        }
        if (site.waterTempFromModel && site.waterTemp != null) {
            Text(stringResource(R.string.bathing_sea_temp, Units.temp(site.waterTemp, s.temperatureUnit)), fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        site.quality?.let { q ->
            Text(stringResource(R.string.bathing_eu_quality, qualityName(q)), fontSize = 13.sp, color = NimbusColors.Secondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggleFavorite).padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (favorite) R.string.bathing_unfavorite else R.string.bathing_favorite), fontSize = 13.sp, color = Color.White)
            }
            site.profileLink?.let { link ->
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.bathing_profile), Modifier.clip(RoundedCornerShape(8.dp)).clickable { runCatching { uri.openUri(link) } }.padding(vertical = 4.dp),
                    fontSize = 13.sp, color = Color(0xFF9CC8FF), fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
