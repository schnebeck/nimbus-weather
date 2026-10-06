/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/InFlight.kt
 * One computation per key at a time: who asks for it meanwhile gets its result.
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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/**
 * A radar step or a preview picture asked for twice at once (the card and the prefetching, the
 * player and the preview) is fetched and computed once: the second waits for the first's result.
 * If the first fails, the waiting ones get the same. If the first is called off (its screen left,
 * its downloader restarted after panning), the waiting ones are not: one of them computes it –
 * handed the cancellation, a download worker of the player took it for its own and ended, and
 * with all of them gone the loop loaded no further step.
 */
class InFlight<K : Any, V> {
    private val running = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun get(key: K, compute: suspend () -> V): V {
        while (true) {
            val mine = CompletableDeferred<V>()
            val other = running.putIfAbsent(key, mine)
            if (other != null) {
                try {
                    return other.await()
                } catch (e: CancellationException) {
                    // called off ourselves: so be it – else the first one was: on with it here
                    currentCoroutineContext().ensureActive()
                    running.remove(key, other)
                    continue
                }
            }
            return try {
                compute().also { mine.complete(it) }
            } catch (e: Throwable) {
                mine.completeExceptionally(e)
                throw e
            } finally {
                running.remove(key, mine)
            }
        }
    }
}
