/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/IdleClose.kt
 * The radar closes by itself after a while untouched: a loop left playing costs processor and data.
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

package dev.nimbus.weather.ui.radar

import kotlinx.coroutines.delay

/**
 * Untouched for [afterMs] by [clock] (ms): the radar screen goes back where it was opened from –
 * a loop playing on unseen costs processor, display and data.
 */
class IdleClose(private val afterMs: Long = AFTER_MS, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var touched = clock()

    /** A touch (or the screen shown again): the time starts anew. */
    fun touched() { touched = clock() }

    /** Returns once untouched for [afterMs]. */
    suspend fun awaitIdle() {
        var left = touched + afterMs - clock()
        while (left > 0) {
            delay(left)
            left = touched + afterMs - clock()
        }
    }

    companion object {
        const val AFTER_MS = 2 * 60_000L
    }
}
