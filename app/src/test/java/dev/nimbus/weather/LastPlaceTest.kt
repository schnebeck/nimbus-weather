/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LastPlaceTest.kt
 * The place last looked at: the app opens there again, and a widget may follow it.
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

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.MainViewModel
import dev.nimbus.weather.ui.ViewModelDeps
import dev.nimbus.weather.widget.WidgetConfig
import dev.nimbus.weather.widget.Widgets
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
class LastPlaceTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val hannover = Place("h", "Hannover", latitude = 52.3759, longitude = 9.732)
    private val norden = Place("n", "Norden", latitude = 53.596, longitude = 7.206)
    private val store by lazy { Store(app) }
    private var vm: MainViewModel? = null

    private fun viewModel(): MainViewModel {
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
        val repo = WeatherRepository(OpenMeteoSource(offline), BrightSkySource(offline), CommunitySource(offline), PollenSource(offline))
        return MainViewModel(app, ViewModelDeps(store, repo, LocationProvider(app), HistorySource(offline), offline, offline)).also { vm = it }
    }

    @After fun tidy() {
        // the store is one per process: the next test finds it as it was
        vm?.viewModelScope?.cancel()
        runBlocking { store.updatePlaces { emptyList() }; store.updateSettings { Settings() }; store.setLastPlace(null) }
    }

    /** Norden looked at last, not first in the list: the app opens with Norden again – and keeps the next one looked at. */
    @Test fun theAppOpensWhereItWasLeft() {
        runBlocking {
            store.updateSettings { Settings(preloadRadar = false) }
            store.updatePlaces { listOf(hannover, norden) }
            store.setLastPlace(norden.id)
        }
        val vm = viewModel()
        until("started") { vm.state.value.initialized }
        assertEquals(norden.id, vm.state.value.selectedPlaceId)
        vm.select(hannover.id)
        until("kept") { runBlocking { store.lastPlace.first() } == hannover.id }
    }

    /** A place no longer in the list: the app opens with the first. */
    @Test fun aPlaceGoneOpensTheFirst() {
        runBlocking {
            store.updateSettings { Settings(preloadRadar = false) }
            store.updatePlaces { listOf(hannover, norden) }
            store.setLastPlace("gone")
        }
        val vm = viewModel()
        until("started") { vm.state.value.initialized }
        assertEquals(hannover.id, vm.state.value.selectedPlaceId)
    }

    /**
     * A widget following the app shows the place last looked at there (Norden, though Hannover is
     * first); a widget with a place of its own keeps it; none looked at yet: as "my location".
     */
    @Test fun aWidgetFollowsTheApp() = runBlocking {
        store.updatePlaces { listOf(hannover, norden) }
        val follows = WidgetConfig(followApp = true)
        assertEquals(hannover.id, Widgets.placeOf(store, follows)?.id)
        store.setLastPlace(norden.id)
        assertEquals(norden.id, Widgets.placeOf(store, follows)?.id)
        assertEquals(hannover.id, Widgets.placeOf(store, WidgetConfig(placeId = hannover.id))?.id)
        store.setLastPlace(hannover.id)
        assertEquals(hannover.id, Widgets.placeOf(store, follows)?.id)
    }

    private fun until(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            if (System.currentTimeMillis() > end) throw AssertionError("not reached: $what")
            Thread.sleep(20)
        }
    }
}
