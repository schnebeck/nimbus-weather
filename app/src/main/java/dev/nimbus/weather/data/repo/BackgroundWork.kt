/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/BackgroundWork.kt
 * Nimbus works only while it is shown: the periodic background work of earlier versions is
 * called off.
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
import androidx.work.WorkManager

/**
 * Earlier versions refreshed the weather every hour and the radar loop every 15 minutes in the
 * background – for nobody: the app shows no notifications and has no widget, and opened it loads
 * what is out of date anyway. Measured on a phone over 15 hours: 19 radar and 8 weather runs,
 * and most of 169 MB over Wi-Fi. Now it works only while it is shown; the work scheduled by an
 * earlier version is called off at the start.
 */
object BackgroundWork {
    /** The names the earlier versions scheduled their work under. */
    val formerNames = listOf("hourly-refresh", "radar-prefetch")

    fun stopAll(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        formerNames.forEach { wm.cancelUniqueWork(it) }
    }
}
