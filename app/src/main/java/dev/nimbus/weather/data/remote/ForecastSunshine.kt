/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/ForecastSunshine.kt
 * The sunshine of a forecast hour from what the model says of it, learnt from DWD stations.
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

/**
 * The sunshine of a forecast hour from the model's own sunshine, its direct beam against a clear
 * sky's, the sun's height, the cloud and the precipitation. Open-Meteo derives the model's sunshine
 * from the hour's mean direct irradiance and counts an hour of passing showers as sunny; the hour's
 * weather as a whole tells how long the sun shines. Trees learnt from DWD stations measuring
 * sunshine against the model's archived hours ([MODEL], written and checked by
 * tools/sunshine_calibration.py, see docs/STATIONS.md).
 */
object ForecastSunshine {
    /** Absolute: the release build renames the classes and their packages, a relative path would not find the file. */
    const val MODEL = "/dev/nimbus/weather/data/remote/forecast_sunshine_model.json"
    val FEATURES = listOf("sunshine", "direct", "elevation", "cloud", "precipitation")

    /**
     * Minutes of sunshine in the hour ending [hourEnd]; the model's own where its direct beam is
     * missing, the sun down or the trees unreadable. [cloudStart], [cloudEnd]: the cloud cover (%)
     * at the hour's start and end.
     */
    fun minutes(
        sunshine: Double?, direct: Double?, hourEnd: Long, lat: Double, lon: Double,
        cloudStart: Double?, cloudEnd: Double?, precipitation: Double?,
    ): Double? = model?.let { m -> features(sunshine, direct, hourEnd, lat, lon, cloudStart, cloudEnd, precipitation)?.let(m::minutes) } ?: sunshine

    /** In the order of [FEATURES]; null: no estimate. */
    internal fun features(
        sunshine: Double?, direct: Double?, hourEnd: Long, lat: Double, lon: Double,
        cloudStart: Double?, cloudEnd: Double?, precipitation: Double?,
    ): DoubleArray? {
        val sun = sunshine ?: return null
        val beam = direct ?: return null
        val sunHour = SunHour(hourEnd, lat, lon)
        if (!sunHour.up) return null
        return doubleArrayOf(
            sun,
            if (sunHour.clearDirect > 0) beam / sunHour.clearDirect else 0.0,
            sunHour.elevation,
            if (cloudStart != null && cloudEnd != null) (cloudStart + cloudEnd) / 2 else Double.NaN,
            precipitation ?: Double.NaN,
        )
    }

    /** Null if unreadable: the model's own sunshine stands. */
    internal val model: SunshineTrees? by lazy { SunshineTrees.load(MODEL, FEATURES) }
}
