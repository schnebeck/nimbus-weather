/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StationMissedTest.kt
 * The look-back without the DWD's readings for a moment (no network, the service down): it says
 * so instead of "no station near", keeps the readings it showed and asks again.
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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.History
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.SummaryCard
import dev.nimbus.weather.util.TimeFormat
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de", application = android.app.Application::class)
class StationMissedTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-29T10:20:00Z").toEpochMilli()

    /** The look-back of Hannover with Bright Sky answering [brightSky] (null: not at all). */
    private fun load(brightSky: MockResponse?, inGermany: Boolean = true): History = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/v1/forecast" -> MockResponse.Builder().code(200).body(Fixtures.text("openmeteo_history.json")).build()
                "/weather" -> brightSky ?: MockResponse.Builder().code(503).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            HistorySource(OkHttpClient(), base, base).load(52.3759, 9.732, "icon_seamless", inGermany, now = now)
        } finally { server.close() }
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    /** Bright Sky not answering: no station named, but not "none near" – the readings missed. */
    @Test fun theReadingsMissedAreNotNoStation() {
        val missed = load(null)
        assertNull(missed.stationName)
        assertTrue(missed.stationMissed)
        // answered: the station; answered without one near; outside Germany not asked – none missed
        val found = load(ok(Fixtures.text("brightsky_history.json")))
        assertEquals("Hannover-Herrenhausen", found.stationName)
        assertFalse(found.stationMissed)
        val none = load(ok("""{"weather":[],"sources":[]}"""))
        assertNull(none.stationName)
        assertFalse(none.stationMissed)
        assertFalse(load(null, inGermany = false).stationMissed)
    }

    /** The readings shown stay while a new load misses them; without any shown, the new one stands. */
    @Test fun theReadingsShownStayWhileMissed() {
        val found = load(ok(Fixtures.text("brightsky_history.json")))
        val missed = load(null)
        assertSame(found, HistorySource.shown(missed, found))
        assertSame(missed, HistorySource.shown(missed, null))
        assertSame(found, HistorySource.shown(found, missed))
        assertSame(found, HistorySource.shown(null, found))
    }

    /** The card says the readings did not come – not that there is no station near. */
    @Test fun theCardSaysTheReadingsDidNotCome() {
        val missed = load(null)
        val day = missed.days.last { it.hours.isNotEmpty() }
        val tf = TimeFormat("Europe/Berlin", true)
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalTimeFormat provides tf) {
                Column { SummaryCard(DaySummary.of(day, missed.fetchedAt), missed, Settings(), tf, soFar = true) }
            }
        }
        compose.onNodeWithText("DWD-Messwerte gerade nicht erreichbar", substring = true).assertExists()
        compose.onNodeWithText("Keine DWD-Station in der Nähe", substring = true).assertDoesNotExist()
    }
}
