/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarHabitsTest.kt
 * The radar as it is used: layers on from the start (settings), and closed by itself when left
 * untouched.
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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.nimbus.weather.data.model.RadarOverlay
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.radar.IdleClose
import dev.nimbus.weather.ui.settings.RadarOverlaysOnOpening
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de", application = android.app.Application::class)
class RadarHabitsTest {
    @get:Rule val compose = createComposeRule()

    /** Lightning and satellite on from the start, chosen in the settings; kept over a restart. */
    @Test fun layersOnFromTheStart() {
        var settings by mutableStateOf(Settings())
        compose.setContent { RadarOverlaysOnOpening(settings.radarOverlays) { o -> settings = settings.copy(radarOverlays = o) } }
        compose.onNodeWithText("Blitze").performClick()
        compose.onNodeWithText("Satellit").performClick()
        compose.waitForIdle()
        assertEquals(setOf(RadarOverlay.LIGHTNING, RadarOverlay.SATELLITE), settings.radarOverlays)
        val stored = JsonCodec.encodeToString(Settings.serializer(), settings)
        assertEquals(settings.radarOverlays, JsonCodec.decodeFromString(Settings.serializer(), stored).radarOverlays)
        // settings stored before: none on, as before
        assertEquals(emptySet<RadarOverlay>(), JsonCodec.decodeFromString(Settings.serializer(), "{}").radarOverlays)
    }

    /** Two minutes untouched: closed; a touch starts the two minutes anew. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun closedAfterTwoMinutesUntouched() = runTest {
        val idle = IdleClose(clock = { testScheduler.currentTime })
        var closed = false
        launch { idle.awaitIdle(); closed = true }
        advanceTimeBy(90_000); runCurrent()
        idle.touched()
        advanceTimeBy(90_000); runCurrent()
        assertFalse("closed though touched 90 s ago", closed)
        advanceTimeBy(30_001); runCurrent()
        assertTrue(closed)
        assertEquals(2 * 60_000L, IdleClose.AFTER_MS)
    }

    /**
     * The loop loaded ahead in the background, if the battery is not low – over Wi-Fi, or any
     * network if so set; switched off with the setting. Around the place last seen on the radar.
     */
    @Test fun loadedAheadInTheBackground() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        // initialised once per process (another test may have done it)
        runCatching { androidx.work.WorkManager.initialize(ctx, androidx.work.Configuration.Builder().setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor()).build()) }
        val wm = androidx.work.WorkManager.getInstance(ctx)
        fun info() = wm.getWorkInfosForUniqueWork(dev.nimbus.weather.data.repo.BackgroundWork.RADAR_AHEAD).get().last()
        dev.nimbus.weather.data.repo.BackgroundWork.radarAhead(ctx, on = true, mobile = false)
        assertEquals(androidx.work.NetworkType.UNMETERED, info().constraints.requiredNetworkType)
        assertTrue(info().constraints.requiresBatteryNotLow())
        assertEquals(30 * 60_000L, info().periodicityInfo!!.repeatIntervalMillis)
        dev.nimbus.weather.data.repo.BackgroundWork.radarAhead(ctx, on = true, mobile = true)
        assertEquals(androidx.work.NetworkType.CONNECTED, info().constraints.requiredNetworkType)
        dev.nimbus.weather.data.repo.BackgroundWork.radarAhead(ctx, on = false, mobile = true)
        assertEquals(androidx.work.WorkInfo.State.CANCELLED, info().state)
        // the place it loads for: where the radar was opened last
        dev.nimbus.weather.ui.radar.RadarPrefetcher.radarOpened(ctx, dev.nimbus.weather.data.model.Place("p", "Celle", latitude = 52.62, longitude = 10.08))
        assertEquals(52.62 to 10.08, dev.nimbus.weather.ui.radar.RadarPrefetcher.lastPlace(ctx))
        assertFalse(Settings().preloadRadarMobile)
    }

    /** Everything of the radar in the settings' group "Radar": the layers on opening, loading ahead, the colours. */
    @Test @Config(qualifiers = "de-w411dp-h4000dp") fun theRadarsSettingsTogether() {
        compose.setContent { dev.nimbus.weather.ui.settings.SettingsScreen(Settings(), {}, {}, {}) }
        compose.waitForIdle()
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        val radar = top("RADAR")
        val next = top("BADEGEWÄSSER")
        for (t in listOf("Ebenen beim Öffnen", "Radar vorladen", "Radarfarben")) {
            assertTrue("$t in the radar's group", top(t) in radar..next)
        }
    }

    /** The sources take no line more with the lightning on (two at a phone's width), three at most with the satellite too. */
    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi") fun theSourcesTakeNoLineMore() {
        var plain = 0; var withLightning = 0; var withBoth = 0
        compose.setContent {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(379.dp)) {
                dev.nimbus.weather.ui.radar.RadarSourcesNote(false, null) { plain = it }
                dev.nimbus.weather.ui.radar.RadarSourcesNote(true, null) { withLightning = it }
                dev.nimbus.weather.ui.radar.RadarSourcesNote(true, 2026) { withBoth = it }
            }
        }
        compose.waitForIdle()
        assertEquals("lightning on: no line more", plain, withLightning)
        assertTrue("$withLightning", withLightning <= 2)
        assertTrue("$withBoth", withBoth <= 3)
    }
}
