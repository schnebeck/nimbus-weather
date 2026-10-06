/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/InFlightTest.kt
 * A picture asked for twice at once is computed once – and a called-off first one leaves the others waiting for it unharmed.
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

package dev.nimbus.weather

import dev.nimbus.weather.ui.radar.InFlight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class InFlightTest {
    @Test fun askedTwiceComputedOnce() = runBlocking {
        val f = InFlight<String, Int>()
        val n = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val a = async { f.get("k") { n.incrementAndGet(); gate.await(); 7 } }
        delay(50)
        val b = async { f.get("k") { n.incrementAndGet(); 8 } }
        delay(50)
        gate.complete(Unit)
        assertEquals(7 to 7, a.await() to b.await())
        assertEquals(1, n.get())
    }

    /** The first one called off (its downloader restarted): the one waiting computes it itself. */
    @Test fun theFirstCalledOffTheWaitingOneCarriesOn() = runBlocking {
        val f = InFlight<String, Int>()
        val first = launch { f.get("k") { awaitCancellation() } }
        delay(50)
        val second = async { f.get("k") { 8 } }
        delay(50)
        first.cancel()
        assertEquals(8, withTimeout(2_000) { second.await() })
    }

    /** The one waiting called off itself: it ends, it does not compute. */
    @Test fun theWaitingOneCalledOffEnds() = runBlocking {
        val f = InFlight<String, Int>()
        val gate = CompletableDeferred<Unit>()
        val first = async { f.get("k") { gate.await(); 7 } }
        delay(50)
        var computed = false
        val second = launch { f.get("k") { computed = true; 8 } }
        delay(50)
        second.cancel(); second.join()
        gate.complete(Unit)
        assertEquals(7, first.await())
        assertTrue(second.isCancelled && !computed)
    }

    @Test fun aFailureReachesTheWaitingOne() = runBlocking {
        val f = InFlight<String, Int>()
        val gate = CompletableDeferred<Unit>()
        val a = async { runCatching { f.get("k") { gate.await(); error("no picture") } } }
        delay(50)
        val b = async { runCatching { f.get("k") { 8 } } }
        delay(50)
        gate.complete(Unit)
        assertEquals("no picture", a.await().exceptionOrNull()?.message)
        assertEquals("no picture", b.await().exceptionOrNull()?.message)
    }
}
