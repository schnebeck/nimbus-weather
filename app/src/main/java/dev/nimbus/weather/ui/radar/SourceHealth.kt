/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/SourceHealth.kt
 * A radar source that does not answer is left out for a while instead of holding up every step.
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

import java.util.concurrent.ConcurrentHashMap

/**
 * Sources beside the place's own one (another country's composite, RainViewer): one that failed
 * [FAILS] times in a row is left out for [PAUSE_MS] – asked on, every step waited for it in vain.
 */
class SourceHealth(private val clock: () -> Long = System::currentTimeMillis) {
    private class State(var fails: Int = 0, var until: Long = 0L)
    private val states = ConcurrentHashMap<String, State>()

    /** Whether [source] is asked now. */
    fun usable(source: String): Boolean = (states[source]?.until ?: 0L) <= clock()

    fun answered(source: String) { states.remove(source) }

    fun failed(source: String) {
        val s = states.getOrPut(source) { State() }
        synchronized(s) {
            s.fails++
            if (s.fails >= FAILS) { s.until = clock() + PAUSE_MS; s.fails = 0 }
        }
    }

    companion object {
        const val FAILS = 2
        const val PAUSE_MS = 5 * 60_000L
    }
}
