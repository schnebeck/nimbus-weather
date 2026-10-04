/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/Freshness.kt
 * How long the data stays current – the app brought back from the background knows what to load.
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

import dev.nimbus.weather.data.model.DataPart

/**
 * The rules of what is current: how long each kind of data keeps, and how long to wait before
 * asking again after a request without result. The records ([DataRecord]) keep to them – they
 * tell their cards when they go out of date; nothing in the app looks at the clock for that.
 */
object Freshness {
    /** The forecast with station readings: the station reports every 10 minutes. */
    const val FORECAST_MS = 10 * 60_000L
    /** A part without a new answer (a source that failed, one standing in): asked again after this long. */
    const val STALE_RETRY_MS = 2 * 60_000L
    /** The look-back: today so far grows with every station report. */
    const val HISTORY_MS = 15 * 60_000L
    /**
     * The position of "my location": on the road 5 minutes are a few kilometres. Older, the page
     * of the current location no longer counts as current (its dots turn yellow).
     */
    const val LOCATION_MS = 5 * 60_000L

    /**
     * How long a part of the weather counts as current – after that its card turns yellow and the
     * part is loaded again: the forecast with the station and the citizen sensors report every 10
     * minutes, the gauges every 15, the flood alerts of the states as their portal asks (10);
     * air quality is an hourly model, bathing samples change by the hour at most, the pollen
     * forecast a few times a day.
     */
    fun lifeMs(part: DataPart): Long = when (part) {
        DataPart.FORECAST, DataPart.COMMUNITY, DataPart.FLOOD -> FORECAST_MS
        DataPart.GAUGES -> 15 * 60_000L
        DataPart.AIR_QUALITY, DataPart.BATHING -> 60 * 60_000L
        DataPart.POLLEN -> 3 * 60 * 60_000L
    }

    /**
     * The pause after [misses] searches for the position in a row without result: 2, 5, then 10
     * minutes – indoors without network location the GPS ran half the time.
     */
    fun locationPauseMs(misses: Int): Long = when {
        misses <= 1 -> STALE_RETRY_MS
        misses == 2 -> 5 * 60_000L
        else -> 10 * 60_000L
    }

    /** Whether, after [misses] searches without result (the last at [triedAt]), a search may start at [now]. */
    fun locationRetryDue(triedAt: Long, now: Long, misses: Int): Boolean = misses == 0 || now - triedAt >= locationPauseMs(misses)

    /**
     * Whether a search that brought the position found at [foundAt] (null: none) came back
     * empty-handed at [now]: nothing, or only an older position – not a current one.
     */
    fun locationMissed(foundAt: Long?, now: Long): Boolean = foundAt == null || now - foundAt >= LOCATION_MS
}
