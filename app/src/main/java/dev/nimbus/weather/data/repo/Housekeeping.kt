/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/Housekeeping.kt
 * Every stored file has its time: what nobody needs any more – data of a moment older than the
 * look-back's four days, lists not used for a month, the weather of a place given up – is deleted.
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
import java.io.File

/**
 * Two kinds of stored data:
 * - of a moment (readings, forecasts, pictures): nothing in the app looks back further than four
 *   days (the look-back: today and two days before, the radar archive: [MOMENT_KEEP_MS]) – older
 *   ones go; the grids of the live radar (temperature, wind) already after a day;
 * - lists that hardly change (gauges and bathing waters of an area, the tide fit of a gauge, the
 *   radar coverage): they renew themselves when used (every few days) – one not renewed for
 *   [LIST_KEEP_MS] belongs to a place no longer visited and goes.
 * The time of a file is its last write. Not here: the HTTP caches (OkHttp, by size) and the
 * radar frames ([dev.nimbus.weather.ui.radar.RadarStore], same four days).
 */
object Housekeeping {
    const val MOMENT_KEEP_MS = 4L * 24 * 3_600_000L
    const val LIVE_GRID_KEEP_MS = 24L * 3_600_000L
    const val LIST_KEEP_MS = 30L * 24 * 3_600_000L
    /** At most this often: a look through the folders. */
    const val SWEEP_EVERY_MS = 24L * 3_600_000L

    private const val PREFS = "nimbus_use"
    private const val KEY = "last_sweep"

    /**
     * How long [name] in the folder [dir] of the cache is kept after its last write; null: not
     * ours to delete here.
     */
    fun keepMs(dir: String, name: String): Long? = when (dir) {
        // temperature and wind of the radar: a past day's grid (`_p<hours>`) serves the look-back
        "grid" -> if (Regex("""_p\d+\.json$""").containsMatchIn(name)) MOMENT_KEEP_MS else LIVE_GRID_KEEP_MS
        // the radar picture of the preview: of a moment; its base map and lines: lists
        "previews" -> if (name.startsWith("radar_")) MOMENT_KEEP_MS else LIST_KEEP_MS
        // Schleswig-Holstein's latest samples: of a moment; the EU list of an area: a list
        "bathing" -> if (name.endsWith(".csv")) MOMENT_KEEP_MS else LIST_KEEP_MS
        // station lists of the states, tide fits of single gauges
        "tides" -> LIST_KEEP_MS
        // radar coverage and the state of the radar sources
        "radar" -> LIST_KEEP_MS
        else -> null
    }

    /**
     * Deletes in [cacheDir] what is past its time ([keepMs]) and in [filesDir] the last weather of
     * places no longer kept – all but [placeFiles] ([Store.cacheFileName]) – or older than four
     * days. Returns how many files went.
     */
    fun sweep(cacheDir: File, filesDir: File, placeFiles: Set<String>, now: Long): Int {
        var deleted = 0
        fun drop(f: File) { if (f.delete()) deleted++ }
        cacheDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                val keep = keepMs(dir.name, f.name) ?: return@forEach
                if (now - f.lastModified() > keep) drop(f)
            }
        }
        File(filesDir, Store.CACHE_DIR).listFiles()?.filter { it.isFile }?.forEach { f ->
            if (f.name !in placeFiles || now - f.lastModified() > MOMENT_KEEP_MS) drop(f)
        }
        return deleted
    }

    /** Whether a sweep is due at [now] after the last one at [last]. */
    fun due(last: Long, now: Long): Boolean = now - last >= SWEEP_EVERY_MS

    /**
     * Sweeps when one is due (at most once a day): on the app's start. Call off the main thread.
     */
    fun runIfDue(context: Context, placeIds: Collection<String>, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!due(prefs.getLong(KEY, 0L), now)) return
        prefs.edit().putLong(KEY, now).apply()
        val n = sweep(context.cacheDir, context.filesDir, placeIds.mapTo(mutableSetOf()) { Store.cacheFileName(it) }, now)
        if (dev.nimbus.weather.BuildConfig.DEBUG) android.util.Log.d("Nimbus", "housekeeping: $n files deleted")
    }
}
