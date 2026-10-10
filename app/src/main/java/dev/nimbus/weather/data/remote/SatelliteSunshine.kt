/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SatelliteSunshine.kt
 * The hour's sunshine from the satellite's direct irradiance, calibrated against DWD stations.
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

import dev.nimbus.weather.util.Moon
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The sunshine of an hour from the satellite's mean direct normal irradiance (DNI): the sun shone
 * for the share of the hour that this mean is of the DNI under a clear sky. Open-Meteo's own
 * sunshine counts an hour with a passing shower as fully sunny once its mean DNI is high enough –
 * against 18 DWD stations (Feb–Oct 2026, 59,000 hours) it gave 6 minutes an hour too many; on
 * the 9 stations not used for the fit this estimate cuts the error of an hour from 11.8 to 8.7
 * minutes and that of a day from 87 to 61 (docs/STATIONS.md, tools/sunshine_calibration.py).
 */
object SatelliteSunshine {
    /** The clear sky's DNI the satellite reaches at most (haze, its 2.5 km pixel): fitted. */
    const val CLEAR_SHARE = 0.7
    /** WMO: sunshine is direct irradiance from 120 W/m² on – below it no estimate by the share. */
    private const val WMO_MIN = 120.0

    /** Minutes of sunshine in the hour ending [hourEnd]; [openMeteo] where the share tells nothing (sun low, DNI missing). */
    fun minutes(openMeteo: Double?, dni: Double?, hourEnd: Long, lat: Double, lon: Double): Double? {
        val clear = clearSkyDni(hourEnd, lat, lon) * CLEAR_SHARE
        if (dni == null || clear < WMO_MIN) return openMeteo
        return 60 * min(1.0, dni / clear)
    }

    /** Mean DNI of a clear sky over the hour ending [hourEnd] (W/m², Meinel's model, six steps). */
    fun clearSkyDni(hourEnd: Long, lat: Double, lon: Double): Double = (0 until 6).sumOf { k ->
        val e = Moon.sunAltitude(hourEnd - (5 + 10 * k) * 60_000L, lat, lon) * PI / 180
        if (e <= 0.01) 0.0 else 1367 * 0.7.pow((1 / sin(e)).pow(0.678))
    } / 6
}
