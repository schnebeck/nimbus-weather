/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MyLocationTest.kt
 * "My location" asked for anew (reload, the pin): every request for it waits until the position
 * is confirmed or the new place is taken – then its data load, for the place it is.
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
import android.location.Location
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.repo.Locate
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.LocationStatus
import dev.nimbus.weather.ui.MainViewModel
import dev.nimbus.weather.ui.ViewModelDeps
import dev.nimbus.weather.ui.main.locationMark
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MyLocationTest {
    private val server = MockWebServer()
    /** The forecast answers this late (ms): its cards stay yellow meanwhile. */
    @Volatile private var forecastDelayMs = 0L
    /** Latitudes of the forecast requests, in order. */
    private val asked = java.util.Collections.synchronizedList(mutableListOf<Double>())

    private val hannover = Place(LocationProvider.CURRENT_LOCATION_ID, "Hannover", latitude = 52.37, longitude = 9.73, isCurrentLocation = true)

    /** The phone's position: answers when the test says so. */
    private class Phone(app: Application) : LocationProvider(app) {
        var answer = CompletableDeferred<Locate.Found<Location>?>()
        var askedFresh: Boolean? = null
        override fun hasPermission() = true
        override fun enabled() = true
        override suspend fun currentLocation(fresh: Boolean) = answer.await().also { askedFresh = fresh }
        override suspend fun toPlace(location: Location, fallbackName: String) =
            Place(CURRENT_LOCATION_ID, if (location.latitude < 52.0) "Bad Harzburg" else "Hannover", latitude = location.latitude, longitude = location.longitude, isCurrentLocation = true)
    }

    private fun at(lat: Double, lon: Double, time: Long) = Locate.Found(Location("gps").apply { latitude = lat; longitude = lon }, time)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                return when {
                    url.encodedPath == "/v1/forecast" -> {
                        if (url.queryParameter("models") == null || url.queryParameter("models") == "icon_seamless") {
                            url.queryParameter("latitude")?.toDouble()?.let { asked += it }
                        }
                        val body = if (url.queryParameter("models") == "icon_seamless") "openmeteo_icon.json" else "openmeteo_best.json"
                        MockResponse.Builder().code(200).body(Fixtures.text(body))
                            .headersDelay(forecastDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                    }
                    url.encodedPath == "/v1/air-quality" -> ok("openmeteo_aq.json")
                    url.encodedPath == "/current_weather" -> ok("brightsky_current.json")
                    url.encodedPath == "/alerts" -> MockResponse.Builder().code(200).body("""{"alerts":[]}""").build()
                    url.encodedPath.startsWith("/airrohr") -> ok("sensor_community.json")
                    url.encodedPath == "/pollen.json" -> ok("dwd_pollen.json")
                    url.encodedPath == "/wms" -> ok("dwd_pollen_region.json")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    /** Runs the main thread – its clock with it (the delays of the view model) – until [done] or 10 s. */
    private fun until(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            if (System.currentTimeMillis() > end) throw AssertionError("not reached: $what")
            Thread.sleep(20)
        }
    }

    private fun model(app: Application, phone: Phone): MainViewModel {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        val repo = WeatherRepository(
            OpenMeteoSource(http, base, base, base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base),
        )
        // nothing else reaches the network (radar, previews, look-back)
        val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
        val store = Store(app)
        runBlocking {
            store.updateSettings { Settings(preloadRadar = false) }
            // "my location" was Hannover, its data current
            store.cacheWeather(repo.load(hannover, Settings(), german = true))
        }
        asked.clear()
        return MainViewModel(app, ViewModelDeps(store, repo, phone, HistorySource(offline), offline, offline))
    }

    /**
     * Driven from Hannover to Bad Harzburg, pulled to reload: nothing is loaded while the position
     * is looked for – not Hannover's data again –, the page counts as not current meanwhile;
     * the new place's data load once it is taken.
     */
    @Test fun aReloadWaitsForThePosition() {
        val app = RuntimeEnvironment.getApplication()
        val phone = Phone(app)
        val vm = model(app, phone)
        // the start: the position confirmed (Hannover), its data loaded
        until("the start's search") { vm.state.value.locationStatus == LocationStatus.LOADING }
        phone.answer.complete(at(52.37, 9.73, System.currentTimeMillis()))
        until("Hannover loaded") { vm.state.value.locationStatus == LocationStatus.AVAILABLE && vm.state.value.states[hannover.id]?.loading == false }
        asked.clear()

        phone.answer = CompletableDeferred()
        vm.refresh(hannover.id)
        // looking for the position: not current, and no request for the place's data
        until("searching") { vm.state.value.locationStatus == LocationStatus.LOADING }
        assertFalse("shown as current while asked for anew", locationMark(vm.state.value, System.currentTimeMillis()).current)
        repeat(20) { shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20)); Thread.sleep(20) }
        assertTrue("loaded while the position was looked for: $asked", asked.isEmpty())

        // the answer: Bad Harzburg – its data, and only its
        phone.answer.complete(at(51.88, 10.56, System.currentTimeMillis()))
        until("Bad Harzburg loaded") { vm.state.value.states[hannover.id]?.data?.place?.name == "Bad Harzburg" && vm.state.value.states[hannover.id]?.loading == false }
        assertTrue("requests: $asked", asked.isNotEmpty() && asked.all { it == 51.88 })
        assertTrue(locationMark(vm.state.value, System.currentTimeMillis()).current)
    }

    /** The same place confirmed: its data load anew all the same (pulled to reload). */
    @Test fun aReloadAtTheSamePlaceLoadsAnew() {
        val app = RuntimeEnvironment.getApplication()
        val phone = Phone(app)
        val vm = model(app, phone)
        until("the start's search") { vm.state.value.locationStatus == LocationStatus.LOADING }
        phone.answer.complete(at(52.37, 9.73, System.currentTimeMillis()))
        until("Hannover loaded") { vm.state.value.locationStatus == LocationStatus.AVAILABLE && vm.state.value.states[hannover.id]?.loading == false }
        asked.clear()

        val position = at(52.37, 9.73, System.currentTimeMillis() - 30_000)   // the same, half a minute old
        phone.answer = CompletableDeferred(position)
        vm.refresh(hannover.id)
        until("Hannover loaded anew") { asked.isNotEmpty() && vm.state.value.states[hannover.id]?.loading == false }
        assertEquals("Hannover", vm.state.value.states[hannover.id]?.data?.place?.name)
        assertTrue(asked.all { it == 52.37 })
    }

    /**
     * A forced reload with "my location" active, step by step: everything yellow at once; the
     * position asked for anew (not the system's last one); the place's dot green when it is
     * confirmed, the cards still yellow; then the cards green when their data are there.
     */
    @Test fun aForcedReloadShowsEachStep() {
        val app = RuntimeEnvironment.getApplication()
        val phone = Phone(app)
        val vm = model(app, phone)
        until("the start's search") { vm.state.value.locationStatus == LocationStatus.LOADING }
        phone.answer.complete(at(52.37, 9.73, System.currentTimeMillis()))
        until("Hannover loaded") { vm.state.value.locationStatus == LocationStatus.AVAILABLE && vm.state.value.states[hannover.id]?.loading == false }
        assertEquals("the start may take the system's recent position", false, phone.askedFresh)
        fun yellow(): Set<dev.nimbus.weather.data.model.DataPart> {
            val st = vm.state.value
            val data = st.states[hannover.id]!!.data!!
            val now = System.currentTimeMillis()
            return dev.nimbus.weather.ui.main.pageStale(data.stale + dev.nimbus.weather.data.repo.Freshness.expiredParts(data, now), locationMark(st, now))
        }
        assertTrue("not all green before: ${yellow()}", yellow().isEmpty())

        forecastDelayMs = 1_500
        phone.answer = CompletableDeferred()
        vm.refresh(hannover.id)
        // 1. everything yellow, the position being asked for – anew
        until("searching") { vm.state.value.locationStatus == LocationStatus.LOADING }
        assertEquals(dev.nimbus.weather.data.model.DataPart.entries.toSet(), yellow())
        assertFalse(locationMark(vm.state.value, System.currentTimeMillis()).current)
        // 2. the position confirmed: the place's dot green, the cards still yellow (their data on the way)
        phone.answer.complete(at(52.37, 9.73, System.currentTimeMillis()))
        until("position confirmed") { vm.state.value.locationStatus == LocationStatus.AVAILABLE }
        assertEquals("the position was not asked for anew", true, phone.askedFresh)
        assertTrue(locationMark(vm.state.value, System.currentTimeMillis()).current)
        assertTrue("cards green before their data: ${yellow()}", dev.nimbus.weather.data.model.DataPart.FORECAST in yellow())
        // 3. the data there: green
        until("loaded anew") { vm.state.value.states[hannover.id]?.loading == false }
        assertTrue("still yellow: ${yellow()}", yellow().isEmpty())
    }
}
