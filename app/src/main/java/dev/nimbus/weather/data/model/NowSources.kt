/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/NowSources.kt
 * Where each value of the weather now comes from: measured at a station (which one), or the
 * model's – the sky too.
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

package dev.nimbus.weather.data.model

import kotlinx.serialization.Serializable

/** The values of the weather now that a station may measure. */
@Serializable
enum class NowValue { TEMPERATURE, HUMIDITY, DEW_POINT, PRESSURE, WIND, GUSTS, VISIBILITY, SKY }

/** A value measured at a station [station], [distanceKm] away. */
@Serializable
data class MeasuredValue(val value: NowValue, val station: String, val distanceKm: Double)

/**
 * Where the sky now (the condition) comes from. The cloud cover counts thin high cirrus as well:
 * the sunshine of the hour brightens it ([WeatherCodes.withSunshine]) – as in the hours.
 */
@Serializable
enum class SkyBasis {
    /** The model's condition. */
    MODEL,
    /** The model's, brightened by its sunshine in the hour running now. */
    MODEL_SUNSHINE,
    /** The station's present weather (rain, fog, …; dry: its cloud cover). */
    OBSERVED,
    /** Held against the sunshine the station measured: brightened where it shone. */
    MEASURED_SUNSHINE,
}
