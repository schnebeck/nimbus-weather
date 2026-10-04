/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/Representative.kt
 * Which measurements stand for a place: the height of the station or sensor against the place's,
 * the measured temperature against the model's.
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

import kotlin.math.abs

/**
 * A measurement is shown for a place only if it was taken at the place's height: 300 m make
 * about 2 K and decide between rain and snow, fog and sun, calm and storm. A station in the valley
 * does not measure the summit, nor a summit station the valley – however near it is.
 *
 * And a measured temperature far off the model's is not taken either: the model may err by a few
 * degrees, not by ten – then the measurement belongs to somewhere else.
 */
object Representative {
    const val MAX_HEIGHT_DIFF_M = 300.0
    const val MAX_TEMPERATURE_DIFF_K = 10.0

    /**
     * True if a site at [siteM] metres measures for a place at [placeM] metres. Without the
     * place's height there is nothing to hold against (true); a site without a height is not
     * taken where the place's height is known.
     */
    fun height(siteM: Double?, placeM: Double?): Boolean =
        placeM == null || (siteM != null && abs(siteM - placeM) <= MAX_HEIGHT_DIFF_M)

    /** True if the measured temperature [measured] can belong to the place the model gives [model] for. */
    fun temperature(measured: Double, model: Double?): Boolean =
        model == null || abs(measured - model) <= MAX_TEMPERATURE_DIFF_K
}
