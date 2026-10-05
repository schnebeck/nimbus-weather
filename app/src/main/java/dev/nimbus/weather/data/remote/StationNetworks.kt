/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/StationNetworks.kt
 * Station measurements besides the DWD's: each network's station nearest to a place.
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

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/**
 * The station networks besides the DWD's ([BrightSkySource]): GeoSphere Austria, MeteoSwiss and DMI
 * every 10 minutes, without a key – each only for its country; airports worldwide (METAR). Each
 * gives its station nearest to the place; which stands for the place decides
 * [StationObservation.forPlace], which network goes first [dev.nimbus.weather.data.repo.WeatherRepository.pickObservation].
 */
class StationNetworks(
    private val geosphere: GeoSphereSource,
    private val meteoswiss: MeteoSwissSource,
    private val dmi: DmiSource,
    private val metar: MetarSource,
) {
    constructor(http: OkHttpClient) : this(GeoSphereSource(http), MeteoSwissSource(http), DmiSource(http), MetarSource(http))

    /** The nearest station of each network measuring at the place – none failing the others. */
    suspend fun nearby(lat: Double, lon: Double): List<StationObservation> = coroutineScope {
        listOf(
            async { runCatching { if (inArea(lat, lon, AUSTRIA)) geosphere.nearest(lat, lon) else null }.getOrNull() },
            async { runCatching { if (inArea(lat, lon, SWITZERLAND)) meteoswiss.nearest(lat, lon) else null }.getOrNull() },
            async { runCatching { if (inArea(lat, lon, DENMARK)) dmi.nearest(lat, lon) else null }.getOrNull() },
            async { runCatching { metar.nearest(lat, lon) }.getOrNull() },
        ).mapNotNull { it.await() }
    }

    private companion object {
        /** South, north, west, east. */
        val AUSTRIA = doubleArrayOf(46.3, 49.1, 9.4, 17.3)
        val SWITZERLAND = doubleArrayOf(45.8, 47.9, 5.9, 10.6)
        val DENMARK = doubleArrayOf(54.5, 57.9, 7.9, 15.3)

        fun inArea(lat: Double, lon: Double, a: DoubleArray) = lat in a[0]..a[1] && lon in a[2]..a[3]
    }
}
