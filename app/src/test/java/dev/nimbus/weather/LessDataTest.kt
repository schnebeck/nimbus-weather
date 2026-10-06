/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LessDataTest.kt
 * Less data and less radio: the radar loop loaded ahead only for its user and without the nowcast,
 * the look-back renewed for the place shown only.
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
import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarPrefetcher
import dev.nimbus.weather.ui.radar.RadarTimeline
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * „Würdest du nach der Akkuanalyse noch maßnahmen ergreifen wollen?“ – „ja, als 1.36.1 umsetzen“:
 * 60 MB a day over Wi-Fi with 22 minutes of use, the radio awake after every opening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LessDataTest {
    private val step = 5 * 60_000L
    private val latest = 1_791_219_000_000L / step * step

    /** The steps loaded ahead: the past ones – the nowcast is new with every analysis. */
    @Test fun noNowcastLoadedAhead() {
        val tl = RadarTimeline((-24..24).map { k -> latest + k * step }.map { t -> RadarFrame(t, t > latest, if (t > latest) latest else null, null) }, 24, "")
        val steps = RadarPrefetcher.stepsAhead(tl, listOf(DwdRadar))
        assertEquals(25, steps.size)
        assertTrue(steps.none { it.second.isForecast })
    }

    /** Loaded ahead only for someone who opened the radar within a week. */
    @Test fun onlyForTheRadarsUser() {
        val now = 1_791_219_000_000L
        assertFalse("never opened", RadarPrefetcher.usedRecently(0L, now))
        assertTrue(RadarPrefetcher.usedRecently(now - 6 * 24 * 3_600_000L, now))
        assertFalse(RadarPrefetcher.usedRecently(now - 8 * 24 * 3_600_000L, now))
        val app = RuntimeEnvironment.getApplication()
        assertFalse(RadarPrefetcher.usedRecently(app, now))
        RadarPrefetcher.radarOpened(app, now)
        assertTrue(RadarPrefetcher.usedRecently(app, now + 3_600_000L))
    }

    private val server = MockWebServer()
    /** The latitudes the look-back was asked for. */
    private val lookBack = java.util.Collections.synchronizedList(mutableListOf<Double>())

    @After fun tearDown() = server.close()

    /** Back from the background, both look-backs out of date: only the shown place's is renewed. */
    @Test fun theLookBackOfTheShownPlaceOnly() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                if (url.queryParameter("past_days") != null) {
                    url.queryParameter("latitude")?.toDoubleOrNull()?.let { lookBack += it }
                    return MockResponse.Builder().code(200).body(Fixtures.text("openmeteo_history.json")).build()
                }
                return MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val app: Application = RuntimeEnvironment.getApplication()
        val base = server.url("/").toString().trimEnd('/')
        val http = OkHttpClient()
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
        val repo = WeatherRepository(
            OpenMeteoSource(offline), BrightSkySource(offline), CommunitySource(offline), PollenSource(offline),
        )
        val shown = Place("a", "Hannover", latitude = 52.3759, longitude = 9.732)
        val other = Place("b", "München", latitude = 48.137, longitude = 11.575)
        val store = Store(app)
        runBlocking {
            store.updateSettings { Settings(preloadRadar = false) }
            store.updatePlaces { listOf(shown, other) }
        }
        val vm = MainViewModel(app, ViewModelDeps(store, repo, LocationProvider(app), HistorySource(http, base, base), offline, offline))
        until("started") { vm.state.value.initialized && vm.state.value.selectedPlaceId == shown.id }
        vm.onResume()
        vm.loadHistory(shown.id)
        vm.loadHistory(other.id)
        until("both look-backs") { vm.state.value.states[shown.id]?.history != null && vm.state.value.states[other.id]?.history != null }
        // away – and both out of date meanwhile
        vm.onPause()
        vm.shelf.lookBack(shown.id).stale()
        vm.shelf.lookBack(other.id).stale()
        lookBack.clear()
        vm.onResume()
        until("the shown place's renewed") { lookBack.isNotEmpty() && vm.state.value.states[shown.id]?.historyLoading == false }
        repeat(20) { shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20)); Thread.sleep(20) }
        assertTrue("asked for: $lookBack", lookBack.all { kotlin.math.abs(it - shown.latitude) < 0.001 })
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
