/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/Geo.kt
 * Distances on the map – between a place and its stations, gauges, waters and alert areas.
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

package dev.nimbus.weather.util

import kotlin.math.cos
import kotlin.math.hypot

/** Small geometry on latitude and longitude. */
object Geo {
    /** Kilometres per degree of latitude. */
    private const val KM_PER_DEG = 111.2

    /**
     * The distance in km between two points – flat (the longitude shrunk at [lat1]): within the
     * few hundred kilometres the app compares it is off by well under a percent.
     */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        hypot((lat2 - lat1) * KM_PER_DEG, (lon2 - lon1) * KM_PER_DEG * cos(Math.toRadians(lat1)))
}
