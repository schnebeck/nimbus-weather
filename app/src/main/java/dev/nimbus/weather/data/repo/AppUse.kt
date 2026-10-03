/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/AppUse.kt
 * When the app was last in use – the background work rests while nobody looks.
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

import android.content.Context

/**
 * The moment the app was last opened (brought to the front), kept across restarts. The periodic
 * workers ask it: refreshing the weather every hour and the radar loop every 15 minutes is only
 * worth the battery and data while someone actually opens the app – after a pause the first
 * opening loads everything anyway.
 */
object AppUse {
    private const val PREFS = "nimbus_use"
    private const val KEY = "last_used"

    /** The hourly weather refresh rests after this long without use … */
    const val REFRESH_IDLE_MS = 3L * 24 * 3_600_000L
    /** … the radar loop (bigger downloads, every 15 minutes) already after one day. */
    const val RADAR_IDLE_MS = 24L * 3_600_000L
    /** The base map of the radar view hardly changes: drawn in the background at most this often. */
    const val BASE_MAP_EVERY_MS = 12L * 3_600_000L

    fun touch(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, now).apply()
    }

    /** Last use; 0 for never (a fresh install counts as used: the first worker run comes after the first start). */
    fun lastUsed(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)

    /** Whether background work of this kind should run at [now], last use [lastUsed]. */
    fun worthIt(lastUsed: Long, now: Long, idleMs: Long): Boolean = lastUsed > 0L && now - lastUsed <= idleMs

    /** Whether the base map for [key] (a place) is due again in the background; remembers the run when it is. */
    fun baseMapDue(context: Context, key: String, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong("basemap:$key", 0L)
        if (now - last < BASE_MAP_EVERY_MS) return false
        prefs.edit().putLong("basemap:$key", now).apply()
        return true
    }
}
