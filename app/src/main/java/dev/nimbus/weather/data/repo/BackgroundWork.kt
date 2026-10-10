/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/BackgroundWork.kt
 * Nimbus works only while it is shown: periodic background work still scheduled is called off.
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
 * Work in the background serves nobody: the app shows no notifications and has no widget, and
 * opened it loads what is out of date anyway – while hourly weather and 15-minute radar runs cost
 * battery and data. The app works only while it is shown; work still scheduled under [NAMES] (an
 * installation updated in place keeps it) is called off at the start.
 */
object BackgroundWork {
    /** The names background work was scheduled under – called off wherever it is still there. */
    val formerNames = listOf("hourly-refresh", "radar-prefetch")

    fun stopAll(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        formerNames.forEach { wm.cancelUniqueWork(it) }
    }
}
