/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarLatest.kt
 * The newest step of each radar composite: asked for with a small request, kept on disk.
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The newest step of each composite ([RadarComposite.latest]). Kept on disk too: without network
 * the loop is then built from exactly the steps in the store (an estimated time would miss them).
 */
object RadarLatest {
    @Volatile var dir: File? = null
    /** Each request may take this long; after that the known time applies. */
    private const val TIMEOUT_MS = 6_000L
    private val known = ConcurrentHashMap<String, Long>()

    /** The newest step of [c] known (from memory, else from disk), no request. */
    fun known(c: RadarComposite): Long? = known[c.id] ?: stored(c)?.also { known[c.id] = it }

    /** Asks [c] for its newest step now; null on errors or a slow answer. */
    suspend fun check(http: OkHttpClient, c: RadarComposite): Long? =
        withTimeoutOrNull(TIMEOUT_MS) { runCatching { c.latest(http) }.getOrNull() }?.also { t ->
            known[c.id] = t
            withContext(Dispatchers.IO) { runCatching { dir?.let { it.mkdirs(); file(it, c).writeText(t.toString()) } } }
        }

    private fun file(dir: File, c: RadarComposite) = File(dir, "radar_latest_${c.id}")
    private fun stored(c: RadarComposite): Long? = runCatching { dir?.let { file(it, c).readText().trim().toLong() } }.getOrNull()
}
