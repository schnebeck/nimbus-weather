/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/places/PlaceTwins.kt
 * The same place more than once, each with its own forecast model: the copy's id, and the model
 * named beside a place that has a twin.
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

package dev.nimbus.weather.ui.places

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.modelFor
import dev.nimbus.weather.ui.main.modelName

object PlaceTwins {
    /** Between a place's id and the number of its copy: "geo-2911298#2". */
    private const val MARK = "#"

    /** A copy of [place] for another model: the same spot, an id of its own (the next free number). */
    fun copyOf(place: Place, places: List<Place>): Place {
        val base = place.id.substringBefore(MARK)
        val n = generateSequence(2) { it + 1 }.first { k -> places.none { it.id == "$base$MARK$k" } }
        return place.copy(id = "$base$MARK$n")
    }

    /** Whether another saved place stands on the same spot. */
    fun hasTwin(place: Place, places: List<Place>): Boolean =
        places.any { it.id != place.id && !it.isCurrentLocation && it.latitude == place.latitude && it.longitude == place.longitude }

    /** The model to name beside [place] – only if it has a twin (alone, its name says enough). */
    fun label(place: Place, places: List<Place>, settings: Settings): String? =
        if (place.isCurrentLocation || !hasTwin(place, places)) null else modelName(settings.modelFor(place))
}

/** Under a page's place name: the model of a place in the list more than once ([PlaceTwins.label]). */
@Composable
fun TwinModelLine(label: String?, style: TextStyle) {
    if (label != null) Text(label, fontSize = 15.sp, color = Color.White, maxLines = 1, style = style)
}
