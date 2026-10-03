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

    /** Whether the forecast fetched at [fetchedAt] (with [stale] parts older) should be loaded again at [now]. */
    fun forecastDue(fetchedAt: Long, now: Long, stale: Set<DataPart>): Boolean =
        now - fetchedAt >= FORECAST_MS || (stale.isNotEmpty() && now - fetchedAt >= STALE_RETRY_MS)

    /** Whether the look-back fetched at [fetchedAt] should be loaded again at [now]. */
    fun historyDue(fetchedAt: Long, now: Long): Boolean = now - fetchedAt >= HISTORY_MS
}
