/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StoreTest.kt
 * The store's writes: none of them waits for the main thread.
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

import android.os.Looper
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.repo.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StoreTest {
    private val store by lazy { Store(RuntimeEnvironment.getApplication()) }
    private val norden = Place("n", "Norden", latitude = 53.596, longitude = 7.206)
    private val viewModel = CoroutineScope(Dispatchers.Main.immediate)

    @After fun tidy() {
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.cancel()
        // the store is one per process: the next test finds it as it was
        runBlocking { store.updatePlaces { emptyList() }; store.updateSettings { Settings() }; store.setLastPlace(null) }
    }

    /**
     * A write from the main thread (as the view model's) and, behind it, one while the main thread
     * is busy (here: waiting for it): both get through – the store's writes are one after the other,
     * a write that needed the main thread would hold up every one after it.
     */
    @Test fun aWriteFromTheMainThreadNeedsNotTheMainThread() {
        // the store opened: a write goes straight to its queue
        runBlocking { store.updateSettings { Settings(preloadRadar = false) } }
        viewModel.launch { store.setLastPlace(norden.id) }
        runBlocking {
            withTimeout(WAIT_MS) { store.updatePlaces { listOf(norden) } }
            assertEquals(norden.id, withTimeout(WAIT_MS) { store.lastPlace.first() })
            assertEquals(listOf(norden), store.places.first())
        }
    }

    /**
     * Writes from the main thread one right after the other – a place copied, then its model set,
     * then a mark – reach the store in that order: when the mark is there, the copy has its model.
     */
    @Test fun writesInTheOrderAskedFor() {
        val copy = norden.copy(id = "n2")
        repeat(ROUNDS) { round ->
            val model = if (round % 2 == 0) ForecastModel.KNMI else ForecastModel.ECMWF
            viewModel.launch { store.updatePlaces { list -> list + copy } }
            viewModel.launch { store.updatePlaces { list -> list.map { if (it.id == copy.id) it.copy(model = model) else it } } }
            viewModel.launch { store.updatePlaces { list -> list + norden.copy(id = "mark") } }
            runBlocking {
                val places = withTimeout(WAIT_MS) { store.places.first { p -> p.any { it.id == "mark" } } }
                assertEquals("round $round", listOf(copy.copy(model = model)), places.filter { it.id == copy.id })
                store.updatePlaces { emptyList() }
            }
        }
    }

    private companion object {
        const val ROUNDS = 50
        const val WAIT_MS = 5_000L
    }
}
