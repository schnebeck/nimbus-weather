/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/NetworkStation.kt
 * A station of a measuring network, and finding the one nearest to a place.
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

package dev.nimbus.weather.data.remote

import kotlin.math.exp

/** A station of a network: where it is and how high. */
data class NetworkStation(val id: String, val name: String, val lat: Double, val lon: Double, val heightM: Double?)

object NetworkStations {
    /** As far as a station may be from the place (the DWD's via Bright Sky: the same). */
    const val MAX_KM = 30.0

    /** The station nearest to the place, within [MAX_KM], and how far. */
    fun nearest(stations: List<NetworkStation>, lat: Double, lon: Double): Pair<NetworkStation, Double>? =
        stations.map { it to GaugeGeo.distanceKm(lat, lon, it.lat, it.lon) }.filter { it.second <= MAX_KM }.minByOrNull { it.second }

    /** Relative humidity from temperature and dew point (Magnus formula). */
    fun humidity(t: Double, dewPoint: Double): Double =
        (100 * exp(17.625 * dewPoint / (243.04 + dewPoint)) / exp(17.625 * t / (243.04 + t))).coerceIn(0.0, 100.0)

    /** "WIEN/INNERE STADT" → "Wien/Innere Stadt"; mixed case stays as it is. */
    fun niceName(s: String): String =
        if (s != s.uppercase()) s else Regex("[\\p{L}]+").replace(s.lowercase()) { m -> m.value.replaceFirstChar { it.uppercase() } }
}
