/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/KeptList.kt
 * A list kept for a while (station lists): not asked anew while it lasts, but on a
 * forced reload – the kept one standing in when that fails.
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

package dev.nimbus.weather.data.remote

/**
 * A value kept for [lifeMs]: not asked anew while it lasts – asked anew it is on a forced reload
 * ([freshData]); failing then, the kept one stands in ([standIn]).
 */
class KeptList<T>(private val lifeMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private var value: T? = null
    private var at = 0L

    suspend fun get(load: suspend () -> T): T {
        val kept = value
        if (kept != null && !freshData() && clock() - at < lifeMs) return kept
        return runCatching { load() }.onSuccess { value = it; at = clock() }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException || kept == null) throw e
            standIn()
            kept
        }
    }
}
