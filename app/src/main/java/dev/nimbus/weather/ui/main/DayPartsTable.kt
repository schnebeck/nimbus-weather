/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/DayPartsTable.kt
 * The look-back day in parts – early, morning … night – as a table under its sky.
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

import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.util.Texts

/**
 * The day in parts as a table of three columns and two rows – early, morning, forenoon above,
 * afternoon, evening, night below –, each with its name, symbol and weather, for the parts that
 * have begun; the part the sky shows now is lit, the others dimmed. One font size for all cells,
 * small enough (on narrow phones, with a large system font) that the longest single word
 * ("Überwiegend", "Nachmittags") fits its column: lines break between words only, never inside one.
 */
@Composable
internal fun DayPartsTable(parts: List<dev.nimbus.weather.data.remote.DayPartWeather>, shown: Int, halo: androidx.compose.ui.text.TextStyle) {
    val labels = parts.map { stringResource(dayPartLabel(it.part)) }
    val weathers = parts.map { stringResource(Texts.condition(it.condition, it.isDay)) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = minOf(maxWidth / PART_COLUMNS, 120.dp) - 4.dp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val columnPx = with(density) { column.toPx() }
        // the largest size (up to [max]) at which the widest of [words] fits the column: scaled,
        // then measured again and stepped down – small sizes do not scale exactly (glyphs snap to pixels)
        fun fit(words: List<String>, max: androidx.compose.ui.unit.TextUnit, weight: FontWeight): androidx.compose.ui.unit.TextUnit {
            // with the density of now (the measurer keeps the one it was made with)
            fun widest(size: Float) = words.maxOfOrNull { w ->
                measurer.measure(w, halo.copy(fontSize = size.sp, fontWeight = weight), softWrap = false, density = density).size.width
            } ?: 0
            var size = max.value
            val first = widest(size)
            if (first <= columnPx) return max
            size *= columnPx / first
            while (size > 6f && widest(size) > columnPx) size -= 0.25f
            return size.sp
        }
        // keyed by the density too: a larger system font needs a smaller size
        val labelSize = remember(labels, columnPx, density) { fit(labels, 13.sp, FontWeight.Normal) }
        val weatherSize = remember(weathers, columnPx, density) { fit(weathers.flatMap { it.split(' ') }, 13.sp, FontWeight.Medium) }
        // a fixed grid: a row not full yet (the afternoon alone at 13:00) starts in the first column
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            parts.indices.chunked(PART_COLUMNS).forEach { row ->
                Row(Modifier.width((column + 4.dp) * PART_COLUMNS)) {
                    row.forEach { i -> DayPartCell(parts[i], labels[i], weathers[i], i == shown, column, labelSize, weatherSize, halo) }
                }
            }
        }
    }
}

/** One part of the day: name, symbol, weather (always two lines, so the rows keep their height). */
@Composable
private fun DayPartCell(
    p: dev.nimbus.weather.data.remote.DayPartWeather, label: String, weather: String, lit: Boolean,
    column: androidx.compose.ui.unit.Dp, labelSize: androidx.compose.ui.unit.TextUnit, weatherSize: androidx.compose.ui.unit.TextUnit,
    halo: androidx.compose.ui.text.TextStyle,
) {
    // Every cell in full white on the glass pill of the station line – as opaque as the brightest
    // sky behind needs (on the bare sky "Früh / Nebel" vanished on a white cloud); the part the sky
    // shows on a darker glass with a white rim
    val pill = dev.nimbus.weather.ui.components.LocalHeaderStyle.current.pill
    val ground by androidx.compose.animation.animateColorAsState(dayPartGround(pill, lit), androidx.compose.animation.core.tween(600), label = "lit")
    val rim by androidx.compose.animation.animateColorAsState(if (lit) LitRim else LitRim.copy(alpha = 0f), androidx.compose.animation.core.tween(600), label = "rim")
    Column(
        Modifier.width(column + 4.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(12.dp)).background(ground)
            .border(1.5.dp, rim, RoundedCornerShape(12.dp)).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = labelSize, color = Color.White, style = halo, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        WeatherIcon(p.condition, p.isDay, size = 34.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            weather, fontSize = weatherSize, lineHeight = weatherSize * 1.2f, color = Color.White, style = halo,
            fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2, maxLines = 2,
        )
    }
}

/**
 * The glass behind a cell of the day parts: every cell at least the station line's [pill] (the
 * contrast its small text needs on the brightest sky behind), the one the sky shows darker.
 */
internal fun dayPartGround(pill: Color, lit: Boolean): Color =
    if (lit) pill.copy(alpha = (pill.alpha + LIT_EXTRA).coerceAtMost(1f)) else pill

/** How much darker the glass of the part the sky shows is. */
private const val LIT_EXTRA = 0.3f

/** The rim of the part the sky shows. */
private val LitRim = Color(0xD9FFFFFF)

/** Columns of the day-parts table: two rows of three. */
private const val PART_COLUMNS = 3

private fun dayPartLabel(p: dev.nimbus.weather.data.remote.DayPart) = when (p) {
    dev.nimbus.weather.data.remote.DayPart.EARLY -> R.string.day_part_early
    dev.nimbus.weather.data.remote.DayPart.MORNING -> R.string.day_part_morning
    dev.nimbus.weather.data.remote.DayPart.FORENOON -> R.string.day_part_forenoon
    dev.nimbus.weather.data.remote.DayPart.AFTERNOON -> R.string.day_part_afternoon
    dev.nimbus.weather.data.remote.DayPart.EVENING -> R.string.day_part_evening
    dev.nimbus.weather.data.remote.DayPart.NIGHT -> R.string.day_part_night
}
