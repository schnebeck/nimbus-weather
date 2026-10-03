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
 * Every kind of data expires: the app checks when it comes back to the front and then every
 * minute while it is seen, and loads what has expired – the page never shows last night's data
 * without saying so (it showed the look-back of 01:00 at 09:28).
 */
object Freshness {
    /** The forecast with station readings: the station reports every 10 minutes. */
    const val FORECAST_MS = 10 * 60_000L
    /** Parts still showing older values (a source that failed, the background refresh's extras): tried again after this long. */
    const val STALE_RETRY_MS = 2 * 60_000L
    /** The look-back: today so far grows with every station report. */
    const val HISTORY_MS = 15 * 60_000L
    /** How often the app looks while it is in front. */
    const val CHECK_EVERY_MS = 60_000L
    /**
     * The position of "my location": on the road 5 minutes are a few kilometres. Older, the page
     * of the current location no longer counts as current (its dots turn yellow).
     */
    const val LOCATION_MS = 5 * 60_000L

    /** Whether the forecast fetched at [fetchedAt] (with [stale] parts older) should be loaded again at [now]. */
    fun forecastDue(fetchedAt: Long, now: Long, stale: Set<DataPart>): Boolean =
        now - fetchedAt >= FORECAST_MS || (stale.isNotEmpty() && now - fetchedAt >= STALE_RETRY_MS)

    /**
     * Whether the position found at [fixedAt] should be looked for again at [now] – after
     * [misses] searches in a row without result (the last at [triedAt]) only after a growing
     * pause ([locationPauseMs]): indoors without network location the GPS ran half the time.
     */
    fun locationDue(fixedAt: Long, triedAt: Long, now: Long, misses: Int = 1): Boolean =
        now - fixedAt >= LOCATION_MS && now - triedAt >= locationPauseMs(misses)

    /** The pause after [misses] searches in a row without result: 2, 5, then 10 minutes. */
    fun locationPauseMs(misses: Int): Long = when {
        misses <= 1 -> STALE_RETRY_MS
        misses == 2 -> 5 * 60_000L
        else -> 10 * 60_000L
    }

    /** Whether the position found at [fixedAt] is still the current one at [now]. */
    fun locationCurrent(fixedAt: Long, now: Long): Boolean = now - fixedAt < LOCATION_MS

    /** Whether the look-back fetched at [fetchedAt] should be loaded again at [now]. */
    fun historyDue(fetchedAt: Long, now: Long): Boolean = now - fetchedAt >= HISTORY_MS
}
