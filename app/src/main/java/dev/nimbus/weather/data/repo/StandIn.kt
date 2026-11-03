/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/StandIn.kt
 * How long a source's last answer stands in for one that fails.
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

package dev.nimbus.weather.data.repo

import dev.nimbus.weather.data.model.WeatherAlert

/**
 * A source of the page that fails does not change what the page shows for that load: its last
 * answer ([dev.nimbus.weather.data.model.LastAnswers]) stands in while it still stands for now –
 * the part is out of date all the same (its cards yellow) and asked for again soon.
 */
object StandIn {
    private const val MIN_MS = 60_000L

    /** The station's readings: they come every 10 minutes; half an hour old they no longer tell of now. */
    const val STATION_MS = 30 * MIN_MS
    /** MOSMIX: a new run every hour, the long one every 6 hours – its hours stay good for that long. */
    const val MOSMIX_MS = 6 * 60 * MIN_MS
    /** The satellite's hours: their newest stands for now as long ([NowWeather.SATELLITE_VALID_MS]). */
    const val SATELLITE_MS = NowWeather.SATELLITE_VALID_MS
    /** The ICON-D2 chance: a run every 3 hours. */
    const val D2_CHANCE_MS = 3 * 60 * MIN_MS
    /** The spread of the forecast: a day old, its days ahead still say how sure they are. */
    const val SPREAD_MS = 24 * 60 * MIN_MS

    /** [last], answered at [at] (0: never), while it is at most [lifeMs] old at [now]; else null. */
    fun <T> within(last: T, at: Long, lifeMs: Long, now: Long): T? = last.takeIf { at > 0 && now - at in 0..lifeMs }

    /** The last answer's warnings still in force at [now] (one without an end: in force). */
    fun alerts(last: List<WeatherAlert>, now: Long): List<WeatherAlert> = last.filter { (it.expires ?: Long.MAX_VALUE) > now }
}
